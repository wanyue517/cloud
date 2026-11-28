package com.wanyue.cloud.modules.drive;

import com.wanyue.cloud.common.result.R;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import static com.wanyue.cloud.modules.drive.DriveModels.*;

@RestController
@RequestMapping("/api/v1/uploads")
public class UploadController {
    private final UploadService uploads;
    private final DirectUploadService direct;
    public UploadController(UploadService uploads, DirectUploadService direct) { this.uploads=uploads; this.direct=direct; }
    @PostMapping("/{id}/direct") public R<java.util.Map<String, Object>> directInfo(Authentication a,@PathVariable long id) throws IOException {
        return R.ok(direct.info(DriveController.user(a),id));
    }
    @GetMapping("/{id}/direct/parts/{index}") public R<java.util.Map<String, Object>> directPart(Authentication a,@PathVariable long id,@PathVariable int index) throws IOException {
        return R.ok(direct.presign(DriveController.user(a),id,index));
    }
    @PostMapping("/{id}/fallback") public R<Upload> fallback(Authentication a,@PathVariable long id) throws IOException {
        return R.ok(uploads.fallback(DriveController.user(a),id));
    }
    @GetMapping public R<java.util.List<java.util.Map<String, Object>>> list(Authentication a) { return R.ok(uploads.listByUser(DriveController.user(a))); }
    @PostMapping public R<Upload> create(Authentication a,@Valid @RequestBody UploadInput input) throws IOException { return R.ok(uploads.create(DriveController.user(a),input)); }
    @GetMapping("/{id}") public R<Upload> status(Authentication a,@PathVariable long id) throws IOException { return R.ok(uploads.status(DriveController.user(a),id,false)); }
    @GetMapping("/{id}/presign") public R<java.util.Map<String, Object>> presign(Authentication a,@PathVariable long id,@RequestParam(defaultValue="900") int expiresSeconds) throws IOException {
        direct.info(DriveController.user(a),id);
        return R.ok(direct.presign(DriveController.user(a),id,0));
    }
    @PutMapping("/{id}/chunks/{index}") public R<Void> chunk(Authentication a,@PathVariable long id,@PathVariable int index,@RequestParam MultipartFile file,@RequestParam(required=false) String sha256) throws IOException {
        try(var in=file.getInputStream()) {uploads.chunk(DriveController.user(a),id,index,in,file.getSize(),sha256);} return R.ok();
    }
    @PostMapping("/{id}/complete") public R<String> complete(Authentication a,@PathVariable long id,@RequestParam(required=false) String expectedSha256) throws IOException {
        return R.ok(uploads.complete(DriveController.user(a),id,expectedSha256));
    }
    @PostMapping("/{id}/finalize") public R<Upload> finalizeUpload(Authentication a,@PathVariable long id,@RequestParam(required=false) String expectedSha256) throws IOException {
        return R.ok(uploads.requestCompletion(DriveController.user(a),id,expectedSha256));
    }
    @DeleteMapping("/{id}") public R<Void> cancel(Authentication a,@PathVariable long id) { uploads.cancel(DriveController.user(a),id);return R.ok(); }
}
