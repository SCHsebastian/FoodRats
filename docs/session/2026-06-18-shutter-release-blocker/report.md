# Shutter-sound release blocker — report

**Status: DONE — release build green; OUR muted no-op is now the deterministically-kept copy.**

## What the blocker was
`io.github.ismoy.imagepickerkmp.presentation.ui.utils.PlayShutterSoundKt` exists twice:
- the imagepickerkmp 1.0.41 library's copy (plays the shutter via `MediaActionSound`),
- our deliberate same-FQN no-op in `:core:presentation` (mutes it).

R8/D8 global dex-merge on release fails with "Type … PlayShutterSoundKt is defined
multiple times". The `StripImagePickerShutterClass` AGP CLASSES transform
(added 2026-06-16) already made the build pass — so the *build failure* itself was
not actually open. The 16 KB report that listed it as a live blocker predates the
transform.

## The real defect found
The transform kept the **first-seen** copy and assumed "project class dirs are fed
before external jars". The build logs prove that assumption false — **both** copies
arrive as jars:

```
keeping muted no-op playShutterSound from jar classes.jar!…/PlayShutterSoundKt.class
dropped library shutter copy from jar library-release-runtime.jar!…/PlayShutterSoundKt.class
```

So which copy survived was decided by jar iteration order. If that order flipped (or
the library reordered), the **library's sound-playing copy could win silently** with
no build error — i.e. it did not reliably honor "use OUR class".

## Fix
`androidApp/build.gradle.kts` — `StripImagePickerShutterClass.strip()` now selects by
**content**, order-independent:
- byte-scan the colliding class; the library's copy contains
  `android/media/MediaActionSound` (1590 B), ours does not (455 B);
- drop every copy with the marker, keep the one without it, dedup extras;
- `check(keptOurs)` fails the build if our no-op is missing (shutter would play);
- `check(droppedLibraryCopies.isNotEmpty())` fails if the library copy vanished
  (library changed → mute strategy must be revisited);
- `logger.lifecycle` reports which source was kept / dropped.

No change to product code; the shadow class in `:core:presentation` is untouched.

## Verification (quoted)
`./gradlew :androidApp:assembleRelease -PcrashlyticsMappingUpload=false --rerun-tasks`
```
> Task :androidApp:stripReleaseImagePickerShutter
StripImagePickerShutter: keeping muted no-op playShutterSound from jar classes.jar!io/github/ismoy/imagepickerkmp/presentation/ui/utils/PlayShutterSoundKt.class
StripImagePickerShutter: dropped library shutter copy from jar library-release-runtime.jar!io/github/ismoy/imagepickerkmp/presentation/ui/utils/PlayShutterSoundKt.class
BUILD SUCCESSFUL in 1m 53s
```

## Status / follow-ups
- Change is **uncommitted** on `develop` (`androidApp/build.gradle.kts`, `CLAUDE.md`,
  this session dir). Not committed — no commit was requested.
- Unsigned release AAB/APK (no local Firebase creds) — expected; alignment/dex path is
  what matters here and is green.
