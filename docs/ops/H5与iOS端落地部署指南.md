# Cloudlane 移动端（H5 / PWA 与 iOS 原生 App）落地部署指南

> 本文档面向实际生产落地，分 **H5 / PWA 端** 与 **iOS 原生 App 端** 两部分，给出从后端配置、Nginx/Docker 部署、到 iOS 打包安装（含个人自用 / TestFlight / App Store）的完整实操步骤。

---

## 一、核心前置：后端必须确认的配置（H5 与 iOS 共用）

### 1. CORS 白名单配置（**iOS 端必配，否则接口会被跨域拦截**）
iOS Capacitor App 运行在本地 WKWebView 中，其请求头 `Origin` 固定为 **`capacitor://localhost`**；而 H5 端如果通过自定义域名访问，也应将域名加入白名单。

在后端配置（`application.yml` / 环境变量 / Docker Compose 环境变量）中设置：
```yaml
cloud:
  cors:
    allowed-origins:
      - https://cloud.yourdomain.com     # 你的正式域名（H5 与 PC 共用）
      - capacitor://localhost            # iOS 原生 App 固定 Origin（必配！）
      - https://localhost                # Android 原生 App 固定 Origin（按需保留）
```
或通过环境变量传入（Spring Boot 宽松绑定）：
```env
CLOUD_CORS_ALLOWED_ORIGINS=https://cloud.yourdomain.com,capacitor://localhost,https://localhost
```
> 后端 [SecurityConfig.java](../../backend/cloud-api/src/main/java/com/wanyue/cloud/common/security/SecurityConfig.java) 已放行 iOS 原生所需的全部自定义请求头：`X-Client-Type`、`X-Device-Name`、`X-Refresh-Token`、`X-Private-Token` 以及媒体分块 `Range` 响应头。

### 2. HTTPS 证书（强烈建议）
- **H5 / PWA**：iOS Safari 要求 **HTTPS** 才能使用「添加到主屏幕（PWA 全屏模式）」、相机扫码、麦克风录音、Cache Storage 缩略图持久化。
- **iOS App**：Apple ATS（App Transport Security）默认要求后端接口为 **HTTPS**；若仅在内网 IP 用 HTTP 调试，需在 `Info.plist` 额外开启 ATS 例外（下文第四节提供配置）。

---

## 二、H5 / PWA 端落地步骤（手机浏览器打开即用）

H5 端挂载在同域名的 `/m/` 路径下（与 PC 端 `/`、管理端 `/admin/` 共用同一 Nginx），且**运行时会自动隐藏所有 iOS 原生专属入口**（如自托管地址配置、生物识别锁、相册自动备份、离线文件、文档扫描、外部应用打开），不会出现任何不可用按钮。

### 方式 A：Docker Compose 一键部署（推荐）
项目根目录下 `frontend/Dockerfile` 与 `frontend/nginx.conf` 已经内置 `/m/` 的构建与 Nginx 路由规则：
1. 确认 `frontend/nginx.conf` 中已包含（默认已配好）：
   - `location = /m { return 308 /m/; }`
   - `location /m/ { try_files $uri $uri/ /m/index.html; }`
   - `/api/v1/.*/events$` 关闭缓冲（保障消息与共享 SSE 实时推送）
   - `/api/` 透传 `Range` / `If-Range`（保障视频拖拽与 200+ 页大 PDF 分块流式加载）
2. 执行镜像构建并启动：
   ```bash
   docker compose up -d --build
   ```
3. 手机浏览器访问 `https://cloud.yourdomain.com/m/`：
   - 手机访问 PC 端 `https://cloud.yourdomain.com/` 时，顶部也会自动弹出切换横幅引导进入移动版。
   - **iOS Safari 安装为桌面图标（PWA）**：打开 `/m/` -> 点击底部「分享」按钮 -> 「添加到主屏幕」-> 即可像 App 一样无地址栏全屏使用。

### 方式 B：手工构建静态包部署到自有 Nginx
1. 在项目根目录执行发布脚本构建 H5 静态产物：
   ```powershell
   .\scripts\release.ps1 -Module mobile
   ```
   （或在 `frontend/apps/mobile` 目录执行 `pnpm build`，产物位于 `frontend/apps/mobile/dist`）
