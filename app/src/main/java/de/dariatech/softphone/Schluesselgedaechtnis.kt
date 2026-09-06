package de.dariatech.softphone

import android.content.Context
import org.json.JSONObject
import java.io.File

/** Was die App über den Schlüssel eines fremden Geräts weiß. */
enum class Schluesselzustand {
    /** Genau diesen Schlüssel hat dieses Gerät schon einmal gehabt. */
    BEKANNT,
    /** Dieses Gerät ist neu – nie gesehen, also auch nie verglichen. */
    NEU,
    /** Dieses Gerät hatte schon einmal einen ANDEREN Schlüssel. */
    GEWECHSELT
}

/**
 * Das Gedächtnis für fremde Schlüssel.
 *
 * DER ANLASS (Sicherheitsdurchsicht, 06.09.2026, gefunden in der
 * iOS-App und hier wortgleich vorhanden). Stufe 3 verspricht, dass die
 * Anlage nicht mitlesen kann. Nachprüfbar ist das nur über den
 * Fingerabdruck, den sich zwei Menschen am Telefon vorlesen.
 *
 * DER SCHADEN: Angezeigt wurde `fingerabdruck` aus der Antwort der
 * Anlage, verschlüsselt aber mit `schluessel` aus derselben Antwort –
 * zwei Felder, die nichts aneinander bindet. Wer die Anlage übernimmt,
 * tauscht den Schlüssel und lässt den Fingerabdruck stehen; die beiden
 * lesen sich dasselbe Wort vor, und er liest mit. Auch das Warnzeichen
 * `gewechselt` kam von der Anlage – und ein NEUES Gerät ist per
 * Definition nicht „gewechselt", erschien also unauffällig.
 *
 * DIE REGEL: Die App glaubt der Anlage NICHTS über Schlüssel. Sie
 * rechnet den Fingerabdruck selbst und merkt sich hier, welchen
 * Schlüssel sie bei welchem Gerät gesehen hat. Das ist „trust on first
 * use": Der erste Schlüssel wird geglaubt – mehr geht ohne einen
 * zweiten Kanal nicht –, aber jede Änderung fällt auf, und ein neues
 * Gerät auch.
 *
 * WARUM IM TRESOR: Wer diese Liste ändern kann, schaltet die Warnung
 * ab. Im Tresor liegt sie verschlüsselt, und beim Abmelden ist sie weg.
 */
object Schluesselgedaechtnis {
    private fun datei(context: Context) = File(context.filesDir, "schluesselgedaechtnis.json")

    /** kollegeId → geraetId → Schlüssel (Base64), so wie zuletzt bestätigt. */
    private fun laden(context: Context): JSONObject {
        val roh = Tresor.lies(context, datei(context)) ?: return JSONObject()
        return try {
            JSONObject(String(roh, Charsets.UTF_8))
        } catch (e: Exception) {
            JSONObject()
        }
    }

    /**
     * Woran ist man bei diesem Gerät?
     *
     * Der Schlüssel wird MITGEGEBEN und nicht nachgeschlagen: Verglichen
     * wird der Schlüssel, mit dem gleich verschlüsselt wird, nicht
     * irgendeiner aus derselben Antwort.
     */
    fun zustand(
        context: Context,
        kollege: String,
        geraet: String,
        schluessel: String
    ): Schluesselzustand {
        val bekannt = laden(context).optJSONObject(kollege)?.optString(geraet).orEmpty()
        if (bekannt.isEmpty()) return Schluesselzustand.NEU
        return if (bekannt == schluessel) Schluesselzustand.BEKANNT else Schluesselzustand.GEWECHSELT
    }

    /**
     * Diesen Schlüssel für dieses Gerät als gesehen festhalten.
     *
     * Aufgerufen wird das NUR, wenn ein Mensch bestätigt hat – nicht
     * automatisch beim Anzeigen. Sonst würde sich die Warnung selbst
     * wegklicken, und das Gedächtnis wäre eine Verzierung.
     */
    fun merke(context: Context, kollege: String, geraet: String, schluessel: String) {
        val stand = laden(context)
        val fuerKollegen = stand.optJSONObject(kollege) ?: JSONObject()
        fuerKollegen.put(geraet, schluessel)
        stand.put(kollege, fuerKollegen)
        Tresor.schreibe(context, datei(context), stand.toString().toByteArray(Charsets.UTF_8))
    }

    /** Beim Abmelden: alles vergessen. */
    fun vergiss(context: Context) {
        datei(context).delete()
    }
}
