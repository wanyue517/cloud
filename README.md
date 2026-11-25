# Cloudlane

个人云端网盘，Spring Boot 4.2 / Java 17、MySQL 8、Redis、Vue 3、TypeScript、shadcn-vue。完整需求及完成情况见 [收工验收台账](docs/收工验收台账.md)。当前仍在开发，不能将本分支视为 P0/P1 已全部验收。

## 分支

`main` 的 `91d0f62` 保存接手时的完整项目；后续实现在 `codex`。原 `frontend` Git 仓库已保存当前快照，其完整元数据保留在本机 `.repository-backups/frontend.git`，不参与发布。

## 本地启动

需要 Java 17、Maven、Node 24、pnpm 11.25、MySQL 8 和 Redis。现有项目使用 Java 17，尚未升级设计文档中的 Java 21。

复制 `.env.example` 为 `.env` 并填写数据库、SMTP 和独立密钥；Compose 自动读取此文件，直接运行 Maven 时须将变量设置到当前终端环境。JWT 和验证码密钥各使用独立的至少 32 字节随机值。可在 PowerShell 中分别生成：

```powershell
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
[Convert]::ToBase64String($bytes)
```

验证码密钥的格式及 SMTP 配置见 [邮件配置](backend/MAIL_SETUP.md)。本机原配置已保存在 `backend/application-local.yaml`，该文件仅供本机使用且被 Git/Docker 忽略；从 `backend` 目录启动会自动读取。有本机配置时，使用 Spring 标准环境变量 `SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD` 覆盖数据库配置。

```powershell
docker compose up -d mysql redis
cd backend
mvn -B spring-boot:run
```

另开终端：

```powershell
cd frontend
pnpm install --frozen-lockfile
pnpm dev
```

前端由独立web/admin应用和Vue共享组件、会话包组成；`pnpm dev`同时启动两者，后台通过用户端同源代理访问，不能从3001端口单独登录。分别使用`pnpm check:web`、`pnpm check:admin`、`pnpm build:web`、`pnpm build:admin`检查/构建；API固定为`/api`，后端地址由`API_PROXY_TARGET`配置。详细证据及剩余差距见[T008验收记录](docs/T008_前端工程结构与验收.md)。

打开 `http://localhost:3000`，API 位于 `http://localhost:8000`。Flyway 自动迁移数据库；本地对象与上传分片位于后端工作目录的 `data`。应同时备份数据库和该目录，避免更改工作目录导致历史对象无法定位。

## 界面与主题

按钮、输入、弹窗、右键菜单基于 shadcn-vue / Reka UI。侧栏「外观设置」支持跟随系统、浅色、深色、强调色、圆角、紧凑列表；设置保存在当前浏览器。业务页面使用统一语义色令牌。

个人空间目录支持列表/网格、名称搜索、排序、多选、拖动移动、目录目标选择、分片上传及断点续传、基本预览、收藏、回收站、文件替换和版本恢复。预览超过 64 MB 时提示下载；Markdown 当前按文本显示，Office 仍使用不支持类型兜底。

## 验证

```powershell
cd backend
mvn -B test
cd ../frontend
pnpm --filter @cloud/web test
pnpm --filter @cloud/web build
```

后端集成测试需要可用的 MySQL；测试事务回滚业务数据，Flyway 迁移仍会应用。测试替换 Redis 启动探测，不发送邮件；正式启动仍要求 Redis 可连接。建议通过 `SPRING_DATASOURCE_URL` 指向专用测试库，不在生产库运行集成测试。

前端浏览器交互回归脚本位于 `frontend/apps/web/tests/drive-browser.py`，使用 API 测试夹具，不能替代真实前后端端到端验收。需要可启动 Chromium 的 Playwright 环境。

## 容器与部署

```powershell
docker compose --profile app up -d --build
```

访问 `http://localhost:8080`。Nginx 代理 API、转发 Range 并关闭请求缓冲。数据库、Redis 和对象目录使用独立卷。当前环境 Docker 未运行，容器构建和全新环境启动尚未实测；发布前必须完成验收台账中的相关检查。

只有全新空库的第一个成功注册用户会原子性获得超级管理员。升级已有库不会自动提升任何历史用户权限；已有库的管理员角色需按既有管理流程配置。管理后台 `/admin` 通过服务端角色校验，普通用户前端显示 404。

T008真实联调可运行 `python scripts/verify-t008-live.py --dev`（开发代理）或构建前端验收镜像后运行 `python scripts/verify-t008-live.py`（Nginx）。脚本创建独立临时库并在结束时删除，详细前置条件和结果见T008验收记录。
