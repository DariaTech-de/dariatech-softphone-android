package de.dariatech.softphone

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.TextureView
import org.linphone.core.Account
import org.linphone.core.AudioDevice
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Factory
import org.linphone.core.MediaEncryption
import org.linphone.core.RegistrationState
import org.linphone.core.TransportType

/**
 * Wrapper um den Liblinphone-Core: Registrierung, Anrufe, Stummschaltung,
 * Lautsprecher, Halten, DTMF und die eigene Anrufliste. Die UI hängt sich
 * über [listener] an.
 */
object LinphoneManager {

    interface Listener {
        fun onRegistration(state: RegistrationState?, message: String)
        fun onCallState(call: Call, state: Call.State?, message: String)
    }

    lateinit var core: Core
        private set

    var listener: Listener? = null
    private var appContext: Context? = null

    // Verfolgung des aktuellen Anrufs für die Anrufliste
    private var trackNumber = ""
    private var trackIncoming = false
    private var trackConnected = false

    fun init(context: Context) {
        appContext = context.applicationContext
        val factory = Factory.instance()
        core = factory.createCore(null, null, context)
        core.isPushNotificationEnabled = false

        // VIDEO IST VORHANDEN, ABER NICHT AUTOMATISCH.
        //
        // Liblinphone kann Video vollständig – das ist der Grund, warum
        // es hier überhaupt in Tagen und nicht in Monaten geht. Trotzdem
        // steht es beim Start auf „vorhanden, aber nicht von selbst":
        //
        //  · isVideoCaptureEnabled/isVideoDisplayEnabled schalten die
        //    FÄHIGKEIT ein. Ohne sie kann die App gar kein Bild, auch
        //    wenn die Gegenstelle eins anbietet.
        //  · isVideoActivationPolicy „automatically initiate/accept =
        //    false" heißt: Kein Anruf startet von selbst mit Bild, und
        //    keiner nimmt Bild von selbst an. Der Mensch drückt.
        //
        // Der Grund ist nicht Vorsicht um ihrer selbst willen: Ein
        // Telefon, das bei jedem Anruf ungefragt die Kamera einschaltet,
        // ist ein Datenschutzvorfall mit Ansage. Die Anlage gibt Video
        // frei (Einstellungen → Video); DASS es läuft, entscheidet hier
        // die Person am Gerät.
        // DIE SPRACHE WIRD VERSCHLÜSSELT (SRTP) – seit dem 06.09.2026.
        //
        // ANLASS: Der Auftrag des Inhabers, Punkt 11 aus Stufe 2 des
        // Masterplans. Ohne diese Zeile geht das Gespräch als offenes
        // RTP durchs Netz, und im WLAN eines Hotels hört jeder mit, der
        // danebensitzt.
        //
        // UND ES IST KEIN SCHMUCK MEHR, SONDERN PFLICHT: Die Anlage
        // legt seit demselben Tag NEUE Nebenstellen mit
        // `media_encryption=sdes` und `media_encryption_optimistic=no`
        // an – also STRIKT. Eine App ohne SRTP bekäme dort überhaupt
        // kein Gespräch zustande.
        //
        // `isMediaEncryptionMandatory = false` ist der Bestandsschutz:
        // Nebenstellen, die es seit Jahren gibt, sprechen weiter offen.
        // Auf `true` wäre die Härtung eine Aussperrung – und wer nicht
        // telefonieren kann, ruft auch keine 112.
        // `setMediaEncryption` gibt einen Status zurueck, deshalb kein
        // Zuweisungs-Schreibstil: Kotlin macht daraus keine Eigenschaft.
        core.setMediaEncryption(MediaEncryption.SRTP)
        core.isMediaEncryptionMandatory = false

        core.isVideoCaptureEnabled = true
        core.isVideoDisplayEnabled = true
        val politik = factory.createVideoActivationPolicy()
        politik.automaticallyInitiate = false
        politik.automaticallyAccept = false
        core.videoActivationPolicy = politik
        core.addListener(object : CoreListenerStub() {
            override fun onAccountRegistrationStateChanged(
                core: Core,
                account: Account,
                state: RegistrationState?,
                message: String
            ) {
                listener?.onRegistration(state, message)
            }

            override fun onCallStateChanged(
                core: Core,
                call: Call,
                state: Call.State?,
                message: String
            ) {
                trackCall(call, state)
                listener?.onCallState(call, state, message)
            }
        })
        core.start()
    }

