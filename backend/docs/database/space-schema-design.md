# 空间数据库设计

空间采用独立资源容器模型，所有权由 `space.owner_user_id` 确定。基础建表覆盖个人空间、共享空间成员及用户导航偏好；邀请与私密空间提供独立的 P1 扩展脚本。所有业务表沿用现有 MyBatis-Plus `BaseDO`，本次不生成接口、Service 或 Mapper，也不执行当前 `cloud` 库的迁移。

## 设计依据

| 文档 | 落地规则 |
|---|---|
| `../../../docs/阶段00设计基线/T002_空间资源归属与生命周期.md` | PERSONAL、SHARED、PRIVATE；共享文件夹不是新空间；所有权、转换、退出与删除边界 |
| `../../../docs/阶段00设计基线/T004_共享权限矩阵与继承规则.md` | OWNER、MANAGER、EDITOR、VIEWER；平台角色与资源角色分离；私密空间禁止共享 |
| `../../../docs/阶段00设计基线/T005_导航信息架构与页面路由清单.md` | 自有和受邀空间混排；排序及折叠按用户保存；偏好不授予访问权 |
| `../../../docs/阶段00设计基线/T003_文件版本与物理对象三层模型.md` | 文件通过 space_id 归属；逻辑容量与物理占用分开；共享容量计入所有者 |
| `../../../docs/阶段00设计基线/T007_命名错误码API响应与ID传输规范.md` | BIGINT 雪花ID、snake_case、UTC、前端ID字符串 |
| `../../../docs/网盘开发计划_2小时任务.md` | T028–T030 基础空间；T097–T099 共享邀请；T143–T145 私密验证 |
| `../../../docs/个人云端网盘系统需求说明书.md` 第十六章及技术架构 DOCX | 授权来源独立；短期私密验证；文件与物理存储解耦 |

文档未给出完整 DDL。以下默认策略是本次设计选择：允许空间重名；首个版本以独立密码完成私密验证；建议空闲15分钟锁定、单次解锁最多1小时。这些时长保存在配置行，可调整。

## 表与关系

| 阶段 | 表 | 职责 |
|---|---|---|
| V3 基础 | `space` | 所有者、类型、名称、展示、共享查看者策略与生命周期 |
| V3 基础 | `space_member` | 仅共享空间的有效成员、资源角色及到期时间 |
| V3 基础 | `space_user_preference` | 每个用户对每个空间的排序、目录树折叠 |
| P1 扩展 | `space_invitation` | 向邮箱发起的共享空间邀请及接受记录 |
| P1 扩展 | `space_private_security` | 私密空间独立验证密码哈希与锁定时长 |
| P1 扩展 | `space_unlock_session` | 绑定普通登录会话的短期私密访问授权 |

`sys_user` 一对多拥有 `space`。`space` 与 `sys_user` 通过 `space_member` 形成共享成员关系；偏好分别关联用户与空间。私密空间对应一份有效验证配置及多份解锁会话。`sys_user_setting.default_space_id` 继续保存用户首选入口，不在空间表重复保存“是否默认”。

共享文件夹的 `resource_share`、公开链接 `share_link`、文件节点 `file_node` 及闭包表属于后续文件/分享模块，不在此次建表中混入。文件根目录采用 `parent_id=0`，空间不另建伪文件夹，也不保存冗余 `root_folder_id`。

## 公共字段与约束

所有表都有 `id, created_by, created_at, updated_by, updated_at, deleted, version`；`deleted=0` 有效，删除后 `deleted=id`。ID 在 Java 中使用 Long，接口中使用字符串。生成辅助列不映射为普通可写 DO 字段。

`DATETIME(3)` 保存 UTC。现有 JDBC 的 `connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true` 必须保留；Worker、运维脚本和其他写入连接也必须执行 `SET time_zone='+00:00'`。数据库默认时间依赖连接会话，不能仅靠字段注释保证 UTC。

沿用现有库的逻辑关联，不新增物理外键。MySQL CHECK、唯一索引保证单行状态及唯一性；有效用户、空间类型、成员所属和所有者一致性属于跨表规则，必须由 Service 事务校验，不能把“数据库有索引”当作已完成业务鉴权。

## 空间主表

