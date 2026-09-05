/**
 * Prüfstück: Diese App gehört EINER Anlage – der von DariaTech.
 *
 * DER ANLASS ist eine Ansage des Inhabers vom 30.08.2026: „Beide Apps
 * müssen nur für DariaTech sein, also es sollen alle Serverdaten im
 * Hintergrund hinterlegt sein."
 *
 * VORGEFUNDEN war das Gegenteil: ein allgemeines Softphone. Zehn
 * Anbietervorlagen von easybell über sipgate bis zur FRITZ!Box, ein
 * freies Feld für die Serveradresse, eine Auswahl für den Transport.
 * Wer die App eines DariaTech-Kunden einrichtete, musste sich durch
 * eine Anbieterliste arbeiten, in der neun Einträge falsch waren – und
 * die zehn Zeilen Hinweistext zur richtigen Adresse waren nötig, weil
 * genau das reihenweise schiefging.
 *
 * WAS HIER FESTGENAGELT WIRD:
 *
 *  1. Die Serveradresse steht an GENAU EINER Stelle im Quelltext
 *     (`Anlage.kt`) und nirgends sonst. Ein Wert an zwei Stellen
 *     driftet an einer davon.
 *  2. Kein Eingabefeld mehr für Server, Transport oder Anbieter. Was
 *     der Nutzer nicht eintragen kann, kann er auch nicht falsch
 *     eintragen.
 *  3. Kein fremder Anbieter mehr im Quelltext.
 *  4. Ein Altbestand mit abweichendem Server wird GEMELDET, nicht
 *     stillschweigend übergangen. Wer seine App vor dieser Änderung auf
 *     einen anderen Server gestellt hatte, soll erfahren, warum sie
 *     jetzt woanders anruft.
 *
 * Aufruf:  node pruefstuecke/anlage.mjs
 */
import { readFileSync, existsSync, readdirSync } from "node:fs";

const ergebnisse = [];
const pruefe = (name, ok, extra = "") => {
  ergebnisse.push([name, ok]);
  console.log((ok ? "  ok   " : " FEHL  ") + name + (ok || !extra ? "" : "  – " + extra));
};

const QUELLEN = "app/src/main/java/de/dariatech/softphone";
const LAYOUT = "app/src/main/res/layout/activity_main.xml";
const dateien = existsSync(QUELLEN)
  ? readdirSync(QUELLEN).filter((d) => d.endsWith(".kt"))
  : [];
const lies = (d) => readFileSync(`${QUELLEN}/${d}`, "utf-8");

