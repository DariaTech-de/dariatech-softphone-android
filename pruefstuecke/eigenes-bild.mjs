/**
 * Prüfstück: Das eigene Bild steht in der Kopfleiste – nicht nur die
 * Initialen.
 *
 * DER AUFTRAG (Inhaber, 05.09.2026): „In der App soll auch das
 * Profilbild vom Nutzer angezeigt werden."
 *
 * DER SCHADEN war ein Kreis mit zwei Buchstaben, gerechnet aus dem
 * SIP-BENUTZERNAMEN. Zwei Dinge stimmten daran nicht:
 *
 *   1. Das Bild, das die Kollegen beim Anruf sehen, sah der Mensch
 *      selbst nie. Wer nicht weiß, wie er bei anderen aussieht, ändert
 *      es auch nicht.
 *   2. Seit die SIP-Benutzernamen unerratbar sind, heißt der Anschluss
 *      „nst-e492zth5a84g". Daraus wurde das Kürzel „NS" – für jeden
 *      Menschen im Haus dasselbe.
 *
 * Beides löst dieselbe Auskunft: der eigene Eintrag in der
 * Kollegenliste, gefunden über die interne Nummer, die die Anlage beim
 * Tausch des Tokens nennt. Dieselbe Regel wie in der iPhone-App
 * (Verzeichnis.ich) – und aus demselben Grund an EINER Stelle.
 *
 * Aufruf:  node pruefstuecke/eigenes-bild.mjs
 */
import { readFileSync, existsSync } from "node:fs";

const ergebnisse = [];
const pruefe = (name, ok, extra = "") => {
  ergebnisse.push([name, ok]);
  console.log((ok ? "  ok   " : " FEHL  ") + name + (ok || !extra ? "" : "  – " + extra));
};

const WURZEL = "app/src/main/java/de/dariatech/softphone";
const lies = (p) => (existsSync(p) ? readFileSync(p, "utf-8") : "");
const ohneKommentar = (t) =>
  t.replace(/\/\*[\s\S]*?\*\//g, "").replace(/\/\/[^\n]*/g, "");

const verzeichnis = ohneKommentar(lies(`${WURZEL}/Verzeichnis.kt`));
const haupt = ohneKommentar(lies(`${WURZEL}/MainActivity.kt`));
const layout = lies("app/src/main/res/layout/activity_main.xml");

console.log("\n1) Die App weiß, wer sie ist");
{
  pruefe("es gibt eine Auskunft über den eigenen Eintrag",
    /fun ich\(|val ich\b/.test(verzeichnis));
  /* ÜBER DIE INTERNE NUMMER, nicht über den SIP-Namen: Der steht in
     keiner Kollegenliste, und seit der Umstellung sagt er nichts mehr
     über den Menschen. */
  pruefe("gefunden über die eigene Nebenstelle",
    /eigeneNebenstelle\(/.test(verzeichnis) && /nebenstellen\.contains\(/.test(verzeichnis));
}

console.log("\n2) Das Bild steht in der Kopfleiste");
{
  pruefe("die Kopfleiste hat eine Fläche für das Bild",
    /android:id="@\+id\/profilBild"/.test(layout));
  pruefe("sie ist rund zugeschnitten", /profilBild/.test(haupt) &&
    /RoundedBitmapDrawable|circleCrop|setCircular/.test(haupt));
  pruefe("und das Kürzel weicht, wenn ein Bild da ist",
    /profilKuerzel\.visibility/.test(haupt));
  /* OHNE BILD BLEIBEN DIE INITIALEN – ein leerer Kreis sieht aus wie
     ein Ladefehler. */
  pruefe("ohne Bild bleiben die Initialen", /profilBild\.visibility/.test(haupt));
}

console.log("\n3) Das Kürzel kommt aus dem NAMEN, nicht aus dem SIP-Namen");
{
  /* „nst-e492zth5a84g" ergibt „NS" – bei jedem im Haus. Steht der
     eigene Eintrag zur Verfügung, wird sein Name genommen. */
  pruefe("der eigene Name wird bevorzugt",
    /Verzeichnis\.ich\(|verzeichnisIch/.test(haupt));
  pruefe("und über dieselbe Kürzel-Regel wie die Kontaktliste",
    /Verzeichnis\.kuerzel\(/.test(haupt));
}

console.log("\n4) Es wird nachgezogen, wenn die Daten kommen");
{
  /* Das Verzeichnis kommt aus dem Netz und ist beim Start noch leer.
     Ohne diese Zeile stünde bis zum nächsten App-Start das Kürzel da,
     obwohl das Bild längst geladen ist. */
  pruefe("beim Eintreffen neuer Daten wird das Profil neu gezeichnet",
    /beiAenderung = \{[\s\S]{0,200}(setzeKuerzel|zeigeProfil)/.test(haupt));
}

const fehler = ergebnisse.filter(([, ok]) => !ok);
console.log(`\n${ergebnisse.length - fehler.length}/${ergebnisse.length} Prüfungen bestanden`);
if (fehler.length > 0) {
  console.log("FEHLGESCHLAGEN: " + fehler.map(([n]) => n).join(", "));
  process.exit(1);
}
