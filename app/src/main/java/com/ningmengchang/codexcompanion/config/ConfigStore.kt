package com.ningmengchang.codexcompanion.config

import android.content.Context
import java.io.File

class ConfigStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val secretStore = SecretStore()

    val knownHostsFile: File
        get() = File(appContext.filesDir, "ssh/known_hosts")

    fun hasConfig(): Boolean = preferences.getString(KEY_HOST, "").orEmpty().isNotBlank()

    fun load(): ConnectionConfig {
        val rememberSecret = preferences.getBoolean(KEY_REMEMBER_SECRET, true)
        val encrypted = if (rememberSecret) preferences.getString(KEY_SECRET, "").orEmpty() else ""
        return ConnectionConfig(
            host = preferences.getString(KEY_HOST, "").orEmpty(),
            sshPort = preferences.getInt(KEY_SSH_PORT, 22),
            username = preferences.getString(KEY_USERNAME, "").orEmpty(),
            authMode = runCatching {
                AuthMode.valueOf(preferences.getString(KEY_AUTH_MODE, AuthMode.PASSWORD.name).orEmpty())
            }.getOrDefault(AuthMode.PASSWORD),
            secret = secretStore.decrypt(encrypted),
            privateKeyPath = preferences.getString(KEY_PRIVATE_KEY_PATH, "").orEmpty(),
            rememberSecret = rememberSecret,
            autoConnect = preferences.getBoolean(KEY_AUTO_CONNECT, true),
            remoteServicePort = preferences.getInt(KEY_REMOTE_PORT, ConnectionConfig.DEFAULT_SERVICE_PORT),
            localServicePort = preferences.getInt(KEY_LOCAL_PORT, ConnectionConfig.DEFAULT_SERVICE_PORT),
        )
    }

    fun save(config: ConnectionConfig) {
        preferences.edit()
            .putString(KEY_HOST, config.host.trim())
            .putInt(KEY_SSH_PORT, config.sshPort)
            .putString(KEY_USERNAME, config.username.trim())
            .putString(KEY_AUTH_MODE, config.authMode.name)
            .putString(KEY_PRIVATE_KEY_PATH, config.privateKeyPath)
            .putBoolean(KEY_REMEMBER_SECRET, config.rememberSecret)
            .putBoolean(KEY_AUTO_CONNECT, config.autoConnect)
            .putInt(KEY_REMOTE_PORT, config.remoteServicePort)
            .putInt(KEY_LOCAL_PORT, config.localServicePort)
            .apply {
                if (config.rememberSecret) putString(KEY_SECRET, secretStore.encrypt(config.secret))
                else remove(KEY_SECRET)
            }
            .apply()
    }

    fun hasKnownHost(host: String, port: Int): Boolean {
        val marker = hostMarker(host, port)
        return knownHostsFile.takeIf(File::isFile)
            ?.useLines { lines -> lines.any { it.substringBefore(' ') == marker } }
            ?: false
    }

    fun saveKnownHost(host: String, port: Int, keyType: String, key: String) {
        val marker = hostMarker(host, port)
        knownHostsFile.parentFile?.mkdirs()
        val retained = knownHostsFile.takeIf(File::isFile)
            ?.readLines()
            .orEmpty()
            .filter { it.isNotBlank() && it.substringBefore(' ') != marker }
        knownHostsFile.writeText((retained + "$marker $keyType $key").joinToString("\n", postfix = "\n"))
    }

    fun importedPrivateKeyFile(): File {
        val directory = File(appContext.filesDir, "ssh")
        directory.mkdirs()
        return File(directory, "id_imported")
    }

    companion object {
        fun hostMarker(host: String, port: Int): String = if (port == 22) host else "[$host]:$port"

        private const val PREFERENCES = "connection"
        private const val KEY_HOST = "host"
        private const val KEY_SSH_PORT = "ssh_port"
        private const val KEY_USERNAME = "username"
        private const val KEY_AUTH_MODE = "auth_mode"
        private const val KEY_SECRET = "secret"
        private const val KEY_PRIVATE_KEY_PATH = "private_key_path"
        private const val KEY_REMEMBER_SECRET = "remember_secret"
        private const val KEY_AUTO_CONNECT = "auto_connect"
        private const val KEY_REMOTE_PORT = "remote_port"
        private const val KEY_LOCAL_PORT = "local_port"
    }
}
