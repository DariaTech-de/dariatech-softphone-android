/**
 * Prüfstück: Was im Gerät liegt, liegt nicht im Klartext.
 *
 * DER AUFTRAG (Inhaber, 06.09.2026): „Es muss alles verschlüsselt
 * werden." Stufe 1c des Masterplans (im Repository der Anlage:
 * docs/AUFTRAG-VERSCHLUESSELUNG.md) – nach der Anlage jetzt das Gerät.
 *
 * DER BEFUND: Das SIP-Passwort und das Token liegen seit dem 30.08.2026
 * im verschlüsselten Speicher (EncryptedSharedPreferences). Die
 * INHALTE nicht: Der Chatverlauf lag als `nachrichten.json` in
 * `filesDir`, die Bilder der Kollegen als JPEG in `cacheDir`.
 *
 * WER DAS LIEST: Wer das Gerät gerootet hat, wer eine Sicherung
 * ausliest, wer ein weitergegebenes Diensthandy in die Hand bekommt.
 * Auf einem Firmentelefon, das zwischen Mitarbeitern wandert, ist das
 * der wahrscheinlichste Fall von allen.
 *
 * DER WEG IST DERSELBE WIE BEIM PASSWORT: `EncryptedFile` aus
 * androidx.security – derselbe Baustein wie
 * EncryptedSharedPreferences, derselbe Schlüssel im Android-Keystore,
 * der das Gerät nie verlässt. Kein eigener Krypto-Code: Wer ihn selbst
 * schreibt, macht ihn falsch.
 *
 * DASSELBE GIBT ES AUF iOS (`Tresor.swift`). Zwei Apps, die ihre Daten
 * verschieden schützen, erzeugen zwei Fehlerbilder für dieselbe Frage
 * des Datenschutzbeauftragten.
 *
 * Aufruf:  node pruefstuecke/ruhende-daten.mjs
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
const tresor = ohneKommentar(lies(`${WURZEL}/Tresor.kt`));
const postfach = ohneKommentar(lies(`${WURZEL}/Postfach.kt`));
const verzeichnis = ohneKommentar(lies(`${WURZEL}/Verzeichnis.kt`));
const haupt = ohneKommentar(lies(`${WURZEL}/MainActivity.kt`));

console.log("\n1) Es gibt EINEN Ort für verschlüsselte Dateien");
{
  pruefe("es gibt einen Tresor", existsSync(`${WURZEL}/Tresor.kt`));
  pruefe("er schreibt und liest", /fun schreibe\(/.test(tresor) && /fun lies\(/.test(tresor));
  /* KEIN EIGENER KRYPTO-CODE. Der Baustein von Google nimmt den
     Android-Keystore; ein selbstgebautes AES daneben wäre die Stelle,
     an der ein Schlüssel im Code landet. */
  pruefe("mit EncryptedFile aus androidx.security", /EncryptedFile/.test(tresor));
  pruefe("und dem Schlüssel im Keystore", /MasterKey/.test(tresor));
  pruefe("kein selbstgebautes AES daneben",
    !/Cipher\.getInstance|SecretKeySpec/.test(tresor),
    "wer Krypto selbst schreibt, macht sie falsch");
}

console.log("\n2) Der Chatverlauf geht durch den Tresor");
{
  pruefe("das Postfach schreibt nicht mehr roh",
    !/\.writeText\(/.test(postfach) && /Tresor\./.test(postfach),
    "sonst liegt der Verlauf weiter im Klartext in filesDir");
  pruefe("und liest auch darüber", (postfach.match(/Tresor\./g) ?? []).length >= 2);
}

console.log("\n3) Auch die Bilder der Kollegen");
{
  pruefe("das Verzeichnis legt Bilder über den Tresor ab", /Tresor\./.test(verzeichnis));
  pruefe("und liest sie darüber", (verzeichnis.match(/Tresor\./g) ?? []).length >= 2);
  /* NICHT IN DEN cacheDir. Was dort liegt, räumt Android bei Bedarf
     weg – und es ist der Ordner, den jedes Sicherungswerkzeug als
     erstes mitnimmt. */
  pruefe("und nicht mehr im allgemeinen Zwischenspeicher",
    !/cacheDir/.test(verzeichnis), "filesDir statt cacheDir");
}

console.log("\n4) Beim Benutzerwechsel ist alles weg");
{
  pruefe("Postfach und Verzeichnis werden geleert",
    /Postfach\.leere\(/.test(haupt) && /Verzeichnis\.leere\(/.test(haupt));
  pruefe("und die Dateien wirklich gelöscht",
    /delete\(\)/.test(postfach) && /deleteRecursively\(\)|delete\(\)/.test(verzeichnis));
}

const fehler = ergebnisse.filter(([, ok]) => !ok);
console.log(`\n${ergebnisse.length - fehler.length}/${ergebnisse.length} Prüfungen bestanden`);
if (fehler.length > 0) {
  console.log("FEHLGESCHLAGEN: " + fehler.map(([n]) => n).join(", "));
  process.exit(1);
}
