package com.ningmengchang.codexcompanion

import com.ningmengchang.codexcompanion.web.NativeUiBridge
import org.junit.Assert.assertEquals
import org.junit.Test

class NativeUiBridgeTest {
    @Test
    fun forwardsDialogStateToNativeListener() {
        val states = mutableListOf<Boolean>()
        val bridge = NativeUiBridge(states::add)

        bridge.setDialogOpen(true)
        bridge.setDialogOpen(false)

        assertEquals(listOf(true, false), states)
    }
}
