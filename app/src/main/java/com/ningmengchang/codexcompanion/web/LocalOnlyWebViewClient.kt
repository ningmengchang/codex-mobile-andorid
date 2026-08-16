package com.ningmengchang.codexcompanion.web

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.ningmengchang.codexcompanion.share.NativeSharePatch

class LocalOnlyWebViewClient(
    private val localPort: () -> Int,
    private val onPageStarted: () -> Unit,
    private val onPageReady: () -> Unit,
    private val onMainFrameError: (String) -> Unit,
) : WebViewClient() {

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        if (url != null && isAllowed(url, localPort())) onPageStarted()
    }

    override fun onPageFinished(view: WebView, url: String?) {
        if (url == null || !isAllowed(url, localPort())) return
        view.evaluateJavascript(NativeSharePatch.script, null)
        view.evaluateJavascript(NativeUiPatch.script) { onPageReady() }
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        if (isAllowed(url, localPort())) return false
        openExternal(view, request.url)
        return true
    }

    @Deprecated("Legacy WebView callback")
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
        if (isAllowed(url, localPort())) return false
        openExternal(view, Uri.parse(url))
        return true
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame) onMainFrameError(error.description?.toString().orEmpty())
    }

    private fun openExternal(view: WebView, uri: Uri) {
        if (uri.scheme !in setOf("https", "http", "mailto")) return
        runCatching {
            view.context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }

    companion object {
        fun isAllowed(rawUrl: String, port: Int): Boolean {
            val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return false
            if (!uri.scheme.equals("http", ignoreCase = true)) return false
            if (uri.host != "127.0.0.1" && !uri.host.equals("localhost", ignoreCase = true)) return false
            val effectivePort = if (uri.port == -1) 80 else uri.port
            return effectivePort == port
        }
    }
}
