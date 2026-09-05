package de.dariatech.softphone

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.concurrent.Executors

/**
 * Das Verzeichnis im Gerät: wer ruft da an, und wie sieht er aus?
 *
 * DER AUFTRAG (Inhaber, 05.09.2026): „…und wenn man sich gegenseitig
 * anruft, dass das Foto von dem Anruf angezeigt wird."
 *
 * DIE RÜCKWÄRTSSUCHE MACHT DIE APP SELBST. So steht es in
 * `pbx/docs/CLIENT-API.md`, und der Grund ist hart: Sie muss bei JEDEM
 * Anruf funktionieren, auch ohne Netz. Wer beim Klingeln erst den Server
 * fragt, zeigt den Namen nach dem dritten Klingeln – oder gar nicht.
 *
 * ZUM VERGLEICHEN WIRD NORMALISIERT, ZUM ANZEIGEN NICHT. „+49 831 555 12",
 * „0049831555 12" und „0831 55512" sind dieselbe Nummer; angezeigt wird
 * die Schreibweise, die der Mensch selbst gewählt hat – sie ist die, die
 * er wiedererkennt.
 *
 * BILDER LIEGEN IM GERÄT, benannt nach ihrer MARKE. Ändert sich das
 * Bild, ändert sich der Name – und nur dann wird geladen. Ein Telefon
 * fragt bei jedem Klingeln; ohne das lüde es dasselbe Bild hundertmal am
 * Tag, über Mobilfunk, auf Kosten des Kunden.
 *
 * DASSELBE GIBT ES AUF iOS (`Verzeichnis.swift`), mit denselben Regeln
 * und denselben Namen.
 */
object Verzeichnis {
    private val faden = Executors.newSingleThreadExecutor()
    private val hauptfaden = Handler(Looper.getMainLooper())

    @Volatile var kollegen: List<Kollege> = emptyList()
        private set
    @Volatile var kontakte: List<Kontakt> = emptyList()
        private set

    private val bilder = HashMap<String, Bitmap>()
    private var zuletzt = 0L

    /** Wird gerufen, wenn neue Daten da sind – die Liste zeichnet neu. */
    var beiAenderung: (() -> Unit)? = null

    /**
     * Nachladen – höchstens alle fünf Minuten, außer jemand besteht darauf.
     *
     * Ein Adressbuch ändert sich selten; die App startet aber oft. Ohne
     * diese Bremse fragte sie bei jedem Wechsel in den Bereich neu.
     */
    fun lade(context: Context, erzwingen: Boolean = false) {
        if (!Dienst.angemeldet(context)) return
        val jetzt = System.currentTimeMillis()
        if (!erzwingen && jetzt - zuletzt < 300_000) return
        zuletzt = jetzt
        val app = context.applicationContext
        faden.execute {
            val leute = Dienst.kollegen(app)
            val buch = Dienst.kontakte(app)
            /* NUR ERSETZEN, WENN ETWAS KAM. Eine leere Antwort nach einem
               Netzfehler würde sonst das Verzeichnis leeren – und beim
               nächsten Anruf stünde wieder eine nackte Nummer da, obwohl
               die Daten längst im Gerät waren. */
            if (leute.isNotEmpty()) kollegen = leute
            if (buch.isNotEmpty()) kontakte = buch
            for (k in leute) if (k.foto.isNotEmpty()) ladeBild(app, k)
            hauptfaden.post { beiAenderung?.invoke() }
        }
    }

    /** Alles vergessen – beim Abmelden. Auch die Bilder auf der Platte. */
    fun leere(context: Context) {
        kollegen = emptyList()
        kontakte = emptyList()
        synchronized(bilder) { bilder.clear() }
        zuletzt = 0L
        ordner(context).deleteRecursively()
        hauptfaden.post { beiAenderung?.invoke() }
    }

    // ---- Rückwärtssuche ----

