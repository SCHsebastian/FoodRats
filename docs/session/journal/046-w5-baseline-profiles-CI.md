# 046 · w5-baseline-profiles-CI

**Status:** done — **WAVE 5 COMPLETE** · **ALL ROADMAP WAVES (0–5) COMPLETE**

**Summary (≤6 lines):**
- New `:baselineprofile` macrobenchmark module (`com.android.test` + `androidx.baselineprofile`), `BaselineProfileGenerator` + `StartupBenchmark`, wired into `:androidApp` release (profile consumed into the AAB); CI job added (`workflow_dispatch` + `continue-on-error` — free runners lack KVM, documented honestly).
- Files: `baselineprofile/*` (new), `settings.gradle.kts`, root `build.gradle.kts`, `gradle/libs.versions.toml`, `androidApp/build.gradle.kts`, `.github/workflows/ci.yml`, `human.md`.
- Decisions: benchmark/baselineprofile `1.5.0-alpha06` (stable 1.4.1 rejects AGP 9.0.1); GMD Pixel 6 / API 34 / aosp-atd; release `compileReleaseArtProfile` runs.
- Blockers: none for build. Device step (human.md): `:androidApp:generateBaselineProfile` then commit `baseline-prof.txt`; benchmark `:baselineprofile:pixel6Api34BenchmarkReleaseAndroidTest`. §5.3 crash-free-rate gate out of scope (needs SLO decision #8 + creds).

**Verify (quoted):**
```
:baselineprofile:assemble → BUILD SUCCESSFUL in 55s
:androidApp:assembleDebug → BUILD SUCCESSFUL in 1m 8s
:androidApp:assembleRelease -PcrashlyticsMappingUpload=false → compileReleaseArtProfile ran, BUILD SUCCESSFUL in 1m 41s
```

Report: `docs/session/reports/w5-baseline-profiles-CI.md`
