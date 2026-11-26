package com.wanyue.cloud.modules.message;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.modules.message.MessageModels.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

@Service
public class MessageService {
    private final JdbcTemplate db;
    private final MessageEventHub eventHub;

    public MessageService(JdbcTemplate db, MessageEventHub eventHub) {
        this.db = db;
        this.eventHub = eventHub;
    }

    private static String toIso(Object ts) {
        if (ts == null) return "";
        if (ts instanceof LocalDateTime ldt) return ldt.toInstant(ZoneOffset.UTC).toString();
        return String.valueOf(ts);
    }

    @Transactional
    public String sendPersonal(long senderUserId, long targetUserId, String category, String title, String content, String actionUrl) {
        long messageId = IdWorker.getId();
        String safeAction = actionUrl == null ? "" : actionUrl.strip();
        db.update(
                "INSERT INTO sys_message(id, sender_user_id, scope, category, title, content, action_url) VALUES (?, ?, 'DIRECT', ?, ?, ?, ?)",
                messageId, senderUserId, category, title, content == null ? "" : content, safeAction);
        db.update(
                "INSERT INTO sys_message_receipt(id, message_id, user_id, is_read) VALUES (?, ?, ?, 0)",
                IdWorker.getId(), messageId, targetUserId);
        eventHub.pushNewMessage(targetUserId, String.valueOf(messageId), title, category, safeAction);
        return String.valueOf(messageId);
    }

    @Transactional
    public String publishAdminMessage(long adminUserId, PublishMessageInput input) {
        String scope = input.scope() == null ? "ALL" : input.scope().toUpperCase(Locale.ROOT);
        long messageId = IdWorker.getId();
        String safeAction = input.actionUrl() == null ? "" : input.actionUrl().strip();
        db.update(
                "INSERT INTO sys_message(id, sender_user_id, scope, category, title, content, action_url) VALUES (?, ?, ?, 'SYSTEM', ?, ?, ?)",
                messageId, adminUserId, scope, input.title().strip(), input.content() == null ? "" : input.content().strip(), safeAction);

        List<Long> recipients;
        if ("ALL".equals(scope)) {
            recipients = db.queryForList("SELECT id FROM sys_user WHERE deleted=0 AND status=1", Long.class);
        } else {
            long targetUserId = resolveTargetUser(input.target());
            recipients = List.of(targetUserId);
        }
        for (Long uid : recipients) {
            db.update("INSERT IGNORE INTO sys_message_receipt(id, message_id, user_id, is_read) VALUES (?, ?, ?, 0)",
                    IdWorker.getId(), messageId, uid);
            eventHub.pushNewMessage(uid, String.valueOf(messageId), input.title().strip(), "SYSTEM", safeAction);
        }
        return String.valueOf(messageId);
    }

    private long resolveTargetUser(String target) {
        String trimmed = target == null ? "" : target.strip();
        if (trimmed.isEmpty()) throw new BizException(HttpStatus.BAD_REQUEST, 400, "定向消息必须指定目标用户邮箱或 ID");
        if (trimmed.matches("^\\d{1,20}$")) {
            Long id = Long.parseLong(trimmed);
            Integer exists = db.queryForObject("SELECT COUNT(*) FROM sys_user WHERE id=? AND deleted=0 AND status=1", Integer.class, id);
            if (exists != null && exists > 0) return id;
        }
        var rows = db.queryForList(
                "SELECT u.id FROM auth_identity ai JOIN sys_user u ON u.id=ai.user_id "
                        + "WHERE ai.identifier=? AND ai.deleted=0 AND u.deleted=0 AND u.status=1 LIMIT 1",
                trimmed.toLowerCase(Locale.ROOT));
        if (rows.isEmpty()) throw BizException.notFound("未找到目标用户");
        return ((Number) rows.get(0).get("id")).longValue();
    }

    public InboxResponse inbox(long userId) {
        Long unread = db.queryForObject(
                "SELECT COUNT(*) FROM sys_message_receipt WHERE user_id=? AND is_read=0",
                Long.class, userId);
        List<MessageItem> items = db.query(
                "SELECT m.id, m.category, m.scope, m.title, m.content, m.action_url, r.is_read, m.created_at "
                        + "FROM sys_message_receipt r JOIN sys_message m ON m.id=r.message_id "
                        + "WHERE r.user_id=? ORDER BY m.created_at DESC LIMIT 100",
                (r, i) -> new MessageItem(
                        r.getString("id"),
                        r.getString("category"),
                        r.getString("scope"),
                        r.getString("title"),
                        r.getString("content"),
                        r.getString("action_url"),
                        r.getBoolean("is_read"),
                        toIso(r.getObject("created_at"))),
                userId);
        return new InboxResponse(unread == null ? 0L : unread, items);
    }

    @Transactional
    public void markRead(long userId, long messageId) {
        db.update(
                "UPDATE sys_message_receipt SET is_read=1, read_at=UTC_TIMESTAMP(3) WHERE message_id=? AND user_id=? AND is_read=0",
                messageId, userId);
    }

    @Transactional
    public void markAllRead(long userId) {
        db.update("UPDATE sys_message_receipt SET is_read=1, read_at=UTC_TIMESTAMP(3) WHERE user_id=? AND is_read=0", userId);
    }

    public List<AdminMessageRecord> listAdminMessages() {
        return db.query(
                "SELECT m.id, m.scope, m.category, m.title, m.content, m.action_url, m.created_at, "
                        + "COUNT(r.id) recipient_count, COALESCE(SUM(CASE WHEN r.is_read=1 THEN 1 ELSE 0 END), 0) read_count "
                        + "FROM sys_message m LEFT JOIN sys_message_receipt r ON r.message_id=m.id "
                        + "WHERE m.category='SYSTEM' GROUP BY m.id, m.scope, m.category, m.title, m.content, m.action_url, m.created_at "
                        + "ORDER BY m.created_at DESC LIMIT 100",
                (r, i) -> new AdminMessageRecord(
                        r.getString("id"),
                        r.getString("scope"),
                        r.getString("category"),
                        r.getString("title"),
                        r.getString("content"),
                        r.getString("action_url"),
                        r.getLong("recipient_count"),
                        r.getLong("read_count"),
                        toIso(r.getObject("created_at"))));
    }
}
