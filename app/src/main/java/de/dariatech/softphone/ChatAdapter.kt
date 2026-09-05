package de.dariatech.softphone

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Die Liste im Bereich „Nachrichten": alle Menschen des Hauses.
 *
 * AUCH DIE, MIT DENEN MAN NOCH NIE GESCHRIEBEN HAT. Das stand so im
 * Auftrag („einen User in der Organisation zu finden"), und es ist der
 * Unterschied zwischen einem Chat und einer Liste alter Gespräche.
 *
 * WER ZULETZT GESCHRIEBEN HAT, STEHT OBEN. Alphabetisch wäre ordentlich
 * und für den, der eine Antwort sucht, nutzlos.
 */
class ChatAdapter(
    private val context: Context,
    private val ich: String,
    private val beimOeffnen: (Kollege) -> Unit
) : RecyclerView.Adapter<ChatAdapter.Halter>() {

    private var leute: List<Kollege> = emptyList()
    private val uhr = SimpleDateFormat("HH:mm", Locale.GERMANY)
    private val tag = SimpleDateFormat("dd.MM.", Locale.GERMANY)

    fun setze(kollegen: List<Kollege>) {
        leute = kollegen
            .filter { it.id != ich }
            .sortedWith(
                compareByDescending<Kollege> { Postfach.letzte(context, it.id, ich)?.zeit ?: 0L }
                    .thenBy { it.name.lowercase() }
            )
        notifyDataSetChanged()
    }

    override fun getItemCount() = leute.size

    override fun onCreateViewHolder(eltern: ViewGroup, art: Int): Halter =
        Halter(LayoutInflater.from(eltern.context).inflate(R.layout.zeile_chat, eltern, false))

    override fun onBindViewHolder(halter: Halter, stelle: Int) {
        val k = leute[stelle]
        halter.name.text = k.name
        halter.kuerzel.text = Verzeichnis.kuerzel(k.name)

        /* DAS GESICHT ODER DIE INITIALEN – nie beides, nie keines. Ein
           leerer Kreis sähe aus wie ein Bild, das nicht geladen hat. */
        val bild = Verzeichnis.bild(k.id)
        if (bild != null) {
            halter.bild.setImageDrawable(
                androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
                    .create(halter.bild.resources, bild)
                    .apply { isCircular = true }
            )
            halter.bild.visibility = View.VISIBLE
            halter.kuerzel.visibility = View.GONE
        } else {
            halter.bild.visibility = View.GONE
            halter.kuerzel.visibility = View.VISIBLE
        }

        val letzte = Postfach.letzte(context, k.id, ich)
        halter.vorschau.text = when {
            letzte != null && letzte.von == ich -> "Sie: ${letzte.text}"
            letzte != null -> letzte.text
            k.durchwahl.isNotEmpty() -> "Durchwahl ${k.durchwahl}"
            else -> context.getString(R.string.chat_noch_nichts)
        }
        halter.zeit.text = letzte?.let {
            val d = Date(it.zeit)
            /* HEUTE DIE UHRZEIT, sonst das Datum. „14:32" bei einer
               Nachricht von vorletzter Woche ist eine falsche Auskunft. */
            if (System.currentTimeMillis() - it.zeit < 86_400_000) uhr.format(d) else tag.format(d)
        } ?: ""

        halter.itemView.setOnClickListener { beimOeffnen(k) }
    }

    class Halter(sicht: View) : RecyclerView.ViewHolder(sicht) {
        val name: TextView = sicht.findViewById(R.id.chatName)
        val kuerzel: TextView = sicht.findViewById(R.id.chatKuerzel)
        val bild: ImageView = sicht.findViewById(R.id.zeileChatBild)
        val vorschau: TextView = sicht.findViewById(R.id.chatVorschau)
        val zeit: TextView = sicht.findViewById(R.id.chatZeit)
    }
}

/**
 * Der Verlauf eines Gesprächs – eine Blase je Nachricht.
 *
 * DIE EIGENEN RECHTS UND IN DER MARKENFARBE, die fremden links und
 * grau. Das ist keine Mode, sondern die einzige Art, einen Verlauf im
 * Vorbeigehen zu lesen.
 */
class BlasenAdapter(private val ich: String) : RecyclerView.Adapter<BlasenAdapter.Halter>() {

    private var alle: List<Nachricht> = emptyList()
    private val uhr = SimpleDateFormat("HH:mm", Locale.GERMANY)

    fun setze(neue: List<Nachricht>) {
        alle = neue
        notifyDataSetChanged()
    }

    override fun getItemCount() = alle.size

    override fun onCreateViewHolder(eltern: ViewGroup, art: Int): Halter =
        Halter(LayoutInflater.from(eltern.context).inflate(R.layout.zeile_blase, eltern, false))

    override fun onBindViewHolder(halter: Halter, stelle: Int) {
        val n = alle[stelle]
        val eigen = n.von == ich
        halter.text.text = n.text
        halter.zeit.text = uhr.format(Date(n.zeit))
        halter.rahmen.gravity = if (eigen) android.view.Gravity.END else android.view.Gravity.START
        halter.blase.setBackgroundResource(
            if (eigen) R.drawable.blase_eigen else R.drawable.blase_fremd
        )
        val farbe = androidx.core.content.ContextCompat.getColor(
            halter.itemView.context,
            if (eigen) R.color.auf_primaer else R.color.text
        )
        halter.text.setTextColor(farbe)
        halter.zeit.setTextColor(farbe)
        halter.zeit.alpha = 0.75f
    }

    class Halter(sicht: View) : RecyclerView.ViewHolder(sicht) {
        val rahmen: android.widget.LinearLayout = sicht.findViewById(R.id.blaseRahmen)
        val blase: android.widget.LinearLayout = sicht.findViewById(R.id.blase)
        val text: TextView = sicht.findViewById(R.id.blaseText)
        val zeit: TextView = sicht.findViewById(R.id.blaseZeit)
    }
}
