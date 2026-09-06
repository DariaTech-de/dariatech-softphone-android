/**
 * Prüfstück: Der Anruf gehört dem SYSTEM, nicht nur der App.
 *
 * DER AUFTRAG (Inhaber, 06.09.2026): „Beide Apps müssen Apple Car und
 * Android Car unterstützen." Etappe 1 aus docs/AUFTRAG-AUTO.md.
 *
 * DER BEFUND. Die App hatte CallStyle-Meldungen (Anrufmeldung.kt),
 * aber keinen `PhoneAccount`. Ein Anruf lebte damit ausschließlich in
 * ihrer eigenen Oberfläche – wie auf iOS vor CallKit. Die Folgen sind
 * nicht auf das Auto beschränkt:
 *
 *   * In Android Auto ist die App unsichtbar. Google verlangt die
 *     Telecom-Anbindung ausdrücklich „zu jeder Zeit, nicht nur wenn
 *     Android Auto läuft" – sie ist die Voraussetzung für alles
 *     Weitere.
 *   * An jeder Freisprecheinrichtung dasselbe: Die Lenkradtaste
 *     nimmt nichts an, weil das System von dem Anruf nichts weiß.
 *   * Kommt während eines DariaTech-Gesprächs ein GSM-Anruf, weiß
 *     keiner der beiden vom anderen. Das Ergebnis ist zwei Anrufe auf
 *     einem Lautsprecher.
 *
 * WARUM SELF-MANAGED: Unsere Anrufe gehören nicht in die Anrufliste
 * des Telefon-Wählers, und fremde Anrufe nicht in unsere. Genau dafür
 * gibt es `CAPABILITY_SELF_MANAGED` (ab API 26 – unser minSdk).
 *
 * Aufruf:  node pruefstuecke/auto-telecom.mjs
 */
import { readFileSync, existsSync } from "node:fs";

const ergebnisse = [];
const pruefe = (name, ok, extra = "") => {
  ergebnisse.push([name, ok]);
  console.log((ok ? "  ok   " : " FEHL  ") + name + (ok || !extra ? "" : "  – " + extra));
};
const lies = (p) => (existsSync(p) ? readFileSync(p, "utf-8") : "");
const ohneKommentare = (t) =>
  t.replace(/\/\*[\s\S]*?\*\//g, "").replace(/^[ \t]*\/\/.*$/gm, "");

const D = "app/src/main/java/de/dariatech/softphone/";
const auto = lies(D + "Autoanruf.kt");
const autoCode = ohneKommentare(auto);
const mgr = ohneKommentare(lies(D + "LinphoneManager.kt"));
const manifest = lies("app/src/main/AndroidManifest.xml");
const gradle = lies("app/build.gradle.kts");
const plan = lies("docs/AUFTRAG-AUTO.md");

console.log("1) Die Anbindung an Telecom gibt es überhaupt");
pruefe("Sources: Autoanruf.kt ist da", auto.length > 0);
pruefe("die Telecom-Bibliothek ist eingebunden",
  /androidx\.core:core-telecom/.test(gradle));
pruefe("ein PhoneAccount wird angemeldet",
  /registerPhoneAccount|CallsManager|registerAppWithTelecom/.test(autoCode));
/* BERICHTIGT BEIM BAU. Hier stand `/SELF_MANAGED/` – ein Konstantenname
   aus dem alten `android.telecom`. Die Jetpack-Bibliothek
   `androidx.core:core-telecom` IST selbstverwaltet, das ist ihr Zweck;
   sie meldet unter der Haube einen `PhoneAccount` mit
   CAPABILITY_SELF_MANAGED an, und `CAPABILITY_BASELINE` ist der Weg,
   das zu verlangen. Ein Prüfstück, das einen Namen erwartet, den es in
   der benutzten Bibliothek gar nicht gibt, prüft nichts. */
pruefe("die Grundfähigkeiten werden verlangt – core-telecom ist selbstverwaltet",
  /CAPABILITY_BASELINE/.test(autoCode));

console.log("\n2) Jeder Anruf geht durch – rein wie raus");
pruefe("ein eingehender Anruf wird gemeldet", /fun kommtAn\(/.test(autoCode));
pruefe("ein ausgehender auch", /fun gehtRaus\(/.test(autoCode));
pruefe("und das Ende ebenso", /fun beendet\(/.test(autoCode));
pruefe("das System kann annehmen", /onAnswer|onSetActive/.test(autoCode));
pruefe("und auflegen", /onDisconnect|onSetInactive/.test(autoCode));

console.log("\n3) Verdrahtet, und zwar IMMER – nicht nur im Auto");
pruefe("der Telefonie-Kern meldet den Anruf an Telecom",
  /Autoanruf\./.test(mgr),
  "sonst weiß das System von unseren Anrufen nichts");
pruefe("die Berechtigung MANAGE_OWN_CALLS steht im Manifest",
  /android\.permission\.MANAGE_OWN_CALLS/.test(manifest));

console.log("\n4) Der Notruf bleibt beim Wähler des Telefons");
pruefe("das steht als Regel im Masterplan",
  /112 und 110 gehören NICHT/.test(plan));
pruefe("und die App leitet 112 nicht über Telecom um",
  !/112/.test(autoCode), (autoCode.match(/.*112.*/) || [""])[0].trim());

const fehler = ergebnisse.filter(([, ok]) => !ok);
console.log(`\n${ergebnisse.length - fehler.length}/${ergebnisse.length} Prüfungen bestanden`);
if (fehler.length) {
  console.log("FEHLGESCHLAGEN:\n  " + fehler.map(([n]) => n).join("\n  "));
  process.exit(1);
}
