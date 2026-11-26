# 阶段 07：Office、视频与压缩包预览验收（T083–T096）

日期：2026-11-26。状态：**已验收**。

## 实现清单

对照开发计划阶段 07（T083–T096），完成 Office 转换派生链路、Excel 分段表格与打印预览、xgplayer 视频播放与转码判定、ZIP/TAR 归档安全解析及转换资源/超时隔离：

- **T083–T084 & T096（Gotenberg 转换服务、派生资源状态机与超时/并发限制）**：
  - [compose.yaml](file:///E:/wanyue/projects/cloud/compose.yaml) 新增 `gotenberg/gotenberg:8` 服务（profile `["app", "office"]`，配置超时与队列上限）；
  - 新增 Flyway 迁移 [V7__preview_asset.sql](file:///E:/wanyue/projects/cloud/backend/cloud-api/src/main/resources/db/migration/V7__preview_asset.sql) 创建 `preview_asset` 表（记录 `PENDING | PROCESSING | READY | FAILED` 状态、尝试次数、存储键与错误信息）；
  - 新增 [PreviewAssetService.java](file:///E:/wanyue/projects/cloud/backend/cloud-api/src/main/java/com/wanyue/cloud/modules/drive/preview/PreviewAssetService.java) 与 [PreviewController.java](file:///E:/wanyue/projects/cloud/backend/cloud-api/src/main/java/com/wanyue/cloud/modules/drive/preview/PreviewController.java)，使用 `Semaphore` 并发上限、`ExecutorService` 隔离线程池、`max-source-bytes` 体积上限及子进程/HTTP 严格超时保护与强制销毁，避免异常文档拖垮主 API。
- **T085–T086（Word / PPT 预览与幻灯片演示模式）**：
  - 新增 [OfficePreviewer.vue](file:///E:/wanyue/projects/cloud/frontend/apps/web/src/components/drive/preview/OfficePreviewer.vue)，自动轮询 `OFFICE_PDF` 转换状态并在失败时提供「重新转换」与「下载原件」；
  - 增强 [PdfPreviewer.vue](file:///E:/wanyue/projects/cloud/frontend/apps/web/src/components/drive/preview/PdfPreviewer.vue) 支持自定义派生 PDF 加载器与 PPT 左侧幻灯片缩略图导航 + 全屏演示翻页。
- **T087–T089（Apache POI Excel 分段读取 + 虚拟滚动 + 打印预览 PDF 模式）**：
  - 新增 [ExcelPreviewService.java](file:///E:/wanyue/projects/cloud/backend/cloud-api/src/main/java/com/wanyue/cloud/modules/drive/preview/ExcelPreviewService.java)，集成 `poi-ooxml 5.3.0` 读取 `.xlsx/.xls`（并兼容 `.csv`）工作表元数据与按 `rowOffset/rowLimit` 分段读取单元格；
  - 新增 [ExcelPreviewer.vue](file:///E:/wanyue/projects/cloud/frontend/apps/web/src/components/drive/preview/ExcelPreviewer.vue)，支持「表格模式（Sheet 标签栏 + 100 行分段拉取 + 虚拟滚动窗口）」与「打印预览 (PDF)」一键切换。
- **T090–T093（xgplayer + FFprobe 元数据探测 + 直放/转封装/转码判定与 FFmpeg Worker）**：
  - [PreviewAssetService.java](file:///E:/wanyue/projects/cloud/backend/cloud-api/src/main/java/com/wanyue/cloud/modules/drive/preview/PreviewAssetService.java) 实现 FFprobe JSON 探测与 `DIRECT_PLAY / REMUX / TRANSCODE` 策略判定（环境无 FFprobe 时自动按容器兜底），并实现 FFmpeg 转封装/转码后台任务与重试接口；
  - 升级 [VideoPreviewer.vue](file:///E:/wanyue/projects/cloud/frontend/apps/web/src/components/drive/preview/VideoPreviewer.vue) 集成 `xgplayer`（支持 0.5x–2x 倍速、进度条、画中画与全屏），自动展示编码/分辨率与播放策略，非直放视频自动轮询转码状态并支持重试。
- **T094–T095（ZIP/TAR 归档目录解析与安全按需提取）**：
  - 新增 [ArchivePreviewService.java](file:///E:/wanyue/projects/cloud/backend/cloud-api/src/main/java/com/wanyue/cloud/modules/drive/preview/ArchivePreviewService.java)，集成 `commons-compress 1.27.1` 解析 `.zip/.tar/.tar.gz/.tgz` 层级目录（限制最大条目数 2000），按需提取严格拦截 `..`/绝对路径/Zip Slip 并限制单文件解压输出不超过 50 MB；
  - 新增 [ArchivePreviewer.vue](file:///E:/wanyue/projects/cloud/frontend/apps/web/src/components/drive/preview/ArchivePreviewer.vue) 支持目录逐层浏览、面包屑导航与单文件安全提取下载。

## 验证结果

| 项目 | 实际结果 |
|---|---|
| 后端预览集成与安全测试 | [PreviewIntegrationTest.java](file:///E:/wanyue/projects/cloud/backend/cloud-api/src/test/java/com/wanyue/cloud/modules/drive/preview/PreviewIntegrationTest.java)（4/4，覆盖真实 POI 生成 `.xlsx` 分段读取与越权 404、真实 `.zip` 层级解析/提取与 Zip Slip 400 拦截、视频直放/转封装/转码策略判定及 `/video/probe`）与 `OpenApiExportTest`（1/1）全部通过 |
| 前端单元测试 | 36/36 全部通过（含 Word/PPT/Excel/CSV/ZIP/TAR.GZ 分发断言与 OpenAPI 零漂移校验） |
| 类型检查与生产构建 | `web` 端 `vue-tsc` 零错误，`vite build` 1.17s 构建成功并验证 `OfficePreviewer`、`ExcelPreviewer`、`ArchivePreviewer`、`VideoPreviewer`（`xgplayer`）独立分包 |
