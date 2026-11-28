package com.wanyue.cloud.modules.drive.preview;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** A downloadable JPEG + MP4 container. Original cloud assets remain untouched. */
public final class MotionPhotoWriter {
    private static final byte[] XMP_PREFIX = "http://ns.adobe.com/xap/1.0/\0".getBytes(StandardCharsets.US_ASCII);
    private MotionPhotoWriter() {}

    public static void write(LivePhotoService.Part photo, Path video, OutputStream output) throws IOException {
        long videoLength = Files.size(video);
        String xml = "<x:xmpmeta xmlns:x='adobe:ns:meta/'><rdf:RDF xmlns:rdf='http://www.w3.org/1999/02/22-rdf-syntax-ns#'>"
                + "<rdf:Description xmlns:Camera='http://ns.google.com/photos/1.0/camera/' xmlns:Container='http://ns.google.com/photos/1.0/container/' xmlns:Item='http://ns.google.com/photos/1.0/container/item/' "
                + "Camera:MotionPhoto='1' Camera:MotionPhotoVersion='1' Camera:MotionPhotoPresentationTimestampUs='-1' "
                + "Camera:MicroVideo='1' Camera:MicroVideoVersion='1' Camera:MicroVideoOffset='" + videoLength + "'>"
                + "<Container:Directory><rdf:Seq><rdf:li rdf:parseType='Resource'><Container:Item Item:Mime='image/jpeg' Item:Semantic='Primary' Item:Length='0' Item:Padding='0'/></rdf:li>"
                + "<rdf:li rdf:parseType='Resource'><Container:Item Item:Mime='video/mp4' Item:Semantic='MotionPhoto' Item:Length='" + videoLength + "' Item:Padding='0'/></rdf:li>"
                + "</rdf:Seq></Container:Directory></rdf:Description></rdf:RDF></x:xmpmeta>";
        byte[] metadata = xml.getBytes(StandardCharsets.UTF_8);
        try (var source = new RandomAccessFile(photo.content().path().toFile(), "r")) {
            source.seek(photo.offset());
            if (source.readUnsignedShort() != 0xffd8) throw new IOException("安卓动态照片导出需要 JPEG 预览图");
            var out = new DataOutputStream(output);
            out.writeShort(0xffd8);
            out.writeShort(0xffe1);
            out.writeShort(2 + XMP_PREFIX.length + metadata.length);
            out.write(XMP_PREFIX);
            out.write(metadata);
            long end = photo.offset() + photo.length();
            // Drop previous standard/extended XMP from this derivative to avoid contradictory directories.
            while (source.getFilePointer() < end) {
                long markerPosition = source.getFilePointer();
                if (source.readUnsignedByte() != 0xff) throw new IOException("JPEG 结构损坏");
                int marker;
                do { marker = source.readUnsignedByte(); } while (marker == 0xff);
                if (marker == 0xda || marker == 0xd9) {
                    source.seek(markerPosition);
                    copy(source, end - markerPosition, out);
                    break;
                }
                if (marker == 0x01 || marker >= 0xd0 && marker <= 0xd7) { out.writeShort(0xff00 | marker); continue; }
                int length = source.readUnsignedShort();
                if (length < 2 || source.getFilePointer() + length - 2 > end) throw new IOException("JPEG 元数据损坏");
                byte[] payload = new byte[length - 2];
                source.readFully(payload);
                if (marker == 0xe1 && new String(payload, 0, Math.min(payload.length, 40), StandardCharsets.US_ASCII).startsWith("http://ns.adobe.com/")) continue;
                out.writeShort(0xff00 | marker);
                out.writeShort(length);
                out.write(payload);
            }
        }
        Files.copy(video, output);
    }

    private static void copy(RandomAccessFile source, long length, OutputStream output) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        while (length > 0) {
            int n = source.read(buffer, 0, (int) Math.min(buffer.length, length));
            if (n < 0) throw new EOFException("JPEG 原件不完整");
            output.write(buffer, 0, n);
            length -= n;
        }
    }
}
