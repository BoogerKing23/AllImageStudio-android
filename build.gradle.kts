// Plugins są tu tylko zadeklarowane (apply false) — faktycznie
// stosowane są w app/build.gradle.kts.
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