    private fun trackCall(call: Call, state: Call.State?) {
        when (state) {
            Call.State.IncomingReceived, Call.State.IncomingEarlyMedia -> {
                trackNumber = call.remoteAddress.username ?: call.remoteAddress.asStringUriOnly()
                trackIncoming = true
                trackConnected = false
            }
            Call.State.OutgoingInit -> {
                trackNumber = call.remoteAddress.username ?: call.remoteAddress.asStringUriOnly()
                trackIncoming = false
                trackConnected = false
            }
            Call.State.Connected, Call.State.StreamsRunning -> trackConnected = true
            Call.State.End, Call.State.Error -> {
                if (trackNumber.isNotEmpty()) {
                    val direction = when {
                        trackIncoming && !trackConnected -> "missed"
                        trackIncoming -> "in"
                        else -> "out"
                    }
                    appContext?.let {
                        CallLogStore.add(
                            it,
                            CallEntry(trackNumber, direction, System.currentTimeMillis(), call.duration)
                        )
                    }
                    trackNumber = ""
                }
            }
            else -> Unit
        }
    }

    /**
     * Die Signalisierung läuft UNVERSCHLÜSSELT – weil die Anlage kein
     * SIP über TLS annimmt und die App zurückgefallen ist.
     *
     * WARUM DAS SICHTBAR SEIN MUSS: Ein Rückfall, den niemand sieht,
     * ist schlimmer als gar kein Versuch. Der Kunde hielte die App für
     * verschlüsselt, obwohl im Netz mitzulesen ist, wer wen anruft.
     */
    var signalisierungOffen = false
        private set

    /** Was zuletzt angemeldet wurde – der Rückfall braucht es noch einmal. */
    private var letzterBenutzer = ""
    private var letztesPasswort = ""
    private var letzteDomain = ""
    private var versuchMitTls = false
    private val uhr = Handler(Looper.getMainLooper())

    /** Meldet das Konto an; vorhandene Konten werden ersetzt. */
    fun login(username: String, password: String, domain: String, transport: TransportType) {
        letzterBenutzer = username
        letztesPasswort = password
        letzteDomain = domain
        versuchMitTls = transport == TransportType.Tls
        if (versuchMitTls) signalisierungOffen = false
        melde(username, password, domain, transport)
        if (versuchMitTls) beobachteTlsVersuch()
    }

    /**
     * Acht Sekunden auf eine Anmeldung warten, sonst zurückfallen.
     *
     * Gewartet wird eine feste, kurze Zeit statt auf einen Fehlercode:
     * Ein TLS-Handschlag ins Leere endet je nach Netz mit einem
     * Zeitablauf, einem Verbindungsabbruch oder gar nichts. Auf „keine
     * Anmeldung nach acht Sekunden" ist Verlass, auf die Fehlermeldung
     * nicht.
     */
    private fun beobachteTlsVersuch() {
        uhr.postDelayed({
            if (versuchMitTls && !istRegistriert()) faelleZurueck()
        }, 8_000)
    }

    private fun faelleZurueck() {
        versuchMitTls = false
        signalisierungOffen = true
        android.util.Log.w(
            "Anlage",
            "Diese Anlage nimmt kein SIP über TLS an. Anmeldung über " +
                "${Anlage.RUECKFALL_TRANSPORT}; die Sprache bleibt durch SRTP " +
                "verschlüsselt, die Signalisierung nicht."
        )
        melde(letzterBenutzer, letztesPasswort, letzteDomain, TransportType.Udp)
    }

    private fun melde(username: String, password: String, domain: String, transport: TransportType) {
        core.clearAccounts()
        core.clearAllAuthInfo()

        val authInfo = Factory.instance()
            .createAuthInfo(username, null, password, null, null, domain, null)
        core.addAuthInfo(authInfo)

        val params = core.createAccountParams()
        params.identityAddress = Factory.instance().createAddress("sip:$username@$domain")
        val server = Factory.instance().createAddress("sip:$domain")
        server?.transport = transport
        params.serverAddress = server
        params.isRegisterEnabled = true
        val account = core.createAccount(params)
        core.addAccount(account)
        core.defaultAccount = account
    }

