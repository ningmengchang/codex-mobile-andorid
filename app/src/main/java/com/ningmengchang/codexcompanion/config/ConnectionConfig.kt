package com.ningmengchang.codexcompanion.config

enum class AuthMode {
    PASSWORD,
    PRIVATE_KEY,
}

data class ConnectionConfig(
    val host: String = "",
    val sshPort: Int = 22,
    val username: String = "",
    val authMode: AuthMode = AuthMode.PASSWORD,
    val secret: String = "",
    val privateKeyPath: String = "",
    val rememberSecret: Boolean = true,
    val autoConnect: Boolean = true,
    val remoteServicePort: Int = DEFAULT_SERVICE_PORT,
    val localServicePort: Int = DEFAULT_SERVICE_PORT,
) {
    val localUrl: String
        get() = "http://127.0.0.1:$localServicePort/"

    fun validationError(): String? {
        if (host.isBlank()) return "请输入电脑地址或 SSH 域名"
        if (host.any { it.isWhitespace() }) return "电脑地址不能包含空格"
        if (sshPort !in 1..65535) return "SSH 端口必须在 1 到 65535 之间"
        if (username.isBlank()) return "请输入 SSH 用户名"
        if (remoteServicePort !in 1..65535) return "服务端口必须在 1 到 65535 之间"
        if (localServicePort !in 1024..65535) return "手机本地端口必须在 1024 到 65535 之间"
        if (authMode == AuthMode.PASSWORD && secret.isBlank()) return "请输入 SSH 密码"
        if (authMode == AuthMode.PRIVATE_KEY && privateKeyPath.isBlank()) return "请先导入 SSH 私钥"
        return null
    }

    companion object {
        const val DEFAULT_SERVICE_PORT = 3765
    }
}
