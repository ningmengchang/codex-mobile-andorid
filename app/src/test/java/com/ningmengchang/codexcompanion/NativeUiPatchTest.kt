package com.ningmengchang.codexcompanion

import com.ningmengchang.codexcompanion.web.NativeUiPatch
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeUiPatchTest {
    @Test
    fun patchDelegatesPageHistoryToWebViewAfterClosingTransientLayers() {
        val script = NativeUiPatch.script

        assertTrue(script.contains("closeTopDialog"))
        assertTrue(script.contains("goUpDirectory"))
        assertTrue(script.contains("navigateWebHistory"))
        assertTrue(script.contains("activeViewName() === 'threads'"))
        assertTrue(!script.contains("goToPreviousView"))
        assertTrue(script.contains("hideWebFullscreenButton"))
        assertTrue(script.contains("fullscreenButton"))
        assertTrue(script.contains("installLandscapeChatStyles"))
        assertTrue(script.contains("nativeLandscapeChatStyles"))
        assertTrue(script.contains("orientation: landscape"))
        assertTrue(script.contains("#jumpQuestionButton"))
        assertTrue(script.contains("right: max(12px, env(safe-area-inset-right))"))
        assertTrue(script.contains("#chatView.active .composer textarea"))
        assertTrue(script.contains("font-size: 11px"))
        assertTrue(script.contains("padding: 5px 28px 5px 9px"))
        assertTrue(script.contains("nativeConnectionSettingsButton"))
        assertTrue(script.contains("openConnectionSettings"))
        assertTrue(script.contains("__codexNativeUiBack"))
        assertTrue(NativeUiPatch.handleBackScript.contains("__codexNativeUiBack"))
    }
}