    /**
     * Die Registrierung neu anstoßen, ohne Zugangsdaten anzufassen.
     *
     * NACH EINEM NETZWECHSEL steht sie manchmal still: Wer aus dem WLAN
     * in den Mobilfunk läuft, behält eine Registrierung, die ins Leere
     * zeigt, bis der Ablauftimer greift – und das kann Minuten dauern.
     * In dieser Zeit klingelt das Telefon nicht. Ein Knopf, der das
     * anstößt, ist keine Bequemlichkeit, sondern der kürzeste Weg aus
     * einem stillen Telefon.
     */
    fun neuAnmelden() {
        core.refreshRegisters()
    }

    /** Ob gerade ein Konto angemeldet ist – für die Einstellungen. */
    fun istRegistriert(): Boolean =
        core.defaultAccount?.state == org.linphone.core.RegistrationState.Ok

    /**
     * Anrufen. `mitVideo` startet den Anruf mit Bild – sonst rein
     * akustisch, wie bisher. Video lässt sich im Gespräch jederzeit
     * dazuschalten (siehe [videoUmschalten]).
     */
    fun call(number: String, mitVideo: Boolean = false) {
        val address = core.interpretUrl(number, true) ?: return
        val params = core.createCallParams(null) ?: return
        params.isVideoEnabled = mitVideo
        /* ZRTP NUR NACH INNEN, und gesetzt wird es AM ANRUF.

           Am Kern gesetzt gälte es auch nach draußen – und dort kann es
           niemand: Der Anbieter spricht kein ZRTP, das Gespräch käme
           entweder unverschlüsselt oder gar nicht zustande. Punkt 20
           des Masterplans sagt dasselbe von der anderen Seite: nach
           draußen und beim Notruf geht es über die Anlage.

           Wirksam wird es erst, wenn die Anlage sich aus dem Sprachweg
           zurückzieht (Schalter „Sicheres Direktgespräch"). Tut sie es
           nicht, läuft ZRTP zwischen App und Anlage – dann gilt das
           Sicherheitswort nur bis dorthin. */
        params.mediaEncryption =
            if (istKollege(number)) MediaEncryption.ZRTP else MediaEncryption.SRTP
        core.inviteAddressWithParams(address, params)
    }

    /**
     * Ist diese Nummer eine Nebenstelle DIESES Hauses?
     *
     * Nur dort ergibt ZRTP einen Sinn: Es braucht eine Gegenstelle, die
     * es auch spricht, und einen Medienweg, aus dem die Anlage sich
     * zurückziehen kann.
     *
     * Erkannt wird es an der Kollegenliste. Eine Längenregel („alles
     * unter fünf Stellen ist intern") wäre geraten; Häuser mit
     * fünfstelligen Durchwahlen gibt es.
     */
    fun istKollege(nummer: String): Boolean {
        val z = nummer.filter { it.isDigit() }
        if (z.isEmpty()) return false
        return Verzeichnis.kollegen.any { k -> k.nebenstellen.contains(z) || k.durchwahl == z }
    }

    /**
     * Das Sicherheitswort dieses Gesprächs – oder `null`.
     *
     * Steht es da, ist die Sprache ENDE ZU ENDE verschlüsselt: Die
     * Schlüssel wurden im Medienstrom ausgehandelt (ZRTP), also dort,
     * wo die Anlage nach ihrem Rückzug nicht mehr ist. Wer dazwischen
     * säße, müsste zwei verschiedene Schlüssel aushandeln – und dann
     * stünden auf den beiden Bildschirmen zwei verschiedene Wörter.
     *
     * `null` heißt NICHT „unverschlüsselt", sondern „verschlüsselt bis
     * zur Anlage" (SRTP, Stufe 2).
     */
    fun sicherheitswort(): String? {
        val wort = core.currentCall?.authenticationToken ?: return null
        return wort.ifEmpty { null }
    }

    /**
     * Ist DIESES Gespräch überhaupt verschlüsselt?
     *
     * Gelesen wird, was für den laufenden Anruf gilt – nicht, was die
     * App gern hätte: Die Gegenstelle kann eine alte Nebenstelle ohne
     * SRTP sein, und dann geht die Sprache offen durchs Netz, auch wenn
     * die eigene Seite alles richtig macht.
     */
    fun gespraechVerschluesselt(): Boolean {
        val art = core.currentCall?.currentParams?.mediaEncryption ?: return false
        return art != MediaEncryption.None
    }

    /** Hat der Mensch das Wort schon mit der Gegenseite verglichen? */
    fun sicherheitswortBestaetigt(): Boolean =
        core.currentCall?.authenticationTokenVerified == true

