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
    /**
     * Den öffentlichen Schlüssel DIESES Geräts bei der Anlage anmelden.
     *
     * Läuft nach jeder Anmeldung und beim Start. Die Anlage verwahrt
     * ihn und gibt ihn den Kollegen; rechnen tut sie damit nichts – der
     * private Teil liegt im verschlüsselten Speicher dieses Telefons.
     *
     * Fehlschläge sind hier KEIN Drama: Eine ältere Anlage kennt den
     * Weg nicht (404), und dann chattet die App wie bisher.
     */
    fun meldeSchluessel(context: Context): Boolean {
        val oeffentlich = Ende2Ende.oeffentlich(context)
        if (oeffentlich.isEmpty()) return false
        val rumpf = JSONObject()
            .put("geraet", Ende2Ende.geraetId(context))
            .put("oeffentlich", oeffentlich)
            .toString().toByteArray()
        return sende("/schluessel", "POST", rumpf, "application/json", token(context)) != null
    }

    /**
     * Einen gemeldeten Schlüsselwechsel bestätigen, nachdem die App ihn
     * gezeigt hat. Eine Warnung, die nie verschwindet, liest nach der
     * dritten Woche niemand mehr.
     */
    fun bestaetigeWechsel(context: Context, geraet: String): Boolean {
        val rumpf = JSONObject().put("geraet", geraet).put("bestaetigt", true)
            .toString().toByteArray()
        return sende("/schluessel", "POST", rumpf, "application/json", token(context)) != null
    }

    fun sendeNachricht(
        context: Context,
        an: String,
        text: String,
        umschlaege: Map<String, String> = emptyMap()
    ): Nachricht? {
        /* MIT UMSCHLÄGEN GEHT KEIN TEXT MIT. Sonst läge der Klartext
           neben dem Chiffrat auf der Platte der Anlage, und die ganze
           Stufe wäre eine Behauptung. */
        val o = JSONObject().put("an", an)
        if (umschlaege.isNotEmpty()) {
            o.put("umschlaege", JSONObject(umschlaege as Map<*, *>))
            o.put("geraet", Ende2Ende.geraetId(context))
        } else {
            o.put("text", text)
        }
        val rumpf = o.toString().toByteArray()
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

    /**
     * Was die Anlage von DIESEM Gerät sieht (GET /geraet, CLIENT-API.md).
     *
     * DER VORFALL (iOS, 07.09.2026): Die App zeigte „Verbunden", das
     * Portal „abgemeldet", Asterisk hatte keinen Contact. Drei Stunden
     * lang die Frage, wer lügt. Seitdem fragt die App die Anlage selbst
     * – und „Verbunden" gilt nur, wenn die Anlage es bestätigt.
     *
     * `null` heißt: keine Antwort (kein Netz, ältere Anlage ohne diesen
     * Weg). Das ist KEIN Widerspruch zur eigenen Anzeige, nur keine
     * Bestätigung.
     */
    fun geraet(context: Context): Geraetesicht? {
        val (status, roh) = sendeMitStatus("/geraet", "GET", null, null, token(context))
        if (status != 200 || roh == null) return null
        return try {
            Geraetesicht.aus(JSONObject(String(roh)))
        } catch (e: Exception) {
            Log.w(TAG, "Sicht der Anlage unlesbar: ${e.message}")
            null
        }
    }

    /** Der Dienststand des Menschen hinter diesem Apparat (GET /dienst). */
    fun dienst(context: Context): Dienstantwort =
        dienstAntwort(sendeMitStatus("/dienst", "GET", null, null, token(context)))

    /**
     * An- oder abmelden an den Warteschleifen (POST /dienst).
     *
     * ÜBER DIE ANLAGE, NICHT ÜBER *45. Der Tastencode ändert den Stand
     * in Asterisk, aber die App erfährt nie, ob es geklappt hat – und
     * am 07.09.2026 hatte der Apparat gar keinen Benutzer, sodass *45
     * eine Ansage spielte und nichts änderte. Über den Dienst kommt die
     * Antwort zurück: der neue Stand, oder mit 409 die Begründung der
     * Anlage im Klartext.
     */
    fun setzeDienst(context: Context, ausser: Boolean): Dienstantwort =
        dienstAntwort(
            sendeMitStatus(
                "/dienst", "POST",
                JSONObject().put("ausserDienst", ausser).toString().toByteArray(),
                "application/json", token(context)
            )
        )

    private fun dienstAntwort(antwort: Pair<Int, ByteArray?>): Dienstantwort {
        val (status, roh) = antwort
        if (status == 0) return Dienstantwort(null, "Die Anlage hat nicht geantwortet.")
        val text = roh?.let { String(it) } ?: ""
        if (status == 200) {
            return try {
                Dienstantwort(Dienststand.aus(JSONObject(text)), null)
            } catch (e: Exception) {
                Dienstantwort(null, "Antwort der Anlage unlesbar.")
            }
        }
        val grund = try { JSONObject(text).optString("error") } catch (e: Exception) { "" }
        // 409: Kein Benutzer zu diesem Apparat – eine Auskunft im
        // Klartext, kein Fehler. Genau die Information, die am
        // 07.09.2026 drei Stunden gefehlt hat.
        if (status == 409) {
            return Dienstantwort(null, grund.ifEmpty { "Diesem Apparat ist kein Benutzer zugeordnet." })
        }
        return Dienstantwort(null, grund.ifEmpty { "Die Anlage hat abgelehnt ($status)." })
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
        val (status, roh) = sendeMitStatus(weg, methode, koerper, typ, token)
        return if (status in 200..299) roh else null
    }

    /**
     * Dieselbe Anfrage, aber mit Statuscode und Rumpf AUCH im Fehlerfall.
     *
     * Für /dienst reicht „hat nicht geklappt" nicht: Die Anlage sagt
     * mit 409 im Klartext, WARUM (kein Benutzer zugeordnet), und diesen
     * Satz soll der Mensch lesen, nicht eine Zeile im Protokoll.
     * Status 0 heißt: gar keine Antwort.
     */
    private fun sendeMitStatus(
        weg: String,
        methode: String,
        koerper: ByteArray?,
        typ: String?,
        token: String?
    ): Pair<Int, ByteArray?> {
        if (token != null && token.isEmpty()) return Pair(0, null)
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
            val status = verbindung.responseCode
            if (status !in 200..299) {
                Log.w(TAG, "$weg antwortet mit $status")
                val fehlerRumpf = try { verbindung.errorStream?.use { it.readBytes() } } catch (e: Exception) { null }
                return Pair(status, fehlerRumpf)
            }
            Pair(status, verbindung.inputStream.use { it.readBytes() })
        } catch (e: Exception) {
            /* KEIN ABSTURZ, NUR EINE ZEILE IM PROTOKOLL. Ohne Netz soll
               die App telefonieren; Kontakte und Bilder sind Beiwerk. */
            Log.w(TAG, "$weg nicht erreichbar: ${e.message}")
            Pair(0, null)
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
    /**
     * Der Text – LEER, wenn die Nachricht Ende-zu-Ende verschlüsselt
     * ist. Dann steht sie in `umschlaege`, und die Anlage kann sie
     * nicht lesen. Beim Empfang wird sie im Gerät geöffnet (Postfach).
     */
    val text: String,
    val zeit: Long,
    /** Ein Umschlag je Gerät: Gerätekennung → Chiffrat (base64). */
    val umschlaege: Map<String, String> = emptyMap(),
    /** Mit welchem GERÄT der Absender geschrieben hat. */
    val absenderGeraet: String = ""
) {
    /** Der andere – egal in welche Richtung die Nachricht lief. */
    fun gegenueber(ich: String): String = if (von == ich) an else von

    companion object {
        fun aus(o: JSONObject): Nachricht {
            val u = o.optJSONObject("umschlaege")
            val umschlaege = mutableMapOf<String, String>()
            if (u != null) for (k in u.keys()) umschlaege[k] = u.optString(k)
            return Nachricht(
                id = o.optString("id"),
                von = o.optString("von"),
                an = o.optString("an"),
                text = o.optString("text"),
                zeit = o.optLong("zeit"),
                umschlaege = umschlaege,
                absenderGeraet = o.optString("absenderGeraet")
            )
        }
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
    val foto: String,
    /**
     * Seine Geräte mit ihren öffentlichen Schlüsseln (Stufe 3 des
     * Masterplans). Leer heißt: diese Anlage führt kein
     * Schlüsselverzeichnis, oder er hat noch keine App angemeldet –
     * dann geht der Chat wie bisher.
     */
    val geraete: List<Geraeteschluessel> = emptyList()
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
                foto = o.optString("foto"),
                geraete = o.optJSONArray("geraete").let { g ->
                    (0 until (g?.length() ?: 0)).map { Geraeteschluessel.aus(g!!.getJSONObject(it)) }
                }
            )
        }
    }
}

