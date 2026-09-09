package de.dariatech.softphone

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Die Anlage, zu der diese App gehört – an EINER Stelle.
 *
 * DER ANLASS ist eine Ansage des Inhabers vom 30.08.2026: Die App ist
 * die App von DariaTech, nicht ein allgemeines Softphone. Alle
 * Serverdaten liegen im Hintergrund; der Nutzer trägt nur noch seinen
 * Benutzernamen und sein Passwort ein.
 *
 * WARUM DAS BESSER IST, nicht nur kürzer: Vorher stand hier eine Liste
 * von zehn Anbietervorlagen, ein freies Feld für die Serveradresse und
 * eine Auswahl für den Transport. Neun der zehn Vorlagen waren für
 * einen DariaTech-Kunden falsch, und der Hinweistext zur richtigen
 * Vorlage war zehn Zeilen lang – er war so lang, weil genau diese
 * Eingabe reihenweise schiefging (Portaladresse statt SIP-Adresse,
 * Cloudflare-Tunnel, Endpunktname statt SIP-Benutzername). Was man
 * nicht eintragen kann, kann man auch nicht falsch eintragen.
 *
 * WER HIER ETWAS ÄNDERT, ÄNDERT ES FÜR ALLE. Ein zweiter Ort mit
 * derselben Adresse driftet an einem von beiden; das Prüfstück
 * `anlage.mjs` lässt genau das scheitern.
 *
 * FÜR EINE EIGENE ANLAGE (Self-Hosting) ist das hier die einzige
 * Stelle, die ein eigener Bau anfassen muss – eine Zeile. Die App holt
 * sich die Adresse NICHT von irgendwoher; das wäre ein Weg, auf dem
 * jemand ein fremdes Ziel unterschieben könnte.
 */
object Anlage {
    /**
     * Der SIP-Server der DariaTech-Telefonanlage.
     *
     * ACHTUNG, DAS IST NICHT ZWANGSLÄUFIG DIE PORTALADRESSE: Wer das
     * Portal über einen Tunnel oder einen Zugangsschutz erreicht, gilt
     * das nur für die Weboberfläche – SIP läuft dort nicht durch.
     * Maßgeblich ist die Adresse, die das Portal bei den Nebenstellen
     * als SIP-Server anzeigt.
     *
     * UND GENAU DAS IST HIER PASSIERT. Bis zum 05.09.2026 stand hier
     * `pbx.dariatech.de` – der Name, unter dem die Anlage in den
     * Betriebsdokumenten geführt wird. Nachgemessen im Namensdienst:
     *
     *     pbx.dariatech.de → 104.21.14.53, 172.67.157.219,
     *                        2606:4700:3030::6815:e35
     *     sip.dariatech.de → 178.254.6.5
     *
     * Die erste Reihe gehört Cloudflare. Deren Proxy führt HTTP und
     * HTTPS weiter und sonst nichts – ein REGISTER über UDP kommt dort
     * nie an. Eine App mit diesem Namen kann sich nicht anmelden, und
     * der Fehler sieht für den Kunden aus wie ein Netzproblem bei ihm.
     *
     * Der SIP-Name ist `sip.dariatech.de`; so steht er auch im
     * Störungsprotokoll der Anlage (REGISTER von
     * <sip:…@sip.dariatech.de>). Wer die Anlage umzieht, ändert diesen
     * Namen im Namensdienst – nicht diese Zeile.
     *
     * DIESELBE ZEILE STEHT IN Anlage.swift der iOS-App. Wer hier
     * ändert, ändert dort mit.
     */
    /* UMGEZOGEN AM 08.09.2026 auf den neuen Hauptserver.
     *
     * DER VORFALL (Inhaber, 08.09.2026, mit Bildschirmfoto aus der
     * iOS-App – die Android-App trug denselben Namen): „Die App hat noch
     * keinen Ausweis von der Anlage." Kein eigenes Bild, keine Kontakte,
     * kein Chat, während die Telefonie längst über pbx.taamas.de lief.
     *
     * DER GRUND: Die App holt ihren Ausweis über
     * POST https://<server>:8443/token – und <server> war noch der ALTE
     * Rechner. Nachgemessen am selben Abend im Namensdienst:
     *
     *     sip.dariatech.de → 178.254.6.5    (alt, Asterisk dort AUS)
     *     pbx.taamas.de    → 31.70.137.58   (der neue Hauptserver)
     *
     * Der Inhaber hat den Namen am selben Tag festgelegt: „ich habe mich
     * für pbx.taamas.de entschieden für client für SIP anmeldung."
     * Altlasten vom alten Server werden ausdrücklich nicht übernommen.
     */
    const val SERVER = "pbx.taamas.de"

