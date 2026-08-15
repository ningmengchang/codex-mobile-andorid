package com.ningmengchang.codexcompanion.tunnel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import com.ningmengchang.codexcompanion.MainActivity
import com.ningmengchang.codexcompanion.R
import com.ningmengchang.codexcompanion.config.AuthMode
import com.ningmengchang.codexcompanion.config.ConfigStore
import com.ningmengchang.codexcompanion.config.ConnectionConfig
import java.io.File
import java.net.BindException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.util.Base64
import java.util.Properties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TunnelService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var configStore: ConfigStore
    private var connectionJob: Job? = null

    @Volatile
    private var desiredConnected = false

    @Volatile
    private var activeSession: Session? = null

    @Volatile
    private var hostApproval: CompletableDeferred<Boolean>? = null

    override fun onCreate() {
        super.onCreate()
        configStore = ConfigStore(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> beginConnection()
            ACTION_DISCONNECT -> disconnectAndStop()
            ACTION_APPROVE_HOST -> hostApproval?.complete(true)
            ACTION_REJECT_HOST -> hostApproval?.complete(false)
            null -> {
                val config = configStore.load()
                if (configStore.hasConfig() && config.autoConnect) beginConnection()
                else stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        val preserveError = TunnelRuntime.state.value is TunnelState.Error
        desiredConnected = false
        hostApproval?.cancel()
        connectionJob?.cancel()
        disconnectSession()
        TunnelRuntime.pendingConfig = null
        serviceScope.cancel()
        if (!preserveError) TunnelRuntime.publish(TunnelState.Idle)
        super.onDestroy()
    }

    private fun beginConnection() {
        val config = TunnelRuntime.pendingConfig ?: configStore.load()
        config.validationError()?.let {
            publish(TunnelState.Error(it))
            stopSelf()
            return
        }
        desiredConnected = true
        startForegroundNotification("正在连接 ${config.host}")
        hostApproval?.cancel()
        connectionJob?.cancel()
        disconnectSession()
        connectionJob = serviceScope.launch { runConnectionLoop(config) }
    }

    private suspend fun runConnectionLoop(config: ConnectionConfig) {
        var attempt = 0
        while (desiredConnected && serviceScope.isActive) {
            if (attempt == 0) publish(TunnelState.Connecting())
            try {
                connectOnce(config)
                attempt = 0
                monitorConnection()
                if (desiredConnected) throw JSchException("SSH connection closed")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                disconnectSession()
                if (!desiredConnected) break
                val message = friendlyMessage(error)
                if (isFatal(error)) {
                    desiredConnected = false
                    publish(TunnelState.Error(message))
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    break
                }
                attempt += 1
                val waitSeconds = minOf(30L, 2L shl minOf(attempt - 1, 3))
                publish(TunnelState.Reconnecting(attempt, waitSeconds, message))
                delay(waitSeconds * 1_000)
                publish(TunnelState.Connecting("正在重试 SSH 连接…"))
            }
        }
    }

    private suspend fun connectOnce(config: ConnectionConfig) {
        val jsch = JSch()
        val knownHost = configStore.hasKnownHost(config.host, config.sshPort)
        if (knownHost) jsch.setKnownHosts(configStore.knownHostsFile.absolutePath)

        if (config.authMode == AuthMode.PRIVATE_KEY) {
            val keyFile = File(config.privateKeyPath)
            require(keyFile.isFile) { "导入的 SSH 私钥不存在，请重新导入" }
            if (config.secret.isBlank()) jsch.addIdentity(keyFile.absolutePath)
            else jsch.addIdentity(keyFile.absolutePath, config.secret.toByteArray(Charsets.UTF_8))
        }

        val session = jsch.getSession(config.username, config.host, config.sshPort)
        activeSession = session
        if (config.authMode == AuthMode.PASSWORD) {
            session.setPassword(config.secret.toByteArray(Charsets.UTF_8))
        }
        session.setConfig(
            Properties().apply {
                put("StrictHostKeyChecking", if (knownHost) "yes" else "no")
                put(
                    "PreferredAuthentications",
                    if (config.authMode == AuthMode.PRIVATE_KEY) "publickey" else "password,keyboard-interactive",
                )
            },
        )
        session.serverAliveInterval = 15_000
        session.serverAliveCountMax = 3
        session.connect(CONNECT_TIMEOUT_MS)

        if (!knownHost) {
            val hostKey = session.hostKey
            val approval = CompletableDeferred<Boolean>()
            hostApproval = approval
            publish(
                TunnelState.AwaitingHostApproval(
                    host = ConfigStore.hostMarker(config.host, config.sshPort),
                    fingerprint = sha256Fingerprint(hostKey.key),
                ),
            )
            val accepted = approval.await()
            hostApproval = null
            if (!accepted) {
                desiredConnected = false
                throw HostRejectedException()
            }
            check(session.isConnected) { "确认期间 SSH 连接已经断开，请重试" }
            configStore.saveKnownHost(config.host, config.sshPort, hostKey.type, hostKey.key)
        }

        session.setPortForwardingL(
            "127.0.0.1",
            config.localServicePort,
            "127.0.0.1",
            config.remoteServicePort,
        )
        publish(TunnelState.Connected(config.localUrl))
    }

    private suspend fun monitorConnection() {
        while (desiredConnected && serviceScope.isActive) {
            delay(10_000)
            val session = activeSession ?: throw JSchException("SSH session missing")
            if (!session.isConnected) throw JSchException("SSH connection closed")
            session.sendKeepAliveMsg()
        }
    }

    private fun disconnectAndStop() {
        desiredConnected = false
        TunnelRuntime.pendingConfig = null
        hostApproval?.cancel()
        connectionJob?.cancel()
        disconnectSession()
        publish(TunnelState.Idle)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun disconnectSession() {
        runCatching { activeSession?.disconnect() }
        activeSession = null
    }

    private fun publish(state: TunnelState) {
        TunnelRuntime.publish(state)
        val text = when (state) {
            TunnelState.Idle -> "连接已停止"
            is TunnelState.Connecting -> state.message
            is TunnelState.AwaitingHostApproval -> "等待确认电脑指纹"
            is TunnelState.Connected -> "已连接电脑端 Codex 随行"
            is TunnelState.Reconnecting -> "连接中断，${state.delaySeconds} 秒后重试"
            is TunnelState.Error -> state.message
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun startForegroundNotification(text: String) {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(text),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(desiredConnected)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.tunnel_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.tunnel_channel_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun friendlyMessage(error: Throwable): String {
        val message = generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" · ")
        val normalized = message.lowercase()
        return when {
            error is HostRejectedException -> "已取消信任这台电脑"
            normalized.contains("auth fail") || normalized.contains("authentication") ->
                "SSH 登录失败，请检查用户名、密码或私钥"
            normalized.contains("hostkey") || normalized.contains("host key") ->
                "电脑的 SSH 指纹与上次不同，已阻止连接"
            normalized.contains("address already in use") || error is BindException ->
                "手机本地 3765 端口已被占用，请先关闭 ConnextBot 中的同名转发"
            normalized.contains("connection refused") || error is ConnectException ->
                "电脑拒绝 SSH 连接，请检查地址、端口和 SSH 服务"
            normalized.contains("timeout") || error is SocketTimeoutException ->
                "SSH 连接超时，请检查电脑是否在线和网络是否可达"
            normalized.contains("privatekey") || normalized.contains("invalid private key") ->
                "SSH 私钥无法读取，请重新导入正确的私钥"
            message.isNotBlank() -> message
            else -> error.javaClass.simpleName
        }
    }

    private fun isFatal(error: Throwable): Boolean {
        val message = generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" ")
            .lowercase()
        return error is HostRejectedException || error is IllegalArgumentException ||
            message.contains("auth fail") || message.contains("authentication") ||
            message.contains("hostkey has been changed") || message.contains("host key has changed") ||
            message.contains("invalid privatekey") || message.contains("invalid private key")
    }

    private fun sha256Fingerprint(base64Key: String): String {
        val decoded = Base64.getDecoder().decode(base64Key)
        val digest = MessageDigest.getInstance("SHA-256").digest(decoded)
        return "SHA256:${Base64.getEncoder().withoutPadding().encodeToString(digest)}"
    }

    private class HostRejectedException : IllegalStateException("SSH host key rejected")

    companion object {
        private const val ACTION_CONNECT = "com.ningmengchang.codexcompanion.CONNECT"
        private const val ACTION_DISCONNECT = "com.ningmengchang.codexcompanion.DISCONNECT"
        private const val ACTION_APPROVE_HOST = "com.ningmengchang.codexcompanion.APPROVE_HOST"
        private const val ACTION_REJECT_HOST = "com.ningmengchang.codexcompanion.REJECT_HOST"
        private const val CHANNEL_ID = "codex_tunnel"
        private const val NOTIFICATION_ID = 3765
        private const val CONNECT_TIMEOUT_MS = 20_000

        fun connect(context: Context) = start(context, ACTION_CONNECT)
        fun disconnect(context: Context) = start(context, ACTION_DISCONNECT)
        fun approveHost(context: Context) = start(context, ACTION_APPROVE_HOST)
        fun rejectHost(context: Context) = start(context, ACTION_REJECT_HOST)

        private fun start(context: Context, action: String) {
            val intent = Intent(context, TunnelService::class.java).setAction(action)
            if (action == ACTION_CONNECT) ContextCompat.startForegroundService(context, intent)
            else context.startService(intent)
        }
    }
}
