package com.pilulesync.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object NotificationHelper {
    const val CHANNEL_REMIND = "rappels"
    const val CHANNEL_SYNC = "sync"

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMIND, "Rappels pilule", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Rappel de prise de la pilule" })
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SYNC, "Synchronisation", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Connexion de synchronisation entre les deux téléphones" })
    }

    fun show(context: Context, id: Int, title: String, text: String) {
        ensureChannels(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val pi = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = android.app.Notification.Builder(context, CHANNEL_REMIND)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 400, 200, 400))
            .build()
        nm.notify(id, notif)
    }

    fun syncNotification(context: Context): android.app.Notification {
        ensureChannels(context)
        return android.app.Notification.Builder(context, CHANNEL_SYNC)
            .setSmallIcon(android.R.drawable.ic_menu_rotate)
            .setContentTitle("PiluleSync connecté")
            .setContentText("Synchronisation avec l'autre téléphone active")
            .setOngoing(true)
            .build()
    }

    fun hasNotifPermission(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.areNotificationsEnabled()
    }
}