    /**
     * Wer bin ich?
     *
     * DIE FRAGE IST NICHT TRIVIAL, und deshalb steht sie hier und nicht
     * an drei Stellen: Die App kennt ihren SIP-Benutzernamen, aber der
     * steht in keiner Kollegenliste – und seit die Namen unerratbar
     * sind (`nst-e492zth5a84g`), sagt er auch nichts mehr über den
     * Menschen. Die Anlage nennt beim Tausch des Tokens die interne
     * Nummer der Nebenstelle, und die steht bei genau einem Menschen
     * unter `nebenstellen`.
     *
     * Findet sich keiner, gehört dieser Apparat niemandem (ein
     * Konferenzraum, ein Türsprecher). Dann gibt es kein eigenes Bild,
     * und das ist keine Störung.
     *
     * Dieselbe Regel wie in der iPhone-App (Verzeichnis.ich).
     */
    fun ich(context: Context): Kollege? {
        val meine = Dienst.eigeneNebenstelle(context)
        if (meine.isEmpty()) return null
        return kollegen.firstOrNull { it.nebenstellen.contains(meine) }
    }

    /**
     * Wer ist das? Liefert Namen und – wenn es einen gibt – die Kennung
     * des Menschen, an der sein Bild hängt.
     *
     * DIE REIHENFOLGE IST DIE AUSSAGE: erst die eigenen Kollegen, dann
     * das Adressbuch. Wer intern anruft, ist ein Kollege; stünde
     * dieselbe Nummer zufällig auch im Adressbuch, wäre der Kollege die
     * bessere Auskunft – er hat ein Gesicht.
     */
    fun wer(nummer: String): Pair<String, String?>? {
        val gesucht = normalisiere(nummer)
        if (gesucht.isEmpty()) return null
        for (k in kollegen) {
            if (k.nebenstellen.contains(nummer)) return k.name to k.id
            if (k.durchwahl.isNotEmpty() && k.durchwahl == nummer) return k.name to k.id
            if (k.nummer.isNotEmpty() && normalisiere(k.nummer) == gesucht) return k.name to k.id
        }
        for (e in kontakte) {
            for (n in e.nummern) {
                if (normalisiere(n.nummer) == gesucht) {
                    val wer = if (e.firma.isEmpty()) e.name else "${e.name} · ${e.firma}"
                    return wer to null
                }
            }
        }
        return null
    }

    /**
     * Zwei Nummern vergleichbar machen.
     *
     * +49, 0049 und die führende 0 treffen dasselbe. Alles, was keine
     * Ziffer ist, fliegt raus – Leerzeichen, Bindestriche, Klammern,
     * Schrägstriche schreibt jeder anders.
     */
    fun normalisiere(roh: String): String {
        var z = roh.filter { it.isDigit() || it == '+' }
        if (z.startsWith("+")) z = "00" + z.drop(1)
        z = z.filter { it.isDigit() }
        if (z.startsWith("0049")) z = "0" + z.drop(4)
        return z
    }

    // ---- Bilder ----

    private fun ordner(context: Context) = File(context.cacheDir, "gesichter")

    /** Das Bild eines Menschen, falls schon geladen. */
    fun bild(benutzerId: String?): Bitmap? {
        if (benutzerId == null) return null
        synchronized(bilder) { return bilder[benutzerId] }
    }

    private fun ladeBild(context: Context, k: Kollege) {
        val ziel = File(ordner(context), "${k.id}-${k.foto}.jpg")
        /* SCHON DA? Dann nichts holen. Der Dateiname trägt die Marke –
           ändert sich das Bild, ändert sich der Name, und der alte
           Eintrag wird nie wieder gefunden. */
        if (ziel.exists()) {
            BitmapFactory.decodeFile(ziel.absolutePath)?.let {
                synchronized(bilder) { bilder[k.id] = it }
            }
            return
        }
        val daten = Dienst.foto(context, k.id) ?: return
        val bild = BitmapFactory.decodeByteArray(daten, 0, daten.size) ?: return
        ordner(context).mkdirs()
        ziel.writeBytes(daten)
        synchronized(bilder) { bilder[k.id] = bild }
    }

    /**
     * Zwei Buchstaben aus einem Namen – oder ein Punkt.
     *
     * OHNE BILD BLEIBEN DIE INITIALEN. Ein leerer grauer Kreis sieht aus
     * wie ein Bild, das nicht geladen hat; zwei Buchstaben sehen aus wie
     * eine Entscheidung.
     */
    fun kuerzel(name: String): String {
        val teile = name.split(' ', '.', '-', '_', '·').filter { it.isNotEmpty() }
        return when {
            teile.size >= 2 -> "${teile[0].first()}${teile[1].first()}".uppercase()
            teile.size == 1 -> teile[0].take(2).uppercase()
            else -> "·"
        }
    }
}
