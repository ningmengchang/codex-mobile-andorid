package com.ningmengchang.codexcompanion.web

enum class RequestedImageSource { CAMERA, GALLERY }

object ImageChooserPolicy {
    fun shouldOpenCamera(
        requestedSource: RequestedImageSource?,
        captureEnabled: Boolean,
        acceptTypes: Array<String>?,
    ): Boolean {
        if (requestedSource == RequestedImageSource.GALLERY) return false
        if (requestedSource != RequestedImageSource.CAMERA && !captureEnabled) return false
        val types = normalizedAcceptTypes(acceptTypes)
        return types.isEmpty() || types.all { it == "image/*" || it.startsWith("image/") }
    }

    fun shouldOpenGalleryPicker(
        requestedSource: RequestedImageSource?,
        acceptTypes: Array<String>?,
    ): Boolean {
        if (requestedSource != RequestedImageSource.GALLERY) return false
        val types = normalizedAcceptTypes(acceptTypes)
        return types.isEmpty() || types.all { it == "image/*" || it.startsWith("image/") }
    }

    private fun normalizedAcceptTypes(acceptTypes: Array<String>?): List<String> = acceptTypes.orEmpty()
        .flatMap { it.split(',') }
        .map { it.substringBefore(';').trim().lowercase() }
        .filter { it.isNotEmpty() }
}
