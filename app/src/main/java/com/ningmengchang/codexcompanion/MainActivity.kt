package com.ningmengchang.codexcompanion

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ningmengchang.codexcompanion.config.AuthMode
import com.ningmengchang.codexcompanion.config.ConfigStore
import com.ningmengchang.codexcompanion.config.ConnectionConfig
import com.ningmengchang.codexcompanion.share.NativeShareBridge
import com.ningmengchang.codexcompanion.share.NativeShareManager
import com.ningmengchang.codexcompanion.tunnel.TunnelRuntime
import com.ningmengchang.codexcompanion.tunnel.TunnelService
import com.ningmengchang.codexcompanion.tunnel.TunnelState
import com.ningmengchang.codexcompanion.web.LocalOnlyWebViewClient
import com.ningmengchang.codexcompanion.web.NativeUiBridge
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.max

class MainActivity : ComponentActivity(), NativeShareManager.Listener {
    private lateinit var configStore: ConfigStore
    private lateinit var shareManager: NativeShareManager

    private lateinit var root: View
    private lateinit var webView: WebView
    private lateinit var setupScroll: ScrollView
    private lateinit var loadingOverlay: View
    private lateinit var loadingText: TextView
    private lateinit var connectionPill: TextView
    private lateinit var nativeSettingsButton: ImageButton
    private lateinit var setupStatusText: TextView
    private lateinit var connectButton: Button
    private lateinit var disconnectButton: Button
    private lateinit var hostInput: EditText
    private lateinit var sshPortInput: EditText
    private lateinit var usernameInput: EditText
    private lateinit var authModeSpinner: Spinner
    private lateinit var secretLabel: TextView
    private lateinit var secretInput: EditText
    private lateinit var privateKeyRow: LinearLayout
    private lateinit var keyStatusText: TextView
    private lateinit var rememberSecretCheck: CheckBox
    private lateinit var autoConnectCheck: CheckBox
    private lateinit var remotePortInput: EditText
    private lateinit var localPortInput: EditText
    private lateinit var shareProgressPanel: View
    private lateinit var shareProgressText: TextView
    private lateinit var shareProgressBar: ProgressBar

    private var importedPrivateKeyPath = ""
    private var pageReady = false
    private var connectedUrl: String? = null
    private var approvalDialogKey: String? = null
    private var currentTunnelState: TunnelState = TunnelState.Idle
    private var webFileCallback: ValueCallback<Array<Uri>>? = null
    private var webDialogOpen = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { startTunnel() }

