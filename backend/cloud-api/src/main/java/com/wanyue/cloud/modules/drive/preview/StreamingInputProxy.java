package com.wanyue.cloud.modules.drive.preview;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.wanyue.cloud.modules.drive.DriveModels.MediaSource;
import com.wanyue.cloud.modules.drive.storage.StorageRouterService;
import org.springframework.http.HttpRange;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.UUID;
import java.util.Optional;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** Loopback-only, unguessable input URL with disk-backed read-through ranges.
 * FFmpeg may seek between audio/video packets; repeated seeks must not reopen R2.
 */
final class StreamingInputProxy implements AutoCloseable {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(StreamingInputProxy.class);
    private static final int CHUNK_BYTES = 4 * 1024 * 1024;
    private final HttpServer server;
    private final ExecutorService workers = Executors.newFixedThreadPool(4, r -> {
        var thread = new Thread(r, "video-input-range"); thread.setDaemon(true); return thread;
    });
    private final ConcurrentHashMap<Long, CompletableFuture<Path>> chunks = new ConcurrentHashMap<>();
    private final StorageRouterService storage;
    private final MediaSource source;
    private final Path cache;
    private final String path = "/input/" + UUID.randomUUID();

    StreamingInputProxy(StorageRouterService storage, MediaSource source, Path cache) throws IOException {
        this.storage = storage; this.source = source; this.cache = cache;
        Files.createDirectories(cache);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 8);
        server.setExecutor(workers);
        server.createContext(path, this::serve);
        server.start();
    }

    String url() { return "http://127.0.0.1:" + server.getAddress().getPort() + path; }

    Optional<byte[]> mp4Metadata() throws IOException {
        long offset = 0;
        for (int boxes = 0; boxes < 32 && offset <= source.size() - 8; boxes++) {
            byte[] header = read(offset, (int) Math.min(16, source.size() - offset));
            long size = Integer.toUnsignedLong(ByteBuffer.wrap(header).getInt());
            String type = new String(header, 4, 4, StandardCharsets.US_ASCII);
            if (offset == 0 && !java.util.Set.of("ftyp", "moov", "mdat", "free", "wide", "skip").contains(type)) return Optional.empty();
            int headerSize = 8;
            if (size == 1) { if (header.length < 16) return Optional.empty(); size = ByteBuffer.wrap(header, 8, 8).getLong(); headerSize = 16; }
            if (size == 0) size = source.size() - offset;
            if (size < headerSize || size > source.size() - offset) return Optional.empty();
            if ("moov".equals(type)) return size <= 8 * 1024 * 1024 ? Optional.of(read(offset, (int) size)) : Optional.empty();
            offset += size; // A tail moov is fetched with a seek, without downloading mdat.
        }
        return Optional.empty();
    }

    private byte[] read(long start, int length) throws IOException {
        byte[] bytes = new byte[length];
        int copied = 0;
        while (copied < length) {
            long position = start + copied;
            Path file = chunk(position / CHUNK_BYTES);
            try (var input = Files.newInputStream(file)) {
                input.skipNBytes(position % CHUNK_BYTES);
                int count = (int) Math.min(length - copied, Files.size(file) - position % CHUNK_BYTES);
                int read = input.readNBytes(bytes, copied, count);
                if (read != count) throw new EOFException();
                copied += read;
            }
        }
        return bytes;
    }

    private void serve(HttpExchange request) throws IOException {
        long sent = 0;
        try {
            if (!request.getRequestURI().getPath().equals(path)
                    || !(request.getRequestMethod().equals("GET") || request.getRequestMethod().equals("HEAD"))) {
                request.sendResponseHeaders(404, -1); return;
            }
            long start = 0, end = source.size() - 1;
            String range = request.getRequestHeaders().getFirst("Range");
            if (range != null) {
                try {
                    var ranges = HttpRange.parseRanges(range);
                    if (ranges.size() != 1) throw new IllegalArgumentException();
                    start = ranges.get(0).getRangeStart(source.size());
                    end = ranges.get(0).getRangeEnd(source.size());
                    if (start < 0 || start > end || start >= source.size()) throw new IllegalArgumentException();
                } catch (IllegalArgumentException invalid) {
                    request.getResponseHeaders().set("Content-Range", "bytes */" + source.size());
                    request.sendResponseHeaders(416, -1); return;
                }
            }
            request.getResponseHeaders().set("Accept-Ranges", "bytes");
            request.getResponseHeaders().set("Connection", "close");
            log.debug("Video input version={} range={}-{} method={}", source.versionId(), start, end, request.getRequestMethod());
            request.getResponseHeaders().set("Content-Type", "application/octet-stream");
            request.getResponseHeaders().set("Content-Length", Long.toString(Math.max(0, end - start + 1)));
            if (range != null) request.getResponseHeaders().set("Content-Range", "bytes " + start + "-" + end + "/" + source.size());
            if (request.getRequestMethod().equals("HEAD") || source.size() == 0) {
                request.sendResponseHeaders(range == null ? 200 : 206, -1); return;
            }
            request.sendResponseHeaders(range == null ? 200 : 206, end - start + 1);
            try (var output = request.getResponseBody()) {
                byte[] buffer = new byte[65536];
                for (long position = start; position <= end;) {
                    long index = position / CHUNK_BYTES;
                    Path file = chunk(index);
                    try (var input = Files.newInputStream(file)) {
                        input.skipNBytes(position % CHUNK_BYTES);
                        long remaining = Math.min(end - position + 1, Files.size(file) - position % CHUNK_BYTES);
                        while (remaining > 0) {
                            int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                            if (read < 0) throw new EOFException();
                            output.write(buffer, 0, read);
                            output.flush(); // Notice cancelled FFmpeg seeks before fetching another remote chunk.
                            remaining -= read;
                            position += read;
                            sent += read;
                        }
                    }
                }
            }
        } catch (Exception disconnected) {
            log.debug("Video input request ended version={} reason={}", source.versionId(), disconnected.toString());
        } finally {
            log.debug("Video input response finished version={} bytes={}", source.versionId(), sent);
            request.close();
        }
    }

    private Path chunk(long index) throws IOException {
        long start = index * CHUNK_BYTES;
        long end = Math.min(source.size() - 1, start + CHUNK_BYTES - 1);
        Path target = cache.resolve("chunk-" + index + ".bin");
        if (Files.isRegularFile(target) && Files.size(target) == end - start + 1) return target;
        var pending = new CompletableFuture<Path>();
        var existing = chunks.putIfAbsent(index, pending);
        if (existing != null) {
            try { return existing.get(60, TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException(interrupted); }
            catch (Exception failed) { throw new IOException("视频分块读取失败", failed); }
        }
        Path temporary = Files.createTempFile(cache, "input-", ".tmp");
        try (var input = storage.openRange(source, start, end)) {
            long copied = Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
            if (copied != end - start + 1) throw new EOFException("视频分块不完整");
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            pending.complete(target);
            return target;
        } catch (Exception failed) {
            pending.completeExceptionally(failed);
            throw failed instanceof IOException io ? io : new IOException(failed);
        } finally {
            Files.deleteIfExists(temporary);
            chunks.remove(index, pending);
        }
    }

    @Override public void close() {
        server.stop(0);
        workers.shutdownNow();
    }
}
