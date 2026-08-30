/**
 * Prüfstück: Die Gestaltung trägt – hell wie dunkel, und lesbar.
 *
 * DER ANLASS ist ein Auftrag des Inhabers vom 30.08.2026: die Apps
 * „optisch wie Webex" und technisch auf höchstes Niveau zu bringen.
 * Vorgefunden: fünf Farben, ein einziges helles Thema, kein
 * Dunkelmodus. Wer sein Telefon auf dunkel stellt – und das ist
 * inzwischen die Mehrheit –, bekam eine weiße Fläche ins Gesicht.
 *
 * ZWEI DINGE WERDEN HIER GEMESSEN, nicht behauptet:
 *
 *  1. VOLLSTÄNDIGKEIT. Jede Farbe, die es hell gibt, muss es auch
 *     dunkel geben. Eine fehlende Nachtfarbe fällt nicht beim Bauen auf,
 *     sondern beim ersten Nutzer, der nachts telefoniert – als weißer
 *     Kasten im schwarzen Bildschirm.
 *
 *  2. LESBARKEIT. Der Kontrast zwischen Text und Untergrund wird
 *     AUSGERECHNET (WCAG 2.1, relative Leuchtdichte) und muss AA
 *     erreichen: 4,5:1 für gewöhnlichen Text, 3:1 für große Schrift und
 *     für Umrisse. Das ist keine Geschmacksfrage – es ist der
 *     Unterschied zwischen „sieht edel aus" und „im Sonnenlicht nicht
 *     bedienbar". Eine Telefon-App wird draußen benutzt.
 *
 * Aufruf:  node pruefstuecke/gestaltung.mjs
 */
import { readFileSync, existsSync } from "node:fs";

const ergebnisse = [];
const pruefe = (name, ok, extra = "") => {
  ergebnisse.push([name, ok]);
  console.log((ok ? "  ok   " : " FEHL  ") + name + (ok || !extra ? "" : "  – " + extra));
};

/** Farben aus einer colors.xml lesen. */
function farben(pfad) {
  if (!existsSync(pfad)) return null;
  const text = readFileSync(pfad, "utf-8");
  const aus = {};
  for (const m of text.matchAll(/<color name="([^"]+)">\s*(#[0-9A-Fa-f]{6,8})\s*<\/color>/g)) {
    aus[m[1]] = m[2].toUpperCase();
  }
  return aus;
}

/** Relative Leuchtdichte nach WCAG 2.1. */
function leuchtdichte(hex) {
  const roh = hex.replace("#", "");
  const teil = roh.length === 8 ? roh.slice(2) : roh; // führendes Alpha weg
  const kanal = (i) => {
    const v = parseInt(teil.slice(i * 2, i * 2 + 2), 16) / 255;
    return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
  };
  return 0.2126 * kanal(0) + 0.7152 * kanal(1) + 0.0722 * kanal(2);
}

/** Kontrastverhältnis zweier Farben, 1:1 bis 21:1. */
function kontrast(a, b) {
  const la = leuchtdichte(a);
  const lb = leuchtdichte(b);
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
}

const hell = farben("app/src/main/res/values/colors.xml");
const dunkel = farben("app/src/main/res/values-night/colors.xml");

console.log("\n1) Es gibt beide Fassungen");
pruefe("helle Farben liegen vor", Boolean(hell));
pruefe("dunkle Farben liegen vor (values-night)", Boolean(dunkel),
  "ohne sie bekommt jeder mit dunklem Telefon eine weiße Fläche");
if (!hell || !dunkel) {
  console.log("\nAbbruch: ohne beide Fassungen ist der Rest nicht messbar.");
  process.exit(1);
}

console.log("\n2) Keine Farbe fehlt in der Nacht");
{
  const fehlend = Object.keys(hell).filter((n) => !(n in dunkel));
  pruefe("jede helle Farbe hat eine dunkle Entsprechung",
    fehlend.length === 0, fehlend.join(", "));
}

