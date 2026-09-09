/**
 * Prüfstück: Die drei Befunde der Sicherheitsdurchsicht vom 06.09.2026.
 *
 * Gefunden wurden sie in der iOS-App; sie gelten hier WORTGLEICH, weil
 * beide Apps nach demselben Muster gebaut sind. Ein Befund, der nur in
 * einer der beiden behoben wird, kommt über die andere zurück.
 *
 * 1) DER VORGELESENE FINGERABDRUCK KAM VOM SERVER. `Ende2Ende.
 *    fingerabdruck()` stand da und wurde nirgends aufgerufen; angezeigt
 *    wurde `it.fingerabdruck` aus der Antwort der Anlage, verschlüsselt
 *    aber mit `it.schluessel` – zwei Felder derselben Antwort, die
 *    nichts aneinander bindet. Wer die Anlage übernimmt, tauscht den
 *    Schlüssel, lässt den Fingerabdruck stehen, und die beiden lesen
 *    sich am Telefon dasselbe Wort vor, während er mitliest. Auch
 *    `gewechselt` kam von der Anlage – und ein NEUES Gerät ist per
 *    Definition nicht „gewechselt".
 *
 * 2) DER TLS-RÜCKFALL WAR VOM NETZ ERZWINGBAR. Acht Sekunden ohne
 *    Registrierung, dann UDP ohne TLS. Wer im selben WLAN Pakete nach
 *    5061 verwirft, bekommt das – samt der SDES-Medienschlüssel, die
 *    bei SRTP im `a=crypto:` des SDP stehen. Der Rückfall MUSS bleiben,
 *    wo TLS noch nie ging (sonst wählt das Telefon keine 112); wo es
 *    schon einmal ging, entscheidet der Mensch.
 *
 * 3) „ENDE-ZU-ENDE" OHNE PRÜFUNG DES MEDIENWEGS. Solange „Sicheres
 *    Direktgespräch" in der Anlage aus ist – der Regelfall –, steht sie
 *    im Sprachweg und handelt ZRTP als zwei getrennte Strecken aus.
 *
 * Aufruf:  node pruefstuecke/durchsicht-06-09.mjs
 */
import { readFileSync, existsSync } from "node:fs";

const ergebnisse = [];
const pruefe = (name, ok, extra = "") => {
  ergebnisse.push([name, ok]);
  console.log((ok ? "  ok   " : " FEHL  ") + name + (ok || !extra ? "" : "  – " + extra));
};
const lies = (p) => (existsSync(p) ? readFileSync(p, "utf-8") : "");
/* OHNE KOMMENTARE: Der Vertrag gilt dem Code, nicht der Begründung
   daneben – sonst erzieht das Prüfstück dazu, Begründungen wegzulassen. */
const ohneKommentare = (t) =>
  t.replace(/\/\*[\s\S]*?\*\//g, "").replace(/^[ \t]*\/\/.*$/gm, "");

const D = "app/src/main/java/de/dariatech/softphone/";
const gespraech = ohneKommentare(lies(D + "GespraechsActivity.kt"));
const mgr = ohneKommentare(lies(D + "LinphoneManager.kt"));
const anlage = lies(D + "Anlage.kt");
const ged = lies(D + "Schluesselgedaechtnis.kt");
const postfach = lies(D + "Postfach.kt");
const haupt = ohneKommentare(lies(D + "MainActivity.kt"));
const texte = lies("app/src/main/res/values/strings.xml");

console.log("1) Der Fingerabdruck wird gerechnet, nicht geglaubt");
pruefe("es gibt ein Schlüsselgedächtnis", ged.length > 0);
pruefe("mit den drei Zuständen",
  /BEKANNT/.test(ged) && /NEU/.test(ged) && /GEWECHSELT/.test(ged));
pruefe("es liegt verschlüsselt, nicht offen",
  /Tresor\./.test(ged), "sonst schaltet ein Angreifer die Warnung ab");
pruefe("und wird beim Abmelden geleert",
  /Schluesselgedaechtnis\.vergiss\(/.test(postfach));
pruefe("die Anzeige rechnet den Fingerabdruck selbst",
  /Ende2Ende\.fingerabdruck\(/.test(gespraech));
pruefe("und benutzt das Serverfeld nicht mehr",
  !/\.fingerabdruck\b/.test(gespraech.replace(/Ende2Ende\.fingerabdruck/g, "")),
  (gespraech.match(/.*it\.fingerabdruck.*/) || [""])[0].trim());
pruefe("der Zustand kommt aus dem Gedächtnis, nicht aus g.gewechselt",
  /Schluesselgedaechtnis\.zustand\(/.test(gespraech) && !/\.gewechselt/.test(gespraech),
  (gespraech.match(/.*\.gewechselt.*/) || [""])[0].trim());

/* ÜBERHOLT AM 09.09.2026, bewusst stehen gelassen: Befund 2 der
   Durchsicht (der Rückfall fragt das Gedächtnis) ist gegenstandslos,
   seit es keinen Rückfall mehr gibt – TLS ist Pflicht. Übrig bleibt
   die Negation, siehe pflicht.mjs. */
console.log("\n2) Kein Rückfall mehr – und damit nichts, was ein Netz erzwingen könnte");
pruefe("kein Transportgedächtnis", !/object Transportgedaechtnis/.test(anlage));
pruefe("kein Rückfall im Manager", !/faelleZurueck|beobachteTlsVersuch|Transportgedaechtnis/.test(mgr));
pruefe("und kein „Trotzdem unverschlüsselt anmelden“", !/offenAnmelden/.test(mgr) && !/offenAnmelden/.test(haupt));

console.log("\n3) Kein Ende-zu-Ende, das nicht geprüft ist");
pruefe("die Texte versprechen kein Ende-zu-Ende mehr",
  !/Ende-zu-Ende verschlüsselt/.test(texte),
  (texte.match(/.*Ende-zu-Ende verschlüsselt.*/) || [""])[0].trim());
pruefe("sondern nennen die Bedingung",
  /beide Seiten dasselbe Wort/.test(texte));

const fehler = ergebnisse.filter(([, ok]) => !ok);
console.log(`\n${ergebnisse.length - fehler.length}/${ergebnisse.length} Prüfungen bestanden`);
if (fehler.length) {
  console.log("FEHLGESCHLAGEN:\n  " + fehler.map(([n]) => n).join("\n  "));
  process.exit(1);
}
