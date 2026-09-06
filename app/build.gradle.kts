plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "de.dariatech.softphone"
    compileSdk = 34

    defaultConfig {
        applicationId = "de.dariatech.softphone"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "0.2.2"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    /* TELECOM: Der Anruf gehört dem SYSTEM (Etappe 1, docs/AUFTRAG-AUTO.md).
       Ohne diese Bibliothek ist die App in Android Auto und an jeder
       Freisprecheinrichtung unsichtbar – die Lenkradtaste nimmt nichts
       an, weil das System von unseren Anrufen nichts weiß. Google
       verlangt sie ausserdem als Voraussetzung für die Telefonie-
       Kategorie in Android Auto. */
    implementation("androidx.core:core-telecom:1.0.0")

    implementation("org.linphone:linphone-sdk-android:5.4.47")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    // Verschlüsselte Ablage für das SIP-Passwort (Android-Keystore).
    // Siehe Zugangsspeicher.kt – ein SIP-Passwort im Klartext ist eine
    // Telefonrechnung, die jemand anderes schreibt.
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    /* X25519 für den Ende-zu-Ende-Chat (Stufe 3 der Verschlüsselung).
       Androids eigenes „XDH" gibt es erst ab API 33; diese App läuft ab
       API 26, und ein halbes Haus ohne Verschlüsselung wäre keine
       Verschlüsselung. Die Kurve von Hand zu rechnen ist genau die
       Stelle, an der stille Fehler wohnen. */
    implementation("com.google.crypto.tink:tink-android:1.13.0")
    // Der Anrufverlauf als Liste – siehe VerlaufAdapter.
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}
