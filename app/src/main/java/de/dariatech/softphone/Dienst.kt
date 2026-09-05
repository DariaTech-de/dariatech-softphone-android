package de.dariatech.softphone

import android.content.Context
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/**
 * Der Draht zur Anlage – alles, was nicht Telefonie ist.
 *
 * DER AUFTRAG (Inhaber, 05.09.2026): „Sobald die App ausgeht, muss dann
 * nur nach Benutzername und nach Passwort gefragt werden." Genau das
 * macht dieser Draht möglich: Er tauscht Name und Passwort EINMAL gegen
 * ein Token, mit dem danach alles andere geht – Kollegen, Bilder,
 * Kontakte, Faxe.
 *
 * DASSELBE GIBT ES AUF iOS (`Dienst.swift`), mit denselben Wegen und
 * denselben Regeln. Zwei Apps, die verschieden mit der Anlage reden,
 * erzeugen zwei Fehlerbilder für dieselbe Ursache.
 *
 * DAS TOKEN LIEGT IN DER VERSCHLÜSSELTEN ABLAGE, nicht in den
 * gewöhnlichen Einstellungen – dieselbe Regel wie beim SIP-Passwort.
 * Wer es hat, kommt an das Adressbuch des Kunden.
 *
 * NICHTS HIER DARF DAS TELEFONIEREN AUFHALTEN. Wer kein Netz hat oder
 * dessen Anlage den Dienst gar nicht anbietet, soll telefonieren
 * können. Jeder Fehler endet deshalb in `null` oder einer leeren Liste,
 * nie in einem Absturz – und alles läuft im Hintergrundfaden.
 */
object Dienst {
    private const val TAG = "Dienst"
    private const val TOKEN = "dienst-token"
    private const val NEBENSTELLE = "dienst-nebenstelle"
    private const val GEDULD_MS = 15_000

    fun token(context: Context): String = Zugangsspeicher.lies(context, TOKEN)

    fun angemeldet(context: Context): Boolean = token(context).isNotEmpty()

    /**
     * Die eigene Nebenstelle, wie die Anlage sie beim Tausch genannt hat.
     *
     * WOZU: Um das EIGENE Bild zu zeigen und zu ändern, muss die App
     * wissen, welcher der Menschen aus `/kollegen` sie selbst ist. Sie
     * kennt aber nur ihren SIP-Benutzernamen (`nst-…`), und der steht in
     * keiner Kollegenliste. Die Anlage nennt beim Tausch die interne
     * Nummer – die steht bei genau einem Menschen unter `nebenstellen`.
     *
     * Kein Geheimnis, deshalb in der gewöhnlichen Ablage.
     */
    fun eigeneNebenstelle(context: Context): String =
        Zugangsspeicher.offen(context).getString(NEBENSTELLE, "") ?: ""

    /** Beim Abmelden mitzunehmen – sonst bliebe der Ausweis liegen. */
    fun vergiss(context: Context) {
        Zugangsspeicher.setze(context, TOKEN, "")
        Zugangsspeicher.offen(context).edit().remove(NEBENSTELLE).apply()
    }

    /**
     * Name und Passwort gegen ein Token tauschen.
     *
     * WANN: nach der geglückten SIP-Anmeldung, nicht davor. Sind die
     * Zugangsdaten falsch, scheitert ohnehin schon die Registrierung –
     * zwei Fehlermeldungen für einen Tippfehler sind eine zu viel.
     */
    fun hole(context: Context, benutzer: String, passwort: String): Boolean {
        val antwort = sende(
            "/token", "POST",
            JSONObject().put("benutzer", benutzer).put("passwort", passwort).toString()
                .toByteArray(),
            "application/json", null
        ) ?: return false
        return try {
            val roh = JSONObject(String(antwort))
            val neu = roh.optString("token")
            if (neu.isEmpty()) return false
            Zugangsspeicher.setze(context, TOKEN, neu)
            Zugangsspeicher.offen(context).edit()
                .putString(NEBENSTELLE, roh.optString("nebenstelle")).apply()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Antwort der Anlage unlesbar: ${e.message}")
            false
        }
    }

