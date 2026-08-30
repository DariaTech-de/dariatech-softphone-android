# Einweisung für KI-Agenten

Diese Datei wird von Claude Code automatisch gelesen. Wer hier anfängt zu
arbeiten, liest sie zuerst und hält sich daran.

---

## Was das hier ist

**DariaTech Softphone für Android** – die native Begleit-App zum
Desktop-Softphone. Kein Bastelprojekt: Es telefonieren echte Kunden
damit, mit echten Rufnummern und echten Notrufen.

Dieses Repository enthält **nur** die Android-App. Die übrigen Teile
liegen woanders:

| Teil | Repository |
|---|---|
| Telefonanlage (PBX), Portal, KI-Assistent | `DariaTech-de/dariatech-pbx` |
| Softphone für Windows, macOS, Linux | `DariaTech-de/DariaTech-Softphone` |
| Softphone für iOS | `DariaTech-de/dariatech-softphone-ios` |

Der Telefonie-Kern ist **Liblinphone**
(`org.linphone:linphone-sdk-android`) – ein vollständiger SIP/RTP-Stack
mit Audio-Engine und Echo-Unterdrückung. Das ist der wichtigste
Entscheid dieser App: Auf Mobilgeräten ist Audio-Routing, Bluetooth und
die Unterbrechung durch GSM-Anrufe ein eigenes Fachgebiet; eine
Eigenentwicklung wäre auf Jahre schlechter.

Die Oberfläche ist nativ (Kotlin, Material 3) im DariaTech-Design.

---

## Die Schnittstelle zur Anlage

Alles, was diese App bei der Anlage abholt – Status, Warteschleifen,
Kontakte, Faxe, Provisionierung –, steht in **`pbx/docs/CLIENT-API.md`**
im Repository `DariaTech-de/dariatech-pbx`. Diese Beschreibung ist die
einzige Klammer, die nach der Aufteilung in mehrere Repositories übrig
bleibt, und sie ist am Prüfstück `pbx/src/test/client.api.ts`
festgenagelt.

> Diese Schnittstelle wird **erweitert, nicht umgebaut**. Eine App, die
> draußen im Feld steht, lässt sich nicht gleichzeitig mit dem Server
> aktualisieren.

Praktisch heißt das für diese App:

* **Unbekannte Felder werden ignoriert**, nie als Fehler behandelt. Der
  Server darf jederzeit ein Feld dazulegen.
* Ein Feldname, der hier fest verdrahtet ist, darf serverseitig nicht
  umbenannt werden. Wer das braucht, legt einen neuen Weg daneben.
* Wer hier einen neuen Weg benutzt, sorgt dafür, dass er in
  `CLIENT-API.md` steht – sonst scheitert dort das Prüfstück.

---

## Die Regeln. Ohne Ausnahme.

### 1. Alles auf Deutsch

Antworten an den Nutzer, Commit-Nachrichten, Code-Kommentare,
Fehlermeldungen, Oberflächentexte, Prüfstück-Namen. Der Nutzer ist
**temori@dariatech.de**, und seine Mitarbeiter lesen diesen Code.

Englische Bezeichner im Code sind geduldet, wo sie historisch stehen
oder wo das Android-Gerüst sie vorgibt (`onCreate`, `MainActivity`).
Neuer Code bekommt deutsche Namen.

**Achtung bei Anführungszeichen:** Das deutsche schließende `“` ist ein
anderes Zeichen als das gerade `"`. In Kotlin-Zeichenketten bricht
`„…“` den Code. In `strings.xml` gehören deutsche Anführungszeichen
hin, im Quelltext nicht.

### 2. Härtung auf höchstem Niveau, nichts kaputtmachen

Beides gilt gleichzeitig. Eine Änderung, die eine Lücke schließt und
dabei einen laufenden Kunden aussperrt, ist keine Verbesserung.

Der übliche Ausweg heißt **Bestandsschutz**: Geprüft wird, was jemand
SETZT, nicht was schon dasteht. Ein Altbestand bleibt stehen und wird
ins Log geschrieben.

### 3. Jede Änderung mit ROT→GRÜN-Nachweis

Das ist die härteste Regel und die, an der die meisten Agenten
vorbeiarbeiten.

1. **Zuerst den Test schreiben** und ihn laufen lassen. Er muss
   **fehlschlagen**, und zwar mit dem echten Schaden in der Ausgabe.
2. **Dann beheben.**
3. **Dann den Test grün sehen.**
4. Bei bestehendem Code: die Behebung zurückdrehen, den roten Lauf
   festhalten, wieder einsetzen. Die rote Ausgabe gehört in die
   Commit-Nachricht.

Ein Test, der von Anfang an grün ist, beweist nichts.

