package com.ningmengchang.codexcompanion.download

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import com.ningmengchang.codexcompanion.share.MimeTypes
import com.ningmengchang.codexcompanion.share.ShareRequestValidator
import java.net.URI

data class ArtifactDownloadRequest(
    val url: String,
    val userAgent: String,
    val contentDisposition: String,
    val mimeType: String,
)

data class PreparedArtifactDownload(
    val uri: URI,
    val userAgent: String,
    val displayName: String,
    val storedName: String,
    val mimeType: String,
)

class NativeDownloadManager(private val activity: Activity) {
    fun prepare(request: ArtifactDownloadRequest, localPort: Int): Result<PreparedArtifactDownload> = runCatching {
        val uri = ShareRequestValidator.validate(request.url, localPort)
        val displayName = DownloadFileNames.resolve(request.url, request.contentDisposition)
        PreparedArtifactDownload(
            uri = uri,
            userAgent = request.userAgent,
            displayName = displayName,
            storedName = DownloadFileNames.storedName(displayName, System.currentTimeMillis()),
            mimeType = MimeTypes.resolve(displayName, request.mimeType),
        )
    }

    fun enqueue(download: PreparedArtifactDownload): Result<Long> = runCatching {
        val url = download.uri.toString()
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(download.displayName)
            setDescription("下载完成后可在“下载”目录查看")
            setMimeType(download.mimeType)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(false)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, download.storedName)
            val cookie = CookieManager.getInstance().getCookie(url).orEmpty()
            if (cookie.isNotBlank()) addRequestHeader("Cookie", cookie)
            if (download.userAgent.isNotBlank()) addRequestHeader("User-Agent", download.userAgent)
        }
        val manager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
    }
}
