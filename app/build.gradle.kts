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
    implementation("org.linphone:linphone-sdk-android:5.4.47")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    // Verschlüsselte Ablage für das SIP-Passwort (Android-Keystore).
    // Siehe Zugangsspeicher.kt – ein SIP-Passwort im Klartext ist eine
    // Telefonrechnung, die jemand anderes schreibt.
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    // Der Anrufverlauf als Liste – siehe VerlaufAdapter.
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}
