package de.dariatech.softphone

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File

/**
 * Der Tresor: Dateien im Gerät, verschlüsselt.
 *
 * DER AUFTRAG (Inhaber, 06.09.2026): „Es muss alles verschlüsselt
 * werden." Stufe 1c des Masterplans (im Repository der Anlage:
 * docs/AUFTRAG-VERSCHLUESSELUNG.md) – nach der Anlage jetzt das Gerät.
 *
 * DER BEFUND: Das SIP-Passwort und das Token liegen seit dem 30.08.2026
 * im verschlüsselten Speicher. Die INHALTE nicht: Der Chatverlauf lag
 * als `nachrichten.json` in `filesDir`, die Bilder der Kollegen als
 * JPEG in `cacheDir`.
 *
 * WER DAS LIEST: Wer das Gerät gerootet hat, wer eine Sicherung
 * ausliest, wer ein weitergegebenes Diensthandy in die Hand bekommt.
 * Auf einem Firmentelefon, das zwischen Mitarbeitern wandert, ist das
 * der wahrscheinlichste Fall von allen.
 *
 * DERSELBE BAUSTEIN WIE BEIM PASSWORT: `EncryptedFile` aus
 * androidx.security, mit dem Schlüssel im Android-Keystore. Er verlässt
 * den Keystore nie; auf Geräten mit sicherem Element steckt er in
 * Hardware. KEIN eigener Krypto-Code – wer ihn selbst schreibt, macht
 * ihn falsch, und der Schlüssel landet am Ende im Quelltext.
 *
 * BESTANDSSCHUTZ: Eine Datei aus der Zeit davor ist kein Umschlag.
 * `lies` gibt sie unverändert zurück, damit der Verlauf beim Update
 * nicht verschwindet – beim nächsten Schreiben zieht sie um.
 *
 * KEIN ABSTURZ, WENN DER KEYSTORE KLEMMT. Ein Gerät mit kaputtem
 * Keystore ist selten, aber es gibt ihn (dieselbe Erfahrung wie im
 * Zugangsspeicher). Dann wird laut protokolliert und im Klartext
 * gearbeitet: Ein Chat, der nicht mehr startet, ist der schlimmere
 * Schaden.
 */
object Tresor {
    private const val TAG = "Tresor"

    private fun schluessel(context: Context): MasterKey? = try {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    } catch (e: Exception) {
        Log.e(TAG, "Keystore nicht verfügbar: ${e.message}")
        null
    }

    /** Etwas ablegen. Ohne Keystore im Klartext – laut protokolliert. */
    fun schreibe(context: Context, datei: File, daten: ByteArray) {
        datei.parentFile?.mkdirs()
        val key = schluessel(context)
        if (key == null) {
            datei.writeBytes(daten)
            return
        }
        try {
            /* EncryptedFile schreibt nur in eine NEUE Datei – eine
               vorhandene muss weg, sonst wirft es. Das ist der
               Stolperstein dieses Bausteins. */
            if (datei.exists()) datei.delete()
            EncryptedFile.Builder(
                context, datei, key, EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build().openFileOutput().use { it.write(daten) }
        } catch (e: Exception) {
            Log.e(TAG, "Verschlüsseltes Schreiben fehlgeschlagen: ${e.message}")
            datei.writeBytes(daten)
        }
    }

    /**
     * Etwas lesen – oder `null`, wenn es nichts gibt.
     *
     * Erst der Umschlag, dann der Altbestand: Eine Datei, die vor
     * dieser Änderung entstanden ist, lässt sich nicht entschlüsseln
     * und wird unverändert zurückgegeben.
     */
    fun lies(context: Context, datei: File): ByteArray? {
        if (!datei.exists()) return null
        val key = schluessel(context)
        if (key != null) {
            try {
                return EncryptedFile.Builder(
                    context, datei, key, EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                ).build().openFileInput().use { it.readBytes() }
            } catch (e: Exception) {
                /* Kein Umschlag (Altbestand) oder nicht zu öffnen. Der
                   Altbestand wird gleich versucht; ist auch das nichts,
                   gibt es null statt Kauderwelsch. */
                Log.i(TAG, "Nicht als Umschlag lesbar (${datei.name}) – versuche Altbestand")
            }
        }
        return try {
            datei.readBytes()
        } catch (e: Exception) {
            Log.w(TAG, "Datei nicht lesbar: ${e.message}")
            null
        }
    }
}