| 字段组 | 字段 | 规则 |
|---|---|---|
| 所有权 | `owner_user_id` | 非空、正整数；引用有效用户，文件创建者不替代所有者 |
| 分类 | `kind` | PERSONAL、SHARED、PRIVATE；大小写严格匹配 |
| 展示 | `name, icon, color, description` | 名称1–50字符，允许同用户重名；服务器校验图标白名单和颜色格式 |
| 共享限制 | `viewer_download_allowed, viewer_version_read_allowed` | 默认允许；仅限制对应查看者动作，不用于授予额外动作 |
| 授权失效 | `access_version` | 所有权、成员、共享策略、删除、恢复等安全变更时递增 |
| 幂等 | `creation_request_key` | 可空、全局唯一且删除后不复用；注册为服务端生成的 `REGISTER:<userId>` |
| 删除 | `status, delete_requested_at, deleted_at, purge_after` | 由删除状态机一起维护，见下文 |

空间名称不建唯一索引。P0 允许多个个人空间；创建共享或私密空间前必须具备对应业务实现，不能因为 DDL 接受该枚举就向用户开放创建。

`access_version` 专门处理授权变化；`version` 保留 MyBatis-Plus 乐观并发语义。纯名称/颜色/排序变更无需使所有私密令牌失效。

## 共享成员与个人偏好

`space_member` 中同一 `space_id + user_id` 最多一条有效记录，退出/移除后允许新增关系。生成列 `owner_space_id` 的唯一索引保证同一共享空间最多一个有效 OWNER。OWNER 不能配置过期时间。

数据库不能保证“恰好一个 OWNER，且等于 space.owner_user_id”。创建、转换和转让必须锁定空间行，在同一事务内完成主表和 OWNER 关系。PERSONAL 和 PRIVATE 不写成员行；所有者权限从主表直接推导。

有效成员条件是 `deleted=0 AND (expires_at IS NULL OR expires_at>当前UTC时间)`。到期不等待后台清理才失效；OWNER 不能直接退出，MANAGER 不能修改 OWNER 或转让所有权。

排序统一存 `space_user_preference.sort_order`，不把所有者排序和受邀者排序放在同一个空间字段。T002 概念层的 sortOrder 在物理模型中映射到这张表。创建空间和接受邀请时初始化偏好，缺失时按0回退，最后以 `space_id` 稳定排序。`tree_collapsed` 同样属于用户个人状态。

偏好记录可以滞后清理，但绝不授予访问权。成员被移除、空间删除或身份失效后，即使偏好仍在，列表也不能显示该空间。

## 邀请与私密验证

邀请仅作用于 SHARED 空间，目标角色不能是 OWNER。接收邮箱按现有 `EmailNormalizer` 规范化；接受人必须拥有对应的有效且已验证 `auth_identity`。邀请令牌只存摘要，原令牌不进日志。邀请未接受前不创建成员。

`space_invitation` 的生成列保证每空间、每邮箱最多一份 PENDING 邀请。过期不能由依赖当前时间的生成列自动消失；发起新邀请前，在事务内把旧的已过期 PENDING 行置为 EXPIRED，再插入。接受、拒绝、撤销与过期必须写终态时间；只有 ACCEPTED 才允许有接受用户ID。

私密空间先创建验证配置，再与空间主记录同事务提交。独立验证密码使用现有自适应 PasswordEncoder，不复制账号密码哈希，不宣称内容已加密。错误次数和限流在 Redis 处理，避免高频失败请求持续写主表。

解锁会话保存独立令牌摘要、`space_id + user_id + auth_session_id`、凭证版本及空间授权版本快照，同时检查绝对与空闲到期时间。每次访问还要校验：用户有效、仍是所有者、空间 ACTIVE/PRIVATE、账号会话未过期未撤销、账号会话版本有效、私密配置有效且版本相同、空间授权版本相同、令牌未撤销。

安全访问后的续期为 `idle_expires_at=min(当前UTC时间+idle_timeout_seconds, expires_at)`，不能延长绝对有效期。超过有效期的会话不能靠续期复活；主动锁定、登出、修改私密密码、空间删除均立即失效。元数据、原件、缩略图、搜索和派生预览使用同一校验。平台 SUPER_ADMIN 不自动获得私密访问权。

PRIVATE 不允许成员、文件夹授权或公开链接。普通 PERSONAL/SHARED 不直接原地转 PRIVATE，以免旧分享凭证绕过验证；若以后开放，需单独设计迁移与凭证撤销。

