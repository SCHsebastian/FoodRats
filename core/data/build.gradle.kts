import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinxSerialization)
    // Compose is needed by the share-card renderer (StoryCardRenderer): it captures an
    // @Composable Fr*ShareCard off-screen to a PNG. The card composables live in
    // :core:designsystem; this module composes + rasterizes them.
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
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
        // Runs commonTest on the JVM (Robolectric) — the iOS test target can't link the native
        // Firebase frameworks. Covers the vendor-free commonMain logic (AppPreferences, the
        // consent-gated analytics decorator) that needs no real Firebase at runtime.
        withHostTest {
            isIncludeAndroidResources = true
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.domain)
            // Outbox is now backed by the SQLDelight outbox table (P3b-T6). This brings the
            // generated FoodRatsDatabase / outboxQueries + the coroutines flow extensions.
            implementation(projects.core.database)
            implementation(libs.sqldelight.coroutines.ext)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.androidx.datastore.preferences)
            implementation(libs.bundles.firebase.gitlive)
            // Callable Cloud Functions binding — backs FirebaseImageUrlResolver (mintPlateUrls).
            implementation(libs.firebase.functions)
            implementation(libs.koin.core)
            implementation(libs.okio)
            implementation(libs.coil.core)
            implementation(libs.coil.network.ktor3)
            // Share-card renderer: composes the Fr*ShareCard templates (:core:designsystem)
            // off-screen and rasterizes them to PNG. Compose UI + foundation + the theme.
            implementation(projects.core.designsystem)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            // Coil's non-Composable decode API (ImageRequest + ImageLoader.execute) used to
            // pre-decode the plate's signed URL into an ImageBitmap before off-screen capture.
            implementation(libs.coil.compose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.turbine)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.koin.test)
        }
        val androidHostTest by getting {
            dependencies {
                implementation(libs.kotlin.testJunit)
                implementation(libs.junit)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.androidx.activity.compose)
                // JVM in-memory SQLDelight driver for OutboxLocalStoreTest (P3b-T6) — the outbox
                // store now wraps FoodRatsDatabase; the host test seeds a real in-memory table.
                implementation(libs.sqldelight.jvm.driver)
            }
        }
        androidMain.dependencies {
            // PreferenceDataStoreFactory (JVM/Android) is provided by datastore-preferences artifact
            // Firebase BOM — pins versions for com.google.firebase:* pulled transitively by dev.gitlive.
            implementation(project.dependencies.platform(libs.firebase.bom))
            // Crashlytics has no GitLive KMP binding — AndroidCrashReporter (androidMain) wraps the
            // native SDK directly. Version pinned by the BOM above.
            implementation(libs.firebase.crashlytics)
            // Analytics (GA4) — no GitLive binding; FirebaseAnalyticsTracker (androidMain) wraps the
            // native SDK directly. Version pinned by the BOM above.
            implementation(libs.firebase.analytics)
            // Remote Config backs the FeatureFlagPort kill-switch (RemoteConfigFeatureFlags,
            // androidMain). No GitLive KMP binding; native SDK pinned by the BOM above.
            implementation(libs.firebase.config)
            // Ktor engine for Coil's KtorNetworkFetcherFactory on Android.
            implementation(libs.ktor.client.okhttp)
            // ActivityResultLauncher + ActivityResultContracts for LocationPermissionLauncherHolder.
            implementation(libs.androidx.activity.compose)
            // FileProvider for the share-card PNG content:// URI (StoryShareLauncher).
            implementation(libs.androidx.core.ktx)
        }
        iosMain.dependencies {
            // PreferenceDataStoreFactory.createWithPath is provided by datastore-preferences (KMP)
            // Ktor engine for Coil's KtorNetworkFetcherFactory on iOS.
            implementation(libs.ktor.client.darwin)
        }
    }
}
