// Supply-chain security (buildscript/plugin classpath): AGP 9.0.1 pins vulnerable
// transitives on the ROOT buildscript classpath (bundletool → jose4j/bouncycastle,
// jetifier → jdom2, commons-compress → commons-lang3). The `subprojects {}` force
// block below only covers *project* configurations, so the plugin classpath needs
// its own constraints here. Plugins-DSL artifacts resolve into this same
// `classpath` configuration, so these constraints apply to them. Verified with
// `./gradlew buildEnvironment`.
buildscript {
    dependencies {
        constraints {
            add("classpath", "org.bouncycastle:bcprov-jdk18on:1.84") {
                because("GHSA critical GOST keystream reuse (<=1.80.1) + medium LDAP injection (<1.84); AGP 9.0.1 pins 1.79")
            }
            add("classpath", "org.bouncycastle:bcpkix-jdk18on:1.84") {
                because("GHSA medium (<1.84); keep in lockstep with bcprov")
            }
            add("classpath", "org.bouncycastle:bcutil-jdk18on:1.84") {
                because("keep the BouncyCastle family on one version")
            }
            add("classpath", "org.bitbucket.b_c:jose4j:0.9.6") {
                because("GHSA high (<0.9.6); AGP 9.0.1 bundletool chain pins 0.9.5")
            }
            add("classpath", "org.jdom:jdom2:2.0.6.1") {
                because("XXE, GHSA high (<2.0.6.1); AGP 9.0.1 jetifier chain pins 2.0.6")
            }
            add("classpath", "org.apache.commons:commons-lang3:3.18.0") {
                because("GHSA medium (<3.18.0); AGP 9.0.1 commons-compress chain pins 3.16.0")
            }
        }
    }
}

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.googleServices) apply false
    alias(libs.plugins.firebaseCrashlytics) apply false
    alias(libs.plugins.kotlinxSerialization) apply false
    alias(libs.plugins.androidTest) apply false
    alias(libs.plugins.androidxBaselineprofile) apply false
}

