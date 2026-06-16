# 032 · w3-shareable-cards-platform

**Status:** done

**Summary (≤6 lines):**
- Shareable-card platform layer in `:core:data/share/` (expect/actual): `StoryCardRenderer` (off-screen rasterize `Fr*ShareCard` → PNG → FileProvider URI), `StoryShareLauncher` (Android `ACTION_SEND`/Instagram-Stories intent; iOS Swift-bridge share sheet), `PlateImageDecoder` (Coil non-Composable decode). Android complete, iOS compiles, Swift glue header-verified only.
- Files: NEW `core/data/.../share/*` (common + android + ios + commonTest), `androidApp/res/xml/file_paths.xml`, `iosApp/.../StoryShareBridge.swift`; MOD `core/data/build.gradle.kts` (Compose plugin+deps), `FoodRatsApplication.kt` (Koin), `ShareIosModule.kt`, `MainViewController.kt`, `iosApp/{ContentView.swift,Info.plist}`, `androidApp/AndroidManifest.xml` (FileProvider + `<queries>`), `human.md`.
- Decisions: renderer in `:core:data` per spec (had to ADD Compose plugin+deps — spec's "already has Compose" was wrong); `renderStory()`/`renderSquare()` extensions (actual typealias = no default args); plate decode split into `PlateImageDecoder` so renderer stays I/O-free; iOS launcher non-suspending sync 0/1/2 status; analytics leaves left to presentation.
- Blockers: none. MANUAL: on-device share smoke + add `StoryShareBridge.swift` to Xcode target (both in human.md).

**Verify (quoted):**
```
:core:data:testAndroidHostTest → BUILD SUCCESSFUL in 2s (ShareCardFilesTest 3/3, StoryShareOutcomeTest 1/1)
:androidApp:assembleDebug → BUILD SUCCESSFUL in 7s
:shared:linkDebugFrameworkIosSimulatorArm64 → BUILD SUCCESSFUL in 21s (storyShare bridge symbol confirmed)
```

Report: `docs/session/reports/w3-shareable-cards-platform.md` · Handoff: `docs/session/handoffs/w3-shareable-cards-platform.md`
