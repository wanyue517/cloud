package com.wanyue.cloud.modules.drive.storage;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import static org.junit.jupiter.api.Assertions.*;

class S3MultipartTest {
    @TempDir Path temp;
    HttpServer server;
    S3StorageProvider provider;
    StorageRouterService.StorageProfileMeta profile;
    AtomicReference<Throwable> failure = new AtomicReference<>();
    AtomicBoolean embeddedError = new AtomicBoolean();

    @BeforeEach void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        provider = new S3StorageProvider(HttpClient.newBuilder().proxy(new ProxySelector() {
            public List<Proxy> select(URI uri) { return List.of(Proxy.NO_PROXY); }
            public void connectFailed(URI uri, SocketAddress address, java.io.IOException error) { }
        }).build(), temp, Duration.ofSeconds(10));
        profile = new StorageRouterService.StorageProfileMeta(2, "S3", "", "http://127.0.0.1:" + server.getAddress().getPort(), "bucket", "auto", "access", "", null);
        server.createContext("/bucket/object", exchange -> {
            try {
                String rawQuery = exchange.getRequestURI().getRawQuery();
                String hash = exchange.getRequestHeaders().getFirst("x-amz-content-sha256");
                String date = exchange.getRequestHeaders().getFirst("x-amz-date");
                String headers = "host:" + exchange.getRequestHeaders().getFirst("Host") + "\nx-amz-content-sha256:" + hash + "\nx-amz-date:" + date + "\n";
                String canonical = exchange.getRequestMethod() + "\n/bucket/object\n" + (rawQuery == null ? "" : rawQuery) + "\n" + headers + "\nhost;x-amz-content-sha256;x-amz-date\n" + hash;
                String scope = date.substring(0, 8) + "/auto/s3/aws4_request";
                String expected = signature(date, scope, canonical);
                assertTrue(exchange.getRequestHeaders().getFirst("Authorization").endsWith("Signature=" + expected));
                String body = "";
                if (exchange.getRequestMethod().equals("POST") && "uploads=".equals(rawQuery)) body = "<InitiateMultipartUploadResult><UploadId>id+/=</UploadId></InitiateMultipartUploadResult>";
                else if (exchange.getRequestMethod().equals("GET")) {
                    assertTrue(rawQuery.contains("uploadId=id%2B%2F%3D"));
                    body = "<ListPartsResult><IsTruncated>false</IsTruncated><Part><PartNumber>1</PartNumber><Size>6</Size><ETag>&quot;etag&quot;</ETag></Part></ListPartsResult>";
                } else if (exchange.getRequestMethod().equals("POST")) {
                    String xml = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    assertTrue(xml.contains("&quot;etag&quot;"));
                    body = embeddedError.get() ? "<Error><Code>InvalidPart</Code></Error>" : "<CompleteMultipartUploadResult><ETag>done</ETag></CompleteMultipartUploadResult>";
                } else if (exchange.getRequestMethod().equals("HEAD")) {
                    exchange.getResponseHeaders().set("Content-Length", "6");
                    exchange.sendResponseHeaders(200, -1);
                    return;
                }
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length == 0 ? -1 : bytes.length);
                if (bytes.length > 0) exchange.getResponseBody().write(bytes);
            } catch (Throwable e) {
                failure.set(e);
                try { exchange.sendResponseHeaders(500, -1); } catch (Exception ignored) { }
            } finally { exchange.close(); }
        });
        server.start();
    }

    @AfterEach void stop() { server.stop(0); }

    private static byte[] hmac(byte[] key, String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String signature(String date, String scope, String canonical) throws Exception {
        byte[] key = hmac("AWS4secret".getBytes(StandardCharsets.UTF_8), date.substring(0, 8));
        key = hmac(hmac(hmac(key, "auto"), "s3"), "aws4_request");
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        return HexFormat.of().formatHex(hmac(key, "AWS4-HMAC-SHA256\n" + date + "\n" + scope + "\n" + hash));
    }

    @Test void signsControlRequestsAndCompletesUsingStoragePartEtags() throws Exception {
        assertEquals("id+/=", provider.initiateMultipart(profile, "secret", "object"));
        var parts = provider.listParts(profile, "secret", "object", "id+/=");
        assertEquals(6, parts.get(0).size());
        provider.completeMultipart(profile, "secret", "object", "id+/=", parts);
        assertEquals(6, provider.headObjectSize(profile, "secret", "object"));
        provider.abortMultipart(profile, "secret", "object", "id+/=");
        assertNull(failure.get());
    }

    @Test void rejectsErrorEmbeddedInHttp200CompletionResponse() throws Exception {
        embeddedError.set(true);
        assertThrows(java.io.IOException.class, () -> provider.completeMultipart(profile, "secret", "object", "id+/=",
                List.of(new S3StorageProvider.RemotePart(1, 6, "\"etag\""))));
        assertNull(failure.get());
    }

    @Test void presignedPartBindsUploadIdPartNumberAndExpiry() throws Exception {
        URI uri = URI.create(provider.presignPart(profile, "secret", "object", "id+/=", 7, 900));
        var query = new TreeMap<String, String>();
        for (String pair : uri.getRawQuery().split("&")) {
            int at = pair.indexOf('='); query.put(pair.substring(0, at), pair.substring(at + 1));
        }
        String actual = query.remove("X-Amz-Signature");
        assertEquals("7", query.get("partNumber"));
        assertEquals("id%2B%2F%3D", query.get("uploadId"));
        assertEquals("900", query.get("X-Amz-Expires"));
        String date = query.get("X-Amz-Date");
        String canonicalQuery = query.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(java.util.stream.Collectors.joining("&"));
        String canonical = "PUT\n/bucket/object\n" + canonicalQuery + "\nhost:" + uri.getAuthority() + "\n\nhost\nUNSIGNED-PAYLOAD";
        assertEquals(signature(date, date.substring(0, 8) + "/auto/s3/aws4_request", canonical), actual);
    }
}
