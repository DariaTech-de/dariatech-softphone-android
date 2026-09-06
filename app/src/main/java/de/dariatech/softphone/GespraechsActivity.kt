package de.dariatech.softphone

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import de.dariatech.softphone.databinding.ActivityGespraechBinding

/**
 * Ein Gespräch mit einem Menschen des eigenen Hauses.
 *
 * DER AUFTRAG (Inhaber, 06.09.2026): „…die Mitarbeiter der gleichen
 * Organisation sich gegenseitig finden und chatten."
 *
 * EIN EIGENER BILDSCHIRM UND KEIN AUSKLAPPER: Wer schreibt, will den
 * ganzen Platz – die Tastatur nimmt schon die Hälfte. Zurück führt der
 * gewohnte Weg der Leiste, nicht ein selbstgebauter Knopf.
 */
class GespraechsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGespraechBinding
    private lateinit var blasen: BlasenAdapter
    private var wer = ""
    private var ich = ""

    companion object {
        const val ZIEL = "ziel"
        const val NAME = "name"
    }

    override fun onCreate(zustand: Bundle?) {
        super.onCreate(zustand)
        binding = ActivityGespraechBinding.inflate(layoutInflater)
        setContentView(binding.root)

        wer = intent.getStringExtra(ZIEL) ?: ""
        ich = Verzeichnis.ich(this)?.id ?: ""
        /* OHNE BEIDE KENNUNGEN GIBT ES KEIN GESPRÄCH. Das kommt vor –
           etwa wenn das Token abgelaufen ist, während der Bildschirm
           offen war. Dann zurück, statt eine leere Liste zu zeigen. */
        if (wer.isEmpty() || ich.isEmpty()) {
            finish()
            return
        }

        setSupportActionBar(binding.gespraechLeiste)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = intent.getStringExtra(NAME) ?: wer
        binding.gespraechLeiste.setNavigationOnClickListener { finish() }

        zeigeFingerabdruck()

        blasen = BlasenAdapter(ich)
        binding.blasenListe.layoutManager = LinearLayoutManager(this)
        binding.blasenListe.adapter = blasen

        binding.chatSenden.setOnClickListener { abschicken() }

        /* NEUE NACHRICHTEN LANDEN DIREKT IM BILDSCHIRM. Ohne diesen
           Haken sähe man die Antwort erst beim nächsten Öffnen. */
        Postfach.beiAenderung = { zeichneNeu() }
        zeichneNeu()
        Postfach.lade(this, erzwingen = true)
    }

    override fun onDestroy() {
        /* DEN HAKEN WIEDER LÖSEN. Ein Verweis auf einen abgebauten
           Bildschirm ist ein Leck und beim nächsten Zeichnen ein
           Absturz. */
        if (Postfach.beiAenderung != null) Postfach.beiAenderung = null
        super.onDestroy()
    }

    /**
     * Der Fingerabdruck des Gegenübers – unter seinem Namen.
     *
     * ER IST DER EINZIGE WEG, aus dem Verschlüsseln eine Gewissheit zu
     * machen: Wer ganz sicher sein will, liest ihn dem anderen am
     * Telefon vor. Stimmen beide überein, hat sich niemand
     * dazwischengeschoben. Ohne diese Möglichkeit ist Ende-zu-Ende ein
     * Versprechen der Anlage – und genau der wollte man ja nicht mehr
     * glauben müssen.
     *
     * BEI EINEM WECHSEL steht die Warnung statt des Fingerabdrucks:
     * „neu installiert" und „jemand schiebt sich dazwischen" sehen von
     * hier gleich aus, und deshalb entscheidet der Mensch, nicht die
     * App.
     */
    private fun zeigeFingerabdruck() {
        /* DER ANGEZEIGTE WERT WIRD GERECHNET, NICHT GEGLAUBT.

           Bis zum 06.09.2026 stand hier `it.fingerabdruck` – ein Feld
           aus der Antwort der Anlage –, während mit `it.schluessel`
           verschlüsselt wurde. Zwei Felder derselben Antwort, die
           nichts aneinander bindet: Wer die Anlage übernimmt, tauscht
           den Schlüssel, lässt den Fingerabdruck stehen, und die beiden
           lesen sich am Telefon dasselbe Wort vor, während er mitliest.
           Auch `gewechselt` kam von der Anlage – und ein NEUES Gerät ist
           per Definition nicht „gewechselt". Seitdem rechnet die App den
           Fingerabdruck aus GENAU DEM Schlüssel, mit dem sie gleich
           verschließt, und der Zustand kommt aus dem eigenen Gedächtnis. */
        val geraete = Verzeichnis.kollegen.firstOrNull { it.id == wer }?.geraete ?: emptyList()
        val unbestaetigt = geraete.firstOrNull {
            Schluesselgedaechtnis.zustand(this, wer, it.id, it.schluessel) !=
                Schluesselzustand.BEKANNT
        }
        supportActionBar?.subtitle = when {
            geraete.isEmpty() -> null
            unbestaetigt != null -> {
                val abdruck = Ende2Ende.fingerabdruck(unbestaetigt.schluessel)
                val neuesGeraet = Schluesselgedaechtnis.zustand(
                    this, wer, unbestaetigt.id, unbestaetigt.schluessel
                ) == Schluesselzustand.NEU
                getString(
                    if (neuesGeraet) R.string.chat_geraet_neu
                    else R.string.chat_schluessel_gewechselt,
                    abdruck
                )
            }
            else -> getString(
                R.string.chat_fingerabdruck,
                geraete.joinToString(", ") { Ende2Ende.fingerabdruck(it.schluessel) }
            )
        }
        /* BESTÄTIGT WIRD VON HAND – ein Tippen auf die Leiste. Würde
           die App das selbst tun, sobald sie den Schlüssel einmal
           gesehen hat, wäre das Gedächtnis eine Verzierung: Der
           Angreifer wäre nach einer Sekunde wieder unauffällig. */
        binding.gespraechLeiste.setOnClickListener {
            if (unbestaetigt != null) bestaetigeSchluessel()
        }
    }

    /**
     * „Der Fingerabdruck stimmt" – gedrückt vom Menschen.
     *
     * Erst danach gilt der Schlüssel als gesehen. Die Anlage bekommt es
     * auch gesagt, damit sie ihr eigenes Kennzeichen abräumt; maßgeblich
     * ist aber das Gedächtnis hier.
     */
    fun bestaetigeSchluessel() {
        val geraete = Verzeichnis.kollegen.firstOrNull { it.id == wer }?.geraete ?: emptyList()
        for (g in geraete) Schluesselgedaechtnis.merke(this, wer, g.id, g.schluessel)
        /* DER ANLAGE SAGEN WIR ES AUCH – aber auf einem eigenen Faden.
           `Dienst.bestaetigeWechsel` geht ins Netz, und ein Netzaufruf
           auf dem Hauptfaden ist unter Android eine
           NetworkOnMainThreadException. Massgeblich ist ohnehin das
           Gedaechtnis oben; die Anlage raeumt damit nur ihr eigenes
           Kennzeichen ab. */
        val app = applicationContext
        val kennungen = geraete.map { it.id }
        Thread { for (id in kennungen) Dienst.bestaetigeWechsel(app, id) }.start()
        zeigeFingerabdruck()
    }

    private fun zeichneNeu() {
        val verlauf = Postfach.gespraech(this, wer, ich)
        blasen.setze(verlauf)
        /* IMMER ANS ENDE. Ein Chat, der oben aufmacht, zwingt zum
           Wischen, bevor man das Neueste sieht. */
        if (verlauf.isNotEmpty()) binding.blasenListe.scrollToPosition(verlauf.size - 1)
    }

    private fun abschicken() {
        val text = binding.chatEingabe.text.toString()
        if (text.isBlank()) return
        binding.chatEingabe.setText("")
        binding.chatFehler.visibility = View.GONE
        Postfach.sende(this, wer, text, ich) { ok ->
            if (!ok) {
                /* DER TEXT KOMMT ZURÜCK INS FELD. Eine Nachricht, die
                   beim Senden verschwindet, ist schlimmer als eine, die
                   nicht ankommt: Man weiß nicht einmal mehr, was man
                   geschrieben hatte. */
                binding.chatEingabe.setText(text)
                binding.chatFehler.setText(R.string.chat_fehler_senden)
                binding.chatFehler.visibility = View.VISIBLE
            }
        }
    }
}
