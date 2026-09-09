/**
 * Prüfstück: Das Profil eines Menschen – das eigene und das der Kollegen.
 *
 * DER AUFTRAG (Inhaber, 09.09.2026): „jetzt alles was in Portal
 * eingetragen wird pro User soll in der App im Profil angezeogt werden,
 * soll im einegnen Profil als auch im Profil andere Koolegen der
 * Organistation." Und der Satz davor, der sagt WOFÜR: „vorallem um in
 * Apps nach der mitarbeiter zu suchen und anzuchaten wie bei Whatsapp
 * oder Teams."
 *
 * DER SCHADEN: Die Kollegenliste kannte vier Felder – id, name, nummer,
 * durchwahl. Wer jemanden suchte, musste seinen NAMEN kennen. In einem
 * Haus mit vierzig Leuten findet man so den Kollegen aus der
 * Buchhaltung nicht, dessen Namen man vergessen hat. Ein Profil gab es
 * gar nicht: Ein Druck auf eine Zeile wählte, mehr war nicht.
 *
 * Die Anlage liefert die fünf Profilfelder seit dem 09.09.2026
 * (`pbx/src/client/server.ts`, Weg `/kollegen`; festgehalten in
 * `pbx/docs/CLIENT-API.md` und im Prüfstück `benutzer.profil.ts`).
 *
 * DAS GEGENSTÜCK AUF iOS HEISST GENAUSO und prüft dasselbe.
 *
 * WARUM QUELLTEXT-VERTRAG und kein Gerätetest: Ein Profilblatt lässt
 * sich ohne Emulator nicht öffnen, und der steht in dieser Umgebung
 * nicht zur Verfügung. Ein Vertrag, der den Fehler fängt, ist mehr wert
 * als ein Gerätetest, den niemand laufen lässt. Was er NICHT prüft:
 * ob es auf dem Gerät gut AUSSIEHT – das bleibt der Nachweis am Gerät.
 *
 * Aufruf:  node pruefstuecke/profil.mjs
 */
import { readFileSync, existsSync } from "node:fs";

const ergebnisse = [];
const pruefe = (name, ok, extra = "") => {
  ergebnisse.push([name, ok]);
  console.log(`${ok ? " ok  " : "FEHL "} ${name}${extra ? `  – ${extra}` : ""}`);
};
const lies = (p) => (existsSync(p) ? readFileSync(p, "utf8") : "");

const QUELLE = "app/src/main/java/de/dariatech/softphone";
const dienst = lies(`${QUELLE}/Dienst.kt`);
const profil = lies(`${QUELLE}/Profil.kt`);
const adapter = lies(`${QUELLE}/KontakteAdapter.kt`);
const haupt = lies(`${QUELLE}/MainActivity.kt`);
const texte = lies("app/src/main/res/values/strings.xml");

console.log("\n1) Die App liest die fünf Profilfelder");
/* GESUCHT WIRD IM BLOCK „Kollege", nicht in der ganzen Datei.

   Der erste Anlauf suchte in Dienst.kt insgesamt – und „ausserDienst"
   war grün, weil es die Klasse `Dienststand` schon gibt. Eine Prüfung,
   die den Treffer woanders findet, prüft nichts. */
const kollegeBlock =
  (dienst.match(/data class Kollege\([\s\S]*?\n\}/) ?? [""])[0];
pruefe("die Klasse Kollege ist auffindbar", kollegeBlock.length > 0,
  "ohne sie prüft alles Folgende ins Leere");
for (const feld of ["position", "abteilung", "email", "ausserDienst", "fax"]) {
  pruefe(`Kollege kennt „${feld}“`,
    new RegExp(`val\\s+${feld}\\s*:`).test(kollegeBlock),
    "sonst kommt es in der App nie an, auch wenn es im Portal steht");
}

/* UNBEKANNTE UND FEHLENDE FELDER DÜRFEN NICHT WEHTUN. Die Regel steht
   in CLIENT-API.md: „Unbekannte Felder werden ignoriert, nie als Fehler
   behandelt." Eine App im Feld lässt sich nicht mit dem Server
   gleichzeitig aktualisieren – eine ältere Anlage schickt diese Felder
   noch gar nicht. `optString`/`optBoolean` liefern dann leer bzw.
   false; `getString` würfe. */
