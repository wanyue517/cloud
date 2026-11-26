package com.wanyue.cloud.modules.drive;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.common.result.R;
import com.wanyue.cloud.modules.drive.storage.StorageRouterService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 系统级图片资源直传与访问接口（头像、站点 Logo 等）：
 * 统一写入当前已配置的默认 StorageProfile（LOCAL / DB / S3），禁止前端手工拼接外链 URL。
 */
@RestController
@RequestMapping("/api/v1")
public class SystemAssetController {
    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final Map<String, String> ALLOWED_IMAGE_EXTS = Map.of(
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "gif", "image/gif",
            "webp", "image/webp",
            "ico", "image/x-icon",
            "svg", "image/svg+xml");

    private final JdbcTemplate db;
    private final StorageRouterService storageRouter;

    public SystemAssetController(JdbcTemplate db, StorageRouterService storageRouter) {
        this.db = db;
        this.storageRouter = storageRouter;
    }

    @PostMapping("/assets/images")
    public R<Map<String, Object>> uploadImage(
            Authentication auth,
            @RequestParam MultipartFile file,
            @RequestParam(defaultValue = "general") String category) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BizException(HttpStatus.BAD_REQUEST, 400, "请选择要上传的图片文件");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new BizException(HttpStatus.PAYLOAD_TOO_LARGE, 413, "图片大小不能超过 5 MB");
        }
        String name = file.getOriginalFilename() == null ? "image.png" : file.getOriginalFilename().strip();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        String mime = ALLOWED_IMAGE_EXTS.get(ext);
        if (mime == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, 400, "仅支持 PNG / JPG / JPEG / GIF / WEBP / ICO / SVG 图片格式");
        }
        String safeCategory = category == null ? "general" : category.strip().toLowerCase(Locale.ROOT);
        if (!Set.of("avatar", "site-logo", "general").contains(safeCategory)) {
            safeCategory = "general";
        }
        long profileId = storageRouter.resolveDefaultProfileId();
        var stored = storageRouter.write(profileId, file.getInputStream(), MAX_IMAGE_BYTES);
        long assetId = IdWorker.getId();
        long uid = DriveController.user(auth);
        db.update(
                "INSERT INTO sys_asset(id, category, storage_profile_id, storage_key, original_name, mime_type, size_bytes, uploaded_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                assetId, safeCategory, profileId, stored.key(), name, mime, stored.size(), uid);
        String url = "/api/v1/public/assets/" + assetId;
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", String.valueOf(assetId));
        resp.put("url", url);
        resp.put("storageProfileId", String.valueOf(profileId));
        resp.put("mimeType", mime);
        resp.put("sizeBytes", stored.size());
        return R.ok(resp);
    }

    @GetMapping("/public/assets/{id}")
    public ResponseEntity<FileSystemResource> getPublicAsset(@PathVariable long id) {
        var rows = db.queryForList(
                "SELECT storage_profile_id, storage_key, mime_type FROM sys_asset WHERE id=?", id);
        if (rows.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var row = rows.get(0);
        long profileId = ((Number) row.get("storage_profile_id")).longValue();
        String storageKey = String.valueOf(row.get("storage_key"));
        String mime = String.valueOf(row.get("mime_type"));
        Path path = storageRouter.resolveReadablePath(profileId, storageKey);
        if (!Files.isRegularFile(path)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(1)).cachePublic())
                .contentType(MediaType.parseMediaType(mime))
                .body(new FileSystemResource(path));
    }
}
