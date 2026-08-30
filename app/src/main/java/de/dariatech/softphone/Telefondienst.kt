package de.dariatech.softphone

import org.linphone.core.RegistrationState

/**
 * Der Zustand des Telefondienstes – in Worten, die jemand versteht.
 *
 * DER ANLASS ist eine Ansage des Inhabers vom 30.08.2026: Der Zustand
 * des Telefondienstes gehört in die Einstellungen, nicht nur in die
 * Leiste oben.
 *
 * WAS VORHER STAND: Bei einem Fehlschlag reichte die App durch, was
 * Liblinphone lieferte – „Anmeldung fehlgeschlagen: Forbidden". Das ist
 * ein englisches Wort aus einem SIP-Protokoll. Wer es liest, weiß
 * nicht, ob sein Passwort falsch ist, die Nebenstelle nicht existiert
 * oder das Netz klemmt. Und er liest es genau in dem Moment, in dem er
 * telefonieren will.
 *
 * DIE ÜBERSETZUNG IST DER EIGENTLICHE INHALT dieser Datei. Ein Zustand
 * ohne Grund ist eine halbe Auskunft; ein Grund im Fachjargon ist gar
 * keine.
 *
 * Dasselbe gibt es auf iOS (`Telefondienst.swift`) mit denselben
 * Sätzen. Zwei Apps, die verschieden erklären, erzeugen zwei
 * Fehlerbilder für dieselbe Ursache.
 */
enum class Dienstzustand {
    /** Angemeldet, die Anlage nimmt Anrufe an. */
    VERBUNDEN,

    /** Die Anmeldung läuft gerade. */
    LAEUFT,

    /** Angemeldet werden konnte nicht – der Grund steht daneben. */
    GETRENNT,

    /**
     * Es wurden noch keine Zugangsdaten eingetragen.
     *
     * EIN EIGENER ZUSTAND, kein „getrennt": Wer nichts eingetragen hat,
     * hat keine Störung. Ihm ein rotes „Nicht verbunden" hinzustellen,
     * schickt ihn auf Fehlersuche statt in das Feld darunter.
     */
    UNVOLLSTAENDIG
}

object Telefondienst {

    /** Der Zustand als Wort – nie nur als Farbe. */
    fun bezeichnung(zustand: Dienstzustand): String = when (zustand) {
        Dienstzustand.VERBUNDEN -> "Verbunden"
        Dienstzustand.LAEUFT -> "Wird angemeldet …"
        Dienstzustand.GETRENNT -> "Nicht verbunden"
        Dienstzustand.UNVOLLSTAENDIG -> "Noch nicht eingerichtet"
    }

    fun zustandAus(state: RegistrationState?): Dienstzustand = when (state) {
        RegistrationState.Ok -> Dienstzustand.VERBUNDEN
        RegistrationState.Progress -> Dienstzustand.LAEUFT
        else -> Dienstzustand.GETRENNT
    }

    /**
     * Aus der SIP-Meldung einen Satz machen, der weiterhilft.
     *
     * Geprüft wird auf Bestandteile und nicht auf Gleichheit: Was
     * Liblinphone durchreicht, ist mal „Forbidden", mal
     * „403 Forbidden", mal ein Satz mit dem Wort darin. Ein Vergleich
     * auf Gleichheit hätte in genau dem Fall danebengegriffen, in dem
     * die Auskunft gebraucht wird.
     *
     * DIE ORIGINALMELDUNG BLEIBT ERHALTEN, wo sie unbekannt ist – eine
     * Meldung, die niemand übersetzt hat, ist immer noch mehr wert als
     * eine leere Zeile. Der Kundendienst kann damit arbeiten.
     */
    fun grund(meldung: String): String {
        val m = meldung.lowercase()
        return when {
            m.contains("unauthorized") || m.contains("401") ->
                "Benutzername oder Passwort stimmt nicht."
            m.contains("forbidden") || m.contains("403") ->
                "Die Anlage weist diese Anmeldung ab. Meist ist die Nebenstelle " +
                    "gesperrt oder für ein anderes Gerät vergeben."
            m.contains("not found") || m.contains("404") ->
                "Diese Nebenstelle gibt es auf der Anlage nicht."
            m.contains("timeout") || m.contains("408") ->
                "Die Anlage antwortet nicht. Meist liegt es am Netz – im " +
                    "WLAN kann auch ein Router mit SIP-Hilfe (SIP-ALG) dazwischenfunken."
            m.contains("service unavailable") || m.contains("503") ->
                "Die Anlage nimmt gerade keine Anmeldungen an. Später erneut versuchen."
            m.contains("io error") || m.contains("no route") || m.contains("unreachable") ->
                "Keine Verbindung zur Anlage. Mobilfunk oder WLAN prüfen."
            meldung.isBlank() -> ""
            else -> meldung
        }
    }
}
