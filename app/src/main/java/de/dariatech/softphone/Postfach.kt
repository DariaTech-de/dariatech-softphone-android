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
            /* DURCH DEN TRESOR. Bis zum 06.09.2026 lag der Verlauf hier
               als gewoehnliche JSON-Datei in filesDir – lesbar fuer
               jeden, der das Geraet gerootet hat oder eine Sicherung
               ausliest. Auf einem Diensthandy, das weitergegeben wird,
               ist das der wahrscheinlichste Fall. */
            val roh = Tresor.lies(context, datei(context))?.toString(Charsets.UTF_8) ?: return
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
        for (roh in neue) if (bekannt.add(roh.id)) zusammen.add(geoeffnet(context, roh))
        alle = zusammen.sortedBy { it.zeit }.takeLast(HOECHSTENS)
        sichere(context)
    }

    /**
     * Eine Ende-zu-Ende-Nachricht aufmachen.
     *
     * Gesucht wird der Umschlag für DIESES Gerät und der öffentliche
     * Schlüssel GENAU des Geräts, mit dem der Absender geschrieben hat –
     * nicht irgendeines seiner Geräte.
     *
     * BESTANDSSCHUTZ: Eine Nachricht ohne Umschläge geht unverändert
     * durch. Eine ältere App, die noch Klartext schickt, wird nicht
     * unsichtbar.
     *
     * GEHT ES NICHT AUF, bleibt ein deutscher Satz stehen statt
     * Kauderwelsch. Der häufigste Grund ist harmlos und gehört gesagt:
     * Der Absender hat verschlüsselt, bevor DIESES Gerät seinen
     * Schlüssel angemeldet hatte.
     */
    private fun geoeffnet(context: Context, n: Nachricht): Nachricht {
        if (n.umschlaege.isEmpty()) return n
        val meiner = n.umschlaege[Ende2Ende.geraetId(context)]
            ?: return n.copy(
                text = "Diese Nachricht war für dieses Gerät nicht bestimmt – " +
                    "sie kam an, bevor es seinen Schlüssel angemeldet hatte."
            )
        val absender = Verzeichnis.kollegen.firstOrNull { it.id == n.von }
        val geraet = absender?.geraete?.firstOrNull { it.id == n.absenderGeraet }
            ?: return n.copy(text = "Diese Nachricht ließ sich nicht entschlüsseln.")
        val klar = Ende2Ende.oeffne(context, meiner, geraet.schluessel)
            ?: return n.copy(text = "Diese Nachricht ließ sich nicht entschlüsseln.")
        return n.copy(text = klar)
    }

    /**
     * Für welche Geräte wird verschlüsselt?
     *
     * Die des Empfängers UND DIE EIGENEN ANDEREN. Ohne die zweiten
     * stünde die eigene Nachricht auf dem eigenen Rechner nicht im
     * Verlauf – man schriebe ins Leere und sähe es erst beim
     * Gerätewechsel.
     *
     * Kommt nichts zusammen (ältere Anlage ohne Schlüsselverzeichnis,
     * Kollege ohne App), bleibt die Liste leer: Dann geht die Nachricht
     * wie bisher als Text. Ausgesperrt wird niemand.
     */
    private fun empfaengergeraete(context: Context, an: String, ich: String): List<Geraeteschluessel> {
        val kollegen = Verzeichnis.kollegen
        val ziel = kollegen.firstOrNull { it.id == an }?.geraete ?: emptyList()
        val eigene = kollegen.firstOrNull { it.id == ich }?.geraete ?: emptyList()
        return ziel + eigene
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
            /* VERSCHLÜSSELT, WENN ES GEHT. Die Anlage bekommt dann kein
               Wort des Textes zu sehen – siehe Ende2Ende. */
            val umschlaege = Ende2Ende.verschliesse(
                app, sauber, empfaengergeraete(app, an, ich)
            )
            val echt = Dienst.sendeNachricht(app, an, sauber, umschlaege)
            alle = alle.filter { it.id != vorlaeufig.id }
            /* DER EIGENE TEXT BLEIBT LOKAL STEHEN. Die Anlage gibt eine
               verschlüsselte Nachricht ohne Text zurück – sie hat ihn
               nie gesehen. Würde man das übernehmen, verschwände das
               eigene Wort eine Sekunde nach dem Abschicken. */
            val meine = if (echt != null && echt.text.isEmpty()) echt.copy(text = sauber) else echt
            if (meine != null) haengeAn(app, listOf(meine)) else sichere(app)
            hauptfaden.post {
                beiAenderung?.invoke()
                fertig(meine != null)
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
        /* UND DAS SCHLÜSSELPAAR DIESES GERÄTS. Bliebe es liegen,
           könnte der Nächste am selben Gerät die Post des Vorigen
           öffnen, sobald sie nachgeladen wird. */
        Ende2Ende.vergiss(context)
        Schluesselgedaechtnis.vergiss(context)
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
            Tresor.schreibe(context, datei(context), liste.toString().toByteArray())
        } catch (e: Exception) {
            /* EIN FEHLGESCHLAGENES SICHERN DARF DEN CHAT NICHT ANHALTEN.
               Die Nachrichten liegen auf der Anlage; hier geht nur der
               Vorrat für den nächsten Start verloren. */
            Log.w(TAG, "Nachrichten nicht gesichert: ${e.message}")
        }
    }
}
