package com.ningmengchang.codexcompanion.share

import android.app.Activity
import android.view.View
import android.webkit.WebView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.ningmengchang.codexcompanion.R
import org.json.JSONObject

class ShareProgressController(activity: Activity, private val webView: WebView) : NativeShareManager.Listener {
    private val shareProgressPanel: View = activity.findViewById(R.id.shareProgressPanel)
    private val shareProgressText: TextView = activity.findViewById(R.id.shareProgressText)
    private val shareProgressBar: ProgressBar = activity.findViewById(R.id.shareProgressBar)
    private val context = activity
    val isSharing: Boolean get() = shareProgressPanel.visibility == View.VISIBLE

    override fun onShareStarted(fileName: String) {
        shareProgressText.text = "正在从电脑读取 $fileName…"
        shareProgressBar.isIndeterminate = true
        shareProgressPanel.visibility = View.VISIBLE
        updateWebShareProgress("正在从电脑读取文件…")
    }

    override fun onShareProgress(fileName: String, percent: Int?) {
        shareProgressText.text = if (percent == null) "正在读取 $fileName…" else "正在读取 $fileName · $percent%"
        shareProgressBar.isIndeterminate = percent == null
        if (percent != null) shareProgressBar.progress = percent
        updateWebShareProgress(if (percent == null) "正在从电脑读取文件…" else "正在从电脑读取文件… $percent%")
    }

    override fun onShareReady(requestId: String) {
        shareProgressPanel.visibility = View.GONE
        evaluateShareCallback("__codexNativeShareResolve", requestId)
    }

    override fun onShareFailed(requestId: String, message: String) {
        shareProgressPanel.visibility = View.GONE
        evaluateShareCallback("__codexNativeShareReject", requestId, message)
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    private fun updateWebShareProgress(message: String) {
        val quoted = JSONObject.quote(message)
        webView.evaluateJavascript(
            "window.__codexNativeShareProgress && window.__codexNativeShareProgress($quoted);",
            null,
        )
    }

    private fun evaluateShareCallback(function: String, requestId: String, message: String? = null) {
        val arguments = buildString {
            append(JSONObject.quote(requestId))
            if (message != null) append(',').append(JSONObject.quote(message))
        }
        webView.evaluateJavascript("window.$function && window.$function($arguments);", null)
    }

}