console.log("\n1) Es gibt eine Datei, in der die Anlage steht");
{
  pruefe("Anlage.kt liegt vor", dateien.includes("Anlage.kt"),
    "hier gehören Serveradresse und Transport hin – und nur hier");
  if (dateien.includes("Anlage.kt")) {
    const t = lies("Anlage.kt");
    /* DER SIP-NAME IST sip.dariatech.de – UND DAS IST KEIN GESCHMACK.
       Befund vom 05.09.2026, nachgemessen im Namensdienst:

         pbx.dariatech.de → 104.21.14.53, 172.67.157.219,
                            2606:4700:3030::6815:e35   (Cloudflare)
         sip.dariatech.de → 178.254.6.5                (die Anlage)

       Cloudflares Proxy führt HTTP und HTTPS weiter, sonst nichts – ein
       REGISTER über UDP kommt dort nie an. Wer diesen Namen einträgt,
       baut eine App, die sich niemals anmelden kann, und der Fehler
       sieht aus wie ein Netzproblem beim Kunden.

       DASSELBE STEHT IM iOS-REPO. Zwei Apps mit zwei Servern wären zwei
       Fehlerbilder für dieselbe Ursache. */
    const ohneKommentar = t.replace(/\/\*[\s\S]*?\*\//g, "").replace(/\/\/[^\n]*/g, "");
    pruefe("und sie nennt die SIP-Adresse der Anlage", /"sip\.dariatech\.de"/.test(t),
      (t.match(/const val SERVER = "([^"]*)"/) ?? [])[1] ?? "keine gefunden");
    pruefe("und NICHT den Namen hinter dem CDN",
      !/pbx\.dariatech\.de|portal\.dariatech\.de/.test(ohneKommentar),
      "Cloudflare führt nur HTTP/HTTPS weiter – SIP kommt dort nie an");
    pruefe("und den Transport", /"UDP"|"TCP"|"TLS"/.test(t));
  }
}

console.log("\n2) Die Serveradresse steht nur an dieser einen Stelle");
{
  const woanders = dateien
    .filter((d) => d !== "Anlage.kt")
    .filter((d) => /sip\.dariatech\.de/.test(lies(d)));
  pruefe("kein zweites Vorkommen im Kotlin-Quelltext",
    woanders.length === 0, woanders.join(", "));
}

console.log("\n3) Keine fremden Anbieter mehr");
{
  // Die Adressen aus der alten Vorlagenliste. Sie gehören in ein
  // allgemeines Softphone, nicht in die App EINES Anbieters.
  const fremd = [
    "easybell.de", "sipgate.de", "t-online.de", "fpbx.de",
    "fonial.de", "1und1.de", "fritz.box"
  ];
  const gefunden = [];
  for (const d of dateien) {
    const t = lies(d);
    for (const f of fremd) if (t.includes(f)) gefunden.push(`${d}: ${f}`);
  }
  pruefe("kein fremder Registrar im Quelltext", gefunden.length === 0, gefunden.join(" · "));
  pruefe("die Vorlagenliste ist weg", !dateien.includes("Presets.kt"),
    "Presets.kt liegt noch da");
}

console.log("\n4) Nichts davon lässt sich mehr eintippen");
{
  const layout = existsSync(LAYOUT) ? readFileSync(LAYOUT, "utf-8") : "";
  for (const [feld, name] of [
    ["@+id/domain", "kein Feld für die Serveradresse"],
    ["@+id/transport", "keine Auswahl für den Transport"],
    ["@+id/preset", "keine Anbieterliste"],
    ["@+id/presetHint", "kein Hinweistext zur Anbieterliste"]
  ]) {
    pruefe(name, !layout.includes(feld), feld);
  }
  // Was BLEIBEN muss: Ohne diese beiden Felder kann sich niemand mehr
  // anmelden. Ein Prüfstück, das nur Entfernen prüft, wird grün, wenn
  // jemand die halbe Ansicht löscht.
  pruefe("das Feld für den Benutzernamen bleibt", layout.includes("@+id/username"));
  pruefe("das Feld für das Passwort bleibt", layout.includes("@+id/password"));
  pruefe("der Knopf zum Verbinden bleibt", layout.includes("@+id/connectButton"));
}

console.log("\n5) Der Code liest den Server nicht mehr aus den Einstellungen");
{
  const gefunden = [];
  for (const d of dateien) {
    const t = lies(d);
    for (const m of t.matchAll(/(get|put)String\(\s*"(domain|transport|preset)"/g)) {
      gefunden.push(`${d}: ${m[0]}`);
    }
  }
  pruefe("weder gelesen noch geschrieben", gefunden.length === 0, gefunden.join(" · "));
}

console.log("\n6) Ein Altbestand wird gemeldet, nicht verschwiegen");
{
  // Wer die App vor dieser Änderung auf einen anderen Server gestellt
  // hatte, ruft ab jetzt woanders an. Das ist eine Nebenwirkung, und
  // eine Nebenwirkung gehört benannt – ins Protokoll, damit sie bei
  // einer Störungssuche auffindbar ist.
  const t = dateien.includes("Anlage.kt") ? lies("Anlage.kt") : "";
  pruefe("Anlage.kt sieht sich einen Altbestand an",
    /Altbestand/i.test(t) && /Log\.[wi]/.test(t),
    "ohne Meldung sucht jemand stundenlang, warum die App den Server gewechselt hat");
}

const fehler = ergebnisse.filter(([, ok]) => !ok);
console.log(`\n${ergebnisse.length - fehler.length}/${ergebnisse.length} Prüfungen bestanden`);
if (fehler.length > 0) console.log("FEHLGESCHLAGEN: " + fehler.map(([n]) => n).join(", "));
process.exit(fehler.length > 0 ? 1 : 0);
