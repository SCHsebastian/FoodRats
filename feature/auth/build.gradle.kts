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
        namespace = "es.schsebastian.foodrats.feature.auth"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        // JVM_17: firebase-auth-ktx and firebase-firestore (BOM 33.5.1) ship inline
        // functions compiled at JVM 17; inlining into JVM 11 target is rejected by kotlinc.
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
            implementation(libs.bundles.firebase.gitlive)
            // GitLive Functions binding — the `deleteAccount` callable backing
            // FirebaseAccountDeletionPort. Not part of the firebase-gitlive bundle
            // (same as :core:data, which pulls it in standalone for FirebaseImageUrlResolver).
            implementation(libs.firebase.functions)
            // Avatar picker (gallery) — mirrors :feature:crew. If iOS link breaks on
            // material-icons-extended, add the same exclude noted in CLAUDE.md.
            implementation(libs.imagepickerkmp)
            implementation(libs.coil.compose)
        }
        commonTest.dependencies {
            implementation(libs.bundles.feature.test)
        }
        androidMain.dependencies {
            // Firebase BOM — pins versions for com.google.firebase:* pulled transitively by dev.gitlive.
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.play.services.auth)
            implementation(libs.google.id)
        }
        val androidHostTest by getting {
            dependencies {
                implementation(libs.bundles.feature.hosttest)
            }
        }
    }
}
