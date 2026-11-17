package com.pilulesync.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Prefs.resetIfNewDay(context)
            if (Prefs.name(context).isNotBlank() && Prefs.code(context).isNotBlank()) {
                Alarms.scheduleEvening(context)
                NtfyService.start(context)
            }
        }
    }
}
