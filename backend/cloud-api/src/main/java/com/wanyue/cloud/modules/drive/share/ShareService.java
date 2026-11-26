package com.wanyue.cloud.modules.drive.share;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.modules.drive.DriveModels;
import com.wanyue.cloud.modules.drive.DriveService;
import com.wanyue.cloud.modules.drive.share.ShareModels.*;
import com.wanyue.cloud.modules.message.MessageService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class ShareService {
    private final JdbcTemplate db;
    private final DriveService drive;
    private final ShareEventHub eventHub;
    private final MessageService messageService;

    public ShareService(JdbcTemplate db, DriveService drive, ShareEventHub eventHub, MessageService messageService) {
        this.db = db;
        this.drive = drive;
        this.eventHub = eventHub;
        this.messageService = messageService;
    }

    private long resolveTargetUser(String target) {
        String trimmed = target == null ? "" : target.strip();
        if (trimmed.isEmpty()) throw new BizException(HttpStatus.BAD_REQUEST, 400, "请输入目标用户邮箱或 ID");
        if (trimmed.matches("^\\d{1,20}$")) {
            Long id = Long.parseLong(trimmed);
            Integer exists = db.queryForObject("SELECT COUNT(*) FROM sys_user WHERE id=? AND deleted=0 AND status=1", Integer.class, id);
            if (exists != null && exists > 0) return id;
        }
        var rows = db.queryForList(
                "SELECT u.id FROM auth_identity ai JOIN sys_user u ON u.id=ai.user_id "
                        + "WHERE ai.identifier=? AND ai.deleted=0 AND u.deleted=0 AND u.status=1 LIMIT 1",
                trimmed.toLowerCase(Locale.ROOT));
        if (rows.isEmpty()) {
            throw BizException.notFound("未找到对应的有效用户");
        }
        return ((Number) rows.get(0).get("id")).longValue();
    }

    private static String toIso(Object ts) {
        if (ts == null) return "";
        if (ts instanceof LocalDateTime ldt) return ldt.toInstant(ZoneOffset.UTC).toString();
        return String.valueOf(ts);
    }

    @Transactional
    public void convertToSharedSpace(long user, long spaceId) {
        drive.requireSpaceOwner(user, spaceId, true);
        String kind = db.queryForObject("SELECT kind FROM space WHERE id=?", String.class, spaceId);
        if ("SHARED".equals(kind)) return;
        db.update("UPDATE space SET kind='SHARED', access_version=access_version+1, version=version+1 WHERE id=?", spaceId);
        Integer hasOwner = db.queryForObject(
                "SELECT COUNT(*) FROM space_member WHERE space_id=? AND user_id=? AND deleted=0",
                Integer.class, spaceId, user);
        if (hasOwner == null || hasOwner == 0) {
            db.update("INSERT INTO space_member(id,space_id,user_id,role,created_by,updated_by) VALUES (?,?,?,'OWNER',?,?)",
                    IdWorker.getId(), spaceId, user, user, user);
        }
        List<Long> retained = db.queryForList(
                "SELECT user_id FROM space_member WHERE space_id=? AND user_id<>? AND deleted=0",
                Long.class, spaceId, user);
        for (Long memberId : retained) {
            eventHub.notifyAccessChanged(memberId, "SPACE_CONVERTED_SHARED", String.valueOf(spaceId));
        }
    }

    @Transactional
    public void convertToPersonalSpace(long user, long spaceId, boolean keepMembers) {
        drive.requireSpaceOwner(user, spaceId, true);
        String kind = db.queryForObject("SELECT kind FROM space WHERE id=?", String.class, spaceId);
        if ("PERSONAL".equals(kind)) return;
        if (!"SHARED".equals(kind)) {
            throw BizException.conflict("仅共享空间可转为个人空间");
        }
        List<Long> affectedMembers = db.queryForList(
                "SELECT user_id FROM space_member WHERE space_id=? AND user_id<>? AND deleted=0",
                Long.class, spaceId, user);
        db.update("UPDATE space SET kind='PERSONAL', access_version=access_version+1, version=version+1 WHERE id=?", spaceId);
        db.update(
                "UPDATE share_invitation SET status='CANCELLED', responded_at=UTC_TIMESTAMP(3) "
                        + "WHERE space_id=? AND target_type='SPACE' AND status='PENDING'",
                spaceId);
        if (!keepMembers) {
            db.update(
                    "UPDATE space_member SET deleted=id, updated_by=?, version=version+1 "
                            + "WHERE space_id=? AND user_id<>? AND deleted=0",
                    user, spaceId, user);
        }
        for (Long memberId : affectedMembers) {
            eventHub.notifyAccessChanged(memberId, "SPACE_CONVERTED_PERSONAL", String.valueOf(spaceId));
        }
    }

    public SpaceMembersOverview spaceMembers(long user, long spaceId) {
        String myRole = drive.resolveSpaceRole(user, spaceId, false);
        if (myRole == null) throw BizException.notFound("空间不存在或无权访问");
        String kind = db.queryForObject("SELECT kind FROM space WHERE id=?", String.class, spaceId);

        List<SpaceMemberItem> members = db.query(
                "SELECT m.id, m.user_id, COALESCE(u.display_name, '') display_name, "
                        + "COALESCE((SELECT ai.identifier FROM auth_identity ai WHERE ai.user_id=m.user_id AND ai.identity_type='EMAIL' AND ai.deleted=0 ORDER BY ai.is_primary DESC, ai.id ASC LIMIT 1), '') email, "
                        + "m.role, m.joined_at, m.expires_at "
                        + "FROM space_member m LEFT JOIN sys_user u ON u.id=m.user_id "
                        + "WHERE m.space_id=? AND m.deleted=0 ORDER BY FIELD(m.role,'OWNER','MANAGER','EDITOR','VIEWER'), m.joined_at ASC",
                (r, i) -> new SpaceMemberItem(
                        r.getString("id"),
                        r.getString("user_id"),
                        r.getString("display_name"),
                        r.getString("email"),
                        r.getString("role"),
                        toIso(r.getObject("joined_at")),
                        toIso(r.getObject("expires_at"))),
                spaceId);

        List<InvitationItem> pending = ("OWNER".equals(myRole) || "MANAGER".equals(myRole))
                ? listInvitationsInternal("WHERE i.space_id=? AND i.target_type='SPACE' AND i.status='PENDING' ORDER BY i.created_at DESC", spaceId)
                : List.of();
        return new SpaceMembersOverview(String.valueOf(spaceId), kind, myRole, members, pending);
    }

    @Transactional
    public String inviteToSpace(long user, long spaceId, InviteMemberInput input) {
        String myRole = drive.resolveSpaceRole(user, spaceId, true);
        if (!"OWNER".equals(myRole) && !"MANAGER".equals(myRole)) {
            throw new BizException(HttpStatus.FORBIDDEN, 403, "仅空间所有者或管理员可邀请成员");
        }
        String kind = db.queryForObject("SELECT kind FROM space WHERE id=?", String.class, spaceId);
        if (!"SHARED".equals(kind)) {
            throw BizException.conflict("请先将个人空间转为共享空间后再邀请成员");
        }
        if ("MANAGER".equals(myRole) && "MANAGER".equals(input.role())) {
            throw new BizException(HttpStatus.FORBIDDEN, 403, "管理员不能直接邀请其他管理员");
        }
        long targetUser = resolveTargetUser(input.target());
        if (targetUser == user) throw BizException.conflict("不能邀请自己");
        Integer alreadyMember = db.queryForObject(
                "SELECT COUNT(*) FROM space_member WHERE space_id=? AND user_id=? AND deleted=0",
                Integer.class, spaceId, targetUser);
        if (alreadyMember != null && alreadyMember > 0) {
            throw BizException.conflict("该用户已是空间成员");
        }
        Integer pending = db.queryForObject(
                "SELECT COUNT(*) FROM share_invitation WHERE space_id=? AND target_type='SPACE' AND invitee_user_id=? AND status='PENDING'",
                Integer.class, spaceId, targetUser);
        if (pending != null && pending > 0) {
            throw BizException.conflict("已存在待处理的邀请");
        }
        long id = IdWorker.getId();
        db.update(
                "INSERT INTO share_invitation(id, target_type, space_id, node_id, inviter_user_id, invitee_user_id, role, status, message, expires_at) "
                        + "VALUES (?, 'SPACE', ?, NULL, ?, ?, ?, 'PENDING', ?, DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 7 DAY))",
                id, spaceId, user, targetUser, input.role(), input.message() == null ? "" : input.message().strip());
        eventHub.notifyAccessChanged(targetUser, "INVITATION_RECEIVED", String.valueOf(id));
        String spaceName = db.queryForObject("SELECT name FROM space WHERE id=?", String.class, spaceId);
        messageService.sendPersonal(user, targetUser, "SHARE_INVITE",
                "收到共享空间邀请：" + spaceName,
                "你被邀请以 " + input.role() + " 角色加入共享空间「" + spaceName + "」，点击前往共享中心处理。",
                "/app/shared");
        return String.valueOf(id);
    }

    @Transactional
    public void acceptInvitation(long user, long invitationId) {
        var rows = db.queryForList("SELECT * FROM share_invitation WHERE id=? AND invitee_user_id=? FOR UPDATE", invitationId, user);
        if (rows.isEmpty()) throw BizException.notFound("邀请不存在");
        var inv = rows.get(0);
        if (!"PENDING".equals(inv.get("status"))) throw BizException.conflict("该邀请已处理或已失效");

        long spaceId = ((Number) inv.get("space_id")).longValue();
        String role = String.valueOf(inv.get("role"));
        String targetType = String.valueOf(inv.get("target_type"));
        long inviterId = ((Number) inv.get("inviter_user_id")).longValue();

        if ("SPACE".equals(targetType)) {
            Integer exists = db.queryForObject(
                    "SELECT COUNT(*) FROM space_member WHERE space_id=? AND user_id=? AND deleted=0",
                    Integer.class, spaceId, user);
            if (exists == null || exists == 0) {
                db.update(
                        "INSERT INTO space_member(id, space_id, user_id, role, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?)",
                        IdWorker.getId(), spaceId, user, role, inviterId, user);
            }
            db.update("UPDATE space SET access_version=access_version+1 WHERE id=?", spaceId);
        }
        db.update("UPDATE share_invitation SET status='ACCEPTED', responded_at=UTC_TIMESTAMP(3) WHERE id=?", invitationId);
        eventHub.notifyAccessChanged(user, "SPACE_JOINED", String.valueOf(spaceId));
        eventHub.notifyAccessChanged(inviterId, "INVITATION_ACCEPTED", String.valueOf(spaceId));
    }

    @Transactional
    public void rejectInvitation(long user, long invitationId) {
        int updated = db.update(
                "UPDATE share_invitation SET status='REJECTED', responded_at=UTC_TIMESTAMP(3) WHERE id=? AND invitee_user_id=? AND status='PENDING'",
                invitationId, user);
        if (updated != 1) throw BizException.notFound("待处理邀请不存在");
    }

    @Transactional
    public void cancelInvitation(long user, long invitationId) {
        var rows = db.queryForList("SELECT space_id, inviter_user_id, invitee_user_id FROM share_invitation WHERE id=? AND status='PENDING'", invitationId);
        if (rows.isEmpty()) throw BizException.notFound("待处理邀请不存在");
        long spaceId = ((Number) rows.get(0).get("space_id")).longValue();
        long inviter = ((Number) rows.get(0).get("inviter_user_id")).longValue();
        long invitee = ((Number) rows.get(0).get("invitee_user_id")).longValue();
        String myRole = drive.resolveSpaceRole(user, spaceId, false);
        if (user != inviter && !"OWNER".equals(myRole) && !"MANAGER".equals(myRole)) {
            throw new BizException(HttpStatus.FORBIDDEN, 403, "无权取消该邀请");
        }
        db.update("UPDATE share_invitation SET status='CANCELLED', responded_at=UTC_TIMESTAMP(3) WHERE id=?", invitationId);
        eventHub.notifyAccessChanged(invitee, "INVITATION_CANCELLED", String.valueOf(invitationId));
    }

    @Transactional
    public void updateMemberRole(long user, long spaceId, long memberUserId, UpdateMemberRoleInput input) {
        String myRole = drive.resolveSpaceRole(user, spaceId, true);
        if (!"OWNER".equals(myRole) && !"MANAGER".equals(myRole)) {
            throw new BizException(HttpStatus.FORBIDDEN, 403, "无权修改成员角色");
        }
        var rows = db.queryForList("SELECT id, role FROM space_member WHERE space_id=? AND user_id=? AND deleted=0", spaceId, memberUserId);
        if (rows.isEmpty()) throw BizException.notFound("成员不存在");
        String currentRole = String.valueOf(rows.get(0).get("role"));
        if ("OWNER".equals(currentRole)) throw BizException.conflict("不能修改空间所有者的角色");
        if ("MANAGER".equals(myRole) && ("MANAGER".equals(currentRole) || "MANAGER".equals(input.role()))) {
            throw new BizException(HttpStatus.FORBIDDEN, 403, "管理员不能调整其他管理员角色");
        }
        db.update("UPDATE space_member SET role=?, updated_by=?, version=version+1 WHERE space_id=? AND user_id=? AND deleted=0",
                input.role(), user, spaceId, memberUserId);
        db.update("UPDATE space SET access_version=access_version+1 WHERE id=?", spaceId);
        eventHub.notifyAccessChanged(memberUserId, "ROLE_UPDATED", String.valueOf(spaceId));
    }

    @Transactional
    public void removeMember(long user, long spaceId, long memberUserId) {
        String myRole = drive.resolveSpaceRole(user, spaceId, true);
        if (myRole == null) throw BizException.notFound("空间不存在或无权访问");
        var rows = db.queryForList("SELECT id, role FROM space_member WHERE space_id=? AND user_id=? AND deleted=0", spaceId, memberUserId);
        if (rows.isEmpty()) throw BizException.notFound("成员不存在");
        String targetRole = String.valueOf(rows.get(0).get("role"));
        if ("OWNER".equals(targetRole)) throw BizException.conflict("不能移除空间所有者");
        boolean selfLeave = user == memberUserId;
        if (!selfLeave) {
            if (!"OWNER".equals(myRole) && !"MANAGER".equals(myRole)) {
                throw new BizException(HttpStatus.FORBIDDEN, 403, "无权移除成员");
            }
            if ("MANAGER".equals(myRole) && "MANAGER".equals(targetRole)) {
                throw new BizException(HttpStatus.FORBIDDEN, 403, "管理员不能移除其他管理员");
            }
        }
        db.update("UPDATE space_member SET deleted=id, updated_by=?, version=version+1 WHERE space_id=? AND user_id=? AND deleted=0",
                user, spaceId, memberUserId);
        db.update("UPDATE space SET access_version=access_version+1 WHERE id=?", spaceId);
        eventHub.notifyAccessChanged(memberUserId, "MEMBER_REMOVED", String.valueOf(spaceId));
    }

    @Transactional
    public String shareFolder(long user, long spaceId, long nodeId, FolderShareInput input) {
        String myRole = drive.resolveSpaceRole(user, spaceId, true);
        if (!"OWNER".equals(myRole) && !"MANAGER".equals(myRole)) {
            throw new BizException(HttpStatus.FORBIDDEN, 403, "仅空间所有者或管理员可共享文件夹");
        }
        DriveModels.Node node = drive.getNode(spaceId, nodeId, false);
        if (node.hidden()) {
            throw BizException.conflict("隐藏项不能对外共享");
        }
        long targetUser = resolveTargetUser(input.target());
        long ownerUserId = db.queryForObject("SELECT owner_user_id FROM space WHERE id=?", Long.class, spaceId);
        if (targetUser == ownerUserId || targetUser == user) {
            throw BizException.conflict("不能共享给自己或空间所有者");
        }
        var existing = db.queryForList("SELECT id FROM resource_share WHERE node_id=? AND target_user_id=?", nodeId, targetUser);
        if (!existing.isEmpty()) {
            long shareId = ((Number) existing.get(0).get("id")).longValue();
            db.update("UPDATE resource_share SET permission=?, revoked_at=NULL, granted_by=?, updated_at=UTC_TIMESTAMP(3) WHERE id=?",
                    input.permission(), user, shareId);
            eventHub.notifyAccessChanged(targetUser, "FOLDER_SHARED", String.valueOf(nodeId));
            return String.valueOf(shareId);
        }
        long id = IdWorker.getId();
        db.update(
                "INSERT INTO resource_share(id, space_id, node_id, owner_user_id, target_user_id, permission, source_type, granted_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'DIRECT', ?)",
                id, spaceId, nodeId, ownerUserId, targetUser, input.permission(), user);
        eventHub.notifyAccessChanged(targetUser, "FOLDER_SHARED", String.valueOf(nodeId));
        messageService.sendPersonal(user, targetUser, "SHARE_EVENT",
                "收到共享文件夹：" + node.name(),
                "有人向你共享了文件夹「" + node.name() + "」（权限：" + input.permission() + "），点击前往查看。",
                "/app/shared");
        return String.valueOf(id);
    }

    @Transactional
    public void revokeResourceShare(long user, long shareId) {
        var rows = db.queryForList("SELECT space_id, node_id, owner_user_id, target_user_id, granted_by FROM resource_share WHERE id=? AND revoked_at IS NULL", shareId);
        if (rows.isEmpty()) throw BizException.notFound("共享授权不存在或已撤销");
        var row = rows.get(0);
        long spaceId = ((Number) row.get("space_id")).longValue();
        long ownerId = ((Number) row.get("owner_user_id")).longValue();
        long targetUserId = ((Number) row.get("target_user_id")).longValue();
        long grantedBy = ((Number) row.get("granted_by")).longValue();
        String role = drive.resolveSpaceRole(user, spaceId, false);
        if (user != ownerId && user != grantedBy && user != targetUserId && !"OWNER".equals(role) && !"MANAGER".equals(role)) {
            throw new BizException(HttpStatus.FORBIDDEN, 403, "无权撤销该共享");
        }
        db.update("UPDATE resource_share SET revoked_at=UTC_TIMESTAMP(3) WHERE id=?", shareId);
        eventHub.notifyAccessChanged(targetUserId, "FOLDER_SHARE_REVOKED", String.valueOf(row.get("node_id")));
    }

    public List<ResourceShareItem> sharedWithMe(long user) {
        return listResourceShares(
                "WHERE rs.target_user_id=? AND rs.revoked_at IS NULL AND (rs.expires_at IS NULL OR rs.expires_at > UTC_TIMESTAMP(3)) "
                        + "AND s.deleted=0 AND s.status='ACTIVE' AND n.deleted_at IS NULL ORDER BY rs.created_at DESC",
                user);
    }

    public List<ResourceShareItem> sharedByMe(long user) {
        return listResourceShares(
                "WHERE (rs.owner_user_id=? OR rs.granted_by=?) AND rs.revoked_at IS NULL "
                        + "AND s.deleted=0 AND s.status='ACTIVE' AND n.deleted_at IS NULL ORDER BY rs.created_at DESC",
                user, user);
    }

    public List<InvitationItem> myReceivedInvitations(long user) {
        return listInvitationsInternal("WHERE i.invitee_user_id=? AND i.status='PENDING' ORDER BY i.created_at DESC", user);
    }

    private List<ResourceShareItem> listResourceShares(String where, Object... args) {
        return db.query(
                "SELECT rs.id, rs.space_id, COALESCE(s.name, '') space_name, rs.node_id, n.name node_name, n.node_type, "
                        + "rs.owner_user_id, COALESCE(uo.display_name, '') owner_name, "
                        + "rs.target_user_id, COALESCE(ut.display_name, '') target_name, "
                        + "rs.permission, rs.source_type, rs.created_at "
                        + "FROM resource_share rs "
                        + "JOIN space s ON s.id=rs.space_id "
                        + "JOIN file_node n ON n.id=rs.node_id "
                        + "LEFT JOIN sys_user uo ON uo.id=rs.owner_user_id "
                        + "LEFT JOIN sys_user ut ON ut.id=rs.target_user_id "
                        + where,
                (r, i) -> new ResourceShareItem(
                        r.getString("id"),
                        r.getString("space_id"),
                        r.getString("space_name"),
                        r.getString("node_id"),
                        r.getString("node_name"),
                        r.getString("node_type"),
                        r.getString("owner_user_id"),
                        r.getString("owner_name"),
                        r.getString("target_user_id"),
                        r.getString("target_name"),
                        r.getString("permission"),
                        r.getString("source_type"),
                        toIso(r.getObject("created_at"))),
                args);
    }

    private List<InvitationItem> listInvitationsInternal(String where, Object... args) {
        return db.query(
                "SELECT i.id, i.target_type, i.space_id, COALESCE(s.name, '') space_name, i.node_id, COALESCE(n.name, '') node_name, "
                        + "i.inviter_user_id, COALESCE(u1.display_name, '') inviter_name, "
                        + "i.invitee_user_id, COALESCE(u2.display_name, '') invitee_name, "
                        + "i.role, i.status, i.message, i.created_at "
                        + "FROM share_invitation i "
                        + "LEFT JOIN space s ON s.id=i.space_id "
                        + "LEFT JOIN file_node n ON n.id=i.node_id "
                        + "LEFT JOIN sys_user u1 ON u1.id=i.inviter_user_id "
                        + "LEFT JOIN sys_user u2 ON u2.id=i.invitee_user_id "
                        + where,
                (r, idx) -> new InvitationItem(
                        r.getString("id"),
                        r.getString("target_type"),
                        r.getString("space_id"),
                        r.getString("space_name"),
                        r.getString("node_id"),
                        r.getString("node_name"),
                        r.getString("inviter_user_id"),
                        r.getString("inviter_name"),
                        r.getString("invitee_user_id"),
                        r.getString("invitee_name"),
                        r.getString("role"),
                        r.getString("status"),
                        r.getString("message"),
                        toIso(r.getObject("created_at"))),
                args);
    }
}
