package com.wanyue.cloud.modules.drive.ops;

import com.wanyue.cloud.common.result.R;
import com.wanyue.cloud.common.security.UserIdPrincipal;
import com.wanyue.cloud.modules.drive.ops.VersionPinUndoService.*;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class VersionPinUndoController {
    private final VersionPinUndoService service;

    public VersionPinUndoController(VersionPinUndoService service) {
        this.service = service;
    }

    private static long user(Authentication auth) {
        return ((UserIdPrincipal) auth.getPrincipal()).userId();
    }

    @GetMapping("/me/version-policy")
    public R<UserVersionPolicy> getVersionPolicy(Authentication auth) {
        return R.ok(service.getUserVersionPolicy(user(auth)));
    }

    @PutMapping("/me/version-policy")
    public R<UserVersionPolicy> updateVersionPolicy(Authentication auth, @Valid @RequestBody UpdateUserVersionPolicyInput input) {
        return R.ok(service.updateUserVersionPolicy(user(auth), input.maxVersions()));
    }

    @GetMapping("/quick-access")
    public R<List<QuickAccessItem>> listQuickAccess(Authentication auth) {
        return R.ok(service.listQuickAccess(user(auth)));
    }

    @PutMapping("/spaces/{space}/nodes/{id}/pin")
    public R<Void> setPin(Authentication auth, @PathVariable long space, @PathVariable long id, @RequestParam boolean enabled) {
        service.setQuickAccessPin(user(auth), space, id, enabled);
        return R.ok();
    }

    @PostMapping("/undo/issue")
    public R<UndoTokenReceipt> issueUndo(Authentication auth, @Valid @RequestBody IssueUndoInput input) {
        return R.ok(service.issueUndoToken(user(auth), input.spaceId(), input.actionType(), input.summary(),
                Map.of(
                        "nodeId", input.nodeId(),
                        "oldName", input.oldName() == null ? "" : input.oldName(),
                        "oldParentId", input.oldParentId() == null ? 0L : input.oldParentId())));
    }

    @PostMapping("/undo/{tokenId}")
    public R<Void> executeUndo(Authentication auth, @PathVariable long tokenId) {
        service.executeUndo(user(auth), tokenId);
        return R.ok();
    }

    @PostMapping("/spaces/{space}/nodes/batch-rename/preview")
    public R<List<BatchRenamePreviewItem>> previewBatchRename(
            Authentication auth,
            @PathVariable long space,
            @Valid @RequestBody BatchRenameRequest req) {
        return R.ok(service.previewBatchRename(user(auth), space, req));
    }

    @PostMapping("/spaces/{space}/nodes/batch-rename/apply")
    public R<BatchRenameApplyResult> applyBatchRename(
            Authentication auth,
            @PathVariable long space,
            @Valid @RequestBody BatchRenameRequest req) {
        return R.ok(service.applyBatchRename(user(auth), space, req));
    }
}
