package com.wanyue.cloud.modules.drive.storage;

import com.wanyue.cloud.common.exception.BizException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;

@Component
public class S3StorageProvider implements StorageProvider {
    private static final DateTimeFormatter AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final DateTimeFormatter DATE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    @Override
    public String providerType() {
        return "S3";
    }

    /** Probes S3-compatible endpoint using AWS SigV4 signed HEAD request on the target bucket. */
    public Map<String, Object> testConnection(String endpoint, String bucket, String region, String accessKey, String secretKey) {
        if (endpoint == null || endpoint.isBlank() || bucket == null || bucket.isBlank()) {
            throw new BizException(HttpStatus.BAD_REQUEST, 400, "S3 Endpoint 与 Bucket 不能为空");
        }
        String base = endpoint.strip().replaceAll("/+$", "");
        String safeRegion = (region == null || region.isBlank()) ? "us-east-1" : region.strip();
        URI uri = URI.create(base + "/" + bucket.strip());
        long start = System.currentTimeMillis();
        try {
            HttpRequest req = buildSignedRequest("HEAD", uri, safeRegion, accessKey, secretKey, new byte[0], null);
            HttpResponse<Void> resp = httpClient.send(req, HttpResponse.BodyHandlers.discarding());
            long elapsed = System.currentTimeMillis() - start;
            if (resp.statusCode() >= 200 && resp.statusCode() < 400) {
                return Map.of("ok", true, "endpoint", base, "bucket", bucket.strip(), "statusCode", resp.statusCode(), "elapsedMs", elapsed);
            }
            throw new BizException(HttpStatus.BAD_GATEWAY, 16004, "S3 存储连接测试失败，HTTP 状态码: " + resp.statusCode());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(HttpStatus.SERVICE_UNAVAILABLE, 16004, "S3 存储不可达: " + e.getMessage());
        }
    }

