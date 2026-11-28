package com.wanyue.cloud.modules.drive.storage;

import com.wanyue.cloud.common.exception.BizException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

@Component
public class S3StorageProvider implements StorageProvider {
    private static final DateTimeFormatter AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final DateTimeFormatter DATE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final HttpClient httpClient;
    private final Path uploadTemp;
    private final Duration uploadTimeout;

    @Autowired
    public S3StorageProvider(
            @Value("${cloud.storage.worker-tmp:data/worker-tmp}") String workerTmp,
            @Value("${cloud.storage.s3-upload-timeout:PT30M}") Duration uploadTimeout) {
        this(HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .proxy(ProxySelector.getDefault())
            .connectTimeout(Duration.ofSeconds(15))
            .build(), Path.of(workerTmp).resolve("s3-uploads"), uploadTimeout);
    }

    S3StorageProvider(HttpClient httpClient, Path uploadTemp, Duration uploadTimeout) {
        if (uploadTimeout.isNegative() || uploadTimeout.isZero()) {
            throw new IllegalArgumentException("S3 upload timeout must be positive");
        }
        this.httpClient = httpClient;
        this.uploadTemp = uploadTemp;
        this.uploadTimeout = uploadTimeout;
    }

    @Override
    public String providerType() {
        return "S3";
    }

    /** Probes S3-compatible endpoint using AWS SigV4 signed HEAD request on the target bucket. */
    public Map<String, Object> testConnection(String endpoint, String bucket, String region, String accessKey, String secretKey) {
        if (endpoint == null || endpoint.isBlank() || bucket == null || bucket.isBlank()) {
            throw new BizException(HttpStatus.BAD_REQUEST, 400, "S3 Endpoint 与 Bucket 不能为空");
        }
        String safeRegion = (region == null || region.isBlank()) ? "us-east-1" : region.strip();
        URI uri = buildBucketUri(endpoint, bucket);
        long start = System.currentTimeMillis();
        try {
            HttpRequest req = buildSignedRequest("HEAD", uri, safeRegion, accessKey, secretKey, new byte[0], null, Duration.ofSeconds(20));
            HttpResponse<Void> resp = httpClient.send(req, HttpResponse.BodyHandlers.discarding());
            long elapsed = System.currentTimeMillis() - start;
            if (resp.statusCode() >= 200 && resp.statusCode() < 400) {
                return Map.of(
                        "ok", true,
                        "endpoint", uri.toString(),
                        "bucket", bucket.strip(),
                        "statusCode", resp.statusCode(),
                        "elapsedMs", elapsed);
            }
            String hint = switch (resp.statusCode()) {
                case 403 -> "（请检查 AccessKey/SecretKey、Region 或桶访问权限）";
                case 404 -> "（目标 Bucket 不存在或 Endpoint 路径不匹配）";
                default -> "";
            };
            throw new BizException(HttpStatus.BAD_GATEWAY, 16004,
                    "S3 存储连接测试失败，HTTP 状态码: " + resp.statusCode() + hint + " [" + uri + "]");
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            String reason = e.getMessage() != null && !e.getMessage().isBlank()
                    ? e.getMessage()
                    : e.getClass().getSimpleName();
            throw new BizException(HttpStatus.SERVICE_UNAVAILABLE, 16004,
                    "S3 存储不可达 (" + uri + "): " + reason + "（请检查服务器能否连通该 Endpoint/端口，或防火墙与代理设置）");
        }
    }

