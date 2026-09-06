# CarPlay und Android Auto

**Auftrag des Inhabers vom 06.09.2026:** „Beide Apps müssen Apple Car
und Android Car unterstützen."

Dieses Dokument steht gleichlautend im Repository der iOS-App. Was hier
gebaut wird, gilt für Android; die Etappen sind so geschnitten, dass
beide Plattformen dieselbe Fähigkeit zur selben Zeit bekommen.

---

## Zuerst: was die Plattformen ERLAUBEN

Das ist keine Frage des Programmierens, sondern der Freigabe. Wer hier
falsch anfängt, baut monatelang etwas, das kein Kunde je zu sehen
bekommt.

### Apple CarPlay

**Zwei Stufen, und die erste braucht gar nichts.**

Eine VoIP-App, die **CallKit** benutzt, ist in CarPlay bereits
integriert: Ein eingehender Anruf erscheint auf dem Autobildschirm, das
Lenkrad nimmt ihn an, der Ton geht über die Anlage des Wagens, und der
Anruf steht in der Anrufliste des Autos. Das gilt seit iOS 10 und
verlangt **kein CarPlay-Entitlement**. Unsere iOS-App hat CallKit seit
dem 05.09.2026 (`Sources/Anrufkarte.swift`).

Was ohne Entitlement **nicht** geht: ein eigenes App-Symbol auf dem
CarPlay-Bildschirm mit eigener Oberfläche. Dafür braucht es
`com.apple.developer.carplay-communication`, und das vergibt Apple auf
Antrag unter <https://developer.apple.com/carplay/>.

Der Weg dazwischen, und er ist der wichtigste: **SiriKit.** Mit
`INStartCallIntent` funktioniert „Hey Siri, ruf Anna mit DariaTech an"
im Auto – ohne Entitlement, ohne Antrag. Ohne das kann man aus dem Auto
nur ANNEHMEN, nicht WÄHLEN.

### Android Auto

**Hier ist die Lage unangenehmer, und das gehört zuerst gesagt.**

Telefonie-Apps für Android Auto sind bei Google **in einer Beta**. Die
Kategorie `androidx.car.app.category.CALLING` lässt sich nur über
*Internal Testing* und *Closed Testing* im Play Store veröffentlichen;
**Produktion ist nicht erlaubt**, und die Teilnahme setzt eine
Nominierung durch Google voraus (Formular auf der Entwicklerseite).

Das heißt: **Eine eigene Oberfläche im Auto lässt sich für Android
heute nicht ausliefern.** Wer etwas anderes verspricht, verspricht
etwas, das der Play Store zurückweist.

Was **heute** geht und den größten Teil des Nutzens bringt: die
Anbindung an **Telecom**. Google verlangt sie ohnehin als Voraussetzung
für die Beta – ausdrücklich „zu jeder Zeit, nicht nur wenn Android Auto
läuft". Mit ihr erscheinen unsere Anrufe in der Anrufoberfläche des
Autos, das Lenkrad bedient sie, der Ton geht über die Anlage des
Wagens, und Bluetooth-Umschaltung, Unterbrechung durch einen
GSM-Anruf und die Rückkehr danach macht das System.

**Unsere Android-App hat das heute NICHT.** Sie hat CallStyle-Meldungen
(`Anrufmeldung.kt`), aber keinen `PhoneAccount`. Damit ist sie im Auto
unsichtbar, und im Übrigen auch an jeder Freisprecheinrichtung.

---

## Die Etappen

### Etappe 1 – Die Grundlage: der Anruf gehört dem System

| | iOS | Android |
|---|---|---|
| Was | CallKit | Telecom (`androidx.core:core-telecom`) |
| Stand | **steht** seit 05.09.2026 | **fehlt** |
| Freigabe nötig | nein | nein |

Ohne diese Etappe ist alles Weitere sinnlos: Sie ist es, die den Anruf
aus der App herausholt und dem Gerät übergibt. Danach bedient ihn das
Auto, die Freisprecheinrichtung, die Uhr und der Sperrbildschirm – ohne
dass die App davon etwas wissen muss.

Google verlangt ausdrücklich, dass die Anbindung IMMER läuft, nicht nur
im Auto. Das ist keine Schikane: Eine App, die den Telecom-Weg nur
manchmal benutzt, hinterlässt Geisteranrufe im System.

### Etappe 2 – Wählen aus dem Auto, ohne eigene Oberfläche

| | iOS | Android |
|---|---|---|
| Was | SiriKit `INStartCallIntent` | Assistant-Anrufabsicht |
| Freigabe nötig | nein | nein |

„Ruf Anna mit DariaTech an." Das ist der Punkt, an dem die App im Auto
vom Empfänger zum Telefon wird – und er kostet keinen Antrag.

**Zum Notruf, und zwar ausdrücklich:** 112 und 110 gehören NICHT in
diesen Weg. Sie gehen über den eigenen Wähler des Telefons, wie immer.
Eine Notrufnummer über eine Sprachabsicht zu leiten, die vom Verstehen
eines Assistenten abhängt, wäre die schlechteste Idee dieses Projekts.

### Etappe 3 – Die eigene Oberfläche im Auto

| | iOS | Android |
|---|---|---|
| Was | `CPTemplateApplicationScene` | `CarAppService`, Kategorie CALLING |
| Freigabe nötig | **ja** – Entitlement von Apple | **ja** – Nominierung durch Google |
| Auslieferbar | ja, nach Freigabe | **nein** – Beta, nur Testkanäle |

Verlauf und Kollegen als Liste auf dem Autobildschirm, Antippen wählt.
Hier wird erst gebaut, wenn die Freigaben da sind – und für Android
erst, wenn Google die Kategorie für die Produktion öffnet.

**Ein halb funktionierendes Auto-Erlebnis ist schlimmer als ein offen
benanntes fehlendes.** Dieselbe Regel wie beim Hintergrund-Empfang auf
iOS.

---

## Was der Inhaber besorgen muss

Beides sind Anträge, keine Geheimnisse – hier steht nur, WO:

1. **Apple CarPlay-Entitlement** (für Etappe 3, iOS):
   <https://developer.apple.com/carplay/> → Kategorie *Communication*.
   Apple fragt nach Zweck und Zielgruppe der App.
2. **Google Early-Access für Telefonie in Android Auto** (für Etappe 3,
   Android): das Nominierungsformular, verlinkt auf
   <https://developer.android.com/training/cars/communication/calling>.

Ohne 1. bleibt CarPlay bei Etappe 1 und 2 – was für den Alltag im Auto
bereits genügt. Ohne 2. ist Etappe 3 für Android gar nicht möglich,
unabhängig davon, was wir bauen.
