package de.dariatech.softphone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Die Benachrichtigung, die einen eingehenden Anruf meldet.
 *
 * DER ANLASS steht seit Wochen als offener Punkt in der CLAUDE.md
 * („CallStyle-Benachrichtigungen und Vollbild-Anrufannahme") und ist mit
 * dem Auftrag vom 30.08.2026 fällig geworden.
 *
 * DER SCHADEN OHNE SIE: Ein eingehender Anruf war nur zu sehen, wenn die
 * App offen war. Lag das Telefon gesperrt in der Tasche – der
 * Normalfall –, klingelte es zwar, aber auf dem Sperrbildschirm stand
 * nichts. Wer nicht rechtzeitig entsperrte und die App suchte, verpasste
 * den Anruf.
 *
 * WARUM CallStyle UND NICHT EINE GEWÖHNLICHE MELDUNG: Android behandelt
 * sie anders. Sie darf oben am Sperrbildschirm stehen, sie hat
 * „Annehmen" und „Ablehnen" als richtige Knöpfe statt als kleine
 * Textzeilen, und sie wird nicht von „Nicht stören" verschluckt. Genau
 * das ist bei einem Anruf der Unterschied zwischen gemeldet und nicht
 * gemeldet.
 *
 * DER VOLLBILD-VERSUCH (setFullScreenIntent) ist eine BITTE, keine
 * Zusage. Ist der Bildschirm an und das Gerät entsperrt, zeigt Android
 * stattdessen die Meldung oben – und das ist richtig so: Niemand will,
 * dass eine App ihm mitten in der Arbeit den ganzen Bildschirm nimmt.
 * Bei gesperrtem Gerät wird daraus die Vollbild-Annahme.
 */
object Anrufmeldung {

    private const val KANAL_ANRUF = "anruf"
    private const val KANAL_LAUFEND = "laufend"
    const val MELDUNG_ID = 4711

    /**
     * Die Kanäle anlegen. Zweimal denselben anzulegen ist harmlos –
     * Android ersetzt nichts, was schon da ist, und genau darauf ist
     * Verlass.
     */
    fun kanaeleAnlegen(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        // WICHTIGKEIT „HIGH" ist bei einem Anruf keine Angeberei: Nur
        // damit darf die Meldung überhaupt oben einblenden und den
        // Vollbild-Versuch stellen.
        val anruf = NotificationChannel(
            KANAL_ANRUF,
            context.getString(R.string.kanal_anruf),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.kanal_anruf_text)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        // Das laufende Gespräch soll NICHT klingeln und nicht vibrieren –
        // es meldet nur, dass etwas läuft, und bietet das Auflegen an.
        val laufend = NotificationChannel(
            KANAL_LAUFEND,
            context.getString(R.string.kanal_laufend),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.kanal_laufend_text)
            setShowBadge(false)
        }

        manager.createNotificationChannel(anruf)
        manager.createNotificationChannel(laufend)
    }

    private fun absicht(context: Context, was: String): PendingIntent {
        val i = Intent(context, MainActivity::class.java).apply {
            action = was
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, was.hashCode(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Ein eingehender Anruf. */
    fun eingehend(context: Context, nummer: String) {
        kanaeleAnlegen(context)
        val anzeige = nummer.ifBlank { context.getString(R.string.unbekannter_anrufer) }
        val annehmen = absicht(context, MainActivity.AKTION_ANNEHMEN)
        val ablehnen = absicht(context, MainActivity.AKTION_ABLEHNEN)

        val bau = NotificationCompat.Builder(context, KANAL_ANRUF)
            .setSmallIcon(R.drawable.ic_anrufe)
            .setContentTitle(anzeige)
            .setContentText(context.getString(R.string.eingehender_anruf))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(false)
            // Der Versuch, den Bildschirm zu übernehmen. Siehe Kopf:
            // Bei entsperrtem Gerät wird daraus eine Einblendung oben.
            .setFullScreenIntent(annehmen, true)

        // NotificationCompat.CallStyle und NICHT Notification.CallStyle.
        //
        // GEZOGEN beim ersten Bau: Die Fassung aus dem Android-Rahmen
        // gibt es erst ab Android 12, und sie lässt sich nicht in einen
        // NotificationCompat.Builder hineinreichen –
        // „Unresolved reference: extend". Die Fassung aus AndroidX kann
        // beides: Ab Android 12 wird daraus die echte Anrufmeldung, davor
        // baut sie selbst zwei gewöhnliche Knöpfe. Eine Verzweigung
        // weniger, die falsch sein kann.
        val wer = androidx.core.app.Person.Builder()
            .setName(anzeige)
            .setImportant(true)
            .build()
        bau.setStyle(NotificationCompat.CallStyle.forIncomingCall(wer, ablehnen, annehmen))

        zeige(context, bau.build())
    }

    /** Ein laufendes Gespräch – leise, mit Auflegen. */
    fun laufend(context: Context, nummer: String) {
        kanaeleAnlegen(context)
        val anzeige = nummer.ifBlank { context.getString(R.string.unbekannter_anrufer) }
        val bau = NotificationCompat.Builder(context, KANAL_LAUFEND)
            .setSmallIcon(R.drawable.ic_anrufe)
            .setContentTitle(anzeige)
            .setContentText(context.getString(R.string.laufendes_gespraech))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(absicht(context, MainActivity.AKTION_OEFFNEN))
            .addAction(0, context.getString(R.string.hangup), absicht(context, MainActivity.AKTION_AUFLEGEN))
        zeige(context, bau.build())
    }

    fun weg(context: Context) {
        NotificationManagerCompat.from(context).cancel(MELDUNG_ID)
    }

    /**
     * Anzeigen – und ein fehlendes Recht nicht als Absturz behandeln.
     *
     * Seit Android 13 muss der Benutzer Benachrichtigungen ausdrücklich
     * erlauben. Lehnt er ab, wirft `notify` eine SecurityException. Ein
     * Telefonat darf daran nicht scheitern: Es klingelt weiter, und wer
     * die App offen hat, sieht den Anruf ohnehin.
     */
    private fun zeige(context: Context, meldung: Notification) {
        try {
            NotificationManagerCompat.from(context).notify(MELDUNG_ID, meldung)
        } catch (_: SecurityException) {
            // Kein Recht – siehe oben. Kein Grund für einen Absturz.
        }
    }
}
