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
        namespace = "es.schsebastian.foodrats.feature.feed"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget = JvmTarget.JVM_11 }
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
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            // Image loading for meal photos (FrFeedMealCard).
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
            // Coil 3 with the Ktor 3 fetcher needs a Ktor engine on each platform; we share
            // the project's Ktor client artifacts so we don't bring in a second HTTP stack.
            implementation(libs.ktor.client.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotest.assertions.core)
            implementation(libs.turbine)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.koin.test)
        }
        val androidHostTest by getting {
            dependencies {
                implementation(libs.kotlin.testJunit)
                implementation(libs.junit)
                implementation(libs.androidx.testExt.junit)
                implementation(libs.compose.ui.test.junit4)
                implementation(libs.robolectric)
            }
        }
        androidMain.dependencies {
            // Ktor engine used by Coil's KtorNetworkFetcherFactory on Android.
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            // Ktor engine used by Coil's KtorNetworkFetcherFactory on iOS.
            implementation(libs.ktor.client.darwin)
        }
    }
}
