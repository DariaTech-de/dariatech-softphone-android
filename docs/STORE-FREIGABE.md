# Prüfliste vor jeder Einreichung bei Google Play

**Stufe 6 des Masterplans** (im Repository der Anlage:
`docs/AUFTRAG-VERSCHLUESSELUNG.md`, Punkte 35–40).

Diese Liste wird vor JEDER Einreichung durchgegangen. Was sich messen
lässt, misst `pruefstuecke/store-freigabe.mjs` – der Rest steht hier,
weil er in einem Formular bei Google auszufüllen ist und in keiner
Datei dieses Repositorys steht.

> **Der teuerste Fehler ist nicht die Ablehnung, sondern der
> Widerspruch.** Play vergleicht das Data-Safety-Formular mit dem, was
> die App tut, und mit der Datenschutzerklärung. Drei Aussagen, die
> nicht zusammenpassen, kosten eine Woche.

---

## 1. Data-Safety-Formular (Punkt 35)

Auszufüllen in der Play Console unter *App-Inhalt → Datensicherheit*.
Grundlage ist `pbx/docs/DSGVO-VERZEICHNIS.md` in der Anlage – daraus
ergibt sich Zeile für Zeile:

| Frage von Google | Antwort | Warum |
|---|---|---|
| Werden Daten erhoben? | **Ja** | Name, Rufnummer, Verbindungsdaten |
| Werden Daten geteilt? | **Nein** | Alles bleibt in der Anlage des Kunden |
| Verschlüsselt bei der Übertragung? | **Ja** | TLS, SRTP – Stufe 2 |
| Kann der Nutzer Löschung verlangen? | **Ja** | Portal, zwei Knöpfe – Stufe 5 |
| Persönliche Daten | Name, E-Mail-Adresse | Kollegenliste |
| Telefon | Anrufliste, Rufnummern | Verlauf, Rückwärtssuche |
| Nachrichten | ja, **Ende-zu-Ende verschlüsselt** | Chat – Stufe 3 |
| Fotos | Bild der Person | Kollegenliste |
| Audio | Sprachnachrichten | Voicemail |
| Kennungen | Gerätekennung der App | für den Ende-zu-Ende-Chat |

**Nicht erhoben:** Standort, Kontakte des Telefons, Kalender,
Gesundheitsdaten, Zahlungsdaten, Werbekennungen. Es gibt kein Tracking,
kein Analysewerkzeug und keine Werbung in dieser App.

## 2. Berechtigungen – jede einzeln begründet (Punkt 38)

| Berechtigung | Wofür, sichtbar für den Nutzer |
|---|---|
| `INTERNET` | Die App telefoniert. Ohne Netz keine Telefonie. |
| `ACCESS_NETWORK_STATE` | Nach einem Netzwechsel (WLAN → Mobilfunk) die Registrierung neu anstoßen – sonst klingelt das Telefon nicht mehr. |
| `RECORD_AUDIO` | Das Mikrofon im Gespräch. Ein Telefon ohne Mikrofon hört nur zu. |
| `CAMERA` | **Nur** beim Videoanruf, und erst, wenn jemand im Gespräch auf „Video" drückt. Als `required="false"` vermerkt: Ein Gerät ohne Kamera soll die App trotzdem installieren können. |
| `MODIFY_AUDIO_SETTINGS` | Umschalten zwischen Hörer, Lautsprecher und Bluetooth. |
| `WAKE_LOCK` | Ein eingehender Anruf muss das Gerät wecken. |
| `POST_NOTIFICATIONS` | Eingehender Anruf, verpasster Anruf, neue Nachricht. Ohne sie bleibt ein Anruf still. |
| `FOREGROUND_SERVICE` | Die Registrierung im Hintergrund halten. |
| `FOREGROUND_SERVICE_PHONE_CALL` | Typ des Dienstes – seit Android 14 Pflicht. |
| `FOREGROUND_SERVICE_MICROPHONE` | Der Dienst trägt auch den Ton des Gesprächs. |
| `MANAGE_OWN_CALLS` | Die App meldet ihre Anrufe beim System an (Telecom, selbstverwaltet). Erst dadurch erscheint ein Anruf in **Android Auto**, an der Freisprecheinrichtung und auf dem Sperrbildschirm, und erst dadurch weiß ein hereinkommender GSM-Anruf, dass hier schon telefoniert wird. **Nicht gefährlich**, wird nicht erfragt, gibt keinen Zugriff auf die Anrufliste oder den Wähler des Telefons: Selbstverwaltete Anrufe bleiben von der Telefon-App getrennt. Siehe [`AUFTRAG-AUTO.md`](AUFTRAG-AUTO.md), Etappe 1. |

**Was NICHT gefragt wird und auch nicht gefragt werden soll:**
Standort, Kontakte des Telefons, Speicher, `READ_PHONE_STATE`,
Werbekennung.

## 3. Vordergrunddienst (Punkt 36)

`foregroundServiceType="phoneCall|microphone"` mit beiden Erlaubnissen.
Begründung für die Play Console, wörtlich verwendbar:

> Die App ist ein SIP-Telefon. Der Vordergrunddienst hält die
> Registrierung beim Server des Kunden aufrecht, damit eingehende
> Anrufe das Gerät erreichen, und trägt während des Gesprächs den
> Tonstrom. Ohne den Dienst klingelt das Telefon nicht, sobald Android
> die App in den Hintergrund schickt.

## 4. Ziel-SDK (Punkt 37)

`targetSdk = 34` in `app/build.gradle.kts`. Play nimmt keine App mit
älterem Ziel-SDK mehr an; das Prüfstück misst es.

## 5. Datenlöschung auch außerhalb der App (Punkt 39)

Play verlangt eine **Adresse im Netz**, unter der ein Nutzer die
Löschung seiner Daten verlangen kann – erreichbar ohne die App.

* **In der App:** Einstellungen → Abmelden entfernt Token, Schlüssel,
  Chatverlauf und Bilder von DIESEM Gerät.
* **Aus der Anlage:** Der Verwalter des Anschlusses löscht sie im
  Portal mit einem Knopf (Benutzer → *Daten löschen (DSGVO)*).
* **Adresse für das Formular:** *[vom Inhaber einzutragen – eine Seite
  auf dariatech.de, die den Weg beschreibt und eine Anschrift nennt]*

> Diese Adresse fehlt noch. Sie ist **die** Bedingung, an der eine
> Einreichung sonst hängen bleibt, und sie lässt sich nicht aus dem
> Code herstellen.

## 6. Kein Klartext (Punkt 40)

`android:usesCleartextTraffic="false"`. Play prüft das automatisch.
Der Dienst der Anlage besteht ohnehin auf TLS.

## 7. Und eines, das Play nicht prüft, wir aber müssen

`android:allowBackup="false"` samt `dataExtractionRules`. Androids
Vorgabe ist **wahr** – dann wandert der private Anwendungsspeicher in
Googles Sicherung, und darin liegt seit Stufe 3 der **private
Schlüssel des Ende-zu-Ende-Chats**. Die Zusage lautet „er verlässt das
Gerät nie".

**Der Preis, offen benannt:** Wer das Telefon wechselt, meldet sich neu
an und bekommt einen neuen Schlüssel. Sein bisheriger Chatverlauf
bleibt auf dem alten Gerät.

---

## Vor dem Hochladen

```bash
node pruefstuecke/*.mjs
gradle assembleRelease --no-daemon
```

Die Signierung geschieht mit dem Schlüssel des Inhabers; er liegt als
*Repository secret* unter *Settings → Secrets and variables → Actions*
und in keiner Datei dieses Repositorys.
