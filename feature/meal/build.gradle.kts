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
        namespace = "es.schsebastian.foodrats.feature.meal"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        // JVM_17: firebase-firestore and firebase-storage (BOM 33.5.1) ship inline
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
            implementation(projects.core.database)
            implementation(projects.core.designsystem)
            implementation(projects.core.presentation)
            implementation(projects.core.i18n)
            implementation(libs.bundles.feature.ui)
            implementation(libs.bundles.firebase.gitlive)
            implementation(libs.bundles.kotlinx.common)
            implementation(libs.okio)
            implementation(libs.imagepickerkmp)
            // SQLDelight runtime types (FoodRatsDatabase/MealQueries) + the asFlow/mapToList
            // reactive extensions the MealLocalStore reads return. :core:database exposes these
            // `implementation` (not api), so feature/meal declares them directly.
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines.ext)
        }
        commonTest.dependencies {
            implementation(libs.bundles.feature.test)
            // In-memory DataStore harness for MealDraftLocalStore round-trip tests
            // (AppPreferences wraps a real DataStore<Preferences>).
            implementation(libs.androidx.datastore.preferences)
        }
        val androidHostTest by getting {
            dependencies {
                implementation(libs.bundles.feature.hosttest)
                // In-memory JVM SQLDelight driver for MealLocalStore + repository read-path host tests.
                implementation(libs.sqldelight.jvm.driver)
                // In-memory DataStore harness for FirebaseMealRepository's MealDraftLocalStore dep
                // (the repo read-path tests build a real repository, which needs a DataStore-backed
                // draft store). The repository test lives here (not commonTest) because it seeds a
                // SQLDelight-backed MealLocalStore via the JVM-only JdbcSqliteDriver.
                implementation(libs.androidx.datastore.preferences)
            }
        }
        // StorageData.android.kt / StorageData.ios.kt expect/actual for Firebase Storage Data type
        androidMain.dependencies {
            // Firebase BOM — pins versions for com.google.firebase:* pulled transitively by dev.gitlive.
            implementation(project.dependencies.platform(libs.firebase.bom))
            // WorkManager runs the MealUploadWorker that resumes uploads after process death.
            implementation(libs.androidx.work.runtime)
            // Koin Android for KoinComponent in the worker (resolves the coordinator at runtime).
            implementation(libs.koin.android)
        }
        iosMain.dependencies { }
    }
}
