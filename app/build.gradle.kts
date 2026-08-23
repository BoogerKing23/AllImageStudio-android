import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Wczytujemy klucz API Gemini z local.properties (plik NIE wchodzi do repo —
// patrz .gitignore), żeby nigdy nie trzymać go na stałe w kodzie źródłowym.
// Awaryjnie można go też podać jako zmienną środowiskową GEMINI_API_KEY
// (przydatne np. w CI).
val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use { load(it) }
    }
}
val geminiApiKey: String = localProperties.getProperty("GEMINI_API_KEY")
    ?: System.getenv("GEMINI_API_KEY")
    ?: ""

android {
    namespace = "com.example.aiimagestudio"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.aiimagestudio"
        minSdk = 29 // Android 10 — pozwala zapisywać obrazy do galerii bez uprawnień pamięci (scoped storage)
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // Trafia do wygenerowanej klasy BuildConfig jako BuildConfig.GEMINI_API_KEY
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Compose (wersje pilnowane przez BOM)
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // Sieć: lekki, bezpośredni REST do Gemini API (bez ciężkiego SDK)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
