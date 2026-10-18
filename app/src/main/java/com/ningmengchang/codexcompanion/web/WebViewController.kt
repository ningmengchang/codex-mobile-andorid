package com.ningmengchang.codexcompanion.web

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import com.ningmengchang.codexcompanion.download.ArtifactDownloadRequest
import com.ningmengchang.codexcompanion.notification.NativeNotificationBridge
import com.ningmengchang.codexcompanion.notification.ThreadStatusNotifier
import com.ningmengchang.codexcompanion.share.NativeShareBridge
import com.ningmengchang.codexcompanion.share.NativeShareManager

class WebViewController(
    private val activity: Activity,
    private val webView: WebView,
    private val localPort: () -> Int,
    private val onPageStarted: () -> Unit,
    private val onPageReady: () -> Unit,
    private val onMainFrameError: (String) -> Unit,
    private val images: ImageInputController,
    private val onDownload: (ArtifactDownloadRequest) -> Unit,
) {
    @SuppressLint("SetJavaScriptEnabled")
    fun configure() {
        WebView.setWebContentsDebuggingEnabled(
            activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
        )
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, false)
        }
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = "$userAgentString CodexCompanion/0.1"
        }
        webView.webViewClient = LocalOnlyWebViewClient(localPort, onPageStarted, onPageReady, onMainFrameError)
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
            override fun onShowFileChooser(webView: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean =
                images.choose(callback, params)
        }
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            onDownload(
                ArtifactDownloadRequest(
                    url = url.orEmpty(),
                    userAgent = userAgent.orEmpty(),
                    contentDisposition = contentDisposition.orEmpty(),
                    mimeType = mimeType.orEmpty(),
                ),
            )
        }
    }

    fun installBridges(shareManager: NativeShareManager, notifier: ThreadStatusNotifier, onConnectionSettings: () -> Unit) {
        webView.addJavascriptInterface(NativeShareBridge(shareManager), NATIVE_SHARE_BRIDGE)
        webView.addJavascriptInterface(NativeNotificationBridge(notifier::notify), NATIVE_NOTIFICATION_BRIDGE)
        webView.addJavascriptInterface(NativeUiBridge(
            onOpenConnectionSettings = onConnectionSettings,
            onCameraCaptureRequested = { images.requestSource(RequestedImageSource.CAMERA) },
            onGalleryPickerRequested = { images.requestSource(RequestedImageSource.GALLERY) },
        ), NATIVE_UI_BRIDGE)
    }

    fun destroy() {
        webView.removeJavascriptInterface(NATIVE_SHARE_BRIDGE)
        webView.removeJavascriptInterface(NATIVE_NOTIFICATION_BRIDGE)
        webView.removeJavascriptInterface(NATIVE_UI_BRIDGE)
        webView.stopLoading()
        webView.destroy()
    }

    private companion object {
        const val NATIVE_SHARE_BRIDGE = "CodexNativeShare"
        const val NATIVE_NOTIFICATION_BRIDGE = "CodexNativeNotifications"
        const val NATIVE_UI_BRIDGE = "CodexNativeUi"
    }
}
