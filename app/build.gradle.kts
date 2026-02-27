plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.dagger.hilt.android")
    id("androidx.navigation.safeargs.kotlin")
    id("org.jetbrains.kotlinx.kover")
}

koverReport {
    filters {
        excludes {
            // Exclude Android UI, DI, generated code from coverage
            classes(
                // UI layer
                "*Fragment",
                "*Fragment\$*",
                "*Activity",
                "*Activity\$*",
                "*Adapter",
                "*Adapter\$*",
                "*Application",
                "*Application\$*",
                "*ViewModel",
                "*ViewModel\$*",
                "*UiState",
                "*UiState\$*",
                "*Receiver",
                "*Receiver\$*",
                // Service (Android-dependent, not unit-testable)
                "*Service",
                "*Service\$*",
                // UI helper classes
                "de.fosstenbuch.ui.stats.StatItem",
                "de.fosstenbuch.ui.trips.TripFilter",
                "de.fosstenbuch.ui.trips.TripSort",
                "de.fosstenbuch.ui.trips.TripPhase",
                "de.fosstenbuch.ui.stats.StatsFilterMode",
                "de.fosstenbuch.ui.common.*",
                // DI modules
                "*Module",
                "*Module\$*",
                // Hilt / Dagger generated
                "*_Factory",
                "*_Factory\$*",
                "*_HiltModules*",
                "*_MembersInjector",
                "*Binding*",
                "hilt_aggregated_deps.*",
                "*_HiltComponents*",
                "*_ComponentTreeDeps*",
                "*_GeneratedInjector",
                "*Hilt_*",
                // Room generated DAO implementations
                "*_Impl",
                "*_Impl\$*",
                // Navigation generated
                "*BuildConfig",
                "*Directions",
                "*Directions\$*",
                "*Args",
                "*Args\$*",
                // DAOs (interfaces, covered via repository tests)
                "*Dao",
                "*Dao\$*",
                // Android-dependent classes not unit-testable
                "*PdfTripExporter",
                "*PdfTripExporter\$*",
                "*BackupManager",
                "*BackupManager\$*",
                "*PreferencesManager",
                "*PreferencesManager\$*",
                "*PreferencesManagerKt",
                "*TimberDebugTree",
                // Room database
                "de.fosstenbuch.data.local.AppDatabase",
                "de.fosstenbuch.data.local.AppDatabase\$*"
            )
            annotatedBy("dagger.Module", "dagger.internal.DaggerGenerated", "androidx.room.Database")
        }
    }
}

android {
    namespace = "de.fosstenbuch"
    compileSdk = 34

    defaultConfig {
        applicationId = "de.garske_systems.FOSStenbuch"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Use a consistent debug keystore from CI secrets so debug APK updates
        // work without uninstalling. Falls back to default local debug keystore.
        getByName("debug") {
            val debugKsPath = System.getenv("DEBUG_KEYSTORE_PATH")
            if (debugKsPath != null) {
                storeFile = file(debugKsPath)
                storePassword = System.getenv("DEBUG_KEYSTORE_PASSWORD") ?: "android"
                keyAlias = System.getenv("DEBUG_KEY_ALIAS") ?: "androiddebugkey"
                keyPassword = System.getenv("DEBUG_KEY_PASSWORD") ?: "android"
            }
        }
        // Release signing via CI secrets so Gradle produces a signed APK directly.
        create("release") {
            val releaseKsPath = System.getenv("RELEASE_KEYSTORE_PATH")
            if (releaseKsPath != null) {
                storeFile = file(releaseKsPath)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        dataBinding = false
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    
    // Mocking for tests
    testImplementation("io.mockk:mockk:1.13.9")
    androidTestImplementation("io.mockk:mockk-android:1.13.9")
    
    // Coroutines test
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")

    // Room for database
    implementation("androidx.room:room-runtime:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.room:room-paging:2.6.1")

    // Paging 3
    implementation("androidx.paging:paging-runtime-ktx:3.2.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-common-java8:2.7.0")

    // Navigation
    implementation("androidx.navigation:navigation-fragment-ktx:2.6.0")
    implementation("androidx.navigation:navigation-ui-ktx:2.6.0")

    // Hilt for Dependency Injection
    implementation("com.google.dagger:hilt-android:2.48")
    kapt("com.google.dagger:hilt-compiler:2.48")

    // Timber for logging
    implementation("com.jakewharton.timber:timber:5.0.1")

    // Google Play Services Location (GPS)
    implementation("com.google.android.gms:play-services-location:21.2.0")

    // DataStore for preferences
    implementation("androidx.datastore:datastore-preferences:1.0.0")
}