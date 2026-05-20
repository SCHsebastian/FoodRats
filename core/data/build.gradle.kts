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
            implementation(libs.firebase.common)
            implementation(libs.firebase.auth)
            implementation(libs.firebase.firestore)
            implementation(libs.firebase.storage)
            implementation(libs.koin.core)
            implementation(libs.okio)
            implementation(libs.coil.core)
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
            implementation(project.dependencies.platform("com.google.firebase:firebase-bom:33.5.1"))
            // Crashlytics has no GitLive KMP binding — AndroidCrashReporter (androidMain) wraps the
            // native SDK directly. Version pinned by the BOM above.
            implementation("com.google.firebase:firebase-crashlytics")
        }
        iosMain.dependencies {
            // PreferenceDataStoreFactory.createWithPath is provided by datastore-preferences (KMP)
        }
    }
}
