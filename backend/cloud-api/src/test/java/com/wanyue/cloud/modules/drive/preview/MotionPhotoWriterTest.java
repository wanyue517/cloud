package com.wanyue.cloud.modules.drive.preview;

import com.wanyue.cloud.modules.drive.DriveModels;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class MotionPhotoWriterTest {
    @TempDir Path directory;

    @Test void exportedMotionPhotoRemainsAReadableJpegAndContainsExactVideoBytes() throws Exception {
        var photo = directory.resolve("photo.jpg");
        ImageIO.write(new BufferedImage(32, 24, BufferedImage.TYPE_INT_RGB), "jpg", photo.toFile());
        byte[] original = Files.readAllBytes(photo);
        byte[] movie = {0, 0, 0, 20, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 0, 0, 'i', 's', 'o', 'm'};
        var video = directory.resolve("video.mp4");
        Files.write(video, movie);
        var content = new DriveModels.Content(photo, original.length, "photo.jpg", "image/jpeg");
        var target = directory.resolve("photoMP.jpg");
        try (var out = Files.newOutputStream(target)) { MotionPhotoWriter.write(new LivePhotoService.Part(content, 0, content.size(), content.name(), content.mimeType()), video, out); }
        var image = ImageIO.read(target.toFile());
        assertEquals(32, image.getWidth());
        assertEquals(24, image.getHeight());
        var segment = MotionPhotoReader.find(target).orElseThrow();
        assertEquals(movie.length, segment.length());
        var extracted = directory.resolve("extracted.mp4");
        MotionPhotoReader.extract(target, segment, extracted);
        assertArrayEquals(movie, Files.readAllBytes(extracted));
        assertArrayEquals(original, Files.readAllBytes(photo));
        // Re-export must replace existing XMP and strip the previous embedded track.
        var twice = directory.resolve("twiceMP.jpg");
        var prior = new DriveModels.Content(target, Files.size(target), "prior.jpg", "image/jpeg");
        try (var out = Files.newOutputStream(twice)) { MotionPhotoWriter.write(new LivePhotoService.Part(prior, 0, segment.offset(), prior.name(), prior.mimeType()), video, out); }
        assertEquals(Files.size(target), Files.size(twice));
        assertEquals(20, MotionPhotoReader.find(twice).orElseThrow().length());
    }

    @Test void heicCannotBeMislabelledAsAnAndroidMotionJpeg() throws Exception {
        var photo = directory.resolve("photo.heic");
        var video = directory.resolve("clip.mp4");
        Files.write(photo, new byte[] {0, 0, 0, 20});
        Files.write(video, new byte[20]);
        var content = new DriveModels.Content(photo, 4, "photo.heic", "image/heic");
        assertThrows(IOException.class, () -> MotionPhotoWriter.write(new LivePhotoService.Part(content, 0, 4, content.name(), content.mimeType()), video, new ByteArrayOutputStream()));
    }
}
