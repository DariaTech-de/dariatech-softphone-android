/**
 * Prüfstück: Das sichere Direktgespräch – Sicherheitswort und
 * ehrliches Kennzeichen.
 *
 * DER AUFTRAG (Inhaber, 06.09.2026): „Es muss alles verschlüsselt
 * werden." Stufe 4 des Masterplans (im Repository der Anlage:
 * docs/AUFTRAG-VERSCHLUESSELUNG.md, Punkte 17–21).
 *
 * WORUM ES GEHT, und der Unterschied zu Stufe 2 ist der ganze Punkt:
 * SRTP verschlüsselt die Sprache – aber die Schlüssel gehen im SDP
 * durch die Anlage. Wer die Anlage hat, hört mit. ZRTP handelt seine
 * Schlüssel IM MEDIENSTROM aus; zieht sich die Anlage aus dem Sprachweg
 * zurück, kommt sie an die Schlüssel nicht mehr heran.
 *
 * DAS SICHERHEITSWORT IST DER BEWEIS: Wer dazwischen säße, müsste zwei
 * verschiedene Schlüssel aushandeln und bekäme zwei verschiedene
 * Wörter. Steht auf beiden Bildschirmen dasselbe, ist niemand
 * dazwischen.
 *
 * UND DAS KENNZEICHEN MUSS EHRLICH SEIN. Ein Schloss, das immer gleich
 * aussieht, ist eine Lüge.
 *
 * Aufruf:  node pruefstuecke/sicheres-gespraech.mjs
 */
import { readFileSync } from "node:fs";

const ergebnisse = [];
const pruefe = (name, ok, extra = "") => {
  ergebnisse.push([name, ok]);
  console.log((ok ? "  ok   " : " FEHL  ") + name + (ok || !extra ? "" : "  – " + extra));
};
const lies = (p) => {
  try {
    return readFileSync(p, "utf8");
  } catch {
    return "";
  }
};

const w = "app/src/main/java/de/dariatech/softphone";
const manager = lies(`${w}/LinphoneManager.kt`);
const haupt = lies(`${w}/MainActivity.kt`);
const layout = lies("app/src/main/res/layout/activity_main.xml");
const texte = lies("app/src/main/res/values/strings.xml");

console.log("\n1) Zum Kollegen wird ZRTP angeboten");
pruefe(
  "es gibt einen Weg, der ein Gespräch als INTERN erkennt",
  /fun\s+istKollege/.test(manager),
  "die App unterscheidet nicht zwischen innen und außen"
);
/* ZURÜCKGESTELLT AM 09.09.2026: Die Anlage verlangt SRTP strikt; ein
   ZRTP-Angebot (RTP/AVP ohne a=crypto) lehnt der Endpunkt mit 488 ab,
   bevor ein Kanal entsteht. ZRTP wird deshalb nicht mehr gewählt. Das
   Sicherheitswort und das ehrliche Kennzeichen bleiben im Code – für
   den Tag, an dem die Anlage einen Direktweg ohne SDES anbietet. */
pruefe("aber es wird kein ZRTP mehr gewählt – der strikte SDES-Endpunkt lehnt das ab", !/MediaEncryption\.ZRTP/.test(manager));
pruefe(
  "gesetzt wird es AM ANRUF, nicht am Kern",
  /params\.mediaEncryption\s*=/.test(manager),
  "am Kern gesetzt würde es auch nach draußen gelten – dort kann es niemand"
);
pruefe(
  "nach draußen bleibt es bei SRTP",
  /MediaEncryption\.SRTP/.test(manager),
  "SRTP ist verschwunden – dann telefoniert niemand mehr verschlüsselt zur Anlage"
);

console.log("\n2) Das Sicherheitswort");
pruefe("der Manager liest es aus dem Anruf", /authenticationToken/.test(manager));
pruefe(
  "es ist von außen abfragbar",
  /sicherheitswort/.test(manager),
  "die Oberfläche kann es nicht anzeigen"
);
pruefe(
  "und es lässt sich bestätigen",
  /fun\s+bestaetigeSicherheitswort/.test(manager),
  "ein Wort, das man nicht bestätigen kann, fragt jedes Mal neu"
);

console.log("\n3) Das Kennzeichen sagt die Wahrheit");
pruefe(
  "die Oberfläche zeigt es im Gespräch",
  /sicherheitswort/i.test(haupt) && /gespraechKrypto/.test(layout),
  "kein eigenes Feld im Gesprächsbildschirm"
);
pruefe(
  "„Ende-zu-Ende“ und „bis zur Anlage“ sind zwei verschiedene Texte",
  /name="gespraech_e2e"/.test(texte) && /name="gespraech_bis_anlage"/.test(texte),
  "beide Fälle sehen gleich aus – das wäre die Lüge"
);
pruefe(
  "und der Text zum Sicherheitswort sagt, was man damit tut",
  /vorlesen/i.test(texte),
  "ein Wort ohne Anleitung liest niemand vor"
);

const fehler = ergebnisse.filter(([, ok]) => !ok);
console.log(`\n${ergebnisse.length - fehler.length}/${ergebnisse.length} Prüfungen bestanden`);
if (fehler.length) console.log("FEHLER: " + fehler.map(([n]) => n).join(", "));
process.exit(fehler.length ? 1 : 0);
