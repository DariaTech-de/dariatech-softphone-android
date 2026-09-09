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
 * BERICHTIGT AM 09.09.2026: Der Rückfall auf UDP und der
 * „Bestandsschutz" isMediaEncryptionMandatory = false sind weg – TLS
 * und SRTP sind Pflicht (Auftrag des Inhabers; ausführlich in
 * pflicht.mjs). Die Abschnitte 2 bis 4 sind auf die neue Regel
 * gezogen, nicht gelöscht.
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
  "und zwingend – die Anlage verlangt SRTP strikt, ein offenes Angebot darf die App nicht annehmen",
  /isMediaEncryptionMandatory\s*=\s*true/.test(manager),
  "isMediaEncryptionMandatory fehlt oder steht auf false"
);

console.log("\n2) Die Signalisierung wird zuerst über TLS versucht");
pruefe(
  "Anlage.kt nennt TLS als ersten Transport",
  /const val TRANSPORT\s*=\s*"TLS"/.test(anlage),
  "TRANSPORT steht nicht auf TLS"
);
pruefe(
  "und keinen Rückfall mehr (Pflicht seit dem 09.09.2026)",
  !/RUECKFALL_TRANSPORT/.test(anlage),
  "ein Rückfall ist benannt"
);

console.log("\n3) Der Rückfall greift, statt den Kunden auszusperren");
pruefe(
  "der Manager kennt keinen Rückfall",
  !/fun faelleZurueck/.test(manager),
  "Rückfall-Funktion vorhanden"
);
pruefe(
  "und meldet ausschließlich über TLS an",
  /melde\(username, password, domain, TransportType\.Tls\)/.test(manager),
  "melde() bekommt keinen festen TLS-Transport"
);

console.log("\n4) Der Mensch sieht, woran er ist");
pruefe(
  "die Einstellungen zeigen eine Zeile zur Verschlüsselung",
  /verschluesselungZeile/.test(layout) && /verschluesselungZeile/.test(haupt),
  "weder im Layout noch in MainActivity"
);
pruefe(
  "und der Text dafür steht in strings.xml – ohne einen Text für „offen“, den es nicht mehr gibt",
  /name="verschluesselung_zu"/.test(texte) && !/name="verschluesselung_offen"/.test(texte),
  "Text fehlt oder der Text für den offenen Fall steht noch da"
);
pruefe(
  "der Text sagt, dass es Pflicht ist",
  /name="verschluesselung_zu">[^<]*Pflicht/.test(texte),
  "der Satz nennt die Pflicht nicht"
);

const fehler = ergebnisse.filter(([, ok]) => !ok);
console.log(`\n${ergebnisse.length - fehler.length}/${ergebnisse.length} Prüfungen bestanden`);
if (fehler.length) console.log("FEHLER: " + fehler.map(([n]) => n).join(", "));
process.exit(fehler.length ? 1 : 0);
