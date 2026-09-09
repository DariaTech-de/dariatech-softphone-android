/**
 * Prüfstück: Verschlüsselung ist Pflicht – die App kennt keinen offenen Weg.
 *
 * DER AUFTRAG (Inhaber, 09.09.2026, nach einem Tag ohne Telefonie):
 * „TLS muss für die gesamte Anlage und alle Kunden außer der
 * Amtsleitung verpflichtend sein, es soll keine andere Wahl geben.
 * Alles innerhalb der Anlage muss zwangsläufig verschlüsselt sein."
 *
 * WAS DIE APP BIS DAHIN TAT – und warum es gehen muss:
 *
 *   · Rückfall auf UDP nach acht Sekunden ohne Anmeldung, mit
 *     Transportgedächtnis und einem Knopf „Trotzdem unverschlüsselt
 *     anmelden". Die Anlage nimmt seit dem 09.09.2026 nur noch TLS an;
 *     ein Rückfall meldet sich damit ins Leere und lässt die App
 *     „abgemeldet" zeigen, wo „TLS blockiert" die Wahrheit wäre.
 *   · `mediaEncryptionMandatory = false` als Bestandsschutz. Der
 *     Endpunkt in der Anlage ist strikt SDES; ein offenes Gespräch
 *     kommt dort ohnehin nicht zustande. Steht die App auf „nicht
 *     zwingend", nimmt sie aber ein offenes Angebot an, wenn es eines
 *     gibt – und das ist genau die Lücke, die zu bleiben nicht darf.
 *   · ZRTP für Gespräche zum Kollegen. Ein Endpunkt mit
 *     `media_encryption=sdes` und `optimistic=no` lehnt ein
 *     ZRTP-Angebot (RTP/AVP ohne a=crypto) mit 488 ab, bevor ein Kanal
 *     entsteht. Genau das war das Bild vom 09.09.2026: „0 calls
 *     processed", während der Inhaber ständig anrief.
 *
 * Aufruf:  node pruefstuecke/pflicht.mjs
 *
 * DIESELBE REGEL STEHT IN pruefstuecke/pflicht.mjs der iOS-App.
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
const zeile = (t, re) => (t.match(new RegExp(".*" + re.source + ".*")) || [""])[0].trim();

const anlage = ohneKommentare(lies("app/src/main/java/de/dariatech/softphone/Anlage.kt"));
const mgr = ohneKommentare(lies("app/src/main/java/de/dariatech/softphone/LinphoneManager.kt"));
const inhalt = ohneKommentare(lies("app/src/main/java/de/dariatech/softphone/MainActivity.kt"));
const texte = lies("app/src/main/res/values/strings.xml");

console.log("1) Es gibt nur einen Transport: TLS");
pruefe("Anlage.kt nennt TLS", /const val TRANSPORT\s*=\s*"TLS"/.test(anlage));
pruefe("und keinen Rückfall mehr", !/RUECKFALL_TRANSPORT/.test(anlage), zeile(anlage, /RUECKFALL_TRANSPORT/));
pruefe("das Transportgedächtnis ist weg – es gibt nichts mehr zu merken",
  !/object Transportgedaechtnis/.test(anlage) && !/Transportgedaechtnis\./.test(mgr));
pruefe("der Manager fällt nicht zurück", !/faelleZurueck|beobachteTlsVersuch/.test(mgr), zeile(mgr, /faelleZurueck|beobachteTlsVersuch/));
pruefe("und der Mensch kann sich nicht offen anmelden", !/offenAnmelden/.test(mgr) && !/offenAnmelden/.test(inhalt));
pruefe("nirgends ein zweiter Anmeldeweg über UDP", !/TransportType\.Udp/.test(mgr), zeile(mgr, /TransportType\.Udp/));

console.log("\n2) Die Sprache ist zwingend verschlüsselt");
pruefe("SRTP am Kern", /setMediaEncryption\(MediaEncryption\.SRTP\)/.test(mgr));
pruefe("und zwingend – kein Bestandsschutz mehr", /isMediaEncryptionMandatory\s*=\s*true/.test(mgr), zeile(mgr, /isMediaEncryptionMandatory/));
pruefe("kein ZRTP mehr – der strikte SDES-Endpunkt lehnt es mit 488 ab", !/MediaEncryption\.ZRTP/.test(mgr), zeile(mgr, /MediaEncryption\.ZRTP/));
pruefe("jeder Anruf geht mit SRTP raus", /params\.mediaEncryption\s*=\s*MediaEncryption\.SRTP/.test(mgr), zeile(mgr, /params\.mediaEncryption/));

console.log("\n3) Die Oberfläche behauptet keinen offenen Weg mehr");
pruefe("kein Knopf „Trotzdem unverschlüsselt anmelden“", !/verschluesselung_trotzdem/.test(inhalt) && !/name="verschluesselung_trotzdem"/.test(texte), zeile(inhalt, /verschluesselung_trotzdem/));
pruefe("kein Zustand „Signalisierung offen“", !/signalisierungOffen|tlsBlockiert/.test(inhalt) && !/signalisierungOffen|tlsBlockiert/.test(mgr));
pruefe("die Einstellungen sagen, dass es Pflicht ist", /name="verschluesselung_zu">[^<]*Pflicht/.test(texte), zeile(texte, /name="verschluesselung_zu"/));
pruefe("und nennen den Port, damit der Mensch die Anlage prüfen kann", /name="verschluesselung_zu">[^<]*5061/.test(texte));

const fehler = ergebnisse.filter(([, ok]) => !ok);
console.log(`\n${ergebnisse.length - fehler.length}/${ergebnisse.length} Prüfungen bestanden`);
if (fehler.length) {
  console.log("FEHLGESCHLAGEN:\n  " + fehler.map(([n]) => n).join("\n  "));
  process.exit(1);
}
