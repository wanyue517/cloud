# T007｜工程命名、错误码、API 响应与 ID 传输规范

> 阶段 00 · 设计基线 V1.0 · 跨前端、后端、数据库、任务 Worker 统一采用  
> 主要目标：减少接口字段风格不一致、前后端类型反复映射、长整型精度损失、错误处理和权限响应混乱。

## 1. 技术基线

| 层级 | 统一技术 | 编码/规范 |
|---|---|---|
| 前端 | Vue 3 + TypeScript + Vite | UTF-8、ESM、ESLint/Oxlint + 格式化 |
| UI | shadcn-vue + Tailwind CSS | Design Tokens，组件命名 PascalCase |
| 后端 | Java 21 + Spring Boot 4.x | Maven 多模块，Java 17+ 语法可按兼容性受控使用 |
| 数据层 | MySQL 8.4 + MyBatis-Plus | 表/列 snake_case；索引命名标准化 |
| HTTP | REST 风格 JSON | `/api/v1` 统一前缀，OpenAPI 3 为接口契约 |
| ID | 服务端 BIGINT（如雪花 ID） | 前端和 JSON 中**全部按字符串传输** |
| 时间 | UTC + ISO 8601 | `2026-11-21T14:00:00.000Z`；前端依用户时区显示 |
| 二进制/文件 | 原始字节流 | API 不在普通 JSON 里传 Base64 大文件 |

**依赖版本**：固定到受测试的精确版本并提交 lockfile / Maven BOM；前端使用 `pnpm-lock.yaml`，CI 与本地构建保持一致。

## 2. 项目与包命名

推荐 Monorepo：

```text
apps/web
apps/admin
packages/ui
packages/api-client
packages/shared
packages/preview-core
server/cloud-api
server/cloud-worker
server/cloud-file
server/cloud-space
server/cloud-storage
server/cloud-share
```

规则：
- 文件夹：`kebab-case`，例如 `file-preview`。
- Vue 单文件组件：`PascalCase.vue`，例如 `FileTreeNode.vue`、`ShareMemberDialog.vue`。
- Vue 组合函数：`useCamelCase.ts`，例如 `useFileSelection.ts`。
- 普通 TS 模块：`kebab-case.ts`，例如 `format-file-size.ts`。
- TS 变量、函数：`camelCase`；类、接口、组件：`PascalCase`；枚举值：`UPPER_SNAKE_CASE`。
- Java 类：`PascalCase`；方法/字段：`camelCase`；常量：`UPPER_SNAKE_CASE`。
- Java 包：小写、按业务模块划分，例如 `com.cloudlane.file.service`（`com.cloudlane` 为暂定前缀，建仓时统一替换）。
- 数据库表/列：`snake_case`，例如 `file_node`, `current_version_id`。
- 表索引：`idx_<table_or_short>_<columns>`；唯一约束：`uk_<table_or_short>_<columns>`。
- API 路径：小写复数名词 + `kebab-case`，不要在路径中用驼峰或直接写业务动词代替资源。

## 3. 数据实体、VO 和服务命名

建议：

| 用途 | 示例 | 约定 |
|---|---|---|
| 数据库实体 | `FileNodeDO` | 只承担持久化模型 |
| 请求对象 | `CreateFolderReqVO` | 对外接口校验、字段与 OpenAPI 对齐 |
| 响应对象 | `FileNodeRespVO` | 不返回内部存储凭证/物理 Key |
| Service | `FileNodeService` | 业务动作 |
| Mapper | `FileNodeMapper` | 数据访问 |
| Controller | `FileNodeController` | HTTP 边界 |
| 前端 DTO | 从 OpenAPI 生成 | 不手写不必要的 VO 重复映射 |
| 前端页面 | `FileWorkspacePage.vue` | 页面与业务组件分离 |

避免把所有文件操作都集中在一个超大 `FileService` 中；空间、权限、上传、版本、预览和存储各自负责领域逻辑。

## 4. REST 路由约定

