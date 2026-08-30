/**
 * Prüfstück: Der Telefondienst sagt in den Einstellungen, wie es steht.
 *
 * DER ANLASS ist eine Ansage des Inhabers vom 30.08.2026, mit Blick auf
 * die Webex-Bildschirmfotos: „Telefondienst Status soll auch in
 * Einstellungen sein."
 *
 * VORGEFUNDEN: Der Anmeldezustand stand NUR oben in der Markenleiste,
 * und er stand dort als das, was Liblinphone durchreicht. Bei einem
 * Fehlschlag las der Kunde „Anmeldung fehlgeschlagen: Forbidden" – ein
 * englisches Wort aus einem SIP-Protokoll. Wer das liest, weiß nicht,
 * ob sein Passwort falsch ist, die Nebenstelle nicht existiert oder das
 * Netz klemmt. Genau in diesem Moment ruft er an.
 *
 * WAS HIER FESTGENAGELT WIRD:
 *
 *  1. Der Zustand steht in den EINSTELLUNGEN, nicht nur in der Leiste.
 *     Dort sucht man ihn, wenn etwas nicht geht.
 *  2. Der Grund steht auf DEUTSCH und nennt die Ursache, nicht den
 *     SIP-Code. „Benutzername oder Passwort stimmt nicht" ist eine
 *     Auskunft; „Forbidden" ist keine.
 *  3. Der Zustand hängt nicht an der FARBE allein. Wer Rot und Grün
 *     nicht unterscheidet, liest den Text – dieselbe Regel wie in der
 *     Markenleiste.
 *  4. Es gibt einen Weg, sich neu anzumelden, ohne das Passwort noch
 *     einmal einzutippen.
 *
 * Aufruf:  node pruefstuecke/telefondienst.mjs
 */
import { readFileSync, existsSync, readdirSync } from "node:fs";

const ergebnisse = [];
const pruefe = (name, ok, extra = "") => {
  ergebnisse.push([name, ok]);
  console.log((ok ? "  ok   " : " FEHL  ") + name + (ok || !extra ? "" : "  – " + extra));
};

const QUELLEN = "app/src/main/java/de/dariatech/softphone";
const LAYOUT = "app/src/main/res/layout/activity_main.xml";
const TEXTE = "app/src/main/res/values/strings.xml";
const dateien = existsSync(QUELLEN) ? readdirSync(QUELLEN).filter((d) => d.endsWith(".kt")) : [];
const lies = (d) => readFileSync(`${QUELLEN}/${d}`, "utf-8");
const layout = existsSync(LAYOUT) ? readFileSync(LAYOUT, "utf-8") : "";
const texte = existsSync(TEXTE) ? readFileSync(TEXTE, "utf-8") : "";

console.log("\n1) Es gibt eine Stelle, die den Zustand übersetzt");
{
  pruefe("Telefondienst.kt liegt vor", dateien.includes("Telefondienst.kt"),
    "hier gehört die Übersetzung von SIP nach Deutsch hin");
}
const dienst = dateien.includes("Telefondienst.kt") ? lies("Telefondienst.kt") : "";

console.log("\n2) Jeder Zustand hat einen deutschen Namen");
{
  for (const wort of ["Verbunden", "Nicht verbunden", "Wird angemeldet"]) {
    pruefe(`der Zustand „${wort}" ist benannt`, dienst.includes(wort));
  }
  pruefe("und der Fall ohne Zugangsdaten ist eigens benannt",
    /noch nicht eingerichtet/i.test(dienst),
    "wer nichts eingetragen hat, hat keine Störung – er hat noch nichts eingetragen");
}

