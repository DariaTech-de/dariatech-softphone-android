/**
 * Prüfstück: Wer ruft da an – und wie sieht er aus?
 *
 * DER AUFTRAG (Inhaber, 05.09.2026): „Ich möchte, dass man selber Fotos
 * hochladen kann … und wenn man sich gegenseitig anruft, dass das Foto
 * von dem Anruf angezeigt wird." Dazu der Satz davor: „Sobald die App
 * ausgeht, muss dann nur nach Benutzername und nach Passwort gefragt
 * werden."
 *
 * DAS GEGENSTÜCK AUF iOS HEISST GENAUSO und prüft dasselbe. Zwei Apps,
 * die verschieden mit der Anlage reden, erzeugen zwei Fehlerbilder für
 * dieselbe Ursache – deshalb stehen hier dieselben Regeln.
 *
 * DIE NORMALISIERUNG WIRD WIRKLICH GERECHNET, nicht nur im Quelltext
 * gesucht: „+49, 0049 und 0 treffen dasselbe" ist die eine Stelle, an
 * der ein Fehler dazu führt, dass beim Anruf der eigenen Kollegin eine
 * nackte Nummer steht.
 *
 * Aufruf:  node pruefstuecke/verzeichnis.mjs
 */
import { readFileSync, existsSync } from "node:fs";

const ergebnisse = [];
const pruefe = (name, ok, extra = "") => {
  ergebnisse.push([name, ok]);
  console.log((ok ? "  ok   " : " FEHL  ") + name + (ok || !extra ? "" : "  – " + extra));
};

