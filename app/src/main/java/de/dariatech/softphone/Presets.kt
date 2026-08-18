package de.dariatech.softphone

/**
 * Anbieter-Vorlagen – Spiegel der Desktop-App (src/utils/providerPresets.ts).
 * Eine Vorlage füllt Domain/Transport vor; alles bleibt manuell änderbar.
 */
data class ProviderPreset(
    val id: String,
    val label: String,
    val domain: String,
    val transport: String,
    val hint: String
)

val PROVIDER_PRESETS = listOf(
    ProviderPreset(
        "dariatech-pbx", "DariaTech PBX", "pbx.dariatech.de", "UDP",
        "DariaTech-Telefonanlage. Benutzername und SIP-Passwort stehen im " +
            "Admin-Portal unter „Nebenstellen“ (Knopf „Kopieren“). ACHTUNG bei der " +
            "Serveradresse: Steht vor dem Portal ein Zugangsschutz wie Cloudflare " +
            "Access, gilt dessen Adresse NUR für die Weboberfläche – SIP läuft dort " +
            "nicht durch. Maßgeblich ist die Adresse, die im Portal bei den " +
            "Nebenstellen als SIP-Server angezeigt wird; im Zweifel die direkte " +
            "Adresse oder IP des Anlagenservers."
    ),
    // Zwei Vorlagen statt einer: easybell hat zwei verschiedene Produkte mit
    // verschiedenen Registraren. Vorher stand hier sip.easybell.de mit einem
    // Hinweistext, der das Cloud-Produkt beschrieb – wer dem folgte, trug
    // Cloud-Zugangsdaten gegen den Trunk-Registrar ein und bekam eine
    // Registrierung, die einfach nicht zustande kommt, ohne Fehlermeldung.
    ProviderPreset(
        "easybell-cloud", "easybell Cloud-Telefonanlage", "pbx.easybell.de", "UDP",
        "Für Endgeräte der easybell CLOUD-TELEFONANLAGE (Benutzer wie „CPBX-…“): im " +
            "easybell-Portal unter Endgeräte anlegen und dessen SIP-Benutzername/" +
            "Passwort hier eintragen. Registrar ist pbx.easybell.de."
    ),
    ProviderPreset(
        "easybell-trunk", "easybell SIP-Trunk / Telefonanschluss", "sip.easybell.de", "UDP",
        "Für den direkten easybell-TELEFONANSCHLUSS (SIP-Zugangsdaten aus dem Portal " +
            "unter Telefonanschluss): Registrar sip.easybell.de. Nicht für Endgeräte " +
            "der Cloud-Telefonanlage – dafür die eigene Vorlage wählen."
    ),
    ProviderPreset(
        "sipgate", "sipgate", "sipgate.de", "UDP",
        "Benutzername = SIP-ID (z. B. 1234567e0), Passwort = SIP-Passwort."
    ),
    ProviderPreset(
        "telekom", "Telekom CompanyFlex", "tel.t-online.de", "UDP",
        "Benutzername = vollständige Rufnummer (+49…), Passwort aus dem CompanyFlex-Portal."
    ),
    ProviderPreset(
        "placetel", "Placetel", "fpbx.de", "UDP",
        "SIP-Zugangsdaten aus dem Placetel-Webportal (Telefonie → SIP-Zugangsdaten)."
    ),
    ProviderPreset(
        "fonial", "fonial", "sip.fonial.de", "UDP",
        "Zugangsdaten eines fonial-Endgeräts (Kundenkonto → Geräte)."
    ),
    ProviderPreset(
        "1und1", "1&1 Versatel", "sip.1und1.de", "UDP",
        "SIP-Zugangsdaten aus dem 1&1 Control-Center."
    ),
    ProviderPreset(
        "fritzbox", "FRITZ!Box (lokal)", "fritz.box", "UDP",
        "In der FRITZ!Box ein IP-Telefon (LAN/WLAN) anlegen; dessen Zugangsdaten eintragen."
    ),
    ProviderPreset(
        "custom", "Anderer Anbieter …", "", "UDP",
        "Registrar/Domain laut Anbieter-Unterlagen eintragen."
    )
)
