# R8 / ProGuard rules for the release build (isMinifyEnabled = true).
#
# Most of the stack is R8-safe out of the box: Compose ships its own rules,
# Koin uses lambdas (no reflection over class names), and the Firebase /
# GitLive AARs bundle consumer rules. The one thing R8 *will* break without
# help is kotlinx-serialization, which relies on generated $$serializer
# classes and Companion references discovered by name at runtime.
#
# IMPORTANT: this file is a starting point. Validate it with the smoke test
# described in docs/specs/2026-05-20-cicd-store-release-pipeline-design.md §4.1
# (install the minified AAB and walk sign-in → crew → publish meal → feed →
# stats → notification). If something crashes in runtime it's almost always a
# missing -keep here.

# ---- kotlinx.serialization -------------------------------------------------
# Keep generated serializers and the Companion accessors the runtime resolves.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class **$$serializer { *; }

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$Companion Companion;
}
-keep class <1>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep the @Serializable types themselves (DTOs persisted to Firestore and the
# @Serializable navigation Route objects). Their field names are part of the
# wire/route contract and must not be renamed.
-keepclasseswithmembers, allowobfuscation class * {
    @kotlinx.serialization.Serializable <fields>;
}

# ---- Room (transitive via WorkManager) -------------------------------------
# WorkManager 2.10 keeps its job state in an internal Room database (WorkDatabase).
# Room instantiates the generated `WorkDatabase_Impl` purely by REFLECTION
# (Room.getGeneratedImplementation → Class.getDeclaredConstructor()). Room 2.6.1's
# bundled consumer rule keeps only the CLASS (`-keep class * extends RoomDatabase`),
# NOT its constructor — so R8 full mode (AGP 8+ default) strips the no-arg `<init>()`
# nothing calls directly. At launch that surfaces as
#   NoSuchMethodException: androidx.work.impl.WorkDatabase_Impl.<init> []
# thrown inside androidx.startup's WorkManagerInitializer, i.e. during ContentProvider
# init BEFORE Application.onCreate — which is why it instant-crashes the release build
# and never reaches Crashlytics. Keep the no-arg constructor of every Room DB impl.
# (Room 2.7+ fixes this in its own consumer rules; remove this once Work pulls 2.7+.)
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# ---- Firebase component discovery ------------------------------------------
# Firebase finds its components (Crashlytics, Auth, Messaging, App Check,
# Installations, …) by REFLECTING on each `ComponentRegistrar`'s no-arg
# constructor at startup. firebase-common's bundled consumer rule keeps only the
# CLASS (`-keep class * implements ComponentRegistrar`), not `<init>()`, so under
# R8 full mode the constructors are stripped and `FirebaseCrashlytics.getInstance()`
# throws "FirebaseCrashlytics component is not present" (and Auth/Messaging would
# fail the same way when first used). We disable full mode in gradle.properties,
# but keep the constructors explicitly too so a future full-mode flip can't silently
# re-break Firebase init.
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }

# ---- MediaPipe Tasks Vision (on-device food classifier) --------------------
# MediaPipe (`com.google.mediapipe:tasks-vision`) ships NO consumer ProGuard
# rules, and its Tasks runtime reaches Java FROM native by name: the C++ graph
# delivers result packets via `PacketCallback.process(Packet)` (JNI GetMethodID)
# and Java extracts the proto/bitmap payload through `PacketGetter` /
# `AndroidPacketGetter`. R8 can't see those edges statically, so it strips them —
# verified: `PacketGetter`, `PacketCallback`, and `AndroidPacketGetter` all landed
# in build/outputs/mapping/release/usage.txt (removed) even though `ImageClassifier`
# stayed reachable. The classifier then crashes the moment it tries to read a
# result in the minified build. (Debug never global-shrinks, so it only bites
# release — and the meal-ai release smoke is still pending, hence this was latent.)
# Keep the whole package: the Java classes are tiny next to the bundled .so/.tflite.
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# ---- Kotlin metadata -------------------------------------------------------
-keep class kotlin.Metadata { *; }

# ---- Crashlytics: keep line numbers for readable stack traces --------------
-keepattributes SourceFile, LineNumberTable
# (dSYM-equivalent for Android: do NOT strip these, Crashlytics needs them.)
