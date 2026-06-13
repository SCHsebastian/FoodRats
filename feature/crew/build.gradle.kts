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
        namespace = "es.schsebastian.foodrats.feature.crew"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        // JVM_17: firebase-firestore (BOM 33.5.1) ships inline functions compiled at JVM 17;
        // inlining into JVM 11 target is rejected by kotlinc.
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
            implementation(libs.bundles.firebase.gitlive)
            implementation(libs.bundles.kotlinx.common)
            implementation(libs.androidx.datastore.preferences)
            // Avatar picker (gallery) — same KMP picker the meal feature uses.
            // Mirror feature:meal: if iOS link breaks on material-icons-extended, add the same
            // exclude noted in CLAUDE.md.
            implementation(libs.imagepickerkmp)
            // AsyncImage for rendering avatar URLs.
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
            implementation(libs.ktor.client.core)
        }
        commonTest.dependencies {
            implementation(libs.bundles.feature.test)
        }
        androidMain.dependencies {
            // Firebase BOM — pins versions for com.google.firebase:* pulled transitively by dev.gitlive.
            implementation(project.dependencies.platform(libs.firebase.bom))
            // Ktor engine used by Coil's KtorNetworkFetcherFactory on Android.
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            // Ktor engine used by Coil's KtorNetworkFetcherFactory on iOS.
            implementation(libs.ktor.client.darwin)
        }
        val androidHostTest by getting {
            dependencies {
                implementation(libs.bundles.feature.hosttest)
            }
        }
    }
}
