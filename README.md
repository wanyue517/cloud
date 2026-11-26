# Cloudlane 个人云端网盘系统

基于 **Spring Boot 4.2 / Java 17 + MySQL 8 + Redis + Vue 3 + TypeScript + shadcn-vue** 构建的自托管个人云端网盘系统，P0/P1 全阶段（阶段 01–14，T008–T178）已全部完成并验收，完整台账见 [收工验收台账](docs/收工验收台账.md)。

## 核心特性

- **多空间文件工作台**：个人空间、共享空间与密码解锁的私密空间；支持列表/网格双视图、目录树虚拟滚动、目录内全选、右键/三点统一操作菜单、10 秒撤销、批量重命名与 ZIP 打包下载、隐藏项密码验证显示。
- **分片上传与任务中心**：支持文件与文件夹独立上传、SHA-256 校验、断点续传、并发队列控制与独立任务中心页面。
- **全格式在线预览**：图片（Viewer.js）、PDF（PDF.js）、Markdown/代码（高亮+目录）、音频、视频（xgplayer + FFprobe 策略 + FFmpeg 转码）、Office（Gotenberg 转 PDF + Excel 表格虚拟滚动）、压缩包（ZIP/TAR 层级浏览 + Zip Slip / Zip Bomb 防护）。
- **协同共享与公开分享**：空间成员角色、共享文件夹子树最小权限、SSE 实时通知、公开分享链接（SHA-256 摘要 + 提取码 + 有效期）、站内消息中心。
- **全文检索与版本控制**：MySQL 组合检索 + Meilisearch + Apache Tika 全文分段索引（双层权限过滤防泄露）；文件历史版本保留、预览、恢复与系统/用户双层上限策略。
- **独立管理后台与可切换存储**：`/admin/` 独立应用，覆盖仪表盘、用户管理、消息发布、LOCAL / S3 / DB 多类型存储配置与加密凭据（AES-256-GCM）、站点设置、认证/SMTP/OAuth2 设置、文件策略、Worker 异步作业/死信重试、存储孤儿延迟回收与全链路请求观测日志。

## 快速开始与部署要求

完整生产环境要求、软硬件分级、必填环境变量、Docker Compose 多 Profile 启停、Nginx 调优、首次管理员初始化及备份恢复，请参见 **[部署与运维指南](docs/ops/部署与运维指南.md)**。

### 环境要求（最低 / 推荐）
- **必需基础设施**：MySQL 8.0 / 8.4（UTF8MB4 + UTC，Flyway 自动迁移 `V1`–`V15`）、Redis 7.x（建议开启 AOF）。
- **源码构建环境**：Java 17 LTS、Maven 3.9+、Node.js 22/24、pnpm 10+。
- **可选扩展服务**（通过 Docker Compose Profile 按需启用）：Gotenberg 8（`office`）、Meilisearch v1.11 + Apache Tika 2.9（`search`）、FFmpeg/FFprobe（非原生编码视频转码）。
- **硬件建议**：基础版 2–4 核 / 4–8 GB；完整版（含全文检索与 Office 转换）8 核 / 16 GB+ / SSD。
- **必填密钥**：复制 `.env.example` 为 `.env`，必须独立生成 32 字节 Base64 随机值填入 `JWT_SECRET`、`VERIFICATION_HMAC_SECRET` 与 `CLOUD_STORAGE_SECRET_KEY`（严禁复用或提交 `.env`）。

### 2. 启动基础设施与后端
```powershell
docker compose up -d mysql redis
# 可选按需启动 Office 转换或全文检索扩展服务：
# docker compose --profile office up -d
# docker compose --profile search up -d

mvn -f backend/pom.xml -pl cloud-api -am -B spring-boot:run
# 可选另开终端启动独立 Worker：
# mvn -f backend/pom.xml -pl cloud-worker -am -B spring-boot:run
```

### 3. 启动前端（用户端 + 管理后台）
```powershell
cd frontend
pnpm install --frozen-lockfile
pnpm dev
```
访问 `http://localhost:3000`（用户端 `/app`，管理后台 `/admin/`，API 代理至 `http://localhost:8000/api`）。全新空库首个注册用户自动获得 `SUPER_ADMIN` 角色。

## 容器化多 Profile 部署

```powershell
# 启动完整应用（含基础服务 + API + Worker + Nginx）
docker compose --profile app up -d --build

# 仅追加 Office 预览或全文检索服务
docker compose --profile office up -d
docker compose --profile search up -d
```
Nginx 配置位于 [frontend/nginx.conf](frontend/nginx.conf)，已针对大文件分片上传（`client_max_body_size 0` / 关闭请求缓冲）、媒体 Range 透传与 SSE 长连接（`proxy_buffering off` + `3600s` 超时）做专门调优。

## 验证、发布与运维

- **按模块打包发布**：`.\scripts\release.ps1 -Module all`（支持 `backend` / `web` / `admin` / `all`）
- **备份与恢复演练**：`.\scripts\backup.ps1`，详细步骤见 [备份恢复与演练指南](docs/ops/备份恢复与演练指南.md)
- **端到端 Playwright 测试**：`E2E_BASE_URL=http://localhost:3000 node frontend/tests/e2e/core-flows.spec.mjs`
- **单元测试与契约检查**：
  ```powershell
  mvn -f backend/pom.xml -pl cloud-api -am -B test
  node --test --experimental-test-isolation=none frontend/apps/web/tests/*.test.cjs
  ```
