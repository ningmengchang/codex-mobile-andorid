package com.ningmengchang.codexcompanion.share

import android.webkit.MimeTypeMap

object MimeTypes {
    private val overrides = mapOf(
        "md" to "text/markdown",
        "ppt" to "application/vnd.ms-powerpoint",
        "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "doc" to "application/msword",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xls" to "application/vnd.ms-excel",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "zip" to "application/zip",
        "pdf" to "application/pdf",
    )

    fun resolve(fileName: String, reportedType: String?): String {
        val cleanReported = reportedType?.substringBefore(';')?.trim().orEmpty()
        if (cleanReported.isNotBlank() && cleanReported != "application/octet-stream") return cleanReported
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return overrides[extension]
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"
    }
}
