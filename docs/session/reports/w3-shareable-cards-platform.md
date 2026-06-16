# Report — w3-shareable-cards-platform

The platform layer for shareable story cards: an off-screen Compose-card → PNG renderer and a
share launcher that hands the PNG to Instagram Stories with a system-share-sheet fallback.
Implemented as KMP `expect/actual` in `:core:data/share/` (Android actual complete; iOS actual
compiles, Swift glue not xcodebuild-verified). PLATFORM LAYER ONLY — feed/stats entry points,
domain→props mappers, analytics leaves, and i18n toast strings are
`w3-shareable-cards-presentation`.

No prior interrupted work existed (`StoryCardRenderer`/`StoryShareLauncher` were absent).

## What was built

### `:core:data` build (Compose added to the module)
The spec (§4.2) places the renderer in `:core:data` "beside the existing `ShareController`". That
module had NO Compose plugin/deps (the spec's "already depends on Compose transitively" was wrong —
it only had Coil/Ktor/Firebase). Added the Compose Multiplatform plugin + `compose-runtime`,
`compose-foundation`, `compose-ui`, `coil-compose`, and a dep on `:core:designsystem` (for the
`Fr*ShareCard` templates) to `commonMain`; `androidx-core-ktx` (FileProvider) to `androidMain`.
`:core:data` stays JVM 17; depending on the JVM-11 `:core:designsystem` is fine (17 ≥ 11).

### commonMain (`core/data/src/commonMain/.../share/`)
- `ShareCardSize.kt` — `STORY_WIDTH_PX=1080`, `STORY_HEIGHT_PX=1920`, `SQUARE_SIDE_PX=1080`.
- `StoryCardRenderer.kt` — `expect class StoryCardRenderer { suspend fun renderToPng(widthPx, heightPx, content: @Composable () -> Unit): ByteArray }`
  + `renderStory(content)` / `renderSquare(content)` extension helpers (the expect can't carry
  default args when actualized via typealias — see Decisions). Does NO I/O; plate is pre-decoded.
- `StoryShareLauncher.kt` — `expect class StoryShareLauncher { fun shareToStories(imagePng): StoryShareOutcome }`
  + `enum StoryShareOutcome { OpenedInstagram, OpenedFallbackSheet, Failed }`.
- `PlateImageDecoder.kt` — common class `PlateImageDecoder(platformContext)` with
  `suspend fun decode(url: String?): ImageBitmap?` using Coil's non-Composable API
  (`SingletonImageLoader.get` + `ImageRequest` + `loader.execute()` + `coil3.toBitmap()`), returns
  `null` on any failure. `internal expect fun imageBitmapFromCoil(coil3.Bitmap): ImageBitmap`.
- `ShareCardFiles.kt` — pure, host-testable helpers: `CACHE_SUBDIR="share_cards"`,
  `PNG_MIME="image/png"`, `fileName()="share_card.png"` (stable name → no unbounded cache growth),
  `fileProviderAuthority(pkg)="$pkg.fileprovider"`.

### androidMain
- `StoryCardRenderer.android.kt` — `actual typealias StoryCardRenderer = StoryCardRendererAndroid`.
  Off-screen capture WITHOUT attaching to the window: a `ComposeView` is given an in-memory
  `OffscreenLifecycleOwner` (LifecycleRegistry RESUMED + SavedStateRegistryController) via
  `setViewTreeLifecycleOwner`/`setViewTreeSavedStateRegistryOwner` + `DisposeOnLifecycleDestroyed`,
  measured/laid out at exactly `widthPx×heightPx`, then drawn onto a Bitmap-backed `Canvas` on the
  next frame (`post`). Compose work on `Dispatchers.Main`, PNG encode on `dispatchers.io` (the single
  justified adapter `withContext`).
- `StoryShareLauncher.android.kt` — `actual typealias … = StoryShareLauncherAndroid`. Writes PNG to
  `cacheDir/share_cards/share_card.png`, `FileProvider.getUriForFile(...)`, tries
  `com.instagram.share.ADD_TO_STORY` (with `FLAG_GRANT_READ_URI_PERMISSION` + `source_application`,
  guarded by `resolveActivity`), else `ACTION_SEND` (image/png) chooser. Never throws → always an
  outcome.
- `PlateImageDecoder.android.kt` — `imageBitmapFromCoil` = `Bitmap.asImageBitmap()`.

### iosMain
- `StoryCardRenderer.ios.kt` — `actual typealias … = StoryCardRendererIos`. `ImageComposeScene`
  (density 1f so 1dp==1px) → `scene.render()` → `Image.encodeToData(PNG).bytes` (the same Skia path
  the avatar/meal compressors already use on iOS). `scene.close()` in `finally`.
- `StoryShareLauncher.ios.kt` — `actual typealias … = StoryShareLauncherIos(storyBridge: (ByteArray) -> Int)`.
  Maps the Swift bridge's status code (0/1/2) to `StoryShareOutcome`.
- `PlateImageDecoder.ios.kt` — `imageBitmapFromCoil` = `Bitmap.asComposeImageBitmap()`.

### Koin wiring
- Android: `FoodRatsApplication.androidShareModule()` now binds
  `single<StoryCardRenderer> { StoryCardRendererAndroid(androidContext(), dispatchers = get()) }`,
  `single<StoryShareLauncher> { StoryShareLauncherAndroid(androidContext()) }`,
  `single { PlateImageDecoder(platformContext = androidContext()) }`.
- iOS: new `storyShareIosModule(storyShare: (ByteArray) -> Int)` in `ShareIosModule.kt` binds the
  three; registered in `MainViewController` (new `storyShare` param) and threaded from
  `ContentView.swift`.