    /** SigV4 签名写入对象并计算 SHA-256 */
    public StoredObject putObject(
            String endpoint, String bucket, String region, String accessKey, String secretKey,
            String key, InputStream input, long maxBytes) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        MessageDigest sha;
        try { sha = MessageDigest.getInstance("SHA-256"); }
        catch (Exception e) { throw new IOException(e); }
        long total = 0L;
        byte[] buf = new byte[8192];
        int n;
        while ((n = input.read(buf)) >= 0) {
            total += n;
            if (total > maxBytes) {
                throw new BizException(HttpStatus.PAYLOAD_TOO_LARGE, 413, "文件大小超出系统上传限制");
            }
            sha.update(buf, 0, n);
            out.write(buf, 0, n);
        }
        byte[] payload = out.toByteArray();
        URI uri = buildObjectUri(endpoint, bucket, key);
        String safeRegion = (region == null || region.isBlank()) ? "us-east-1" : region.strip();
        try {
            HttpRequest req = buildSignedRequest("PUT", uri, safeRegion, accessKey, secretKey, payload, null);
            HttpResponse<Void> resp = httpClient.send(req, HttpResponse.BodyHandlers.discarding());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new IOException("S3 对象写入失败，HTTP 状态码: " + resp.statusCode());
            }
            return new StoredObject(key, total, HexFormat.of().formatHex(sha.digest()));
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("S3 对象写入异常: " + e.getMessage(), e);
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
            httpClient.send(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            throw new IOException("S3 对象删除异常: " + e.getMessage(), e);
        }
    }

    private static URI buildObjectUri(String endpoint, String bucket, String key) {
        String base = endpoint.strip().replaceAll("/+$", "");
        String cleanKey = key.replaceAll("^/+", "");
        return URI.create(base + "/" + bucket.strip() + "/" + cleanKey);
    }

    /** 生成标准 AWS SigV4 Query String 预签名 PUT URL（需求 7.6 S3 直传） */
    public String generatePresignedPutUrl(
            String endpoint, String bucket, String region, String accessKey, String secretKey,
            String key, int expiresSeconds) {
        int safeExpires = Math.max(60, Math.min(3600, expiresSeconds));
        String safeRegion = (region == null || region.isBlank()) ? "us-east-1" : region.strip();
        URI uri = buildObjectUri(endpoint, bucket, key);
        try {
            ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
            String amzDate = AMZ_DATE.format(now);
            String dateStamp = DATE_STAMP.format(now);
            String host = uri.getPort() > 0 ? uri.getHost() + ":" + uri.getPort() : uri.getHost();
            String canonicalUri = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
            String scope = dateStamp + "/" + safeRegion + "/s3/aws4_request";
            String credential = (accessKey == null ? "" : accessKey.strip()) + "/" + scope;
            String encodedCred = java.net.URLEncoder.encode(credential, StandardCharsets.UTF_8).replace("+", "%20");
            String canonicalQuery = "X-Amz-Algorithm=AWS4-HMAC-SHA256"
                    + "&X-Amz-Credential=" + encodedCred
                    + "&X-Amz-Date=" + amzDate
                    + "&X-Amz-Expires=" + safeExpires
                    + "&X-Amz-SignedHeaders=host";
            String canonicalRequest = "PUT\n" + canonicalUri + "\n" + canonicalQuery + "\nhost:" + host + "\n\nhost\nUNSIGNED-PAYLOAD";
            String stringToSign = "AWS4-HMAC-SHA256\n" + amzDate + "\n" + scope + "\n"
                    + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));
            byte[] signingKey = getSignatureKey(secretKey == null ? "" : secretKey, dateStamp, safeRegion, "s3");
            String signature = HexFormat.of().formatHex(hmacSha256(signingKey, stringToSign));
            return uri + "?" + canonicalQuery + "&X-Amz-Signature=" + signature;
        } catch (Exception e) {
            throw new BizException(HttpStatus.INTERNAL_SERVER_ERROR, 500, "生成 S3 预签名上传地址失败: " + e.getMessage());
        }
    }

    public HttpRequest buildSignedRequest(
            String method, URI uri, String region, String accessKey, String secretKey, byte[] payload, String rangeHeader) throws Exception {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        String amzDate = AMZ_DATE.format(now);
        String dateStamp = DATE_STAMP.format(now);
        String payloadHash = sha256Hex(payload);
        String host = uri.getPort() > 0 ? uri.getHost() + ":" + uri.getPort() : uri.getHost();
        String canonicalUri = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        String canonicalHeaders = "host:" + host + "\nx-amz-content-sha256:" + payloadHash + "\nx-amz-date:" + amzDate + "\n";
        String signedHeaders = "host;x-amz-content-sha256;x-amz-date";
        String canonicalRequest = method + "\n" + canonicalUri + "\n\n" + canonicalHeaders + "\n" + signedHeaders + "\n" + payloadHash;
        String scope = dateStamp + "/" + region + "/s3/aws4_request";
        String stringToSign = "AWS4-HMAC-SHA256\n" + amzDate + "\n" + scope + "\n" + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));
        byte[] signingKey = getSignatureKey(secretKey == null ? "" : secretKey, dateStamp, region, "s3");
        String signature = HexFormat.of().formatHex(hmacSha256(signingKey, stringToSign));
        String authHeader = "AWS4-HMAC-SHA256 Credential=" + (accessKey == null ? "" : accessKey) + "/" + scope
                + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;

        var builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("x-amz-date", amzDate)
                .header("x-amz-content-sha256", payloadHash)
                .header("Authorization", authHeader);
        if (rangeHeader != null && !rangeHeader.isBlank()) {
            builder.header("Range", rangeHeader);
        }
        return switch (method) {
            case "HEAD" -> builder.method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
            case "PUT" -> builder.PUT(HttpRequest.BodyPublishers.ofByteArray(payload)).build();
            case "DELETE" -> builder.DELETE().build();
            default -> builder.GET().build();
        };
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