const QUELLE = "app/src/main/java/de/dariatech/softphone";
const da = (d) => existsSync(`${QUELLE}/${d}`);
const lies = (d) => (da(d) ? readFileSync(`${QUELLE}/${d}`, "utf-8") : "");
const ohneKommentar = (t) =>
  t.replace(/\/\*[\s\S]*?\*\//g, "").replace(/\/\/[^\n]*/g, "");

const dienst = lies("Dienst.kt");
const verzeichnis = lies("Verzeichnis.kt");
const haupt = ohneKommentar(lies("MainActivity.kt"));
const speicher = lies("Zugangsspeicher.kt");

console.log("\n1) Der Ausweis für den Dienst");
{
  pruefe("es gibt einen Draht zur Anlage", da("Dienst.kt"));
  pruefe("er tauscht Name und Passwort gegen ein Token", /"\/token"/.test(dienst));
  /* IN DER VERSCHLÜSSELTEN ABLAGE, nicht in den gewöhnlichen
     Einstellungen: Wer das Token hat, kommt an das Adressbuch des
     Kunden und an die Bilder seiner Leute. */
  pruefe("das Token liegt in der verschlüsselten Ablage",
    /Zugangsspeicher\.setze\(context, TOKEN/.test(dienst));
  pruefe("und der Speicher kann das auch",
    /fun setze\(context: Context, feld: String/.test(speicher));
  pruefe("beim Abmelden geht es mit", /fun vergiss\(/.test(dienst));
  pruefe("geholt wird es beim Anmelden", /holeAusweis\(\)/.test(haupt));
  /* NIE IM HAUPTFADEN. Android beendet eine App, die dort ins Netz
     geht (NetworkOnMainThreadException) – und zwar sofort. */
  pruefe("aber nie im Hauptfaden", /Thread \{/.test(haupt),
    "Android beendet eine App, die im Hauptfaden ins Netz geht");
}

console.log("\n2) Die Anlage bestimmt keine zweite Adresse");
{
  const anlage = lies("Anlage.kt");
  pruefe("die Adresse des Dienstes steht in Anlage.kt", /const val DIENST/.test(anlage));
  pruefe("und leitet sich vom SIP-Namen ab", /\$SERVER/.test(anlage),
    "wer umzieht, ändert einen DNS-Eintrag und nicht zwei Zeilen in einer App");
  pruefe("sie geht über TLS", /https:\/\//.test(anlage),
    "ein Bearer-Token im Klartext ist ein verschenktes Geheimnis");
}

console.log("\n3) Die Rückwärtssuche – gerechnet, nicht behauptet");
{
  const quelle = verzeichnis.slice(
    verzeichnis.indexOf("fun normalisiere"),
    verzeichnis.indexOf("// ---- Bilder ----")
  );
  const js = quelle
    .replace(/fun normalisiere\(roh: String\): String \{/, "function n(roh) {")
    .replace(/var z = roh\.filter \{ it\.isDigit\(\) \|\| it == '\+' \}/,
      'let z = [...roh].filter((c) => /[0-9]/.test(c) || c === "+").join("")')
    .replace(/if \(z\.startsWith\("\+"\)\) z = "00" \+ z\.drop\(1\)/,
      'if (z.startsWith("+")) z = "00" + z.slice(1);')
    .replace(/z = z\.filter \{ it\.isDigit\(\) \}/,
      'z = [...z].filter((c) => /[0-9]/.test(c)).join("");')
    .replace(/if \(z\.startsWith\("0049"\)\) z = "0" \+ z\.drop\(4\)/,
      'if (z.startsWith("0049")) z = "0" + z.slice(4);')
    .replace(/return z\s*\}/, "return z; }");
  let n = null;
  try {
    n = new Function(`${js}; return n;`)();
  } catch (e) {
    pruefe("die Regel lässt sich nachrechnen", false, String(e));
  }
  if (n) {
    for (const [a, b] of [
      ["+49 831 555 12", "0049831 555-12"],
      ["+49 831 555 12", "0831/55512"],
      ["0049 831 55512", "0831 555 12"]
    ]) {
      pruefe(`„${a}“ und „${b}“ treffen dasselbe`, n(a) === n(b), `${n(a)} vs. ${n(b)}`);
    }
    pruefe("zwei verschiedene Nummern nicht", n("0831 55512") !== n("0831 55513"));
    pruefe("Klammern und Schrägstriche fallen weg",
      n("(0831) 555-12/0") === n("0831555120"));
    pruefe("eine leere Eingabe bleibt leer", n("") === "");
  }
  pruefe("gesucht wird im Gerät", /fun wer\(nummer: String\)/.test(verzeichnis));
  pruefe("die Kollegen zuerst",
    verzeichnis.indexOf("for (k in kollegen)") < verzeichnis.indexOf("for (e in kontakte)"));
}

console.log("\n4) Beim Anruf steht der Name aus dem Verzeichnis");
{
  pruefe("der Anrufer wird nachgeschlagen", /Verzeichnis\.wer\(nummer\)/.test(haupt));
  pruefe("die Kennung für das Bild wird mitgeführt", /gegenueberId = treffer\?\.second/.test(haupt));
  pruefe("und das Gesicht gezeigt", /fun zeigeGesicht\(\)/.test(haupt));
  /* NUR WENN ES EINES GIBT. Ein leerer Kreis sieht aus wie ein Bild,
     das nicht geladen hat. */
  pruefe("ohne Bild bleibt der Platz leer, nicht grau",
    /callerFoto\.visibility = View\.GONE/.test(haupt));
  const layout = existsSync("app/src/main/res/layout/activity_main.xml")
    ? readFileSync("app/src/main/res/layout/activity_main.xml", "utf-8")
    : "";
  pruefe("im Anrufbereich ist Platz dafür", /@\+id\/callerFoto/.test(layout));
}

console.log("\n5) Der Bereich „Kontakte“ ist keine leere Seite mehr");
{
  pruefe("es gibt eine Liste", da("KontakteAdapter.kt"));
  const adapter = lies("KontakteAdapter.kt");
  pruefe("mit zwei Abschnitten", /"Kollegen"/.test(adapter) && /"Adressbuch"/.test(adapter));
  /* SEINE DURCHWAHL, WENN ER EINE HAT – dann klingeln ALLE seine
     Geräte. Wer das umdreht, erwischt den Menschen nur, wenn er
     zufällig am richtigen Apparat sitzt. */
  pruefe("ein Kollege wird über seine Durchwahl gerufen",
    /k\.durchwahl\.isNotEmpty\(\) -> k\.durchwahl/.test(adapter));
  pruefe("und sonst über seine erste Nebenstelle",
    /k\.nebenstellen\.first\(\)/.test(adapter));
  pruefe("ein Druck wählt", /beimAnruf\(z\.ziel\)/.test(adapter));
  pruefe("Bild oder Initialen – immer genau eines",
    /kuerzel\.visibility = View\.INVISIBLE/.test(adapter)
      && /Verzeichnis\.kuerzel\(z\.name\)/.test(adapter));
  pruefe("die Liste hängt im Bereich", /kontakteListe\.adapter = kontakteAdapter/.test(haupt));
  pruefe("und der leere Zustand wechselt mit",
    /kontakteLeer\.visibility/.test(haupt));
}

console.log("\n6) Ein Netzfehler leert das Verzeichnis nicht");
{
  pruefe("nur ersetzen, wenn etwas kam",
    /if \(leute\.isNotEmpty\(\)\) kollegen = leute/.test(verzeichnis),
    "sonst stünde nach einem Netzfehler wieder eine nackte Nummer da");
  pruefe("Bilder tragen ihre Marke im Dateinamen",
    /\$\{k\.id\}-\$\{k\.foto\}\.jpg/.test(verzeichnis));
  pruefe("liegt es schon da, wird nichts geholt", /if \(ziel\.exists\(\)\)/.test(verzeichnis));
}

console.log("\n7) Die Anführungszeichen-Falle");
{
  const suender = [];
  for (const d of ["Dienst.kt", "Verzeichnis.kt", "KontakteAdapter.kt", "MainActivity.kt", "Anlage.kt"]) {
    ohneKommentar(lies(d)).split("\n").forEach((z, i) => {
      for (const m of z.matchAll(/"([^"\\]|\\.)*"/g)) {
        if (/[„“]/.test(m[0])) suender.push(`${d}:${i + 1} ${m[0]}`);
      }
    });
  }
  pruefe("kein deutsches Anführungszeichen in einer Zeichenkette",
    suender.length === 0, suender.join(", "));
}

const fehler = ergebnisse.filter(([, ok]) => !ok);
console.log(`\n${ergebnisse.length - fehler.length}/${ergebnisse.length} Prüfungen bestanden`);
if (fehler.length > 0) {
  console.log("FEHLGESCHLAGEN: " + fehler.map(([n]) => n).join(", "));
  process.exit(1);
}
