package de.dariatech.softphone

import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton

/**
 * Das Profil eines Menschen – das eigene und das der Kollegen.
 *
 * DER AUFTRAG (Inhaber, 09.09.2026): „jetzt alles was in Portal
 * eingetragen wird pro User soll in der App im Profil angezeogt werden,
 * soll im einegnen Profil als auch im Profil andere Koolegen der
 * Organistation." Als Vorbild hat er WhatsApp und Teams genannt.
 *
 * DER SCHADEN VORHER: Die Kollegenliste kannte vier Felder, und ein
 * Druck auf eine Zeile wählte. Mehr war nicht. Wer wissen wollte, wer
 * da eigentlich sitzt, musste jemanden fragen.
 *
 * ALLES KOMMT AUS `/kollegen`, nichts wird hier nachgeladen. Das
 * Verzeichnis liegt schon im Gerät (Verzeichnis.kt) – ein Profil, das
 * beim Öffnen erst den Server fragt, steht im Funkloch leer da.
 *
 * WAS HIER NICHT STEHT, und das ist Absicht: die Klingeldauer und die
 * ausgehende Rufnummer. Die Anlage gibt beides gar nicht erst heraus
 * (`pbx/src/client/server.ts`); es sind Einstellungen der Technik und
 * kein Profil. Wer sie sähe, hielte sie für etwas, das er ändern kann.
 *
 * DASSELBE GIBT ES AUF iOS (`Profil.swift`), mit denselben Feldern und
 * denselben Regeln.
 */
object Profil {

    /**
     * Das Profil eines Kollegen zeigen.
     *
     * `beimAnruf` und `beiNachricht` kommen von aussen, weil beides in
     * der MainActivity hängt (Anrufbereich, Gesprächsbildschirm). Ein
     * Blatt, das selbst anruft, müsste den halben Anrufweg kennen.
     */
    fun zeige(
        activity: Activity,
        wer: Kollege,
        ich: Boolean = false,
        beimAnruf: ((String) -> Unit)? = null,
        beiNachricht: ((Kollege) -> Unit)? = null
    ) {
        val blatt = LayoutInflater.from(activity).inflate(R.layout.blatt_profil, null)
        val dialog = BottomSheetDialog(activity)
        dialog.setContentView(blatt)

        val name = blatt.findViewById<TextView>(R.id.profilName)
        val rolle = blatt.findViewById<TextView>(R.id.profilRolle)
        val kuerzel = blatt.findViewById<TextView>(R.id.profilKuerzel)
        val foto = blatt.findViewById<ImageView>(R.id.profilFoto)
        val ausser = blatt.findViewById<TextView>(R.id.profilAusserDienst)
        val angaben = blatt.findViewById<LinearLayout>(R.id.profilAngaben)
        val leer = blatt.findViewById<TextView>(R.id.profilLeer)
        val knoepfe = blatt.findViewById<LinearLayout>(R.id.profilKnoepfe)
        val anrufen = blatt.findViewById<MaterialButton>(R.id.profilAnrufen)
        val nachricht = blatt.findViewById<MaterialButton>(R.id.profilNachricht)

        name.text = wer.name

        /* POSITION UND ABTEILUNG unter dem Namen, nicht in der Liste
           der Angaben: Sie sagen, WER das ist – dieselbe Stelle, an der
           bei WhatsApp der Statustext steht. */
        val rollentext = listOf(wer.position, wer.abteilung).filter { it.isNotEmpty() }
            .joinToString(" · ")
        rolle.text = rollentext
        rolle.visibility = if (rollentext.isEmpty()) View.GONE else View.VISIBLE

        /* BILD ODER INITIALEN – immer genau eines. Ein leerer Kreis
           sieht aus wie ein Bild, das nicht geladen hat. */
        val bild = Verzeichnis.bild(wer.id)
        if (bild != null) {
            foto.setImageBitmap(bild)
            foto.visibility = View.VISIBLE
            kuerzel.visibility = View.INVISIBLE
        } else {
            foto.visibility = View.GONE
            kuerzel.visibility = View.VISIBLE
            kuerzel.text = Verzeichnis.kuerzel(wer.name)
        }

        ausser.visibility = if (wer.ausserDienst) View.VISIBLE else View.GONE

        /* IM EIGENEN PROFIL KEINE KNÖPFE. Sich selbst anzurufen oder
           sich selbst zu schreiben ist kein Vorgang; die Anlage würde
           den Anruf durchstellen und das eigene Gerät klingeln lassen. */
        knoepfe.visibility = if (ich) View.GONE else View.VISIBLE

        val ziel = ruf(wer)
        anrufen.isEnabled = ziel.isNotEmpty()
        anrufen.setOnClickListener {
            dialog.dismiss()
            if (ziel.isNotEmpty()) beimAnruf?.invoke(ziel)
        }
        nachricht.setOnClickListener {
            dialog.dismiss()
            beiNachricht?.invoke(wer)
        }

        /* DIE ANGABEN. Nur, was gepflegt ist – eine Zeile „Abteilung: –"
           sagt nichts und kostet Platz. */
        angaben.removeAllViews()
        val ctx = activity as Context
        zeile(ctx, angaben, R.string.profil_position, wer.position)
        zeile(ctx, angaben, R.string.profil_abteilung, wer.abteilung)
        zeile(ctx, angaben, R.string.profil_durchwahl, wer.durchwahl)
        zeile(ctx, angaben, R.string.profil_nummer, wer.nummer)
        zeile(ctx, angaben, R.string.profil_fax, wer.fax)
        zeile(ctx, angaben, R.string.profil_email, wer.email)

        /* IST NICHTS GEPFLEGT, sagt das Blatt das – statt eine leere
           Fläche zu zeigen. „Nichts da" und „kaputt" sehen sonst gleich
           aus. Im eigenen Profil steht dazu, WER es einträgt. */
        if (angaben.childCount == 0) {
            leer.visibility = View.VISIBLE
            leer.setText(if (ich) R.string.profil_ich_leer else R.string.profil_leer)
        } else {
            leer.visibility = View.GONE
        }

        dialog.show()
    }

