# 实况图实验分支

分支：`feat/live-photo`。

## 当前可试用的流程

后端启动时由 Flyway 执行 `V22__live_photo.sql`、`V23__live_photo_poster.sql`。沿用现有存储、上传会话、配额和鉴权配置；转换需要可执行的 FFmpeg 和 FFprobe，配置项仍为 `cloud.preview.ffmpeg-bin`、`cloud.preview.ffprobe-bin`。

iOS 原生上传、保存、云端 IPA 构建及 Windows 7 天续签步骤见 [iOS 打包与续签](ios-ipa-and-renewal.md)。仓库已包含 iOS 工程和插件，Windows 验证不代表 Swift 编译或真机验收通过。

1. 普通上传保留完整的 Android 动态 JPEG。首次打开图片预览时读取 JPEG APP1 XMP，支持 Google 相机命名空间下的 `MicroVideoOffset`，以及容器 Item 中位于文件末尾的 `MotionPhoto` MP4。校验偏移、长度和 `ftyp` 后异步生成 `LIVE_PHOTO_MP4`。
2. 对导出的图片与配套 MOV/MP4，先上传图片，在当前版本的图片预览中点击“添加实况视频”，选择小于 64 MB 的视频。视频通过原有分片上传保存为当前目录中的独立文件，再绑定照片版本。请保留这个视频文件；删除、移动到其他空间或取消对它的访问，会使配对预览不可用。绑定失败时已上传的视频仍保留在目录中。
3. Web 与移动端显示处理状态。转换完成后点击 LIVE 播放，或长按图片约 400 ms 播放、松手停止。播放结束恢复静态图。为兼容浏览器自动播放规则，默认静音，可点击“开启原声”。
4. 失败时点击 LIVE 重试；重新打开预览会查询已有派生资源。照片版本间的绑定互不继承，同一照片版本不允许更换已绑定的视频。

## API

- `GET /api/v1/spaces/{space}/nodes/{id}/live-photo?versionId=...`：检查当前或指定照片版本，返回 `livePhoto`、`status`、`errorMessage`；按需触发内嵌视频派生。`retry=true` 重试失败任务。
- `POST /api/v1/spaces/{space}/nodes/{id}/live-photo`：JSON `{ "versionId": "照片版本ID", "videoNodeId": "视频节点ID" }`。要求两个文件均有写权限、位于同一空间。固定绑定所选视频的当前版本。
- `POST .../preview/stream-ticket?assetType=LIVE_PHOTO_MP4&versionId=...`：要求照片与配套视频均可读且派生资源就绪，返回短期播放地址。流端点支持 HEAD、HTTP Range。票据有效期沿用现有的 30 分钟授权快照；读取时仍检查原件是否被删除。
- 配对接口可传 `posterNodeId`，绑定原始 HEIC 的 JPEG 兼容预览图；预览图也固定其文件版本并校验权限。
- `GET .../live-photo/parts/{photo|video|poster}?versionId=...`：鉴权下载原件轨道。Android 动态 JPEG 返回精确切出的图像/视频区间；Apple 配对的视频返回原始 MOV，不用转码 MP4 替代。
- `GET .../live-photo/motion-photo?versionId=...`：播放资源就绪后，导出标准 XMP 目录 + 内嵌 MP4 的动态 JPEG，原文件不变。

原始 JPEG、图片及视频均不改写。`preview_asset` 的 `live/` 子目录仅存放可重建的播放资产。

## 本轮边界

- 动态照片在首次预览时识别，不在上传完成时扫描。暂未添加列表、时光轴实况角标。
- 没有承诺所有 Android 厂商格式兼容；当前支持范围为上述 XMP JPEG 子集，暂不支持 HEIF Motion Photo、扩展 XMP、带尾部 padding 的容器视频或厂商私有封装。需要 Pixel、三星、小米等真实样本继续验证。
- 手工绑定不会验证 Apple 配对标识；用户需要选择正确的视频。静态图预览沿用当前图片解码能力，浏览器不支持的 HEIC 可能需要先导出 JPEG。
- 已新增 iOS PHPicker/Photos 插件及原生工程，支持成对选取和相册保存，已加入安卓 Motion Photo 导出。Windows 无法验证 Swift 编译及真机相册识别；请运行云端构建并按上述说明验收。安卓厂商兼容性不保证一致。
- 本轮未在运行中的数据库上执行迁移，也未验证真实 FFmpeg 转码和手机原生相册；不将单元测试和浏览器夹具测试视作真机兼容证明。

## 验证

```powershell
# 后端解析、权限、版本及既有流播放回归
cd backend
mvn -B -pl cloud-api -am test "-Dtest=MotionPhotoReaderTest,MotionPhotoWriterTest,LivePhotoServiceTest,LivePhotoOriginalsTest,LivePhotoControllerTest,MediaStreamControllerTest,MediaPlaybackControllerTest"

# 在 frontend 中分别构建两端
pnpm --filter @cloud/web build
pnpm --filter @cloud/mobile build
pnpm --filter @cloud/mobile test

# 启动 Web Vite 服务后，在仓库根目录运行浏览器交互夹具
python scripts/verify-live-photo-ui.py
```

浏览器脚本使用 `http://127.0.0.1:5173`，默认使用本机 Edge；可通过 `PLAYWRIGHT_CHANNEL=chrome` 切换。它覆盖桌面/手机视口的真实 Vue 组件，包括网络失败、切图时的旧请求、长按松手、文件校验与上传状态；测试地址及上传均为夹具，不连接实际云盘。