    /** Die Menschen des eigenen Hauses. */
    fun kollegen(context: Context): List<Kollege> {
        val roh = sende("/kollegen", "GET", null, null, token(context)) ?: return emptyList()
        return try {
            val liste = JSONArray(String(roh))
            (0 until liste.length()).map { Kollege.aus(liste.getJSONObject(it)) }
        } catch (e: Exception) {
            Log.w(TAG, "Kollegenliste unlesbar: ${e.message}")
            emptyList()
        }
    }

    /** Das Adressbuch: Organisationsbuch plus das eigene private. */
    fun kontakte(context: Context): List<Kontakt> {
        val roh = sende("/kontakte", "GET", null, null, token(context)) ?: return emptyList()
        return try {
            val liste = JSONArray(String(roh))
            (0 until liste.length()).map { Kontakt.aus(liste.getJSONObject(it)) }
        } catch (e: Exception) {
            Log.w(TAG, "Adressbuch unlesbar: ${e.message}")
            emptyList()
        }
    }

    /**
     * Eine Nachricht an einen Kollegen.
     *
     * ÜBER DIE ANLAGE, NICHT ÜBER SIP. SIP MESSAGE erreicht nur
     * ANGEMELDETE Geräte. Beendet Android den Vordergrunddienst – und
     * das tut es, wenn der Akku knapp wird –, wäre die Nachricht WEG,
     * nicht verspätet. Über den Dienst liegt sie auf der Anlage, bis
     * sie jemand abholt, auf jedem seiner Geräte.
     */
    fun sendeNachricht(context: Context, an: String, text: String): Nachricht? {
        val rumpf = JSONObject().put("an", an).put("text", text).toString().toByteArray()
        val roh = sende("/nachrichten", "POST", rumpf, "application/json", token(context))
            ?: return null
        return try {
            Nachricht.aus(JSONObject(String(roh)).getJSONObject("nachricht"))
        } catch (e: Exception) {
            Log.w(TAG, "Antwort auf die Nachricht unlesbar: ${e.message}")
            null
        }
    }

    /**
     * Alles Neue seit einem Zeitpunkt.
     *
     * `seit` ist die Zeit der jüngsten Nachricht, die das Gerät schon
     * hat. Ohne diesen Schnitt lüde das Telefon bei JEDER Frage den
     * ganzen Verlauf – über Mobilfunk, auf Kosten des Kunden.
     */
    fun nachrichten(context: Context, seit: Long = 0L): List<Nachricht> {
        val roh = sende("/nachrichten?seit=$seit", "GET", null, null, token(context))
            ?: return emptyList()
        return try {
            val liste = JSONObject(String(roh)).optJSONArray("nachrichten") ?: return emptyList()
            (0 until liste.length()).map { Nachricht.aus(liste.getJSONObject(it)) }
        } catch (e: Exception) {
            Log.w(TAG, "Nachrichtenliste unlesbar: ${e.message}")
            emptyList()
        }
    }

    /** Das Bild eines Menschen – roh, wie es kommt. */
    fun foto(context: Context, benutzerId: String): ByteArray? =
        sende("/foto/$benutzerId", "GET", null, null, token(context))

    /** Sein eigenes Bild setzen. `null` entfernt es. */
    fun setzeEigenesFoto(context: Context, daten: ByteArray?): Boolean =
        sende(
            "/foto", if (daten == null) "DELETE" else "POST",
            daten, if (daten == null) null else "image/jpeg", token(context)
        ) != null

