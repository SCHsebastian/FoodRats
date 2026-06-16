# w5-baseline-profiles-CI — Android Baseline Profiles + startup macrobenchmarks + CI hook

**Status:** DONE (code + build wiring verified green). The *actual* profile generation + the
startup-benchmark numbers require a device/emulator and are documented as a device step — they
cannot run in this environment (no device) nor on a free GitHub runner (no KVM).

Roadmap §5.3 (Baseline Profiles + startup traces). Terminal Wave 5 task.

---

## What was built

### 1. New `:baselineprofile` Gradle module
`baselineprofile/` — a plain `com.android.test` Android module (NOT KMP: macrobenchmark drives a
separate Android *app* process via UiAutomator, so it's Android-only by definition and the
`com.android.test` plugin models a single test variant with sources in `src/main`).

- `baselineprofile/build.gradle.kts`
  - Plugins: `com.android.test` + `androidx.baselineprofile`. **No** `org.jetbrains.kotlin.android`
    plugin — AGP 9.0 ships built-in Kotlin support and *rejects* the standalone Kotlin plugin
    (same as `:androidApp`). jvmTarget set via the top-level `kotlin { compilerOptions { … } }`
    block, mirroring `:androidApp`.
  - `targetProjectPath = ":androidApp"` — wires the benchmark to the app under test.
  - Deps from the catalog: `androidx-test-uiautomator`, `androidx-benchmark-macro-junit4`,
    `androidx-testExt-junit`, `androidx-espresso-core`, `junit`.
  - **Gradle Managed Device** `pixel6Api34` — Pixel 6 / API 34 / `aosp-atd` system image (no GMS,
    no animations, fastest headless boot). `baselineProfile { managedDevices += "pixel6Api34";
    useConnectedDevices = false }` so `generateBaselineProfile` targets the GMD by default.
- `baselineprofile/src/main/AndroidManifest.xml` — empty manifest (required by the plugin).
- `baselineprofile/src/main/kotlin/.../BaselineProfileGenerator.kt` — `BaselineProfileRule`;
  `pressHome()` → `startActivityAndWait()` → `waitForFirstScreen()` (cold start to the first
  interactive frame). `includeInStartupProfile = true`.
- `baselineprofile/src/main/kotlin/.../StartupBenchmark.kt` — `MacrobenchmarkRule`,
  `StartupTimingMetric`, COLD start, 10 iterations, two tests:
  `startupNoCompilation` (`CompilationMode.None`) vs
  `startupBaselineProfile` (`CompilationMode.Partial(BaselineProfileMode.Require)`) — proves the
  speed-up rather than assuming it (§5.3).

**Journey limitation (documented in-code):** the app gates everything behind Google Sign-In, so
the generator/benchmark only walk cold-start → first screen (Splash → SignIn). A fuller
authenticated journey (feed scroll, composer) needs a test account or a debug sign-in bypass; the
generator's `waitForFirstScreen()` KDoc says how to extend it.

### 2. `:androidApp` consumes the profile
`androidApp/build.gradle.kts`:
- Applied `androidx.baselineprofile` plugin (consumes the generated profile into the release
  variant's ART profile — it ships in the AAB and complements R8).
- Added `baselineProfile(projects.baselineprofile)` dependency.
- Added `implementation(libs.androidx.profileinstaller)` (installs the bundled profile at first run
  on devices without Play Cloud profiles; pinned for clarity though Compose pulls it transitively).

### 3. Version catalog + root plugins
`gradle/libs.versions.toml`:
- Versions: `androidxBenchmark = "1.5.0-alpha06"`, `androidxBaselineprofile = "1.5.0-alpha06"`,
  `androidxUiautomator = "2.3.0"`, `androidxProfileinstaller = "1.4.1"`.
  **Why the 1.5.0-alpha line, not stable 1.4.1:** AGP here is **9.0.1**. baselineprofile 1.4.1
  targets AGP 8.x and rejected `:androidApp` with *"Module `:androidApp` is not a supported android
  module."*. The 1.5.0-alpha line is the one that recognises AGP 9's variant API. (Documented in a
  comment in the catalog.)
- Libraries: `androidx-benchmark-macro-junit4`, `androidx-test-uiautomator`,
  `androidx-profileinstaller`.
- Plugins: `androidTest` (`com.android.test`, version.ref `agp`), `androidxBaselineprofile`.
`build.gradle.kts` (root): added `alias(libs.plugins.androidTest) apply false` +
`alias(libs.plugins.androidxBaselineprofile) apply false`.
`settings.gradle.kts`: `include(":baselineprofile")`.

