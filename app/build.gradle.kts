plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.elyssov.archives"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.elyssov.eugenesarchives"
        minSdk = 24
        targetSdk = 36
        // versionCode is Play Store's monotonic integer (must increase each release).
        // Scheme: major*100 + minor*10 + patch. So 1.5 → 15, 1.6 → 16, 1.10 → 110.
        versionCode = 39
        versionName = "2.9.5"
    }

    signingConfigs {
        create("release") {
            val ksFile = file("archives-release.jks")
            if (ksFile.exists()) {
                storeFile = ksFile
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "archives2026"
                keyAlias = System.getenv("KEY_ALIAS") ?: "archives"
                keyPassword = System.getenv("KEY_PASSWORD") ?: "archives2026"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val releaseSigning = signingConfigs.findByName("release")
            if (releaseSigning?.storeFile?.exists() == true) {
                signingConfig = releaseSigning
            }
        }
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
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.webkit:webkit:1.10.0")
    // Транзитивно у нас тянется androidx.fragment 1.0.0 — Google Play в Pre-launch
    // отчёт об устаревшем SDK. Закрепляем явную свежую (1.0.0 → 1.8.5).
    implementation("androidx.fragment:fragment-ktx:1.8.5")

}
