package com.ningmengchang.codexcompanion.share

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import androidx.core.content.FileProvider
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NativeShareManager(
    private val activity: Activity,
    private val localPort: () -> Int,
    private val listener: Listener,
) {
    interface Listener {
        fun onShareStarted(fileName: String)
        fun onShareProgress(fileName: String, percent: Int?)
        fun onShareReady(requestId: String)
        fun onShareFailed(requestId: String, message: String)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null

    fun share(rawUrl: String, requestedName: String, requestedType: String, requestId: String) {
        activity.runOnUiThread {
            if (activeJob?.isActive == true) {
                listener.onShareFailed(requestId, "已有文件正在准备，请稍候")
                return@runOnUiThread
            }
            val uri = runCatching { ShareRequestValidator.validate(rawUrl, localPort()) }
                .getOrElse {
                    listener.onShareFailed(requestId, it.message ?: "分享链接无效")
                    return@runOnUiThread
                }
            val fileName = ShareRequestValidator.safeFileName(requestedName)
            val cookie = CookieManager.getInstance().getCookie(uri.toString()).orEmpty()
            listener.onShareStarted(fileName)
            activeJob = scope.launch {
                try {
                    cleanupExpiredFiles()
                    val downloaded = download(uri, fileName, requestedType, cookie)
                    withContext(Dispatchers.Main) {
                        openShareSheet(downloaded.file, downloaded.mimeType)
                        listener.onShareReady(requestId)
                    }
                } catch (cancelled: CancellationException) {
                    withContext(NonCancellable + Dispatchers.Main) {
                        listener.onShareFailed(requestId, "已取消文件分享")
                    }
                } catch (error: Throwable) {
                    withContext(Dispatchers.Main) {
                        listener.onShareFailed(requestId, friendlyMessage(error))
                    }
                }
            }
        }
    }

    fun cancelActiveShare() {
        activeJob?.cancel()
    }

    fun close() {
        scope.cancel()
    }

    private suspend fun download(
        uri: URI,
        fileName: String,
        requestedType: String,
        cookie: String,
    ): DownloadedFile {
        val requestDirectory = File(activity.cacheDir, "shared/${UUID.randomUUID()}")
        check(requestDirectory.mkdirs()) { "无法创建文件缓存目录" }
        val output = File(requestDirectory, fileName)
        val connection = (uri.toURL().openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 20_000
            readTimeout = 120_000
            requestMethod = "GET"
            setRequestProperty("Accept", "*/*")
            setRequestProperty("User-Agent", "CodexCompanion-Android/0.1")
            if (cookie.isNotBlank()) setRequestProperty("Cookie", cookie)
        }
        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText().take(300) }.orEmpty()
                throw IllegalStateException(
                    if (detail.isBlank()) "文件读取失败（$responseCode）" else "文件读取失败（$responseCode）：$detail",
                )
            }
            val totalBytes = connection.contentLengthLong.takeIf { it >= 0 }
            if (totalBytes != null && totalBytes > MAX_SHARE_BYTES) {
                throw IllegalStateException("文件超过 512 MB，暂不支持直接分享")
            }
            var copied = 0L
            var lastReported = -1
            BufferedInputStream(connection.inputStream).use { input ->
                BufferedOutputStream(FileOutputStream(output)).use { target ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        target.write(buffer, 0, count)
                        copied += count
                        if (copied > MAX_SHARE_BYTES) {
                            throw IllegalStateException("文件超过 512 MB，暂不支持直接分享")
                        }
                        val percent = totalBytes?.takeIf { it > 0 }?.let { (copied * 100 / it).toInt().coerceAtMost(100) }
                        if (percent == null || percent >= lastReported + 2) {
                            lastReported = percent ?: lastReported
                            withContext(Dispatchers.Main) { listener.onShareProgress(fileName, percent) }
                        }
                    }
                }
            }
            check(output.isFile) { "文件缓存失败" }
            val mime = MimeTypes.resolve(fileName, connection.contentType ?: requestedType)
            return DownloadedFile(output, mime)
        } catch (error: Throwable) {
            output.delete()
            requestDirectory.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun openShareSheet(file: File, mimeType: String) {
        val contentUri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.files",
            file,
        )
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            clipData = ClipData.newRawUri(file.name, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(sendIntent, "分享 ${file.name}").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(chooser)
    }

    private fun cleanupExpiredFiles() {
        val root = File(activity.cacheDir, "shared")
        val cutoff = System.currentTimeMillis() - CACHE_LIFETIME_MS
        root.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.deleteRecursively() }
    }

    private fun friendlyMessage(error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            message.contains("401") || message.contains("403") -> "登录状态已失效，请回到页面重新配对"
            message.contains("timed out", ignoreCase = true) -> "从电脑读取文件超时，请检查网络后重试"
            message.isNotBlank() -> message
            else -> "文件分享失败，请重试"
        }
    }

    private data class DownloadedFile(val file: File, val mimeType: String)

    private companion object {
        const val MAX_SHARE_BYTES = 512L * 1024L * 1024L
        const val CACHE_LIFETIME_MS = 24L * 60L * 60L * 1_000L
    }
}
