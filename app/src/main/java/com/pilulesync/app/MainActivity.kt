package com.pilulesync.app

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var nameEdit: EditText
    private lateinit var codeEdit: EditText
    private lateinit var statusView: TextView
    private lateinit var takeButton: Button
    private lateinit var infoView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper.ensureChannels(this)
        buildUi()
        askPermissions()
        Prefs.resetIfNewDay(this)
    }

    override fun onResume() {
        super.onResume()
        Prefs.resetIfNewDay(this)
        loadPrefs()
        refreshStatus()
    }

    private fun buildUi() {
        val pad = (resources.displayMetrics.density * 20).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val title = TextView(this).apply {
            text = "💊 PiluleSync"
            textSize = 26f
            gravity = Gravity.CENTER
        }
        root.addView(title)

        val subtitle = TextView(this).apply {
            text = "Rappel quotidien à 21h30, synchronisé entre deux téléphones."
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, pad / 2, 0, pad)
        }
        root.addView(subtitle)

        val nameLabel = TextView(this).apply { text = "Votre prénom :" }
        root.addView(nameLabel)
        nameEdit = EditText(this).apply {
            hint = "ex : Sofia"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        root.addView(nameEdit)

        val codeLabel = TextView(this).apply {
            text = "Code de salon (identique sur les 2 téléphones) :"
            setPadding(0, pad / 2, 0, 0)
        }
        root.addView(codeLabel)
        codeEdit = EditText(this).apply {
            hint = "ex : couple2025"
        }
        root.addView(codeEdit)

        val saveButton = Button(this).apply {
            text = "Enregistrer et activer"
            setOnClickListener { save() }
        }
        root.addView(saveButton)

        statusView = TextView(this).apply {
            textSize = 16f
            setPadding(0, pad, 0, pad / 2)
            gravity = Gravity.CENTER
        }
        root.addView(statusView)

        takeButton = Button(this).apply {
            text = "J'ai pris ma pilule ✅"
            setOnClickListener { markTaken() }
        }
        root.addView(takeButton)

        infoView = TextView(this).apply {
            textSize = 12f
            setPadding(0, pad / 2, 0, 0)
            gravity = Gravity.CENTER
        }
        root.addView(infoView)

        setContentView(android.widget.ScrollView(this).apply { addView(root) })
    }

    private fun loadPrefs() {
        nameEdit.setText(Prefs.name(this))
        codeEdit.setText(Prefs.code(this))
    }

    private fun askPermissions() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        if (Build.VERSION.SDK_INT >= 31) {
            val am = getSystemService(android.app.AlarmManager::class.java)
            if (!am.canScheduleExactAlarms()) {
                Toast.makeText(
                    this,
                    "Autorisez « Alarmes et rappels » pour un rappel exact à 21h30.",
                    Toast.LENGTH_LONG
                ).show()
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                } catch (e: Exception) {
                }
            }
        }
    }

    private fun save() {
        val name = nameEdit.text.toString().trim()
        val code = codeEdit.text.toString().trim()
        if (name.isEmpty() || code.length < 4) {
            Toast.makeText(
                this,
                "Prénom requis et code d'au moins 4 caractères.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        Prefs.prefs(this).edit()
            .putString("name", name)
            .putString("code", code)
            .apply()
        Alarms.scheduleEvening(this)
        NtfyService.start(this)
        Toast.makeText(this, "Activé : rappel chaque jour à 21h30.", Toast.LENGTH_LONG).show()
        refreshStatus()
    }

    private fun markTaken() {
        val name = Prefs.name(this)
        val code = Prefs.code(this)
        if (name.isBlank() || code.isBlank()) {
            Toast.makeText(this, "Renseignez prénom et code, puis enregistrez.", Toast.LENGTH_LONG).show()
            return
        }
        Thread {
            val err = Ntfy.publish(this, "TAKEN|$name|${Prefs.today()}")
            runOnUiThread {
                if (err == null) {
                    Prefs.markTaken(this, name)
                    Alarms.cancelReminder(this)
                    NotificationHelper.show(
                        this, 3,
                        "💊 Pilule prise ✅",
                        "Confirmation envoyée à l'autre téléphone. Rappels arrêtés."
                    )
                } else {
                    Prefs.markTaken(this, name)
                    Alarms.cancelReminder(this)
                    Toast.makeText(
                        this,
                        "Marquée prise localement, mais l'envoi a échoué ($err). Ouvrez l'app pour resynchroniser.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                refreshStatus()
            }
        }.start()
    }

    private fun refreshStatus() {
        Prefs.resetIfNewDay(this)
        val name = Prefs.name(this)
        val code = Prefs.code(this)
        if (name.isBlank() || code.isBlank()) {
            statusView.text = "Non configuré."
        } else if (Prefs.takenToday(this)) {
            statusView.text = "✅ Pilule déjà prise aujourd'hui par ${Prefs.takenBy(this)}."
        } else {
            statusView.text = "⏳ Pilule pas encore prise aujourd'hui.\nProchain rappel à 21h30, puis toutes les 30 min."
        }
        takeButton.isEnabled = !Prefs.takenToday(this) && name.isNotBlank()
        infoView.text = buildString {
            append("Salon : pilule-$code (identique sur les 2 téléphones).\n")
            append("À 21h30 : rappel sur les deux téléphones. Tant que personne ne confirme, ")
            append("rappel toutes les 30 minutes sur les deux. Dès qu'une personne confirme, ")
            append("l'autre reçoit immédiatement « a pris la pilule » et les rappels s'arrêtent.")
        }
    }
}