**Die Ausgabe des roten Laufs ist der Beweis, nicht die Behauptung.**

Wo eine Änderung sich nicht sinnvoll als Test fassen lässt – Layout,
Farben, Symbole –, gehört stattdessen der **Nachweis am Gerät** in die
Commit-Nachricht: welche APK, welches Android, was war vorher zu sehen,
was jetzt.

### 4. Der Bau bleibt grün

```bash
node pruefstuecke/*.mjs                # Quelltext-Verträge (ohne Gerät)
gradle assembleDebug --no-daemon       # Java 17, Android SDK 34
```

Die Prüfstücke in `pruefstuecke/` sind eigenständige Node-Skripte, wie in
der Anlage. Sie prüfen, was sich OHNE Gerät prüfen lässt – etwa dass das
SIP-Passwort nirgends in einen unverschlüsselten Speicher geschrieben
wird (`passwort.mjs`). Ein Gerätetest wäre dafür der bessere Weg, aber
`EncryptedSharedPreferences` braucht den Android-Keystore, also einen
Emulator; der steht in der Prüfumgebung nicht zur Verfügung. Ein
Quelltext-Vertrag, der den Fehler fängt, ist mehr wert als ein
Gerätetest, den niemand laufen lässt.

Derselbe Befehl läuft in `.github/workflows/mobile-android.yml` und legt
die APK als Vorabversion `mobile-build-N` ab.

Ein Prüfstück oder ein Bauschritt, der eine Änderung meldet, hat **recht
bis zum Beweis des Gegenteils**. Es wird nicht weichgeklopft, sondern
gelesen.

### 5. Git

* Entwicklungszweig: **`claude/aufgaben-repositorys-j342nm`**
* Gepusht wird **auf beide**:

```bash
git push -u origin claude/aufgaben-repositorys-j342nm
git push origin claude/aufgaben-repositorys-j342nm:main
```

* Commit-Nachrichten sind **ausführlich und auf Deutsch**: was war der
  Anlass, was war der Schaden, was wurde geändert, was ist der Nachweis.
  Sie sind das Gedächtnis des Projekts.
* Abschluss jeder Commit-Nachricht, wörtlich:

```
Co-Authored-By: Claude <noreply@anthropic.com>
```

* **Die Modellkennung gehört NIE in Commits, PRs oder Code.** Nur in den
  Chat.

### 6. Geheimnisse

Diese Dinge kommen **nicht** in den Chat, nicht ins Repository, nicht in
eine Datei:

* SIP-Passwörter aus dem Betrieb
* Schlüssel und Passwörter zum Signieren der App
* Zugangsdaten zum Google-Play-Konto
* API-Schlüssel der KI-Anbieter

Der Nutzer trägt sie selbst ein – Signierschlüssel als
*Repository secrets* unter *Settings → Secrets and variables →
Actions*. Wer sie braucht, sagt **wo** sie einzutragen sind – und fragt
nicht danach.

### 7. Kommentare erklären das WARUM

Der Code sagt, was passiert. Der Kommentar sagt, warum es so und nicht
anders ist – am besten mit dem Vorfall, aus dem die Regel stammt.

Keine Kommentare, die den Code nacherzählen.

---

## Wo was steht

```
app/src/main/java/de/dariatech/softphone/
  SoftphoneApp.kt       Anwendungsklasse, startet den Telefonie-Kern
  LinphoneManager.kt    Liblinphone: Registrierung, Anrufe, Audio
  MainActivity.kt       Oberfläche: Wählfeld, Anrufsteuerung
  CallLogStore.kt       Anrufverlauf, lokal gespeichert
  Presets.kt            Vorlagen für bekannte Anbieter (u. a. DariaTech-PBX)
app/src/main/res/       Layouts, Farben, Texte, Symbole
build.gradle.kts        Abhängigkeiten, SDK-Stände
.github/workflows/      Der Bau
```

---

## Offene Punkte

1. Verlauf & Kontakte wie auf dem Desktop, Klingelton-Auswahl
2. CallStyle-Benachrichtigungen und Vollbild-Anrufannahme
3. Release-Signierung und Verteilung über den Play Store
   (Google-Play-Konto nötig, 25 $ einmalig)

---

## Arbeitsweise, die sich bewährt hat

**Erst lesen, dann behaupten.** Eine naheliegende Vermutung ist hier oft
falsch. Wer prüft, findet das; wer glaubt, baut den falschen Fix.

**Bei Unklarheit fragen, aber nur einmal.** Eine Frage, die sich aus dem
Code beantworten lässt, ist keine Frage für den Nutzer.

**Nebenwirkungen benennen.** Wenn eine Härtung eine Funktion einschränkt,
gehört das in die Antwort. Nicht kleingeredet, nicht verschwiegen.

**Fehler zugeben.**
