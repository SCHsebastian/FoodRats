# w0-16kb-alignment — report

**Task:** Make the Android release artifact 16-KB-page-size compatible. Known defect:
`lib/arm64-v8a/libmediapipe_tasks_vision_jni.so` had LOAD segments not aligned at 16 KB
boundaries, blocking Google Play submission for Android 15+ (mandatory since 2025-11-01).

**Status: DONE — verified on the packaged release APK.**

## Prior (interrupted) work found on disk

- Commit `6d61d93 fix(meal-ai): bump MediaPipe tasks-vision 0.10.14 → 0.10.35 for 16-KB page
  alignment` had already bumped `mediapipeTasksVision = "0.10.35"` in `gradle/libs.versions.toml`
  (with an explanatory comment). The **version bump was already in place**; it was NOT yet verified,
  and the AGP packaging guard + the actual alignment verification were outstanding.

## What changed

| File | Change |
|---|---|
| `gradle/libs.versions.toml` | Version already at `0.10.35` (prior commit). Updated the two comments to note the `.so` was renamed/moved (see below) and to record the verified `align 2**14` evidence. **No version-number edit needed.** |
| `androidApp/build.gradle.kts` | Added `packaging { jniLibs { useLegacyPackaging = false } }` — keeps native libs uncompressed + page-aligned. AGP is 9.0.1 (≥ 8.5.1), so the default is already `false`; made it explicit per roadmap §0.3 to guard the alignment intent. **`extractNativeLibs` NOT set.** |
| `CLAUDE.md` | "Active tech debt" 16-KB note flipped to RESOLVED + documented the lib rename; added a note for the pre-existing release-build blocker found below. |

## Old version → new version

- **0.10.14** (old pin): `lib/arm64-v8a/libmediapipe_tasks_vision_jni.so`, LOAD segments `align 2**12`
  (4 KB / 0x1000) — the exact reported defect.
- **0.10.35** (current pin, latest available — confirmed against the Google Maven `group-index.xml`):
  16-KB-aligned. **Two upstream changes in 0.10.35 worth recording:**
  1. The native lib was **renamed** `libmediapipe_tasks_vision_jni.so` → **`libmediapipe_tasks_jni.so`**.
  2. It **moved out of the `tasks-vision` AAR** (now only 227 KB, classes-only) **into the transitive
     `tasks-core` AAR**. So the original filename in the CLAUDE.md tech-debt note no longer exists in
     the artifact at all.

## Alignment evidence

### Upstream artifact (0.10.35 `tasks-core` AAR, all ABIs)
```
arm64-v8a    -> align 2**14
armeabi-v7a  -> align 2**14
x86          -> align 2**14
x86_64       -> align 2**14
```
(0.10.14 for contrast: `align 2**12`.)

### `:androidApp:assembleRelease`
```
> Task :androidApp:packageRelease
> Task :androidApp:assembleRelease
BUILD SUCCESSFUL in 1m 32s
```
Built with `-PcrashlyticsMappingUpload=false` (CI flag; no Firebase creds locally → unsigned AAB/APK,
fine for the alignment check). Output: `androidApp/build/outputs/apk/release/androidApp-release-unsigned.apk`.

> NOTE: this `assembleRelease` succeeded only after temporarily moving aside a PRE-EXISTING,
> unrelated release blocker (see below). The build files committed for this task are unchanged by that
> workaround — the file was restored immediately after the build (git status clean).

### `zipalign -c -P 16 -v 4` on the packaged release APK
(`$ANDROID_SDK/build-tools/37.0.0/zipalign`)
```
 9355264 lib/arm64-v8a/libmediapipe_tasks_jni.so (OK)
19972096 lib/armeabi-v7a/libmediapipe_tasks_jni.so (OK)
27475968 lib/x86/libmediapipe_tasks_jni.so (OK)
42565632 lib/x86_64/libmediapipe_tasks_jni.so (OK)
Verification successful
```

### `objdump -p` on the `.so` extracted from the packaged APK (arm64-v8a)
```
LOAD off 0x0...0000 vaddr 0x0...0000 ... align 2**14
LOAD off 0x0...990000 vaddr 0x0...990000 ... align 2**14
LOAD off 0x0...9f6ba0 vaddr 0x0...9faba0 ... align 2**14
```
All LOAD segments are `align 2**14` = 0x4000 = 16384 = 16 KB. (Non-LOAD segments show `2**0/2**2/
2**3/2**64`; those do not affect page alignment.)

## Decisions

- Did **not** repackage/rebuild the `.so` from source — unnecessary, the latest published vendor
  release (0.10.35) already ships 16-KB-aligned libs across all four ABIs.
- Made `useLegacyPackaging = false` explicit even though AGP 9.0.1 defaults it that way — cheap guard
  that documents the page-alignment intent and survives any future default change.

## Residual blocker (PRE-EXISTING, separate concern — NOT introduced by this task)

`:androidApp:assembleRelease` fails on its own due to a duplicate-class collision unrelated to 16-KB:

```
Type io.github.ismoy.imagepickerkmp.presentation.ui.utils.PlayShutterSoundKt is defined multiple times
```

Root cause: `core/presentation/src/androidMain/kotlin/io/github/ismoy/imagepickerkmp/presentation/ui/
utils/playShutterSound.kt` is a **shadow class** placed in the `imagepickerkmp` package (an empty
`playShutterSound()` to mute the camera shutter sound). It collides with the real library class at the
D8/R8 **dex-merge** step — this fails the release build with minify ON *or* OFF (debug is unaffected
because debug dexes per-module without the global merge collision check).

To complete the alignment verification I temporarily moved this file aside, ran the full minified
`assembleRelease`, ran `zipalign`/`objdump`, then **restored the file** (git status clean — unchanged).

Fix direction (NOT done here — it changes product behavior, out of this task's scope): replace the
shadow trick with a supported mute path, or relocate it so it doesn't share the library's FQN. Tracked
in CLAUDE.md "Active tech debt". This blocker must be resolved before the release pipeline (CI
`release-smoke` / `release-beta`) can build a release artifact unaided.
