package com.ningmengchang.codexcompanion.download

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat

class DownloadPermissionController(
    private val activity: ComponentActivity,
    private val localPort: () -> Int,
    private val requestStoragePermission: () -> Unit,
) {
    private val downloadManager = NativeDownloadManager(activity)
    private var pendingDownload: PreparedArtifactDownload? = null

    fun handle(request: ArtifactDownloadRequest) {
        val prepared = downloadManager.prepare(request, localPort())
            .getOrElse { error ->
                Toast.makeText(activity, error.message ?: "下载链接无效", Toast.LENGTH_LONG).show()
                return
            }
        if (
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingDownload = prepared
            requestStoragePermission()
            return
        }
        enqueueDownload(prepared)
    }

    private fun enqueueDownload(download: PreparedArtifactDownload) {
        downloadManager.enqueue(download)
            .onSuccess {
                Toast.makeText(activity, "已开始下载“${download.displayName}”，可在通知栏查看", Toast.LENGTH_LONG).show()
            }
            .onFailure { error ->
                Toast.makeText(activity, error.message ?: "无法启动下载", Toast.LENGTH_LONG).show()
            }
    }

    fun onPermissionResult(granted: Boolean) {
        val download = pendingDownload
        pendingDownload = null
        if (granted && download != null) enqueueDownload(download)
        else if (!granted) Toast.makeText(activity, "没有存储权限，文件未下载", Toast.LENGTH_LONG).show()
    }
}
