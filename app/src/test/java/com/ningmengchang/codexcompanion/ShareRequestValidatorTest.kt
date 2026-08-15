package com.ningmengchang.codexcompanion

import com.ningmengchang.codexcompanion.share.ShareRequestValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ShareRequestValidatorTest {
    @Test
    fun acceptsOnlyCurrentLocalArtifactRawUrl() {
        val uri = ShareRequestValidator.validate(
            "http://127.0.0.1:3765/api/artifacts/token-123/raw?download=1",
            3765,
        )

        assertEquals("127.0.0.1", uri.host)
        assertEquals("/api/artifacts/token-123/raw", uri.path)
    }

    @Test
    fun rejectsExternalHostWrongPortAndOtherApiPaths() {
        assertThrows(IllegalArgumentException::class.java) {
            ShareRequestValidator.validate("https://example.com/api/artifacts/a/raw", 3765)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ShareRequestValidator.validate("http://127.0.0.1:9999/api/artifacts/a/raw", 3765)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ShareRequestValidator.validate("http://127.0.0.1:3765/api/config", 3765)
        }
    }

    @Test
    fun removesPathCharactersFromSharedFileName() {
        assertEquals("_secret_report_.pptx", ShareRequestValidator.safeFileName("../secret/report?.pptx"))
        assertEquals("Codex-文件", ShareRequestValidator.safeFileName("..."))
    }
}
