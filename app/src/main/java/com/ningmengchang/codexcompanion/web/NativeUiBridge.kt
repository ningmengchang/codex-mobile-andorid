package com.ningmengchang.codexcompanion.web

import android.webkit.JavascriptInterface

class NativeUiBridge(
    private val onOpenConnectionSettings: () -> Unit,
) {
    @JavascriptInterface
    fun openConnectionSettings() {
        onOpenConnectionSettings()
    }
}
