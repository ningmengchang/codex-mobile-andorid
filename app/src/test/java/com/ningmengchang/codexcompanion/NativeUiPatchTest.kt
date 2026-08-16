package com.ningmengchang.codexcompanion

import com.ningmengchang.codexcompanion.web.NativeUiPatch
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeUiPatchTest {
    @Test
    fun patchProvidesLayeredSinglePageBackNavigation() {
        val script = NativeUiPatch.script

        assertTrue(script.contains("closeTopDialog"))
        assertTrue(script.contains("goUpDirectory"))
        assertTrue(script.contains("goToPreviousView"))
        assertTrue(script.contains("hideWebFullscreenButton"))
        assertTrue(script.contains("fullscreenButton"))
        assertTrue(script.contains("nativeConnectionSettingsButton"))
        assertTrue(script.contains("openConnectionSettings"))
        assertTrue(script.contains("__codexNativeUiBack"))
        assertTrue(NativeUiPatch.handleBackScript.contains("__codexNativeUiBack"))
    }
}
