package de.dariatech.softphone

import android.content.Context
import android.view.TextureView

/**
 * Die Naht zum Telefonie-Kern – EINE Schnittstelle, hinter der der
 * Motor steckt.
 *
 * DER ANLASS (11.09.2026): Der Kern wird getauscht. Liblinphone steht
 * unter AGPLv3; für eine geschlossen verteilte App hieße das
 * Offenlegung. Der Ersatz ist libre/baresip (BSD) – siehe
 * docs/KERNWECHSEL.md im Repository der Anlage.
 *
 * Ein Kern lässt sich nur tauschen, wenn der Rest der App ihn nicht
 * kennt. Beim Nachsehen kannte er ihn: MainActivity und Telefondienst
 * importierten `org.linphone.core` direkt – Zustände, das Anrufobjekt,
 * den Transport. Jede dieser Stellen wäre beim Tausch eine zweite
 * Baustelle gewesen. Deshalb hier EIGENE Typen: Was die Oberfläche
 * wissen muss, steht in dieser Datei, und nichts davon heißt wie in
 * einer Bibliothek.
 *
 * DIE BAUFORM: erst die Naht, dann der Motor. [LinphoneManager] ist die
 * erste Implementierung – ohne Verhaltensänderung. Der neue Kern wird
 * die zweite; [aktiv] entscheidet, welcher läuft. So gibt es keinen
 * Tag, an dem die App nicht telefoniert, und die AGPL fliegt erst,
 * wenn der neue Kern alles kann, was der alte kann.
 *
 * Das Prüfstück pruefstuecke/kern-naht.mjs hält fest: kein
 * `org.linphone` ausserhalb der Kerne, jeder Aufruf der App hat hier
 * sein Gegenstück, und die Oberfläche redet nur über [aktiv].
 */
interface Telefonkern {

    /** Was die Oberfläche über die Anmeldung wissen muss – nicht mehr. */
    interface Zuhoerer {
        fun beiAnmeldung(zustand: Anmeldezustand, meldung: String)
        fun beiAnruf(gegenstelle: Gegenstelle, zustand: Anrufzustand, meldung: String)
    }

    var zuhoerer: Zuhoerer?

    fun init(context: Context)

    /**
     * Anmelden – NUR über TLS. Es gibt keinen Transport-Parameter mehr:
     * Seit dem 09.09.2026 nimmt die Anlage nichts anderes an, und ein
     * Parameter, der nur einen Wert haben darf, ist eine Einladung zum
     * Fehler.
     */
    fun login(username: String, password: String, domain: String)
    fun neuAnmelden()
    fun vordergrund()
    fun istRegistriert(): Boolean

    fun call(number: String, mitVideo: Boolean = false)
    fun istKollege(nummer: String): Boolean
    fun answer()
    fun annehmenVomSystem()
    fun hangup()

    fun sicherheitswort(): String?
    fun gespraechVerschluesselt(): Boolean
    fun sicherheitswortBestaetigt(): Boolean
    fun bestaetigeSicherheitswort()

    fun gegenstelleMitVideo(): Boolean
    fun videoLaeuft(): Boolean
    fun videoUmschalten(): Boolean
    fun kameraWechseln()
    fun videoFlaechen(fremd: TextureView?, eigen: TextureView?)

    fun setzeGehalten(gehalten: Boolean)
    fun toggleMute(): Boolean
    fun toggleHold(): Boolean
    fun toggleSpeaker(): Boolean
    fun sendDtmf(digit: Char)
    fun currentCallDuration(): Int

    companion object {
        /**
         * Der Schalter. Heute Liblinphone; beim Kernwechsel kommt hier
         * der neue Kern hin – an genau EINER Stelle, nicht in jeder
         * Datei, die telefoniert.
         */
        val aktiv: Telefonkern get() = LinphoneManager
    }
}

/** Die Anmeldung, wie die Oberfläche sie sieht. */
enum class Anmeldezustand { LAEUFT, ANGEMELDET, FEHLGESCHLAGEN, ABGEMELDET }

/**
 * Ein Anruf, wie die Oberfläche ihn sieht. Bewusst grob: Die Oberfläche
 * unterscheidet genau diese fünf Lagen; jede feinere Stufe des Kerns
 * wird hier zusammengefasst.
 */
enum class Anrufzustand { KLINGELT_HEREIN, RUFT_HINAUS, VERBUNDEN, GEAENDERT, BEENDET, SONST }

/** Wer am anderen Ende ist – ohne ein Anrufobjekt der Bibliothek. */
data class Gegenstelle(val nummer: String, val anzeigename: String?, val adresse: String)
