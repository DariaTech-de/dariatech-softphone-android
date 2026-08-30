package de.dariatech.softphone

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.GridLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import de.dariatech.softphone.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.linphone.core.Call
import org.linphone.core.RegistrationState
import org.linphone.core.TransportType

/**
 * Hauptbildschirm: Wähltastatur, Anrufliste und SIP-Konto (mit Anbieter-
 * Vorlagen wie in der Desktop-App) plus Vollbild-Anrufansicht mit
 * Stumm/Lautsprecher/Halten/DTMF.
 */
class MainActivity : AppCompatActivity(), LinphoneManager.Listener {

    private lateinit var binding: ActivityMainBinding
    private val handler = Handler(Looper.getMainLooper())
    private var durationTimer: Runnable? = null
    private var dtmfVisible = false

    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /**
     * Die Kamera wird ERST GEFRAGT, wenn jemand Video einschaltet.
     *
     * Nicht beim Start zusammen mit dem Mikrofon: Wer eine Telefon-App
     * öffnet und sofort nach der Kamera gefragt wird, lehnt ab – und
     * dann geht Video auch später nicht mehr ohne Umweg über die
     * Android-Einstellungen. Gefragt wird im Moment des Bedarfs, weil
     * dann klar ist, wofür.
     */
    private var videoNachFreigabe = false
    private val kameraFreigabe =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { erlaubt ->
            if (erlaubt && videoNachFreigabe) {
                videoNachFreigabe = false
                videoEinschalten()
            } else if (!erlaubt) {
                videoNachFreigabe = false
                binding.callState.text = getString(R.string.video_no_permission)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        micPermission.launch(Manifest.permission.RECORD_AUDIO)

        // Liblinphone zeichnet selbst auf diese beiden Flächen. Ohne sie
        // bliebe das Bild aus, obwohl der Strom läuft.
        LinphoneManager.videoFlaechen(binding.videoFremd, binding.videoEigen)

        setupKeypad()
        setupNavigation()
        setupSettings()
        setupCallControls()

        LinphoneManager.listener = this
        showCallUi(inCall = false, ringing = false)
        setzeKuerzel()
        // Der Verlauf ist der Einstieg: Wer die App öffnet, will
        // meistens zurückrufen. Das Wählfeld ist einen Tipp entfernt.
        binding.leiste.selectedItemId = R.id.leiste_anrufe
        showTab(Tab.HISTORY)

        // Gespeicherte Zugangsdaten laden und automatisch anmelden
        val prefs = prefs()
        if (!prefs.getString("username", "").isNullOrEmpty()) {
            connect()
        } else {
            showTab(Tab.SETTINGS) // Erststart: direkt zum Konto
        }
    }

    override fun onDestroy() {
        // Die Flächen gehören zu DIESER Activity. Bleibt der Verweis im
        // Core stehen, zeichnet Liblinphone beim nächsten Anruf auf eine
        // zerstörte View – und das ist ein Absturz, kein leeres Bild.
        LinphoneManager.videoFlaechen(null, null)
        super.onDestroy()
    }

    private fun prefs() = getSharedPreferences("sip", Context.MODE_PRIVATE)

    // ---------- Tabs ----------

    private enum class Tab { DIALPAD, HISTORY, SETTINGS }

    /**
     * Die Leiste am unteren Rand statt dreier Textknöpfe.
     *
     * DER AUFTRAG des Inhabers vom 30.08.2026: die Apps optisch auf
     * höchstes Niveau zu bringen. Drei Wörter nebeneinander waren
     * bedienbar und sahen selbstgebaut aus.
     *
     * EINSTELLUNGEN STEHEN NICHT IN DER LEISTE. Man öffnet sie einmal
     * beim Einrichten und danach selten; sie hängen am Profilbild oben,
     * wie überall. Dafür ist unten Platz für das, was täglich gebraucht
     * wird.
     */
    private fun setupNavigation() {
        binding.leiste.setOnItemSelectedListener { punkt ->
            when (punkt.itemId) {
                R.id.leiste_anrufe -> showTab(Tab.HISTORY)
                R.id.leiste_tastenfeld -> showTab(Tab.DIALPAD)
                // Kontakte und Nachrichten holt die App noch nicht von
                // der Anlage. Bis dahin führen beide auf den Verlauf,
                // statt einen leeren Bildschirm zu zeigen, den niemand
                // erklärt hat.
                else -> showTab(Tab.HISTORY)
            }
            true
        }
        binding.profilKnopf.setOnClickListener { showTab(Tab.SETTINGS) }
    }

    private fun showTab(tab: Tab) {
        binding.viewDialpad.visibility = if (tab == Tab.DIALPAD) View.VISIBLE else View.GONE
        binding.viewHistory.visibility = if (tab == Tab.HISTORY) View.VISIBLE else View.GONE
        binding.viewSettings.visibility = if (tab == Tab.SETTINGS) View.VISIBLE else View.GONE
        if (tab == Tab.HISTORY) refreshHistory()
    }

    /**
     * Das Namenskürzel im Profilknopf.
     *
     * Aus dem SIP-Benutzernamen: „ahmadsaber.temori" wird „AT". Ein
     * leeres Feld bekommt einen Punkt statt zwei Leerzeichen – ein
     * Kreis, in dem nichts steht, sieht nach einem Ladefehler aus.
     */
    private fun setzeKuerzel() {
        val name = prefs().getString("username", "") ?: ""
        val teile = name.split(".", "_", "-", " ").filter { it.isNotBlank() }
        val kuerzel = when {
            teile.size >= 2 -> "${teile[0].first()}${teile[1].first()}"
            teile.size == 1 -> teile[0].take(2)
            else -> "·"
        }
        binding.profilKuerzel.text = kuerzel.uppercase()
    }

    // ---------- Wähltastatur ----------

    private fun setupKeypad() {
        binding.number.showSoftInputOnFocus = false
        buildKeypad(binding.keypadGrid, large = true, textColor = 0) { digit ->
            binding.number.append(digit.toString())
        }
        binding.backspaceButton.setOnClickListener {
            val t = binding.number.text
            if (t.isNotEmpty()) binding.number.setText(t.substring(0, t.length - 1))
            binding.number.setSelection(binding.number.text.length)
        }
        binding.backspaceButton.setOnLongClickListener {
            binding.number.setText("")
            true
        }
        binding.callButton.setOnClickListener {
            val number = binding.number.text.toString().trim()
            if (number.isNotEmpty()) LinphoneManager.call(number)
        }
    }

    /** Die Buchstaben unter den Ziffern – in der Reihenfolge der Tasten. */
    private val tastenBuchstaben = mapOf(
        '1' to R.string.tasten_1, '2' to R.string.tasten_2, '3' to R.string.tasten_3,
        '4' to R.string.tasten_4, '5' to R.string.tasten_5, '6' to R.string.tasten_6,
        '7' to R.string.tasten_7, '8' to R.string.tasten_8, '9' to R.string.tasten_9,
        '*' to R.string.tasten_stern, '0' to R.string.tasten_0, '#' to R.string.tasten_raute
    )

    /**
     * Baut ein 3×4-Tastenfeld (1-9, *, 0, #) in das GridLayout.
     *
     * JEDE TASTE KOMMT AUS taste.xml und wird nicht mehr im Code
     * zusammengesetzt. Vorher war es ein nackter `Button` mit einer
     * Ziffer – funktionsfähig und erkennbar selbstgebaut. Jetzt trägt
     * sie ihre Buchstaben, wie jedes Telefon der Welt, und ihre
     * Gestaltung steht an einer Stelle.
     *
     * Die kleine Fassung (DTMF im Gespräch) zeigt KEINE Buchstaben: Wer
     * mitten im Gespräch eine Ziffer für ein Sprachmenü tippt, sucht
     * nicht nach Buchstaben, und die Fläche ist knapp.
     */
    private fun buildKeypad(grid: GridLayout, large: Boolean, textColor: Int, onKey: (Char) -> Unit) {
        grid.removeAllViews()
        val kante = (resources.displayMetrics.density * (if (large) 72 else 56)).toInt()
        for (key in "123456789*0#") {
            val taste = layoutInflater.inflate(R.layout.taste, grid, false)
            val ziffer = taste.findViewById<android.widget.TextView>(R.id.tasteZiffer)
            val buchstaben = taste.findViewById<android.widget.TextView>(R.id.tasteBuchstaben)
            ziffer.text = key.toString()
            ziffer.textSize = if (large) 28f else 20f
            val text = tastenBuchstaben[key]?.let { getString(it) } ?: ""
            buchstaben.text = text
            // Leere Buchstabenzeile GONE und nicht INVISIBLE: Sonst
            // sitzt die Ziffer bei 1, * und # höher als bei den anderen,
            // und ein Wählfeld, dessen Ziffern nicht auf einer Linie
            // stehen, sieht unfertig aus.
            buchstaben.visibility = if (large && text.isNotEmpty()) View.VISIBLE else View.GONE
            if (textColor != 0) ziffer.setTextColor(resources.getColor(textColor, theme))
            val lp = GridLayout.LayoutParams()
            lp.width = kante
            lp.height = kante
            taste.layoutParams = lp
            taste.setOnClickListener { onKey(key) }
            grid.addView(taste)
        }
    }

    // ---------- Anrufliste ----------

    private fun refreshHistory() {
        val entries = CallLogStore.list(this)
        val missed = CallLogStore.missedToday(this)
        binding.missedInfo.text = getString(R.string.missed_today, missed)
        binding.missedInfo.visibility = if (missed > 0) View.VISIBLE else View.GONE
        binding.historyEmpty.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE

        val fmt = SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY)
        val rows = entries.map { e ->
            val dir = when (e.direction) {
                "missed" -> "✗ ${getString(R.string.dir_missed)}"
                "in" -> "↓ ${getString(R.string.dir_in)}"
                else -> "↑ ${getString(R.string.dir_out)}"
            }
            val duration = if (e.durationSec > 0) " · ${e.durationSec / 60}:%02d".format(e.durationSec % 60) else ""
            mapOf("line1" to e.number, "line2" to "$dir · ${fmt.format(Date(e.at))}$duration")
        }
        binding.historyList.adapter = android.widget.SimpleAdapter(
            this,
            rows,
            android.R.layout.simple_list_item_2,
            arrayOf("line1", "line2"),
            intArrayOf(android.R.id.text1, android.R.id.text2)
        )
        binding.historyList.setOnItemClickListener { _, _, position, _ ->
            // Antippen = Nummer in die Wähltastatur übernehmen (Rückruf)
            binding.number.setText(entries[position].number)
            binding.number.setSelection(binding.number.text.length)
            showTab(Tab.DIALPAD)
        }
    }

