package com.wanyue.cloud.modules.drive.storage;

import com.sun.net.httpserver.HttpServer;
import com.wanyue.cloud.common.exception.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.atomic.*;

import static org.junit.jupiter.api.Assertions.*;

class S3StreamingUploadTest {
    @TempDir Path temp;
    HttpServer server;
    String endpoint;
    AtomicLong received = new AtomicLong();
    AtomicReference<Throwable> serverFailure = new AtomicReference<>();
    AtomicInteger status = new AtomicInteger(200);
    AtomicLong delayMillis = new AtomicLong();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        endpoint = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/bucket/object", exchange -> {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                long count = 0;
                byte[] buffer = new byte[64 * 1024];
                try (var in = exchange.getRequestBody()) {
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        count += n;
                        digest.update(buffer, 0, n);
                    }
                }
                received.set(count);
                String hash = HexFormat.of().formatHex(digest.digest());
                assertEquals(Long.toString(count), exchange.getRequestHeaders().getFirst("Content-Length"));
                assertEquals(hash, exchange.getRequestHeaders().getFirst("x-amz-content-sha256"));
                String date = exchange.getRequestHeaders().getFirst("x-amz-date");
                String host = exchange.getRequestHeaders().getFirst("Host");
                String signed = "host;x-amz-content-sha256;x-amz-date";
                String canonical = "PUT\n/bucket/object\n\nhost:" + host + "\nx-amz-content-sha256:" + hash
                        + "\nx-amz-date:" + date + "\n\n" + signed + "\n" + hash;
                String scope = date.substring(0, 8) + "/us-east-1/s3/aws4_request";
                String toSign = "AWS4-HMAC-SHA256\n" + date + "\n" + scope + "\n"
                        + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(canonical.getBytes(StandardCharsets.UTF_8)));
                byte[] key = hmac("AWS4secret".getBytes(StandardCharsets.UTF_8), date.substring(0, 8));
                key = hmac(hmac(hmac(key, "us-east-1"), "s3"), "aws4_request");
                assertEquals("AWS4-HMAC-SHA256 Credential=access/" + scope + ", SignedHeaders=" + signed
                                + ", Signature=" + HexFormat.of().formatHex(hmac(key, toSign)),
                        exchange.getRequestHeaders().getFirst("Authorization"));
                if (delayMillis.get() > 0) Thread.sleep(delayMillis.get());
                exchange.sendResponseHeaders(status.get(), -1);
            } catch (Throwable e) {
                serverFailure.set(e);
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    @AfterEach void stop() { server.stop(0); }

    private static byte[] hmac(byte[] key, String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }

    private S3StorageProvider provider(Duration timeout) {
        var client = HttpClient.newBuilder().proxy(new ProxySelector() {
            @Override public java.util.List<Proxy> select(URI uri) { return java.util.List.of(Proxy.NO_PROXY); }
            @Override public void connectFailed(URI uri, SocketAddress address, IOException error) { }
        }).build();
        return new S3StorageProvider(client, temp, timeout);
    }

    private StorageProvider.StoredObject put(S3StorageProvider provider, InputStream input, long max) throws IOException {
        return provider.putObject(endpoint, "bucket", "", "access", "secret", "object", input, max);
    }

    private void assertClean() throws IOException {
        try (var paths = Files.list(temp)) { assertEquals(0, paths.count()); }
    }

    @Test
    void uploadsFileLargerThanHeapWithValidSignatureAndLength() throws Exception {
        long size = 512L * 1024 * 1024;
        var expected = MessageDigest.getInstance("SHA-256");
        InputStream generated = new InputStream() {
            long remaining = size;
            @Override public int read() { throw new AssertionError("Use buffered reads"); }
            @Override public int read(byte[] bytes, int offset, int length) {
                if (remaining == 0) return -1;
                int n = (int) Math.min(remaining, length);
                java.util.Arrays.fill(bytes, offset, offset + n, (byte) 37);
                expected.update(bytes, offset, n);
                remaining -= n;
                return n;
            }
        };
        var stored = put(provider(Duration.ofMinutes(5)), generated, size);
        assertEquals(size, stored.size());
        assertEquals(HexFormat.of().formatHex(expected.digest()), stored.sha256());
        assertEquals(size, received.get());
        assertNull(serverFailure.get());
        assertClean();
    }

    @Test
    void cleansStagingOnLimitAndInputFailure() throws Exception {
        var provider = provider(Duration.ofSeconds(5));
        assertEquals(413, assertThrows(BizException.class,
                () -> put(provider, new ByteArrayInputStream(new byte[100]), 99)).getCode());
        assertClean();
        assertThrows(IOException.class, () -> put(provider, new InputStream() {
            @Override public int read() throws IOException { throw new IOException("broken input"); }
        }, 100));
        assertClean();
        assertEquals(0, received.get());
    }

    @Test
    void cleansStagingOnHttpFailureAndAllowsRetry() throws Exception {
        status.set(503);
        var provider = provider(Duration.ofSeconds(5));
        assertThrows(IOException.class, () -> put(provider, new ByteArrayInputStream(new byte[100]), 100));
        assertClean();
        status.set(200);
        assertEquals(100, put(provider, new ByteArrayInputStream(new byte[100]), 100).size());
        assertNull(serverFailure.get());
        assertClean();
    }

    @Test
    void uploadTimeoutIsBoundedAndCleansStaging() throws Exception {
        delayMillis.set(1000);
        assertThrows(HttpTimeoutException.class, () -> put(provider(Duration.ofMillis(250)),
                new ByteArrayInputStream(new byte[100]), 100));
        assertClean();
    }
}