// Software-supply-chain security: force patched versions of transitive Maven
// dependencies that Dependabot flags via settings.gradle.kts. These arrive
// transitively through Firebase BOM, gRPC, Google Play services, and the
// Android Gradle Plugin's Unified Test Platform (UTP) tooling. We force the
// patched transitive versions here instead of bumping firebase-bom — the
// Firebase modules are deliberately pinned at JVM 17 and a BOM major bump is
// risky (see CLAUDE.md). Only coordinates actually present on a resolvable
// classpath are forced. Versions verified published on Maven Central
// 2026-06-21 (guava/basement re-verified 2026-07-20 — see below).
subprojects {
    // Compose-compiler stability config + reports, applied build-wide WITHOUT any source
    // change (keeps the no-Compose-in-:core:domain rule intact). Configures every subproject
    // that applies the Compose compiler plugin; the compiler reads the conf during normal
    // compilation, so the gate is just `assembleDebug`. The plugin class is on the root
    // buildscript classpath via `alias(libs.plugins.composeCompiler) apply false` above.
    plugins.withId("org.jetbrains.kotlin.plugin.compose") {
        extensions.configure<org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension> {
            stabilityConfigurationFiles.add(
                rootProject.layout.projectDirectory.file("compose_stability.conf"),
            )
            reportsDestination.set(layout.buildDirectory.dir("compose_compiler"))
            metricsDestination.set(layout.buildDirectory.dir("compose_compiler"))
        }
    }

    configurations.configureEach {
        resolutionStrategy {
            // Netty — keep ONE consistent version across the whole family so
            // no sibling is left on an older line. (grpc-netty / UTP tooling.)
            force(
                "io.netty:netty-codec-http:4.1.135.Final",
                "io.netty:netty-codec-http2:4.1.135.Final",
                "io.netty:netty-handler:4.1.135.Final",
                "io.netty:netty-handler-proxy:4.1.135.Final",
                "io.netty:netty-codec:4.1.135.Final",
                "io.netty:netty-codec-socks:4.1.135.Final",
                "io.netty:netty-common:4.1.135.Final",
                "io.netty:netty-buffer:4.1.135.Final",
                "io.netty:netty-transport:4.1.135.Final",
                "io.netty:netty-transport-native-unix-common:4.1.135.Final",
                "io.netty:netty-resolver:4.1.135.Final",
            )
            // Protobuf — the full protobuf-java / -kotlin / -java-util are UTP
            // test tooling; force the 3.x line to 3.25.5.
            //
            // protobuf-javalite is intentionally NOT forced: it ships in the
            // app runtime via Firebase, and forcing the 4.x line (4.27.5)
            // breaks dexing — com.google.firebase:protolite-well-known-types
            // (pinned by firebase-bom 33.5.1) bundles 3.x DescriptorProtos
            // classes that collide with javalite 4.x ("Duplicate class
            // com.google.protobuf.DescriptorProtos$..."). It resolves to
            // 4.26.1 today; a safe bump requires a firebase-bom upgrade that
            // ships a 4.x-compatible protolite-well-known-types. See residual.
            force(
                "com.google.protobuf:protobuf-java:3.25.5",
                "com.google.protobuf:protobuf-kotlin:3.25.5",
                "com.google.protobuf:protobuf-java-util:3.25.5",
            )
            // BouncyCastle (UTP tooling).
            force(
                "org.bouncycastle:bcprov-jdk18on:1.84",
                "org.bouncycastle:bcpkix-jdk18on:1.84",
                "org.bouncycastle:bcutil-jdk18on:1.84",
            )
            // Apache Commons / HttpClient (UTP tooling).
            force(
                "org.apache.commons:commons-lang3:3.18.0",
                "org.apache.httpcomponents:httpclient:4.5.13",
            )
            // wire-runtime — GHSA high (<=6.2.0). Pulled only by
            // androidx.benchmark:benchmark-macro 1.5.0-alpha06 in
            // :baselineprofile (on-device macrobenchmark APK; never ships in
            // the app). 6.3.0 is the patched line.
            force(
                "com.squareup.wire:wire-runtime:6.3.0",
            )
            // opentelemetry-api — GHSA medium (<=1.61.0). Appears only on
            // `swiftExportClasspathResolvable` (Kotlin Gradle Plugin's
            // swift-export-embeddable 2.3.21 worker classpath; build-time
            // only, never ships). 1.62.0 pulls opentelemetry-context 1.62.0
            // transitively.
            force(
                "io.opentelemetry:opentelemetry-api:1.62.0",
            )
            // guava — GHSA moderate + low (<32.0.0-android), Dependabot #7/#8.
            // A previous pass believed guava was already safe everywhere
            // (33.x resolves on most configs via Firebase/gRPC/UTP
            // transitives that request it explicitly), but two classpaths
            // have NOTHING else requesting a newer version, so Gradle's
            // "highest wins" keeps the vendored floor: `:feature:meal-ai`'s
            // `androidCompileClasspath` / `androidHostTestCompileClasspath` /
            // lint-checks classpaths vendor MediaPipe's guava:27.0.1-android
            // directly; `:core:data`'s same four classpaths vendor
            // guava:31.1-android via
            // play-services-measurement-{api,impl} (Firebase Analytics);
            // `:baselineprofile`'s on-device benchmark-apk configs
            // (`*TestedApks`) resolve the same 31.1-android through the
            // tested `:androidApp` variant. Force the patched `-android`
            // line so it wins conflict resolution everywhere; already-safe
            // configs already resolve at/above this so there's no downgrade
            // risk.
            force(
                "com.google.guava:guava:33.4.8-android",
            )
            // play-services-basement — GHSA moderate (<18.0.2), Dependabot
            // #3. Same gap: `:feature:achievements` / `:feature:ingredient` /
            // `:feature:notifications`'s `androidCompileClasspath` /
            // `androidHostTestCompileClasspath` vendor
            // play-services-basement:18.0.0 via play-services-base:18.0.1
            // (pulled by firebase-appcheck-interop -> firebase-firestore),
            // with nothing else on those classpaths requesting newer.
            force(
                "com.google.android.gms:play-services-basement:18.4.0",
            )
        }
    }
}
