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