### Manifest / plist / Swift glue
- `androidApp/src/main/AndroidManifest.xml`: added `<provider androidx.core.content.FileProvider>`
  authority `${applicationId}.fileprovider`, `grantUriPermissions`, `@xml/file_paths`; added
  `<queries>` for `com.instagram.share.ADD_TO_STORY` + `com.instagram.android` (Android-11+ package
  visibility, else `resolveActivity` always null).
- `androidApp/src/main/res/xml/file_paths.xml`: `<cache-path name="share_cards" path="share_cards/"/>`.
- `iosApp/iosApp/StoryShareBridge.swift` (NEW): mirrors `ShareBridge.swift` — UIPasteboard
  `com.instagram.sharedSticker.backgroundImage` + `instagram-stories://share` (returns 0), else
  `UIActivityViewController` from the top VC (returns 1), else 2. `+ KotlinByteArray.toData()`
  extension.
- `iosApp/iosApp/ContentView.swift`: wires `storyShare: { pngBytes in KotlinInt(value: StoryShareBridge.shareToStories(pngBytes.toData())) }`.
- `iosApp/iosApp/Info.plist`: added `LSApplicationQueriesSchemes = [instagram-stories, instagram]`.

### Tests (host-JVM, `:core:data` commonTest)
- `ShareCardFilesTest` (3) — authority, stable `.png` name, MIME/subdir constants.
- `StoryShareOutcomeTest` (1) — the three-state taxonomy in documented order.
The actual rasterization + real share sheet are platform/IO-only and are a MANUAL on-device check
(spec §12) — NOT faked as a passing test.

## Decisions

1. **`actual typealias`, not subtyping.** `expect class` actualized via `actual typealias X = XAndroid`
   keeps the platform classes named distinctly (`StoryCardRendererAndroid`/`Ios`) and matches the
   existing `FirebaseInitializer`/`GoogleAuthClient` pattern. Consequence: the `expect` method can't
   carry default arg values AND the typealias-target can't either — so `renderToPng` has no defaults;
   `renderStory()`/`renderSquare()` extensions provide the standard 1080×1920 / 1080×1080 sizes.
2. **Renderer does no I/O; `PlateImageDecoder` is separate.** The spec's renderer signature is
   `content: @Composable () -> Unit` with the plate pre-decoded (§5). Added `PlateImageDecoder` as a
   clean common API for the presentation VM to call before render, satisfying the task's "pre-decode
   via Coil's non-Composable API" requirement without polluting the renderer with I/O.
3. **iOS launcher returns a sync status code.** `StoryShareBridge.shareToStories` decides
   Instagram-vs-fallback synchronously (`canOpenURL` + set pasteboard), dispatches the actual
   open/present async to main, and returns 0/1/2 immediately — so `StoryShareLauncher` stays
   non-suspending fire-and-present per spec §4.3.
4. **Plist + manifest applied now (not left manual).** Both are deterministic declarative edits; I
   applied them and flagged them for on-device verification rather than leaving them as TODOs.
5. **Analytics leaves NOT added here.** `PlateShared`/`AwardShared`/`StreakShared` (spec §7) are fired
   by ViewModels after the launcher returns Ok — that's presentation-layer work
   (`w3-shareable-cards-presentation`), out of this task's platform scope.

## Verify (all green)

```
./gradlew :core:data:testAndroidHostTest
> Task :core:data:testAndroidHostTest
BUILD SUCCESSFUL in 2s
62 actionable tasks: 10 executed, 52 up-to-date
```
(ShareCardFilesTest 3/3, StoryShareOutcomeTest 1/1 — confirmed in test-results XML, 0 failures.)

```
./gradlew :androidApp:assembleDebug
> Task :androidApp:assembleDebug
BUILD SUCCESSFUL in 7s
329 actionable tasks: 43 executed, 286 up-to-date
```

```
./gradlew :shared:compileKotlinIosSimulatorArm64
> Task :shared:compileKotlinIosSimulatorArm64
BUILD SUCCESSFUL in 5s
146 actionable tasks: 40 executed, 106 up-to-date
```

```
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
> Task :shared:linkDebugFrameworkIosSimulatorArm64
BUILD SUCCESSFUL in 21s
147 actionable tasks: 30 executed, 117 up-to-date
```

Generated debug ObjC header confirms the bridge contract:
`storyShare:(FRSInt *(^)(FRSKotlinByteArray *))` (Swift `(KotlinByteArray) -> KotlinInt`);
`FRSKotlinByteArray` exposes `get(index:) -> int8_t` + `size: Int32`; `FRSInt` is `KotlinInt(value:)`
— exactly what `StoryShareBridge.swift` / `ContentView.swift` use.

## Manual steps that remain (cannot be done in this environment)

- **On-device share smoke (the ONLY verification of the real intent/URL-scheme + rasterization).**
  Build/install on a real Android device + iPhone WITH Instagram installed; from the presentation
  task's share button: confirm the Story opens with the card as the full-screen background; then
  uninstall Instagram (or use a device without it) → confirm the system share sheet appears with the
  PNG attached. (spec §15 step 11.)
- **iOS Xcode:** add `StoryShareBridge.swift` to the `iosApp` Xcode target (it's a new file — Xcode
  must compile it). The Swift edits are NOT xcodebuild-verified here (no xcodebuild) — they call the
  verified-exported `MainViewController(... storyShare:)` symbol + standard UIKit APIs and match the
  generated header. Confirm `Info.plist`'s `LSApplicationQueriesSchemes` is in the target's built
  plist.
- **Presentation task wires the actual share buttons / VM handlers / analytics / toast strings**
  (`w3-shareable-cards-presentation`) — this task only provides the platform API.
