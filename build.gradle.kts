// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "9.4.1" apply false
    // Nur Classpath: hebt die Kotlin-Version des in AGP 9 eingebauten Kotlin-Supports an
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("com.google.devtools.ksp") version "2.3.12" apply false
    id("com.google.dagger.hilt.android") version "2.60.1" apply false
    id("androidx.navigation.safeargs.kotlin") version "2.10.2" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.11" apply false
}
