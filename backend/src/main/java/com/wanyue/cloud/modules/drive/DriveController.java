package com.wanyue.cloud.modules.drive;

import com.wanyue.cloud.common.result.R;
import com.wanyue.cloud.common.security.UserIdPrincipal;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static com.wanyue.cloud.modules.drive.DriveModels.*;

@RestController
@RequestMapping("/api/v1")
public class DriveController {
    private final DriveService drive;
    public DriveController(DriveService drive) { this.drive=drive; }
    static long user(Authentication auth) { return ((UserIdPrincipal)auth.getPrincipal()).userId(); }
    @GetMapping("/spaces") public R<List<Space>> spaces(Authentication a) { return R.ok(drive.spaces(user(a))); }
    @PostMapping("/spaces") public R<String> createSpace(Authentication a,@Valid @RequestBody SpaceInput input) { return R.ok(drive.createSpace(user(a),input)); }
    @PutMapping("/spaces/{space}") public R<Void> updateSpace(Authentication a,@PathVariable long space,@Valid @RequestBody SpaceInput input) { drive.updateSpace(user(a),space,input); return R.ok(); }
    @DeleteMapping("/spaces/{space}") public R<Void> deleteSpace(Authentication a,@PathVariable long space,@RequestParam String confirmation) { drive.deleteSpace(user(a),space,confirmation); return R.ok(); }
    @GetMapping("/spaces/{space}/nodes")
    public R<Page> list(Authentication a,@PathVariable long space,@RequestParam(defaultValue="0") long parentId,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="100") int pageSize,
            @RequestParam(defaultValue="false") boolean folders,@RequestParam(defaultValue="false") boolean trash,
            @RequestParam(required=false) String query,@RequestParam(defaultValue="name") String sort,
            @RequestParam(defaultValue="false") boolean hidden,@RequestParam(defaultValue="false") boolean favorites) {
        return R.ok(drive.list(user(a),space,parentId,page,pageSize,folders,trash,query,sort,hidden,favorites));
    }
    @GetMapping("/spaces/{space}/nodes/{id}/ancestors") public R<List<Node>> ancestors(Authentication a,@PathVariable long space,@PathVariable long id) {
        drive.access(user(a),space,false); return R.ok(drive.ancestors(space,id));
    }
    @PostMapping("/spaces/{space}/folders") public R<Node> folder(Authentication a,@PathVariable long space,@Valid @RequestBody NodeInput input) { return R.ok(drive.folder(user(a),space,input)); }
    @PatchMapping("/spaces/{space}/nodes/{id}") public R<Node> change(Authentication a,@PathVariable long space,@PathVariable long id,@Valid @RequestBody ChangeInput input) { return R.ok(drive.change(user(a),space,id,input)); }
    @DeleteMapping("/spaces/{space}/nodes/{id}") public R<Void> trash(Authentication a,@PathVariable long space,@PathVariable long id,@RequestParam long revision) { drive.trash(user(a),space,id,revision); return R.ok(); }
    @PostMapping("/spaces/{space}/nodes/{id}/restore") public R<Void> restore(Authentication a,@PathVariable long space,@PathVariable long id) { drive.restore(user(a),space,id); return R.ok(); }
    @PostMapping("/spaces/{space}/files") public R<Node> upload(Authentication a,@PathVariable long space,@RequestParam(defaultValue="0") long parentId,
            @RequestParam MultipartFile file,@RequestParam(required=false) Long replaceId,@RequestParam(required=false) Long revision) throws IOException {
        try(var input=file.getInputStream()) { return R.ok(drive.upload(user(a),space,parentId,file.getOriginalFilename(),file.getContentType(),input,file.getSize(),replaceId,revision)); }
    }
    @GetMapping("/spaces/{space}/nodes/{id}/content") public ResponseEntity<FileSystemResource> content(Authentication a,@PathVariable long space,@PathVariable long id,
            @RequestParam(required=false) Long versionId,@RequestParam(defaultValue="false") boolean download) {
        Content c=drive.content(user(a),space,id,versionId);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                (download?ContentDisposition.attachment():ContentDisposition.inline()).filename(c.name(),StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options","nosniff").header("Content-Security-Policy","sandbox; default-src 'none'")
                .cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(c.mimeType()))
                .body(new FileSystemResource(c.path())); // Spring's resource converter handles HTTP Range.
    }
    @GetMapping("/spaces/{space}/nodes/{id}/versions") public R<List<Version>> versions(Authentication a,@PathVariable long space,@PathVariable long id) { return R.ok(drive.versions(user(a),space,id)); }
    @PostMapping("/spaces/{space}/nodes/{id}/versions/{version}/restore") public R<Node> restoreVersion(Authentication a,@PathVariable long space,@PathVariable long id,@PathVariable long version,@RequestParam long revision) { return R.ok(drive.restoreVersion(user(a),space,id,version,revision)); }
    @PutMapping("/spaces/{space}/nodes/{id}/favorite") public R<Void> favorite(Authentication a,@PathVariable long space,@PathVariable long id,@RequestParam boolean enabled) { drive.favorite(user(a),space,id,enabled); return R.ok(); }
}
