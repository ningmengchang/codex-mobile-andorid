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
- `MainActivity.kt`：Activity 生命周期、五个 ActivityResult 注册、窗口 Insets 和连接状态编排。
- `config/ConnectionFormController.kt`：表单、输入校验所需配置读取、私钥导入；沿用 ConfigStore/Keystore。
- `web/WebViewController.kt`：WebView 安全设置与三个稳定的原生桥入口。
- `web/ImageInputController.kt`：相机/相册/文件选择意图和回调状态；Activity 负责注册和投递结果。
- `download/DownloadPermissionController.kt`：下载准备、旧版存储权限结果与 DownloadManager 交接。
- `navigation/BackNavigationController.kt`：返回策略、网页返回委托和双击回桌面确认。
- `share/ShareProgressController.kt`：分享进度界面与稳定的网页 resolve/reject 回调。

## 重构兼容边界

起点 `c6da004`。本次只拆分职责，未改变包名、版本、签名、SharedPreferences 名称、Keystore 别名、登录和 SSH 配置存储。布局资源和业务交互保持原样。三个原生桥的名称与方法参数不变。

相机/分享/下载分别管理各自状态，不混用原生 DownloadManager 的后台任务与 NativeShareManager 的流式缓存。导航和产出物读取共用纯 `LocalOriginPolicy`，产出物校验仍额外拒绝用户信息、fragment 和非 raw 路径。

Activity 旋转继续复用 `TunnelRuntime.state`；`onDestroy` 的配置变更分支、服务保活/重连/任务划走清理和 SSH 指纹确认不改。本次没有重写 TunnelService。

验证入口：`scripts/build-debug-apk.sh` 执行原生补丁脚本、控制器接线约束、JVM 单测、Lint 和 APK 构建。接线约束检查不替代真实手机的相机、系统返回、旋转、通知与分享验收。

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

## 任务移除与连接清理

- `TunnelService` 设置 `android:stopWithTask="true"`，最近任务被移除时由系统停止服务。此配置下系统不会调用 `onTaskRemoved()`，统一清理放在服务的 `onDestroy()` 与主动断开路径中。
- 服务返回 `START_NOT_STICKY`；空或未知启动指令只执行停止。自动连接由 Activity 打开时按用户设置发起，不通过服务重启恢复。
- 服务的生命周期、协程状态和通知更新集中在主线程。阻塞的 SSH 与文件操作使用 `Dispatchers.IO`，取消后的结果不能返回主线程发布连接成功、失败重试或通知。
- 每次连接尝试使用独立的 `SshConnection`，持有自己的 JSch Session 和底层 Socket。退出时先取消任务，再关闭 Socket 和 Session；旧尝试的清理不会影响新尝试。端口绑定与关闭互斥，避免迟到的连接重新占用端口。
- 停止时只向 `TunnelRuntime` 发布结束状态，移除前台通知并取消对应通知 ID，不再发布“连接已停止”通知。已存在的会话状态通知不在此清理范围内。
- Activity 重建时复用仍在运行的连接；`onStop()` 与普通 Activity 销毁不主动停止服务，避免切换应用、锁屏、拍照和分享误断开。

### 验证

`./scripts/build-debug-apk.sh` 运行单元测试、原生桥脚本回归、Android Lint 并构建 APK。`SshConnectionTest` 使用仅监听本机的测试服务，验证 SSH 握手阻塞时可取消、退出后禁止迟到连接/端口绑定，以及旧连接清理不会关闭新连接，不使用实际 SSH 凭据。

真机验收需安装新版 APK，在已连接、连接中、等待重试和等待指纹确认四种状态分别划掉 App；检查连接通知消失、SSH 服务停止，并观察至少一分钟。恢复 VPN 后应保持退出，重新打开按自动连接设置恢复。另检查锁屏、返回桌面、切换应用、相机和分享仍保持正常连接。没有真机时，构建与单元测试不能代替此验收。
