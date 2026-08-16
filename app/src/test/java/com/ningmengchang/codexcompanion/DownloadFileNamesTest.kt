package com.ningmengchang.codexcompanion

import com.ningmengchang.codexcompanion.download.DownloadFileNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadFileNamesTest {
    @Test
    fun readsUtf8FilenameFromServerContentDisposition() {
        assertEquals(
            "新版安装包.apk",
            DownloadFileNames.resolve(
                "http://127.0.0.1:3765/api/artifacts/token/raw?download=1",
                "attachment; filename*=UTF-8''%E6%96%B0%E7%89%88%E5%AE%89%E8%A3%85%E5%8C%85.apk",
            ),
        )
    }

    @Test
    fun sanitizesPlainFilenameAndFallsBackForRawRoute() {
        assertEquals(
            "_unsafe_.apk",
            DownloadFileNames.resolve(
                "http://127.0.0.1:3765/api/artifacts/token/raw?download=1",
                "attachment; filename=../unsafe?.apk",
            ),
        )
        assertEquals(
            "Codex-文件",
            DownloadFileNames.resolve(
                "http://127.0.0.1:3765/api/artifacts/token/raw?download=1",
                "",
            ),
        )
    }

    @Test
    fun createsCollisionSafeStoredNameWithoutLosingExtension() {
        val stored = DownloadFileNames.storedName("CodexCompanion-debug.apk", 1_700_000_000_000L)
        assertTrue(stored.startsWith("CodexCompanion-debug-"))
        assertTrue(stored.endsWith(".apk"))
        assertTrue(stored.length <= 180)
    }
}
