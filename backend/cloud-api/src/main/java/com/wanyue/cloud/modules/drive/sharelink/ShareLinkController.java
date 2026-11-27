package com.wanyue.cloud.modules.drive.sharelink;

import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.common.result.R;
import com.wanyue.cloud.common.security.UserIdPrincipal;
import com.wanyue.cloud.modules.drive.DriveModels;
import com.wanyue.cloud.modules.drive.sharelink.ShareLinkModels.*;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ShareLinkController {
    private final ShareLinkService service;

    public ShareLinkController(ShareLinkService service) {
        this.service = service;
    }

    private static long user(Authentication auth) {
        return ((UserIdPrincipal) auth.getPrincipal()).userId();
    }

    @PostMapping("/spaces/{space}/nodes/{id}/share-links")
    public R<CreatedShareLink> create(
            Authentication auth,
            @PathVariable long space,
            @PathVariable long id,
            @Valid @RequestBody CreateShareLinkInput input) {
        return R.ok(service.create(user(auth), space, id, input));
    }

    @GetMapping("/share-links")
    public R<List<ShareLinkItem>> listMyLinks(Authentication auth) {
        return R.ok(service.listMyLinks(user(auth)));
    }

    @PostMapping("/share-links/{id}/disable")
    public R<Void> disable(Authentication auth, @PathVariable long id) {
        service.disable(user(auth), id);
        return R.ok();
    }

    @GetMapping("/public/shares/{token}")
    public R<PublicShareMeta> publicMeta(
            @PathVariable String token,
            @RequestHeader(value = "X-Share-Password", required = false) String passwordHeader,
            @RequestParam(required = false) String password) {
        return R.ok(service.publicMeta(token, passwordHeader != null ? passwordHeader : password));
    }

    @PostMapping("/public/shares/{token}/verify")
    public R<Boolean> verifyPassword(@PathVariable String token, @Valid @RequestBody VerifyPasswordInput input) {
        return R.ok(service.verifyPassword(token, input.password()));
    }

    @GetMapping("/public/shares/{token}/nodes")
    public R<PublicNodeList> publicNodes(
            @PathVariable String token,
            @RequestHeader(value = "X-Share-Password", required = false) String passwordHeader,
            @RequestParam(required = false) String password,
            @RequestParam(required = false) Long parentId) {
        return R.ok(service.publicNodes(token, passwordHeader != null ? passwordHeader : password, parentId));
    }

    @GetMapping("/public/shares/{token}/content")
    public ResponseEntity<FileSystemResource> publicContent(
            @PathVariable String token,
            @RequestHeader(value = "X-Share-Password", required = false) String passwordHeader,
            @RequestParam(required = false) String password,
            @RequestParam(required = false) Long nodeId,
            @RequestParam(defaultValue = "true") boolean download,
            WebRequest webRequest) {
        DriveModels.Content c = service.publicContent(token, passwordHeader != null ? passwordHeader : password, nodeId, download);
        String etag = (c.etag() != null && !c.etag().isBlank()) ? ("\"" + c.etag() + "\"") : ("\"" + c.size() + "-" + c.name().hashCode() + "\"");
        if (!download && webRequest.checkNotModified(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).cacheControl(CacheControl.noCache().cachePrivate()).build();
        }
        MediaType mediaType = resolveMediaType(c.mimeType(), c.name());
        var builder = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        (download ? ContentDisposition.attachment() : ContentDisposition.inline()).filename(c.name(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .eTag(etag)
                .cacheControl(download ? CacheControl.noStore() : CacheControl.noCache().cachePrivate())
                .contentType(mediaType);
        if (!download && "text".equalsIgnoreCase(mediaType.getType()) && "html".equalsIgnoreCase(mediaType.getSubtype())) {
            builder.header("Content-Security-Policy", "sandbox allow-scripts; default-src 'none'; style-src 'unsafe-inline' *; img-src * data: blob:; script-src 'unsafe-inline' *;");
        }
        return builder.body(new FileSystemResource(c.path()));
    }

    @PostMapping("/public/shares/{token}/save")
    public R<DriveModels.Node> saveToMyDrive(
            Authentication auth,
            @PathVariable String token,
            @RequestHeader(value = "X-Share-Password", required = false) String passwordHeader,
            @Valid @RequestBody SavePublicShareInput input) {
        if (auth == null || !(auth.getPrincipal() instanceof UserIdPrincipal principal)) {
            throw new BizException(HttpStatus.UNAUTHORIZED, 401, "请先登录后再转存到网盘");
        }
        return R.ok(service.saveToMyDrive(principal.userId(), token, passwordHeader, input));
    }

    private static MediaType resolveMediaType(String rawMime, String filename) {
        if (rawMime != null && !rawMime.isBlank()) {
            try {
                return MediaType.parseMediaType(rawMime.strip());
            } catch (InvalidMediaTypeException ignored) { /* fallback by filename */ }
        }
        return MediaTypeFactory.getMediaType(filename).orElse(MediaType.APPLICATION_OCTET_STREAM);
    }
}
