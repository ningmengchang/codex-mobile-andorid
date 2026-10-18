package com.ningmengchang.codexcompanion.web

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import java.io.File

class ImageInputController(
    private val activity: ComponentActivity,
    private val launchFile: (Intent) -> Unit,
    private val launchGallery: () -> Unit,
) {
    private var webFileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingCameraUri: Uri? = null
    @Volatile private var requestedImageSource: RequestedImageSource? = null
    @Volatile private var requestedImageSourceUntil = 0L

    fun requestSource(source: RequestedImageSource) {
        requestedImageSource = source
        requestedImageSourceUntil = SystemClock.elapsedRealtime() + IMAGE_SOURCE_REQUEST_WINDOW_MS
    }

    fun choose(filePathCallback: ValueCallback<Array<Uri>>, fileChooserParams: WebChromeClient.FileChooserParams): Boolean {
        webFileCallback?.onReceiveValue(null)
        webFileCallback = filePathCallback
        return runCatching {
            val requestedSource = requestedImageSource
                ?.takeIf { SystemClock.elapsedRealtime() <= requestedImageSourceUntil }
            requestedImageSource = null
            requestedImageSourceUntil = 0L
            if (ImageChooserPolicy.shouldOpenCamera(
                    requestedSource,
                    fileChooserParams.isCaptureEnabled,
                    fileChooserParams.acceptTypes,
                )
            ) {
                launchCameraChooser()
            } else if (ImageChooserPolicy.shouldOpenGalleryPicker(
                    requestedSource,
                    fileChooserParams.acceptTypes,
                )
            ) {
                pendingCameraUri = null
                launchGallery()
            } else {
                pendingCameraUri = null
                val chooserIntent = fileChooserParams.createIntent().apply {
                    if (fileChooserParams.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    }
                }
                launchFile(chooserIntent)
            }
            true
        }.getOrElse {
            webFileCallback = null
            pendingCameraUri = null
            filePathCallback.onReceiveValue(null)
            Toast.makeText(activity, "无法打开手机文件选择器", Toast.LENGTH_SHORT).show()
            false
        }

    }

    private fun launchCameraChooser() {
        val cameraDirectory = File(activity.cacheDir, "camera").apply {
            mkdirs()
            listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > CAMERA_CACHE_TTL_MS }
                ?.forEach { it.delete() }
        }
        val target = File.createTempFile("codex-photo-", ".jpg", cameraDirectory)
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.files", target)
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            clipData = ClipData.newRawUri("Codex 图片", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        check(intent.resolveActivity(activity.packageManager) != null) { "手机上没有可用的相机应用" }
        pendingCameraUri = uri
        launchFile(intent)
    }

    private fun extractSelectedUris(resultCode: Int, data: Intent?): List<Uri> {
        if (resultCode != Activity.RESULT_OK) return emptyList()
        val selected = linkedSetOf<Uri>()
        data?.clipData?.let { clip ->
            repeat(clip.itemCount) { index ->
                clip.getItemAt(index).uri?.let(selected::add)
            }
        }
        data?.data?.let(selected::add)
        if (selected.isEmpty()) {
            WebChromeClient.FileChooserParams.parseResult(resultCode, data)
                ?.filterNotNull()
                ?.forEach(selected::add)
        }
        return selected.toList()
    }

    fun completeSelection(uris: List<Uri>) {
        val callback = webFileCallback ?: run {
            pendingCameraUri = null
            return
        }
        webFileCallback = null
        pendingCameraUri = null
        callback.onReceiveValue(uris.distinct().takeIf { it.isNotEmpty() }?.toTypedArray())
    }

    fun onFileResult(resultCode: Int, data: Intent?) {
        val selected = extractSelectedUris(resultCode, data).ifEmpty {
            pendingCameraUri?.takeIf { resultCode == Activity.RESULT_OK }?.let(::listOf).orEmpty()
        }
        completeSelection(selected)
    }

    fun dispose() {
        webFileCallback?.onReceiveValue(null)
        webFileCallback = null
        pendingCameraUri = null
        requestedImageSource = null
        requestedImageSourceUntil = 0L
    }

    private companion object {
        const val CAMERA_CACHE_TTL_MS = 24L * 60L * 60L * 1_000L
        const val IMAGE_SOURCE_REQUEST_WINDOW_MS = 2_000L
    }
}
