package com.ningmengchang.codexcompanion.web

import android.webkit.JavascriptInterface

class NativeUiBridge(
    private val onDialogStateChanged: (Boolean) -> Unit,
) {
    @JavascriptInterface
    fun setDialogOpen(open: Boolean) {
        onDialogStateChanged(open)
    }
}
