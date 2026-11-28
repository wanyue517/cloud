import Capacitor
import Photos
import PhotosUI
import ImageIO
import UniformTypeIdentifiers

@objc(LivePhotoPlugin)
public final class LivePhotoPlugin: CAPPlugin, CAPBridgedPlugin, PHPickerViewControllerDelegate {
    public let identifier = "LivePhotoPlugin"
    public let jsName = "LivePhoto"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "pick", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "save", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "cleanup", returnType: CAPPluginReturnPromise)
    ]
    private var pickerCall: CAPPluginCall?
    private var saving = false
    static let maxBytes = 64 * 1024 * 1024

    static func directory(_ session: String, create: Bool = false) throws -> URL {
        guard UUID(uuidString: session) != nil else { throw failure("实况临时会话无效") }
        let cache = try FileManager.default.url(for: .cachesDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
        let directory = cache.appendingPathComponent("cloudlane-live", isDirectory: true).appendingPathComponent(session, isDirectory: true)
        if create { try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true) }
        return directory
    }

    static func failure(_ message: String) -> NSError {
        NSError(domain: "Cloudlane.LivePhoto", code: 1, userInfo: [NSLocalizedDescriptionKey: message])
    }

    private static func checkedFile(_ directory: URL, _ name: String) throws -> URL {
        guard !name.isEmpty, name == (name as NSString).lastPathComponent, name != ".", name != ".." else { throw failure("实况文件名无效") }
        let file = directory.appendingPathComponent(name).resolvingSymlinksInPath()
        guard file.deletingLastPathComponent() == directory.resolvingSymlinksInPath() else { throw failure("实况文件路径无效") }
        let size = try file.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
        guard size > 0, size <= maxBytes else { throw failure("实况原件须在 64 MB 以内") }
        return file
    }

    @objc func pick(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            guard self.pickerCall == nil, !self.saving else { call.reject("正在处理另一张实况照片", "BUSY"); return }
            guard let controller = self.bridge?.viewController, controller.presentedViewController == nil else {
                call.reject("相册暂时无法打开，请关闭当前弹窗后重试"); return
            }
            self.pickerCall = call
            var configuration = PHPickerConfiguration()
            configuration.filter = .livePhotos
            configuration.selectionLimit = 1
            configuration.preferredAssetRepresentationMode = .current
            let picker = PHPickerViewController(configuration: configuration)
            picker.delegate = self
            controller.present(picker, animated: true)
        }
    }

    public func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
        picker.dismiss(animated: true)
        guard let call = pickerCall else { return }
        guard let result = results.first else { pickerCall = nil; call.reject("已取消选择", "CANCELLED"); return }
        guard result.itemProvider.canLoadObject(ofClass: PHLivePhoto.self) else {
            pickerCall = nil; call.reject("所选资源不包含完整实况照片"); return
        }
        result.itemProvider.loadObject(ofClass: PHLivePhoto.self) { object, error in
            guard let live = object as? PHLivePhoto else {
                DispatchQueue.main.async { self.pickerCall = nil; call.reject("实况原件读取失败，请确认 iCloud 原件已下载", nil, error) }
                return
            }
            Task {
                let session = UUID().uuidString
                do {
                    let directory = try Self.directory(session, create: true)
                    let resources = PHAssetResource.assetResources(for: live)
                    guard let photo = resources.first(where: { $0.type == .photo || $0.type == .fullSizePhoto }),
                          let video = resources.first(where: { $0.type == .pairedVideo || $0.type == .fullSizePairedVideo }) else {
                        throw Self.failure("所选照片缺少静态原件或配套视频")
                    }
                    let photoExt = UTType(photo.uniformTypeIdentifier)?.preferredFilenameExtension ?? "heic"
                    let photoURL = directory.appendingPathComponent("photo.\(photoExt)")
                    let videoURL = directory.appendingPathComponent("paired.mov")
                    try await self.export(photo, to: photoURL)
                    try await self.export(video, to: videoURL)
                    _ = try Self.checkedFile(directory, photoURL.lastPathComponent)
                    _ = try Self.checkedFile(directory, videoURL.lastPathComponent)
                    let posterURL = directory.appendingPathComponent("poster.jpg")
                    try LivePhotoComposer.writePhoto(source: photoURL, destination: posterURL, identifier: nil, maxPixels: 2048)
                    DispatchQueue.main.async {
                        self.pickerCall = nil
                        call.resolve([
                            "sessionId": session,
                            "photo": ["path": photoURL.absoluteString, "name": photoURL.lastPathComponent, "mimeType": UTType(photo.uniformTypeIdentifier)?.preferredMIMEType ?? "image/heic"],
                            "video": ["path": videoURL.absoluteString, "name": "paired.mov", "mimeType": "video/quicktime"],
                            "poster": ["path": posterURL.absoluteString, "name": "poster.jpg", "mimeType": "image/jpeg"]
                        ])
                    }
                } catch {
                    if let directory = try? Self.directory(session) { try? FileManager.default.removeItem(at: directory) }
                    DispatchQueue.main.async { self.pickerCall = nil; call.reject(error.localizedDescription, "EXPORT_FAILED", error) }
                }
            }
        }
    }

    private func export(_ resource: PHAssetResource, to url: URL) async throws {
        let options = PHAssetResourceRequestOptions()
        options.isNetworkAccessAllowed = true
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            PHAssetResourceManager.default().writeData(for: resource, toFile: url, options: options) { error in
                if let error = error { continuation.resume(throwing: error) }
                else { continuation.resume() }
            }
        }
    }

    @objc func save(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            guard !self.saving, self.pickerCall == nil else { call.reject("正在处理另一张实况照片", "BUSY"); return }
            guard let session = call.getString("sessionId"), let photoName = call.getString("photoName"), let videoName = call.getString("videoName") else {
                call.reject("缺少实况文件"); return
            }
            self.saving = true
            Task {
                do {
                    let status = await PHPhotoLibrary.requestAuthorization(for: .addOnly)
                    guard status == .authorized || status == .limited else { throw Self.failure("请在系统设置中允许 Cloudlane 添加照片") }
                    let directory = try Self.directory(session)
                    let photo = try Self.checkedFile(directory, photoName)
                    let video = try Self.checkedFile(directory, videoName)
                    var importPhoto = photo
                    var importVideo = video
                    // Preserve existing Apple pairs byte-for-byte. Convert Android/manual pairs only if validation fails.
                    if !(await self.validPair(photo: photo, video: video)) {
                        let result = try await LivePhotoComposer.compose(photo: photo, video: video, directory: directory)
                        importPhoto = result.0
                        importVideo = result.1
                        guard await self.validPair(photo: importPhoto, video: importVideo) else { throw Self.failure("无法将此文件转换为系统实况照片") }
                    }
                    try await PHPhotoLibrary.shared().performChanges {
                        let request = PHAssetCreationRequest.forAsset()
                        request.addResource(with: .photo, fileURL: importPhoto, options: nil)
                        request.addResource(with: .pairedVideo, fileURL: importVideo, options: nil)
                    }
                    DispatchQueue.main.async { self.saving = false; call.resolve() }
                } catch {
                    DispatchQueue.main.async { self.saving = false; call.reject(error.localizedDescription, "SAVE_FAILED", error) }
                }
            }
        }
    }

    private func validPair(photo: URL, video: URL) async -> Bool {
        await withCheckedContinuation { continuation in
            PHLivePhoto.request(withResourceFileURLs: [photo, video], placeholderImage: nil, targetSize: .zero, contentMode: .aspectFit) { livePhoto, info in
                if (info[PHLivePhotoInfoIsDegradedKey] as? Bool) == true { return }
                continuation.resume(returning: livePhoto != nil && info[PHLivePhotoInfoErrorKey] == nil)
            }
        }
    }

    @objc func cleanup(_ call: CAPPluginCall) {
        guard let session = call.getString("sessionId") else { call.reject("缺少临时会话"); return }
        do {
            let directory = try Self.directory(session)
            if FileManager.default.fileExists(atPath: directory.path) { try FileManager.default.removeItem(at: directory) }
            call.resolve()
        } catch { call.reject("实况临时文件清理失败", nil, error) }
    }
}
