import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinxSerialization)
}

kotlin {
    iosArm64()
    iosSimulatorArm64()
    androidLibrary {
        namespace = "es.schsebastian.foodrats.feature.achievements"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        // JVM 17 (w2-badges-data): the data layer now touches Firebase via the GitLive
        // firebase-firestore artifact + Firebase BOM (whose inline funcs are compiled at JVM 17),
        // so this module follows :feature:crew/:feature:meal off JVM 11 (spec §4).
        compilerOptions { jvmTarget = JvmTarget.JVM_17 }
        androidResources { enable = true }
        withHostTest {
            isIncludeAndroidResources = true
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.domain)
            implementation(projects.core.data)
            implementation(projects.core.designsystem)
            implementation(projects.core.presentation)
            implementation(projects.core.i18n)
            implementation(libs.bundles.feature.ui)
            implementation(libs.bundles.kotlinx.common)
            // Data layer (w2-badges-data): persists unlock timestamps under
            // accounts/{uid}/achievements via GitLive Firestore (spec §6.2).
            implementation(libs.firebase.firestore)
        }
        androidMain.dependencies {
            // Firebase BOM pins the com.google.firebase:* artifacts the GitLive KMP wrappers pull
            // in transitively on Android — required in every Firebase-touching module (CLAUDE.md).
            implementation(project.dependencies.platform(libs.firebase.bom))
        }
        commonTest.dependencies {
            implementation(libs.bundles.feature.test)
        }
        val androidHostTest by getting {
            dependencies {
                implementation(libs.bundles.feature.hosttest)
            }
        }
    }
}
