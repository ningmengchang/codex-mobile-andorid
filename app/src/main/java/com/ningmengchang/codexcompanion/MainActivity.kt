package com.ningmengchang.codexcompanion

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
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
import com.ningmengchang.codexcompanion.config.ConfigStore
import com.ningmengchang.codexcompanion.config.ConnectionFormController
import com.ningmengchang.codexcompanion.download.DownloadPermissionController
import com.ningmengchang.codexcompanion.navigation.BackNavigationController
import com.ningmengchang.codexcompanion.navigation.BackNavigationSnapshot
import com.ningmengchang.codexcompanion.notification.ThreadStatusNotifier
import com.ningmengchang.codexcompanion.share.NativeShareManager
import com.ningmengchang.codexcompanion.share.ShareProgressController
import com.ningmengchang.codexcompanion.tunnel.TunnelRuntime
import com.ningmengchang.codexcompanion.tunnel.TunnelService
import com.ningmengchang.codexcompanion.tunnel.TunnelState
import com.ningmengchang.codexcompanion.web.ImageInputController
import com.ningmengchang.codexcompanion.web.WebViewController
import kotlin.math.max
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var configStore: ConfigStore
    private lateinit var connectionForm: ConnectionFormController
    private lateinit var images: ImageInputController
    private lateinit var downloads: DownloadPermissionController
    private lateinit var shareProgress: ShareProgressController
    private lateinit var webController: WebViewController
    private lateinit var backNavigation: BackNavigationController
    private lateinit var shareManager: NativeShareManager
    private lateinit var threadStatusNotifier: ThreadStatusNotifier

    private lateinit var root: View
    private lateinit var webView: WebView
    private lateinit var setupScroll: ScrollView
    private lateinit var loadingOverlay: View
    private lateinit var loadingText: TextView
    private lateinit var setupStatusText: TextView
    private lateinit var connectButton: Button
    private lateinit var disconnectButton: Button

    private var pageReady = false
    private var connectedUrl: String? = null
    private var approvalDialogKey: String? = null
    private var currentTunnelState: TunnelState = TunnelState.Idle

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { startTunnel() }

    private val legacyStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        downloads.onPermissionResult(granted)
    }

    private val privateKeyLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) connectionForm.importPrivateKey(uri)
    }

    private val webFileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        images.onFileResult(result.resultCode, result.data)
    }

    private val galleryFileLauncher = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_CHAT_IMAGES),
    ) { uris ->
        images.completeSelection(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        configStore = ConfigStore(this)
        bindViews()
        connectionForm = ConnectionFormController(this, configStore, ::showSetupError)
        images = ImageInputController(this, { webFileLauncher.launch(it) }, {
            galleryFileLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        })
        downloads = DownloadPermissionController(this, { connectionForm.activeConfig().localServicePort }, {
            legacyStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        })
        shareProgress = ShareProgressController(this, webView)
        backNavigation = BackNavigationController(this, webView, {
            BackNavigationSnapshot(
                shareInProgress = shareProgress.isSharing,
                setupVisible = setupScroll.visibility == View.VISIBLE,
                webVisible = webView.visibility == View.VISIBLE,
                tunnelConnected = currentTunnelState is TunnelState.Connected,
            )
        }, {
            connectedUrl?.let { runCatching { Uri.parse(it).port }.getOrNull() }
                ?.takeIf { it > 0 } ?: configStore.load().localServicePort
        }, { shareManager.cancelActiveShare() }, ::showWeb)
        threadStatusNotifier = ThreadStatusNotifier(this)
        configureWindowInsets()
        connectionForm.configure()
        configureWebView()
        shareManager = NativeShareManager(this, { connectionForm.activeConfig().localServicePort }, shareProgress)
        webController.installBridges(shareManager, threadStatusNotifier) {
            runOnUiThread {
                backNavigation.reset()
                connectionForm.populate(configStore.load())
                showSetup()
            }
        }
        configureActions()
        observeTunnel()
        backNavigation.install()

        val saved = configStore.load()
        connectionForm.populate(saved)
        val existingTunnel = TunnelRuntime.state.value
        if (existingTunnel.isActive) {
            currentTunnelState = existingTunnel
            renderTunnelState(existingTunnel)
        } else if (configStore.hasConfig() && saved.autoConnect && saved.validationError() == null) {
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
        images.dispose()
        if (!isChangingConfigurations) {
            shareManager.close()
            webController.destroy()
        }
        super.onDestroy()
    }

    private fun bindViews() {
        root = findViewById(R.id.root)
        webView = findViewById(R.id.webView)
        setupScroll = findViewById(R.id.setupScroll)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        loadingText = findViewById(R.id.loadingText)
        setupStatusText = findViewById(R.id.setupStatusText)
        connectButton = findViewById(R.id.connectButton)
        disconnectButton = findViewById(R.id.disconnectButton)
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
        findViewById<Button>(R.id.cancelShareButton).setOnClickListener {
            shareManager.cancelActiveShare()
        }
    }

    private fun configureWebView() {
        webController = WebViewController(
            this, webView, { connectionForm.activeConfig().localServicePort },
            onPageStarted = { if (!pageReady) showLoading("正在加载 Codex 随行…") },
            onPageReady = {
                pageReady = true
                loadingOverlay.visibility = View.GONE
                setupScroll.visibility = View.GONE
                webView.visibility = View.VISIBLE
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
            images = images,
            onDownload = downloads::handle,
        )
        webController.configure()
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
                if (loadingOverlay.visibility == View.VISIBLE) showSetup()
            }
            is TunnelState.Connecting -> {
                if (pageReady) showWeb()
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
                if (pageReady) showWeb() else showLoading(message)
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
        val config = connectionForm.activeConfig()
        val error = config.validationError()
        if (error != null) {
            showSetupError(error)
            return
        }
        if (connectionForm.autoConnectSelected && !config.autoConnect) {
            Toast.makeText(this, "未保存口令，本次可连接，但下次需手动输入", Toast.LENGTH_LONG).show()
        }
        configStore.save(config)
        TunnelRuntime.pendingConfig = config
        hideKeyboard()
        showLoading("正在建立 SSH 连接…")
        requestNotificationThenConnect()
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
        if (isFinishing || isDestroyed) return
        TunnelService.connect(this)
    }

    private fun showSetup(error: String? = null) {
        showSystemBars()
        loadingOverlay.visibility = View.GONE
        webView.visibility = View.GONE
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
        loadingText.text = message
        loadingOverlay.visibility = View.VISIBLE
        ViewCompat.requestApplyInsets(root)
    }

    private fun showWeb() {
        setupScroll.visibility = View.GONE
        loadingOverlay.visibility = View.GONE
        webView.visibility = View.VISIBLE
        hideSystemBars()
        ViewCompat.requestApplyInsets(root)
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

    private companion object { const val MAX_CHAT_IMAGES = 4 }
}
