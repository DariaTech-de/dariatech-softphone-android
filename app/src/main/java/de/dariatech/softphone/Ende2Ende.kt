package de.dariatech.softphone

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.crypto.tink.subtle.Hkdf
import com.google.crypto.tink.subtle.X25519
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Ende-zu-Ende-Verschlüsselung für den Chat.
 *
 * DER AUFTRAG (Inhaber, 06.09.2026): „Es muss alles verschlüsselt
 * werden." Stufe 3 des Masterplans (im Repository der Anlage:
 * docs/AUFTRAG-VERSCHLUESSELUNG.md).
 *
 * DER UNTERSCHIED ZU VORHER, und er ist der ganze Punkt: Seit Stufe 1
 * liegt der Chat verschlüsselt auf der Platte der Anlage – aber mit dem
 * Schlüssel DER ANLAGE. Wer sie betreibt, konnte mitlesen; ein
 * Administrator im Portal hätte jeden Chat des Hauses lesen können.
 *
 * AB HIER liegt der private Schlüssel im verschlüsselten Speicher
 * dieses Telefons (Android-Keystore) und verlässt es nie.
 *
 * WARUM TINK UND NICHT ANDROIDS EIGENE KRYPTO: X25519 gibt es in
 * Android als `XDH` erst ab API 33; diese App läuft ab API 26. Ein
 * halbes Haus stünde sonst ohne Verschlüsselung da – und die Kurve von
 * Hand zu rechnen ist genau die Stelle, an der stille Fehler wohnen.
 * Der eigene Code dieses Hauses steht im SIP- und Faxstapel, nicht in
 * der Zahlentheorie.
 *
 * JE GERÄT UND NICHT JE MENSCH: Ein Mensch hat ein Telefon, einen
 * Rechner und vielleicht ein Tablet. Ein Schlüssel je Mensch müsste
 * zwischen den Geräten wandern – und ein privater Schlüssel, der
 * wandert, ist keiner mehr.
 *
 * WAS DAS KOSTET, und es wird nicht verschwiegen: Wer ALLE seine
 * Geräte verliert, verliert den Verlauf. Niemand kann ihn dann noch
 * herausgeben – auch die Anlage nicht.
 */
object Ende2Ende {
    private const val TAG = "Ende2Ende"
    /** Feld im VERSCHLÜSSELTEN Speicher – nie im offenen. */
    private const val PRIVAT = "e2e-privat"
    /** Die Gerätekennung darf offen liegen: Sie ist kein Geheimnis. */
    private const val GERAET = "e2e.geraet"

    /** base64 ohne Zeilenumbrüche – die Anlage erwartet eine Zeile. */
    private const val B64 = Base64.NO_WRAP

    /**
     * Die Kennung DIESES Geräts – stabil, EINMAL gewürfelt.
     *
     * Nicht die ANDROID_ID: Die wechselt beim Zurücksetzen und ist
     * gleichzeitig eine Kennung, die man nicht ohne Not weitergibt.
     */
    fun geraetId(context: Context): String {
        val offen = Zugangsspeicher.offen(context)
        val vorhanden = offen.getString(GERAET, "") ?: ""
        if (vorhanden.isNotEmpty()) return vorhanden
        val roh = ByteArray(8).also { SecureRandom().nextBytes(it) }
        val neu = "android-" + roh.joinToString("") { "%02x".format(it) }
        offen.edit().putString(GERAET, neu).apply()
        return neu
    }

    /**
     * Der private Schlüssel dieses Geräts – aus dem verschlüsselten
     * Speicher, oder beim ersten Mal frisch erzeugt.
     *
     * Gibt `null` zurück, wenn der Keystore nicht zu haben ist. Dann
     * chattet die App wie bisher mit Klartext: LAUT WERDEN, ABER NICHT
     * ABSTÜRZEN – dieselbe Regel wie beim Zugangsspeicher.
     */
    private fun privat(context: Context): ByteArray? {
        val gespeichert = Zugangsspeicher.lies(context, PRIVAT)
        if (gespeichert.isNotEmpty()) {
            val roh = runCatching { Base64.decode(gespeichert, B64) }.getOrNull()
            if (roh != null && roh.size == 32) return roh
        }
        return try {
            val neu = X25519.generatePrivateKey()
            Zugangsspeicher.setze(context, PRIVAT, Base64.encodeToString(neu, B64))
            neu
        } catch (e: Exception) {
            Log.e(TAG, "Kein Schlüsselpaar möglich: ${e.message}")
            null
        }
    }

    /** Der öffentliche Teil, so wie ihn die Anlage erwartet: 32 Byte, base64. */
    fun oeffentlich(context: Context): String {
        val p = privat(context) ?: return ""
        return try {
            Base64.encodeToString(X25519.publicFromPrivate(p), B64)
        } catch (e: Exception) {
            Log.e(TAG, "Öffentlicher Schlüssel nicht ableitbar: ${e.message}")
            ""
        }
    }

