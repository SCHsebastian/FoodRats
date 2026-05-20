import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.googleServices)
    alias(libs.plugins.firebaseCrashlytics)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(projects.shared)
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(projects.core.domain)
    implementation(projects.core.i18n)
    implementation(projects.core.presentation)
    implementation(projects.feature.auth)
    implementation(projects.feature.crew)
    implementation(projects.feature.feed)
    implementation(projects.feature.meal)
    implementation(projects.feature.notifications)
    implementation(projects.feature.stats)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation(libs.kotlinx.datetime)
}

android {
    namespace = "es.schsebastian.foodrats"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "es.schsebastian.foodrats"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", "\"${project.findProperty("googleServerClientId") ?: ""}\"")
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// Local dev: dump debug-keystore SHA fingerprints to a file during build so we
// can paste them into Firebase Console. Safe to remove after registering.
val printDebugSha = tasks.register<Exec>("printDebugSha") {
    val outFile = layout.buildDirectory.file("debug-sha.txt").get().asFile
    outputs.file(outFile)
    commandLine(
        "keytool", "-list", "-v",
        "-keystore", "${System.getProperty("user.home")}/.android/debug.keystore",
        "-alias", "androiddebugkey",
        "-storepass", "android",
        "-keypass", "android",
    )
    doFirst {
        outFile.parentFile.mkdirs()
        standardOutput = outFile.outputStream()
    }
}

afterEvaluate {
    tasks.findByName("preBuild")?.dependsOn(printDebugSha)
}
