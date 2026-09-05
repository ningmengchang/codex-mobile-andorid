package com.ningmengchang.codexcompanion

import com.ningmengchang.codexcompanion.web.NativeUiBridge
import org.junit.Assert.assertEquals
import org.junit.Test

class NativeUiBridgeTest {
    @Test
    fun forwardsConnectionSettingsRequestToNativeListener() {
        var settingsRequests = 0
        var cameraRequests = 0
        var galleryRequests = 0
        val bridge = NativeUiBridge(
            onOpenConnectionSettings = { settingsRequests += 1 },
            onCameraCaptureRequested = { cameraRequests += 1 },
            onGalleryPickerRequested = { galleryRequests += 1 },
        )

        bridge.openConnectionSettings()
        bridge.requestCameraCapture()
        bridge.requestGalleryPicker()

        assertEquals(1, settingsRequests)
        assertEquals(1, cameraRequests)
        assertEquals(1, galleryRequests)
    }
}
