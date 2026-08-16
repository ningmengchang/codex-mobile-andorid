package com.ningmengchang.codexcompanion

import com.ningmengchang.codexcompanion.navigation.BackAction
import com.ningmengchang.codexcompanion.navigation.BackNavigationPolicy
import com.ningmengchang.codexcompanion.navigation.BackNavigationSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackNavigationPolicyTest {
    @Test
    fun resolvesNativeBackLayersInPriorityOrder() {
        assertEquals(
            BackAction.CANCEL_SHARE,
            decide(share = true, setup = true, web = true, connected = true),
        )
        assertEquals(
            BackAction.RETURN_TO_WEB,
            decide(setup = true, connected = true),
        )
        assertEquals(
            BackAction.DELEGATE_TO_WEB,
            decide(web = true, connected = true),
        )
        assertEquals(
            BackAction.CONFIRM_BACKGROUND,
            decide(setup = true, connected = false),
        )
    }

    @Test
    fun parsesWebViewJavascriptBooleanResults() {
        assertTrue(BackNavigationPolicy.javascriptConsumed("true"))
        assertTrue(BackNavigationPolicy.javascriptConsumed("\"true\""))
        assertFalse(BackNavigationPolicy.javascriptConsumed("false"))
        assertFalse(BackNavigationPolicy.javascriptConsumed(null))
    }

    private fun decide(
        share: Boolean = false,
        setup: Boolean = false,
        web: Boolean = false,
        connected: Boolean = false,
    ): BackAction = BackNavigationPolicy.decide(
        BackNavigationSnapshot(
            shareInProgress = share,
            setupVisible = setup,
            webVisible = web,
            tunnelConnected = connected,
        ),
    )
}
