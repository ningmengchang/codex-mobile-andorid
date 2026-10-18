package com.ningmengchang.codexcompanion.web

// Navigation and artifact downloads share origin checks, not URL/path privileges.
object LocalOriginPolicy {
    fun isHttp(scheme: String?): Boolean = scheme.equals("http", ignoreCase = true)

    fun isLoopback(host: String?): Boolean =
        host == "127.0.0.1" || host.equals("localhost", ignoreCase = true)

    fun matchesPort(port: Int, expected: Int): Boolean = (if (port == -1) 80 else port) == expected

    fun allows(scheme: String?, host: String?, port: Int, expected: Int): Boolean =
        isHttp(scheme) && isLoopback(host) && matchesPort(port, expected)
}
