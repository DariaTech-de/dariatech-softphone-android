package de.dariatech.softphone

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Die Liste im Bereich „Kontakte": erst die Kollegen, dann das
 * Adressbuch.
 *
 * ZWEI ABSCHNITTE, NICHT EINE LISTE. Die Kollegen sind der häufigere
 * Fall und haben ein Gesicht; das Adressbuch ist länger und hat oft
 * mehrere Nummern je Eintrag. Beides in einen Topf zu werfen hieße, den
 * häufigen Fall im seltenen zu vergraben. Dieselbe Aufteilung wie auf
 * iOS (`Kontakte.swift`).
 *
 * EIN DRUCK WÄHLT – IM ADRESSBUCH. Wer dort jemanden antippt, will
 * telefonieren, nicht erst eine Karte öffnen, in der noch einmal ein
 * Hörer steht.
 *
 * BEI KOLLEGEN IST ES SEIT DEM 09.09.2026 ANDERS, und das ist eine
 * bewusste Abweichung: Ein Druck öffnet SEIN PROFIL, der Hörer rechts
 * wählt. Der Auftrag des Inhabers nannte WhatsApp und Teams als
 * Vorbild, und dort ist es genau so. Ein Kollege hat jetzt etwas zu
 * zeigen – Position, Abteilung, Durchwahl, Fax, E-Mail –, ein Eintrag
 * aus dem Adressbuch des Telefons nicht. Der Hörer steht in beiden
 * Fällen an derselben Stelle: EIN Druck telefoniert weiterhin.
 */