## 关键事务

### 注册默认空间

注册事务在用户及设置创建后调用空间 Service：用 `REGISTER:<userId>` 幂等创建 PERSONAL「我的空间」，初始化本人偏好，将 `sys_user_setting.default_space_id` 设为新空间ID。任一步失败回滚注册；若日后改为异步，必须通过可靠事件和重试完成，不能留下永久无空间账号。

旧用户补建由独立的一次性初始化任务执行，不在结构迁移中拼凑雪花ID。重试先查幂等键，查到已删除记录时不复活、不重复创建。注册初始化幂等与用户之后选择默认入口是两个概念；默认入口不可访问时回退到可访问的个人空间。

### 转为共享

锁定空间，校验当前所有者和 ACTIVE/PERSONAL，更新 kind 为 SHARED，插入本人 OWNER，递增 access_version，在同一事务提交。Space ID、文件树、文件版本和 Blob 均保持不变。

SHARED 转 PERSONAL 默认关闭。开放时必须先处理其他成员、未完成邀请及派生授权，二次确认后同事务清理成员关系并转换。不能只修改 kind。

### 接受与撤销邀请

锁顺序统一为“空间行、邀请行、成员行”，避免并发接受/撤销死锁。检查状态、到期时间、邮箱验证及邀请人当前权限；按唯一成员键创建或识别已有成员，不能重复插入，也不能静默覆盖现有角色；写 ACCEPTED 终态、偏好和 access_version 后提交。重复接受同一已接受邀请仅对原接受者幂等。

撤销授权、退出、改角色也先锁空间；写成员变化并递增 access_version。缓存失效事件随业务事务写可靠事件记录，禁止仅靠前端或异步搜索刷新撤权。

### 所有权转让

P1 服务实现时要求双方确认。短期确认凭证绑定空间、原所有者、新所有者、预期 access_version 和到期时间，可存 Redis；不能复用普通成员邀请。锁空间后重检双方有效状态、同意凭证和目标用户配额，再把原 OWNER 降级、更新 owner_user_id、把新所有者升级 OWNER并递增 access_version，同事务提交。事务中先移除旧 OWNER 身份再新增 OWNER，以符合唯一索引。

转让后空间内容计入新所有者配额，原所有者角色必须由转让请求明确。PRIVATE 暂不支持转让；确认凭证提交后消费，配合期望版本防重放。需要长期审批或管理员应急工单时另建转让流程表，不扩充普通邀请状态。

## 删除和恢复

| 状态 | deleted | 时间字段 | 访问规则 |
|---|---:|---|---|
| ACTIVE | 0 | 三个删除字段为空 | 按当前所有权和授权检查 |
| DELETING | 0 | 仅 delete_requested_at 非空 | 禁止普通访问和新写入 |
| DELETED | id | 三个字段非空，purge_after不早于deleted_at | 回收恢复流程专用 |

`space` 的 CHECK 明确绑定状态与逻辑删除值。不能对空间直接调用通用 `removeById`；生命周期方法使用条件更新同时写状态、时间、deleted和版本，失败返回冲突。普通 MyBatis-Plus 查询仍需加 `status='ACTIVE'`，因为 DELETING 尚未设置 deleted。

删除发起时即递增 access_version，并可靠安排异步文件逻辑删除任务；成员、邀请、分享和私密会话立即受空间状态阻断。任务完成后才进入 DELETED。部分任务失败不能简单把 status 改回 ACTIVE；先完成一致的安全补偿，或保持 DELETING 重试。

恢复必须在回收期限内校验所有者、配额和文件恢复状态，重置删除字段，递增 access_version。安全默认值为不自动恢复外部授权：先撤销旧邀请、外部成员、文件夹分享和旧解锁会话；共享空间只恢复 OWNER，其余成员重新邀请。

永久清理仅在期限、作业及实际引用均满足条件后执行。空间删除不直接删除物理 Blob；文件版本和引用清理由 T003 的文件生命周期负责。

## 查询与索引

导航先收集自有空间及有效成员空间，再关联偏好；不能从偏好表直接推导可见空间。

