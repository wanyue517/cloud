# Windows 开发、云端 IPA 构建与 7 天续签

本仓库已包含 Capacitor 8 iOS 工程、实况照片插件和未签名真机 IPA 的构建流程。Windows 上可以修改代码、跑前端和后端测试；Swift/iOS 编译必须交给 macOS + Xcode 26 或更新版本。

## 1. 在自己的 iPhone 上测试实况功能

后端先启动到包含 Flyway `V22`、`V23` 的版本，配置 FFmpeg/FFprobe。iOS App 首次启动后在服务器设置中填入手机能访问的云盘 **HTTPS 地址**，不能使用 Windows 本机专用的 `localhost`。

- 文件页“新建与上传” → **实况照片**：系统选择器只选择 Live Photo，一次导出静态原件、配套 MOV 和兼容 JPEG，自动上传并绑定。支持系统下载 iCloud 中的原件；此过程可能需要等待。
- 照片、MOV、JPEG 预览图作为三个普通文件保存，都计算配额。当前版本请一并保留；删除、跨空间移动或撤销对配套文件的访问，会使绑定资源不可用。传输在前台完成，中途失败会提示已保留的原件数量。
- 图片预览 → **LIVE** 或长按：播放兼容实况视频，默认静音，可开启原声。
- 图片预览 → **保存实况到相册**：下载原始照片与原始视频；已是有效 Apple 配对的资源直接成对保存。其他资源尝试用 ImageIO、AVFoundation 写入新的配对标识及静态帧时间轨，验证后保存。首次需要允许添加照片。
- 图片预览 → **下载安卓动态照片**：在播放视频转换就绪后，生成带标准 XMP 目录和内嵌 MP4 的 `*MP.jpg`。这是用于跨端的派生文件，云端原始 HEIC/MOV 不变；各厂商相册识别仍需要真机验证。

每个原件目前最多 64 MB；手工选择配套视频支持 MOV/MP4/M4V。对不匹配的配对进行 Apple 格式转换时，视频最长 30 秒，采用兼容编码的透传封装，不支持的编码会报错，不会把失败当作保存成功。原生拍摄的有效 Apple 配对无需这一步转换。其他来源在没有静态帧时间信息时使用视频中点作为展示时间。

普通 Safari H5 仍不具备这里的原生相册选取与写入能力，不能用网页运行结果代替 App 测试。

## 2. 云端构建未签名 IPA

配置文件：`.github/workflows/ios-unsigned.yml`。

1. 将包括新文件在内的代码提交并推送到你自己的 GitHub 仓库。不要只推旧代码；本次工作区尚未自动提交或推送。
2. 推送 `feat/live-photo` 分支且改动匹配构建路径时，会自动触发 **Build iOS IPA for local signing**。
3. 也支持手工触发：当工作流文件已经存在于默认分支后，进入 **Actions → Build iOS IPA for local signing → Run workflow**，选择要编译的分支。
4. 工作流使用云端 Mac，安装 Node 24/pnpm，带 `CAPACITOR_BUILD=1` 构建移动端，再执行 `cap sync ios`、Xcode 真机编译和 IPA 封装。
5. 运行成功后，在该次运行的 **Artifacts** 中下载 `Cloudlane-iOS-unsigned` ZIP，解压得到 **Cloudlane-unsigned.ipa**。失败时查看步骤输出或产物中的 `xcodebuild.log`。

这个流程不需要 Apple 开发者会员、签名证书或 Apple 账号密钥。它生成的是 **iphoneos 真机程序**，不是模拟器程序；IPA 尚未签名，必须经过下一步才能安装。云端构建额度按 GitHub 账号/仓库计划计算，不承诺免费无限使用。工作流仅请求仓库读取权限。

如使用自己的 Mac 或远程 Mac，先安装 Node 24、pnpm 11.25.0、Xcode 26+，然后从仓库根目录运行：

```bash
cd frontend
pnpm install --frozen-lockfile
CAPACITOR_BUILD=1 pnpm --filter @cloud/mobile build
cd apps/mobile
pnpm exec cap sync ios
cd ../../..
python3 scripts/build-ios-unsigned.py
```

输出为 `release-dist/ios/Cloudlane-unsigned.ipa`。不要在 Windows 执行最后的编译脚本；脚本会明确拒绝非 macOS 环境。

## 3. Windows 使用 Sideloadly 签名与安装

1. 从 [Sideloadly 官方网站](https://sideloadly.io/) 下载 Windows 版本，按官网要求安装 Apple 驱动组件（当前官网说明使用网页版 iTunes/iCloud）。
2. iPhone 用 USB 连接 Windows，解锁手机并选择“信任这台电脑”，确认工具能识别设备。
3. 将解压出的 `Cloudlane-unsigned.ipa` 拖入 Sideloadly，输入你自己的免费 Apple 账号，按提示完成双重验证。
4. 保留固定的 Bundle ID 设置，开启 **自动刷新/Automatically refresh app**，再开始签名安装。Apple 账号和验证信息只在本机工具中输入，无需放进仓库或 CI。
5. 安装后，按 iOS 提示在“设置 → 通用 → VPN 与设备管理”信任开发者；需要时，在“隐私与安全性 → 开发者模式”开启开发者模式并重启。
6. 打开 Cloudlane，设置服务器地址、登录，再测试相册实况选取与保存。免费签名不影响用户授权后的基本 Photos 读写；推送、iCloud 容器等其他高级能力不属于本轮功能。

基础 Bundle ID 是 `com.wanyue.cloudlane`。Sideloadly 可能根据个人账号调整最终标识；后续安装/更新要保持同一 Apple 账号及相同 Bundle ID 配置，避免系统将其当成另一个 App。

## 4. 7 天续签

- 免费个人账号的描述文件通常从签发起有效 **7 天**，每台设备最多 **3 个**此类 App。到期未刷新时，App 会无法启动。
- **正常续签不需要重新云端编译**。让 Sideloadly Daemon 运行，手机通过 USB 连接，或完成无线配对后与电脑在同一网络，工具才有机会自动刷新。
- 建议每周至少连接一次检查刷新结果。电脑长期关机、设备不可发现、Apple 验证失败等情况会导致自动刷新失败；不要把它当作无需维护的永久签名。
- 手工续签或安装新版：在 Sideloadly 导入同一个或新构建的 IPA，使用相同 Apple 账号和 Bundle ID 覆盖安装，**不要先卸载**。正常覆盖更新会保留应用数据；仍应保留重要数据备份。
- 免费个人签名面向自己的设备测试，不是 TestFlight、App Store 或向他人任意分发的方式。

官方参考：[Apple 个人账号限制](https://developer.apple.com/help/account/basics/about-your-developer-account)、[Sideloadly 刷新与覆盖安装 FAQ](https://sideloadly.io/faq)、[Capacitor 环境要求](https://capacitorjs.com/docs/getting-started/environment-setup)。

## 5. 验证范围与待验项目

Windows 可验证 TypeScript、前端构建、桥接编排、后端原件权限/流输出、Motion Photo JPEG 封装及工程文件结构。它无法执行 Xcode 或 iPhone Photos。

首次云端构建仍必须验证 Swift 编译、SPM 依赖解析以及 IPA 内插件注册。真机验收需覆盖：本地/iCloud 原生 Live Photo 上传；安卓/网页图片与实况播放；Apple 配对原样存回相册；安卓 Motion Photo 转成 Apple 实况；相册权限拒绝；7 天续签后的 App 数据保留。未运行这些步骤前，不应将实现代码等同于已完成真机兼容认证。
