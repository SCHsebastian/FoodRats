import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinxSerialization)
}

kotlin {
    iosArm64()
    iosSimulatorArm64()
    androidLibrary {
        namespace = "es.schsebastian.foodrats.core.data"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        // JVM_17: firebase-* (BOM 33.5.1) ship inline functions compiled at JVM 17;
        // inlining into JVM 11 target is rejected by kotlinc.
        compilerOptions { jvmTarget = JvmTarget.JVM_17 }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.domain)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.androidx.datastore.preferences)
            implementation(libs.bundles.firebase.gitlive)
            // Callable Cloud Functions binding — backs FirebaseImageUrlResolver (mintPlateUrls).
            implementation(libs.firebase.functions)
            implementation(libs.koin.core)
            implementation(libs.okio)
            implementation(libs.coil.core)
            implementation(libs.coil.network.ktor3)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.turbine)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.koin.test)
        }
        androidMain.dependencies {
            // PreferenceDataStoreFactory (JVM/Android) is provided by datastore-preferences artifact
            // Firebase BOM — pins versions for com.google.firebase:* pulled transitively by dev.gitlive.
            implementation(project.dependencies.platform(libs.firebase.bom))
            // Crashlytics has no GitLive KMP binding — AndroidCrashReporter (androidMain) wraps the
            // native SDK directly. Version pinned by the BOM above.
            implementation(libs.firebase.crashlytics)
            // Remote Config backs the FeatureFlagPort kill-switch (RemoteConfigFeatureFlags,
            // androidMain). No GitLive KMP binding; native SDK pinned by the BOM above.
            implementation(libs.firebase.config)
            // Ktor engine for Coil's KtorNetworkFetcherFactory on Android.
            implementation(libs.ktor.client.okhttp)
            // ActivityResultLauncher + ActivityResultContracts for LocationPermissionLauncherHolder.
            implementation(libs.androidx.activity.compose)
        }
        iosMain.dependencies {
            // PreferenceDataStoreFactory.createWithPath is provided by datastore-preferences (KMP)
            // Ktor engine for Coil's KtorNetworkFetcherFactory on iOS.
            implementation(libs.ktor.client.darwin)
        }
    }
}
