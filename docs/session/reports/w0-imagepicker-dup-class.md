# w0-imagepicker-dup-class — report

**Task:** Fix the duplicate-class collision that fails `:androidApp:assembleRelease`
(R8/D8 dex merge), surfaced by the 16-KB task. Release-pipeline bugfix, no spec.

**Status: DONE — `assembleRelease` reaches BUILD SUCCESSFUL with no duplicate-class
error and no temporary workaround; `assembleDebug` still green.**

## Root cause

The release build failed at `:androidApp:minifyReleaseWithR8` with:

```
R8: Type io.github.ismoy.imagepickerkmp.presentation.ui.utils.PlayShutterSoundKt
is defined multiple times:
  core/presentation/.../bundleAndroidMainClassesToRuntimeJar/classes.jar:.../PlayShutterSoundKt.class,
  ~/.gradle/.../transformed/library-release-runtime.jar:.../PlayShutterSoundKt.class
```

The same fully-qualified class `io.github.ismoy.imagepickerkmp.presentation.ui.utils.PlayShutterSoundKt`
ships in **two** places:

1. **The library** `io.github.ismoy:imagepickerkmp:1.0.41` — a top-level
   `internal fun playShutterSound()` that calls `MediaActionSound.play(SHUTTER_CLICK)`.
   Its own `CameraCapturePreview` invokes it after a capture
   (`INVOKESTATIC .../PlayShutterSoundKt.playShutterSound()`).
2. **Our project** — a "shadow" file at
   `core/presentation/src/androidMain/kotlin/io/github/ismoy/imagepickerkmp/presentation/ui/utils/playShutterSound.kt`,
   an **empty** `internal fun playShutterSound()` placed in the library's exact
   package so that, on the JVM/Android classpath, OUR class wins (project classes
   precede external deps) and the library's shutter call binds to a no-op.

Debug builds dex per-module and never run the global dex merge, so the collision
only manifests in release (minify ON or OFF — R8 still merges the full classpath).

## Why the shadow file existed (intent to preserve)

It deliberately **mutes the Android camera shutter sound** in the meal
image-capture flow. Verified the intent against the library sources:

- The library hardcodes the `MediaActionSound.play(SHUTTER_CLICK)` call inside
  `CameraCapturePreview` and exposes **no** config (`ImagePickerConfig`,
  `CompressionConfig`, etc.) to disable it — grepped the 1.0.41 sources: zero
  `sound`/`silent`/`mute` toggles. The same-package class-shadow was the only
  mechanism available to silence it.
- The mute is **Android-only** — there is no `iosMain` shadow, and iOS enforces
  the system shutter sound at the OS level anyway.

So deleting the shadow file would re-enable the shutter sound: a UX regression.
That option was rejected (matches the task constraint).

## The fix

Drop the **library's** copy of the colliding class from the merged class set just
before dexing, leaving our no-op as the sole `PlayShutterSoundKt` — using AGP's
official **scoped-artifacts `CLASSES` transform** (`androidComponents.onVariants`,
`forScope(ScopedArtifacts.Scope.ALL).use(task).toTransform(ScopedArtifact.CLASSES, …)`,
stable since AGP 7.4), registered in `androidApp/build.gradle.kts` for the
`release` variant only.

`StripImagePickerShutterClass` (a `@CacheableTask` `DefaultTask`) receives the full
release class graph as `inputJars` + `inputDirs`, writes one merged output jar, and:
- copies **project/module class directories first** (so our override is the first
  occurrence of `…/PlayShutterSoundKt.class` and is kept), then
- copies external jars, **skipping** any later duplicate of that single class.

Net result fed to R8: exactly one `PlayShutterSoundKt` — the empty no-op.

### Why not the obvious alternatives

- `dependencies { exclude(...) }` excludes whole **modules**, not a single class.
- `packagingOptions { … pickFirst }` is for **resources / jniLibs**, not classes —
  D8 duplicate **classes** are explicitly not solved by it.
- An `artifactType`-based artifact transform on `releaseRuntimeClasspath` was tried
  and **did not work** on AGP 9.0.1 — R8 reads its classpath through AGP-internal
  plumbing, so the requested-attribute variant never reached the dexer. The
  scoped-artifacts API operates on exactly the class set R8 dexes, so it is robust.

Bundle-id/signing/version logic is untouched; the transform is release-only, so
debug and the on-device capture flow are unaffected on debug.

## Behavior preserved (verified)

The single surviving class in the merged release classes jar is OUR no-op:

```
$ javap -p -c .../PlayShutterSoundKt.class
public final class io.github.ismoy.imagepickerkmp.presentation.ui.utils.PlayShutterSoundKt {
  public static final void playShutterSound();
    Code:
       0: return            // empty body, NO MediaActionSound reference
}
```

```
$ unzip -l .../stripReleaseImagePickerShutter/classes.jar | grep -c PlayShutterSoundKt.class
1
```

So the library's `CameraCapturePreview.playShutterSound()` call still resolves to
the empty function in the release artifact → the shutter stays muted.

## Verification

### `:androidApp:assembleRelease -PcrashlyticsMappingUpload=false`
```
> Task :androidApp:stripReleaseImagePickerShutter
> Task :androidApp:minifyReleaseWithR8
> Task :androidApp:packageRelease
> Task :androidApp:assembleRelease
BUILD SUCCESSFUL in 1m 9s
```
No duplicate-class error; no file moved aside (git status clean apart from the
build-script change + this report).

### `:androidApp:assembleDebug`
```
> Task :androidApp:packageDebug
> Task :androidApp:assembleDebug
BUILD SUCCESSFUL in 6s
```

## Files changed

- `androidApp/build.gradle.kts` — added the `StripImagePickerShutterClass` task +
  `androidComponents.onVariants(release)` scoped-artifacts CLASSES transform (and
  its imports). No other module touched.

## Follow-up (cleaner, not required to unblock release)

1. **Prefer an upstream/library mute toggle.** The class-shadow + build-time strip
   is two coupled hacks for one missing feature. If a future `imagepickerkmp`
   release adds a "no shutter sound" config, switch to it and **delete both** the
   `core/presentation` shadow file and this transform. (File an upstream issue /
   PR for a `playShutterSound`-disable option.)
2. **Lock the behavior with a test.** There's no test asserting the surviving
   `playShutterSound` is the no-op — a future library bump that renames/moves the
   class would silently break the mute (or re-introduce the collision). Consider a
   small release-artifact check in CI (`release-smoke`) that greps the dex/jar for a
   single, empty `PlayShutterSoundKt`.
3. The transform's "keep first occurrence" relies on project class **directories**
   being fed before external **jars** by `ScopedArtifacts.Scope.ALL`; if AGP ever
   reorders, switch the strip rule to "keep the empty-bodied copy" explicitly.
