package com.wanyue.cloud.modules.drive.preview;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class MotionPhotoReaderTest {
    @TempDir Path dir;
    private static final byte[] VIDEO = new byte[] {0,0,0,20, 'f','t','y','p', 'i','s','o','m',0,0,0,0, 'i','s','o','m'};

    private Path jpeg(String xml, byte[] video) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeShort(0xffd8);
            if (xml != null) {
                byte[] xmp = ("http://ns.adobe.com/xap/1.0/\0" + xml).getBytes(StandardCharsets.UTF_8);
                out.writeShort(0xffe1);
                out.writeShort(xmp.length + 2);
                out.write(xmp);
            }
            out.writeShort(0xffd9);
            out.write(video);
        }
        var path = Files.createTempFile(dir, "photo", ".jpg");
        Files.write(path, bytes.toByteArray());
        return path;
    }

    @Test void extractsLegacyVideoExactlyAndLeavesOriginalUntouched() throws Exception {
        var path = jpeg("<x xmlns:g='http://ns.google.com/photos/1.0/camera/' g:MicroVideoOffset='20'/>", VIDEO);
        byte[] original = Files.readAllBytes(path);
        var segment = MotionPhotoReader.find(path).orElseThrow();
        assertEquals(Files.size(path) - VIDEO.length, segment.offset());
        var target = dir.resolve("clip.mp4");
        MotionPhotoReader.extract(path, segment, target);
        assertArrayEquals(VIDEO, Files.readAllBytes(target));
        assertArrayEquals(original, Files.readAllBytes(path));
    }

    @Test void supportsContainerItemWithArbitraryNamespacePrefix() throws Exception {
        var path = jpeg("<x xmlns:r='http://ns.google.com/photos/1.0/container/item/'><item r:Semantic='MotionPhoto' r:Mime='video/mp4' r:Length='20'/></x>", VIDEO);
        assertEquals(20, MotionPhotoReader.find(path).orElseThrow().length());
    }

    @Test void ordinaryJpegAndUntrustedMetadataCannotBecomeLivePhotos() throws Exception {
        assertTrue(MotionPhotoReader.find(jpeg(null, VIDEO)).isEmpty());
        for (String offset : new String[] {"-20", "0", "9999999999", "bogus", "19"}) {
            assertTrue(MotionPhotoReader.find(jpeg("<x xmlns:g='http://ns.google.com/photos/1.0/camera/' g:MicroVideoOffset='" + offset + "'/>", VIDEO)).isEmpty());
        }
        assertTrue(MotionPhotoReader.find(jpeg("<x xmlns:g='urn:wrong' g:MicroVideoOffset='20'/>", VIDEO)).isEmpty());
        assertTrue(MotionPhotoReader.find(jpeg("<!DOCTYPE x [<!ENTITY a SYSTEM 'file:///etc/passwd'>]><x>&a;</x>", VIDEO)).isEmpty());
    }

    @Test void rejectsInvalidMp4AndTruncatedJpeg() throws Exception {
        assertTrue(MotionPhotoReader.find(jpeg("<x xmlns:g='http://ns.google.com/photos/1.0/camera/' g:MicroVideoOffset='20'/>", new byte[20])).isEmpty());
        var truncated = dir.resolve("bad.jpg");
        Files.write(truncated, new byte[] {(byte)0xff, (byte)0xd8, (byte)0xff, (byte)0xe1, 0});
        assertTrue(MotionPhotoReader.find(truncated).isEmpty());
    }

    @Test void disabledMotionFlagCannotResurrectAResidualEmbeddedVideo() throws Exception {
        var path = jpeg("<x xmlns:g='http://ns.google.com/photos/1.0/camera/' g:MotionPhoto='0' g:MicroVideoOffset='20'/>", VIDEO);
        assertTrue(MotionPhotoReader.find(path).isEmpty());
    }
}
