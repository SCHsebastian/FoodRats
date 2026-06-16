# 007 · w0-16kb-alignment

**Status:** done

**Summary (≤6 lines):**
- Release `.so` verified 16-KB-aligned across all 4 ABIs. MediaPipe `0.10.35` (already bumped on disk from commit `6d61d93`) ships aligned libs; added `packaging { jniLibs { useLegacyPackaging = false } }`.
- Files: `androidApp/build.gradle.kts`, `gradle/libs.versions.toml` (comments), `CLAUDE.md` (tech-debt → RESOLVED).
- Decisions: no source rebuild needed; NB 0.10.35 renamed `libmediapipe_tasks_vision_jni.so` → `libmediapipe_tasks_jni.so` and moved it from `tasks-vision` to transitive `tasks-core`.
- Blockers: discovered a PRE-EXISTING unrelated defect — `core/presentation/.../io/github/ismoy/imagepickerkmp/.../playShutterSound.kt` shadow class duplicate-class-collides at dex-merge, failing `assembleRelease` on its own (minify on OR off). Worked around temporarily, then git-restored. → tracked as new task `w0-imagepicker-dup-class`.

**Verify (quoted):**
```
./gradlew :androidApp:assembleRelease -PcrashlyticsMappingUpload=false → BUILD SUCCESSFUL in 1m 32s (with temp workaround)
zipalign -c -P 16 -v 4 androidApp-release-unsigned.apk → Verification successful
objdump -p in-APK arm64 .so → all LOAD segments align 2**14 (16 KB); old 0.10.14 was 2**12 (4 KB)
```

Report: `docs/session/reports/w0-16kb-alignment.md`
