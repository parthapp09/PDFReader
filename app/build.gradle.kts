plugins {
    alias(libs.plugins.android.application)

}

android {
    namespace = "com.itspark.pdfreader"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.itspark.pdfreader"
        minSdk = 23
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    // Coroutines & Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.fragment:fragment-ktx:1.8.2")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // PDFBox for Android - Apache 2.0 Open Source Text Extraction & Coordinate Search
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    // Google Play Install Referrer (Attribution & GCLID Detection)
    implementation("com.android.installreferrer:installreferrer:2.2")
    // Firebase Analytics (User Segmentation & Attribution Events)
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-analytics")
    // Google Mobile Ads SDK (AdMob)
    implementation("com.google.android.gms:play-services-ads:23.0.0")
    // Process Lifecycle (Foreground App Open Ads)
    implementation("androidx.lifecycle:lifecycle-process:2.8.4")
    // Google User Messaging Platform (UMP SDK for GDPR / Consent Management)
    implementation("com.google.android.ump:user-messaging-platform:2.2.0")
}