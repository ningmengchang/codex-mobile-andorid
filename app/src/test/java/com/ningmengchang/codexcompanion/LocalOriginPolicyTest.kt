package com.ningmengchang.codexcompanion

import com.ningmengchang.codexcompanion.share.ShareRequestValidator
import com.ningmengchang.codexcompanion.web.LocalOriginPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalOriginPolicyTest {
    @Test fun localOriginsKeepTheirExactSchemeHostAndPortRules() {
        assertTrue(LocalOriginPolicy.allows("http", "127.0.0.1", 3765, 3765))
        assertTrue(LocalOriginPolicy.allows("HTTP", "LOCALHOST", -1, 80))
        assertFalse(LocalOriginPolicy.allows("https", "localhost", 3765, 3765))
        assertFalse(LocalOriginPolicy.allows("http", "localhost.evil.test", 3765, 3765))
        assertFalse(LocalOriginPolicy.allows("http", "127.0.0.1", 3766, 3765))
        assertFalse(LocalOriginPolicy.allows(null, null, -1, 80))
    }

    @Test fun artifactValidationRetainsStricterPathAndCredentialRestrictions() {
        ShareRequestValidator.validate("http://127.0.0.1:3765/api/artifacts/token/raw?download=1", 3765)
        for (url in listOf(
            "http://127.0.0.1:3765/",
            "http://user@127.0.0.1:3765/api/artifacts/token/raw",
            "http://127.0.0.1:3765/api/artifacts/token/raw#fragment",
            "http://127.0.0.1:3765/api/artifacts/token/office",
        )) {
            assertTrue(runCatching { ShareRequestValidator.validate(url, 3765) }.isFailure)
        }
    }
}