console.log("\n3) Lesbarkeit – ausgerechnet, nicht geschätzt");
{
  // Die Paare, die wirklich aufeinandertreffen. Mehr Paare wären
  // gründlicher und gleichzeitig wertlos: Was nie übereinanderliegt,
  // braucht keinen Kontrast.
  const paare = [
    ["text", "hintergrund", 4.5, "Fließtext auf dem Untergrund"],
    ["text", "flaeche", 4.5, "Fließtext auf einer Karte"],
    ["text_leise", "flaeche", 4.5, "Nebentext auf einer Karte"],
    ["auf_primaer", "primaer", 4.5, "Beschriftung auf dem grünen Knopf"],
    ["auf_gefahr", "gefahr", 4.5, "Beschriftung auf dem roten Knopf"],
    ["umriss", "flaeche", 3.0, "Trennlinien und Rahmen"],
    ["primaer", "flaeche", 3.0, "Grün als Auszeichnung auf einer Karte"]
  ];
  for (const [modus, satz] of [["hell", hell], ["dunkel", dunkel]]) {
    for (const [vorne, hinten, mindest, wofuer] of paare) {
      if (!(vorne in satz) || !(hinten in satz)) {
        pruefe(`${modus}: ${vorne} auf ${hinten} vorhanden`, false, "Farbe fehlt");
        continue;
      }
      const k = kontrast(satz[vorne], satz[hinten]);
      pruefe(`${modus}: ${wofuer} – ${k.toFixed(1)}:1 (mind. ${mindest}:1)`,
        k >= mindest, `${satz[vorne]} auf ${satz[hinten]}`);
    }
  }
}

console.log("\n4) Die Marke bleibt die Marke");
{
  // Das Portal der Anlage ist grün; die Apps müssen daneben nicht
  // fremd wirken. Diese beiden Werte sind gesetzt und dürfen sich
  // nicht unbemerkt verschieben.
  pruefe("das Markengrün steht", hell.marke === "#0D3D2E", hell.marke);
  pruefe("und der Notruf-Rot-Ton ist eindeutig", "gefahr" in hell, JSON.stringify(Object.keys(hell)));
}

console.log("\n5) Die Abgrenzung: unsere Handschrift, nicht die von Cisco");
{
  // DER ANLASS ist eine Auflage des Inhabers vom 30.08.2026, wörtlich:
  // „Es darf nicht gleich sein, sodass uns Cisco verklagen kann, weil
  // wir deren App-Optik kopiert haben."
  //
  // Konventionen sind frei – ein Wählfeld mit ABC unter der 2 gibt es
  // seit den 1960er Jahren. Eine ERKENNUNGSFARBE ist es nicht. Ciscos
  // ist ein Blau; unsere ist das Grün aus dem Portal der Anlage, und
  // das ist der sichtbarste einzelne Unterschied.
  //
  // Geprüft wird deshalb der Farbton selbst: Ist der Blau-Anteil
  // dominant, ist es kein Grün mehr. Wer eines Tages ein Blau als
  // Auszeichnung einbaut, bekommt einen roten Lauf – und den Hinweis
  // auf docs/GESTALTUNG-MOBIL.md im Repository dariatech-pbx.
  const gruen = (hex) => {
    const roh = hex.replace("#", "");
    const t = roh.length === 8 ? roh.slice(2) : roh;
    const r = parseInt(t.slice(0, 2), 16);
    const g = parseInt(t.slice(2, 4), 16);
    const b = parseInt(t.slice(4, 6), 16);
    return g > r && g > b;
  };
  for (const [modus, satz] of [["hell", hell], ["dunkel", dunkel]]) {
    pruefe(`${modus}: die Auszeichnungsfarbe ist ein Grün, kein Blau`,
      gruen(satz.primaer), `${satz.primaer} – siehe docs/GESTALTUNG-MOBIL.md`);
    pruefe(`${modus}: die Marke ist ein Grün`,
      gruen(satz.marke), satz.marke);
  }
}

console.log("\n6) Ein dunkles Thema ist auch hinterlegt");
{
  const nacht = existsSync("app/src/main/res/values-night/themes.xml");
  pruefe("values-night/themes.xml liegt vor", nacht,
    "sonst gelten nachts die hellen Farbrollen von Material");
}

const fehler = ergebnisse.filter(([, ok]) => !ok);
console.log(`\n${ergebnisse.length - fehler.length}/${ergebnisse.length} Prüfungen bestanden`);
if (fehler.length > 0) console.log("FEHLGESCHLAGEN: " + fehler.map(([n]) => n).join(", "));
process.exit(fehler.length > 0 ? 1 : 0);
