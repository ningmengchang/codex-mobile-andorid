package com.ningmengchang.codexcompanion

import com.ningmengchang.codexcompanion.web.ImageChooserPolicy
import com.ningmengchang.codexcompanion.web.RequestedImageSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageChooserPolicyTest {
    @Test
    fun routesCapturedImageInputsToTheCamera() {
        assertTrue(ImageChooserPolicy.shouldOpenCamera(null, true, arrayOf("image/*")))
        assertTrue(ImageChooserPolicy.shouldOpenCamera(null, true, arrayOf("image/jpeg, image/png")))
        assertTrue(ImageChooserPolicy.shouldOpenCamera(RequestedImageSource.CAMERA, false, arrayOf("image/*")))
    }

    @Test
    fun keepsGalleryAndGenericFilesInTheSystemPicker() {
        assertFalse(ImageChooserPolicy.shouldOpenCamera(null, false, arrayOf("image/*")))
        assertFalse(ImageChooserPolicy.shouldOpenCamera(RequestedImageSource.CAMERA, false, arrayOf("application/pdf")))
        assertFalse(ImageChooserPolicy.shouldOpenCamera(RequestedImageSource.CAMERA, true, arrayOf("image/*", "application/pdf")))
        assertFalse(ImageChooserPolicy.shouldOpenCamera(RequestedImageSource.GALLERY, true, arrayOf("image/*")))
    }

    @Test
    fun routesExplicitGalleryRequestsToTheNativePhotoPicker() {
        assertTrue(ImageChooserPolicy.shouldOpenGalleryPicker(RequestedImageSource.GALLERY, arrayOf("image/*")))
        assertTrue(ImageChooserPolicy.shouldOpenGalleryPicker(RequestedImageSource.GALLERY, emptyArray()))
        assertFalse(ImageChooserPolicy.shouldOpenGalleryPicker(null, arrayOf("image/*")))
        assertFalse(ImageChooserPolicy.shouldOpenGalleryPicker(RequestedImageSource.CAMERA, arrayOf("image/*")))
        assertFalse(ImageChooserPolicy.shouldOpenGalleryPicker(RequestedImageSource.GALLERY, arrayOf("application/pdf")))
    }
}
