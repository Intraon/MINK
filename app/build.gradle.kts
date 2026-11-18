import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// versionCode = minutter siden 2026-01-01: stiger altid og kræver hverken CI eller git-historik
val buildNo = ((System.currentTimeMillis() / 1000 - 1767225600L) / 60).toInt()
fun prop(name: String) = (project.findProperty(name) as String?) ?: ""
val keyProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "dk.intraon.mink"
    compileSdk = 36

    defaultConfig {
        applicationId = "dk.intraon.mink"
        minSdk = 29
        targetSdk = 35
        versionCode = buildNo
        versionName = "0.4.$buildNo"
        buildConfigField("String", "DEF_BAND_LO", "\"${prop("mink.bandLoMhz")}\"")
        buildConfigField("String", "DEF_BAND_HI", "\"${prop("mink.bandHiMhz")}\"")
        buildConfigField("String", "DEF_SIGNAL_KHZ", "\"${prop("mink.signalKhz")}\"")
        buildConfigField("String", "DEF_TARGET", "\"${prop("mink.targetMhz")}\"")
        buildConfigField("String", "DEF_PPM", "\"${prop("mink.ppm")}\"")
        buildConfigField("String", "DEF_WEBHOOK", "\"${prop("mink.webhook")}\"")
        buildConfigField("String", "DEF_CHANNEL", "\"${prop("mink.channelMhz")}\"")
        buildConfigField("String", "REPO", "\"${prop("mink.repo")}\"")
        buildConfigField("String", "DEF_CHANNEL_KHZ", "\"${prop("mink.channelKhz")}\"")
        buildConfigField("String", "MAP_TILES", "\"${prop("mink.mapTiles")}\"")
    }

    signingConfigs {
        // Nøglen ligger uden for repoet. keystore.properties (i repoets rod, ignoreret af git) peger på den:
        //   storeFile=/sti/til/noegle.jks   storePassword=...   keyAlias=...   keyPassword=...
        // Findes filen ikke, signeres der med Androids debug-nøgle, så man stadig kan bygge og prøve selv.
        if (keyProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = file(keyProps.getProperty("storeFile"))
                storePassword = keyProps.getProperty("storePassword")
                keyAlias = keyProps.getProperty("keyAlias")
                keyPassword = keyProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    testImplementation("junit:junit:4.13.2")
    // Androids egen org.json findes ikke i unittests; denne er den samme kode til brug i testene
    testImplementation("org.json:json:20240303")
}
