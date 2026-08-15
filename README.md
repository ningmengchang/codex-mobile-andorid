# Codex 随行 Android

这是一个独立的 Android 遥控端，源码与原来的 `codex-mobile` 项目完全分离。电脑端仍运行现有的 `codex-mobile.service`，App 负责建立 SSH 隧道、显示现有网页，并把文件分享交给 Android 原生系统。

## 第一版能力

- 密码或私钥方式登录 SSH。
- Android Keystore 加密保存密码/私钥口令。
- 首次连接展示 SSH SHA-256 指纹，后续主机密钥变化时拒绝连接。
- 在 App 内建立 `127.0.0.1:3765 → 电脑 127.0.0.1:3765` 转发，不再依赖 ConnextBot。
- SSH 断线后自动重连，并通过低优先级前台通知维持后台连接。
- WebView 复用现有 Codex Mobile 页面、配对状态、会话和文件功能。
- 支持网页文件选择器，用于现有“文件目录”上传入口。
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
