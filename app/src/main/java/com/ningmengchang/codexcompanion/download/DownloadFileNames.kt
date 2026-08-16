package com.ningmengchang.codexcompanion.download

import com.ningmengchang.codexcompanion.share.ShareRequestValidator
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object DownloadFileNames {
    private val extendedFilename = Regex(
        """(?:^|;)\s*filename\*\s*=\s*(?:\"([^\"]*)\"|([^;]*))""",
        RegexOption.IGNORE_CASE,
    )
    private val plainFilename = Regex(
        """(?:^|;)\s*filename\s*=\s*(?:\"([^\"]*)\"|([^;]*))""",
        RegexOption.IGNORE_CASE,
    )

    fun resolve(rawUrl: String, contentDisposition: String): String {
        val candidate = parameter(contentDisposition, extendedFilename)?.let(::decodeExtended)
            ?: parameter(contentDisposition, plainFilename)
            ?: runCatching { URI(rawUrl).path.substringAfterLast('/') }
                .getOrNull()
                ?.takeUnless { it.equals("raw", ignoreCase = true) || it.equals("office", ignoreCase = true) }
            ?: "Codex-文件"
        return ShareRequestValidator.safeFileName(candidate)
    }

    fun storedName(displayName: String, timestampMillis: Long): String {
        val dot = displayName.lastIndexOf('.').takeIf { it in 1 until displayName.lastIndex }
        val extension = dot?.let(displayName::substring).orEmpty()
        val base = dot?.let { displayName.substring(0, it) } ?: displayName
        val suffix = "-${java.lang.Long.toString(timestampMillis, 36)}"
        val maximumBaseLength = (180 - extension.length - suffix.length).coerceAtLeast(1)
        return "${base.take(maximumBaseLength)}$suffix$extension"
    }

    private fun parameter(contentDisposition: String, pattern: Regex): String? {
        val match = pattern.find(contentDisposition) ?: return null
        return (match.groups[1]?.value ?: match.groups[2]?.value)
            ?.trim()
            ?.takeIf(String::isNotBlank)
    }

    private fun decodeExtended(value: String): String {
        val encoded = value.substringAfter("''", value)
        return runCatching {
            URLDecoder.decode(encoded.replace("+", "%2B"), StandardCharsets.UTF_8.name())
        }.getOrDefault(encoded)
    }
}