    /**
     * Das Sicherheitswort mit der Gegenseite verglichen – und es
     * stimmt.
     *
     * Liblinphone merkt sich das über das Gespräch hinaus: Beim
     * nächsten Anruf mit demselben Gegenüber fragt es nicht wieder.
     * Ändert sich der Schlüssel doch, meldet es sich von selbst – und
     * genau das ist der Fall, den man sehen will.
     */
    fun bestaetigeSicherheitswort() {
        core.currentCall?.authenticationTokenVerified = true
    }

    /**
     * Annehmen. Ein Videoanruf wird BEWUSST zunächst ohne Bild
     * angenommen: Wer angerufen wird, soll nicht ungefragt gesendet
     * werden. Das Bild kommt mit einem Druck auf „Video" dazu.
     */
    fun answer() {
        val call = core.currentCall ?: return
        val params = core.createCallParams(call)
        if (params != null) {
            params.isVideoEnabled = false
            call.acceptWithParams(params)
        } else {
            call.accept()
        }
    }

    /** Bietet die Gegenstelle im laufenden Gespräch Bild an? */
    fun gegenstelleMitVideo(): Boolean =
        core.currentCall?.remoteParams?.isVideoEnabled == true

    /** Läuft gerade Bild? */
    fun videoLaeuft(): Boolean = core.currentCall?.currentParams?.isVideoEnabled == true

    /**
     * Bild im laufenden Gespräch dazuschalten oder abschalten.
     * Liefert den neuen Zustand.
     */
    fun videoUmschalten(): Boolean {
        val call = core.currentCall ?: return false
        val an = !(call.currentParams.isVideoEnabled)
        val params = core.createCallParams(call) ?: return false
        params.isVideoEnabled = an
        call.update(params)
        return an
    }

    /** Zwischen Front- und Rückkamera wechseln. */
    fun kameraWechseln() {
        val liste = core.videoDevicesList
        if (liste.size < 2) return
        val jetzt = core.videoDevice
        val naechste = liste.firstOrNull { it != jetzt && !it.contains("StaticImage", true) }
        if (naechste != null) core.videoDevice = naechste
    }

    /**
     * Die beiden Flächen verdrahten, auf denen Bild erscheint.
     *
     * Liblinphone zeichnet selbst auf diese Views; ohne sie bliebe das
     * Bild aus, obwohl der Strom läuft. Sie werden beim Start der
     * Oberfläche gesetzt und beim Beenden wieder gelöst – ein Verweis
     * auf eine zerstörte Activity ist sonst ein Absturz beim nächsten
     * Anruf.
     */
    fun videoFlaechen(fremd: TextureView?, eigen: TextureView?) {
        core.nativeVideoWindowId = fremd
        core.nativePreviewWindowId = eigen
    }

    fun hangup() {
        if (core.callsNb > 0) {
            (core.currentCall ?: core.calls.firstOrNull())?.terminate()
        }
    }

    fun toggleMute(): Boolean {
        core.isMicEnabled = !core.isMicEnabled
        return !core.isMicEnabled
    }

    /** Anruf halten/fortsetzen; liefert true = wird gehalten. */
    fun toggleHold(): Boolean {
        val call = core.currentCall ?: core.calls.firstOrNull() ?: return false
        return if (call.state == Call.State.Paused || call.state == Call.State.Pausing) {
            call.resume()
            false
        } else {
            call.pause()
            true
        }
    }

    /** DTMF-Ton im laufenden Gespräch senden (IVR-Menüs). */
    fun sendDtmf(digit: Char) {
        core.currentCall?.sendDtmf(digit)
    }

    /** Laufzeit des aktiven Anrufs in Sekunden. */
    fun currentCallDuration(): Int = core.currentCall?.duration ?: 0

    /** Wechselt zwischen Hörmuschel und Lautsprecher; liefert true = Lautsprecher. */
    fun toggleSpeaker(): Boolean {
        val call = core.currentCall ?: return false
        val current = call.outputAudioDevice
        val wantSpeaker = current?.type != AudioDevice.Type.Speaker
        val target = core.audioDevices.firstOrNull {
            it.type == (if (wantSpeaker) AudioDevice.Type.Speaker else AudioDevice.Type.Earpiece) &&
                it.hasCapability(AudioDevice.Capabilities.CapabilityPlay)
        }
        if (target != null) call.outputAudioDevice = target
        return wantSpeaker
    }
}
