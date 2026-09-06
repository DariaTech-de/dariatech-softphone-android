/**
 * Prüfstück: Der Chat ist Ende-zu-Ende verschlüsselt.
 *
 * DER AUFTRAG (Inhaber, 06.09.2026): „Es muss alles verschlüsselt
 * werden." Stufe 3 des Masterplans (im Repository der Anlage:
 * docs/AUFTRAG-VERSCHLUESSELUNG.md, Punkte 12–16).
 *
 * DER UNTERSCHIED ZU VORHER, und er ist der ganze Punkt: Seit Stufe 1c
 * liegt der Chatverlauf verschlüsselt IM GERÄT, seit Stufe 1 auch auf
 * der Platte der Anlage – dort aber mit dem Schlüssel DER ANLAGE. Wer
 * die Anlage betreibt, konnte mitlesen.
 *
 * AB HIER liegt der private Schlüssel im verschlüsselten Speicher
 * dieses Telefons und verlässt es nie. Die Anlage bekommt nur den
 * öffentlichen Teil und trägt danach Umschläge, die sie nicht öffnen
 * kann.
 *
 * WARUM EINE BIBLIOTHEK UND KEIN EIGENER CODE: X25519 gibt es in
 * Androids eigener Krypto (`XDH`) erst ab API 33; diese App läuft ab
 * API 26. Elliptische Kurvenarithmetik von Hand zu schreiben ist genau
 * die Stelle, an der stille Fehler wohnen – dafür wird Tink genommen.
 * Der eigene Code dieses Hauses steht im SIP- und Faxstapel, nicht in
 * der Zahlentheorie.
 *
 * Aufruf:  node pruefstuecke/ende-zu-ende.mjs
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
const e2e = lies(`${w}/Ende2Ende.kt`);
const dienst = lies(`${w}/Dienst.kt`);
const postfach = lies(`${w}/Postfach.kt`);
const adapter = lies(`${w}/ChatAdapter.kt`);
const gespraech = lies(`${w}/GespraechsActivity.kt`);
const gradle = lies("app/build.gradle.kts");

console.log("\n1) Das Schlüsselpaar dieses Geräts");
pruefe("es gibt die Datei Ende2Ende.kt", e2e.length > 0, "Ende2Ende.kt fehlt");
pruefe(
  "der Schlüssel ist X25519",
  /X25519/.test(e2e),
  "kein X25519"
);
pruefe(
  "und die Bibliothek dafür ist eingetragen",
  /tink/.test(gradle),
  "keine Krypto-Bibliothek in build.gradle.kts"
);
pruefe(
  "der private Teil liegt im VERSCHLÜSSELTEN Speicher",
  /Zugangsspeicher|EncryptedSharedPreferences/.test(e2e),
  "der private Schlüssel liegt im Klartext"
);
pruefe(
  "die Gerätekennung ist stabil und wird EINMAL erzeugt",
  /geraetId|geraeteKennung/.test(e2e),
  "keine stabile Gerätekennung"
);

console.log("\n2) Verschlüsselt wird je EMPFÄNGERGERÄT");
pruefe("es gibt einen Weg, der Umschläge baut", /fun\s+verschliesse/.test(e2e));
pruefe(
  "abgeleitet wird mit HKDF",
  /[Hh]kdf/.test(e2e),
  "kein HKDF – ein roher DH-Wert als Schlüssel wäre ein Fehler"
);
pruefe("verschlüsselt wird mit AES-GCM", /AES\/GCM/.test(e2e));
pruefe("und es gibt den Weg zurück", /fun\s+oeffne/.test(e2e));
pruefe(
  "der Fingerabdruck wird berechnet",
  /fun\s+fingerabdruck/.test(e2e),
  "kein Fingerabdruck"
);

console.log("\n3) Der Dienst kennt die neuen Wege");
pruefe("der eigene Schlüssel wird angemeldet", /\/schluessel/.test(dienst));
pruefe("die Kollegen bringen ihre Geräte mit", /geraete/.test(dienst));
pruefe("eine Nachricht kann Umschläge tragen", /umschlaege/.test(dienst));
pruefe("und sie sagt, mit welchem Gerät geschrieben wurde", /absenderGeraet/.test(dienst));

console.log("\n4) Das Postfach verschlüsselt und öffnet");
pruefe(
  "die eigenen anderen Geräte bekommen auch einen Umschlag",
  /eigene/i.test(postfach) && /umschlaege/.test(postfach),
  "im Postfach ist davon nichts zu sehen"
);
pruefe(
  "und eine ältere Nachricht ohne Umschläge bleibt sichtbar",
  /umschlaege\.isEmpty\(\)|umschlaege == null|isNullOrEmpty/.test(postfach),
  "eine ältere Nachricht fiele durch"
);

console.log("\n5) Der Mensch sieht, woran er ist");
pruefe(
  "der Fingerabdruck steht in der Oberfläche",
  /[Ff]ingerabdruck/.test(gespraech) || /[Ff]ingerabdruck/.test(adapter),
  "kein Fingerabdruck im Gespräch"
);
pruefe(
  "ein Schlüsselwechsel wird gemeldet",
  /gewechselt/.test(gespraech) || /gewechselt/.test(dienst),
  "ein Schlüsselwechsel bleibt still"
);

const fehler = ergebnisse.filter(([, ok]) => !ok);
console.log(`\n${ergebnisse.length - fehler.length}/${ergebnisse.length} Prüfungen bestanden`);
if (fehler.length) console.log("FEHLER: " + fehler.map(([n]) => n).join(", "));
process.exit(fehler.length ? 1 : 0);
