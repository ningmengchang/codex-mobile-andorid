package com.ningmengchang.codexcompanion.notification

import android.webkit.JavascriptInterface

class NativeNotificationBridge(
    private val onThreadStatusChanged: (ThreadStatusEvent) -> Unit,
) {
    @JavascriptInterface
    fun threadStatusChanged(threadId: String?, threadName: String?, status: String?) {
        ThreadStatusEvent.create(threadId, threadName, status)?.let(onThreadStatusChanged)
    }
}