    // ---------- Einstellungen (SIP-Konto) ----------

    private fun setupSettings() {
        binding.transport.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, listOf("UDP", "TCP", "TLS"))
        )
        binding.preset.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, PROVIDER_PRESETS.map { it.label })
        )
        binding.preset.setOnItemClickListener { _, _, position, _ ->
            val preset = PROVIDER_PRESETS[position]
            if (preset.domain.isNotEmpty()) binding.domain.setText(preset.domain)
            binding.transport.setText(preset.transport, false)
            binding.presetHint.text = preset.hint
            binding.presetHint.visibility = View.VISIBLE
        }

        val prefs = prefs()
        binding.username.setText(prefs.getString("username", ""))
        // Das Passwort kommt aus dem VERSCHLÜSSELTEN Speicher – siehe
        // Zugangsspeicher. Beim ersten Lesen zieht ein Altbestand aus der
        // Klartext-Ablage automatisch mit um.
        binding.password.setText(Zugangsspeicher.passwort(this))
        binding.domain.setText(prefs.getString("domain", "pbx.dariatech.de"))
        binding.transport.setText(prefs.getString("transport", "UDP"), false)
        binding.preset.setText(prefs.getString("preset", ""), false)

        binding.connectButton.setOnClickListener {
            prefs.edit()
                .putString("username", binding.username.text.toString().trim())
                .putString("domain", binding.domain.text.toString().trim())
                .putString("transport", binding.transport.text.toString())
                .putString("preset", binding.preset.text.toString())
                .apply()
            Zugangsspeicher.setzePasswort(this, binding.password.text.toString())
            connect()
            setzeKuerzel()
            binding.leiste.selectedItemId = R.id.leiste_tastenfeld
            showTab(Tab.DIALPAD)
        }
    }

    private fun connect() {
        val transport = when (prefs().getString("transport", "UDP")) {
            "TCP" -> TransportType.Tcp
            "TLS" -> TransportType.Tls
            else -> TransportType.Udp
        }
        LinphoneManager.login(
            prefs().getString("username", "") ?: "",
            Zugangsspeicher.passwort(this),
            prefs().getString("domain", "") ?: "",
            transport
        )
        binding.status.text = getString(R.string.connecting)
    }

    // ---------- Anruf-Vollbild ----------

    private fun setupCallControls() {
        buildKeypad(binding.dtmfPad, large = false, textColor = R.color.gespraech_text) { digit ->
            LinphoneManager.sendDtmf(digit)
        }
        binding.hangupButton.setOnClickListener { LinphoneManager.hangup() }
        binding.answerButton.setOnClickListener { LinphoneManager.answer() }
        binding.declineButton.setOnClickListener { LinphoneManager.hangup() }
        binding.muteButton.setOnClickListener {
            val muted = LinphoneManager.toggleMute()
            binding.muteButton.text = getString(if (muted) R.string.unmute else R.string.mute)
        }
        binding.speakerButton.setOnClickListener {
            val speaker = LinphoneManager.toggleSpeaker()
            binding.speakerButton.text =
                getString(if (speaker) R.string.earpiece else R.string.speaker)
        }
        binding.holdButton.setOnClickListener {
            val held = LinphoneManager.toggleHold()
            binding.holdButton.text = getString(if (held) R.string.resume else R.string.hold)
            binding.callState.text = getString(if (held) R.string.on_hold else R.string.in_call)
        }
        binding.dtmfButton.setOnClickListener {
            dtmfVisible = !dtmfVisible
            binding.dtmfPad.visibility = if (dtmfVisible) View.VISIBLE else View.GONE
        }
        binding.videoButton.setOnClickListener {
            if (LinphoneManager.videoLaeuft()) {
                LinphoneManager.videoUmschalten()
                zeigeVideo()
                return@setOnClickListener
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
            ) {
                videoEinschalten()
            } else {
                videoNachFreigabe = true
                kameraFreigabe.launch(Manifest.permission.CAMERA)
            }
        }
        binding.kameraButton.setOnClickListener { LinphoneManager.kameraWechseln() }
    }

    private fun videoEinschalten() {
        LinphoneManager.videoUmschalten()
        zeigeVideo()
    }

    /**
     * Die Videoflächen zeigen oder verstecken.
     *
     * Ein schwarzes Rechteck in einem Telefonat ohne Bild sieht aus wie
     * ein Fehler – deshalb ist der ganze Bereich weg, solange kein Video
     * läuft. Aufgerufen wird das bei jedem Zustandswechsel des Anrufs,
     * nicht nur beim Drücken: Auch die GEGENSTELLE kann Bild dazu- oder
     * abschalten.
     */
    private fun zeigeVideo() {
        val an = LinphoneManager.videoLaeuft()
        binding.videoBereich.visibility = if (an) View.VISIBLE else View.GONE
        binding.kameraButton.visibility = if (an) View.VISIBLE else View.GONE
        binding.videoButton.text =
            getString(if (an) R.string.video_stop else R.string.video_start)
    }

    private fun startDurationTimer() {
        stopDurationTimer()
        val tick = object : Runnable {
            override fun run() {
                val s = LinphoneManager.currentCallDuration()
                binding.callDuration.text = "%d:%02d".format(s / 60, s % 60)
                handler.postDelayed(this, 1000)
            }
        }
        durationTimer = tick
        handler.post(tick)
    }

    private fun stopDurationTimer() {
        durationTimer?.let { handler.removeCallbacks(it) }
        durationTimer = null
        binding.callDuration.text = ""
    }

    // ---------- Ereignisse aus dem SIP-Stack ----------

    override fun onRegistration(state: RegistrationState?, message: String) {
        runOnUiThread {
            when (state) {
                RegistrationState.Ok -> {
                    binding.status.text = getString(R.string.connected)
                    binding.statusDot.setBackgroundResource(R.drawable.dot_green)
                }
                RegistrationState.Progress -> binding.status.text = getString(R.string.connecting)
                RegistrationState.Failed -> {
                    binding.status.text = getString(R.string.reg_failed, message)
                    binding.statusDot.setBackgroundResource(R.drawable.dot_red)
                }
                else -> binding.status.text = message
            }
        }
    }

    override fun onCallState(call: Call, state: Call.State?, message: String) {
        runOnUiThread {
            val who = call.remoteAddress.displayName ?: call.remoteAddress.username
                ?: call.remoteAddress.asStringUriOnly()
            when (state) {
                Call.State.IncomingReceived, Call.State.IncomingEarlyMedia -> {
                    binding.callerInfo.text = who
                    binding.callState.text = getString(R.string.incoming_call)
                    showCallUi(inCall = false, ringing = true)
                }
                Call.State.OutgoingInit, Call.State.OutgoingProgress, Call.State.OutgoingRinging -> {
                    binding.callerInfo.text = who
                    binding.callState.text = getString(R.string.outgoing_call)
                    showCallUi(inCall = true, ringing = false)
                }
                Call.State.Connected, Call.State.StreamsRunning -> {
                    binding.callState.text =
                        if (LinphoneManager.gegenstelleMitVideo() && !LinphoneManager.videoLaeuft())
                            getString(R.string.video_incoming)
                        else getString(R.string.in_call)
                    showCallUi(inCall = true, ringing = false)
                    startDurationTimer()
                    // Auch die GEGENSTELLE kann Bild dazu- oder abschalten:
                    // Jeder Zustandswechsel richtet die Anzeige nach, statt
                    // sie nur beim eigenen Drücken zu setzen.
                    zeigeVideo()
                }
                Call.State.UpdatedByRemote -> zeigeVideo()
                Call.State.End, Call.State.Released, Call.State.Error -> {
                    showCallUi(inCall = false, ringing = false)
                    stopDurationTimer()
                    refreshHistory()
                }
                else -> Unit
            }
        }
    }

    private fun showCallUi(inCall: Boolean, ringing: Boolean) {
        binding.callOverlay.visibility = if (inCall || ringing) View.VISIBLE else View.GONE
        binding.incomingControls.visibility = if (ringing) View.VISIBLE else View.GONE
        binding.activeControls.visibility = if (inCall) View.VISIBLE else View.GONE
        binding.videoControls.visibility = if (inCall) View.VISIBLE else View.GONE
        binding.hangupButton.visibility = if (inCall) View.VISIBLE else View.GONE
        if (!inCall) {
            dtmfVisible = false
            binding.dtmfPad.visibility = View.GONE
            binding.muteButton.text = getString(R.string.mute)
            binding.speakerButton.text = getString(R.string.speaker)
            binding.holdButton.text = getString(R.string.hold)
            // Das Bild endet mit dem Gespräch. Bliebe die Fläche stehen,
            // sähe man nach dem Auflegen das letzte Standbild.
            binding.videoBereich.visibility = View.GONE
            binding.kameraButton.visibility = View.GONE
            binding.videoButton.text = getString(R.string.video_start)
        }
    }
}
