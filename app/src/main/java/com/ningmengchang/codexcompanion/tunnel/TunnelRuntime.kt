package com.ningmengchang.codexcompanion.tunnel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.ningmengchang.codexcompanion.config.ConnectionConfig

object TunnelRuntime {
    private val mutableState = MutableStateFlow<TunnelState>(TunnelState.Idle)
    val state: StateFlow<TunnelState> = mutableState.asStateFlow()

    @Volatile
    internal var pendingConfig: ConnectionConfig? = null

    internal fun publish(state: TunnelState) {
        mutableState.value = state
    }
}