2. 将产物上传至服务器，例如 `/var/www/cloudlane/m/`。
3. 在你现有的 Nginx HTTPS `server` 块中加入：
   ```nginx
   location = /m { return 308 /m/; }
   location /m/ {
       alias /var/www/cloudlane/m/;
       try_files $uri $uri/ /m/index.html;
   }
   ```
4. 重载 Nginx：`nginx -t && nginx -s reload`。

---

## 三、iOS 原生 App 端落地步骤（Capacitor + Xcode）

> **前提说明**：Apple 规定编译、签名与打包 iOS `.ipa` **必须在 macOS + Xcode 环境下完成**（你可以在当前 Windows 电脑开发代码并推送到 Git，再在 Mac 本地或 GitHub Actions `macos-latest` 上编译打包）。

### 第一步：准备 macOS 环境
- macOS 14+，安装 **Xcode 15+**（App Store 免费安装）
- 安装命令行工具：`xcode-select --install`
- 安装 Node.js 22+、`pnpm`、以及 **CocoaPods**（`brew install cocoapods`）

### 第二步：构建 Capacitor 专用前端包并初始化 iOS 工程
在 macOS 终端进入仓库 `frontend` 目录：
```bash
# 1. 安装依赖
pnpm install

# 2. 以 Capacitor 模式构建移动前端（注意 CAPACITOR_BUILD=1，使用相对路径 ./ 而非 /m/）
cd apps/mobile
CAPACITOR_BUILD=1 pnpm exec vite build

# 3. 首次初始化 iOS 原生工程（会生成 frontend/apps/mobile/ios/ 目录）
pnpm exec cap add ios

# 4. 同步前端静态资源与全部 5 个 Capacitor 原生插件到 Xcode 工程
pnpm exec cap sync ios
```
> 后续每次更新前端代码，只需重复第 2 步与第 4 步（`CAPACITOR_BUILD=1 pnpm exec vite build && pnpm exec cap sync ios`）即可，无需重新 `cap add ios`。

### 第三步：在 Xcode 中配置权限、签名与网络（首次必做）
运行以下命令打开 Xcode 工程：
```bash
pnpm exec cap open ios
```

#### 1. 配置 Bundle ID 与开发者签名
- 左侧选中 `App` 工程 -> `TARGETS -> App` -> `Signing & Capabilities`：
  - 勾选 **Automatically manage signing**
  - **Team**：选择你的 Apple ID（免费个人账号支持真机直装 7 天续签；付费 Apple Developer $99/年支持 TestFlight 90 天与 App Store 上架）
  - **Bundle Identifier**：默认 `com.wanyue.cloudlane`（如被占用可改为 `com.你的名字.cloudlane`，并同步修改 `capacitor.config.ts` 中的 `appId`）

#### 2. 配置 `Info.plist` 隐私权限说明（缺失会导致调用相机/相册/FaceID 闪退）
在 Xcode 中打开 `App/App/Info.plist`（右键 -> Open As -> Source Code），在 `<dict>` 内补充：
```xml
<!-- 相机：拍照上传、扫码登录 PC、文档扫描转 PDF -->
<key>NSCameraUsageDescription</key>
<string>Cloudlane 需要使用相机以进行拍照上传、扫描二维码和纸质文档扫描</string>

<!-- 相册读写：选择照片视频上传、保存分享海报、相册自动备份 -->
<key>NSPhotoLibraryUsageDescription</key>
<string>Cloudlane 需要访问您的相册以上传和备份照片与视频</string>
<key>NSPhotoLibraryAddUsageDescription</key>
<string>Cloudlane 需要权限将文件或二维码海报保存到您的相册</string>

<!-- 麦克风：随手录音上传 -->
<key>NSMicrophoneUsageDescription</key>
<string>Cloudlane 需要使用麦克风以录制语音备忘录并上传至网盘</string>

<!-- 面容 ID：应用启动锁与私密空间快速解锁 -->
<key>NSFaceIDUsageDescription</key>
<string>Cloudlane 使用面容 ID 保护您的应用启动锁与私密空间</string>
```

#### 3. 内网 HTTP 自托管服务器支持（仅当你没有 HTTPS、用局域网 IP 连接时才需要）
同样在 `Info.plist` 中加入：
```xml
<key>NSAppTransportSecurity</key>
<dict>
    <key>NSAllowsArbitraryLoads</key>
    <true/>
</dict>
```
> 若你部署在公网 HTTPS 域名，建议不要开启 `NSAllowsArbitraryLoads`，直接走系统默认 HTTPS 校验更安全。