| 资源 | 请求示例 | 说明 |
|---|---|---|
| 用户 | `GET /api/v1/users/me` | 当前用户信息 |
| 认证 | `POST /api/v1/auth/login/password` | 登录命令接口允许清晰动作命名 |
| 空间 | `POST /api/v1/spaces` | 创建空间 |
| 空间 | `GET /api/v1/spaces` | 我拥有与加入的空间 |
| 文件夹列表 | `GET /api/v1/spaces/{spaceId}/nodes?parentId=0&pageNo=1&pageSize=100` | 直属节点 |
| 文件夹创建 | `POST /api/v1/folders` | 创建子目录 |
| 文件夹移动 | `POST /api/v1/nodes/{id}/move` | 复杂非 CRUD 业务动作 |
| 文件重命名 | `PATCH /api/v1/nodes/{id}` | 修改元数据 |
| 上传初始化 | `POST /api/v1/uploads` | 创建上传会话 |
| 上传分片 | `PUT /api/v1/uploads/{uploadId}/parts/{partNo}` | 分片流 |
| 上传提交 | `POST /api/v1/uploads/{uploadId}/complete` | 幂等完成 |
| 下载 | `GET /api/v1/files/{fileId}/content` | Range 支持 |
| 版本 | `GET /api/v1/files/{fileId}/versions` | 历史版本 |
| 共享 | `POST /api/v1/resource-shares` | 新建共享授权 |
| 共享入口 | `GET /api/v1/resource-shares/received` | 我收到的目录 |
| 管理后台 | `GET /api/v1/admin/users` | 管理员强制校验 |

> 文件公开分享使用另一个带分享令牌的访问上下文，不把用户常规文件 API 直接变成免登录接口。

## 5. 统一 JSON 响应格式

**成功**：

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "id": "9007199254740993",
    "name": "项目资料",
    "parentId": "0",
    "spaceId": "10001"
  },
  "traceId": "4a7f1d9c3fef4ac5"
}
```

**业务失败**：

```json
{
  "code": 14002,
  "msg": "目标目录已存在同名文件夹",
  "data": null,
  "traceId": "4a7f1d9c3fef4ac5"
}
```

### 5.1 顶层字段

| 字段 | 类型 | 规则 |
|---|---|---|
| `code` | integer | `0` 成功，非 0 表示应用错误；与 HTTP 状态协同使用 |
| `msg` | string | 面向用户/开发者的简短信息，不能泄露 SQL、Key、栈信息 |
| `data` | object/array/null | 成功的业务对象；失败固定 `null` |
| `traceId` | string | 服务端生成的请求追踪 ID，便于定位日志 |

必要时可使用 `errors` 数组表达表单校验细节（统一定义，不能让不同接口随意更换数据形态）。

### 5.2 分页响应

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "list": [
      { "id": "10001", "name": "设计资料", "nodeType": "FOLDER" }
    ],
    "pageNo": 1,
    "pageSize": 50,
    "total": 1
  },
  "traceId": "4a7f1d9c3fef4ac5"
}
```

分页参数：`pageNo` 从 1 开始，`pageSize` 有系统最大值；对于数据持续变化的大型列表可增加游标分页，响应形如 `nextCursor, hasMore`，但不在同一个接口随意混用分页模式。

## 6. HTTP 状态与业务错误码

不要全部响应 HTTP 200，再单靠 `code` 判断。HTTP 状态与业务错误码都必须正确。

| HTTP | 适用场景 | 示例业务码 |
|---|---|---|
| 200 | 查询、更新、业务动作成功 | 0 |
| 201 | 创建成功 | 0 |
| 204 | 某些无响应体删除成功（若使用须统一例外） | 无 JSON 响应体 |
| 400 | 参数非法、数据不符合基本请求格式 | 10001 |
| 401 | 未登录、凭证过期 | 11001 |
| 403 | 登录但资源/动作无权 | 11003 |
| 404 | 资源不存在或按安全策略隐藏存在性 | 14001 |
| 409 | 同名冲突、版本冲突、非法状态转换 | 14002 / 14003 |
| 413 | 上传超大小限制 | 20001 |
| 429 | 验证码/请求频率超限 | 11008 |
| 500 | 不可预期服务错误 | 10000 |
| 503 | 下游存储/转换服务不可用 | 16003 / 17003 |

