import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing lives outside the repo (~/.qnote-keys/signing.properties) so
// the key never lands in git. Without it the release build is simply unsigned,
// which keeps `assembleDebug` and CI working for anyone who clones this.
val signingProps = Properties().apply {
    val f = File(System.getProperty("user.home"), ".qnote-keys/signing.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "dev.neonfire.qnote"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.neonfire.qnote"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "1.2.0"
    }

    signingConfigs {
        if (signingProps.isNotEmpty()) {
            create("release") {
                storeFile = file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Yields dev.neonfire.qnote.debug, which is the second package
            // listed in the watchapp's companionApp declaration, so a debug
            // build talks to the watch without editing the PBW.
            applicationIdSuffix = ".debug"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        // AGP 9 no longer generates BuildConfig by default; the About screen
        // shows VERSION_NAME so the licence notice states which build it is.
        buildConfig = true
    }

    testOptions {
        unitTests {
            // Robolectric needs real resources to inflate the theme, and
            // Roborazzi renders the Compose tree through it.
            isIncludeAndroidResources = true
            all {
                // Write PNGs on every run rather than comparing against a
                // golden; these are store assets, not regression baselines.
                it.systemProperty("roborazzi.test.record", "true")
            }
        }
    }

    sourceSets["main"].java.directories.add("src/main/kotlin")
    sourceSets["test"].java.directories.add("src/test/kotlin")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.pebblekit.client)
    implementation(libs.pebblekit.client.ui)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
