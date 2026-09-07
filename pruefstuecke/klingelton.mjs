/**
 * Prüfstück: Klingelton-Auswahl (offener Punkt 1 der CLAUDE.md).
 *
 * Auf Android klingelt der KANAL der Anrufmeldung, nicht die App. Ein
 * Kanal ist nach dem Anlegen unveränderlich – auch sein Ton. Wer den
 * Klingelton wechseln will, muss also einen NEUEN Kanal anlegen (mit
 * neuer Kennung) und den alten löschen. Wer das nicht weiß, setzt den
 * Ton, sieht keinen Fehler, und es klingelt wie vorher.
 *
 * Aufruf:  node pruefstuecke/klingelton.mjs
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
const meldung = ohneKommentare(lies(Q + "Anrufmeldung.kt"));
const main = ohneKommentare(lies(Q + "MainActivity.kt"));
const layout = lies("app/src/main/res/layout/activity_main.xml");

pruefe("die Einstellungen haben eine Zeile „Klingelton“", /klingeltonZeile/.test(layout) && /klingeltonZeile/.test(main));
pruefe("sie öffnet die Auswahl des Systems (RingtoneManager)", /ACTION_RINGTONE_PICKER/.test(main));
pruefe("der gewählte Ton landet im Kanal der Anrufmeldung (setSound)", /setSound\(/.test(meldung));
pruefe("der Kanal bekommt bei einem Wechsel eine NEUE Kennung", /fun kanalAnruf\(/.test(meldung) && /deleteNotificationChannel/.test(meldung), "ein Kanal ist nach dem Anlegen unveränderlich – auch sein Ton");
pruefe("die Anrufmeldung benutzt die aktuelle Kennung, keine feste", /Builder\(context, kanalAnruf\(context\)\)/.test(meldung));

const fehler = ergebnisse.filter(([, ok]) => !ok);
console.log(`\n${ergebnisse.length - fehler.length}/${ergebnisse.length} bestanden`);
process.exit(fehler.length > 0 ? 1 : 0);
