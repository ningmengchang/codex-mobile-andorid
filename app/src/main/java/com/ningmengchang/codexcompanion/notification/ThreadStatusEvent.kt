package com.ningmengchang.codexcompanion.notification

data class ThreadStatusEvent(
    val threadId: String,
    val threadName: String,
    val status: String,
    val statusLabel: String,
    val shouldAlert: Boolean,
) {
    companion object {
        private val statusLabels = mapOf(
            "planning" to "规划中",
            "running" to "执行中",
            "waiting" to "待处理",
            "completed" to "已完成",
            "failed" to "失败",
            "interrupted" to "已停止",
        )

        fun create(threadId: String?, threadName: String?, status: String?): ThreadStatusEvent? {
            val normalizedId = threadId.orEmpty().trim().take(200)
            val normalizedStatus = status.orEmpty().trim().lowercase()
            val statusLabel = statusLabels[normalizedStatus] ?: return null
            if (normalizedId.isBlank()) return null
            return ThreadStatusEvent(
                threadId = normalizedId,
                threadName = threadName.orEmpty().trim().take(120).ifBlank { "Codex 会话" },
                status = normalizedStatus,
                statusLabel = statusLabel,
                shouldAlert = normalizedStatus in setOf("waiting", "completed", "failed", "interrupted"),
            )
        }
    }
}