**注意**：后台前端路由针对非管理员显示 404；后台 API 对已登录普通用户执行时仍应返回 403。不要在后端强行把所有拒绝伪装为成功。

## 7. 错误码分段

| 范围 | 模块 | 样例 |
|---|---|---|
| 10000—10999 | COMMON 通用 | `10001 INVALID_ARGUMENT` |
| 11000—11999 | AUTH 认证/权限 | `11001 UNAUTHENTICATED`、`11003 FORBIDDEN` |
| 12000—12999 | USER 用户 | `12001 USER_NOT_FOUND` |
| 13000—13999 | SPACE 空间 | `13001 SPACE_NOT_FOUND`、`13003 SPACE_OWNER_REQUIRED` |
| 14000—14999 | FILE 文件/目录 | `14001 FILE_NOT_FOUND`、`14002 NAME_CONFLICT`、`14003 VERSION_CONFLICT`、`14004 INVALID_MOVE` |
| 15000—15999 | SHARE 共享/链接 | `15001 SHARE_EXPIRED`、`15002 SHARE_REVOKED` |
| 16000—16999 | STORAGE 存储 | `16001 STORAGE_PROFILE_NOT_FOUND`、`16003 STORAGE_UNAVAILABLE` |
| 17000—17999 | PREVIEW 预览 | `17001 PREVIEW_UNSUPPORTED`、`17003 PREVIEW_SERVICE_UNAVAILABLE` |
| 18000—18999 | SEARCH 搜索 | `18001 SEARCH_INDEX_UNAVAILABLE` |
| 19000—19999 | SYSTEM 系统设置 | `19001 SETTING_INVALID` |
| 20000—20999 | UPLOAD 上传 | `20001 FILE_TOO_LARGE`、`20002 UPLOAD_SESSION_EXPIRED` |

示例码属于这套项目的建议登记值，正式实现需要在共享的错误码注册表中保证唯一且禁止复用已发布的代码。

## 8. 表单校验错误结构

```json
{
  "code": 10001,
  "msg": "参数校验失败",
  "data": null,
  "traceId": "4a7f1d9c3fef4ac5",
  "errors": [
    { "field": "name", "reason": "名称不能为空" }
  ]
}
```

HTTP 400；错误字段名必须和公开请求对象字段一致，前端可直接映射到表单字段提示。

## 9. ID 传输与精度

### 9.1 硬性要求

**BIGINT / Snowflake ID 无论在 URL、JSON、Pinia 还是 TypeScript，全部视为字符串。**

```ts
export type ResourceId = string

interface FileNodeRespVO {
  id: ResourceId
  spaceId: ResourceId
  parentId: ResourceId
  name: string
}
```

不可：

```ts
// 错误：数值超过 2^53 - 1 时会失去精度
const id = Number(response.data.id)
```

Java 层可以使用 `Long`，统一由 Jackson 序列化配置把 Long ID 输出为 JSON string；不要把所有金额、计数、文件大小字段也无差别转换成字符串。

### 9.2 根目录表示

根目录的 `parentId` 统一传字符串 `"0"`。空值 `null` 只用于字段本身不存在的语义，例如文件夹的 `currentVersionId`。

### 9.3 大文件大小

`sizeBytes` 内部使用 Java `long`；JSON 中在预计可能超过 JS 安全整数范围时使用字符串或项目统一的精确整数字段协议。P0 常规文件大小可使用不超过 JS 安全整数范围的 number，但必须在 OpenAPI 契约中约束并编写转换函数；不能随意混用。

## 10. 时间、枚举、单位

- 服务端时间使用 `Instant` 或清晰的 UTC 时间类型，JSON ISO 8601 UTC 字符串。
- UI 根据用户时区本地格式化；不把展示文本直接存数据库。
- 文件大小以 byte 作为原始存储单位，UI 可显示 KiB/MiB/GiB。
- 时长在 API 中以 `durationMs` 等带单位字段表达。
- 百分比推荐用 `progressPercent`（0—100），不得与 0—1 小数比例混淆。
- 枚举大写固定值：`PERSONAL / SHARED / PRIVATE`、`FILE / FOLDER`、`PENDING / RUNNING / SUCCESS / FAILED`。

