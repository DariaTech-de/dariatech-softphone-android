/**
 * Prüfstück: Dieselbe Härtung wie in der iOS-App vom 07.09.2026.
 *
 * DER VORFALL (iOS, 07.09.2026, Vormittag): Die App zeigte „Verbunden“,
 * das Portal „abgemeldet“, Asterisk hatte keinen Contact. Drei Stunden
 * lang die Frage „wer lügt?“. Und *45 aus der App änderte nichts, weil
 * dem Apparat kein Benutzer zugeordnet war – ohne dass jemand das von
 * einem „angemeldet“ unterscheiden konnte.
 *
 * Die Android-App bekommt dieselben vier Dinge:
 *
 *  1. login() ist idempotent: dieselbe Anmeldung, während sie steht,
 *     tut nichts. (iOS: clearAccounts → REGISTER Expires: 0 → neu, bei
 *     jedem Erscheinen des Hauptbildschirms.)
 *  2. Zurück im Vordergrund: Anmeldung auffrischen (refreshRegisters).
 *     Asterisk löscht einen Contact an einer abgerissenen Verbindung.
 *  3. Die ehrliche Anzeige: GET /geraet – „Verbunden“ nur, wenn die
 *     Anlage das Gerät auch sieht; in den Einstellungen die Zeile
 *     „Anlage sieht dieses Gerät“.
 *  4. Der Warteschleifen-Schalter über GET/POST /dienst, mit der
 *     409-Begründung der Anlage im Klartext.
 *
 * Aufruf:  node pruefstuecke/anlagensicht.mjs
 */
import fs from "node:fs";

const ergebnisse = [];
const pruefe = (name, ok, extra = "") => {
  ergebnisse.push([name, ok]);
  console.log((ok ? "  ok   " : " FEHL  ") + name + (ok || !extra ? "" : "  – " + extra));
};
const Q = "app/src/main/java/de/dariatech/softphone/";
const lies = (p) => (fs.existsSync(p) ? fs.readFileSync(p, "utf8") : "");
const ohneKommentare = (t) => t.replace(/\/\*[\s\S]*?\*\//g, "").replace(/\/\/.*$/gm, "");
const manager = ohneKommentare(lies(Q + "LinphoneManager.kt"));
const dienst = ohneKommentare(lies(Q + "Dienst.kt"));
const main = ohneKommentare(lies(Q + "MainActivity.kt"));
const layout = lies("app/src/main/res/layout/activity_main.xml");
const strings = lies("app/src/main/res/values/strings.xml");

console.log("1) login() erkennt eine Wiederholung");
{
  const a = manager.indexOf("fun login("); const b = manager.indexOf("\n    }", a);
  const login = manager.slice(a, b);
  pruefe("vergleicht Benutzer, Passwort, Anlage mit der letzten Anmeldung",
    /letzterBenutzer/.test(login) && /letztesPasswort/.test(login) && /letzteDomain/.test(login) && /==\s*letzterBenutzer|letzterBenutzer\s*==/.test(login));
  pruefe("und ob die Anmeldung steht", /istRegistriert\(\)/.test(login));
  pruefe("und kehrt um, OHNE melde() zu rufen", /return\b/.test(login) && login.indexOf("return") < login.indexOf("melde("));
}

console.log("\n2) Zurück im Vordergrund");
pruefe("der Manager frischt die Anmeldung auf", /fun vordergrund\(\)[\s\S]{0,300}refreshRegisters\(\)/.test(manager));
/* Seit dem 11.09.2026 redet die Oberfläche mit dem Kern nur über die
   Naht `Telefonkern.aktiv` (pruefstuecke/kern-naht.mjs) – der Aufruf
   bleibt derselbe, der Weg ist ein anderer. */
pruefe("MainActivity ruft das in onResume", /override fun onResume\(\)[\s\S]{0,400}Telefonkern\.aktiv\.vordergrund\(\)/.test(main));

console.log("\n3) Die ehrliche Anzeige");
pruefe("Dienst.geraet() holt GET /geraet", /fun geraet\([\s\S]{0,300}"\/geraet"/.test(dienst));
pruefe("MainActivity lädt die Sicht der Anlage alle 30 Sekunden", /30_?000L?/.test(main) && /anlagensicht|Anlagensicht/.test(main));
pruefe("die Kopfzeile sagt „von der Anlage nicht gesehen“", /status_nicht_gesehen/.test(main) && /status_nicht_gesehen/.test(strings));
pruefe("und „nicht erreichbar“", /status_nicht_erreichbar/.test(main) && /status_nicht_erreichbar/.test(strings));
pruefe("die Einstellungen zeigen „Anlage sieht dieses Gerät“", /anlageSichtZeile/.test(layout) && /anlageSichtZeile/.test(main));

console.log("\n4) Der Warteschleifen-Schalter");
pruefe("Dienst.dienst() holt GET /dienst", /fun dienst\([\s\S]{0,300}"\/dienst"/.test(dienst));
pruefe("Dienst.setzeDienst() schickt POST /dienst mit ausserDienst", /fun setzeDienst\([\s\S]{0,600}"\/dienst"[\s\S]{0,600}ausserDienst/.test(dienst));
pruefe("die Begründung der Anlage (409) wird durchgereicht", /409/.test(dienst));
pruefe("ein Schalter im Layout", /warteschleifeSchalter/.test(layout) && /MaterialSwitch|SwitchMaterial|SwitchCompat/.test(layout));
pruefe("er schaltet über die Anlage, nicht über *45", /warteschleifeSchalter[\s\S]{0,900}setzeDienst\(/.test(main));
pruefe("die Begründung steht als Text daneben", /warteschleifeGrund/.test(layout) && /warteschleifeGrund/.test(main));

const fehler = ergebnisse.filter(([, ok]) => !ok);
console.log(`\n${ergebnisse.length - fehler.length}/${ergebnisse.length} bestanden`);
process.exit(fehler.length > 0 ? 1 : 0);