/**
 * Ein Gerät eines Kollegen – mit dem Schlüssel, für den man verschließt.
 */
data class Geraeteschluessel(
    val id: String,
    val schluessel: String,
    val fingerabdruck: String,
    /**
     * Dieses Gerät hat seinen Schlüssel GEWECHSELT.
     *
     * Kein Nebensatz: Ein Wechsel heißt entweder „neu installiert" oder
     * „jemand schiebt sich dazwischen". Die App zeigt es an, damit der
     * Mensch den Fingerabdruck noch einmal vergleicht.
     */
    val gewechselt: Boolean
) {
    companion object {
        fun aus(o: JSONObject) = Geraeteschluessel(
            id = o.optString("id"),
            schluessel = o.optString("schluessel"),
            fingerabdruck = o.optString("fingerabdruck"),
            gewechselt = o.optBoolean("gewechselt", false)
        )
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

/**
 * Was die Anlage von diesem Gerät sieht (GET /geraet).
 *
 * ALLE FELDER DÜRFEN FEHLEN. `angemeldet == null` heißt: Asterisk war
 * gerade nicht befragbar – das ist keine Aussage über das Gerät.
 * Dieselbe Klasse wie `Geraetesicht` in der iOS-App, damit beide Apps
 * dieselben Sätze sagen.
 */
data class Geraetesicht(
    val angemeldet: Boolean?,
    val erreichbar: Boolean?,
    val transport: String,
    val laufzeitMs: Double?,
    val benutzer: String,
    val tls: Boolean?,
    val srtp: Boolean?
) {
    /** Ein Satz für die Einstellungen – ohne Serveradresse, ohne Innenleben. */
    val satz: String
        get() {
            val an = angemeldet ?: return "Anlage gerade nicht befragbar"
            if (!an) return "nicht angemeldet – die Anlage sieht dieses Gerät nicht"
            val weg = transport.uppercase()
            val wegText = if (weg.isEmpty()) "" else " über $weg"
            if (erreichbar == false) return "angemeldet$wegText, aber nicht erreichbar"
            laufzeitMs?.let { return "angemeldet$wegText, ${Math.round(it)} ms" }
            return "angemeldet$wegText"
        }

    /** Die Anlage hat das Gerät und kann es anrufen. */
    val gesund: Boolean get() = angemeldet == true && erreichbar != false

    companion object {
        private fun JSONObject.dreiwertig(name: String): Boolean? =
            if (has(name) && !isNull(name)) optBoolean(name) else null

        fun aus(o: JSONObject) = Geraetesicht(
            angemeldet = o.dreiwertig("angemeldet"),
            erreichbar = o.dreiwertig("erreichbar"),
            transport = o.optString("transport"),
            laufzeitMs = if (o.has("laufzeitMs") && !o.isNull("laufzeitMs")) o.optDouble("laufzeitMs") else null,
            benutzer = o.optString("benutzer"),
            tls = o.dreiwertig("tls"),
            srtp = o.dreiwertig("srtp")
        )
    }
}

/** Der Dienststand des Menschen (GET/POST /dienst). */
data class Dienststand(val benutzer: String, val ausserDienst: Boolean) {
    companion object {
        fun aus(o: JSONObject) = Dienststand(
            benutzer = o.optString("benutzer"),
            ausserDienst = o.optBoolean("ausserDienst", false)
        )
    }
}

/**
 * Die Antwort auf eine Dienst-Anfrage: entweder der Stand oder die
 * Begründung der Anlage im Klartext – nie beides, nie keines.
 */
data class Dienstantwort(val stand: Dienststand?, val fehler: String?)
