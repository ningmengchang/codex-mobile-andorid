# 架构说明

```text
MainActivity
├── 原生 SSH 配置界面
├── WebView（仅允许 127.0.0.1:本地端口）
├── 文件选择器
└── NativeShareManager
    ├── 校验产出物 raw URL
    ├── 携带 WebView Cookie 流式下载
    └── FileProvider / ACTION_SEND

TunnelService（前台服务）
├── ConfigStore / Android Keystore
├── JSch SSH 会话
├── SSH known_hosts / 首次指纹确认
├── 本地端口转发
└── 保活与指数退避重连

手机 127.0.0.1:3765
          │ SSH tunnel
          ▼
电脑 127.0.0.1:3765
          │
          ▼
现有 codex-mobile.service
```

## 目录职责

- `config/`：连接数据校验、SharedPreferences、Keystore 加密、known_hosts。
- `tunnel/`：前台服务、SSH 生命周期、端口转发、重连状态。
- `web/`：WebView 导航白名单与页面生命周期。
- `share/`：网页分享兼容补丁、原生桥、下载、MIME 判断、FileProvider。
- `MainActivity.kt`：界面编排、文件选择、全屏/返回键和状态展示。

## 分享兼容机制

旧网页在点击分享时会先用 `fetch` 把整个文件读成 `Blob`，再调用 Web Share API。Android WebView 加载完成后，App 注入一个很小的兼容补丁：

1. 只在旧网页的文件分享弹窗打开时识别产出物 raw 请求。
2. 向旧网页返回一个空的占位 `Blob`，因此按钮无需等待真实文件下载。
3. 用户点击“微信 / 其他应用”后，把已记录的本地 raw URL、文件名和 MIME 交给原生桥。
4. 原生层验证 URL，携带同源 Cookie 流式下载到 App 私有缓存。
5. `FileProvider` 生成临时 `content://` URI，并给接收应用授予只读权限。

这样不会修改旧项目，也不会在 JavaScript 内存中保存一份完整文件。

## 连接状态

`TunnelRuntime.state` 是 Activity 与 Service 之间唯一的连接状态源：

- `Idle`
- `Connecting`
- `AwaitingHostApproval`
- `Connected`
- `Reconnecting`
- `Error`

服务只有在 `Connected` 后才发布本地 URL；Activity 收到后才显示 WebView，避免先露出不可用的旧页面。
