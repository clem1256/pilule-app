package com.pilulesync.app

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import java.net.HttpURLConnection
import java.net.URL

class NtfyService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(2, NotificationHelper.syncNotification(this))
        Thread { streamLoop() }.start()
        return START_STICKY
    }

    private fun streamLoop() {
        var backoff = 5_000L
        while (Prefs.code(applicationContext).isNotBlank()) {
            try {
                val url = URL(Prefs.ntfyUrl(applicationContext) + "/json")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 0
                val reader = conn.inputStream.bufferedReader()
                var line = reader.readLine()
                while (line != null) {
                    handleLine(line)
                    line = reader.readLine()
                }
                conn.disconnect()
                backoff = 5_000L
            } catch (e: Exception) {
                // réseau indisponible : nouvelle tentative plus tard
            }
            try {
                Thread.sleep(backoff)
                backoff = (backoff * 2).coerceAtMost(60_000L)
            } catch (e: InterruptedException) {
                return
            }
        }
    }

    private fun handleLine(line: String) {
        val m = java.util.regex.Pattern
            .compile("\"event\"\\s*:\\s*\"message\".*\"message\"\\s*:\\s*\"(TAKEN\\|[^|]+\\|[0-9-]+)\"")
            .matcher(line)
        if (!m.find()) return
        val parts = m.group(1)!!.split("|")
        if (parts.size != 3 || parts[2] != Prefs.today()) return
        val by = parts[1]
        val me = Prefs.name(applicationContext)
        Prefs.resetIfNewDay(applicationContext)
        if (Prefs.takenToday(applicationContext) && Prefs.takenBy(applicationContext) == by) return
        Prefs.markTaken(applicationContext, by)
        Alarms.cancelReminder(applicationContext)
        if (by != me || Prefs.takenBy(applicationContext) != me) {
            NotificationHelper.show(
                applicationContext, 3,
                "💊 $by a pris la pilule",
                "Prise confirmée par $by aujourd'hui. Rappels arrêtés."
            )
        }
    }

    companion object {
        fun start(context: Context) {
            if (Prefs.code(context).isBlank()) return
            val intent = Intent(context, NtfyService::class.java)
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                try {
                    context.startService(intent)
                } catch (ignored: Exception) {
                }
            }
        }
    }
}
