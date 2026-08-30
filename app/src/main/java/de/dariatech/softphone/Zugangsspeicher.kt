package de.dariatech.softphone

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Wo die SIP-Zugangsdaten liegen – und warum das Passwort woanders liegt
 * als der Rest.
 *
 * DER ANLASS ist ein Auftrag des Inhabers vom 30.08.2026, die Apps
 * „vollständig, sauber und sicher" auf höchstes Niveau zu bringen. Beim
 * Nachsehen stand das SIP-Passwort in gewöhnlichen SharedPreferences:
 *
 *     prefs.edit().putString("password", …)
 *
 * Das ist eine XML-Datei im App-Verzeichnis, im Klartext. Wer das Gerät
 * root hat, wer eine Sicherung ausliest, wer ein gebrauchtes
 * Diensthandy in die Hand bekommt – alle lesen mit.
 *
 * UND EIN SIP-PASSWORT IST NICHT IRGENDEINES. Damit telefoniert jemand
 * auf Kosten des Kunden: ins Ausland, in Premium-Nummern, nachts, in
 * Mengen. Genau der Schaden, gegen den in der Anlage die
 * Gebührenbetrugs-Erkennung gebaut wurde – nur dass sie erst greift,
 * wenn es schon läuft.
 *
 * WAS HIER LIEGT UND WAS NICHT:
 *
 *   verschlüsselt : ausschließlich das Passwort
 *   gewöhnlich    : Benutzername, Domain, Transport, Anbietervorlage
 *
 * Das ist Absicht. Der verschlüsselte Speicher braucht den
 * Android-Keystore; schlägt er fehl (ein Gerät mit kaputtem Keystore
 * ist selten, aber es gibt ihn), soll die App trotzdem starten und der
 * Benutzer seinen Namen und seine Domain noch sehen. Was dann fehlt,
 * ist das Passwort – und das ist die richtige Seite des Irrtums.
 */
object Zugangsspeicher {
    private const val TAG = "Zugangsspeicher"
    private const val ALT = "sip"
    private const val SICHER = "sip-sicher"
    private const val PASSWORT = "passwort"
    /** Der Schlüsselname der ALTEN, unverschlüsselten Ablage. */
    private const val ALTES_FELD = "password"

    private var sicher: SharedPreferences? = null

    /** Die gewöhnliche Ablage – alles außer dem Passwort. */
    fun offen(context: Context): SharedPreferences =
        context.getSharedPreferences(ALT, Context.MODE_PRIVATE)

    /**
     * Die verschlüsselte Ablage.
     *
     * Der Schlüssel liegt im Android-Keystore und verlässt ihn nie; auf
     * Geräten mit sicherem Element steckt er in Hardware. Verschlüsselt
     * werden Schlüsselnamen UND Werte – sonst verriete schon die Liste
     * der Namen, was gespeichert ist.
     */
    private fun sicher(context: Context): SharedPreferences? {
        sicher?.let { return it }
        return try {
            val schluessel = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                SICHER,
                schluessel,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            ).also { sicher = it }
        } catch (e: Exception) {
            // LAUT WERDEN, ABER NICHT ABSTÜRZEN. Ohne diesen Speicher
            // fehlt das Passwort – die App bleibt bedienbar, und der
            // Benutzer kann es neu eintragen.
            Log.e(TAG, "Verschlüsselter Speicher nicht verfügbar: ${e.message}")
            null
        }
    }

    /**
     * Ein Passwort aus der alten Klartext-Ablage übernehmen.
     *
     * BESTANDSSCHUTZ. Wer die App seit Monaten benutzt, hat sein
     * Passwort im alten Speicher. Es zu ignorieren hieße: Er ist beim
     * nächsten Start abgemeldet und weiß nicht warum. Es stehen zu
     * lassen hieße: Die Lücke bleibt offen. Also umziehen – und den
     * alten Eintrag danach entfernen.
     *
     * Gerufen wird das genau einmal, beim ersten Lesen. Steht im neuen
     * Speicher schon etwas, wird nichts überschrieben: Der neue Stand
     * ist immer der jüngere.
     */
    private fun ziehUm(context: Context, ziel: SharedPreferences) {
        val alt = offen(context)
        val altesPasswort = alt.getString(ALTES_FELD, "") ?: ""
        if (altesPasswort.isEmpty()) return
        if (ziel.getString(PASSWORT, "").isNullOrEmpty()) {
            ziel.edit().putString(PASSWORT, altesPasswort).apply()
        }
        alt.edit().remove(ALTES_FELD).apply()
        Log.i(TAG, "Altes Passwort in den verschlüsselten Speicher übernommen und gelöscht.")
    }

    /** Das SIP-Passwort. Leer, wenn keines hinterlegt ist. */
    fun passwort(context: Context): String {
        val s = sicher(context) ?: return ""
        ziehUm(context, s)
        return s.getString(PASSWORT, "") ?: ""
    }

    /** Das SIP-Passwort setzen. Leer löscht es. */
    fun setzePasswort(context: Context, wert: String) {
        val s = sicher(context) ?: return
        if (wert.isEmpty()) s.edit().remove(PASSWORT).apply()
        else s.edit().putString(PASSWORT, wert).apply()
        // Auch beim Setzen: Ein Altbestand darf nicht liegen bleiben.
        offen(context).edit().remove(ALTES_FELD).apply()
    }
}
