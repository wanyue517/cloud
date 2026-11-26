# T017 OpenAPI、Axios 与前端类型生成流程验收

日期：2026-11-26。状态：**已验收**。

## 范围与架构对齐

对照阶段 00 设计基线 [T007_命名错误码API响应与ID传输规范.md](file:///E:/wanyue/projects/cloud/docs/阶段00设计基线/T007_命名错误码API响应与ID传输规范.md) 第 9 节（ID 字符串传输）与第 13 节（OpenAPI 单一接口契约与构建漂移门禁），建立从后端控制器到前端 TypeScript DTO 的端到端同步生成流程：

- **后端 OpenAPI 3.0.3 自动扫描导出**：
  - 新增 [OpenApiSpecBuilder.java](file:///E:/wanyue/projects/cloud/backend/cloud-api/src/main/java/com/wanyue/cloud/common/openapi/OpenApiSpecBuilder.java) 与 [OpenApiController.java](file:///E:/wanyue/projects/cloud/backend/cloud-api/src/main/java/com/wanyue/cloud/common/openapi/OpenApiController.java)（基于 Spring MVC `RequestMappingHandlerMapping` 反射扫描所有 `/api/**` 端点，完全兼容 Spring Boot 4.2；仅在 `cloud.openapi.enabled=true` 时注册 `GET /v3/api-docs`，默认关闭不暴露生产内部结构）。
  - 严格遵循 T007：所有 `id`/`*Id` 64 位标识映射为 `string`，时间类型映射为 `date-time`，统一包装 `R<T>`（含 `code`、`msg`、`data`、`traceId`）。
  - 新增 [OpenApiExportTest.java](file:///E:/wanyue/projects/cloud/backend/cloud-api/src/test/java/com/wanyue/cloud/common/openapi/OpenApiExportTest.java)，真实启动 `cloud-api` 并通过 `MockMvc` 校验 `/v3/api-docs` 输出，并支持 `-Dcloud.openapi.export=true` 导出版本化契约 [docs/api/openapi.json](file:///E:/wanyue/projects/cloud/docs/api/openapi.json)。
- **前端类型生成与漂移门禁（`frontend/packages/api-client`）**：
  - 新增 [generate-types.mjs](file:///E:/wanyue/projects/cloud/frontend/packages/api-client/scripts/generate-types.mjs)：读取 `docs/api/openapi.json`（或 `--url http://127.0.0.1:8000/v3/api-docs` 实时拉取），确定性生成 [openapi.d.ts](file:///E:/wanyue/projects/cloud/frontend/packages/api-client/src/generated/openapi.d.ts)（包含 `paths`、`components['schemas']`、`ApiSchemas`、`ApiPaths`、`ResourceId`），并提供 `--check` 漂移检测门禁。
  - 在 [frontend/package.json](file:///E:/wanyue/projects/cloud/frontend/package.json) 注册 `pnpm api:generate` 与 `pnpm api:check`。
- **Axios 封装与前端消费对齐**：
  - 更新 [request.ts](file:///E:/wanyue/projects/cloud/frontend/packages/session/request.ts) 中 `R<T>` 补全 `traceId?: string | null`。
  - 在 `apps/web` 与 `apps/admin` 的 `tsconfig.app.json` 注册 `@repo/api-client` 路径映射，[drive.ts](file:///E:/wanyue/projects/cloud/frontend/apps/web/src/api/drive.ts) 直接基于生成类型 `ApiSchemas` 派生 `Space`、`DriveNode`、`NodePage`、`Version`、`Upload`。

## 验证结果

| 项目 | 实际结果 |
|---|---|
| 后端 OpenAPI 导出 | `mvn -f backend/pom.xml -pl cloud-api -am -B "-Dtest=OpenApiExportTest" "-Dcloud.openapi.export=true" test` 通过，成功导出 `docs/api/openapi.json` |
| 前端类型生成与漂移门禁 | `node frontend/packages/api-client/scripts/generate-types.mjs` 生成 2238 行 `openapi.d.ts`，`--check` 返回 `OpenAPI types are up to date.` |
| 前端单元测试 | 32/32 通过（新增 OpenAPI 契约漂移校验与 ID 字段为 `string` 的回归断言） |
| 类型检查与构建 | `web`（已消费 `@repo/api-client`）与 `admin` 两端 `vue-tsc` 零错误通过，`web` 生产构建成功 |