```sql
SELECT s.id, s.name, s.kind, s.owner_user_id,
       CASE WHEN s.owner_user_id = :user_id THEN 'OWNER' ELSE m.role END AS effective_role,
       COALESCE(p.sort_order, 0) AS sort_order
FROM (
    SELECT id FROM space
    WHERE owner_user_id = :user_id AND deleted = 0 AND status = 'ACTIVE'
    UNION
    SELECT sm.space_id FROM space_member sm
    JOIN space shared_space ON shared_space.id = sm.space_id
    WHERE sm.user_id = :user_id AND sm.deleted = 0
      AND (sm.expires_at IS NULL OR sm.expires_at > UTC_TIMESTAMP(3))
      AND shared_space.kind = 'SHARED' AND shared_space.deleted = 0 AND shared_space.status = 'ACTIVE'
) accessible
JOIN space s ON s.id = accessible.id
LEFT JOIN space_member m ON m.space_id = s.id AND m.user_id = :user_id AND m.deleted = 0
    AND (m.expires_at IS NULL OR m.expires_at > UTC_TIMESTAMP(3))
LEFT JOIN space_user_preference p ON p.space_id = s.id AND p.user_id = :user_id AND p.deleted = 0
ORDER BY COALESCE(p.sort_order, 0), s.id;
```

此查询允许向所有者返回私密空间的导航标识，以便显示解锁入口；不返回文件目录、缩略图或其他受保护内容。私密空间 API 必须另行检查解锁会话。

| 索引 | 支持的操作 |
|---|---|
| space(owner_user_id,deleted,status,id) | 获取自有有效空间 |
| space(status,purge_after,id) | 分批获取待清理空间 |
| space_member(space_id,user_id,deleted) 唯一 | 查成员、避免重复加入、退出后重新加入 |
| space_member(user_id,deleted,space_id) | 获取受邀空间 |
| space_member(owner_space_id) 唯一 | 防止两个有效 OWNER |
| preference(user_id,space_id,deleted) 唯一 | 按用户关联空间偏好 |
| preference(user_id,deleted,sort_order,space_id) | 批量读取用户导航排序 |
| invitation(space_id,pending_email) 唯一 | 防重复待处理邀请 |
| invitation(status,expires_at,id) | 邀请过期结算 |
| unlock(token_hash) 唯一 | 根据令牌摘要定位解锁授权 |
| unlock(auth_session_id,deleted) | 登出时撤销关联私密会话 |
| unlock(expires_at,id)及(idle_expires_at,id) | 分批清理到期会话 |

复杂混排可能产生排序临时结果，这是集合合并后的正常开销；先约束用户可加入的空间数量，数据量增大后依据真实执行计划优化，不承诺每一段均无需 filesort。

## 容量归属与后续边界

容量统一以 `owner_user_id` 为计费主体。多个空间不能各自获得同一份用户配额；共享成员上传不得消耗上传者配额。以后接入上传提交时，在用户配额行上原子预占/结算，空间转让和跨所有者移动也需校验目标配额。

当前没有文件版本表，不在空间表预置无法维护的 used_bytes、文件数或成员数作为权威字段。逻辑容量应按保留版本与回收策略统计，物理容量由 Blob/存储模块单独统计；需要优化时再加可校准的空间统计表。用户全局配额表、上传预留及可靠事件表随相应模块落地，本次建表不表示已实现容量执法或删除作业。

## 交付和验证

- 正式结构迁移：`../../src/main/resources/db/migration/V3__create_space_schema.sql`，仅新增3表，不补建旧用户数据。
- P1 扩展设计稿：`space-p1-schema.sql`，位于 Flyway 扫描目录之外，功能实现前转换为新的版本迁移。
- 验证脚本：`../../scripts/test-space-schema.py`，在自动命名的本地临时数据库重放 V1、V2、V3及P1扩展，验证约束与典型查询后清理临时库。
- 此次不启动应用或在现有 cloud 库执行 V3。之后应用启动会按既有 Flyway 配置执行 V3；生产发布前备份，并先部署兼容读取逻辑。
- MySQL DDL 不保证跨语句事务回滚。结构回退应先确认表无业务数据；有数据时采用前向修复，不能直接丢表或篡改已执行迁移。

验收要求：同名空间可共存；注册创建幂等；有效成员和OWNER不重复；导航偏好独立且不越权；删除状态一致；邀请终态完整；私密令牌不跨用户/空间/账号会话复用；版本变更即时阻断旧授权。跨表事务和完整HTTP鉴权在后续 Service/API 实现时补充集成测试。
