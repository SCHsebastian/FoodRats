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
        namespace = "es.schsebastian.foodrats.feature.notifications"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        // JVM_17: firebase-messaging (BOM 33.5.1) ships inline functions compiled at JVM 17.
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
            // Only firestore + messaging needed — messaging isn't in firebase.gitlive; keep explicit.
            implementation(libs.firebase.firestore)
            implementation(libs.firebase.messaging)
        }
        commonTest.dependencies {
            implementation(libs.bundles.feature.test)
        }
        androidMain.dependencies {
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.androidx.work.runtime)
            implementation(libs.androidx.lifecycle.process)
            implementation(libs.androidx.activity.compose)   // provides ActivityResultLauncher + ActivityResultContracts
            implementation(libs.koin.android)                // androidContext() in Koin modules
        }
        val androidHostTest by getting {
            dependencies {
                implementation(libs.bundles.feature.hosttest)
            }
        }
    }
}
