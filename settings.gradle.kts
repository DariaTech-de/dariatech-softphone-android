pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // Liblinphone SDK (SIP/RTP-Stack für Android)
        maven { url = uri("https://download.linphone.org/maven_repository") }
    }
}

rootProject.name = "DariaTech Softphone"
include(":app")
