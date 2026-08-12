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
            "Admin-Portal unter „Nebenstellen“ (Knopf „Kopieren“)."
    ),
    ProviderPreset(
        "easybell", "easybell", "sip.easybell.de", "UDP",
        "Zugangsdaten aus dem easybell-Portal: pro Nebenstelle ein Gerät vom Typ " +
            "„SIP-Telefon“ anlegen – dessen Benutzername/Passwort hier eintragen."
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