    /** SigV4 签名写入对象并计算 SHA-256 */
    public StoredObject putObject(
            String endpoint, String bucket, String region, String accessKey, String secretKey,
            String key, InputStream input, long maxBytes) throws IOException {
        MessageDigest sha;
        try { sha = MessageDigest.getInstance("SHA-256"); }
        catch (Exception e) { throw new IOException(e); }
        long total = 0L;
        URI uri = buildObjectUri(endpoint, bucket, key);
        String safeRegion = (region == null || region.isBlank()) ? "us-east-1" : region.strip();
        Files.createDirectories(uploadTemp);
        Path staged = Files.createTempFile(uploadTemp, "put-", ".bin");
        try {
            // SigV4 needs the final hash before sending headers, not an in-memory payload.
            // Retain the caller's input ownership; only the staging output is closed here.
            try (var out = Files.newOutputStream(staged)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = input.read(buf)) != -1) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new java.io.InterruptedIOException("S3 对象写入已中断");
                    }
                    if (n > maxBytes - total) {
                        throw new BizException(HttpStatus.PAYLOAD_TOO_LARGE, 413, "文件大小超出系统上传限制");
                    }
                    total += n;
                    sha.update(buf, 0, n);
                    out.write(buf, 0, n);
                }
            }
            String payloadHash = HexFormat.of().formatHex(sha.digest());
            // ofFile supplies the exact Content-Length and reads with bounded buffers.
            HttpRequest req = buildSignedRequest("PUT", uri, safeRegion, accessKey, secretKey,
                    payloadHash, HttpRequest.BodyPublishers.ofFile(staged), null, uploadTimeout);
            HttpResponse<Void> resp = httpClient.send(req, HttpResponse.BodyHandlers.discarding());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new IOException("S3 对象写入失败，HTTP 状态码: " + resp.statusCode());
            }
            return new StoredObject(key, total, payloadHash);
        } catch (BizException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new java.io.InterruptedIOException("S3 对象写入已中断");
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("S3 对象写入异常: " + e.getMessage(), e);
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    /** SigV4 签名读取对象到本地目标文件 */
    public void downloadToPath(
            String endpoint, String bucket, String region, String accessKey, String secretKey,
            String key, java.nio.file.Path target) throws IOException {
        URI uri = buildObjectUri(endpoint, bucket, key);
        String safeRegion = (region == null || region.isBlank()) ? "us-east-1" : region.strip();
        try {
            HttpRequest req = buildSignedRequest("GET", uri, safeRegion, accessKey, secretKey, new byte[0], null);
            HttpResponse<InputStream> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new IOException("S3 对象读取失败，HTTP 状态码: " + resp.statusCode());
            }
            java.nio.file.Files.createDirectories(target.getParent());
            try (InputStream body = resp.body()) {
                java.nio.file.Files.copy(body, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("S3 对象读取异常: " + e.getMessage(), e);
        }
    }

    /** SigV4 签名删除对象 */
    public void deleteObject(
            String endpoint, String bucket, String region, String accessKey, String secretKey, String key) throws IOException {
        URI uri = buildObjectUri(endpoint, bucket, key);
        String safeRegion = (region == null || region.isBlank()) ? "us-east-1" : region.strip();
        try {
            HttpRequest req = buildSignedRequest("DELETE", uri, safeRegion, accessKey, secretKey, new byte[0], null);
            var response = httpClient.send(req, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() != 404 && (response.statusCode() < 200 || response.statusCode() >= 300)) {
                throw new IOException("S3 对象删除失败，HTTP " + response.statusCode());
            }
        } catch (Exception e) {
            throw new IOException("S3 对象删除异常: " + e.getMessage(), e);
        }
    }

    private static URI normalizeEndpoint(String rawEndpoint) {
        String base = rawEndpoint.strip().replaceAll("/+$", "");
        if (!base.contains("://")) {
            base = "https://" + base;
        }
        URI parsed;
        try {
            parsed = URI.create(base);
        } catch (IllegalArgumentException e) {
            throw new BizException(HttpStatus.BAD_REQUEST, 400, "S3 Endpoint 格式无效: " + rawEndpoint);
        }
        if (parsed.getHost() == null || parsed.getHost().isBlank()) {
            throw new BizException(HttpStatus.BAD_REQUEST, 400, "S3 Endpoint 缺少有效主机名，请填写完整地址如 https://s3.example.com");
        }
        return parsed;
    }

    private static boolean requiresVirtualHostStyle(String host) {
        String lower = host.toLowerCase(Locale.ROOT);
        return lower.endsWith(".aliyuncs.com")
                || lower.endsWith(".myqcloud.com")
                || lower.endsWith(".myhuaweicloud.com")
                || lower.endsWith(".amazonaws.com");
    }

    private static URI buildBucketUri(String endpoint, String bucket) {
        URI base = normalizeEndpoint(endpoint);
        String cleanBucket = bucket.strip();
        String host = base.getHost();
        String authority = base.getPort() > 0 ? host + ":" + base.getPort() : host;
        if (host.toLowerCase(Locale.ROOT).startsWith(cleanBucket.toLowerCase(Locale.ROOT) + ".")) {
            return URI.create(base.getScheme() + "://" + authority + "/");
        }
        if (requiresVirtualHostStyle(host)) {
            return URI.create(base.getScheme() + "://" + cleanBucket + "." + authority + "/");
        }
        String basePath = base.getRawPath() == null ? "" : base.getRawPath().replaceAll("/+$", "");
        return URI.create(base.getScheme() + "://" + authority + basePath + "/" + cleanBucket);
    }

    private static URI buildObjectUri(String endpoint, String bucket, String key) {
        URI base = normalizeEndpoint(endpoint);
        String cleanBucket = bucket.strip();
        String cleanKey = java.util.Arrays.stream(key.replaceAll("^/+", "").split("/", -1))
                .map(S3StorageProvider::encode).collect(java.util.stream.Collectors.joining("/"));
        String host = base.getHost();
        String authority = base.getPort() > 0 ? host + ":" + base.getPort() : host;
        if (host.toLowerCase(Locale.ROOT).startsWith(cleanBucket.toLowerCase(Locale.ROOT) + ".")) {
            return URI.create(base.getScheme() + "://" + authority + "/" + cleanKey);
        }
        if (requiresVirtualHostStyle(host)) {
            return URI.create(base.getScheme() + "://" + cleanBucket + "." + authority + "/" + cleanKey);
        }
        String basePath = base.getRawPath() == null ? "" : base.getRawPath().replaceAll("/+$", "");
        return URI.create(base.getScheme() + "://" + authority + basePath + "/" + cleanBucket + "/" + cleanKey);
    }

    private static String canonicalHostHeader(URI uri) {
        int port = uri.getPort();
        boolean isDefaultPort = (port == 80 && "http".equalsIgnoreCase(uri.getScheme()))
                || (port == 443 && "https".equalsIgnoreCase(uri.getScheme()));
        return (port > 0 && !isDefaultPort) ? uri.getHost() + ":" + port : uri.getHost();
    }

    /** 生成标准 AWS SigV4 Query String 预签名 PUT URL（需求 7.6 S3 直传） */
    public String generatePresignedPutUrl(
            String endpoint, String bucket, String region, String accessKey, String secretKey,
            String key, int expiresSeconds) {
        return presignPut(endpoint, bucket, region, accessKey, secretKey, key, expiresSeconds, Map.of());
    }

    public String presignPart(StorageRouterService.StorageProfileMeta p, String secret, String key, String uploadId, int part, int ttl) {
        return presignPut(p.endpoint(), p.bucket(), p.region(), p.accessKey(), secret, key, ttl,
                Map.of("partNumber", Integer.toString(part), "uploadId", uploadId));
    }

    private String presignPut(String endpoint, String bucket, String region, String accessKey, String secretKey,
                              String key, int expiresSeconds, Map<String, String> extraQuery) {
        return presign("PUT", endpoint, bucket, region, accessKey, secretKey, key, expiresSeconds, extraQuery);
    }

    public String generatePresignedGetUrl(StorageRouterService.StorageProfileMeta profile, String secret, String key, int ttl) {
        return presign("GET", profile.endpoint(), profile.bucket(), profile.region(), profile.accessKey(), secret, key, ttl, Map.of());
    }

    private String presign(String method, String endpoint, String bucket, String region, String accessKey, String secretKey,
                           String key, int expiresSeconds, Map<String, String> extraQuery) {
        int safeExpires = Math.max(60, Math.min(3600, expiresSeconds));
        String safeRegion = (region == null || region.isBlank()) ? "us-east-1" : region.strip();
        URI uri = buildObjectUri(endpoint, bucket, key);
        try {
            ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
            String amzDate = AMZ_DATE.format(now);
            String dateStamp = DATE_STAMP.format(now);
            String host = canonicalHostHeader(uri);
            String canonicalUri = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
            String scope = dateStamp + "/" + safeRegion + "/s3/aws4_request";
            String credential = (accessKey == null ? "" : accessKey.strip()) + "/" + scope;
            String encodedCred = java.net.URLEncoder.encode(credential, StandardCharsets.UTF_8).replace("+", "%20");
            String canonicalQuery = "X-Amz-Algorithm=AWS4-HMAC-SHA256"
                    + "&X-Amz-Credential=" + encodedCred
                    + "&X-Amz-Date=" + amzDate
                    + "&X-Amz-Expires=" + safeExpires
                    + "&X-Amz-SignedHeaders=host";
            if (!extraQuery.isEmpty()) canonicalQuery += "&" + query(extraQuery);
            String canonicalRequest = method + "\n" + canonicalUri + "\n" + canonicalQuery + "\nhost:" + host + "\n\nhost\nUNSIGNED-PAYLOAD";
            String stringToSign = "AWS4-HMAC-SHA256\n" + amzDate + "\n" + scope + "\n"
                    + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));
            byte[] signingKey = getSignatureKey(secretKey == null ? "" : secretKey.strip(), dateStamp, safeRegion, "s3");
            String signature = HexFormat.of().formatHex(hmacSha256(signingKey, stringToSign));
            return uri + "?" + canonicalQuery + "&X-Amz-Signature=" + signature;
        } catch (Exception e) {
            throw new BizException(HttpStatus.INTERNAL_SERVER_ERROR, 500, "生成对象存储签名地址失败");
        }
    }

    /** Open only the requested bytes. Reject a storage endpoint that ignores Range. */
    public InputStream openRange(StorageRouterService.StorageProfileMeta profile, String secret, String key,
                                 long start, long end, long total) throws IOException {
        try {
            var req = buildSignedRequest("GET", buildObjectUri(profile.endpoint(), profile.bucket(), key),
                    profile.region() == null || profile.region().isBlank() ? "us-east-1" : profile.region(),
                    profile.accessKey(), secret, new byte[0], "bytes=" + start + "-" + end);
            var response = httpClient.send(req, HttpResponse.BodyHandlers.ofInputStream());
            String expected = "bytes " + start + "-" + end + "/" + total;
            if (response.statusCode() != 206 || !expected.equals(response.headers().firstValue("Content-Range").orElse(""))
                    || response.headers().firstValueAsLong("Content-Length").orElse(end - start + 1) != end - start + 1) {
                response.body().close();
                throw new IOException("对象存储未返回正确的视频范围，HTTP " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new java.io.InterruptedIOException("对象存储读取已中断");
        } catch (IOException e) { throw e; }
        catch (Exception e) { throw new IOException("对象存储范围读取失败", e); }
    }

    public HttpRequest buildSignedRequest(
            String method, URI uri, String region, String accessKey, String secretKey, byte[] payload, String rangeHeader) throws Exception {
        return buildSignedRequest(method, uri, region, accessKey, secretKey, payload, rangeHeader, Duration.ofSeconds(120));
    }

    public HttpRequest buildSignedRequest(
            String method, URI uri, String region, String accessKey, String secretKey, byte[] payload, String rangeHeader, Duration timeout) throws Exception {
        return buildSignedRequest(method, uri, region, accessKey, secretKey,
                sha256Hex(payload), HttpRequest.BodyPublishers.ofByteArray(payload), rangeHeader, timeout);
    }

    private HttpRequest buildSignedRequest(
            String method, URI uri, String region, String accessKey, String secretKey, String payloadHash,
            HttpRequest.BodyPublisher publisher, String rangeHeader, Duration timeout) throws Exception {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        String amzDate = AMZ_DATE.format(now);
        String dateStamp = DATE_STAMP.format(now);
        String host = canonicalHostHeader(uri);
        String canonicalUri = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        String canonicalHeaders = "host:" + host + "\nx-amz-content-sha256:" + payloadHash + "\nx-amz-date:" + amzDate + "\n";
        String signedHeaders = "host;x-amz-content-sha256;x-amz-date";
        String canonicalRequest = method + "\n" + canonicalUri + "\n" + (uri.getRawQuery() == null ? "" : uri.getRawQuery()) + "\n" + canonicalHeaders + "\n" + signedHeaders + "\n" + payloadHash;
        String scope = dateStamp + "/" + region + "/s3/aws4_request";
        String stringToSign = "AWS4-HMAC-SHA256\n" + amzDate + "\n" + scope + "\n" + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));
        byte[] signingKey = getSignatureKey(secretKey == null ? "" : secretKey.strip(), dateStamp, region, "s3");
        String signature = HexFormat.of().formatHex(hmacSha256(signingKey, stringToSign));
        String authHeader = "AWS4-HMAC-SHA256 Credential=" + (accessKey == null ? "" : accessKey.strip()) + "/" + scope
                + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;

        var builder = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("x-amz-date", amzDate)
                .header("x-amz-content-sha256", payloadHash)
                .header("Authorization", authHeader);
        if (rangeHeader != null && !rangeHeader.isBlank()) {
            builder.header("Range", rangeHeader);
        }
        return switch (method) {
            case "HEAD" -> builder.method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
            case "PUT" -> builder.PUT(publisher).build();
            case "POST" -> builder.POST(publisher).build();
            case "DELETE" -> builder.DELETE().build();
            default -> builder.GET().build();
        };
    }

    public record RemotePart(int number, long size, String etag) {}

    private static String query(Map<String, String> values) {
        return new java.util.TreeMap<>(values).entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue())).collect(java.util.stream.Collectors.joining("&"));
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20").replace("*", "%2A").replace("%7E", "~");
    }

    public static class S3ResponseException extends IOException {
        public final int status;
        public S3ResponseException(int status) { super("S3 请求失败，HTTP " + status); this.status = status; }
    }

    private HttpResponse<InputStream> multipartRequest(String method, StorageRouterService.StorageProfileMeta p,
            String secret, String key, Map<String, String> params, byte[] body) throws IOException {
        URI base = buildObjectUri(p.endpoint(), p.bucket(), key);
        URI uri = URI.create(base + (params.isEmpty() ? "" : "?" + query(params)));
        try {
            var request = buildSignedRequest(method, uri, p.region().isBlank() ? "us-east-1" : p.region(),
                    p.accessKey(), secret, body, null,
                    "POST".equals(method) && params.containsKey("uploadId") ? Duration.ofMinutes(10) : Duration.ofSeconds(15));
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                response.body().close();
                throw new S3ResponseException(response.statusCode());
            }
            return response;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new java.io.InterruptedIOException("S3 请求已中断");
        } catch (IOException e) { throw e; }
        catch (Exception e) { throw new IOException("S3 请求失败", e); }
    }

    private org.w3c.dom.Document xml(HttpResponse<InputStream> response) throws IOException {
        try (var input = response.body()) {
            var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            // Control-plane XML only. Never buffer a large or untrusted error response in heap.
            byte[] bytes = input.readNBytes(2 * 1024 * 1024 + 1);
            if (bytes.length > 2 * 1024 * 1024) throw new IOException("S3 响应过大");
            var document = factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(bytes));
            if (document.getElementsByTagName("Error").getLength() > 0) throw new IOException("S3 返回错误响应");
            return document;
        } catch (IOException e) { throw e; }
        catch (Exception e) { throw new IOException("S3 响应格式无效", e); }
    }

    private static String text(org.w3c.dom.Element element, String tag) throws IOException {
        var nodes = element.getElementsByTagName(tag);
        if (nodes.getLength() == 0) throw new IOException("S3 响应缺少 " + tag);
        return nodes.item(0).getTextContent();
    }

    public String initiateMultipart(StorageRouterService.StorageProfileMeta p, String secret, String key) throws IOException {
        return text(xml(multipartRequest("POST", p, secret, key, Map.of("uploads", ""), new byte[0])).getDocumentElement(), "UploadId");
    }

    public java.util.List<RemotePart> listParts(StorageRouterService.StorageProfileMeta p, String secret, String key, String uploadId) throws IOException {
        var parts = new java.util.ArrayList<RemotePart>();
        String marker = "0";
        do {
            var root = xml(multipartRequest("GET", p, secret, key,
                    Map.of("uploadId", uploadId, "part-number-marker", marker), new byte[0])).getDocumentElement();
            var nodes = root.getElementsByTagName("Part");
            for (int i = 0; i < nodes.getLength(); i++) {
                var part = (org.w3c.dom.Element) nodes.item(i);
                parts.add(new RemotePart(Integer.parseInt(text(part, "PartNumber")), Long.parseLong(text(part, "Size")), text(part, "ETag")));
            }
            if (!"true".equals(text(root, "IsTruncated"))) break;
            String next = text(root, "NextPartNumberMarker");
            if (Integer.parseInt(next) <= Integer.parseInt(marker) || parts.size() > 10000) throw new IOException("S3 分片分页无效");
            marker = next;
        } while (true);
        return parts;
    }

    public void completeMultipart(StorageRouterService.StorageProfileMeta p, String secret, String key, String uploadId,
                                  java.util.List<RemotePart> parts) throws IOException {
        StringBuilder body = new StringBuilder("<CompleteMultipartUpload>");
        for (var part : parts) body.append("<Part><PartNumber>").append(part.number()).append("</PartNumber><ETag>")
                .append(part.etag().replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;"))
                .append("</ETag></Part>");
        body.append("</CompleteMultipartUpload>");
        var doc = xml(multipartRequest("POST", p, secret, key, Map.of("uploadId", uploadId), body.toString().getBytes(StandardCharsets.UTF_8)));
        if (!"CompleteMultipartUploadResult".equals(doc.getDocumentElement().getTagName())) throw new IOException("S3 完成响应无效");
    }

    public void abortMultipart(StorageRouterService.StorageProfileMeta p, String secret, String key, String uploadId) throws IOException {
        try { multipartRequest("DELETE", p, secret, key, Map.of("uploadId", uploadId), new byte[0]).body().close(); }
        catch (S3ResponseException e) { if (e.status != 404) throw e; }
    }

    public long headObjectSize(StorageRouterService.StorageProfileMeta p, String secret, String key) throws IOException {
        var response = multipartRequest("HEAD", p, secret, key, Map.of(), new byte[0]);
        try (var input = response.body()) {
            return response.headers().firstValueAsLong("Content-Length").orElseThrow(() -> new IOException("S3 对象缺少长度"));
        }
    }

    private static String sha256Hex(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static byte[] hmacSha256(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] getSignatureKey(String key, String dateStamp, String region, String service) throws Exception {
        byte[] kDate = hmacSha256(("AWS4" + key).getBytes(StandardCharsets.UTF_8), dateStamp);
        byte[] kRegion = hmacSha256(kDate, region);
        byte[] kService = hmacSha256(kRegion, service);
        return hmacSha256(kService, "aws4_request");
    }

    @Override
    public StoredObject put(String key, InputStream input, long maxBytes) throws IOException {
        throw new UnsupportedOperationException("请通过带 StorageProfile 配置的 S3 实例执行写入");
    }

    @Override
    public InputStream get(String key) throws IOException {
        throw new UnsupportedOperationException("请通过带 StorageProfile 配置的 S3 实例执行读取");
    }

    @Override
    public InputStream getRange(String key, long start, long endInclusive) throws IOException {
        throw new UnsupportedOperationException("请通过带 StorageProfile 配置的 S3 实例执行范围读取");
    }

    @Override
    public boolean exists(String key) { return false; }

    @Override
    public void delete(String key) {}

    @Override
    public StorageObjectInfo stat(String key) throws IOException {
        throw new UnsupportedOperationException("请通过带 StorageProfile 配置的 S3 实例查询元数据");
    }
}
