/**
 * Prüfstück: Das SIP-Passwort liegt NICHT im Klartext.
 *
 * DER ANLASS ist ein Auftrag des Inhabers vom 30.08.2026: die Apps
 * „vollständig, sauber und sicher" auf höchstes Niveau zu bringen. Beim
 * Nachsehen stand das SIP-Passwort in gewöhnlichen SharedPreferences:
 *
 *   prefs.edit().putString("password", binding.password.text.toString())
 *
 * Das ist eine XML-Datei im App-Verzeichnis, im Klartext. Wer das Gerät
 * root hat, wer eine Sicherung ausliest, wer ein gebrauchtes
 * Diensthandy bekommt – alle lesen mit. Und ein SIP-Passwort ist nicht
 * irgendein Passwort: Damit telefoniert jemand auf Kosten des Kunden,
 * ins Ausland, in Premium-Nummern. Genau der Schaden, gegen den in der
 * Anlage die Gebührenbetrugs-Erkennung gebaut wurde.
 *
 * WARUM ALS QUELLTEXT-VERTRAG und nicht als Gerätetest:
 * EncryptedSharedPreferences braucht den Android-Keystore, also ein
 * echtes Gerät oder einen Emulator. Der steht in der Prüfumgebung nicht
 * zur Verfügung. Geprüft wird deshalb das, was sich ohne Gerät prüfen
 * lässt und den Fehler trotzdem fängt: dass der Quelltext das Passwort
 * nirgends in einen unverschlüsselten Speicher schreibt.
 *
 * Aufruf:  node pruefstuecke/passwort.mjs
 */
import { readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";

const ergebnisse = [];
const pruefe = (name, ok, extra = "") => {
  ergebnisse.push([name, ok]);
  console.log((ok ? "  ok   " : " FEHL  ") + name + (ok || !extra ? "" : "  – " + extra));
};

const QUELLE = "app/src/main/java/de/dariatech/softphone";
const dateien = readdirSync(QUELLE)
  .filter((n) => n.endsWith(".kt"))
  .map((n) => ({ name: n, text: readFileSync(join(QUELLE, n), "utf-8") }));

/** Kommentare heraus – sonst schlägt jede Erklärung an, die das Wort nennt. */
const ohneKommentare = (t) =>
  t.replace(/\/\*[\s\S]*?\*\//g, "").replace(/^\s*\/\/.*$/gm, "");

console.log("\n1) Kein Passwort in gewöhnlichen SharedPreferences");
{
  const treffer = [];
  for (const d of dateien) {
    const code = ohneKommentare(d.text);
    // Ein Schreiben des Passworts in einen Speicher, der nicht der
    // verschlüsselte ist. Gesucht wird nach dem Schlüsselnamen, weil
    // genau der die Ablage bestimmt.
    for (const zeile of code.split("\n")) {
      if (!/putString\s*\(\s*"password"/.test(zeile)) continue;
      treffer.push(`${d.name}: ${zeile.trim()}`);
    }
  }
  pruefe("nirgends putString(\"password\") außerhalb des sicheren Speichers",
    treffer.length === 0, treffer.join(" | "));
}

console.log("\n2) Es gibt einen eigenen, verschlüsselten Speicher");
{
  const speicher = dateien.find((d) => d.name === "Zugangsspeicher.kt");
  pruefe("Zugangsspeicher.kt liegt vor", Boolean(speicher));
  const t = speicher?.text ?? "";
  pruefe("er benutzt EncryptedSharedPreferences", /EncryptedSharedPreferences/.test(t));
  pruefe("mit einem Schlüssel aus dem Android-Keystore", /MasterKey/.test(t));
  pruefe("und AES-256", /AES256/.test(t), "");
}

console.log("\n3) Bestandsschutz: ein altes Klartext-Passwort wird UMGEZOGEN");
{
  // Wer die App seit Monaten benutzt, hat sein Passwort im alten
  // Speicher. Es einfach zu ignorieren hieße: Er wird beim nächsten
  // Start abgemeldet und weiß nicht warum. Es stehenzulassen hieße: Die
  // Lücke bleibt. Also umziehen – und den alten Eintrag löschen.
  const speicher = dateien.find((d) => d.name === "Zugangsspeicher.kt");
  const t = ohneKommentare(speicher?.text ?? "");
  pruefe("der alte Speicher wird gelesen", /getSharedPreferences/.test(t));
  pruefe("und der alte Eintrag danach entfernt", /\.remove\(/.test(t));
}

console.log("\n4) Das Passwort steht in keiner Protokollzeile");
{
  const treffer = [];
  for (const d of dateien) {
    const code = ohneKommentare(d.text);
    for (const zeile of code.split("\n")) {
      if (!/\b(Log\.[dviwe]|println)\s*\(/.test(zeile)) continue;
      // GENAUER ALS BEIM ERSTEN VERSUCH. Der schlug auf JEDE Zeile an,
      // die das Wort enthält – auch auf „Altes Passwort übernommen und
      // gelöscht", die keinen Wert ausgibt. Ein Prüfstück, das
      // erzwingt, dass man um ein Wort herumschreibt, erzieht zu
      // schlechteren Meldungen und findet den echten Fall trotzdem
      // nicht.
      //
      // Der echte Fall ist ein WERT in der Zeile: eine Einsetzung
      // ($passwort, ${wert}) oder eine Variable als Argument. Genau
      // danach wird gesucht.
      const einsetzung = /\$\{?[A-Za-z_][A-Za-z0-9_.]*/.exec(zeile);
      if (!einsetzung) continue;
      if (/\$\{?[A-Za-z_.]*passwor(t|d)/i.test(zeile)) {
        treffer.push(`${d.name}: ${zeile.trim()}`);
      }
    }
  }
  pruefe("kein Log, das einen Passwortwert einsetzt", treffer.length === 0, treffer.join(" | "));
}

const fehler = ergebnisse.filter(([, ok]) => !ok);
console.log(`\n${ergebnisse.length - fehler.length}/${ergebnisse.length} Prüfungen bestanden`);
if (fehler.length > 0) console.log("FEHLGESCHLAGEN: " + fehler.map(([n]) => n).join(", "));
process.exit(fehler.length > 0 ? 1 : 0);
