package com.ningmengchang.codexcompanion

import com.ningmengchang.codexcompanion.web.NativeUiBridge
import org.junit.Assert.assertEquals
import org.junit.Test

class NativeUiBridgeTest {
    @Test
    fun forwardsConnectionSettingsRequestToNativeListener() {
        var requests = 0
        val bridge = NativeUiBridge { requests += 1 }

        bridge.openConnectionSettings()

        assertEquals(1, requests)
    }
}
