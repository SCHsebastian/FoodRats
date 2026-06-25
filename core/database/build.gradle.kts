import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.sqldelight)
}

// Firebase-FREE, JVM 11 (like :core:domain, NOT :core:data) so its iosSimulatorArm64Test links
// and runs — real cross-platform DB coverage. Holds all generated SQLDelight types; domain never
// imports SQLDelight (Konsist-enforced).
kotlin {
    iosArm64()
    iosSimulatorArm64()

    // Silence the K2 "expect/actual class" beta warning for the DriverFactory expect/actual class.
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    androidLibrary {
        namespace = "es.schsebastian.foodrats.core.database"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget = JvmTarget.JVM_11 }
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines.ext)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            // Koin module declarations (databaseModule / driver factory bindings) live here.
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
        // iosTest (default-hierarchy intermediate) needs the native driver on its compile classpath
        // for the in-memory TestDriverFactory actual — iosMain declares it `implementation`, so it
        // isn't transitively visible to tests. `iosTest.dependencies {}` lazily creates the set.
        iosTest.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
        val androidHostTest by getting {
            dependencies {
                implementation(libs.kotlin.testJunit)
                implementation(libs.junit)
                implementation(libs.sqldelight.jvm.driver)
            }
        }
    }
}

sqldelight {
    databases {
        create("FoodRatsDatabase") {
            packageName.set("es.schsebastian.foodrats.core.database")
            generateAsync.set(false)
            // Pre-launch (no shipped schema versions to dump/verify). The 1.sqm migration exists so
            // an upgraded dev/CI install creates the P3b outbox+crew tables instead of crashing;
            // migration *verification* (which needs committed per-version .db schema dumps) is off
            // until launch, matching the project's "schema changes are free pre-launch" convention.
            verifyMigrations.set(false)
        }
    }
}
