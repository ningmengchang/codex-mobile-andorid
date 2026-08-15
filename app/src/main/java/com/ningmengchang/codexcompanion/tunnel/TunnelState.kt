package com.ningmengchang.codexcompanion.tunnel

sealed interface TunnelState {
    data object Idle : TunnelState
    data class Connecting(val message: String = "正在建立 SSH 连接…") : TunnelState
    data class AwaitingHostApproval(
        val host: String,
        val fingerprint: String,
    ) : TunnelState
    data class Connected(val localUrl: String) : TunnelState
    data class Reconnecting(
        val attempt: Int,
        val delaySeconds: Long,
        val reason: String,
    ) : TunnelState
    data class Error(val message: String) : TunnelState
}
