# PlayShutterSoundKt release blocker — make OUR no-op authoritative

## Ask
Fix the `PlayShutterSoundKt` release blocker; the release build must keep the
muted no-op `playShutterSound()` from **our** code (`:core:presentation`), never
the imagepickerkmp library's sound-playing copy.

## Findings
- The duplicate-class **build failure** was already resolved by the
  `StripImagePickerShutterClass` AGP scoped-artifacts CLASSES transform
  (`androidApp/build.gradle.kts`, added 2026-06-16). A fresh
  `:androidApp:assembleRelease -PcrashlyticsMappingUpload=false` succeeds
  (`BUILD SUCCESSFUL in 2m 9s`). The 16 KB report that called it an open blocker
  predates the transform.
- **Gap:** the transform kept the *first-seen* copy by iteration order (dirs
  before jars), with a comment asserting "project classes are fed before
  external jars". That is not guaranteed — `:core:presentation` is a separate
  module whose classes can arrive as a jar, in which case the library's
  sound-playing copy could win silently with no build error.

## Content discriminator (order-independent)
| | size | references `android/media/MediaActionSound` |
|---|---|---|
| library `PlayShutterSoundKt.class` | 1590 B | YES (plays shutter) |
| our no-op (`:core:presentation`) | 455 B | no (muted) |

## Change
Rewrote `StripImagePickerShutterClass.strip()` to select by **content**:
- Drop every colliding copy whose bytes contain `android/media/MediaActionSound`.
- Keep the copy without it (ours); dedup any extra non-library duplicate.
- `check(keptOurs)` — fail the build if our no-op is missing (shutter would play).
- `check(droppedLibraryCopies.isNotEmpty())` — fail if the library copy isn't
  found (library changed; mute strategy must be revisited).
- `logger.lifecycle` prints which source was kept and which were dropped.

## Verification
- `:androidApp:assembleRelease` (content-based transform, `--rerun-tasks`): see report.