#### 4. （可选）配置系统级「分享到 Cloudlane」（iOS Share Extension）
如需从微信、系统相册点击「分享 -> Cloudlane」直接上传：
1. Xcode 菜单 `File -> New -> Target -> Share Extension`，命名为 `CloudlaneShare`。
2. 在主 App 与 `CloudlaneShare` 两个 Target 的 `Signing & Capabilities` 中均添加 **App Groups**（如 `group.com.wanyue.cloudlane`）。
3. 在 `CloudlaneShare/Info.plist` 的 `NSExtensionActivationRule` 中启用图片、视频、文件类型接收，将分享文件拷贝到共享 App Group 目录并通过 URL Scheme 唤起主 App。

### 第四步：真机安装与分发（按你的使用场景任选其一）

| 场景 | 适用人群 | 实操方式 |
|---|---|---|
| **方案 1：数据线直装自己 iPhone** | 仅自己使用，无付费开发者账号 | iPhone 连 Mac，Xcode 顶部设备选择你的 iPhone -> 点击运行 `▶`；首次打开需在手机 `设置 -> 通用 -> VPN与设备管理` 信任开发者证书（免费账号每 7 天需重装续签） |
| **方案 2：TestFlight 分发（推荐自用/小团队）** | 有 Apple Developer 账号（$99/年） | Xcode 顶部设备选 `Any iOS Device (arm64)` -> 菜单 `Product -> Archive` -> `Distribute App -> App Store Connect -> Upload`，在 App Store Connect 加入内部测试员，手机通过 TestFlight 安装（90 天有效，更新自动推送） |
| **方案 3：App Store 正式上架** | 公开对外发布 | 与方案 2 相同 Archive 上传后，在 App Store Connect 填写隐私政策、截图与测试账号提交审核 |
| **方案 4：无 Mac 时用 GitHub Actions 云端编包** | 只有 Windows 电脑 | 在仓库 `.github/workflows/` 使用 `runs-on: macos-14` 执行 `CAPACITOR_BUILD=1 pnpm exec vite build && pnpm exec cap sync ios && xcodebuild archive`，导出未签名或证书签名的 `.ipa` |

---

## 四、首次打开 iOS App 的连接与使用流程

1. 首次启动 App 进入登录页，点击底部 **「配置自托管服务器地址」**（该入口仅 iOS / Android 原生 App 显示，H5 端自动隐藏）。
2. 输入你的后端服务地址，例如 `https://cloud.yourdomain.com`（或内网 `http://192.168.1.10:8000`）。
3. 点击 **「测试连接」**，提示 `连接成功！已识别服务：Cloudlane` 后点击 **「保存配置」**。
4. 正常登录账号：App 会自动以 `X-Client-Type: IOS` 走无 Cookie `X-Refresh-Token` 安全持久化模式，重启 App 自动保持登录态。
5. 登录成功后即可在「我的」与「账户与安全」中按需开启：
   - **应用安全锁与后台防偷窥高斯模糊遮罩**
   - **相册自动增量备份**（结合 SHA-256 秒传去重）
   - **离线文件与无网阅读**
   - **文档扫描转 PDF**
   - **云端相册时光轴**（H5 与 iOS 均可用）

---

## 五、常见问题排查

1. **iOS App 点击「测试连接」或登录提示网络/跨域错误**：
   - 检查后端 `CLOUD_CORS_ALLOWED_ORIGINS` 是否包含 `capacitor://localhost`；
   - 若使用内网 HTTP 地址，检查 `Info.plist` 是否配置了 `NSAllowsArbitraryLoads = true`；
   - 检查 iPhone 与服务器是否在同一局域网（或公网 HTTPS 证书链是否完整，iOS 不信任自签名证书）。
2. **H5 端无法调用相机扫码或录音**：
   - 确认是通过 **HTTPS** 访问 `/m/`（除 `localhost` 外，浏览器在纯 HTTP 下会禁用 `getUserMedia` 与 `BarcodeDetector`）。
3. **200+ 页大 PDF 加载慢或失败**：
   - 确认 Nginx `/api/` 转发保留了 `Range` / `If-Range` 请求头且关闭了 `proxy_buffering`，前端会自动按 128KB 分块流式加载。