class KontakteAdapter(
    private val beimAnruf: (String) -> Unit,
    /** Wird bei einem KOLLEGEN gerufen – das Adressbuch hat kein Profil. */
    private val beimProfil: ((Kollege) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    /** Eine Zeile – entweder eine Überschrift oder ein Eintrag. */
    sealed class Zeile {
        data class Ueberschrift(val text: String) : Zeile()
        data class Eintrag(
            val name: String,
            val unten: String,
            val benutzerId: String?,
            val ziel: String,
            /** Gesetzt nur bei Kollegen – nur die haben ein Profil. */
            val kollege: Kollege? = null
        ) : Zeile()
    }

    private var zeilen: List<Zeile> = emptyList()

    /**
     * Die Liste neu aufbauen.
     *
     * WEN RUFT MAN BEI EINEM KOLLEGEN AN? SEINE DURCHWAHL, wenn er eine
     * hat – dann klingeln ALLE seine Geräte. Sonst die erste
     * Nebenstelle, also ein einzelnes Gerät. Wer das umdreht, erwischt
     * den Menschen nur, wenn er zufällig am richtigen Apparat sitzt.
     */
    fun setze(kollegen: List<Kollege>, kontakte: List<Kontakt>) {
        val neu = ArrayList<Zeile>()
        if (kollegen.isNotEmpty()) {
            neu.add(Zeile.Ueberschrift("Kollegen"))
            for (k in kollegen.sortedBy { it.name.lowercase() }) {
                neu.add(
                    Zeile.Eintrag(
                        name = k.name,
                        unten = untenZeile(k),
                        benutzerId = k.id,
                        ziel = Profil.ruf(k),
                        kollege = k
                    )
                )
            }
        }
        if (kontakte.isNotEmpty()) {
            neu.add(Zeile.Ueberschrift("Adressbuch"))
            for (e in kontakte.sortedBy { it.name.lowercase() }) {
                val wer = if (e.firma.isEmpty()) e.name else "${e.name} · ${e.firma}"
                for (n in e.nummern) {
                    neu.add(
                        Zeile.Eintrag(
                            name = wer,
                            unten = if (n.art.isEmpty()) n.nummer else "${artName(n.art)} · ${n.nummer}",
                            benutzerId = null,
                            ziel = n.nummer
                        )
                    )
                }
            }
        }
        zeilen = neu
        notifyDataSetChanged()
    }

    /**
     * Was unter dem Namen eines Kollegen steht.
     *
     * SEIT DEM 09.09.2026 POSITION UND ABTEILUNG, wenn sie gepflegt
     * sind. Vorher stand dort „Durchwahl 111" – eine Zahl, die man in
     * der Zeile nicht braucht: Wer telefonieren will, drückt den Hörer,
     * und wer die Nummer sehen will, öffnet das Profil. Was man an
     * dieser Stelle wirklich braucht, ist die Antwort auf „wer ist
     * das?" (Auftrag des Inhabers: „um in Apps nach der mitarbeiter zu
     * suchen").
     *
     * Ist nichts gepflegt, bleibt es bei der Durchwahl – eine leere
     * zweite Zeile sähe aus, als fehlte etwas.
     *
     * AUSSER DIENST steht VORAN. Wer das übersieht, ruft dreimal an.
     */
    private fun untenZeile(k: Kollege): String {
        val rolle = listOf(k.position, k.abteilung).filter { it.isNotEmpty() }.joinToString(" · ")
        val kern = when {
            rolle.isNotEmpty() -> rolle
            k.durchwahl.isNotEmpty() -> "Durchwahl ${k.durchwahl}"
            else -> k.nummer
        }
        return if (k.ausserDienst) "Außer Dienst · $kern" else kern
    }

    private fun artName(art: String) = when (art) {
        "buero" -> "Büro"
        "mobil" -> "Mobil"
        "privat" -> "Privat"
        "fax" -> "Fax"
        else -> "Sonstige"
    }

    override fun getItemViewType(position: Int) =
        if (zeilen[position] is Zeile.Ueberschrift) 0 else 1

    override fun getItemCount() = zeilen.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val luft = LayoutInflater.from(parent.context)
        return if (viewType == 0) {
            val t = TextView(parent.context).apply {
                setPadding(48, 28, 48, 10)
                textSize = 13f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            KopfHalter(t)
        } else {
            EintragHalter(luft.inflate(R.layout.zeile_kontakt, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val z = zeilen[position]) {
            is Zeile.Ueberschrift -> (holder as KopfHalter).text.text = z.text
            is Zeile.Eintrag -> {
                val h = holder as EintragHalter
                h.name.text = z.name
                h.unten.text = z.unten
                h.unten.visibility = if (z.unten.isEmpty()) View.GONE else View.VISIBLE
                /* BILD ODER INITIALEN – immer genau eines. Ein leerer
                   Kreis sieht aus wie ein Bild, das nicht geladen hat. */
                val bild = Verzeichnis.bild(z.benutzerId)
                if (bild != null) {
                    h.foto.setImageBitmap(bild)
                    h.foto.visibility = View.VISIBLE
                    h.kuerzel.visibility = View.INVISIBLE
                } else {
                    h.foto.visibility = View.GONE
                    h.kuerzel.visibility = View.VISIBLE
                    h.kuerzel.text = Verzeichnis.kuerzel(z.name)
                }
                val waehlen = View.OnClickListener { beimAnruf(z.ziel) }
                /* Kollege → Profil, Adressbuch → wählen. Die Begründung
                   steht oben am Kopf der Datei. Der Hörer wählt in
                   beiden Fällen. */
                val k = z.kollege
                if (k != null && beimProfil != null) {
                    h.itemView.setOnClickListener { beimProfil.invoke(k) }
                } else {
                    h.itemView.setOnClickListener(waehlen)
                }
                h.anrufen.setOnClickListener(waehlen)
            }
        }
    }

    class KopfHalter(val text: TextView) : RecyclerView.ViewHolder(text)

    class EintragHalter(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.kontaktName)
        val unten: TextView = v.findViewById(R.id.kontaktUnten)
        val kuerzel: TextView = v.findViewById(R.id.kontaktKuerzel)
        val foto: ImageView = v.findViewById(R.id.kontaktFoto)
        val anrufen: ImageView = v.findViewById(R.id.kontaktAnrufen)
    }
}
