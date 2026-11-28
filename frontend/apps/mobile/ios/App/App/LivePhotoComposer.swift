import AVFoundation
import CoreMedia
import ImageIO
import UniformTypeIdentifiers

enum LivePhotoComposer {
    static func writePhoto(source: URL, destination: URL, identifier: String?, maxPixels: Int? = nil) throws {
        guard let input = CGImageSourceCreateWithURL(source as CFURL, nil) else { throw LivePhotoPlugin.failure("无法读取实况照片图像") }
        var options: [CFString: Any] = [kCGImageSourceCreateThumbnailFromImageAlways: true, kCGImageSourceCreateThumbnailWithTransform: true]
        let properties = CGImageSourceCopyPropertiesAtIndex(input, 0, nil) as? [CFString: Any] ?? [:]
        let width = properties[kCGImagePropertyPixelWidth] as? Int ?? 4096
        let height = properties[kCGImagePropertyPixelHeight] as? Int ?? 4096
        options[kCGImageSourceThumbnailMaxPixelSize] = maxPixels ?? max(width, height)
        guard let image = CGImageSourceCreateThumbnailAtIndex(input, 0, options as CFDictionary),
              let output = CGImageDestinationCreateWithURL(destination as CFURL, UTType.jpeg.identifier as CFString, 1, nil) else {
            throw LivePhotoPlugin.failure("实况照片 JPEG 转换失败")
        }
        var metadata: [CFString: Any] = [kCGImageDestinationLossyCompressionQuality: 0.95]
        if let identifier = identifier { metadata[kCGImagePropertyMakerAppleDictionary] = ["17": identifier] }
        CGImageDestinationAddImage(output, image, metadata as CFDictionary)
        guard CGImageDestinationFinalize(output) else { throw LivePhotoPlugin.failure("实况照片 JPEG 写入失败") }
    }

    static func compose(photo: URL, video: URL, directory: URL) async throws -> (URL, URL) {
        let identifier = UUID().uuidString
        let photoURL = directory.appendingPathComponent("converted.jpg")
        let videoURL = directory.appendingPathComponent("converted.mov")
        try writePhoto(source: photo, destination: photoURL, identifier: identifier)
        let asset = AVURLAsset(url: video)
        let duration = try await asset.load(.duration)
        let seconds = CMTimeGetSeconds(duration)
        guard seconds.isFinite, seconds > 0, seconds <= 30 else { throw LivePhotoPlugin.failure("实况视频时长须在 30 秒以内") }
        let tracks = try await asset.loadTracks(withMediaType: .video)
        guard let track = tracks.first else { throw LivePhotoPlugin.failure("配套文件中没有视频轨") }
        let reader = try AVAssetReader(asset: asset)
        let writer = try AVAssetWriter(outputURL: videoURL, fileType: .mov)
        let contentId = AVMutableMetadataItem()
        contentId.keySpace = .quickTimeMetadata
        contentId.key = "com.apple.quicktime.content.identifier" as NSString
        contentId.value = identifier as NSString
        contentId.dataType = kCMMetadataBaseDataType_UTF8 as String
        writer.metadata = [contentId]

        // Passthrough retains original H.264/HEVC and audio; unsupported codecs fail explicitly.
        var streams: [(AVAssetReaderTrackOutput, AVAssetWriterInput)] = []
        let audio = try await asset.loadTracks(withMediaType: .audio)
        for mediaTrack in [track] + Array(audio.prefix(1)) {
            let formats = try await mediaTrack.load(.formatDescriptions)
            let output = AVAssetReaderTrackOutput(track: mediaTrack, outputSettings: nil)
            output.alwaysCopiesSampleData = false
            let input = AVAssetWriterInput(mediaType: mediaTrack.mediaType, outputSettings: nil, sourceFormatHint: formats.first)
            input.expectsMediaDataInRealTime = false
            if mediaTrack.mediaType == .video { input.transform = try await mediaTrack.load(.preferredTransform) }
            guard reader.canAdd(output), writer.canAdd(input) else { throw LivePhotoPlugin.failure("当前视频编码无法写入 Apple 实况容器") }
            reader.add(output)
            writer.add(input)
            streams.append((output, input))
        }

        let specification: [String: Any] = [
            kCMMetadataFormatDescriptionMetadataSpecificationKey_Identifier as String: "mdta/com.apple.quicktime.still-image-time",
            kCMMetadataFormatDescriptionMetadataSpecificationKey_DataType as String: kCMMetadataBaseDataType_SInt8 as String
        ]
        var description: CMFormatDescription?
        let status = CMMetadataFormatDescriptionCreateWithMetadataSpecifications(allocator: kCFAllocatorDefault, metadataType: kCMMetadataFormatType_Boxed,
            metadataSpecifications: [specification] as CFArray, formatDescriptionOut: &description)
        guard status == noErr, let description = description else { throw LivePhotoPlugin.failure("实况时间标记创建失败") }
        let metadataInput = AVAssetWriterInput(mediaType: .metadata, outputSettings: nil, sourceFormatHint: description)
        let adaptor = AVAssetWriterInputMetadataAdaptor(assetWriterInput: metadataInput)
        guard writer.canAdd(metadataInput) else { throw LivePhotoPlugin.failure("无法写入实况时间轨") }
        writer.add(metadataInput)
        guard writer.startWriting(), reader.startReading() else { throw writer.error ?? reader.error ?? LivePhotoPlugin.failure("实况转换启动失败") }
        writer.startSession(atSourceTime: .zero)
        let time = AVMutableMetadataItem()
        time.keySpace = .quickTimeMetadata
        time.key = "com.apple.quicktime.still-image-time" as NSString
        time.value = NSNumber(value: Int8(0))
        time.dataType = kCMMetadataBaseDataType_SInt8 as String
        let group = AVTimedMetadataGroup(items: [time], timeRange: CMTimeRange(start: CMTime(seconds: seconds / 2, preferredTimescale: 600), duration: CMTime(value: 1, timescale: 30)))
        guard adaptor.append(group) else { writer.cancelWriting(); reader.cancelReading(); throw LivePhotoPlugin.failure("实况时间轨写入失败") }
        metadataInput.markAsFinished()

        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            let queue = DispatchQueue(label: "com.wanyue.cloudlane.live-photo.remux")
            let completion = DispatchGroup()
            for (output, input) in streams {
                completion.enter()
                var finished = false
                input.requestMediaDataWhenReady(on: queue) {
                    guard !finished else { return }
                    while input.isReadyForMoreMediaData {
                        guard writer.status == .writing, let sample = output.copyNextSampleBuffer() else {
                            finished = true; input.markAsFinished(); completion.leave(); return
                        }
                        if !input.append(sample) {
                            finished = true; input.markAsFinished(); completion.leave(); return
                        }
                    }
                }
            }
            completion.notify(queue: queue) {
                if reader.status == .failed || writer.status == .failed {
                    writer.cancelWriting()
                    continuation.resume(throwing: reader.error ?? writer.error ?? LivePhotoPlugin.failure("实况视频转换失败"))
                    return
                }
                writer.finishWriting {
                    if writer.status == .completed { continuation.resume() }
                    else { continuation.resume(throwing: writer.error ?? LivePhotoPlugin.failure("实况视频写入失败")) }
                }
            }
        }
        return (photoURL, videoURL)
    }
}
