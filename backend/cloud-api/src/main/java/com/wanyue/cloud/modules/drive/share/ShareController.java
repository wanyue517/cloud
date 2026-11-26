package com.wanyue.cloud.modules.drive.share;

import com.wanyue.cloud.common.result.R;
import com.wanyue.cloud.common.security.UserIdPrincipal;
import com.wanyue.cloud.modules.drive.share.ShareModels.*;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ShareController {
    private final ShareService shareService;
    private final ShareEventHub eventHub;

    public ShareController(ShareService shareService, ShareEventHub eventHub) {
        this.shareService = shareService;
        this.eventHub = eventHub;
    }

    private static long user(Authentication auth) {
        return ((UserIdPrincipal) auth.getPrincipal()).userId();
    }

    @PostMapping("/spaces/{space}/convert-shared")
    public R<Void> convertToShared(Authentication auth, @PathVariable long space) {
        shareService.convertToSharedSpace(user(auth), space);
        return R.ok();
    }

    @PostMapping("/spaces/{space}/convert-personal")
    public R<Void> convertToPersonal(
            Authentication auth,
            @PathVariable long space,
            @RequestParam(defaultValue = "true") boolean keepMembers) {
        shareService.convertToPersonalSpace(user(auth), space, keepMembers);
        return R.ok();
    }

    @GetMapping("/spaces/{space}/members")
    public R<SpaceMembersOverview> spaceMembers(Authentication auth, @PathVariable long space) {
        return R.ok(shareService.spaceMembers(user(auth), space));
    }

    @PostMapping("/spaces/{space}/invitations")
    public R<String> inviteToSpace(Authentication auth, @PathVariable long space, @Valid @RequestBody InviteMemberInput input) {
        return R.ok(shareService.inviteToSpace(user(auth), space, input));
    }

    @PutMapping("/spaces/{space}/members/{memberUserId}")
    public R<Void> updateMemberRole(
            Authentication auth,
            @PathVariable long space,
            @PathVariable long memberUserId,
            @Valid @RequestBody UpdateMemberRoleInput input) {
        shareService.updateMemberRole(user(auth), space, memberUserId, input);
        return R.ok();
    }

    @DeleteMapping("/spaces/{space}/members/{memberUserId}")
    public R<Void> removeMember(Authentication auth, @PathVariable long space, @PathVariable long memberUserId) {
        shareService.removeMember(user(auth), space, memberUserId);
        return R.ok();
    }

    @GetMapping("/shares/invitations")
    public R<List<InvitationItem>> receivedInvitations(Authentication auth) {
        return R.ok(shareService.myReceivedInvitations(user(auth)));
    }

    @PostMapping("/shares/invitations/{id}/accept")
    public R<Void> acceptInvitation(Authentication auth, @PathVariable long id) {
        shareService.acceptInvitation(user(auth), id);
        return R.ok();
    }

    @PostMapping("/shares/invitations/{id}/reject")
    public R<Void> rejectInvitation(Authentication auth, @PathVariable long id) {
        shareService.rejectInvitation(user(auth), id);
        return R.ok();
    }

    @DeleteMapping("/shares/invitations/{id}")
    public R<Void> cancelInvitation(Authentication auth, @PathVariable long id) {
        shareService.cancelInvitation(user(auth), id);
        return R.ok();
    }

    @PostMapping("/spaces/{space}/nodes/{id}/shares")
    public R<String> shareFolder(
            Authentication auth,
            @PathVariable long space,
            @PathVariable long id,
            @Valid @RequestBody FolderShareInput input) {
        return R.ok(shareService.shareFolder(user(auth), space, id, input));
    }

    @DeleteMapping("/shares/resources/{shareId}")
    public R<Void> revokeResourceShare(Authentication auth, @PathVariable long shareId) {
        shareService.revokeResourceShare(user(auth), shareId);
        return R.ok();
    }

    @GetMapping("/shares/with-me")
    public R<List<ResourceShareItem>> sharedWithMe(Authentication auth) {
        return R.ok(shareService.sharedWithMe(user(auth)));
    }

    @GetMapping("/shares/by-me")
    public R<List<ResourceShareItem>> sharedByMe(Authentication auth) {
        return R.ok(shareService.sharedByMe(user(auth)));
    }

    @GetMapping(value = "/shares/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(Authentication auth) {
        return eventHub.subscribe(user(auth));
    }
}
