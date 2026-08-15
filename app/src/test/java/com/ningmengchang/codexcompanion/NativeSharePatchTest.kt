package com.ningmengchang.codexcompanion

import com.ningmengchang.codexcompanion.share.NativeSharePatch
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeSharePatchTest {
    @Test
    fun patchDefersArtifactBytesToNativeBridge() {
        val script = NativeSharePatch.script

        assertTrue(script.contains("/api\\/artifacts"))
        assertTrue(script.contains("new Blob([]"))
        assertTrue(script.contains("CodexNativeShare.shareFile"))
        assertTrue(script.contains("__codexNativeShareResolve"))
        assertTrue(script.contains("__codexNativeShareReject"))
    }
}
