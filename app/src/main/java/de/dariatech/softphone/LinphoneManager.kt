package de.dariatech.softphone

import android.content.Context
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
object LinphoneManager : Telefonkern {

    lateinit var core: Core
        private set

    override var zuhoerer: Telefonkern.Zuhoerer? = null
    private var appContext: Context? = null

    /* DIE ÜBERSETZUNG in die eigenen Typen der Naht (Telefonkern.kt).
       Hier – und nur hier – kennt die App noch die Zustände der
       Bibliothek. */
    private fun uebersetze(state: RegistrationState?): Anmeldezustand = when (state) {
        RegistrationState.Ok -> Anmeldezustand.ANGEMELDET
        RegistrationState.Progress -> Anmeldezustand.LAEUFT
        RegistrationState.Failed -> Anmeldezustand.FEHLGESCHLAGEN
        else -> Anmeldezustand.ABGEMELDET
    }

    private fun uebersetze(state: Call.State?): Anrufzustand = when (state) {
        Call.State.IncomingReceived, Call.State.IncomingEarlyMedia -> Anrufzustand.KLINGELT_HEREIN
        Call.State.OutgoingInit, Call.State.OutgoingProgress, Call.State.OutgoingRinging -> Anrufzustand.RUFT_HINAUS
        Call.State.Connected, Call.State.StreamsRunning -> Anrufzustand.VERBUNDEN
        Call.State.UpdatedByRemote -> Anrufzustand.GEAENDERT
        Call.State.End, Call.State.Released, Call.State.Error -> Anrufzustand.BEENDET
        else -> Anrufzustand.SONST
    }

    private fun gegenstelle(call: Call): Gegenstelle = Gegenstelle(
        nummer = call.remoteAddress.username ?: "",
        anzeigename = call.remoteAddress.displayName,
        adresse = call.remoteAddress.asStringUriOnly()
    )

    // Verfolgung des aktuellen Anrufs für die Anrufliste
    private var trackNumber = ""
    private var trackIncoming = false
    private var trackConnected = false

    override fun init(context: Context) {
        appContext = context.applicationContext
        val factory = Factory.instance()
        core = factory.createCore(null, null, context)
        core.isPushNotificationEnabled = false

        // SRTP – ZWINGEND. Seit dem 09.09.2026.
        //
        // Der Endpunkt jeder Nebenstelle in der Anlage steht auf
        // `media_encryption=sdes` und `media_encryption_optimistic=no`,
        // ohne Wahl. Ein offenes Gespräch kommt dort nicht zustande.
        // `isMediaEncryptionMandatory = false` („Bestandsschutz für
        // alte Nebenstellen") hätte die App ein offenes Angebot annehmen
        // lassen, wenn es eines gäbe – genau die Lücke, die der Inhaber
        // geschlossen haben will. Auf `true` bietet die App nur SRTP an
        // und nimmt nur SRTP an.
        // `setMediaEncryption` gibt einen Status zurück, deshalb kein
        // Zuweisungs-Schreibstil: Kotlin macht daraus keine Eigenschaft.
        core.setMediaEncryption(MediaEncryption.SRTP)
        core.isMediaEncryptionMandatory = true

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
                zuhoerer?.beiAnmeldung(uebersetze(state), message)
            }

