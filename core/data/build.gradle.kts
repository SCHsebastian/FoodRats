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
        compilerOptions { jvmTarget = JvmTarget.JVM_11 }
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
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.turbine)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.koin.test)
        }
        androidMain.dependencies {
            // PreferenceDataStoreFactory (JVM/Android) is provided by datastore-preferences artifact
        }
        iosMain.dependencies {
            // PreferenceDataStoreFactory.createWithPath is provided by datastore-preferences (KMP)
        }
    }
}
