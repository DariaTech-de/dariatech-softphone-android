/**
 * Prüfstück: Der Telefonie-Kern bleibt hinter EINER Naht.
 *
 * DER ANLASS (11.09.2026): Der Kern wird getauscht – Liblinphone
 * (AGPLv3) gegen libre/baresip (BSD), siehe docs/KERNWECHSEL.md im
 * Repository der Anlage. Ein Kern lässt sich nur tauschen, wenn der
 * Rest der App ihn nicht kennt. Beim Nachsehen zeigte sich, dass er ihn
 * kannte: MainActivity.kt und Telefondienst.kt importierten
 * `org.linphone.core` direkt – Zustände, Anrufobjekte, den Transport.
 * Jede dieser Stellen wäre beim Tausch eine zweite Baustelle gewesen,
 * und eine davon hätte man übersehen.
 *
 * WAS FESTGENAGELT WIRD:
 *   1. Es gibt die Naht: `Telefonkern.kt` – ein Interface mit eigenen
 *      Typen, ohne ein einziges `org.linphone`.
 *   2. Ausserhalb der Implementierung(en) des Kerns importiert KEINE
 *      Datei `org.linphone`. Nicht die Oberfläche, nicht der Dienst.
 *   3. Alles, was der Rest der App am Kern aufruft, steht im Interface.
 *      Sonst nutzt jemand still einen Weg, den der neue Kern nicht
 *      hat – und merkt es erst beim Tausch.
 *   4. Die Oberfläche redet mit dem Kern nur über `Telefonkern.aktiv`,
 *      nie über `LinphoneManager` direkt. Der Schalter zwischen altem
 *      und neuem Kern sitzt an genau einer Stelle.
 *
 * Aufruf:  node pruefstuecke/kern-naht.mjs
 */
import { readFileSync, readdirSync } from "node:fs";

const ergebnisse = [];
const pruefe = (name, ok, extra = "") => {
  ergebnisse.push([name, ok]);
  console.log((ok ? "  ok   " : " FEHL  ") + name + (ok || !extra ? "" : "  – " + extra));
};
const lies = (p) => { try { return readFileSync(p, "utf8"); } catch { return ""; } };
const ohneKommentar = (t) => t.replace(/\/\*[\s\S]*?\*\//g, "").replace(/\/\/[^\n]*/g, "");

const w = "app/src/main/java/de/dariatech/softphone";
const dateien = readdirSync(w).filter((d) => d.endsWith(".kt"));
/* Die Implementierungen des Kerns – nur sie dürfen die Bibliothek kennen. */
const KERNE = ["LinphoneManager.kt", "BaresipKern.kt"];
const naht = lies(`${w}/Telefonkern.kt`);

console.log("1) Es gibt die Naht");
pruefe("Telefonkern.kt existiert", naht.length > 0);
pruefe("sie ist ein Interface", /interface Telefonkern\b/.test(naht));
pruefe("mit eigenen Zuständen statt denen der Bibliothek",
  /enum class Anmeldezustand/.test(naht) && /enum class Anrufzustand/.test(naht));
/* Im CODE, nicht im Kommentar: Der Kopfkommentar der Naht darf den
   Vorfall benennen, der sie nötig machte. */
pruefe("und ohne ein einziges org.linphone", !/org\.linphone/.test(ohneKommentar(naht)),
  "die Naht darf die Bibliothek nicht kennen – sonst ist sie keine");
pruefe("der Schalter `aktiv` sitzt in der Naht", /val aktiv: Telefonkern/.test(ohneKommentar(naht)));

console.log("\n2) Ausserhalb der Kerne kennt niemand org.linphone");
for (const d of dateien) {
  if (KERNE.includes(d)) continue;
  const t = ohneKommentar(lies(`${w}/${d}`));
  if (/org\.linphone/.test(t)) {
    pruefe(`${d} importiert org.linphone`, false, "hier sickert der Kern in die App");
  }
}
pruefe("keine Datei ausserhalb der Kerne importiert org.linphone",
  dateien.filter((d) => !KERNE.includes(d)).every((d) => !/org\.linphone/.test(ohneKommentar(lies(`${w}/${d}`)))));

console.log("\n3) Was der Rest der App am Kern ruft, steht im Interface");
{
  const rufe = new Set();
  for (const d of dateien) {
    if (KERNE.includes(d) || d === "Telefonkern.kt") continue;
    const t = ohneKommentar(lies(`${w}/${d}`));
    for (const m of t.matchAll(/Telefonkern\.aktiv\.([A-Za-z_]+)/g)) rufe.add(m[1]);
  }
  const angeboten = new Set([...ohneKommentar(naht).matchAll(/^\s*(?:fun|va[lr])\s+([A-Za-z_]+)/gm)].map((m) => m[1]));
  pruefe("die App ruft den Kern überhaupt über die Naht", rufe.size >= 10, `${rufe.size} Aufrufe`);
  const fehlend = [...rufe].filter((r) => !angeboten.has(r));
  pruefe("jeder Aufruf hat sein Gegenstück im Interface", fehlend.length === 0, `fehlt: ${fehlend.join(", ")}`);
}

console.log("\n4) Die Oberfläche redet nie direkt mit LinphoneManager");
for (const d of dateien) {
  if (KERNE.includes(d) || d === "Telefonkern.kt") continue;
  const t = ohneKommentar(lies(`${w}/${d}`));
  if (/LinphoneManager\./.test(t)) pruefe(`${d} ruft LinphoneManager direkt`, false, "der Schalter wäre umgangen");
}
pruefe("kein direkter Aufruf von LinphoneManager ausserhalb der Kerne",
  dateien.filter((d) => !KERNE.includes(d) && d !== "Telefonkern.kt").every((d) => !/LinphoneManager\./.test(ohneKommentar(lies(`${w}/${d}`)))));

const fehler = ergebnisse.filter(([, ok]) => !ok);
console.log(`\n${ergebnisse.length - fehler.length}/${ergebnisse.length} Prüfungen bestanden`);
if (fehler.length) console.log("FEHLER: " + fehler.map(([n]) => n).join(", "));
process.exit(fehler.length > 0 ? 1 : 0);
