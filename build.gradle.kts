plugins {
    id("com.android.application") version "9.4.1" apply false
    // Nur Classpath: hebt die Kotlin-Version des in AGP 9 eingebauten Kotlin-Supports an
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("com.google.devtools.ksp") version "2.3.12" apply false
}
