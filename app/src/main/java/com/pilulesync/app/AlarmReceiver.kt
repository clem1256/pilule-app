package com.pilulesync.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Prefs.resetIfNewDay(context)
        NtfyService.start(context)
        Alarms.scheduleEvening(context)

        val pending = goAsync()
        Thread {
            try {
                val already = Prefs.takenToday(context) || Ntfy.pollTakenToday(context)
                if (already) {
                    Alarms.cancelReminder(context)
                } else {
                    NotificationHelper.show(
                        context, 1,
                        "💊 Rappel pilule",
                        "Il est temps de prendre la pilule ! Appuyez dans l'app pour confirmer."
                    )
                    Alarms.scheduleReminder(context, 30)
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}
