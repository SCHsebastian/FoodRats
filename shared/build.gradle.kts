import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinxSerialization)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "FoodRatsShared"
            isStatic = true
        }
    }
    
    androidLibrary {
       namespace = "es.schsebastian.foodrats.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            // Firebase BOM — pins versions for all com.google.firebase:* artifacts that
            // dev.gitlive KMP wrappers pull in transitively on Android.
            implementation(project.dependencies.platform(libs.firebase.bom))
        }
        commonMain.dependencies {
            // compose(5) + koin(3) + lifecycle(2)
            implementation(libs.bundles.feature.ui)
            implementation(libs.compose.uiToolingPreview)

            // Module graph
            implementation(projects.core.domain)
            implementation(projects.core.data)
            implementation(projects.core.designsystem)
            implementation(projects.core.presentation)
            implementation(projects.core.i18n)
            implementation(projects.feature.auth)
            implementation(projects.feature.crew)
            implementation(projects.feature.meal)
            implementation(projects.feature.mealAi)
            implementation(projects.feature.ingredient)
            implementation(projects.feature.feed)
            implementation(projects.feature.stats)
            implementation(projects.feature.notifications)

            // Navigation + serialization
            implementation(libs.nav.compose)
            implementation(libs.kotlinx.serialization.json)

            // Firebase (GitLive KMP bindings)
            implementation(libs.bundles.firebase.gitlive)

            // Shared utilities transitively needed by Koin modules
            implementation(libs.kotlinx.datetime)
            implementation(libs.androidx.datastore.preferences)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.turbine)
            implementation(libs.kotlinx.coroutines.test)
        }
        val androidHostTest by getting {
            dependencies {
                // Robolectric-backed Compose UI tests for the imperative nav glue
                // (navigateTopLevel + back-stack behaviour) that commonTest can't reach.
                implementation(libs.bundles.compose.hosttest)
                implementation(libs.androidx.activity.compose)
            }
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}
