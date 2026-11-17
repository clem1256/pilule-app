package com.pilulesync.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Prefs {
    private const val FILE = "pilule"
    private const val KEY_NAME = "name"
    private const val KEY_CODE = "code"
    private const val KEY_TAKEN_DATE = "taken_date"
    private const val KEY_TAKEN_BY = "taken_by"

    fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun name(context: Context) = prefs(context).getString(KEY_NAME, "") ?: ""
    fun code(context: Context) = prefs(context).getString(KEY_CODE, "") ?: ""

    fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun takenToday(context: Context): Boolean =
        prefs(context).getString(KEY_TAKEN_DATE, "") == today()

    fun takenBy(context: Context) = prefs(context).getString(KEY_TAKEN_BY, "") ?: ""

    fun markTaken(context: Context, by: String) {
        prefs(context).edit()
            .putString(KEY_TAKEN_DATE, today())
            .putString(KEY_TAKEN_BY, by)
            .apply()
    }

    fun resetIfNewDay(context: Context) {
        if (prefs(context).contains(KEY_TAKEN_DATE) && !takenToday(context)) {
            prefs(context).edit().remove(KEY_TAKEN_DATE).remove(KEY_TAKEN_BY).apply()
        }
    }

    fun topic(context: Context): String = "pilule-" + code(context).trim().lowercase()

    fun ntfyUrl(context: Context) = "https://ntfy.sh/" + topic(context)
}

object Alarms {
    private const val REQ_EVENING = 1001
    private const val REQ_REMINDER = 1002

    private fun alarmManager(context: Context) =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun pending(context: Context, cls: Class<*>, req: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, req,
            Intent(context, cls),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun canExact(context: Context): Boolean {
        val am = alarmManager(context)
        return android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
    }

    private fun set(context: Context, pi: PendingIntent, at: Long) {
        val am = alarmManager(context)
        if (canExact(context)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun scheduleEvening(context: Context) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 21)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        set(context, pending(context, AlarmReceiver::class.java, REQ_EVENING), cal.timeInMillis)
    }

    fun scheduleReminder(context: Context, inMinutes: Long = 30) {
        cancelReminder(context)
        val at = System.currentTimeMillis() + inMinutes * 60_000L
        set(context, pending(context, AlarmReceiver::class.java, REQ_REMINDER), at)
    }

    fun cancelReminder(context: Context) {
        alarmManager(context).cancel(pending(context, AlarmReceiver::class.java, REQ_REMINDER))
    }

    fun scheduleAll(context: Context) {
        scheduleEvening(context)
        if (!Prefs.takenToday(context)) cancelReminder(context)
    }
}

object Ntfy {
    fun publish(context: Context, message: String): String? {
        return try {
            val conn = java.net.URL(Prefs.ntfyUrl(context)).openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.setDoOutput(true)
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("Title", "PiluleSync")
            conn.outputStream.use { it.write(message.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            conn.disconnect()
            if (code in 200..299) null else "HTTP $code"
        } catch (e: Exception) {
            e.message ?: "erreur réseau"
        }
    }

    fun pollTakenToday(context: Context): Boolean {
        val code = Prefs.code(context)
        if (code.isBlank()) return false
        return try {
            val url = java.net.URL(Prefs.ntfyUrl(context) + "/json?poll=1")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val lines = conn.inputStream.bufferedReader().readLines()
            conn.disconnect()
            val today = Prefs.today()
            var found: String? = null
            for (line in lines) {
                val m = java.util.regex.Pattern
                    .compile("\"message\"\\s*:\\s*\"(TAKEN\\|[^|]+\\|[0-9-]+)\"")
                    .matcher(line)
                if (m.find()) found = m.group(1)
            }
            if (found != null) {
                val parts = found.split("|")
                if (parts.size == 3 && parts[2] == today) {
                    Prefs.markTaken(context, parts[1])
                    true
                } else false
            } else false
        } catch (e: Exception) {
            false
        }
    }
}