console.log("\n3) Der Grund steht auf Deutsch, nicht als SIP-Wort");
{
  // Die Fehlschläge, die im Betrieb wirklich vorkommen. Jeder braucht
  // eine Auskunft, aus der hervorgeht, WAS zu tun ist.
  const faelle = [
    ["Unauthorized", "falsches Passwort"],
    ["Forbidden", "Anlage weist ab"],
    ["Not Found", "Nebenstelle gibt es nicht"],
    ["Timeout", "Anlage antwortet nicht"],
    ["Service Unavailable", "Anlage nimmt nichts an"]
  ];
  // OHNE RÜCKSICHT AUF GROSS UND KLEIN. Der erste Anlauf verglich
  // zeichengenau und meldete fünf Fehlschläge, obwohl der Code alle
  // fünf Fälle abdeckte – er vergleicht die kleingeschriebene Meldung,
  // weil Liblinphone mal „Forbidden" und mal „403 forbidden" liefert.
  // Ein Prüfstück, das an der Schreibweise scheitert, prüft die
  // Schreibweise und nicht die Sache.
  for (const [sip, wofuer] of faelle) {
    pruefe(`${sip} wird übersetzt (${wofuer})`,
      dienst.toLowerCase().includes(sip.toLowerCase()),
      "sonst liest der Kunde das englische SIP-Wort");
  }
  pruefe("und es gibt einen Rückfall für alles Übrige",
    /else ->|return .*meldung|sonst/i.test(dienst),
    "eine unbekannte Meldung darf nicht in einer leeren Zeile enden");
}

console.log("\n4) Die Auskunft kommt nie an ein Geheimnis heran");
{
  // Eine Statusmeldung wird vorgezeigt, abfotografiert und in eine
  // E-Mail an den Kundendienst geklebt. Sie darf kein Geheimnis tragen.
  //
  // DER ERSTE ANLAUF PRÜFTE DAS FALSCHE: Er verbot das WORT „Passwort"
  // in der Datei – und wurde rot an dem Satz „Benutzername oder
  // Passwort stimmt nicht", also genau an der Auskunft, um die es
  // geht. Verboten gehört nicht das Wort, sondern der ZUGRIFF: Diese
  // Datei darf nirgends an den gespeicherten Wert herankommen.
  const zugriff = [];
  for (const m of dienst.matchAll(/Zugangsspeicher\.\w+|getString\(\s*"password"|authInfo/g)) {
    zugriff.push(m[0]);
  }
  pruefe("Telefondienst.kt liest nirgends ein Geheimnis",
    zugriff.length === 0, zugriff.join(", "));
}

console.log("\n5) In den Einstellungen steht er wirklich");
{
  for (const [feld, name] of [
    ["@+id/dienstPunkt", "ein Punkt für den Zustand"],
    ["@+id/dienstText", "und der Zustand als TEXT daneben"],
    ["@+id/dienstGrund", "eine Zeile für den Grund"],
    ["@+id/dienstNebenstelle", "die eigene Nebenstelle"],
    ["@+id/neuAnmelden", "ein Knopf zum erneuten Anmelden"]
  ]) {
    pruefe(name, layout.includes(feld), feld);
  }
  pruefe("der Abschnitt ist überschrieben", /telefondienst_section/.test(texte));
}

console.log("\n6) Der Zustand kommt auch dort an");
{
  const haupt = dateien.includes("MainActivity.kt") ? lies("MainActivity.kt") : "";
  pruefe("onRegistration versorgt die Einstellungen",
    /dienstText|zeigeDienst/.test(haupt),
    "sonst steht dort für immer der Zustand vom Start");
  pruefe("der Knopf meldet wirklich neu an",
    /neuAnmelden\.setOnClickListener/.test(haupt));
  pruefe("und der Stack kann das auch",
    dateien.includes("LinphoneManager.kt") &&
      /refreshRegisters|neuAnmelden/.test(lies("LinphoneManager.kt")),
    "ohne refreshRegisters passiert beim Drücken nichts");
}

const fehler = ergebnisse.filter(([, ok]) => !ok);
console.log(`\n${ergebnisse.length - fehler.length}/${ergebnisse.length} Prüfungen bestanden`);
if (fehler.length > 0) console.log("FEHLGESCHLAGEN: " + fehler.map(([n]) => n).join(", "));
process.exit(fehler.length > 0 ? 1 : 0);
