package com.ningmengchang.codexcompanion.navigation

enum class BackAction {
    CANCEL_SHARE,
    RETURN_TO_WEB,
    DELEGATE_TO_WEB,
    CONFIRM_BACKGROUND,
}

data class BackNavigationSnapshot(
    val shareInProgress: Boolean,
    val setupVisible: Boolean,
    val webVisible: Boolean,
    val tunnelConnected: Boolean,
)

object BackNavigationPolicy {
    fun decide(snapshot: BackNavigationSnapshot): BackAction = when {
        snapshot.shareInProgress -> BackAction.CANCEL_SHARE
        snapshot.setupVisible && snapshot.tunnelConnected -> BackAction.RETURN_TO_WEB
        snapshot.webVisible -> BackAction.DELEGATE_TO_WEB
        else -> BackAction.CONFIRM_BACKGROUND
    }

    fun javascriptConsumed(rawResult: String?): Boolean =
        rawResult?.trim()?.trim('"')?.equals("true", ignoreCase = true) == true
}
