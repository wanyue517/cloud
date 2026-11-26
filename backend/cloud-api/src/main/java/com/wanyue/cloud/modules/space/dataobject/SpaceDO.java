package com.wanyue.cloud.modules.space.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wanyue.cloud.common.domain.BaseDO;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * 空间资源容器；不创建伪根目录节点（space_space）持久化对象。
 * 公共字段由 BaseDO 定义，不重复生成。
 */
@Getter
@Setter
@TableName("space")
public class SpaceDO extends BaseDO {
    /** 权威所有者，关联sys_user.id；共享容量计入此用户 */
    @TableField("owner_user_id")
    private Long ownerUserId;
    /** PERSONAL个人/SHARED共享/PRIVATE私密 */
    @TableField("kind")
    private String kind;
    /** 空间显示名；同一所有者允许重名 */
    @TableField("name")
    private String name;
    /** 前端图标编码，不存任意HTML或脚本 */
    @TableField("icon")
    private String icon;
    /** 颜色值，格式为#RRGGBB */
    @TableField("color")
    private String color;
    /** 空间用途描述 */
    @TableField("description")
    private String description;
    /** ACTIVE/DELETING/DELETED；必须使用生命周期业务方法变更 */
    @TableField("status")
    private String status;
    /** 共享查看者是否允许下载原件 */
    @TableField("viewer_download_allowed")
    private Boolean viewerDownloadAllowed;
    /** 共享查看者是否允许读取历史版本 */
    @TableField("viewer_version_read_allowed")
    private Boolean viewerVersionReadAllowed;
    /** 权限变更版本，用于授权缓存及私密会话失效 */
    @TableField("access_version")
    private Long accessVersion;
    /** 服务端生成的全局幂等键；注册使用REGISTER:userId，删除后不复用 */
    @TableField("creation_request_key")
    private String creationRequestKey;
    /** 最近一次删除流程发起时间UTC */
    @TableField("delete_requested_at")
    private LocalDateTime deleteRequestedAt;
    /** 完成逻辑删除的时间UTC */
    @TableField("deleted_at")
    private LocalDateTime deletedAt;
    /** 允许进入永久清理的最早时间UTC，不代表可直接删除Blob */
    @TableField("purge_after")
    private LocalDateTime purgeAfter;
}

