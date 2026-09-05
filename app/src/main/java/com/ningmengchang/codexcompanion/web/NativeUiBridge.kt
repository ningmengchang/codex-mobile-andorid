package com.ningmengchang.codexcompanion.web

import android.webkit.JavascriptInterface

class NativeUiBridge(
    private val onOpenConnectionSettings: () -> Unit,
    private val onCameraCaptureRequested: () -> Unit = {},
    private val onGalleryPickerRequested: () -> Unit = {},
) {
    @JavascriptInterface
    fun openConnectionSettings() {
        onOpenConnectionSettings()
    }

    @JavascriptInterface
    fun requestCameraCapture() {
        onCameraCaptureRequested()
    }

    @JavascriptInterface
    fun requestGalleryPicker() {
        onGalleryPickerRequested()
    }
}
