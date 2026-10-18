package com.ningmengchang.codexcompanion.config

import android.net.Uri
import android.provider.OpenableColumns
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.ningmengchang.codexcompanion.R
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConnectionFormController(
    private val activity: ComponentActivity,
    private val configStore: ConfigStore,
    private val showSetupError: (String) -> Unit,
) {
    private val hostInput: EditText = activity.findViewById(R.id.hostInput)
    private val sshPortInput: EditText = activity.findViewById(R.id.sshPortInput)
    private val usernameInput: EditText = activity.findViewById(R.id.usernameInput)
    private val authModeSpinner: Spinner = activity.findViewById(R.id.authModeSpinner)
    private val secretLabel: TextView = activity.findViewById(R.id.secretLabel)
    private val secretInput: EditText = activity.findViewById(R.id.secretInput)
    private val privateKeyRow: LinearLayout = activity.findViewById(R.id.privateKeyRow)
    private val keyStatusText: TextView = activity.findViewById(R.id.keyStatusText)
    private val rememberSecretCheck: CheckBox = activity.findViewById(R.id.rememberSecretCheck)
    private val autoConnectCheck: CheckBox = activity.findViewById(R.id.autoConnectCheck)
    private val remotePortInput: EditText = activity.findViewById(R.id.remotePortInput)
    private val localPortInput: EditText = activity.findViewById(R.id.localPortInput)
    private var importedPrivateKeyPath = ""
    val autoConnectSelected: Boolean get() = autoConnectCheck.isChecked

    fun configure() {
        val adapter = ArrayAdapter(
            activity,
            android.R.layout.simple_spinner_item,
            listOf("密码", "私钥"),
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        authModeSpinner.adapter = adapter
        authModeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val privateKey = position == AuthMode.PRIVATE_KEY.ordinal
                privateKeyRow.visibility = if (privateKey) View.VISIBLE else View.GONE
                secretLabel.text = if (privateKey) "私钥口令（没有可留空）" else "SSH 密码"
                secretInput.hint = if (privateKey) "私钥没有口令时留空" else "仅加密保存在这台手机"
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        rememberSecretCheck.setOnCheckedChangeListener { _, checked ->
            if (!checked && secretInput.text.isNotEmpty()) autoConnectCheck.isChecked = false
        }
    }

    fun activeConfig(): ConnectionConfig {
        val authMode = if (authModeSpinner.selectedItemPosition == AuthMode.PRIVATE_KEY.ordinal) {
            AuthMode.PRIVATE_KEY
        } else {
            AuthMode.PASSWORD
        }
        val secret = secretInput.text?.toString().orEmpty()
        val remember = rememberSecretCheck.isChecked
        return ConnectionConfig(
            host = hostInput.text?.toString().orEmpty().trim(),
            sshPort = sshPortInput.text?.toString()?.toIntOrNull() ?: -1,
            username = usernameInput.text?.toString().orEmpty().trim(),
            authMode = authMode,
            secret = secret,
            privateKeyPath = importedPrivateKeyPath,
            rememberSecret = remember,
            autoConnect = autoConnectCheck.isChecked && (remember || secret.isBlank()),
            remoteServicePort = remotePortInput.text?.toString()?.toIntOrNull() ?: -1,
            localServicePort = localPortInput.text?.toString()?.toIntOrNull() ?: -1,
        )
    }

    fun populate(config: ConnectionConfig) {
        hostInput.setText(config.host)
        sshPortInput.setText(config.sshPort.toString())
        usernameInput.setText(config.username)
        authModeSpinner.setSelection(config.authMode.ordinal)
        secretInput.setText(config.secret)
        importedPrivateKeyPath = config.privateKeyPath
        keyStatusText.text = if (config.privateKeyPath.isNotBlank()) "已导入私钥" else "尚未导入"
        rememberSecretCheck.isChecked = config.rememberSecret
        autoConnectCheck.isChecked = config.autoConnect
        remotePortInput.setText(config.remoteServicePort.toString())
        localPortInput.setText(config.localServicePort.toString())
    }

    fun importPrivateKey(uri: Uri) {
        activity.lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val target = configStore.importedPrivateKeyFile()
                    val temporary = java.io.File(target.parentFile, "${target.name}.tmp")
                    try {
                        activity.contentResolver.openInputStream(uri).use { input ->
                            requireNotNull(input) { "无法读取所选私钥" }
                            FileOutputStream(temporary, false).use { output ->
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                var copied = 0L
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    copied += count
                                    require(copied <= MAX_PRIVATE_KEY_BYTES) { "私钥文件过大" }
                                    output.write(buffer, 0, count)
                                }
                            }
                        }
                        require(temporary.length() > 0) { "私钥文件为空" }
                        temporary.copyTo(target, overwrite = true)
                    } finally {
                        temporary.delete()
                    }
                    target.absolutePath
                }
            }
            result.onSuccess { path ->
                importedPrivateKeyPath = path
                keyStatusText.text = "已导入：${displayName(uri)}"
            }.onFailure { error ->
                showSetupError(error.message ?: "私钥导入失败")
            }
        }
    }

    private fun displayName(uri: Uri): String {
        return activity.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "私钥"
    }

    private companion object { const val MAX_PRIVATE_KEY_BYTES = 2L * 1024L * 1024L }
}
