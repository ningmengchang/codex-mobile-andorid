# Codex 随行 Android

这是一个独立的 Android 遥控端，源码与原来的 `codex-mobile` 项目完全分离。电脑端仍运行现有的 `codex-mobile.service`，App 负责建立 SSH 隧道、显示现有网页，并把文件分享交给 Android 原生系统。

## 第一版能力

- 密码或私钥方式登录 SSH。
- Android Keystore 加密保存密码/私钥口令。
- 首次连接展示 SSH SHA-256 指纹，后续主机密钥变化时拒绝连接。
- 在 App 内建立 `127.0.0.1:3765 → 电脑 127.0.0.1:3765` 转发，不再依赖 ConnextBot。
- SSH 断线后自动重连，并通过低优先级前台通知维持后台连接；从最近任务划掉 App 时停止连接、重试和连接通知。
- WebView 复用现有 Codex Mobile 页面、配对状态、会话和文件功能。
- App 默认使用沉浸式全屏，并隐藏网页版多余的全屏切换按钮。
- 手机返回键按弹窗、文件目录、底部页面的层级逐级返回；到达首页后需再次确认才返回桌面。
- SSH 连接配置收纳在网页“设置”弹窗中，不再使用遮挡内容的右上角悬浮按钮。
- 支持网页文件选择器，用于现有“文件目录”上传入口。
- 接管网页产出物下载，携带当前登录状态保存到 Android“下载”目录。
- 拦截现有文件分享流程，使用 App 流式缓存和 `FileProvider + ACTION_SEND` 分享给微信等应用。
- 分享链接严格限制为当前手机本地端口上的 `/api/artifacts/:token/raw`。

## 安装与连接

1. 在电脑上确认 `codex-mobile.service` 正常运行，并监听 `127.0.0.1:3765`。
2. 确认手机能够通过 SSH 地址和端口访问电脑。可以是局域网 IP、域名或已有的远程 SSH 入口。
3. 如果 ConnextBot 仍占用手机的 3765 端口，先关闭其中的 Codex 转发。
4. 把 `dist/CodexCompanion-debug.apk` 发送到 Android 手机并允许“安装未知应用”。
5. 在 App 中填写电脑地址、SSH 端口、用户名和密码，或导入私钥。
6. 首次连接时核对电脑 SSH 指纹。电脑端可执行：

   ```bash
   ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub -E sha256
   ```

7. 隧道建立后会自动打开 Codex 随行；首次仍按原网页流程输入配对码。

> 如果电脑没有 Ed25519 主机密钥，请对照实际使用的 `/etc/ssh/ssh_host_*_key.pub`。

## 后台与退出

- 返回桌面、锁屏、切换其他应用时，继续维持连接和断线重试。
- 从最近任务划掉 App 时，停止 SSH 服务，取消连接和重试，移除常驻连接通知。恢复 VPN 或网络不会自行启动连接。
- 再次打开 App 时，按已保存的“自动连接”设置决定是否连接。系统回收 App 进程后也需要重新打开 App 才恢复连接。
- 手机退出不会主动取消已经提交到电脑端的任务；连接配置、历史和登录信息保留。
- 该行为从 Android 客户端 `0.1.12` 开始生效，需要安装新版 APK。

## 构建

需要 JDK 17 或更高版本和 Android SDK API 35。Android Studio 打开本目录后可直接构建，也可以运行：

```bash
./scripts/build-debug-apk.sh
```

产物会复制到：

```text
dist/CodexCompanion-debug.apk
dist/CodexCompanion-debug.apk.sha256
```

当前 debug 包由本机 Android debug keystore 签名。在同一台电脑持续构建时可以覆盖升级；正式长期分发前应改用单独保存的 release keystore。

## 安全边界

- App 不把 Codex、Skill、历史会话或项目文件搬到手机，所有核心数据仍在电脑端。
- WebView 只允许加载当前 `127.0.0.1` 转发端口；外部网页交给系统浏览器。
- JavaScript 原生桥只能读取当前本地服务的产出物原始文件接口，不能读取任意网址或手机文件。
- 连接配置不会参与 Android 云备份或换机迁移。
- 首次连接采用信任首次使用（TOFU）。应在首次确认前核对电脑上的 SSH 指纹。

详细模块说明见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)。