    /**
     * Eine Anfrage – im aufrufenden Faden, der NIE der Hauptfaden ist.
     *
     * Android beendet eine App, die im Hauptfaden ins Netz geht
     * (NetworkOnMainThreadException). Die Aufrufer sitzen deshalb alle
     * in einem Hintergrundfaden; hier steht es noch einmal, weil das
     * beim nächsten Aufrufer die Stelle wäre, an der man es vergisst.
     */
    private fun sende(
        weg: String,
        methode: String,
        koerper: ByteArray?,
        typ: String?,
        token: String?
    ): ByteArray? {
        if (token != null && token.isEmpty()) return null
        var verbindung: HttpURLConnection? = null
        return try {
            verbindung = (URL(Anlage.DIENST + weg).openConnection() as HttpURLConnection).apply {
                requestMethod = methode
                connectTimeout = GEDULD_MS
                readTimeout = GEDULD_MS
                if (token != null) setRequestProperty("Authorization", "Bearer $token")
                if (typ != null) setRequestProperty("Content-Type", typ)
                if (koerper != null) {
                    doOutput = true
                    outputStream.use { it.write(koerper) }
                }
            }
            if (verbindung.responseCode !in 200..299) {
                Log.w(TAG, "$weg antwortet mit ${verbindung.responseCode}")
                return null
            }
            verbindung.inputStream.use { it.readBytes() }
        } catch (e: Exception) {
            /* KEIN ABSTURZ, NUR EINE ZEILE IM PROTOKOLL. Ohne Netz soll
               die App telefonieren; Kontakte und Bilder sind Beiwerk. */
            Log.w(TAG, "$weg nicht erreichbar: ${e.message}")
            null
        } finally {
            verbindung?.disconnect()
        }
    }
}

/**
 * Eine Nachricht, wie die Anlage sie liefert.
 *
 * `zeit` sind Millisekunden seit 1970 – dieselbe Uhr wie überall in der
 * Anlage und wie in der iPhone-App. In Sekunden umzurechnen wäre die
 * Stelle, an der zwei Geräte verschiedene Reihenfolgen zeigen.
 */
data class Nachricht(
    val id: String,
    val von: String,
    val an: String,
    val text: String,
    val zeit: Long
) {
    /** Der andere – egal in welche Richtung die Nachricht lief. */
    fun gegenueber(ich: String): String = if (von == ich) an else von

    companion object {
        fun aus(o: JSONObject) = Nachricht(
            id = o.optString("id"),
            von = o.optString("von"),
            an = o.optString("an"),
            text = o.optString("text"),
            zeit = o.optLong("zeit")
        )
    }
}

/**
 * Ein Mensch des eigenen Hauses.
 *
 * UNBEKANNTE FELDER WERDEN IGNORIERT, und keines ist Pflicht außer
 * Kennung und Name: Der Server darf jederzeit eines dazulegen (siehe
 * CLIENT-API.md). Deshalb wird von Hand aus dem JSON gelesen und nicht
 * über eine Bibliothek mit harten Pflichtfeldern – die wäre genau die
 * Bruchstelle, vor der die Beschreibung warnt.
 */
data class Kollege(
    val id: String,
    val name: String,
    val nummer: String,
    val durchwahl: String,
    val nebenstellen: List<String>,
    /** Marke seines Bildes. Leer heißt: er hat keines. */
    val foto: String
) {
    companion object {
        fun aus(o: JSONObject): Kollege {
            val nst = o.optJSONArray("nebenstellen")
            return Kollege(
                id = o.optString("id"),
                name = o.optString("name"),
                nummer = o.optString("nummer"),
                durchwahl = o.optString("durchwahl"),
                nebenstellen = (0 until (nst?.length() ?: 0)).map { nst!!.getString(it) },
                foto = o.optString("foto")
            )
        }
    }
}

/** Ein Eintrag aus dem Adressbuch der Anlage. */
data class Kontakt(
    val id: String,
    val name: String,
    val firma: String,
    val nummern: List<Nummer>
) {
    data class Nummer(val art: String, val nummer: String)

    companion object {
        fun aus(o: JSONObject): Kontakt {
            val n = o.optJSONArray("nummern")
            return Kontakt(
                id = o.optString("id"),
                name = o.optString("name"),
                firma = o.optString("firma"),
                nummern = (0 until (n?.length() ?: 0)).map {
                    val e = n!!.getJSONObject(it)
                    Nummer(e.optString("art"), e.optString("nummer"))
                }
            )
        }
    }
}
