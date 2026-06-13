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
        namespace = "es.schsebastian.foodrats.feature.mealai"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        // JVM_17: both new feature modules target JVM 17 (spec §3, "JVM target" note).
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
        }
        commonTest.dependencies {
            implementation(libs.bundles.feature.test)
        }
        val androidHostTest by getting {
            dependencies {
                implementation(libs.bundles.feature.hosttest)
            }
        }
        androidMain.dependencies {
            // JVM 17 required because Koin transitively links shared modules that touch Firebase.
            // If direct Firebase usage is added in this module, add the BOM here too:
            // implementation(project.dependencies.platform("com.google.firebase:firebase-bom:33.5.1"))
            implementation(libs.koin.android)
            // On-device classifier runtime. Version hoisted to the catalog
            // (libs.versions.mediapipeTasksVision); keep it in sync with the iOS
            // MediaPipeTasksVision cocoapod pin in iosApp/Podfile (plan T16).
            implementation(libs.mediapipe.tasks.vision)
        }
        iosMain.dependencies { }
    }
}
