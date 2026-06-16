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
        namespace = "es.schsebastian.foodrats.feature.ingredient"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        // JVM_17: firebase-firestore (BOM 33.5.1) ships inline functions compiled at
        // JVM 17; inlining into JVM 11 target is rejected by kotlinc.
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
            // Only 2 of the 4 GitLive Firebase deps are needed here — keep explicit, don't use the bundle.
            implementation(libs.firebase.common)
            implementation(libs.firebase.firestore)
        }
        commonTest.dependencies {
            implementation(libs.bundles.feature.test)
        }
        val androidHostTest by getting {
            dependencies {
                implementation(libs.bundles.compose.hosttest)
                // Not in compose.hosttest — needed for coroutine-test + Turbine flows here.
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.turbine)
            }
        }
        androidMain.dependencies {
            // Firebase BOM — pins versions for com.google.firebase:* pulled transitively
            // by dev.gitlive wrappers (firebase-firestore, firebase-common).
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.koin.android)
        }
        iosMain.dependencies { }
    }
}