    /**
     * Das EIGENE Profil.
     *
     * Wer man ist, weiß die App über ihre Nebenstelle (Verzeichnis.ich).
     * Gehört dieser Apparat keinem Menschen – ein Konferenzraum, ein
     * Türsprecher –, ist das keine Störung, sondern eine Auskunft.
     */
    fun zeigeMich(activity: Activity) {
        val ich = Verzeichnis.ich(activity)
        if (ich == null) {
            android.app.AlertDialog.Builder(activity)
                .setTitle(R.string.profil_ich)
                .setMessage(R.string.profil_kein_ich)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        zeige(activity, ich, ich = true)
    }

    /**
     * Wen ruft man bei einem Kollegen an? SEINE DURCHWAHL, wenn er eine
     * hat – dann klingeln ALLE seine Geräte. Sonst die erste
     * Nebenstelle, also ein einzelnes Gerät. Dieselbe Regel wie in der
     * Kontaktliste (KontakteAdapter); zwei Fassungen davon laufen
     * auseinander.
     */
    fun ruf(k: Kollege): String = when {
        k.durchwahl.isNotEmpty() -> k.durchwahl
        k.nebenstellen.isNotEmpty() -> k.nebenstellen.first()
        else -> k.nummer
    }

    /**
     * Passt dieser Mensch zur Suche?
     *
     * GENAU DAFÜR SIND POSITION UND ABTEILUNG DA – der Auftrag lautete
     * „um in Apps nach der mitarbeiter zu suchen". Ein Feld, das die App
     * anzeigt, aber nicht durchsucht, erfüllt ihn zur Hälfte.
     *
     * Klein geschrieben verglichen: Wer „mta" tippt, meint „MTA".
     */
    fun passt(k: Kollege, suche: String): Boolean {
        val q = suche.trim().lowercase()
        if (q.isEmpty()) return true
        return listOf(k.name, k.position, k.abteilung, k.durchwahl, k.nummer, k.email)
            .any { it.lowercase().contains(q) }
    }

    /** Eine Zeile „Beschriftung / Wert" – oder gar keine, wenn leer. */
    private fun zeile(ctx: Context, wohin: LinearLayout, beschriftung: Int, wert: String) {
        if (wert.isEmpty()) return
        val spalte = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 14 }
        }
        spalte.addView(TextView(ctx).apply {
            setText(beschriftung)
            textSize = 12f
            setTextColor(farbe(ctx, com.google.android.material.R.attr.colorOnSurfaceVariant))
        })
        spalte.addView(TextView(ctx).apply {
            text = wert
            textSize = 16f
            setTextIsSelectable(true)
            setTextColor(farbe(ctx, com.google.android.material.R.attr.colorOnSurface))
        })
        wohin.addView(spalte)
    }

    private fun farbe(ctx: Context, attr: Int): Int {
        val wert = android.util.TypedValue()
        ctx.theme.resolveAttribute(attr, wert, true)
        return wert.data
    }
}