### 4. CI hook (`.github/workflows/ci.yml`)
- Added `workflow_dispatch:` to the triggers.
- New **`baseline-profile`** job (manual-only via `if: github.event_name == 'workflow_dispatch'`,
  `continue-on-error: true`, free `ubuntu-latest`): enables KVM, synthesizes the placeholder
  `google-services.json`, runs `./gradlew :androidApp:generateBaselineProfile
  -PcrashlyticsMappingUpload=false` on the `pixel6Api34` GMD, uploads the generated profile as an
  artifact.

**Honest CI limitation (the key zero-paid-infra reality):** generating a real profile / running the
macrobenchmark needs a *hardware-accelerated* Android emulator, which needs `/dev/kvm`. GitHub's
**free** `ubuntu-latest` runners do **not** expose nested virtualization, so the emulator can't
boot there for zero cost. The job is therefore:
- `workflow_dispatch`-only (never auto-runs / never blocks merges),
- `continue-on-error` (informational outcome),
- and it `echo`s a clear message if `/dev/kvm` is absent.
It will actually succeed only on a **larger** (paid) GitHub runner with KVM or a **self-hosted
Linux** host with KVM. The authoritative regeneration path is local/device — see human.md §E/§F.
**This does NOT claim CI generates the profile on free runners — it can't, and the job says so.**

---

## Verification (quoted)

`./gradlew :baselineprofile:assemble` (no `assembleDebug` exists for a `com.android.test` module;
`assemble` compiles the macrobenchmark sources + packages the benchmark/non-minified-release APKs):
```
> Task :baselineprofile:assemble
BUILD SUCCESSFUL in 55s
62 actionable tasks: 55 executed, 7 from cache
```

`./gradlew :androidApp:assembleDebug` (plugin applied, app still builds):
```
> Task :androidApp:assembleDebug
BUILD SUCCESSFUL in 1m 8s
329 actionable tasks: 311 executed, 18 from cache
```

`./gradlew :androidApp:assembleRelease -PcrashlyticsMappingUpload=false` (R8 + the baseline
profile compile step `compileReleaseArtProfile` both run):
```
> Task :androidApp:compileReleaseArtProfile
> Task :androidApp:packageRelease
> Task :androidApp:assembleRelease
BUILD SUCCESSFUL in 1m 41s
453 actionable tasks: 196 executed, 8 from cache, 249 up-to-date
```

CI YAML parses: `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/ci.yml'))"` →
`YAML OK`.

Confirmed wired tasks: `:androidApp:generateBaselineProfile`,
`:androidApp:generateReleaseBaselineProfile`, `:baselineprofile:pixel6Api34BenchmarkReleaseAndroidTest`,
`:baselineprofile:connectedBenchmarkReleaseAndroidTest`,
`:baselineprofile:connectedNonMinifiedReleaseAndroidTest`.

**Cannot verify here (no device/emulator):** the actual profile generation and the StartupTimingMetric
numbers. Not faked.

---

## Exact device/CI-emulator commands (the steps that need a device)

Generate the profile (writes `androidApp/src/release/generated/baselineProfiles/baseline-prof.txt`,
which the plugin bakes into the release AAB — then **commit it**):
```
# Managed device (downloads/boots Pixel 6 / API 34 AOSP-ATD; needs KVM):
./gradlew :androidApp:generateBaselineProfile

# OR with a device/emulator already connected:
./gradlew :baselineprofile:connectedNonMinifiedReleaseAndroidTest
./gradlew :androidApp:generateBaselineProfile
```

Measure cold-start speed-up (None vs BaselineProfile):
```
./gradlew :baselineprofile:pixel6Api34BenchmarkReleaseAndroidTest    # GMD
# or, connected device/emulator:
./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest
```

---

## Decisions
- **baselineprofile/benchmark 1.5.0-alpha06**, not stable 1.4.1 — AGP 9.0.1 compatibility (1.4.1
  rejects the app module). Pin to the matching stable once the 1.5.x line ships GA.
- **`com.android.test` (non-KMP) module** — macrobenchmark is inherently Android-only.
- **No standalone Kotlin plugin** in the module — AGP 9 built-in Kotlin rejects it.
- **GMD = Pixel 6 / API 34 / `aosp-atd`** — lightest headless image; `google-atd` only if the
  journey needs Play services.
- **CI job is manual + best-effort**, not auto — free runners have no KVM. Honest, not faked.

## Blockers
- None for the build. The profile itself + the benchmark numbers are a **device/CI-emulator step**
  (human.md §E/§F). The roadmap §5.3 **crash-free-rate release gate** is intentionally OUT of scope
  for this task (it needs the SLO-threshold product decision — roadmap open decision #8 — plus
  Firebase/Play creds in CI); recorded in human.md §F.
