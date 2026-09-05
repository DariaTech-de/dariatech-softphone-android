package de.dariatech.softphone

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject

/**
 * Die Nachrichten im Gerät – damit der Chat auch ohne Netz etwas zeigt.
 *
 * DER AUFTRAG (Inhaber, 06.09.2026): „…die Mitarbeiter der gleichen
 * Organisation sich gegenseitig finden und chatten."
 *
 * WARUM ÜBERHAUPT EINE ABLAGE IM GERÄT, wo doch alles auf der Anlage
 * liegt: Wer den Bereich öffnet und erst eine Antwort aus dem Netz
 * abwarten muss, sieht eine leere Liste – im Aufzug, im Zug, im Keller
 * dauerhaft. Ein Chatverlauf, der bei schlechtem Netz verschwindet,
 * wirkt wie ein Datenverlust, und niemand traut ihm danach noch etwas
 * an.
 *
 * GEHOLT WIRD NUR NEUES. Die Ablage merkt sich die Zeit der jüngsten
 * Nachricht und fragt ab dort. Ohne das lüde das Telefon bei jeder
 * Frage den ganzen Verlauf – über Mobilfunk, auf Kosten des Kunden.
 *
 * SIE GEHÖRT DEM ANGEMELDETEN MENSCHEN. Beim Abmelden wird sie
 * gelöscht; sonst liest der Nächste am selben Gerät die Nachrichten
 * seines Vorgängers.
 *
 * DASSELBE GIBT ES AUF iOS (`Postfach.swift`), mit denselben Regeln.
 */
object Postfach {
    private const val TAG = "Postfach"
    private const val DATEI = "nachrichten.json"
    /** Dieselbe Grenze wie in der Anlage – ein Speicher ohne Boden ist auch hier ein Fehler. */
    private const val HOECHSTENS = 2000

    private val faden = Executors.newSingleThreadExecutor()
    private val hauptfaden = Handler(Looper.getMainLooper())

    @Volatile private var alle: List<Nachricht> = emptyList()
    private var geladen = false
    private var zuletzt = 0L

    /** Wird gerufen, wenn sich etwas geändert hat – die Liste zeichnet neu. */
    var beiAenderung: (() -> Unit)? = null

    private fun datei(context: Context) = File(context.filesDir, DATEI)

    /** Alles, was das Gerät kennt – nach Zeit sortiert. */
    fun alle(context: Context): List<Nachricht> {
        ladeVonPlatte(context)
        return alle
    }

    private fun ladeVonPlatte(context: Context) {
        if (geladen) return
        geladen = true
        alle = try {
            val roh = datei(context).takeIf { it.exists() }?.readText() ?: return
            val liste = JSONArray(roh)
            (0 until liste.length()).map { Nachricht.aus(liste.getJSONObject(it)) }
        } catch (e: Exception) {
            Log.w(TAG, "Nachrichten im Gerät unlesbar: ${e.message}")
            emptyList()
        }
    }

    /**
     * Nachschauen, ob etwas Neues da ist.
     *
     * Die Bremse ist absichtlich kurz (10 Sekunden): Ein Chat, der eine
     * Minute braucht, ist kein Chat. Sie ist trotzdem da, weil ein
     * Bereichswechsel mehrmals hintereinander vorkommt.
     */
    fun lade(context: Context, erzwingen: Boolean = false) {
        if (!Dienst.angemeldet(context)) return
        val jetzt = System.currentTimeMillis()
        if (!erzwingen && jetzt - zuletzt < 10_000) return
        zuletzt = jetzt
        val app = context.applicationContext
        faden.execute {
            ladeVonPlatte(app)
            val neue = Dienst.nachrichten(app, alle.lastOrNull()?.zeit ?: 0L)
            if (neue.isEmpty()) return@execute
            haengeAn(app, neue)
            hauptfaden.post { beiAenderung?.invoke() }
        }
    }

    /**
     * Neue Nachrichten einsortieren.
     *
     * DOPPELTE FLIEGEN RAUS: Eine eigene Nachricht steht schon da, bevor
     * die Anlage antwortet (siehe `sende`) – ohne diese Prüfung stünde
     * sie beim nächsten Abruf ein zweites Mal.
     */
    @Synchronized
    fun haengeAn(context: Context, neue: List<Nachricht>) {
        val bekannt = alle.map { it.id }.toMutableSet()
        val zusammen = alle.toMutableList()
        for (n in neue) if (bekannt.add(n.id)) zusammen.add(n)
        alle = zusammen.sortedBy { it.zeit }.takeLast(HOECHSTENS)
        sichere(context)
    }

    /**
     * Eine Nachricht abschicken – im Hintergrundfaden.
     *
     * SIE STEHT SOFORT DA, nicht erst nach der Antwort der Anlage. Wer
     * tippt und danach eine Sekunde auf sein eigenes Wort wartet, hält
     * die App für kaputt. Kommt die Anlage nicht mit, wird sie wieder
     * entfernt und `fertig(false)` gemeldet – dann kann der Bildschirm
     * den Text zurück ins Feld setzen, statt ihn zu verlieren.
     */
    fun sende(context: Context, an: String, text: String, ich: String, fertig: (Boolean) -> Unit) {
        val sauber = text.trim()
        if (sauber.isEmpty()) return fertig(false)
        val app = context.applicationContext
        val vorlaeufig = Nachricht(
            id = "lokal-${System.nanoTime()}",
            von = ich, an = an, text = sauber,
            zeit = System.currentTimeMillis()
        )
        haengeAn(app, listOf(vorlaeufig))
        beiAenderung?.invoke()
        faden.execute {
            val echt = Dienst.sendeNachricht(app, an, sauber)
            alle = alle.filter { it.id != vorlaeufig.id }
            if (echt != null) haengeAn(app, listOf(echt)) else sichere(app)
            hauptfaden.post {
                beiAenderung?.invoke()
                fertig(echt != null)
            }
        }
    }

    /** Ein Gespräch mit einem Menschen, in der Reihenfolge der Zeit. */
    fun gespraech(context: Context, mit: String, ich: String): List<Nachricht> =
        alle(context).filter {
            (it.von == mit && it.an == ich) || (it.von == ich && it.an == mit)
        }

    /** Die jüngste Nachricht mit einem Menschen – für die Vorschau in der Liste. */
    fun letzte(context: Context, mit: String, ich: String): Nachricht? =
        gespraech(context, mit, ich).lastOrNull()

    /** Beim Abmelden: alles weg. */
    fun leere(context: Context) {
        alle = emptyList()
        zuletzt = 0L
        geladen = true
        datei(context).delete()
    }

    private fun sichere(context: Context) {
        try {
            val liste = JSONArray()
            for (n in alle) {
                liste.put(
                    JSONObject()
                        .put("id", n.id).put("von", n.von).put("an", n.an)
                        .put("text", n.text).put("zeit", n.zeit)
                )
            }
            datei(context).writeText(liste.toString())
        } catch (e: Exception) {
            /* EIN FEHLGESCHLAGENES SICHERN DARF DEN CHAT NICHT ANHALTEN.
               Die Nachrichten liegen auf der Anlage; hier geht nur der
               Vorrat für den nächsten Start verloren. */
            Log.w(TAG, "Nachrichten nicht gesichert: ${e.message}")
        }
    }
}
