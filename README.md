# DariaTech Softphone – Android

Native Android-App zum DariaTech Softphone: gleiches Branding, gleiche
SIP-Konten (z. B. easybell-Direktregistrierung über UDP/TCP/TLS),
Telefonie-Kern **Liblinphone**, Oberfläche in Kotlin/Material 3.

Die übrigen Teile liegen in eigenen Repositories:

| Teil | Repository |
|---|---|
| Telefonanlage (PBX) und Portal | `DariaTech-de/dariatech-pbx` |
| Softphone für Windows, macOS, Linux | `DariaTech-de/DariaTech-Softphone` |
| Softphone für iOS | `DariaTech-de/dariatech-softphone-ios` |

---

## Bauen

**Voraussetzungen:** Java 17, Android SDK 34, Gradle 8.7.

```bash
gradle assembleDebug --no-daemon
```

Die fertige Datei liegt danach unter
`app/build/outputs/apk/debug/app-debug.apk`.

Mit Android Studio: dieses Verzeichnis als Projekt öffnen.

**Automatisch:** Der Ablauf **Android**
(`.github/workflows/mobile-android.yml`) baut bei jedem Push auf `main`
und auf Wunsch über *Run workflow*. Die APK erscheint als Vorabversion
**`mobile-build-N`** unter *Releases*.

## Auf dem Gerät installieren

APK herunterladen, in den Android-Einstellungen „Installation aus
unbekannten Quellen“ für den Browser bzw. die Dateiverwaltung erlauben,
Datei öffnen.

Eine Verteilung über den Play Store setzt ein Google-Play-Konto
(25 $ einmalig) und eine Release-Signierung voraus; beides ist noch
nicht eingerichtet.

## Einrichten

Beim ersten Start Konto anlegen: Benutzername, Passwort, Domain,
Transport (UDP/TCP/TLS). Für die DariaTech-Anlage gibt es eine Vorlage
(`Presets.kt`), die Domain und Transport vorbelegt.

## Schnittstelle zur Anlage

Was die App bei der Anlage abholt, steht in `pbx/docs/CLIENT-API.md` im
Repository `DariaTech-de/dariatech-pbx`. Diese Schnittstelle wird
**erweitert, nicht umgebaut** – eine App im Feld lässt sich nicht
gleichzeitig mit dem Server aktualisieren.

## Mitarbeiten

[`CLAUDE.md`](CLAUDE.md) ist verbindlich: Deutsch in allem, ROT→GRÜN vor
jeder Änderung, ausführliche Commit-Nachrichten.
