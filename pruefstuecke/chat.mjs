/**
 * Prüfstück: Die Kollegen finden sich – und schreiben sich.
 *
 * DER AUFTRAG (Inhaber, 06.09.2026): „…auch das Chatten ist nicht
 * möglich, weder über Anrufprotokolle noch über Chat, einen User in der
 * Organisation zu finden – also die Mitarbeiter der gleichen
 * Organisation sich gegenseitig finden und chatten."
 *
 * DER STAND VORHER: Der Bereich „Nachrichten" zeigte einen leeren
 * Zustand für Voicemail. Ein Chat war nirgends.
 *
 * WIE ER LÄUFT, und das ist die Entscheidung, die dieses Prüfstück
 * festhält: über den Client-Dienst der Anlage (GET/POST /nachrichten),
 * NICHT über SIP MESSAGE. SIP MESSAGE erreicht nur angemeldete Geräte;
 * ein Telefon, dessen Vordergrunddienst das System beendet hat, ist
 * nicht angemeldet – die Nachricht wäre weg statt verspätet.
 *
 * DIESELBEN WEGE WIE AUF iOS. Zwei Apps, die verschieden mit der Anlage
 * reden, erzeugen zwei Fehlerbilder für dieselbe Ursache.
 *
 * Aufruf:  node pruefstuecke/chat.mjs
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

const dienst = ohneKommentar(lies(`${WURZEL}/Dienst.kt`));
const postfach = ohneKommentar(lies(`${WURZEL}/Postfach.kt`));
const haupt = ohneKommentar(lies(`${WURZEL}/MainActivity.kt`));
const layout = lies("app/src/main/res/layout/activity_main.xml");
const texte = lies("app/src/main/res/values/strings.xml");

console.log("\n1) Der Weg zur Anlage");
{
  pruefe("es gibt einen Absender", /fun sendeNachricht\(/.test(dienst));
  pruefe("und einen Abholer", /fun nachrichten\(/.test(dienst));
  pruefe("beide gehen an /nachrichten", /\/nachrichten/.test(dienst));
  /* NUR NEUES. Ohne `seit` lädt das Telefon bei jeder Frage den ganzen
     Verlauf – über Mobilfunk, auf Kosten des Kunden. */
  pruefe("geholt wird nur, was neu ist", /seit=/.test(dienst));
  pruefe("nicht über den SIP-Stack",
    !/ChatRoom|chatRoom|sendMessage/.test(ohneKommentar(lies(`${WURZEL}/LinphoneManager.kt`))),
    "SIP MESSAGE verliert genau die Nachrichten, die im Hintergrund ankommen");
}

console.log("\n2) Die Nachrichten überleben den Neustart");
{
  pruefe("es gibt eine Ablage im Gerät", existsSync(`${WURZEL}/Postfach.kt`));
  pruefe("sie liegt auf der Platte", /filesDir|File\(/.test(postfach));
  /* WER SCHREIBT, SIEHT ES SOFORT – und es verschwindet wieder, wenn
     das Senden scheitert. Eine Nachricht, die stehen bleibt, obwohl
     sie nie ankam, ist eine Lüge auf dem Bildschirm. */
  pruefe("eigene Nachrichten stehen sofort da", /vorlaeufig/.test(postfach));
  pruefe("und verschwinden wieder, wenn das Senden scheitert",
    /removeAll|filter \{ it\.id != vorlaeufig\.id \}/.test(postfach));
  pruefe("beim Abmelden wird alles gelöscht",
    /Postfach\.leere\(/.test(haupt),
    "sonst liest der nächste Mensch am selben Gerät fremde Nachrichten");
}

console.log("\n3) Der Bereich zeigt die Kollegen");
{
  pruefe("es gibt eine Liste dafür", /viewChat|chatListe/.test(layout));
  pruefe("und eine Zeile je Mensch", existsSync("app/src/main/res/layout/zeile_chat.xml"));
  pruefe("mit Gesicht", /zeileChatBild|chatBild/.test(lies("app/src/main/res/layout/zeile_chat.xml")));
  /* KEIN ZWEITES VERZEICHNIS. Zwei Listen derselben Menschen sind die
     Stelle, an der einer fehlt. */
  pruefe("die Menschen kommen aus dem Verzeichnis", /Verzeichnis\.kollegen/.test(haupt));
  pruefe("der Voicemail-Platzhalter ist nicht mehr der ganze Bereich",
    /zeigeChat|chatAdapter/.test(haupt));
}

console.log("\n4) Das Gespräch selbst");
{
  pruefe("es gibt einen Bildschirm dafür", existsSync(`${WURZEL}/GespraechsActivity.kt`));
  pruefe("mit einem Eingabefeld", /chatEingabe/.test(lies("app/src/main/res/layout/activity_gespraech.xml")));
  pruefe("und er steht im Manifest",
    /GespraechsActivity/.test(lies("app/src/main/AndroidManifest.xml")),
    "eine Activity, die nicht im Manifest steht, stürzt beim Öffnen ab");
}

console.log("\n5) Die Texte stehen in strings.xml");
{
  for (const name of ["chat_leer_titel", "chat_senden"]) {
    pruefe(`„${name}“ ist hinterlegt`, new RegExp(`name="${name}"`).test(texte),
      "fest eingetippte Oberflächentexte lassen sich nicht ändern und nicht übersetzen");
  }
}

const fehler = ergebnisse.filter(([, ok]) => !ok);
console.log(`\n${ergebnisse.length - fehler.length}/${ergebnisse.length} Prüfungen bestanden`);
if (fehler.length > 0) {
  console.log("FEHLGESCHLAGEN: " + fehler.map(([n]) => n).join(", "));
  process.exit(1);
}
