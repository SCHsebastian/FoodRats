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

# ---- Kotlin metadata -------------------------------------------------------
-keep class kotlin.Metadata { *; }

# ---- Crashlytics: keep line numbers for readable stack traces --------------
-keepattributes SourceFile, LineNumberTable
# (dSYM-equivalent for Android: do NOT strip these, Crashlytics needs them.)
