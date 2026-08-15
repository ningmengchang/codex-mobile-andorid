package com.ningmengchang.codexcompanion.share

import java.net.URI

object ShareRequestValidator {
    private val artifactRawPath = Regex("^/api/artifacts/[^/]+/raw$")

    fun validate(rawUrl: String, localPort: Int): URI {
        val uri = runCatching { URI(rawUrl) }
            .getOrElse { throw IllegalArgumentException("分享链接格式不正确") }
        require(uri.scheme.equals("http", ignoreCase = true)) { "仅允许读取手机本地转发服务" }
        require(uri.host == "127.0.0.1" || uri.host.equals("localhost", ignoreCase = true)) {
            "分享链接不是手机本地服务"
        }
        val effectivePort = if (uri.port == -1) 80 else uri.port
        require(effectivePort == localPort) { "分享链接端口与当前连接不一致" }
        require(uri.userInfo == null && uri.fragment == null) { "分享链接包含无效信息" }
        require(artifactRawPath.matches(uri.path.orEmpty())) { "仅允许分享 Codex 产出文件" }
        return uri
    }

    fun safeFileName(input: String): String {
        val normalized = input
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .trim()
            .trim('.')
            .take(180)
        return normalized.ifBlank { "Codex-文件" }
    }
}
