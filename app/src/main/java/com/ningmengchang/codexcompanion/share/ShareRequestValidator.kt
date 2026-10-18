package com.ningmengchang.codexcompanion.share

import java.net.URI
import com.ningmengchang.codexcompanion.web.LocalOriginPolicy

object ShareRequestValidator {
    private val artifactRawPath = Regex("^/api/artifacts/[^/]+/raw$")

    fun validate(rawUrl: String, localPort: Int): URI {
        val uri = runCatching { URI(rawUrl) }
            .getOrElse { throw IllegalArgumentException("分享链接格式不正确") }
        require(LocalOriginPolicy.isHttp(uri.scheme)) { "仅允许读取手机本地转发服务" }
        require(LocalOriginPolicy.isLoopback(uri.host)) {
            "分享链接不是手机本地服务"
        }
        require(LocalOriginPolicy.matchesPort(uri.port, localPort)) { "分享链接端口与当前连接不一致" }
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
