plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.elyssov.archives"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.elyssov.archives"
        minSdk = 24
        targetSdk = 34
        // versionCode is Play Store's monotonic integer (must increase each release).
        // Scheme: major*100 + minor*10 + patch. So 1.5 → 15, 1.6 → 16, 1.10 → 110.
        versionCode = 19
        versionName = "1.9"
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

    // Google Play Billing — for the optional tip-jar pop-up at launch.
    // Five consumable SKUs (tip_1, tip_5, tip_10, tip_15, tip_20). After
    // ANY successful tip the prompt never returns; restore works through
    // queryPurchasesAsync(INAPP) on every launch (carries to a new device).
    implementation("com.android.billingclient:billing-ktx:6.1.0")
}
