package com.ningmengchang.codexcompanion.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ningmengchang.codexcompanion.MainActivity
import com.ningmengchang.codexcompanion.R

class ThreadStatusNotifier(context: Context) {
    private val appContext = context.applicationContext
    private val notificationManager = appContext.getSystemService(NotificationManager::class.java)

    init {
        val channel = NotificationChannel(
            CHANNEL_ID,
            appContext.getString(R.string.thread_status_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = appContext.getString(R.string.thread_status_channel_description)
            setShowBadge(true)
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun notify(event: ThreadStatusEvent) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val notificationId = notificationId(event.threadId)
        val openIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(event.threadName)
            .setContentText("会话状态：${event.statusLabel}")
            .setSubText(appContext.getString(R.string.app_name))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setGroup(GROUP_KEY)
            .setPriority(if (event.shouldAlert) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
            .setSilent(!event.shouldAlert)
            .build()
        notificationManager.notify(notificationId, notification)
    }

    private fun notificationId(threadId: String): Int =
        NOTIFICATION_ID_NAMESPACE or (threadId.hashCode() and NOTIFICATION_ID_MASK)

    private companion object {
        const val CHANNEL_ID = "codex_thread_status"
        const val GROUP_KEY = "codex_thread_status_group"
        const val NOTIFICATION_ID_NAMESPACE = 0x40000000
        const val NOTIFICATION_ID_MASK = 0x3fffffff
    }
}
