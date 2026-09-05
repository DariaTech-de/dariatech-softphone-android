package de.dariatech.softphone

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
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
    /**
     * Die Kennung des Menschen am anderen Ende, wenn es ein Kollege ist –
     * daran hängt sein Bild. Leer heißt: kein Gesicht, aber ein Name
     * oder eine Nummer.
     */
    private var gegenueberId: String? = null

    private val meldeRecht =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

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
        Anrufmeldung.kanaeleAnlegen(this)
        // SEIT ANDROID 13 MUSS MAN AUCH DAS FRAGEN. Das Recht stand
        // schon im Manifest, gefragt wurde nie – und ohne die Frage
        // zeigt Android schlicht keine Meldung an. Ein eingehender Anruf
        // blieb damit auf dem Sperrbildschirm unsichtbar, ohne dass
        // irgendwo ein Fehler stand.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            meldeRecht.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        behandleAbsicht(intent)

        // Liblinphone zeichnet selbst auf diese beiden Flächen. Ohne sie
        // bliebe das Bild aus, obwohl der Strom läuft.
        LinphoneManager.videoFlaechen(binding.videoFremd, binding.videoEigen)

        setupKeypad()
        setupNavigation()
        setupSettings()
        setupCallControls()
        setupLeereBereiche()

        LinphoneManager.listener = this
        showCallUi(inCall = false, ringing = false)
        zeigeProfil()
        /* DAS VERZEICHNIS KOMMT NACH DER TELEFONIE, nicht davor. Wer
           die App öffnet, will telefonieren können; Namen und Bilder
           sind Beiwerk, das nachziehen darf. Die Bremse im Verzeichnis
           sorgt dafür, dass das nicht bei jedem Start eine Anfrage
           ist. */
        /* AUCH DAS EIGENE PROFIL. Das Verzeichnis kommt aus dem Netz und
           ist beim Start leer; ohne diese Zeile stünde bis zum nächsten
           App-Start das Kürzel da, obwohl das Bild längst geladen ist. */
        Verzeichnis.beiAenderung = {
            zeigeKontakte()
            zeigeProfil()
            zeigeChat()
        }
        Verzeichnis.lade(this)
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

    /**
     * Was die Anrufmeldung an die App zurückschickt.
     *
     * Als Absicht (Intent) und nicht als Rundruf: So kommt die App in
     * jedem Fall in den Vordergrund – auch aus dem gesperrten Zustand –,
     * und der Benutzer sieht, was er gerade angenommen hat.
     */
    companion object {
        const val AKTION_ANNEHMEN = "de.dariatech.softphone.ANNEHMEN"
        const val AKTION_ABLEHNEN = "de.dariatech.softphone.ABLEHNEN"
        const val AKTION_AUFLEGEN = "de.dariatech.softphone.AUFLEGEN"
        const val AKTION_OEFFNEN = "de.dariatech.softphone.OEFFNEN"
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        behandleAbsicht(intent)
    }

    private fun behandleAbsicht(intent: Intent?) {
        when (intent?.action) {
            AKTION_ANNEHMEN -> LinphoneManager.answer()
            AKTION_ABLEHNEN, AKTION_AUFLEGEN -> LinphoneManager.hangup()
            else -> Unit
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

    private enum class Tab { DIALPAD, HISTORY, KONTAKTE, NACHRICHTEN, SETTINGS }

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
                R.id.leiste_kontakte -> showTab(Tab.KONTAKTE)
                else -> showTab(Tab.NACHRICHTEN)
            }
            true
        }
        binding.profilKnopf.setOnClickListener { showTab(Tab.SETTINGS) }
    }

    private fun showTab(tab: Tab) {
        binding.viewDialpad.visibility = if (tab == Tab.DIALPAD) View.VISIBLE else View.GONE
        binding.viewHistory.visibility = if (tab == Tab.HISTORY) View.VISIBLE else View.GONE
        binding.viewSettings.visibility = if (tab == Tab.SETTINGS) View.VISIBLE else View.GONE
        binding.viewKontakte.root.visibility = if (tab == Tab.KONTAKTE) View.VISIBLE else View.GONE
        binding.viewChat.root.visibility =
            if (tab == Tab.NACHRICHTEN) View.VISIBLE else View.GONE
        if (tab == Tab.HISTORY) refreshHistory()
        if (tab == Tab.NACHRICHTEN) {
            /* BEIM ÖFFNEN NACHSEHEN. Das Verzeichnis liefert die
               Menschen, das Postfach ihre Worte – ohne beides steht der
               Bereich leer da, obwohl alles schon geladen sein könnte. */
            Verzeichnis.lade(this)
            Postfach.lade(this)
            zeigeChat()
        }
    }

    /**
     * Die beiden Bereiche, die noch keine Daten haben.
     *
     * SIE SAGEN, WAS SIE SIND, statt auf einen anderen Bereich
     * umzuleiten. Eine App, die auf „Kontakte" den Verlauf zeigt, ist
     * kaputt; eine, die sagt „kommt aus dem Portal", ist ehrlich – und
     * der Benutzer weiß, dass er nichts falsch gemacht hat.
     *
     * Die Texte stehen schon in strings.xml, die Anbindung an die Anlage
     * kommt später. Das ist der Unterschied zwischen „fehlt noch" und
     * „funktioniert nicht".
     */
    private fun setupLeereBereiche() {
        binding.viewKontakte.kontakteLeerTitel.setText(R.string.leer_kontakte_titel)
        binding.viewKontakte.kontakteLeerText.setText(R.string.leer_kontakte_text)
        binding.viewKontakte.kontakteListe.layoutManager = LinearLayoutManager(this)
        kontakteAdapter = KontakteAdapter { ziel ->
            /* EIN DRUCK WÄHLT und wechselt in den Anrufbereich – wer
               hier tippt, will telefonieren. */
            LinphoneManager.call(ziel)
        }
        binding.viewKontakte.kontakteListe.adapter = kontakteAdapter
        zeigeKontakte()
        binding.viewChat.chatListe.layoutManager = LinearLayoutManager(this)
        Postfach.beiAenderung = { zeigeChat() }
    }

    /**
     * Die Liste im Bereich „Nachrichten" nachziehen.
     *
     * SICHTBAR IST IMMER GENAU EINES – Liste oder leerer Zustand.
     * „Nichts da" und „kaputt" sehen sonst gleich aus.
     *
     * OHNE EIGENE KENNUNG GEHT NICHTS: Die App muss wissen, WER sie
     * ist, sonst weiß sie bei keiner Nachricht, ob sie von ihr kommt.
     * Solange kein Token da ist, steht der leere Zustand.
     */
    private fun zeigeChat() {
        val ich = Verzeichnis.ich(this)?.id
        val leute = if (ich == null) emptyList() else Verzeichnis.kollegen.filter { it.id != ich }
        if (ich == null || leute.isEmpty()) {
            binding.viewChat.chatListe.visibility = View.GONE
            binding.viewChat.chatLeer.visibility = View.VISIBLE
            return
        }
        if (chatAdapter == null) {
            chatAdapter = ChatAdapter(this, ich) { kollege ->
                startActivity(
                    Intent(this, GespraechsActivity::class.java)
                        .putExtra(GespraechsActivity.ZIEL, kollege.id)
                        .putExtra(GespraechsActivity.NAME, kollege.name)
                )
            }
            binding.viewChat.chatListe.adapter = chatAdapter
        }
        chatAdapter?.setze(leute)
        binding.viewChat.chatListe.visibility = View.VISIBLE
        binding.viewChat.chatLeer.visibility = View.GONE
    }

    /**
     * Der Profilknopf: das eigene BILD, sonst die Initialen.
     *
     * DER AUFTRAG (Inhaber, 05.09.2026): „In der App soll auch das
     * Profilbild vom Nutzer angezeigt werden."
     *
     * Bis dahin stand hier ein Kreis mit zwei Buchstaben aus dem
     * SIP-BENUTZERNAMEN. Zwei Dinge stimmten daran nicht: Das Bild, das
     * die Kollegen beim Anruf sehen, sah der Mensch selbst nie – und
     * seit die SIP-Namen unerratbar sind (`nst-e492zth5a84g`), wurde
     * daraus für JEDEN im Haus dasselbe Kürzel „NS".
     *
     * Beides beantwortet derselbe Eintrag: der eigene Mensch aus der
     * Kollegenliste. Kennt die App ihn noch nicht (kein Token, noch
     * nichts geladen), bleibt der alte Weg über den Benutzernamen – ein
     * leerer Kreis sähe aus wie ein Ladefehler.
     */
    private fun zeigeProfil() {
        val ich = Verzeichnis.ich(this)
        val bild = Verzeichnis.bild(ich?.id)
        if (bild != null) {
            binding.profilBild.setImageDrawable(rund(bild))
            binding.profilBild.visibility = View.VISIBLE
            binding.profilKuerzel.visibility = View.GONE
            return
        }
        binding.profilBild.visibility = View.GONE
        binding.profilKuerzel.visibility = View.VISIBLE
        binding.profilKuerzel.text =
            if (ich != null) Verzeichnis.kuerzel(ich.name)
            else Verzeichnis.kuerzel(prefs().getString("username", "") ?: "")
    }

    /**
     * Ein Bild als Kreis.
     *
     * WARUM NICHT IM LAYOUT: Ein ImageView schneidet nicht rund zu.
     * Dieselben Bilder liegen im Portal und auf dem iPhone in einem
     * Kreis; ein Quadrat auf Android hieße, derselbe Mensch sähe auf
     * jedem Gerät anders aus.
     */
    private fun rund(bild: android.graphics.Bitmap) =
        androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
            .create(resources, bild)
            .apply { isCircular = true }

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

    /**
     * Das Gesicht des Gegenübers einblenden – oder ausblenden.
     *
     * NUR WENN ES EINES GIBT. Ein leerer Kreis sieht aus wie ein Bild,
     * das nicht geladen hat; der Name darunter steht ohnehin da.
     */
    private fun zeigeGesicht() {
        val bild = Verzeichnis.bild(gegenueberId)
        if (bild != null) {
            binding.callerFoto.setImageDrawable(rund(bild))
            binding.callerFoto.visibility = View.VISIBLE
        } else {
            binding.callerFoto.setImageDrawable(null)
            binding.callerFoto.visibility = View.GONE
        }
    }

    private var kontakteAdapter: KontakteAdapter? = null
    private var chatAdapter: ChatAdapter? = null

    /**
     * Die Kontaktliste nachziehen – Liste oder leerer Zustand.
     *
     * SICHTBAR IST IMMER GENAU EINES. „Nichts da" und „kaputt" sehen
     * sonst gleich aus, und der Bereich springt beim ersten Laden.
     */
    private fun zeigeKontakte() {
        val leute = Verzeichnis.kollegen
        val buch = Verzeichnis.kontakte
        kontakteAdapter?.setze(leute, buch)
        val leer = leute.isEmpty() && buch.isEmpty()
        binding.viewKontakte.kontakteListe.visibility = if (leer) View.GONE else View.VISIBLE
        binding.viewKontakte.kontakteLeer.visibility = if (leer) View.VISIBLE else View.GONE
    }

    private var verlauf: VerlaufAdapter? = null

    /**
     * Das Namenskürzel für einen Verlaufseintrag.
     *
     * Es gibt in dieser App noch kein Adressbuch – also kommt es aus der
     * NUMMER. Die letzten beiden Ziffern sind das, woran man eine
     * Nebenstelle wiedererkennt („…12"); bei einer langen Rufnummer
     * bleibt ein Rautezeichen, weil zwei beliebige Ziffern daraus nichts
     * bedeuten.
     */
    private fun kuerzelFuer(e: CallEntry): String {
        val nur = e.number.filter { it.isDigit() }
        return when {
            nur.isEmpty() -> "?"
            nur.length <= 4 -> nur.takeLast(2)
            else -> "#"
        }
    }

    private fun refreshHistory() {
        val entries = CallLogStore.list(this)
        val missed = CallLogStore.missedToday(this)
        binding.missedInfo.text = getString(R.string.missed_today, missed)
        binding.missedInfo.visibility = if (missed > 0) View.VISIBLE else View.GONE
        binding.historyEmpty.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE

        if (verlauf == null) {
            binding.historyList.layoutManager =
                androidx.recyclerview.widget.LinearLayoutManager(this)
            verlauf = VerlaufAdapter(
                entries,
                amKuerzel = { kuerzelFuer(it) },
                // ZURÜCKRUFEN IST EIN TIPPEN. Vorher übernahm ein Tippen
                // die Nummer ins Wählfeld, und man musste ein zweites Mal
                // drücken – in einer App, die man im Gehen bedient, ein
                // Schritt zu viel.
                beimAnrufen = { LinphoneManager.call(it.number) },
                // Die Zeile selbst führt weiterhin ins Wählfeld: Wer die
                // Nummer erst ansehen oder ergänzen will, kann das.
                beimTippen = {
                    binding.number.setText(it.number)
                    binding.number.setSelection(binding.number.text.length)
                    binding.leiste.selectedItemId = R.id.leiste_tastenfeld
                    showTab(Tab.DIALPAD)
                }
            )
            binding.historyList.adapter = verlauf
        } else {
            verlauf?.zeige(entries)
        }
    }

    // ---------- Einstellungen (SIP-Konto) ----------

    private fun setupSettings() {
        val prefs = prefs()
        // Ein Altbestand aus der Zeit, als die Serveradresse noch ein
        // Eingabefeld war, wird hier aufgeräumt UND gemeldet – siehe
        // Anlage.raeumeAltbestandAuf().
        Anlage.raeumeAltbestandAuf(prefs)

        binding.anlageZeile.text = getString(R.string.anlage_zeile, Anlage.anzeige)
        binding.username.setText(prefs.getString("username", ""))
        zeigeDienst()
        binding.neuAnmelden.setOnClickListener {
            // Kein erneutes Eintippen des Passworts: Nach einem
            // Netzwechsel (WLAN auf Mobilfunk) steht die Registrierung
            // manchmal still, bis sie jemand anstößt.
            LinphoneManager.neuAnmelden()
            zeigeDienst(Dienstzustand.LAEUFT)
        }
        // Das Passwort kommt aus dem VERSCHLÜSSELTEN Speicher – siehe
        // Zugangsspeicher. Beim ersten Lesen zieht ein Altbestand aus der
        // Klartext-Ablage automatisch mit um.
        binding.password.setText(Zugangsspeicher.passwort(this))

        binding.connectButton.setOnClickListener {
            /* WECHSELT DER MENSCH, GEHEN SEINE NACHRICHTEN MIT.
               Ein anderer Benutzername an diesem Gerät heißt: ein
               anderer Mensch. Bliebe das Postfach stehen, läse er die
               Nachrichten seines Vorgängers – auf einem Diensthandy,
               das weitergegeben wird, der wahrscheinlichste Fall. */
            val vorher = prefs.getString("username", "") ?: ""
            val jetzt = binding.username.text.toString().trim()
            if (vorher != jetzt) {
                Postfach.leere(this)
                Verzeichnis.leere(this)
            }
            prefs.edit()
                .putString("username", jetzt)
                .apply()
            Zugangsspeicher.setzePasswort(this, binding.password.text.toString())
            connect()
            /* DEN AUSWEIS FÜR DEN DIENST HOLEN – nach dem Absenden,
               nicht davor. Sind die Zugangsdaten falsch, scheitert
               ohnehin schon die Registrierung; zwei Fehlermeldungen für
               einen Tippfehler sind eine zu viel.

               Und es hält nichts auf: Wer kein Netz zum Dienst hat,
               telefoniert trotzdem. Kontakte und Bilder ziehen später
               nach. Dieselbe Regel wie auf iOS. */
            holeAusweis()
            zeigeProfil()
            binding.leiste.selectedItemId = R.id.leiste_tastenfeld
            showTab(Tab.DIALPAD)
        }
    }

    /**
     * Name und Passwort einmal gegen das Token des Dienstes tauschen –
     * im Hintergrundfaden, weil Android eine App beendet, die im
     * Hauptfaden ins Netz geht.
     */
    private fun holeAusweis() {
        val name = prefs().getString("username", "")?.trim() ?: ""
        val wort = Zugangsspeicher.passwort(this)
        if (name.isEmpty() || wort.isEmpty()) return
        val app = applicationContext
        Thread {
            if (Dienst.hole(app, name, wort)) Verzeichnis.lade(app, erzwingen = true)
        }.start()
    }

    private fun connect() {
        // Server und Transport kommen aus Anlage.kt, nicht aus den
        // Einstellungen. Diese App gehört EINER Anlage.
        val transport = when (Anlage.TRANSPORT) {
            "TCP" -> TransportType.Tcp
            "TLS" -> TransportType.Tls
            else -> TransportType.Udp
        }
        LinphoneManager.login(
            prefs().getString("username", "") ?: "",
            Zugangsspeicher.passwort(this),
            Anlage.SERVER,
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
            // Die Einstellungen führen denselben Zustand mit. Ohne diese
            // Zeile stünde dort für immer, was beim Öffnen galt – und
            // genau dort sieht jemand nach, wenn nichts geht.
            zeigeDienst(Telefondienst.zustandAus(state), message)
        }
    }

    // ---------- Telefondienst in den Einstellungen ----------

    /**
     * Zustand, Grund und Nebenstelle in den Einstellungen nachziehen.
     *
     * Ohne Argumente wird der Zustand aus dem abgeleitet, was dasteht:
     * Wer noch keine Zugangsdaten eingetragen hat, hat KEINE Störung –
     * ihm ein rotes „Nicht verbunden" hinzustellen, schickt ihn auf
     * Fehlersuche statt in das Feld darunter.
     */
    private fun zeigeDienst(
        zustand: Dienstzustand? = null,
        meldung: String = ""
    ) {
        val nebenstelle = prefs().getString("username", "").orEmpty()
        val echt = zustand ?: when {
            nebenstelle.isBlank() || Zugangsspeicher.passwort(this).isEmpty() ->
                Dienstzustand.UNVOLLSTAENDIG
            LinphoneManager.istRegistriert() -> Dienstzustand.VERBUNDEN
            else -> Dienstzustand.GETRENNT
        }

        binding.dienstText.text = Telefondienst.bezeichnung(echt)
        binding.dienstPunkt.setBackgroundResource(
            when (echt) {
                Dienstzustand.VERBUNDEN -> R.drawable.dot_green
                Dienstzustand.GETRENNT -> R.drawable.dot_red
                else -> R.drawable.dot_grau
            }
        )

        val grund = if (echt == Dienstzustand.GETRENNT) Telefondienst.grund(meldung) else ""
        binding.dienstGrund.text = grund
        binding.dienstGrund.visibility = if (grund.isBlank()) View.GONE else View.VISIBLE

        binding.dienstNebenstelle.text = if (nebenstelle.isBlank()) {
            getString(R.string.nebenstelle_leer)
        } else {
            getString(R.string.nebenstelle_zeile, "$nebenstelle@${Anlage.SERVER}")
        }
    }

    override fun onCallState(call: Call, state: Call.State?, message: String) {
        runOnUiThread {
            /* WER IST DA? – aus dem VERZEICHNIS, nicht nur aus dem
               SIP-Kopf. `displayName` setzt die Gegenstelle, und bei
               einem Anruf von draußen steht dort meistens gar nichts
               oder die Nummer noch einmal. Der Name aus dem eigenen
               Verzeichnis ist der, den der Mensch wiedererkennt – und
               nur über ihn kommt man an sein Bild.

               Gesucht wird im GERÄT (Verzeichnis.wer): Beim Klingeln
               ist keine Zeit für eine Anfrage, und ohne Netz gäbe es
               gar keine Antwort. Dieselbe Regel wie auf iOS. */
            val nummer = call.remoteAddress.username ?: ""
            val treffer = Verzeichnis.wer(nummer)
            val who = treffer?.first
                ?: call.remoteAddress.displayName
                ?: call.remoteAddress.username
                ?: call.remoteAddress.asStringUriOnly()
            gegenueberId = treffer?.second
            zeigeGesicht()
            when (state) {
                Call.State.IncomingReceived, Call.State.IncomingEarlyMedia -> {
                    binding.callerInfo.text = who
                    binding.callState.text = getString(R.string.incoming_call)
                    showCallUi(inCall = false, ringing = true)
                    // Auch wenn die App zu ist und das Telefon in der
                    // Tasche liegt: Ohne diese Meldung klingelt es zwar,
                    // aber auf dem Sperrbildschirm steht nichts.
                    Anrufmeldung.eingehend(this, who)
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
                    Anrufmeldung.laufend(this, who)
                    // Auch die GEGENSTELLE kann Bild dazu- oder abschalten:
                    // Jeder Zustandswechsel richtet die Anzeige nach, statt
                    // sie nur beim eigenen Drücken zu setzen.
                    zeigeVideo()
                }
                Call.State.UpdatedByRemote -> zeigeVideo()
                Call.State.End, Call.State.Released, Call.State.Error -> {
                    Anrufmeldung.weg(this)
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
