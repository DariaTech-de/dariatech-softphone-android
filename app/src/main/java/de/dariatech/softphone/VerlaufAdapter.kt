package de.dariatech.softphone

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Der Anrufverlauf als Liste.
 *
 * DER ANLASS ist ein Auftrag des Inhabers vom 30.08.2026, die Apps
 * optisch auf höchstes Niveau zu bringen. Vorher hing hier ein
 * `SimpleAdapter` auf `android.R.layout.simple_list_item_2`: zwei Zeilen
 * Text aus dem Android-Vorrat, die Richtung als Pfeilzeichen im Text.
 * Bedienbar – und auf den ersten Blick nicht von einer Übung zu
 * unterscheiden.
 *
 * ZWEI DINGE MACHEN DEN UNTERSCHIED, und beide sind keine Zierde:
 *
 *  · DER ANRUFKNOPF IN DER ZEILE. Zurückrufen ist das, wofür man einen
 *    Verlauf öffnet. Vorher übernahm ein Tippen die Nummer ins Wählfeld,
 *    und man musste ein zweites Mal drücken – in einer App, die man im
 *    Gehen bedient, ein Schritt zu viel.
 *  · DIE ZEIT RELATIV. „11:45" bei heute, „Gestern", sonst das Datum.
 *    Ein Verlauf wird gelesen, um zu wissen, ob etwas gerade eben war.
 */
class VerlaufAdapter(
    private var eintraege: List<CallEntry>,
    private val amKuerzel: (CallEntry) -> String,
    private val beimAnrufen: (CallEntry) -> Unit,
    private val beimTippen: (CallEntry) -> Unit
) : RecyclerView.Adapter<VerlaufAdapter.Halter>() {

    class Halter(v: View) : RecyclerView.ViewHolder(v) {
        val kuerzel: TextView = v.findViewById(R.id.zeileKuerzel)
        val nummer: TextView = v.findViewById(R.id.zeileNummer)
        val richtung: TextView = v.findViewById(R.id.zeileRichtung)
        val zeit: TextView = v.findViewById(R.id.zeileZeit)
        val anrufen: ImageView = v.findViewById(R.id.zeileAnrufen)
    }

    fun zeige(neu: List<CallEntry>) {
        eintraege = neu
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Halter =
        Halter(LayoutInflater.from(parent.context).inflate(R.layout.zeile_anruf, parent, false))

    override fun getItemCount() = eintraege.size

    override fun onBindViewHolder(halter: Halter, position: Int) {
        val e = eintraege[position]
        val ctx = halter.itemView.context
        halter.kuerzel.text = amKuerzel(e)
        halter.nummer.text = e.number
        halter.zeit.text = zeitpunkt(e.at)

        val richtung = when (e.direction) {
            "missed" -> ctx.getString(R.string.dir_missed)
            "in" -> ctx.getString(R.string.dir_in)
            else -> ctx.getString(R.string.dir_out)
        }
        val dauer = if (e.durationSec > 0) {
            " · %d:%02d".format(e.durationSec / 60, e.durationSec % 60)
        } else ""
        halter.richtung.text = "$richtung$dauer"
        // Ein verpasster Anruf ist die einzige Zeile, die auffallen soll.
        halter.richtung.setTextColor(
            if (e.direction == "missed") farbe(ctx, com.google.android.material.R.attr.colorError)
            else farbe(ctx, com.google.android.material.R.attr.colorOnSurfaceVariant)
        )

        halter.anrufen.setOnClickListener { beimAnrufen(e) }
        halter.itemView.setOnClickListener { beimTippen(e) }
    }

    private fun farbe(ctx: android.content.Context, attr: Int): Int {
        val wert = android.util.TypedValue()
        ctx.theme.resolveAttribute(attr, wert, true)
        return wert.data
    }

    /**
     * Heute nur die Uhrzeit, gestern „Gestern", sonst das Datum.
     *
     * Ein Datum bei einem Anruf von vor zehn Minuten zwingt zum Rechnen.
     */
    private fun zeitpunkt(zeit: Long): String {
        val jetzt = Calendar.getInstance()
        val dann = Calendar.getInstance().apply { timeInMillis = zeit }
        val gleicherTag = jetzt.get(Calendar.YEAR) == dann.get(Calendar.YEAR) &&
            jetzt.get(Calendar.DAY_OF_YEAR) == dann.get(Calendar.DAY_OF_YEAR)
        jetzt.add(Calendar.DAY_OF_YEAR, -1)
        val gestern = jetzt.get(Calendar.YEAR) == dann.get(Calendar.YEAR) &&
            jetzt.get(Calendar.DAY_OF_YEAR) == dann.get(Calendar.DAY_OF_YEAR)
        return when {
            gleicherTag -> SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date(zeit))
            gestern -> "Gestern"
            else -> SimpleDateFormat("dd.MM.", Locale.GERMANY).format(Date(zeit))
        }
    }
}
