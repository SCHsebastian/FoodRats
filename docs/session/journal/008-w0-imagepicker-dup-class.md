# 008 · w0-imagepicker-dup-class

**Status:** done

**Summary (≤6 lines):**
- Fixed the duplicate-class dex-merge collision blocking `assembleRelease`. Root cause: `:core:presentation` shadow no-op `PlayShutterSoundKt` (mutes Android camera shutter; library 1.0.41 has no mute config) collides with the library's copy at R8 dex-merge.
- Fix: kept the shadow (preserves mute), dropped only the library's duplicate via a release-only AGP scoped-artifacts CLASSES transform (`androidComponents.onVariants(release).artifacts.forScope(ALL).toTransform(CLASSES)`). Verified merged jar has exactly 1 `PlayShutterSoundKt` = empty no-op.
- Files: `androidApp/build.gradle.kts`.
- Rejected: deleting shadow (un-mutes), `dependencies{exclude}` (module-level), `packagingOptions/pickFirst` (resources only), artifactType transform (didn't reach R8 on AGP 9.0.1).
- Blockers: none.

**Verify (quoted):**
```
./gradlew :androidApp:assembleRelease -PcrashlyticsMappingUpload=false → BUILD SUCCESSFUL in 1m 9s
./gradlew :androidApp:assembleDebug → BUILD SUCCESSFUL in 6s
```

**Follow-up (optional):** upstream issue for a library shutter-sound toggle → then delete shadow+transform; CI assertion that dex carries a single empty `playShutterSound`.

Report: `docs/session/reports/w0-imagepicker-dup-class.md`
