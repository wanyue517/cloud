package com.wanyue.cloud.modules.drive.preview;

import org.w3c.dom.Element;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Optional;

/** Reads only JPEG APP1 XMP; never searches arbitrary image/video bytes for an MP4 signature. */
public final class MotionPhotoReader {
    private static final String CAMERA = "http://ns.google.com/photos/1.0/camera/";
    private static final String ITEM = "http://ns.google.com/photos/1.0/container/item/";
    private MotionPhotoReader() {}

    public record Segment(long offset, long length) {}

    public static Optional<Segment> find(Path path) throws IOException {
        try (var file = new RandomAccessFile(path.toFile(), "r")) {
            if (file.length() < 4 || file.readUnsignedShort() != 0xffd8) return Optional.empty();
            while (file.getFilePointer() < Math.min(file.length(), 1024 * 1024)) {
                if (file.readUnsignedByte() != 0xff) return Optional.empty();
                int marker;
                do { marker = file.readUnsignedByte(); } while (marker == 0xff);
                if (marker == 0xda || marker == 0xd9) break;
                if (marker == 0x01 || marker >= 0xd0 && marker <= 0xd7) continue;
                int length = file.readUnsignedShort() - 2;
                if (length < 0 || file.getFilePointer() + length > file.length()) return Optional.empty();
                byte[] payload = new byte[length];
                file.readFully(payload);
                if (marker != 0xe1) continue;
                byte[] prefix = "http://ns.adobe.com/xap/1.0/\0".getBytes(StandardCharsets.US_ASCII);
                if (payload.length <= prefix.length || !java.util.Arrays.equals(prefix, java.util.Arrays.copyOf(payload, prefix.length))) continue;
                long trailing = videoLength(payload, prefix.length);
                if (trailing <= 0 || trailing > 64L * 1024 * 1024 || trailing > file.length() - file.getFilePointer()) continue;
                long offset = file.length() - trailing;
                long resume = file.getFilePointer();
                file.seek(offset);
                long boxLength = Integer.toUnsignedLong(file.readInt());
                int type = file.readInt();
                file.seek(resume);
                if (boxLength >= 16 && boxLength <= trailing && type == 0x66747970) return Optional.of(new Segment(offset, trailing));
            }
        } catch (EOFException ignored) {
            // Truncated JPEGs remain ordinary images.
        }
        return Optional.empty();
    }

    private static long videoLength(byte[] payload, int start) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler());
            var doc = builder.parse(new ByteArrayInputStream(payload, start, payload.length - start));
            var elements = doc.getElementsByTagName("*");
            long legacy = 0;
            long modern = 0;
            for (int i = 0; i < elements.getLength(); i++) {
                var e = (Element) elements.item(i);
                if (e.hasAttributeNS(CAMERA, "MotionPhoto") && !"1".equals(e.getAttributeNS(CAMERA, "MotionPhoto"))) return 0;
                if (e.hasAttributeNS(CAMERA, "MicroVideoOffset")) legacy = Long.parseLong(e.getAttributeNS(CAMERA, "MicroVideoOffset"));
                if ("MotionPhoto".equals(e.getAttributeNS(ITEM, "Semantic")) && "video/mp4".equals(e.getAttributeNS(ITEM, "Mime"))) {
                    // Only support a final video item, without trailing padding.
                    String padding = e.getAttributeNS(ITEM, "Padding");
                    if (!padding.isEmpty() && !"0".equals(padding)) return 0;
                    modern = Long.parseLong(e.getAttributeNS(ITEM, "Length"));
                }
            }
            return modern > 0 ? modern : legacy;
        } catch (Exception ignored) { return 0; }
    }

    public static void extract(Path source, Segment segment, Path target) throws IOException {
        try (var input = new RandomAccessFile(source.toFile(), "r"); var output = Files.newOutputStream(target)) {
            input.seek(segment.offset());
            byte[] buffer = new byte[64 * 1024];
            long remaining = segment.length();
            while (remaining > 0) {
                int n = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (n < 0) throw new EOFException("动态照片视频不完整");
                output.write(buffer, 0, n);
                remaining -= n;
            }
        }
    }
}
