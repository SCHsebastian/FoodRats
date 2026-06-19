import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.sqldelight)
}

// Firebase-FREE, JVM 11 (like :core:domain, NOT :core:data) so its iosSimulatorArm64Test links
// and runs — real cross-platform DB coverage. Holds all generated SQLDelight types; domain never
// imports SQLDelight (Konsist-enforced).
kotlin {
    iosArm64()
    iosSimulatorArm64()

    androidLibrary {
        namespace = "es.schsebastian.foodrats.core.database"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget = JvmTarget.JVM_11 }
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines.ext)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
        val androidHostTest by getting {
            dependencies {
                implementation(libs.kotlin.testJunit)
                implementation(libs.junit)
                implementation(libs.sqldelight.jvm.driver)
            }
        }
    }
}

sqldelight {
    databases {
        create("FoodRatsDatabase") {
            packageName.set("es.schsebastian.foodrats.core.database")
            generateAsync.set(false)
        }
    }
}