## 11. 幂等、并发与分页细则

### 11.1 幂等
- 上传 `complete`：必须幂等；重复请求返回同一个结果，不重复创建 FileNode。
- 分享链接重复创建是否去重按明确业务策略，不暗含幂等。
- 其他重要写请求可通过 `Idempotency-Key` 进行请求去重。

### 11.2 并发
- 文件重命名、移动、版本恢复等涉及并发修改的操作携带 `revision`/`expectedVersionId` 或 `If-Match`。
- 并发冲突返回 HTTP 409，不能悄悄覆盖别人的改动。

### 11.3 查询
- 列表查询必须有明确的分页或游标限制，不允许无限制返回所有节点。
- 文件树只请求当前父节点直属子项。
- 排序字段采用允许列表映射，防止客户端拼接 SQL 列名。

## 12. 鉴权与安全响应

- 身份与资源授权由服务端校验；前端路由守卫只是体验优化。
- 认证失败：HTTP 401；已认证无权：HTTP 403；可为防止敏感资源存在性泄露在文件详情等场景返回 404，但策略必须稳定。
- 私密空间未解锁不得通过错误详情泄露真实文件名、Hash、目录结构。
- API 不返回 `StorageProfile.secretKey`、原始 `object_key`、数据库内部物理路径。
- 原文件下载支持 HTTP Range 时，`206`/`416` 使用标准 HTTP 语义，流式响应不强制套 JSON 包装。

## 13. OpenAPI 及前端类型生成

采用 OpenAPI 作为单一接口契约：

1. 后端维护 Controller + 请求/响应 VO 的 schema。
2. CI 导出 `/v3/api-docs` 或版本化规范文件。
3. 使用 `openapi-typescript` 或已验证的生成工具产出 `packages/api-client` 类型。
4. 前端业务层仅使用生成 DTO 和少量 UI 领域类型。
5. 接口变更导致类型生成差异时，必须在构建检查中暴露，不允许以 `any` 长期绕过。

## 14. 日志与可观测性

- 每次 API 请求生成 `traceId`，响应头 `X-Trace-Id` 并在 JSON 响应中回传。
- 关键操作审计包含 actorId、resourceId、action、result、time、traceId。
- 不记录密码、短信验证码、第三方 OAuth 令牌、完整上传签名 URL、S3 凭据。
- 错误日志记录内部堆栈，响应给用户只提供安全的 `msg` 和 `traceId`。

## 15. 接口样例：文件夹创建

**请求**

```http
POST /api/v1/folders
Content-Type: application/json
Authorization: Bearer <access-token>

{
  "spaceId": "10001",
  "parentId": "0",
  "name": "项目资料"
}
```

**响应**

```http
HTTP/1.1 201 Created
Content-Type: application/json
X-Trace-Id: 4a7f1d9c3fef4ac5

{
  "code": 0,
  "msg": "success",
  "data": {
    "id": "9007199254740993",
    "spaceId": "10001",
    "parentId": "0",
    "name": "项目资料",
    "nodeType": "FOLDER",
    "hasChildren": false,
    "revision": 0,
    "createdAt": "2026-11-21T14:00:00.000Z"
  },
  "traceId": "4a7f1d9c3fef4ac5"
}
```

## 16. 验收清单与统一代码检查

- [x] 定义了前后端、数据库、API 命名风格。
- [x] 定义 `code/msg/data/traceId` 与 HTTP 状态对应。
- [x] 统一 BIGINT 为 JSON string、客户端 string。
- [x] 根目录、分页、时间、枚举和单位约定明确。
- [x] 定义接口幂等、版本并发、请求追踪与安全边界。
- [x] 给出创建文件夹请求与响应完整例子。
- [ ] 具体 Java 包名前缀、CI 命令与 DTO 生成工具在 T008/T014/T017 创建仓库时最终锁定。

**T007 完成定义：前后端可据此进行并行工程初始化和类型化 API 开发。**
