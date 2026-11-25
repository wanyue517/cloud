package com.wanyue.cloud.modules.space.vo;

import lombok.Data;
import java.time.LocalDateTime;

/** 空间资源容器；不创建伪根目录节点新增请求：敏感认证字段不通过通用 CRUD 处理。 */
@Data
public class SpaceCreateReqVO {
    /** 权威所有者，关联sys_user.id；共享容量计入此用户 */
    private Long ownerUserId;
    /** PERSONAL个人/SHARED共享/PRIVATE私密 */
    private String kind;
    /** 空间显示名；同一所有者允许重名 */
    private String name;
    /** 前端图标编码，不存任意HTML或脚本 */
    private String icon;
    /** 颜色值，格式为#RRGGBB */
    private String color;
    /** 空间用途描述 */
    private String description;
    /** ACTIVE/DELETING/DELETED；必须使用生命周期业务方法变更 */
    private String status;
    /** 共享查看者是否允许下载原件 */
    private Boolean viewerDownloadAllowed;
    /** 共享查看者是否允许读取历史版本 */
    private Boolean viewerVersionReadAllowed;
    /** 权限变更版本，用于授权缓存及私密会话失效 */
    private Long accessVersion;
    /** 服务端生成的全局幂等键；注册使用REGISTER:userId，删除后不复用 */
    private String creationRequestKey;
    /** 最近一次删除流程发起时间UTC */
    private LocalDateTime deleteRequestedAt;
    /** 完成逻辑删除的时间UTC */
    private LocalDateTime deletedAt;
    /** 允许进入永久清理的最早时间UTC，不代表可直接删除Blob */
    private LocalDateTime purgeAfter;
}