            override fun onCallStateChanged(
                core: Core,
                call: Call,
                state: Call.State?,
                message: String
            ) {
                trackCall(call, state)
                zuhoerer?.beiAnruf(gegenstelle(call), uebersetze(state), message)
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
                /* DEM SYSTEM MELDEN – hier und nicht in der Activity.
                   Etappe 1 aus docs/AUFTRAG-AUTO.md: Erst dadurch
                   erscheint der Anruf im Auto, an der
                   Freisprecheinrichtung und auf dem Sperrbildschirm,
                   und erst dadurch weiss ein hereinkommender
                   GSM-Anruf, dass hier schon telefoniert wird. */
                appContext?.let {
                    Autoanruf.kommtAn(it, trackNumber, Verzeichnis.wer(trackNumber)?.first ?: trackNumber)
                }
            }
            Call.State.OutgoingInit -> {
                trackNumber = call.remoteAddress.username ?: call.remoteAddress.asStringUriOnly()
                trackIncoming = false
                trackConnected = false
                appContext?.let {
                    Autoanruf.gehtRaus(it, trackNumber, Verzeichnis.wer(trackNumber)?.first ?: trackNumber)
                }
            }
            Call.State.Connected, Call.State.StreamsRunning -> {
                trackConnected = true
                Autoanruf.verbunden()
            }
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
                /* AUFRÄUMEN, IMMER. Ohne diese Zeile bleibt im Auto und
                   auf dem Sperrbildschirm ein Anruf stehen, den es nicht
                   mehr gibt. */
                Autoanruf.beendet()
            }
            else -> Unit
        }
    }

    /** Was zuletzt angemeldet wurde – damit dieselbe Anmeldung nicht zweimal läuft (siehe [login]). */
    private var letzterBenutzer = ""
    private var letztesPasswort = ""
    private var letzteDomain = ""

    /**
     * Meldet das Konto an; vorhandene Konten werden ersetzt.
     *
     * DIESELBE ANMELDUNG NOCH EINMAL TUT NICHTS. Auf iOS stand am
     * 07.09.2026 in der Konsole der Anlage: REGISTER → 200, gleich
     * darauf REGISTER mit Expires: 0, dann wieder von vorn – bei jedem
     * Erscheinen des Hauptbildschirms. Der Grund war `clearAccounts()`
     * in [melde]: Es meldet das alte Konto AB, bevor es das neue
     * anmeldet. Für einen Moment ist das Telefon nicht erreichbar, und
     * wer in dieser Sekunde anruft, landet in der Mailbox. Hier rufen
     * onCreate und der Speichern-Knopf `connect()`; wer nur speichert,
     * ohne etwas geändert zu haben, soll dabei nicht abgemeldet werden.
     */
    override fun login(username: String, password: String, domain: String) {
        val unveraendert = username == letzterBenutzer &&
            password == letztesPasswort &&
            domain == letzteDomain
        if (unveraendert && (istRegistriert() || istImGange())) {
            android.util.Log.i("Anlage", "Anmeldung unverändert und steht – nichts zu tun.")
            return
        }
        letzterBenutzer = username
        letztesPasswort = password
        letzteDomain = domain
        /* NUR TLS – der einzige Weg, den die Naht kennt; seit dem
           09.09.2026 nimmt die Anlage ohnehin nichts anderes an. */
        melde(username, password, domain, TransportType.Tls)
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
    override fun neuAnmelden() {
        core.refreshRegisters()
    }

    /**
     * Die App ist zurück im Vordergrund.
     *
     * ASTERISK LÖSCHT EINEN CONTACT, DESSEN VERBINDUNG ABREISST
     * („Removed contact … due to shutdown", Konsole der Anlage am
     * 07.09.2026). Über TLS hängt die Anmeldung an einer TCP-Verbindung;
     * legt Android die Verbindung im Hintergrund schlafen, ist das
     * Telefon aus Sicht der Anlage weg – während die App weiter
     * „Verbunden" zeigt. Ein Auffrischen beim Zurückkommen kostet ein
     * REGISTER und schließt genau diese Lücke. Kein clearAccounts: Das
     * würde erst abmelden (siehe [login]).
     */
    override fun vordergrund() {
        if (letzterBenutzer.isEmpty()) return
        core.refreshRegisters()
    }

    /** Ob gerade eine Anmeldung LÄUFT – noch kein Ergebnis, aber unterwegs. */
    private fun istImGange(): Boolean =
        core.defaultAccount?.state == org.linphone.core.RegistrationState.Progress

    /** Ob gerade ein Konto angemeldet ist – für die Einstellungen. */
    override fun istRegistriert(): Boolean =
        core.defaultAccount?.state == org.linphone.core.RegistrationState.Ok

    /**
     * Anrufen. `mitVideo` startet den Anruf mit Bild – sonst rein
     * akustisch, wie bisher. Video lässt sich im Gespräch jederzeit
     * dazuschalten (siehe [videoUmschalten]).
     */
    override fun call(number: String, mitVideo: Boolean) {
        val address = core.interpretUrl(number, true) ?: return
        val params = core.createCallParams(null) ?: return
        params.isVideoEnabled = mitVideo
        /* SRTP FÜR JEDEN ANRUF – auch zum Kollegen.

           Bis zum 09.09.2026 stand hier ZRTP für interne Gespräche
           (Stufe 4 des Masterplans: Sicherheitswort, Ende-zu-Ende bei
           direktem Medienweg). Seit jede Nebenstelle in der Anlage
           strikt SDES verlangt, lehnt der Endpunkt ein ZRTP-Angebot
           (RTP/AVP ohne a=crypto) mit 488 ab, BEVOR ein Kanal entsteht
           – das war das Bild vom 09.09.2026: „0 calls processed",
           während der Inhaber ständig anrief. Das Sicherheitswort
           bleibt im Code, aber es entsteht keins mehr; der
           Gesprächsbildschirm zeigt deshalb „bis zur Anlage". */
        params.mediaEncryption = MediaEncryption.SRTP
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
    override fun istKollege(nummer: String): Boolean {
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
    override fun sicherheitswort(): String? {
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
    override fun gespraechVerschluesselt(): Boolean {
        val art = core.currentCall?.currentParams?.mediaEncryption ?: return false
        return art != MediaEncryption.None
    }

    /** Hat der Mensch das Wort schon mit der Gegenseite verglichen? */
    override fun sicherheitswortBestaetigt(): Boolean =
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
    override fun bestaetigeSicherheitswort() {
        core.currentCall?.authenticationTokenVerified = true
    }

    /**
     * Annehmen. Ein Videoanruf wird BEWUSST zunächst ohne Bild
     * angenommen: Wer angerufen wird, soll nicht ungefragt gesendet
     * werden. Das Bild kommt mit einem Druck auf „Video" dazu.
     */
    override fun answer() {
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
    override fun gegenstelleMitVideo(): Boolean =
        core.currentCall?.remoteParams?.isVideoEnabled == true

    /** Läuft gerade Bild? */
    override fun videoLaeuft(): Boolean = core.currentCall?.currentParams?.isVideoEnabled == true

    /**
     * Bild im laufenden Gespräch dazuschalten oder abschalten.
     * Liefert den neuen Zustand.
     */
    override fun videoUmschalten(): Boolean {
        val call = core.currentCall ?: return false
        val an = !(call.currentParams.isVideoEnabled)
        val params = core.createCallParams(call) ?: return false
        params.isVideoEnabled = an
        call.update(params)
        return an
    }

    /** Zwischen Front- und Rückkamera wechseln. */
    override fun kameraWechseln() {
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
    override fun videoFlaechen(fremd: TextureView?, eigen: TextureView?) {
        core.nativeVideoWindowId = fremd
        core.nativePreviewWindowId = eigen
    }

    /**
     * Annehmen – aber der Befehl kam vom SYSTEM, nicht aus unserer App.
     *
     * Lenkradtaste, Sperrbildschirm, Android Auto und die
     * Freisprecheinrichtung kommen alle über Telecom hier an. Getrennt
     * von [answer], weil hier NICHT zurückgemeldet werden darf: Das
     * System weiß es schon, sonst hätte es nicht gefragt – eine
     * Rückmeldung ergäbe eine Schleife.
     */
    override fun annehmenVomSystem() {
        answer()
    }

    /** Halten/Fortsetzen auf Geheiss des Systems (Auto, Bluetooth). */
    override fun setzeGehalten(gehalten: Boolean) {
        val call = core.currentCall ?: core.calls.firstOrNull() ?: return
        if (gehalten) {
            if (call.state != Call.State.Paused) call.pause()
        } else {
            if (call.state == Call.State.Paused) call.resume()
        }
    }

    override fun hangup() {
        if (core.callsNb > 0) {
            (core.currentCall ?: core.calls.firstOrNull())?.terminate()
        }
    }

    override fun toggleMute(): Boolean {
        core.isMicEnabled = !core.isMicEnabled
        return !core.isMicEnabled
    }

    /** Anruf halten/fortsetzen; liefert true = wird gehalten. */
    override fun toggleHold(): Boolean {
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
    override fun sendDtmf(digit: Char) {
        core.currentCall?.sendDtmf(digit)
    }

    /** Laufzeit des aktiven Anrufs in Sekunden. */
    override fun currentCallDuration(): Int = core.currentCall?.duration ?: 0

    /** Wechselt zwischen Hörmuschel und Lautsprecher; liefert true = Lautsprecher. */
    override fun toggleSpeaker(): Boolean {
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
