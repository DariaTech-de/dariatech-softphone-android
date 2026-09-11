package de.dariatech.softphone

import android.content.Context
import android.os.Build
import android.telecom.DisconnectCause
import androidx.annotation.RequiresApi
import androidx.core.telecom.CallAttributesCompat
import android.net.Uri
import androidx.core.telecom.CallControlScope
import androidx.core.telecom.CallsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Der Anruf gehört dem SYSTEM, nicht nur dieser App.
 *
 * DER AUFTRAG (Inhaber, 06.09.2026): „Beide Apps müssen Apple Car und
 * Android Car unterstützen." Das hier ist Etappe 1 aus
 * docs/AUFTRAG-AUTO.md – und es ist die Etappe, ohne die alle weiteren
 * sinnlos wären.
 *
 * DER BEFUND. Die App hatte CallStyle-Meldungen, aber keinen
 * `PhoneAccount`. Ein Anruf lebte damit ausschließlich in ihrer eigenen
 * Oberfläche – genau der Zustand, in dem die iOS-App vor CallKit war
 * („Ich bekomme immer noch keine Anrufe, wenn das Handy gesperrt ist").
 * Die Folgen reichen weit über das Auto hinaus:
 *
 *   * In Android Auto ist die App unsichtbar. Google verlangt die
 *     Telecom-Anbindung ausdrücklich „zu jeder Zeit, nicht nur wenn
 *     Android Auto läuft".
 *   * An jeder Freisprecheinrichtung dasselbe: Die Lenkradtaste nimmt
 *     nichts an, weil das System von dem Anruf nichts weiß.
 *   * Kommt während eines DariaTech-Gesprächs ein GSM-Anruf, weiß
 *     keiner der beiden vom anderen – zwei Anrufe auf einem
 *     Lautsprecher.
 *
 * WARUM SELF-MANAGED. Unsere Anrufe gehören nicht in die Anrufliste des
 * Telefon-Wählers, und fremde Anrufe nicht in unsere Oberfläche. Genau
 * dafür gibt es `CAPABILITY_SUPPORTS_VIDEO_CALLING` zusammen mit dem
 * selbstverwalteten Konto (ab API 26 – unser minSdk ist 26).
 *
 * WAS HIER NICHT PASSIERT: Der NOTRUF. 112 und 110 gehen über den
 * Wähler des Telefons, wie immer. Eine Notrufnummer durch eine eigene
 * Anrufverwaltung zu leiten, wäre die schlechteste Idee dieses
 * Projekts – deshalb kommt in dieser Datei keine Notrufnummer vor.
 */
object Autoanruf {

    /** Steuerung des laufenden Anrufs, vom System her. */
    private var steuerung: CallControlScope? = null

    /**
     * Der Faden, auf dem die Anrufe laufen.
     *
     * `CallsManager.addCall` ist eine anhaltende Funktion (suspend) und
     * läuft, SOLANGE der Anruf steht. Sie gehört deshalb nicht auf den
     * Hauptfaden und auch nicht in den Gültigkeitsbereich einer
     * Activity: Ein Anruf überlebt jeden Bildschirm.
     */
    private val faden = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var manager: CallsManager? = null

    /**
     * Einmalig beim Start: die App beim System als Telefon anmelden.
     *
     * IMMER, nicht nur im Auto. Eine App, die den Telecom-Weg nur
     * manchmal benutzt, hinterlässt Geisteranrufe im System – und
     * Google weist sie aus demselben Grund zurück.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun melde(context: Context) {
        if (manager != null) return
        val m = CallsManager(context.applicationContext)
        m.registerAppWithTelecom(
            CallsManager.CAPABILITY_BASELINE or
                CallsManager.CAPABILITY_SUPPORTS_VIDEO_CALLING
        )
        manager = m
    }

    /** Ein Anruf kommt herein – dem System melden, damit es klingelt. */
    @RequiresApi(Build.VERSION_CODES.O)
    fun kommtAn(context: Context, nummer: String, name: String) {
        starte(context, nummer, name, CallAttributesCompat.DIRECTION_INCOMING)
    }

    /** Ein Anruf geht hinaus – dem System melden, damit das Auto ihn zeigt. */
    @RequiresApi(Build.VERSION_CODES.O)
    fun gehtRaus(context: Context, nummer: String, name: String) {
        starte(context, nummer, name, CallAttributesCompat.DIRECTION_OUTGOING)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun starte(context: Context, nummer: String, name: String, richtung: Int) {
        melde(context)
        val m = manager ?: return
        beendet()
        val merkmale = CallAttributesCompat(
            displayName = if (name.isNotBlank()) name else nummer,
            address = Uri.fromParts("sip", nummer, null),
            direction = richtung,
            callType = CallAttributesCompat.CALL_TYPE_AUDIO_CALL,
            callCapabilities = CallAttributesCompat.SUPPORTS_SET_INACTIVE
        )
        faden.launch {
            try {
                /* `addCall` HÄLT AN, solange der Anruf steht, und kehrt
                   zurück, wenn er vorbei ist. Der Block bekommt nur die
                   Steuerung in die Hand – beendet wird über
                   `disconnect`, nicht dadurch, dass der Block
                   zurückkehrt. Das war der erste Irrtum beim Bau
                   dieser Datei, und der Übersetzer hat ihn gemeldet. */
                m.addCall(
                    callAttributes = merkmale,
                    // DAS AUTO HAT ANGENOMMEN. Lenkradtaste,
                    // Sperrbildschirm und Android Auto kommen alle hier
                    // an – der Kern erfährt es erst dadurch.
                    onAnswer = { _ -> Telefonkern.aktiv.annehmenVomSystem() },
                    onDisconnect = { _ -> Telefonkern.aktiv.hangup() },
                    onSetActive = { Telefonkern.aktiv.setzeGehalten(false) },
                    // Das Auto hält das Gespräch, weil ein GSM-Anruf
                    // hereinkommt.
                    onSetInactive = { Telefonkern.aktiv.setzeGehalten(true) }
                ) {
                    steuerung = this
                }
            } catch (e: Exception) {
                android.util.Log.w("Autoanruf", "Telecom nahm den Anruf nicht: ${e.message}")
            } finally {
                steuerung = null
            }
        }
    }

    /**
     * Der Anruf ist vorbei – dem System sagen, dass es aufräumen kann.
     *
     * OHNE DIESE ZEILE bleibt im Auto und auf dem Sperrbildschirm ein
     * Anruf stehen, den es nicht mehr gibt. Genau dieser Fehler ist der
     * Grund, warum Google verlangt, dass die Anbindung immer läuft.
     */
    fun beendet() {
        val s = steuerung ?: return
        steuerung = null
        faden.launch {
            runCatching { s.disconnect(DisconnectCause(DisconnectCause.LOCAL)) }
        }
    }

    /** Das Gespräch steht – dem System melden, damit die Uhr läuft. */
    fun verbunden() {
        val s = steuerung ?: return
        faden.launch { runCatching { s.setActive() } }
    }
}
