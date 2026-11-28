package com.wanyue.cloud.modules.drive.preview;

import com.wanyue.cloud.modules.drive.DriveModels;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ThumbnailMemorySafetyTest {
    @TempDir Path temp;

    @Test
    void videoThumbnailPreparationDoesNotBlockTheRequestAndReusesTheCompletedCache() throws Exception {
        var entered = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        Path cached = temp.resolve("thumbs/77_480.jpg");
        var service = new ThumbnailService(temp.toString(), "ffmpeg") {
            @Override public Optional<Path> getOrGenerateVideo(long version, String name, String input, int width) {
                entered.countDown();
                try { assertTrue(finish.await(5, TimeUnit.SECONDS)); Files.write(cached, new byte[]{1,2,3}); }
                catch (Exception e) { throw new RuntimeException(e); }
                return Optional.of(cached);
            }
        };
        try {
            assertTimeoutPreemptively(java.time.Duration.ofSeconds(1), () ->
                    assertTrue(service.getOrRequestVideo(77, "source.mp4", "https://storage.invalid/video", 320).isEmpty()));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            finish.countDown();
            for (int i = 0; i < 100 && !Files.isRegularFile(cached); i++) Thread.sleep(10);
            assertEquals(cached, service.getOrRequestVideo(77, "source.mp4", "https://storage.invalid/video", 320).orElseThrow());
        } finally { finish.countDown(); service.close(); }
    }

    @Test
    void rejectsOversizedHeaderWithoutDecodingPixels() throws Exception {
        ImageReader reader = mock(ImageReader.class);
        when(reader.getWidth(0)).thenReturn(8000);
        when(reader.getHeight(0)).thenReturn(6000);
        assertNull(ThumbnailService.readSampledImage(reader, 480));
        verify(reader, never()).read(anyInt(), any());
    }

    @Test
    void requestsSubsamplingAndBoundsExtremePortraits() throws Exception {
        ImageReader reader = mock(ImageReader.class);
        when(reader.getWidth(0)).thenReturn(100);
        when(reader.getHeight(0)).thenReturn(100000);
        var param = new ImageReadParam();
        when(reader.getDefaultReadParam()).thenReturn(param);
        ThumbnailService.readSampledImage(reader, 480);
        assertTrue(param.getSourceXSubsampling() >= 104);
        verify(reader).read(0, param);
    }

    @Test
    void generatesEightCold12MegapixelJpegsConcurrently() throws Exception {
        Path original = temp.resolve("iphone.jpg");
        BufferedImage image = new BufferedImage(4032, 3024, BufferedImage.TYPE_INT_RGB);
        assertTrue(ImageIO.write(image, "jpeg", original.toFile()));
        image.flush();
        var source = new DriveModels.Content(original, Files.size(original), "iphone.jpg", "image/jpeg", "test");
        var service = new ThumbnailService(temp.resolve("preview").toString(), "ffmpeg");
        var pool = Executors.newFixedThreadPool(8);
        var start = new CountDownLatch(1);
        try {
            var results = new ArrayList<Future<Optional<Path>>>();
            for (long id = 1; id <= 8; id++) {
                long version = id;
                results.add(pool.submit(() -> {
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    return service.getOrGenerate(version, source, 480);
                }));
            }
            start.countDown();
            for (var result : results) {
                Path path = result.get(15, TimeUnit.SECONDS).orElseThrow();
                BufferedImage thumb = ImageIO.read(path.toFile());
                assertEquals(480, thumb.getWidth());
                assertEquals(360, thumb.getHeight());
                thumb.flush();
            }
            Files.delete(original);
            assertTrue(service.getOrGenerate(1, source, 480).isPresent(), "Cache hits must not decode again");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void boundsGenerationAndCoalescesSameKey() throws Exception {
        var running = new AtomicInteger();
        var peak = new AtomicInteger();
        var calls = new AtomicInteger();
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var service = new ThumbnailService(temp.toString(), "ffmpeg") {
            @Override Optional<Path> generateImageThumb(Path src, Path target, int width) {
                calls.incrementAndGet();
                peak.accumulateAndGet(running.incrementAndGet(), Math::max);
                entered.countDown();
                try {
                    assertTrue(release.await(5, TimeUnit.SECONDS));
                    Files.writeString(target, "cached");
                    return Optional.of(target);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    running.decrementAndGet();
                }
            }
        };
        var source = new DriveModels.Content(temp.resolve("unused"), 1, "image.jpg", "image/jpeg", "test");
        var pool = Executors.newFixedThreadPool(8);
        try {
            var results = new ArrayList<Future<Optional<Path>>>();
            for (int i = 0; i < 8; i++) {
                long key = i % 4;
                results.add(pool.submit(() -> service.getOrGenerate(key, source, 480)));
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertEquals(2, peak.get());
            release.countDown();
            for (var result : results) assertTrue(result.get(10, TimeUnit.SECONDS).isPresent());
            assertEquals(4, calls.get());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void releasesGenerationSlotsAfterFailures() throws Exception {
        var attempts = new AtomicInteger();
        var service = new ThumbnailService(temp.toString(), "ffmpeg") {
            @Override Optional<Path> generateImageThumb(Path src, Path target, int width) {
                if (attempts.incrementAndGet() <= 3) throw new IllegalStateException("failed decoder");
                return Optional.empty();
            }
        };
        var source = new DriveModels.Content(temp.resolve("unused"), 1, "image.jpg", "image/jpeg", "test");
        for (int i = 0; i < 3; i++) {
            assertThrows(IllegalStateException.class, () -> service.getOrGenerate(1, source, 480));
        }
        assertTrue(service.getOrGenerate(1, source, 480).isEmpty());
    }

    @Test
    void queuedGenerationTimesOutAndCanRetry() throws Exception {
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var service = new ThumbnailService(temp.toString(), "ffmpeg") {
            @Override Optional<Path> generateImageThumb(Path src, Path target, int width) {
                entered.countDown();
                try {
                    assertTrue(release.await(15, TimeUnit.SECONDS));
                    return Optional.empty();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
        };
        var source = new DriveModels.Content(temp.resolve("unused"), 1, "image.jpg", "image/jpeg", "test");
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> service.getOrGenerate(1, source, 480));
            var second = pool.submit(() -> service.getOrGenerate(2, source, 480));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var error = assertThrows(com.wanyue.cloud.common.exception.BizException.class,
                    () -> service.getOrGenerate(3, source, 480));
            assertEquals(503, error.getCode());
            release.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            assertTrue(service.getOrGenerate(3, source, 480).isEmpty());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }
}
