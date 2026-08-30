/**
 * Prüfstück: Die Layouts nennen Rollen, keine Farbtöne.
 *
 * DER ANLASS ist ein Auftrag des Inhabers vom 30.08.2026, die Apps
 * optisch auf höchstes Niveau zu bringen. Der Dunkelmodus dafür steht
 * (values-night) – aber er wirkt nur, wo ein Layout eine ROLLE nennt.
 *
 * Zwei Arten, ihn auszuhebeln, und beide standen im Layout:
 *
 *  1. EIN FESTER FARBWERT (#5E7A6E). Der bleibt nachts, wie er ist.
 *     Ergebnis: helle Schrift auf hellem Grund oder umgekehrt – auf
 *     genau einem Gerät, nämlich dem des Kunden, der dunkel eingestellt
 *     hat.
 *  2. EIN DIREKTER VERWEIS auf eine Farbe (@color/white) statt auf die
 *     Rolle (?attr/colorSurface). Der zieht zwar die Nachtfassung
 *     mit – wenn es eine gibt –, sagt aber nicht, WOFÜR die Farbe da
 *     ist. Beim nächsten Umbau rät jemand.
 *
 * Der Gesprächsbildschirm ist ausgenommen: Er ist in BEIDEN Modi dunkel
 * (siehe colors.xml). Dort sind feste Rollen richtig, und das Prüfstück
 * lässt die dafür vorgesehenen Farben zu.
 *
 * Aufruf:  node pruefstuecke/layouts.mjs
 */
import { readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";

const ergebnisse = [];
const pruefe = (name, ok, extra = "") => {
  ergebnisse.push([name, ok]);
  console.log((ok ? "  ok   " : " FEHL  ") + name + (ok || !extra ? "" : "  – " + extra));
};

const ORDNER = "app/src/main/res/layout";
const dateien = readdirSync(ORDNER)
  .filter((n) => n.endsWith(".xml"))
  .map((n) => ({ name: n, text: readFileSync(join(ORDNER, n), "utf-8") }));

/** Farben, die absichtlich fest sind – der Gesprächsbildschirm. */
const ERLAUBT = new Set([
  // Der Gesprächsbildschirm ist in BEIDEN Modi dunkel: Ein Telefonat am
  // Ohr soll nicht blenden, und ein Helligkeitswechsel mitten im
  // Gespräch irritiert.
  "gespraech_grund",
  "gespraech_text",
  "gespraech_leise",
  // Die MARKENLEISTE ist ebenfalls in beiden Modi dunkelgrün – wie die
  // Kopfzeile im Portal der Anlage. Eine Leiste, die nachts hell wird,
  // wäre keine Marke mehr, sondern eine Fläche.
  //
  // NACHGETRAGEN am 30.08.2026: Die erste Fassung dieses Prüfstücks
  // kannte sie nicht, und die pauschale Umstellung machte aus dem
  // weißen Text auf der Leiste ein ?attr/colorSurface – nachts also
  // DUNKEL auf DUNKELGRÜN. Das Prüfstück hat es gemeldet, und es hatte
  // recht: Genau solche Paare sollen auffallen.
  "marke",
  "auf_marke",
  "auf_primaer",
  "auf_gefahr",
  "primaer",
  "gefahr"
]);

console.log("\n1) Kein fester Farbwert in einem Layout");
{
  const treffer = [];
  for (const d of dateien) {
    for (const m of d.text.matchAll(/#[0-9A-Fa-f]{6,8}/g)) {
      treffer.push(`${d.name}: ${m[0]}`);
    }
  }
  pruefe("nirgends ein #RRGGBB im Layout", treffer.length === 0, treffer.join(", "));
}

console.log("\n2) Keine abgelegten Farbnamen mehr");
{
  // Die alten Namen sagen nichts über ihre Aufgabe. Sie stehen noch in
  // colors.xml, damit nichts bricht – aber ein Layout, das sie noch
  // benutzt, ist ein Layout, das den Umbau nicht mitgemacht hat.
  const alt = ["white", "brand", "emerald", "red", "mint_bg"];
  const treffer = [];
  for (const d of dateien) {
    for (const a of alt) {
      if (new RegExp(`@color/${a}\\b`).test(d.text)) treffer.push(`${d.name}: @color/${a}`);
    }
  }
  pruefe("keine der fünf alten Farben mehr im Layout",
    treffer.length === 0, treffer.join(", "));
}

console.log("\n3) Wo Farben stehen, sind es die vorgesehenen");
{
  const treffer = [];
  for (const d of dateien) {
    for (const m of d.text.matchAll(/@color\/([a-z_]+)/g)) {
      if (!ERLAUBT.has(m[1])) treffer.push(`${d.name}: @color/${m[1]}`);
    }
  }
  pruefe("nur Rollen, die fest sein dürfen", treffer.length === 0,
    `${treffer.join(", ")} – sonst ?attr/colorSurface usw. benutzen`);
}

console.log("\n4) Die Wähltasten tragen ihre Buchstaben");
{
  // Jede Telefon-App der Welt zeigt ABC unter der 2. Wer eine Nummer
  // vom Zettel abliest, sucht danach – und wer sie nicht findet, hält
  // die App für unfertig.
  const s = readFileSync("app/src/main/res/values/strings.xml", "utf-8");
  const fehlend = ["abc", "def", "ghi", "jkl", "mno", "pqrs", "tuv", "wxyz"]
    .filter((b) => !s.toLowerCase().includes(`>${b}<`));
  pruefe("die Buchstabengruppen sind hinterlegt", fehlend.length === 0, fehlend.join(", "));
}

const fehler = ergebnisse.filter(([, ok]) => !ok);
console.log(`\n${ergebnisse.length - fehler.length}/${ergebnisse.length} Prüfungen bestanden`);
if (fehler.length > 0) console.log("FEHLGESCHLAGEN: " + fehler.map(([n]) => n).join(", "));
process.exit(fehler.length > 0 ? 1 : 0);
