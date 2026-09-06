/**
 * Prüfstück: Das Telefonieren selbst ist verschlüsselt.
 *
 * DER AUFTRAG (Inhaber, 06.09.2026): „Es muss alles verschlüsselt
 * werden. Sowohl das Telefonieren als auch Videoanrufe und Chatten."
 * Punkt 11 aus Stufe 2 des Masterplans (im Repository der Anlage:
 * docs/AUFTRAG-VERSCHLUESSELUNG.md).
 *
 * ZWEI DINGE, DIE MAN LEICHT VERWECHSELT:
 *
 *   · SIGNALISIERUNG (SIP): wer wen anruft, wann, und die Anmeldung
 *     selbst. Verschlüsselt wird sie durch den Transport TLS.
 *   · SPRACHE UND BILD (RTP): das Gespräch. Verschlüsselt wird es
 *     durch SRTP, unabhängig vom Transport.
 *
 * WARUM SRTP HIER NICHT WARTEN KANN: Seit dem 06.09.2026 legt die
 * Anlage NEUE Nebenstellen mit `media_encryption=sdes` und
 * `media_encryption_optimistic=no` an – also STRIKT. Eine App ohne
 * SRTP bekommt dort kein Gespräch zustande. Ohne diese Zeilen wäre die
 * Härtung in der Anlage ein Ausfall in der App.
 *
 * WARUM DER RÜCKFALL SEIN MUSS: Ob die Anlage SIP über TLS annimmt,
 * hängt an einem Transport in ihrer pjsip.conf, den ein Mensch
 * einrichten muss. Fehlt er, meldet sich eine App, die auf TLS
 * besteht, gar nicht mehr an – und ein nicht angemeldetes Telefon
 * wählt auch keine 112.
 *
 * Aufruf:  node pruefstuecke/verschluesselung.mjs
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

const wurzel = "app/src/main/java/de/dariatech/softphone";
const anlage = lies(`${wurzel}/Anlage.kt`);
const manager = lies(`${wurzel}/LinphoneManager.kt`);
const haupt = lies(`${wurzel}/MainActivity.kt`);
const texte = lies("app/src/main/res/values/strings.xml");
const layout = lies("app/src/main/res/layout/activity_main.xml");

console.log("\n1) Die Sprache ist verschlüsselt (SRTP)");
pruefe(
  "der Kern bekommt SRTP als Medienverschlüsselung",
  /setMediaEncryption\(MediaEncryption\.SRTP\)/.test(manager),
  "kein core.setMediaEncryption(MediaEncryption.SRTP)"
);
pruefe(
  "aber NICHT zwingend – sonst sperrt es Bestandskunden aus",
  /isMediaEncryptionMandatory\s*=\s*false/.test(manager),
  "isMediaEncryptionMandatory fehlt oder steht auf true"
);

console.log("\n2) Die Signalisierung wird zuerst über TLS versucht");
pruefe(
  "Anlage.kt nennt TLS als ersten Transport",
  /const val TRANSPORT\s*=\s*"TLS"/.test(anlage),
  "TRANSPORT steht nicht auf TLS"
);
pruefe(
  "und den Rückfall daneben",
  /RUECKFALL_TRANSPORT/.test(anlage),
  "kein Rückfall benannt"
);

console.log("\n3) Der Rückfall greift, statt den Kunden auszusperren");
pruefe(
  "der Manager kennt den Rückfall",
  /fun faelleZurueck|rueckfall/i.test(manager),
  "keine Rückfall-Funktion"
);
pruefe(
  "und wartet dafür eine begrenzte Zeit ab",
  /postDelayed|Handler\(/.test(manager),
  "keine Warteuhr"
);
pruefe(
  "der Rückfall ist sichtbar, nicht still",
  /signalisierungOffen/.test(manager),
  "kein sichtbarer Zustand"
);

console.log("\n4) Der Mensch sieht, woran er ist");
pruefe(
  "die Einstellungen zeigen eine Zeile zur Verschlüsselung",
  /verschluesselungZeile/.test(layout) && /verschluesselungZeile/.test(haupt),
  "weder im Layout noch in MainActivity"
);
pruefe(
  "und die Texte dafür stehen in strings.xml",
  /name="verschluesselung_offen"/.test(texte) && /name="verschluesselung_zu"/.test(texte),
  "die Texte fehlen"
);
pruefe(
  "der Text zum offenen Fall sagt, was das heißt",
  /wer wen anruft/i.test(texte),
  "der Satz erklärt die Folge nicht"
);

const fehler = ergebnisse.filter(([, ok]) => !ok);
console.log(`\n${ergebnisse.length - fehler.length}/${ergebnisse.length} Prüfungen bestanden`);
if (fehler.length) console.log("FEHLER: " + fehler.map(([n]) => n).join(", "));
process.exit(fehler.length ? 1 : 0);
