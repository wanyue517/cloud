package com.wanyue.cloud.modules.drive.personal;

import com.wanyue.cloud.common.result.R;
import com.wanyue.cloud.common.security.UserIdPrincipal;
import com.wanyue.cloud.modules.drive.personal.PersonalAndBatchService.*;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class PersonalAndBatchController {
    private final PersonalAndBatchService service;

    public PersonalAndBatchController(PersonalAndBatchService service) {
        this.service = service;
    }

    private static long user(Authentication auth) {
        return ((UserIdPrincipal) auth.getPrincipal()).userId();
    }

    @PostMapping("/spaces/{space}/private/unlock")
    public R<UnlockPrivateResult> unlockPrivate(Authentication auth, @PathVariable long space, @Valid @RequestBody UnlockPrivateInput input) {
        return R.ok(service.unlockPrivateSpace(user(auth), space, input));
    }

    @PostMapping("/spaces/{space}/private/lock")
    public R<Void> lockPrivate(Authentication auth, @PathVariable long space) {
        service.lockPrivateSpace(user(auth), space);
        return R.ok();
    }

    @PostMapping("/spaces/{space}/batch/execute")
    public R<BatchJobView> executeBatch(Authentication auth, @PathVariable long space, @Valid @RequestBody BatchExecuteInput input) {
        return R.ok(service.executeBatch(user(auth), space, input));
    }

    @PostMapping("/spaces/{space}/batch/zip")
    public R<BatchJobView> createZip(Authentication auth, @PathVariable long space, @Valid @RequestBody ZipBatchInput input) throws IOException {
        return R.ok(service.createZipDownloadJob(user(auth), space, input));
    }

    @GetMapping("/batch/jobs/{jobId}/download")
    public ResponseEntity<FileSystemResource> downloadZip(Authentication auth, @PathVariable long jobId) {
        Path zip = service.resolveZipArtifact(user(auth), jobId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("cloudlane-batch-" + jobId + ".zip", StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(zip));
    }

    @GetMapping("/me/preferences")
    public R<UserPreferences> getPreferences(Authentication auth) {
        return R.ok(service.getPreferences(user(auth)));
    }

    @PutMapping("/me/preferences")
    public R<UserPreferences> updatePreferences(Authentication auth, @RequestBody UserPreferences input) {
        return R.ok(service.updatePreferences(user(auth), input));
    }

    @PostMapping("/spaces/{space}/nodes/{id}/access")
    public R<Void> recordAccess(Authentication auth, @PathVariable long space, @PathVariable long id) {
        service.recordAccess(user(auth), space, id);
        return R.ok();
    }

    @GetMapping("/recent")
    public R<List<RecentAccessItem>> listRecent(Authentication auth) {
        return R.ok(service.listRecent(user(auth)));
    }
}