    private val privateKeyLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) importPrivateKey(uri)
    }

    private val webFileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val callback = webFileCallback ?: return@registerForActivityResult
        webFileCallback = null
        callback.onReceiveValue(
            WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        configStore = ConfigStore(this)
        bindViews()
        configureWindowInsets()
        configureForm()
        configureWebView()
        shareManager = NativeShareManager(this, { activeConfig().localServicePort }, this)
        webView.addJavascriptInterface(NativeShareBridge(shareManager), NATIVE_SHARE_BRIDGE)
        webView.addJavascriptInterface(
            NativeUiBridge { open ->
                runOnUiThread {
                    webDialogOpen = open
                    updateNativeSettingsButtonVisibility()
                }
            },
            NATIVE_UI_BRIDGE,
        )
        configureActions()
        observeTunnel()
        configureBackNavigation()

        val saved = configStore.load()
        populateForm(saved)
        if (configStore.hasConfig() && saved.autoConnect && saved.validationError() == null) {
            TunnelRuntime.pendingConfig = saved
            showLoading("正在连接电脑…")
            requestNotificationThenConnect()
        } else {
            showSetup(saved.validationError().takeIf { configStore.hasConfig() })
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && webView.visibility == View.VISIBLE && setupScroll.visibility != View.VISIBLE) {
            hideSystemBars()
        }
    }

    override fun onDestroy() {
        webFileCallback?.onReceiveValue(null)
        webFileCallback = null
        if (!isChangingConfigurations) {
            shareManager.close()
            webView.removeJavascriptInterface(NATIVE_SHARE_BRIDGE)
            webView.removeJavascriptInterface(NATIVE_UI_BRIDGE)
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun bindViews() {
        root = findViewById(R.id.root)
        webView = findViewById(R.id.webView)
        setupScroll = findViewById(R.id.setupScroll)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        loadingText = findViewById(R.id.loadingText)
        connectionPill = findViewById(R.id.connectionPill)
        nativeSettingsButton = findViewById(R.id.nativeSettingsButton)
        setupStatusText = findViewById(R.id.setupStatusText)
        connectButton = findViewById(R.id.connectButton)
        disconnectButton = findViewById(R.id.disconnectButton)
        hostInput = findViewById(R.id.hostInput)
        sshPortInput = findViewById(R.id.sshPortInput)
        usernameInput = findViewById(R.id.usernameInput)
        authModeSpinner = findViewById(R.id.authModeSpinner)
        secretLabel = findViewById(R.id.secretLabel)
        secretInput = findViewById(R.id.secretInput)
        privateKeyRow = findViewById(R.id.privateKeyRow)
        keyStatusText = findViewById(R.id.keyStatusText)
        rememberSecretCheck = findViewById(R.id.rememberSecretCheck)
        autoConnectCheck = findViewById(R.id.autoConnectCheck)
        remotePortInput = findViewById(R.id.remotePortInput)
        localPortInput = findViewById(R.id.localPortInput)
        shareProgressPanel = findViewById(R.id.shareProgressPanel)
        shareProgressText = findViewById(R.id.shareProgressText)
        shareProgressBar = findViewById(R.id.shareProgressBar)
    }

    private fun configureWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val setupVisible = setupScroll.visibility == View.VISIBLE
            view.updatePadding(
                top = if (setupVisible) systemBars.top else 0,
                bottom = max(ime.bottom, if (setupVisible) systemBars.bottom else 0),
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun configureForm() {
        val adapter = ArrayAdapter(
            this,
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

    private fun configureActions() {
        connectButton.setOnClickListener { saveAndConnect() }
        disconnectButton.setOnClickListener {
            TunnelRuntime.pendingConfig = null
            TunnelService.disconnect(this)
            connectedUrl = null
            pageReady = false
            webView.stopLoading()
            webView.loadUrl("about:blank")
            showSetup()
        }
        findViewById<Button>(R.id.importKeyButton).setOnClickListener {
            privateKeyLauncher.launch(arrayOf("*/*"))
        }
        nativeSettingsButton.setOnClickListener {
            populateForm(configStore.load())
            showSetup()
        }
        findViewById<Button>(R.id.cancelShareButton).setOnClickListener {
            shareManager.cancelActiveShare()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        WebView.setWebContentsDebuggingEnabled(
            applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
        )
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, false)
        }
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = "$userAgentString CodexCompanion/0.1"
        }
        webView.webViewClient = LocalOnlyWebViewClient(
            localPort = { activeConfig().localServicePort },
            onPageStarted = {
                webDialogOpen = false
                updateNativeSettingsButtonVisibility()
                if (!pageReady) showLoading("正在加载 Codex 随行…")
            },
            onPageReady = {
                pageReady = true
                loadingOverlay.visibility = View.GONE
                setupScroll.visibility = View.GONE
                webView.visibility = View.VISIBLE
                updateNativeSettingsButtonVisibility()
                connectionPill.visibility = View.GONE
                hideSystemBars()
                ViewCompat.requestApplyInsets(root)
            },
            onMainFrameError = { error ->
                if (currentTunnelState is TunnelState.Connected) {
                    showLoading(if (error.isBlank()) "页面连接失败，正在重试…" else "$error，正在重试…")
                    webView.postDelayed({
                        if (currentTunnelState is TunnelState.Connected) webView.reload()
                    }, 1_500)
                }
            },
        )
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                request.deny()
            }

            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams,
            ): Boolean {
                webFileCallback?.onReceiveValue(null)
                webFileCallback = filePathCallback
                return runCatching {
                    webFileLauncher.launch(fileChooserParams.createIntent())
                    true
                }.getOrElse {
                    webFileCallback = null
                    filePathCallback.onReceiveValue(null)
                    Toast.makeText(this@MainActivity, "无法打开手机文件选择器", Toast.LENGTH_SHORT).show()
                    false
                }
            }
        }
    }

    private fun observeTunnel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                TunnelRuntime.state.collect { state ->
                    currentTunnelState = state
                    renderTunnelState(state)
                }
            }
        }
    }

    private fun renderTunnelState(state: TunnelState) {
        when (state) {
            TunnelState.Idle -> {
                connectionPill.visibility = View.GONE
                if (loadingOverlay.visibility == View.VISIBLE) showSetup()
            }
            is TunnelState.Connecting -> {
                if (pageReady) showConnectionPill(state.message)
                else showLoading(state.message)
            }
            is TunnelState.AwaitingHostApproval -> showHostApproval(state)
            is TunnelState.Connected -> {
                approvalDialogKey = null
                disconnectButton.visibility = View.VISIBLE
                if (connectedUrl != state.localUrl || !pageReady) {
                    connectedUrl = state.localUrl
                    pageReady = false
                    showLoading("SSH 已连接，正在打开 Codex 随行…")
                    webView.loadUrl(state.localUrl)
                } else {
                    showWeb()
                }
            }
            is TunnelState.Reconnecting -> {
                val message = "连接中断，${state.delaySeconds} 秒后重试"
                if (pageReady) showConnectionPill(message) else showLoading(message)
            }
            is TunnelState.Error -> {
                approvalDialogKey = null
                showSetup(state.message)
            }
        }
    }

    private fun showHostApproval(state: TunnelState.AwaitingHostApproval) {
        val key = "${state.host}:${state.fingerprint}"
        if (approvalDialogKey == key) return
        approvalDialogKey = key
        showLoading("请确认电脑身份…")
        AlertDialog.Builder(this)
            .setTitle("确认电脑身份")
            .setMessage(
                "首次连接 ${state.host}。请尽量与电脑上的 SSH 指纹核对：\n\n${state.fingerprint}\n\n确认后，后续指纹变化会被自动拦截。",
            )
            .setCancelable(false)
            .setPositiveButton("信任并连接") { _, _ -> TunnelService.approveHost(this) }
            .setNegativeButton("取消") { _, _ -> TunnelService.rejectHost(this) }
            .show()
    }

    private fun saveAndConnect() {
        val config = activeConfig()
        val error = config.validationError()
        if (error != null) {
            showSetupError(error)
            return
        }
        if (autoConnectCheck.isChecked && !config.autoConnect) {
            Toast.makeText(this, "未保存口令，本次可连接，但下次需手动输入", Toast.LENGTH_LONG).show()
        }
        configStore.save(config)
        TunnelRuntime.pendingConfig = config
        hideKeyboard()
        showLoading("正在建立 SSH 连接…")
        requestNotificationThenConnect()
    }

    private fun activeConfig(): ConnectionConfig {
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

    private fun populateForm(config: ConnectionConfig) {
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

    private fun importPrivateKey(uri: Uri) {
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val target = configStore.importedPrivateKeyFile()
                    val temporary = java.io.File(target.parentFile, "${target.name}.tmp")
                    try {
                        contentResolver.openInputStream(uri).use { input ->
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
        return contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "私钥"
    }

    private fun requestNotificationThenConnect() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startTunnel()
        }
    }

    private fun startTunnel() {
        TunnelService.connect(this)
    }

    private fun showSetup(error: String? = null) {
        showSystemBars()
        loadingOverlay.visibility = View.GONE
        webView.visibility = View.GONE
        nativeSettingsButton.visibility = View.GONE
        connectionPill.visibility = View.GONE
        setupScroll.visibility = View.VISIBLE
        disconnectButton.visibility = if (currentTunnelState is TunnelState.Connected) View.VISIBLE else View.GONE
        connectButton.text = if (currentTunnelState is TunnelState.Connected) "保存并重新连接" else "保存并连接"
        if (error == null) {
            setupStatusText.visibility = View.GONE
        } else {
            showSetupError(error)
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun showSetupError(message: String) {
        setupStatusText.text = message
        setupStatusText.visibility = View.VISIBLE
    }

    private fun showLoading(message: String) {
        setupScroll.visibility = View.GONE
        webView.visibility = View.GONE
        nativeSettingsButton.visibility = View.GONE
        connectionPill.visibility = View.GONE
        loadingText.text = message
        loadingOverlay.visibility = View.VISIBLE
        ViewCompat.requestApplyInsets(root)
    }

    private fun showWeb() {
        setupScroll.visibility = View.GONE
        loadingOverlay.visibility = View.GONE
        webView.visibility = View.VISIBLE
        updateNativeSettingsButtonVisibility()
        hideSystemBars()
        ViewCompat.requestApplyInsets(root)
    }

    private fun showConnectionPill(message: String) {
        setupScroll.visibility = View.GONE
        loadingOverlay.visibility = View.GONE
        webView.visibility = View.VISIBLE
        updateNativeSettingsButtonVisibility()
        connectionPill.text = message
        connectionPill.visibility = View.VISIBLE
        ViewCompat.requestApplyInsets(root)
    }

    private fun updateNativeSettingsButtonVisibility() {
        val webActive = webView.visibility == View.VISIBLE &&
            setupScroll.visibility != View.VISIBLE &&
            loadingOverlay.visibility != View.VISIBLE
        nativeSettingsButton.visibility = if (webActive && !webDialogOpen) View.VISIBLE else View.GONE
    }

    private fun configureBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    shareProgressPanel.visibility == View.VISIBLE -> shareManager.cancelActiveShare()
                    setupScroll.visibility == View.VISIBLE && currentTunnelState is TunnelState.Connected -> showWeb()
                    webView.visibility == View.VISIBLE && webView.canGoBack() -> webView.goBack()
                    else -> moveTaskToBack(true)
                }
            }
        })
    }

    private fun hideKeyboard() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(currentFocus?.windowToken, 0)
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun showSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
    }

    override fun onShareStarted(fileName: String) {
        shareProgressText.text = "正在从电脑读取 $fileName…"
        shareProgressBar.isIndeterminate = true
        shareProgressPanel.visibility = View.VISIBLE
        updateWebShareProgress("正在从电脑读取文件…")
    }

    override fun onShareProgress(fileName: String, percent: Int?) {
        shareProgressText.text = if (percent == null) "正在读取 $fileName…" else "正在读取 $fileName · $percent%"
        shareProgressBar.isIndeterminate = percent == null
        if (percent != null) shareProgressBar.progress = percent
        updateWebShareProgress(if (percent == null) "正在从电脑读取文件…" else "正在从电脑读取文件… $percent%")
    }

    override fun onShareReady(requestId: String) {
        shareProgressPanel.visibility = View.GONE
        evaluateShareCallback("__codexNativeShareResolve", requestId)
    }

    override fun onShareFailed(requestId: String, message: String) {
        shareProgressPanel.visibility = View.GONE
        evaluateShareCallback("__codexNativeShareReject", requestId, message)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun updateWebShareProgress(message: String) {
        val quoted = JSONObject.quote(message)
        webView.evaluateJavascript(
            "window.__codexNativeShareProgress && window.__codexNativeShareProgress($quoted);",
            null,
        )
    }

    private fun evaluateShareCallback(function: String, requestId: String, message: String? = null) {
        val arguments = buildString {
            append(JSONObject.quote(requestId))
            if (message != null) append(',').append(JSONObject.quote(message))
        }
        webView.evaluateJavascript("window.$function && window.$function($arguments);", null)
    }

    private companion object {
        const val NATIVE_SHARE_BRIDGE = "CodexNativeShare"
        const val NATIVE_UI_BRIDGE = "CodexNativeUi"
        const val MAX_PRIVATE_KEY_BYTES = 2L * 1024L * 1024L
    }
}