const ausBlock = (dienst.match(/fun aus\(o: JSONObject\): Kollege \{[\s\S]*?\n {8}\}/) ?? [""])[0];
for (const feld of ["position", "abteilung", "email", "fax"]) {
  pruefe(`„${feld}“ wird mit optString gelesen`,
    new RegExp(`optString\\("${feld}"`).test(ausBlock),
    "eine ältere Anlage schickt es nicht – getString würfe");
}
pruefe("„ausserDienst“ wird mit optBoolean gelesen",
  /optBoolean\("ausserDienst"/.test(ausBlock), ausBlock ? "" : "kein aus()-Block gefunden");

console.log("\n2) Es gibt ein Profilblatt");
pruefe("die Datei Profil.kt ist da", profil.length > 0,
  "ohne sie gibt es kein Profil – nur eine Liste, die wählt");
pruefe("es zeigt das Profil eines KOLLEGEN",
  /fun\s+zeigeKollege|fun\s+zeige\(/.test(profil));
/* DAS EIGENE PROFIL, ausdrücklich verlangt: „soll im einegnen Profil
   als auch im Profil andere Koolegen". Es kommt aus Verzeichnis.ich() –
   die App weiß über ihre Nebenstelle, welcher Mensch sie ist. */
/* IM PROFILBLATT, nicht irgendwo in der App: MainActivity ruft
   Verzeichnis.ich() längst für das eigene Bild. Der erste Anlauf suchte
   in beiden Dateien – und war grün, bevor es ein Profil gab. */
pruefe("und das EIGENE, über Verzeichnis.ich",
  /Verzeichnis\.ich\(/.test(profil),
  "sonst sieht man jeden ausser sich selbst");

console.log("\n3) Im Profil steht, was im Portal steht");
for (const [feld, muster] of [
  ["Position", /\bposition\b/],
  ["Abteilung", /\babteilung\b/],
  ["E-Mail", /\bemail\b/],
  ["Faxnummer", /\bfax\b/],
  ["außer Dienst", /ausserDienst/],
  ["Durchwahl", /durchwahl/],
  ["Rufnummer", /\bnummer\b/]
]) {
  pruefe(`das Profil zeigt ${feld}`, muster.test(profil), "steht nicht in Profil.kt");
}

/* WAS NICHT INS PROFIL GEHÖRT. Die Anlage gibt es gar nicht erst
   heraus (benutzer.profil.ts hält das fest) – aber wer hier eines
   dieser Wörter findet, hat sich einen zweiten Weg gebaut. */
console.log("\n4) Und was NICHT hineingehört");
pruefe("keine Klingeldauer im Profil", !/klingelSec/.test(profil),
  "eine Einstellung der Technik, kein Profil");
pruefe("keine ausgehende Rufnummer im Profil", !/outboundNumber/.test(profil),
  "sie sagt, was der ANGERUFENE sieht – geht keinen Kollegen etwas an");
pruefe("kein Passwort", !/passwort|password/i.test(profil));

console.log("\n5) Die Liste zeigt, wonach man sucht");
pruefe("die Kollegenzeile nennt Position und Abteilung",
  /position/.test(adapter) && /abteilung/.test(adapter),
  "sonst muss man jede Zeile antippen, um zu sehen, wer das ist");
/* WER AUSSER DIENST IST, soll das in der Liste sehen – bevor er
   dreimal anruft. */
pruefe("und wer außer Dienst ist, steht als solcher da",
  /ausserDienst/.test(adapter));

console.log("\n6) Gesucht wird über die neuen Felder");
/* GENAU DAFÜR SIND SIE DA. Ein Feld, das die App anzeigt, aber nicht
   durchsucht, erfüllt den Auftrag zur Hälfte. */
const sucht = (profil + haupt + adapter).match(/fun\s+passt[\s\S]{0,600}/) ?? [""];
for (const feld of ["name", "position", "abteilung", "durchwahl"]) {
  pruefe(`die Suche findet über „${feld}“`,
    new RegExp(`\\b${feld}\\b`).test(sucht[0]),
    sucht[0] ? "" : "keine Suchfunktion gefunden");
}
pruefe("klein und groß geschrieben ist dasselbe",
  /lowercase\(\)/.test(sucht[0]), "wer „mta“ tippt, meint „MTA“");

console.log("\n7) Oberflächentexte stehen in strings.xml");
/* Dieselbe Regel wie überall in dieser App: Ein deutscher Satz, der im
   Kotlin steht, ist beim nächsten Mal an zwei Stellen verschieden. */
for (const name of ["profil_position", "profil_abteilung", "profil_ausser_dienst"]) {
  pruefe(`der Text „${name}“ ist da`, texte.includes(`"${name}"`));
}

const fehler = ergebnisse.filter(([, ok]) => !ok);
console.log(`\n${ergebnisse.length - fehler.length}/${ergebnisse.length} Prüfungen bestanden`);
process.exit(fehler.length > 0 ? 1 : 0);
