package com.ningmengchang.codexcompanion

import com.ningmengchang.codexcompanion.config.AuthMode
import com.ningmengchang.codexcompanion.config.ConfigStore
import com.ningmengchang.codexcompanion.config.ConnectionConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionConfigTest {
    @Test
    fun validPasswordConnectionPassesValidation() {
        val config = ConnectionConfig(
            host = "example.com",
            sshPort = 2222,
            username = "codex",
            authMode = AuthMode.PASSWORD,
            secret = "secret",
        )

        assertNull(config.validationError())
        assertEquals("http://127.0.0.1:3765/", config.localUrl)
    }

    @Test
    fun passwordIsRequiredButPrivateKeyPassphraseIsOptional() {
        val passwordConfig = ConnectionConfig(host = "10.0.0.2", username = "me")
        assertTrue(passwordConfig.validationError().orEmpty().contains("密码"))

        val keyConfig = passwordConfig.copy(
            authMode = AuthMode.PRIVATE_KEY,
            privateKeyPath = "/private/id_ed25519",
        )
        assertNull(keyConfig.validationError())
    }

    @Test
    fun invalidPortsAreRejected() {
        val base = ConnectionConfig(host = "pc", username = "me", secret = "pw")
        assertTrue(base.copy(sshPort = 0).validationError().orEmpty().contains("SSH 端口"))
        assertTrue(base.copy(localServicePort = 80).validationError().orEmpty().contains("手机本地端口"))
    }

    @Test
    fun knownHostMarkerMatchesOpenSshFormat() {
        assertEquals("example.com", ConfigStore.hostMarker("example.com", 22))
        assertEquals("[example.com]:2222", ConfigStore.hostMarker("example.com", 2222))
    }
}
