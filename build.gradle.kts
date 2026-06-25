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
// classpath are forced; already-safe coordinates (guava 33.x,
// play-services-basement 18.4.0) are intentionally NOT forced so we never
// downgrade them. Versions verified published on Maven Central 2026-06-21.
subprojects {
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
        }
    }
}