    /**
     * Alles vergessen – beim Abmelden.
     *
     * Der Verlauf im Gerät wird damit unlesbar, und das ist richtig:
     * Ein Gerät, das den Besitzer wechselt, darf die Nachrichten des
     * Vorigen nicht mehr hergeben.
     */
    fun vergiss(context: Context) {
        Zugangsspeicher.setze(context, PRIVAT, "")
        Zugangsspeicher.offen(context).edit().remove(GERAET).apply()
    }

    /**
     * Der Fingerabdruck zum VORLESEN.
     *
     * Fünf Gruppen zu vier Zeichen aus dem SHA-256 des Schlüssels.
     * DIESELBE RECHNUNG wie in der Anlage und in der iPhone-App – zwei
     * verschiedene Fingerabdrücke für denselben Schlüssel wären
     * schlimmer als keiner.
     *
     * KEINE VERWECHSELBAREN ZEICHEN: 0/O und 1/I/L fehlen. Wer „O wie
     * Otto" sagen muss, liest keinen Fingerabdruck mehr vor.
     */
    fun fingerabdruck(oeffentlichBase64: String): String {
        val alphabet = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
        val h = MessageDigest.getInstance("SHA-256").digest(oeffentlichBase64.toByteArray())
        val aus = StringBuilder()
        for (i in 0 until 20) {
            if (i > 0 && i % 4 == 0) aus.append('-')
            aus.append(alphabet[(h[i].toInt() and 0xff) % alphabet.length])
        }
        return aus.toString()
    }

    /** Der gemeinsame Sitzungsschlüssel mit genau einem fremden Gerät. */
    private fun sitzungsschluessel(context: Context, fremdBase64: String): ByteArray? {
        val meiner = privat(context) ?: return null
        return try {
            val fremd = Base64.decode(fremdBase64, B64)
            if (fremd.size != 32) return null
            val gemeinsam = X25519.computeSharedSecret(meiner, fremd)
            // HKDF, nicht der rohe DH-Wert: Der ist ein Punkt auf einer
            // Kurve und kein Schlüssel. Dieselbe Ableitung wie in den
            // anderen Apps – „DariaTech-Chat" ist die Klammer.
            Hkdf.computeHkdf(
                "HMACSHA256", gemeinsam, ByteArray(0),
                "DariaTech-Chat".toByteArray(), 32
            )
        } catch (e: Exception) {
            Log.w(TAG, "Sitzungsschlüssel nicht ableitbar: ${e.message}")
            null
        }
    }

    /**
     * Einen Umschlag je Empfängergerät bauen.
     *
     * `geraete` sind die Geräte des Empfängers UND die eigenen anderen –
     * ohne die zweiten fehlte dem Absender sein eigener Verlauf.
     *
     * Ein Gerät, dessen Schlüssel sich nicht lesen lässt, wird
     * ÜBERSPRUNGEN und nicht zum Fehler: Die Nachricht soll die übrigen
     * Geräte trotzdem erreichen.
     */
    fun verschliesse(
        context: Context,
        text: String,
        geraete: List<Geraeteschluessel>
    ): Map<String, String> {
        val aus = mutableMapOf<String, String>()
        for (g in geraete) {
            val schluessel = sitzungsschluessel(context, g.schluessel) ?: continue
            try {
                val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
                val c = Cipher.getInstance("AES/GCM/NoPadding")
                c.init(
                    Cipher.ENCRYPT_MODE,
                    SecretKeySpec(schluessel, "AES"),
                    GCMParameterSpec(128, iv)
                )
                val ct = c.doFinal(text.toByteArray())
                aus[g.id] = Base64.encodeToString(iv + ct, B64)
            } catch (e: Exception) {
                Log.w(TAG, "Umschlag für ${g.id} nicht möglich: ${e.message}")
            }
        }
        return aus
    }

    /**
     * Den eigenen Umschlag öffnen – oder `null`.
     *
     * `null` heißt: nicht anzeigen. Kauderwelsch in einen Chat zu
     * schreiben wäre schlimmer als eine fehlende Nachricht.
     */
    fun oeffne(context: Context, umschlag: String, vonGeraet: String): String? {
        val schluessel = sitzungsschluessel(context, vonGeraet) ?: return null
        return try {
            val roh = Base64.decode(umschlag, B64)
            if (roh.size < 29) return null
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(schluessel, "AES"),
                GCMParameterSpec(128, roh, 0, 12)
            )
            String(c.doFinal(roh, 12, roh.size - 12))
        } catch (e: Exception) {
            Log.w(TAG, "Umschlag nicht zu öffnen: ${e.message}")
            null
        }
    }
}
