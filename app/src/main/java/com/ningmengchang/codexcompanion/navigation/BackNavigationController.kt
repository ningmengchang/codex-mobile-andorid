package com.ningmengchang.codexcompanion.navigation

import android.os.SystemClock
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import com.ningmengchang.codexcompanion.web.LocalOnlyWebViewClient
import com.ningmengchang.codexcompanion.web.NativeUiPatch

class BackNavigationController(
    private val activity: ComponentActivity,
    private val webView: WebView,
    private val snapshot: () -> BackNavigationSnapshot,
    private val historyPort: () -> Int,
    private val cancelShare: () -> Unit,
    private val showWeb: () -> Unit,
) {
    private var webBackPending = false
    private var rootBackArmedAt = 0L

    fun install() {
        activity.onBackPressedDispatcher.addCallback(activity, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val action = BackNavigationPolicy.decide(snapshot())
                when (action) {
                    BackAction.CANCEL_SHARE -> {
                        reset()
                        cancelShare()
                    }
                    BackAction.RETURN_TO_WEB -> {
                        reset()
                        showWeb()
                    }
                    BackAction.DELEGATE_TO_WEB -> handleWebBack()
                    BackAction.CONFIRM_BACKGROUND -> confirmBackground()
                }
            }
        })
    }

    private fun handleWebBack() {
        if (webBackPending) return
        webBackPending = true
        webView.evaluateJavascript(NativeUiPatch.handleBackScript) { rawResult ->
            webBackPending = false
            if (BackNavigationPolicy.javascriptConsumed(rawResult)) {
                reset()
                return@evaluateJavascript
            }
            if (canGoBackWithinApp()) {
                reset()
                webView.goBack()
            } else {
                confirmBackground()
            }
        }
    }

    private fun canGoBackWithinApp(): Boolean {
        val history = webView.copyBackForwardList()
        if (history.currentIndex <= 0) return false
        val previousUrl = history.getItemAtIndex(history.currentIndex - 1)?.url ?: return false
        val port = historyPort()
        return LocalOnlyWebViewClient.isAllowed(previousUrl, port)
    }

    private fun confirmBackground() {
        val now = SystemClock.elapsedRealtime()
        if (rootBackArmedAt > 0 && now - rootBackArmedAt <= ROOT_BACK_CONFIRM_MS) {
            rootBackArmedAt = 0L
            activity.moveTaskToBack(true)
            return
        }
        rootBackArmedAt = now
        Toast.makeText(activity, "已到首页，再按一次返回桌面", Toast.LENGTH_SHORT).show()
    }

    fun reset() {
        rootBackArmedAt = 0L
    }

    private companion object { const val ROOT_BACK_CONFIRM_MS = 2_000L }
}
