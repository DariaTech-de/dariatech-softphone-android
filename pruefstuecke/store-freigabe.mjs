/**
 * Prüfstück: Was Google Play prüft, bevor die App überhaupt jemand
 * herunterladen darf.
 *
 * DER AUFTRAG (Inhaber, 06.09.2026): „Den Apps werden normal zum
 * Downloaden freigeben und müssen erst die Prüfung bestehen." Stufe 6
 * des Masterplans (im Repository der Anlage:
 * docs/AUFTRAG-VERSCHLUESSELUNG.md, Punkte 35–40).
 *
 * WARUM DAS EIN PRÜFSTÜCK IST UND KEINE MERKLISTE: Die Fehler, an denen
 * eine Einreichung scheitert, sind alle im Quelltext sichtbar – ein
 * fehlender Dienst-Typ, ein zu altes Ziel-SDK, erlaubter Klartext-HTTP.
 * Sie fallen aber erst auf, wenn Play die Datei ablehnt, und dann sind
 * zwei Tage weg. Hier fallen sie in einer Sekunde auf.
 *
 * DER WICHTIGSTE PUNKT IST KEINER VON PLAYS: `allowBackup`. Steht es
 * auf wahr (Androids Vorgabe!), wandert der verschlüsselte Speicher
 * dieser App in Googles Sicherung – und mit ihm der PRIVATE Schlüssel
 * des Ende-zu-Ende-Chats. Die Zusage aus Stufe 3 lautet „er verlässt
 * das Gerät nie". Mit einer Sicherung wäre sie unwahr.
 *
 * Aufruf:  node pruefstuecke/store-freigabe.mjs
 */
import { readFileSync, existsSync, readdirSync } from "node:fs";
import { execFileSync } from "node:child_process";

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

const manifest = lies("app/src/main/AndroidManifest.xml");
const gradle = lies("app/build.gradle.kts");
const liste = lies("docs/STORE-FREIGABE.md");

console.log("\n1) Der private Schlüssel verlässt das Gerät nicht – auch nicht per Sicherung");
pruefe(
  "die Sicherung durch Google ist abgeschaltet",
  /android:allowBackup="false"/.test(manifest),
  "allowBackup fehlt oder steht auf true – Androids Vorgabe ist TRUE, und damit " +
    "wandert der private Schlüssel des Chats in die Cloud"
);

console.log("\n2) Vordergrunddienst: Typen und Begründung (Android 14, Punkt 36)");
pruefe(
  "der Dienst nennt phoneCall",
  /foregroundServiceType="[^"]*phoneCall/.test(manifest),
  "ohne Typ startet der Dienst auf Android 14 gar nicht"
);
pruefe(
  "und microphone",
  /foregroundServiceType="[^"]*microphone/.test(manifest),
  "der Dienst nimmt im Gespräch Ton auf – der Typ fehlt"
);
pruefe(
  "die Erlaubnis für phoneCall steht dabei",
  /FOREGROUND_SERVICE_PHONE_CALL/.test(manifest)
);
pruefe(
  "die für microphone auch",
  /FOREGROUND_SERVICE_MICROPHONE/.test(manifest),
  "ohne diese Erlaubnis wirft Android beim Start des Dienstes"
);

console.log("\n3) Kein Klartext im Netz (Punkt 40)");
pruefe(
  "Klartext-HTTP ist ausdrücklich verboten",
  /android:usesCleartextTraffic="false"/.test(manifest),
  "Play prüft das automatisch und weist es ab"
);

console.log("\n4) Ziel-SDK (Punkt 37)");
{
  const m = /targetSdk\s*=\s*(\d+)/.exec(gradle);
  const ziel = m ? Number(m[1]) : 0;
  pruefe("Ziel-SDK ist mindestens 34", ziel >= 34, String(ziel));
}

console.log("\n5) Die Prüfliste liegt im Repository (Nachweis der Stufe 6)");
pruefe("docs/STORE-FREIGABE.md gibt es", liste.length > 0, "die Prüfliste fehlt");
pruefe(
  "sie nennt das Data-Safety-Formular",
  /Data.?Safety/i.test(liste),
  "Punkt 35 fehlt"
);
pruefe(
  "sie begründet JEDE Berechtigung",
  ["RECORD_AUDIO", "CAMERA", "POST_NOTIFICATIONS"].every((p) => liste.includes(p)),
  "Punkt 38: eine Berechtigung ohne sichtbare Funktion fliegt raus"
);
pruefe(
  "sie nennt den Weg zur Datenlöschung außerhalb der App",
  /Löschung|löschen/i.test(liste) && /Adresse|URL|Web/i.test(liste),
  "Punkt 39: Play verlangt eine Adresse"
);

/* GEPRÜFT WIRD DAS GEBAUTE PAKET, NICHT DAS EIGENE MANIFEST.
   Berichtigt am 06.09.2026 nach einem Nachfahren. Bis dahin las dieser
   Abschnitt `app/src/main/AndroidManifest.xml` – und sah damit
   grundsätzlich nur die Hälfte: Beim Bau mischt Gradle die
   Berechtigungen der Bibliotheken (Liblinphone, AndroidX) hinein. Im
   ausgelieferten Paket standen NEUN Berechtigungen, die in keiner
   Prüfliste vorkamen, darunter FOREGROUND_SERVICE_CAMERA,
   FOREGROUND_SERVICE_DATA_SYNC und BLUETOOTH_CONNECT.
   Google sieht das Paket, nicht unser Manifest. Also sieht dieses
   Prüfstück ab jetzt dasselbe. */
console.log("\n6) Und jede Berechtigung im GEBAUTEN PAKET steht auch in der Liste");
{
  const apk = "app/build/outputs/apk/debug/app-debug.apk";
  const sdk = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT || "/opt/android-sdk";
  let aapt = "";
  try {
    aapt = readdirSync(`${sdk}/build-tools`).sort().reverse()
      .map((v) => `${sdk}/build-tools/${v}/aapt2`).find((w) => existsSync(w)) || "";
  } catch { /* kein SDK da */ }

  if (!existsSync(apk) || !aapt) {
    /* KEIN GRÜN OHNE PAKET. Ein Prüfstück, das mangels Bau stillschweigend
       durchwinkt, ist genau die Lücke, die hier behoben wurde. */
    pruefe(
      "das gebaute Paket liegt vor und lässt sich lesen",
      false,
      !existsSync(apk)
        ? `${apk} fehlt – erst \`gradle assembleDebug\` laufen lassen`
        : `aapt2 nicht gefunden unter ${sdk}/build-tools`
    );
  } else {
    const roh = execFileSync(aapt, ["dump", "permissions", apk], { encoding: "utf-8" });
    const gefragt = [...roh.matchAll(/^uses-permission: name='android\.permission\.([A-Z_]+)'/gm)]
      .map((m) => m[1]);
    const fehlend = [...new Set(gefragt)].filter((p) => !liste.includes(p));
    pruefe(
      "keine unbegründete Berechtigung im Paket",
      fehlend.length === 0,
      "nicht in der Prüfliste begründet: " + fehlend.join(", ")
    );
    pruefe(
      "und das Paket trägt überhaupt Berechtigungen (das Lesen hat geklappt)",
      gefragt.length > 5,
      `${gefragt.length} gelesen`
    );
  }
}

const fehler = ergebnisse.filter(([, ok]) => !ok);
console.log(`\n${ergebnisse.length - fehler.length}/${ergebnisse.length} Prüfungen bestanden`);
if (fehler.length) console.log("FEHLER: " + fehler.map(([n]) => n).join(", "));
process.exit(fehler.length ? 1 : 0);
