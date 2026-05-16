import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    iosArm64()
    iosSimulatorArm64()
    androidLibrary {
        namespace = "es.schsebastian.foodrats.core.i18n"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget = JvmTarget.JVM_11 }
        androidResources { enable = true }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.components.resources)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
