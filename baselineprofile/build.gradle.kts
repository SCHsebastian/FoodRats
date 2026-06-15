import com.android.build.api.dsl.ManagedVirtualDevice
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// ---------------------------------------------------------------------------
// :baselineprofile — Android Macrobenchmark module (roadmap §5.3).
//
// Two responsibilities, both driven on a real device / emulator (NEVER on a
// plain JVM host — macrobenchmark drives a *separate* app process via UiAutomator):
//
//   1. BaselineProfileGenerator  — records the classes/methods touched during a
//      cold start + first-screen journey; the androidx.baselineprofile plugin
//      consumes its output into :androidApp's release ART profile.
//   2. StartupBenchmark           — measures cold/warm StartupTimingMetric with
//      CompilationMode None vs Partial(BaselineProfile) so the speed-up is proven.
//
// A Gradle Managed Device (GMD) lets both run headlessly on a Linux runner WITH
// KVM. GitHub's *free* ubuntu runners do NOT expose nested virtualization, so CI
// runs this best-effort / manually — see .github/workflows/ci.yml + the report.
// `targetProjectPath = ":androidApp"` wires it to the app under test.
//
// This is a plain `com.android.test` module (NOT KMP): macrobenchmark code is
// Android-only by definition (it drives an Android app process), and the
// `com.android.test` plugin models a single test variant with sources in src/main.
// ---------------------------------------------------------------------------
plugins {
    // AGP 9.0 ships built-in Kotlin support, so no separate org.jetbrains.kotlin.android
    // plugin is applied (it's rejected) — mirrors :androidApp.
    alias(libs.plugins.androidTest)
    alias(libs.plugins.androidxBaselineprofile)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

android {
    namespace = "es.schsebastian.foodrats.baselineprofile"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        // Macrobenchmark needs API 28+ for full profile data; the app's minSdk 30 is fine.
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // The app under test. The plugin reads its variants and writes the profile back.
    targetProjectPath = ":androidApp"

    // Pixel 6, API 34, AOSP ATD ("Automated Test Device" — no GMS, no animations,
    // fastest headless image). `aosp-atd` keeps the run light enough for a CI emulator
    // when KVM is available; swap to `google-atd` only if the journey needs Play services.
    testOptions {
        managedDevices {
            allDevices {
                create<ManagedVirtualDevice>("pixel6Api34") {
                    device = "Pixel 6"
                    apiLevel = 34
                    systemImageSource = "aosp-atd"
                }
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.testExt.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.junit)
}

// Run the generator/benchmark on the GMD by default
// (`./gradlew :baselineprofile:generateBaselineProfile`). Drop `managedDevices` to fall
// back to a connected/booted device or emulator.
baselineProfile {
    managedDevices += "pixel6Api34"
    useConnectedDevices = false
}
