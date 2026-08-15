package com.ningmengchang.codexcompanion.share

import android.webkit.JavascriptInterface

class NativeShareBridge(private val manager: NativeShareManager) {
    @JavascriptInterface
    fun shareFile(rawUrl: String, fileName: String, mimeType: String, requestId: String) {
        manager.share(rawUrl, fileName, mimeType, requestId)
    }
}