    /**
     * TLS – und nichts anderes. Seit dem 09.09.2026.
     *
     * DER AUFTRAG (Inhaber, nach einem Tag ohne Telefonie): „TLS muss
     * für die gesamte Anlage und alle Kunden verpflichtend sein, es
     * soll keine andere Wahl geben. Alles innerhalb der Anlage muss
     * zwangsläufig verschlüsselt sein."
     *
     * Bis dahin stand hier „TLS zuerst, nach acht Sekunden Rückfall auf
     * UDP" – mit einem Transportgedächtnis und einem Knopf „Trotzdem
     * unverschlüsselt anmelden". Die Anlage nimmt seit demselben Tag
     * jede Nebenstelle NUR noch über TLS an (transport=transport-tls
     * im Endpunkt). Ein Rückfall meldete sich damit ins Leere – die App
     * zeigte „abgemeldet", wo „TLS blockiert" die Wahrheit gewesen
     * wäre. Und er war vom Netz erzwingbar: Wer im selben WLAN Pakete
     * nach 5061 verwirft, hätte die App auf offenes UDP bekommen, samt
     * der SDES-Schlüssel im SDP.
     *
     * Kommt TLS nicht zustande, bleibt die App also unangemeldet und
     * sagt es. Das ist die Entscheidung des Inhabers – lieber sichtbar
     * nicht angemeldet als still offen.
     *
     * DIESELBE ZEILE STEHT IN Anlage.swift der iOS-App.
     */
    const val TRANSPORT = "TLS"

    /**
     * Der Dienst der Anlage für alles, was NICHT Telefonie ist:
     * Kollegen, Bilder, Kontakte, Faxe.
     *
     * EIGENER PORT, EIGENE VERSCHLÜSSELUNG. Er ist bewusst getrennt vom
     * Portal – das lauscht nur auf 127.0.0.1. Vorgabe ist 8443, und TLS
     * ist dort Pflicht: Ein Bearer-Token im Klartext über das
     * öffentliche Netz ist ein verschenktes Geheimnis, einmal
     * mitgelesen gilt es für immer (pbx/docs/CLIENT-API.md).
     *
     * DIESELBE ZEILE STEHT IN Anlage.swift der iOS-App.
     */
    const val DIENST = "https://$SERVER:8443"

    /** Was in den Einstellungen als Server dasteht – nur zum Ansehen. */
    val anzeige: String get() = "$SERVER ($TRANSPORT)"

    private const val ALT_SERVER = "domain"
    private const val ALT_TRANSPORT = "transport"
    private const val ALT_VORLAGE = "preset"

    /**
     * Einen Altbestand aufräumen – und ihn MELDEN, wenn er abwich.
     *
     * Wer die App vor dieser Änderung auf einen anderen Server gestellt
     * hatte, ruft ab jetzt woanders an. Das ist eine echte
     * Nebenwirkung, und eine Nebenwirkung wird nicht verschwiegen: Sie
     * steht im Protokoll, damit sie bei einer Störungssuche auffindbar
     * ist. Stillschweigend umzuschalten wäre die Sorte Fehler, die man
     * erst nach Stunden findet.
     *
     * Die alten Schlüssel werden danach entfernt – ein toter Wert in
     * den Einstellungen ist eine Falle für den Nächsten, der ihn liest
     * und für maßgeblich hält.
     */
    fun raeumeAltbestandAuf(prefs: SharedPreferences) {
        val alt = prefs.getString(ALT_SERVER, null)
        if (!alt.isNullOrBlank() && alt != SERVER) {
            Log.w(
                "Anlage",
                "Altbestand: In den Einstellungen stand der Server \"$alt\". " +
                    "Diese App ist auf $SERVER festgelegt und meldet sich ab jetzt " +
                    "dort an. Der alte Eintrag wird entfernt."
            )
        }
        if (prefs.contains(ALT_SERVER) || prefs.contains(ALT_TRANSPORT) ||
            prefs.contains(ALT_VORLAGE)
        ) {
            prefs.edit()
                .remove(ALT_SERVER)
                .remove(ALT_TRANSPORT)
                .remove(ALT_VORLAGE)
                .apply()
        }
    }
}

