# Shareable plate / award / streak cards to Instagram Stories

**Date:** 2026-06-14
**Status:** Design — pending implementation
**Implements:** `docs/roadmap/2026-06-14-feature-roadmap.md` item 3.1 (and the share hand-off from item 2.4, the weekly-digest story).

## 1. Decision

Add a viral-loop share path: from a meal, a stats award, or a streak milestone, the user renders a **beautiful branded card** to a flat PNG and shares it to **Instagram Stories**, with a graceful fallback to the system share sheet when Instagram is absent or its Stories intent is unavailable.

Three new design-system templates carry the visual — `FrPlateShareCard`, `FrAwardShareCard`, `FrStreakShareCard` — each in a square 1:1 and a Story 9:16 variant. They take **primitives only** (an image + strings + the day-emote glyph), never domain types; the feature presentation layer maps `FeedMealUi` / `MealAward` / `HeroStats` to those primitives. Two new `expect/actual` seams do the platform work: `StoryCardRenderer` (compose → PNG `ByteArray`, off-screen, fixed export resolution) and `StoryShareLauncher` (the Instagram-Stories intent / URL scheme, with the system-share fallback). Both live in `:core:data/share/` beside the existing `ShareController`. Analytics reuse the existing GA4 `share` event; we do **not** invent a new event.

## 2. Motivation

The app already has a closed-group meal feed with branded chrome (the per-day `DailyEmote` motif, the Iron & Ember palette, score badges). None of that ever leaves the app — there is zero outbound surface and therefore no organic growth loop. Instagram Stories is where this audience already posts food. A one-tap "make a card → Stories" path turns the nicest in-app moments (a high-scoring plate, "best cook of the week", a 14-day streak) into shareable, branded artifacts that pull new members toward the crew-invite flow.

The infrastructure to do this is already half-built: `ShareController` (`:core:domain/share/`) + `ShareControllerAndroid` / `ShareControllerIos` + the Swift `ShareBridge` already share **text** via the system sheet, and the `share` analytics leaf already exists (`AnalyticsEvent.CrewInviteShared`). This spec extends that exact shape to **images** and to **Instagram Stories specifically**.

Trade-offs accepted:

- **Compose-to-bitmap is version-fragile.** Off-screen capture of a `@Composable` is not a first-class CMP API. We accept a narrow, well-tested render surface (the three cards, fixed size, no scrolling/animation) to keep it reliable — see §5 and §13.
- **No animated / video stories.** Static PNG only. Animated Stories need a video encoder per platform; out of scope (§3).
- **No deep-back-link in the shared asset.** The card is a flat image; it carries no URL into the app. This is deliberate for privacy (§9) and keeps the asset honest (the data is baked in, not a link that could expire or leak a signed URL).

## 3. Scope

In scope: the three `Fr*` card templates (1:1 + 9:16) + their catalog entries; `StoryCardRenderer` (`expect/actual`) and `StoryShareLauncher` (`expect/actual`) in `:core:data`; the iOS Swift bridge mirroring `ShareBridge.swift`; the feature → card-primitive mappers in `:feature:feed` and `:feature:stats`; the four entry points (meal detail, stats award, streak milestone, end of the weekly-digest story); i18n strings (en/es) for buttons, toasts, and **all on-card chrome text**; the `share` analytics call at each entry; the Android `FileProvider` manifest entry; the iOS `LSApplicationQueriesSchemes` plist entry; unit tests for the mappers; Konsist confirmation.

Out of scope: video / animated Stories; deep-back-links from the shared asset into the app; web Open-Graph preview pages; the weekly-digest **story player** itself (that is roadmap 2.4 — this spec only consumes its "end of story → share" hand-off and assumes the player exists or is built alongside); any change to the score / rating / comment subsystem; sharing to platforms other than Instagram-Stories-or-system-sheet (WhatsApp Status, TikTok, etc. are just whatever the fallback chooser offers).

## 4. Architecture

```
:core:designsystem (templates, primitives only)
    FrPlateShareCard / FrAwardShareCard / FrStreakShareCard   (1:1 + 9:16 variants)

:core:domain/share
    ShareController                       (existing — text share, unchanged)

:core:data/share
    ShareController{Android,Ios}          (existing, unchanged)
    StoryCardRenderer        expect       (new — Compose card → PNG ByteArray)
        actual androidMain                (GraphicsLayer capture)
        actual iosMain                    (ImageComposeScene → UIImage → PNG)
    StoryShareLauncher       expect       (new — IG-Stories intent / URL scheme + fallback)
        actual androidMain                (com.instagram.share.ADD_TO_STORY + FileProvider, else ACTION_SEND)
        actual iosMain                    (instagram-stories:// + UIPasteboard, else UIActivityViewController)

iosApp/iosApp
    ShareBridge.swift                     (existing text-share lambda)
    StoryShareBridge.swift                (new — present from a live UIViewController, mirrors ShareBridge)

:feature:feed   — meal detail "Share" → FeedMealUi → FrPlateShareCard primitives
:feature:stats  — award "Share" + streak-milestone "Share" → MealAward / HeroStats → card primitives
shared          — wires the iOS lambdas through MainViewController; weekly-digest story "Share" at its end
```

### 4.1 Card templates (`:core:designsystem/templates/`)

Three new templates, each prefixed `Fr*` and taking **primitives only** (the design-system rule: atoms/molecules/templates here never import domain types — they take primitives or presentation enums; the domain-aware cards like `FrFeedMealCard` live in features). Each has a square 1:1 export variant and a Story 9:16 variant; share to Stories uses 9:16, the in-app preview uses whichever the entry point chooses.

```kotlin
enum class ShareCardFormat { Square, Story }   // presentation enum, no domain type

@Composable
fun FrPlateShareCard(
    plate: ImageBitmap?,        // the decoded plate photo; null → branded placeholder
    dishName: String,
    authorName: String,
    scoreLabel: String?,        // pre-formatted "8.4 ★" via FeedStringKey.RatingSummary at the call site
    dayEmote: String,           // DailyEmote.forDay(day) — the brand motif glyph
    footerBrand: String,        // resolve(ShareCardStringKey.BrandFooter)
    format: ShareCardFormat,
    modifier: Modifier = Modifier,
)

@Composable
fun FrAwardShareCard(
    plate: ImageBitmap?,
    awardLabel: String,         // resolve(ShareCardStringKey.AwardBestMeal) etc. — passed in
    dishName: String,
    authorName: String,
    scoreLabel: String?,
    dayEmote: String,
    footerBrand: String,
    format: ShareCardFormat,
    modifier: Modifier = Modifier,
)

@Composable
fun FrStreakShareCard(
    streakDays: Int,
    headline: String,           // resolve(ShareCardStringKey.StreakHeadline, streakDays)
    subline: String,            // resolve(ShareCardStringKey.StreakSubline)
    dayEmote: String,
    footerBrand: String,
    format: ShareCardFormat,
    modifier: Modifier = Modifier,
)
```

These compose against `MaterialTheme.colorScheme` / `typography` / `shapes` so the Iron & Ember palette propagates automatically. Meaning colors come from `LocalFrSemanticColors.current` — the streak card uses `streakHot` for the day count, the award card uses `celebration` for its banner, exactly as `SemanticColors.kt` defines them (`success`, `warning`, `danger`, `info`, `celebration`, `streakHot`). No raw `Color(0x…)`.

### 4.2 `StoryCardRenderer` — `expect/actual` (`:core:data/share/`)

```kotlin
// commonMain
expect class StoryCardRenderer {
    /**
     * Renders [content] off-screen at the fixed export resolution and returns a PNG byte array.
     * Must be called with the plate bitmap already decoded (see §5) — the renderer does no I/O.
     */
    suspend fun renderToPng(
        widthPx: Int = STORY_WIDTH_PX,    // 1080
        heightPx: Int = STORY_HEIGHT_PX,  // 1920
        content: @Composable () -> Unit,
    ): ByteArray
}
```

`:core:data` already targets `JvmTarget.JVM_17` and hosts `expect/actual` per-platform code (the DataStore factory, the `CrashReporter` impls, the `ShareController` impls), so it is the right home — no new module. It also already depends on Compose transitively via the image-loader setup, so referencing `@Composable` here is consistent.

### 4.3 `StoryShareLauncher` — `expect/actual` (`:core:data/share/`)

```kotlin
// commonMain
expect class StoryShareLauncher {
    /**
     * Attempts to open Instagram Stories with [imagePng] as the full-screen background.
     * Falls back to the system share sheet (same path ShareController.shareText uses for text)
     * when Instagram is not installed or its Stories intent is unavailable. Never throws.
     */
    fun shareToStories(imagePng: ByteArray): StoryShareOutcome
}

enum class StoryShareOutcome { OpenedInstagram, OpenedFallbackSheet, Failed }
```

The outcome is reported back so the ViewModel can drive the right toast (§7, §8). It is **not** a `Result<T, E>` — there is no recoverable domain error to `when`-exhaust; the three states are a UI affordance, not a failure taxonomy, and the launcher is non-suspending fire-and-present (it just starts an Activity / presents a controller).

## 5. Rendering pipeline

**Resolution.** Fixed export at `1080 × 1920` for the Story variant (Meta's recommended Stories asset size), `1080 × 1080` for the square variant. Constants live next to the renderer. We render at a fixed pixel size rather than at the on-screen density so the output is identical across devices.

**Image pre-load (the load-bearing constraint).** Plate URLs are short-lived V4 signed URLs minted by `ImageUrlPort.resolve(...)` (the `mintPlateUrls` callable — see `core/domain/.../image/ImageUrlPort.kt`), loaded on-screen via Coil 3 `AsyncImage` with the singleton `ImageLoader` installed by `installImageLoader()` (`core/data/.../image/ImageLoaderSetup.kt`, called from `FoodRatsApplication.onCreate()` + `MainViewController()`). The off-screen renderer **cannot** rely on `AsyncImage` resolving asynchronously inside a one-shot capture — there is no recomposition loop to wait on. So the share flow is two-phase:

1. The ViewModel pre-decodes the plate into an `ImageBitmap` (Coil `ImageRequest` + `execute()`, reusing the already-installed `ImageLoader` so the signed URL and HTTP engine are shared). On failure it proceeds with `plate = null` → the card renders a branded placeholder (the `dayEmote` on a solid `surface`), never a broken image.
2. The decoded bitmap is handed to `FrPlateShareCard(plate = bitmap, …)` and `StoryCardRenderer.renderToPng(...)` captures the now-fully-resolved tree.

**Threading.** The Coil decode is `suspend` and runs off the main thread. The Compose capture itself has platform constraints: Android `GraphicsLayer` capture and iOS `ImageComposeScene` must touch Compose state on the main dispatcher, so `renderToPng` switches to the main thread for the capture step and back to default for PNG encoding. This is the **one** place a `withContext` appears outside a repository method, and it is justified: `StoryCardRenderer` is a platform adapter (it lives in `:core:data`, the adapter layer), not a use case or ViewModel — the dispatcher-boundary rule ("exactly one `withContext(io)` per public data-layer method; zero in use cases / ViewModels") is satisfied. The renderer's public method owns its single boundary.

**Android actual.** Use `androidx.compose.ui.graphics.layer.GraphicsLayer` (`rememberGraphicsLayer` is not available off-composition, so the actual composes into an `AndroidView`/`ComposeView` measured at the fixed size, draws into the layer, then `layer.toImageBitmap()` → `Bitmap.compress(PNG)` → `ByteArray`). The `ComposeView` is attached to a window-less measured container at `1080 × 1920`.

**iOS actual.** Use Compose Multiplatform's `ImageComposeScene` (renders a `@Composable` to a `Skia` `Image` without a live window), then `Image.encodeToData(EncodedImageFormat.PNG)` → `ByteArray`. This avoids needing a host `UIView` snapshot and keeps the render path pure-Kotlin on iOS; only the **share presentation** needs Swift (§6).

## 6. Platform share

### 6.1 Android — `StoryShareLauncherAndroid`

Instagram Stories ingest is an explicit-package intent:

```kotlin
val uri: Uri = FileProvider.getUriForFile(
    context, "${context.packageName}.fileprovider", pngFileInCacheDir
)
val intent = Intent("com.instagram.share.ADD_TO_STORY").apply {
    setDataAndType(uri, "image/png")              // background asset
    putExtra("source_application", "es.schsebastian.foodrats")
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
if (context.packageManager.resolveActivity(intent, 0) != null) {
    context.startActivity(intent); OpenedInstagram
} else {
    // fallback — same shape as ShareControllerAndroid.shareText, but ACTION_SEND of an image
    shareImageViaChooser(uri); OpenedFallbackSheet
}
```

- The PNG is written to `context.cacheDir` and exposed via a `FileProvider` (`androidx.core.content.FileProvider`) authority `${packageName}.fileprovider` — **the launcher does not embed any signed URL; the pixels are the payload** (§9).
- `FLAG_GRANT_READ_URI_PERMISSION` is mandatory: without it Instagram cannot read the `content://` URI and the Story opens empty.
- Fallback is `Intent.ACTION_SEND` with `type = "image/png"` + `EXTRA_STREAM = uri`, wrapped in `Intent.createChooser`, mirroring the existing `ShareControllerAndroid.shareText` chooser pattern.

**MANUAL — Android manifest.** A `<provider>` for `androidx.core.content.FileProvider` must be added to `androidApp/src/main/AndroidManifest.xml` (authority `${applicationId}.fileprovider`, `grantUriPermissions=true`, with an `@xml/file_paths` resource exposing the cache dir). There is **no** `FileProvider` in the manifest today (confirmed). This is a one-time manual edit flagged in §15.

### 6.2 iOS — `StoryShareLauncherIos`

Per Meta's Stories spec, iOS Instagram ingest is a URL scheme plus a pasteboard hand-off:

```
1. UIPasteboard.general.items = [{ "com.instagram.sharedSticker.backgroundImage": pngData }]
   (with a short pasteboard expiration)
2. open URL "instagram-stories://share?source_application=es.schsebastian.foodrats"
3. if that URL can't be opened → present UIActivityViewController(activityItems: [pngData])
```

Steps 1–3 must run from a live `UIViewController` and on the main thread, which Kotlin/Native can't reach cleanly — so, exactly like `ShareControllerIos` + `ShareBridge.swift`, the iosMain `actual` delegates to a Swift lambda supplied at startup. A new `StoryShareBridge.swift` mirrors `ShareBridge.swift` (top-view-controller walk, main-thread dispatch, `UIActivityViewController` fallback with the iPad popover anchor). The lambda is threaded through `ContentView.swift` → `MainViewController(...)` into the iOS Koin module, alongside the existing `shareBridge`.

**MANUAL — iOS plist.** `instagram-stories` must be listed under `LSApplicationQueriesSchemes` in the iOS app `Info.plist`, or `canOpenURL` returns false and every share silently falls back to the system sheet. Flagged in §15.

### 6.3 Fallback semantics

Fallback is never an error — a user without Instagram still gets the system share sheet with the PNG attached, and `StoryShareOutcome.OpenedFallbackSheet` drives a neutral toast. `Failed` is reserved for the case where even the fallback chooser can't be presented (no Activity / no view controller), which surfaces `ShareCardStringKey.ShareFailed`.

## 7. Analytics

Reuse the existing GA4 predefined `share` event — **do not** add a new leaf. The codebase already maps `share` with `content_type` + `item_id` (`AnalyticsEvent.CrewInviteShared`, `content_type=crew_invite`). Add one analogous leaf per card kind, all named `"share"`:

```kotlin
// core/domain/.../analytics/AnalyticsEvent.kt — alongside CrewInviteShared
data class PlateShared(val mealId: MealId) : AnalyticsEvent {
    override val name = "share"
    override val params = mapOf("content_type" to text("plate"), "item_id" to text(mealId.value))
}
data class AwardShared(val mealId: MealId) : AnalyticsEvent {
    override val name = "share"
    override val params = mapOf("content_type" to text("award"), "item_id" to text(mealId.value))
}
data class StreakShared(val streakDays: Int) : AnalyticsEvent {
    override val name = "share"
    override val params = mapOf("content_type" to text("streak"), "item_id" to count(streakDays))
}
```

`content_type ∈ {plate, award, streak}`; `item_id` is the meal id (plate/award) or the streak length (no meal). **No PII** beyond ids — author display names never go into params (the sealed taxonomy is the single source of truth and is Konsist-gated to stay vendor-free).

**When fired.** After the success path, mirroring every other event in this codebase: the ViewModel fires the event when `StoryShareLauncher.shareToStories(...)` returns `OpenedInstagram` **or** `OpenedFallbackSheet` (the user did share / was handed the sheet). It is **not** fired on `Failed`. The call sites are ViewModels, never use cases. The feature `*ModuleVerifyTest` adds `AnalyticsPort::class` to its `extraTypes` and the ViewModel is registered with explicit `viewModel { … analytics = get() }` (not `viewModelOf`), per the analytics convention.

## 8. Presentation

### 8.1 Entry points and the feature → card-primitive mapping

| Entry | Lives in | Source data | Card | Maps to primitives via |
|---|---|---|---|---|
| Meal detail "Share" | `feature/feed/.../presentation/detail/MealDetailScreen.kt` | `FeedMealUi` | `FrPlateShareCard` | new `FeedMealUi.toPlateCard()` |
| Stats award "Share" | `feature/stats/.../presentation/stats/StatsScreen.kt` | `MealAward` | `FrAwardShareCard` | new `MealAward.toAwardCard()` |
| Streak milestone | `feature/stats/.../presentation/stats/StatsScreen.kt` | `HeroStats.personalStreak` / `crewStreak` (`Streak`) | `FrStreakShareCard` | new `HeroStats.toStreakCard()` |
| End of weekly-digest story | `shared` (digest story player, roadmap 2.4) | the digest's "best meal" / "your week" model | `FrPlateShareCard` / `FrStreakShareCard` | digest model → primitives |

The mappers are pure functions in the **feature presentation layer** (not in `:core:designsystem`, which can't see domain types). Example shape:

```kotlin
// feature/feed/.../presentation/components/  (next to FeedMealUi)
data class PlateShareCardModel(
    val photoUrl: String,            // pre-resolved signed URL — the VM decodes it to ImageBitmap before render
    val dishName: String,
    val authorName: String,
    val scoreLabel: String?,         // built at the call site via resolve(FeedStringKey.RatingSummary, …)
    val dayEmote: String,            // already on FeedMealUi.dayEmote (DailyEmote.forDay)
)

fun FeedMealUi.toPlateCard(scoreLabel: String?): PlateShareCardModel = PlateShareCardModel(
    photoUrl = photoUrl,
    dishName = dishName,
    authorName = authorName,
    scoreLabel = scoreLabel,
    dayEmote = dayEmote,
)
```

`FeedMealUi` already carries `photoUrl`, `dishName`, `authorName`, `averageScore`, `ratingCount`, and `dayEmote` (confirmed in `FeedMealUi.kt`), so the plate mapper needs no new feed data. `MealAward` carries `photoUrl`, `dish`, `author`, `score`, `ratingCount`, `day` — the award mapper resolves `DailyEmote.forDay(award.day)` for the motif. The streak mapper reads `HeroStats.personalStreak.days` (a `Streak` value class wrapping `Int`).

### 8.2 Share buttons (where they live)

- **Meal detail:** an `FrIconButton` (share glyph) in `MealDetailScreen`'s action area, beside the existing affordances. Tapping dispatches a `MealDetailIntent.ShareTapped`.
- **Stats award + streak:** a small `FrButton`/`FrIconButton` on the award card row and on the hero streak block in `StatsScreen`, dispatching `StatsIntent.ShareAwardTapped(mealId)` / `StatsIntent.ShareStreakTapped`.
- **Digest story:** a "Share" CTA on the final scene of the story player (roadmap 2.4 surface).

The ViewModel handler for each: (1) build the card model + `scoreLabel`; (2) `suspend` decode the plate to `ImageBitmap` (where applicable); (3) call `StoryCardRenderer.renderToPng { Fr…ShareCard(...) }`; (4) `StoryShareLauncher.shareToStories(png)`; (5) on `OpenedInstagram`/`OpenedFallbackSheet` fire the `share` analytics event and emit a "shared" toast; on `Failed` emit `ShareFailed`. State carries a transient `isPreparingShare: Boolean` so the button shows a spinner during the decode+render.

## 9. Privacy

The sharer **explicitly chooses** to share, and the card renders **only data already visible to them** in their own crew (the plate they're viewing, an award in their own stats, their own streak). No new read surface is opened. Critically, **no signed plate URL is embedded in the shared asset** — the renderer decodes the (membership-checked, short-lived) signed URL into pixels and bakes them into a flat PNG; the exported image is not a link and cannot be replayed to fetch the original Storage object after the URL expires. Author display names appear *on the card* (they are already on-screen to the sharer) but **never** in analytics params (§7). The Android `FileProvider` exposes only the app's own cache dir, with read permission granted per-share via `FLAG_GRANT_READ_URI_PERMISSION` and revoked when the task ends; the iOS pasteboard hand-off uses a short expiration.

## 10. i18n

All card chrome text **and** the button / toast strings are user-visible and flow through `resolve(StringKey)`. A new `ShareCardStringKey` enum implementing the sealed `StringKey` interface (in `:core:designsystem`'s i18n surface if the card chrome is shared, or split across the consuming features — default: a shared `ShareCardStringKey` for the on-card brand/award/streak text so both features reuse it). The feed/stats screens add their own button/toast keys to `FeedStringKey` / `StatsStringKey`.

| Key | en | es | Notes |
|---|---|---|---|
| `ShareCardStringKey.BrandFooter` | `FoodRats` | `FoodRats` | On-card footer wordmark — still routed through i18n per the glyph/punctuation rule |
| `ShareCardStringKey.AwardBestMeal` | `Best meal` | `Mejor plato` | On-card award banner label |
| `ShareCardStringKey.AwardBestCook` | `Best cook` | `Mejor cocinero/a` | On-card award banner label |
| `ShareCardStringKey.StreakHeadline` | `%1$d-day streak 🔥` | `Racha de %1$d días 🔥` | On-card streak headline; arg = streak days |
| `ShareCardStringKey.StreakSubline` | `Keep it cooking` | `A seguir cocinando` | On-card streak subline |
| `FeedStringKey.ShareMeal` (new) | `Share` | `Compartir` | Meal-detail share button |
| `StatsStringKey.ShareAward` (new) | `Share` | `Compartir` | Award/streak share button |
| `SharedStringKey.ShareSucceeded` (new) | `Shared to your story` | `Compartido en tu historia` | Toast on `OpenedInstagram` |
| `SharedStringKey.ShareOpenedSheet` (new) | `Opening share…` | `Abriendo para compartir…` | Toast on `OpenedFallbackSheet` |
| `SharedStringKey.ShareFailed` (new) | `Couldn't share right now.` | `No se pudo compartir ahora.` | Toast on `Failed` |

`scoreLabel` on the card reuses the existing `FeedStringKey.RatingSummary` (`"%1$s ★ · %2$d"`) — no new key; it is assembled at the call site and passed into the card as a finished string, consistent with how the design system avoids assembling formatted text itself.

## 11. Analytics taxonomy test

`AnalyticsTaxonomyTest` (`core/domain/.../commonTest/.../analytics/AnalyticsTaxonomyTest.kt`) walks the `AnalyticsEvent` sealed tree. Add assertions that `PlateShared` / `AwardShared` / `StreakShared` each emit `name == "share"` with `content_type ∈ {plate, award, streak}` and an `item_id` param, and that no param carries a display name (the no-PII guard). This locks the "reuse `share`, never invent a new event" decision.

## 12. Tests

New tests (commonTest, `kotlin.test`):

- `FeedMealUiShareTest` (`:feature:feed`): `toPlateCard(scoreLabel)` maps `photoUrl` / `dishName` / `authorName` / `dayEmote` straight through; a null `scoreLabel` (no ratings) is preserved as null.
- `MealAwardShareTest` (`:feature:stats`): `toAwardCard()` maps `dish.value` / `author.displayName` / `score`; the day-emote equals `DailyEmote.forDay(award.day)`.
- `HeroStatsShareTest` (`:feature:stats`): `toStreakCard()` reads `personalStreak.days`; a `Streak(0)` still produces a valid (if unexciting) model.
- `ShareViewModelTest` (extend the relevant feed/stats VM test): a share intent fires the `share` analytics event **only** when the launcher returns `OpenedInstagram`/`OpenedFallbackSheet`, and **not** on `Failed`; `isPreparingShare` toggles true→false around the render. Use a `RecordingAnalyticsTracker` and a fake `StoryShareLauncher` returning each outcome.
- `AnalyticsTaxonomyTest`: per §11.

Platform-only — **not** unit-tested, verified manually on device (§15):

- `StoryCardRenderer` actuals (real GraphicsLayer / ImageComposeScene output) — there is no JVM-host way to assert pixels reliably; the render is exercised through the on-device smoke.
- `StoryShareLauncher` actuals (the real Instagram intent / URL scheme) — requires Instagram installed; a fake stands in for ViewModel tests.

Catalog (`catalogApp`, JVM): the three card stories compose under `createComposeRule` only insofar as they render without crashing; the real review surface is the running catalog APK.

## 13. Design system & catalog

Every public `Fr*` composable ships a `catalogApp` `CatalogEntry` (the design-review contract). Add three entries to `catalogApp/.../stories/TemplateStories.kt` under `CatalogGroup.TEMPLATES`, IDs lowercase `template.<name>`:

```kotlin
CatalogEntry("template.plateShareCard",  CatalogGroup.TEMPLATES, "FrPlateShareCard",  "Shareable plate card (1:1 + 9:16)")  { PlateShareCardStory() },
CatalogEntry("template.awardShareCard",  CatalogGroup.TEMPLATES, "FrAwardShareCard",  "Shareable award card (1:1 + 9:16)")  { AwardShareCardStory() },
CatalogEntry("template.streakShareCard", CatalogGroup.TEMPLATES, "FrStreakShareCard", "Shareable streak card (1:1 + 9:16)") { StreakShareCardStory() },
```

Each story uses `CatalogSceneSplit` to render the square and Story variants side by side with labels (the `CatalogScene`/`CatalogSceneSplit` helpers in `catalogApp/.../components/CatalogScene.kt`). Because `catalogApp` depends only on `:core:designsystem` (no features, no Firebase, no Koin — by deliberate design), the stories pass a **static** placeholder `ImageBitmap` (a solid-color or bundled sample), never a Coil-loaded URL. This is exactly why the cards take `plate: ImageBitmap?` rather than a URL — the primitive boundary keeps them catalogable.

## 14. Konsist / arch tests

- `KonsistRulesTest` (`:core:domain`) is unaffected — the new analytics leaves live in `core/domain/.../analytics/` and use only `kotlin.stdlib` + in-module types (the `share` event shape already does this). No Firebase / Android / Compose enters `:core:domain`.
- The card templates live in `:core:designsystem` and import **no domain types** (they take `ImageBitmap`/`String`/`Int`/`ShareCardFormat`), satisfying the "atoms/molecules/templates never import domain types" rule. The feature→card mappers (which *do* see `FeedMealUi`/`MealAward`/`HeroStats`) live in the feature presentation layer, not in the design system.
- `StoryCardRenderer` / `StoryShareLauncher` actuals importing Android (`FileProvider`, `Intent`) and iOS (`UIPasteboard` via bridge) types are correctly in `:core:data` `androidMain`/`iosMain` — the adapter layer — never in `:core:domain`. The vendor-SDK-only-in-adapters rule holds.

## 15. Order of work (for the implementation plan)

The implementation plan (to be generated by `writing-plans` after this spec is approved) should sequence the change roughly as:

1. `:core:domain` — add `PlateShared` / `AwardShared` / `StreakShared` analytics leaves; extend `AnalyticsTaxonomyTest`.
2. `:core:designsystem` — `FrPlateShareCard` / `FrAwardShareCard` / `FrStreakShareCard` + `ShareCardFormat` + `ShareCardStringKey` + en/es strings; three catalog stories in `TemplateStories.kt`; run `:catalogApp:assembleDebug`.
3. `:core:data` — `StoryCardRenderer` (`expect` + Android `GraphicsLayer` actual + iOS `ImageComposeScene` actual); `StoryShareLauncher` (`expect` + Android intent/FileProvider actual + iOS bridge actual); Koin bindings beside the existing `ShareController` binding.
4. `iosApp` — `StoryShareBridge.swift` mirroring `ShareBridge.swift`; thread the lambda through `ContentView.swift` → `MainViewController(...)`.
5. `:feature:feed` — `FeedMealUi.toPlateCard()`; meal-detail share button + intent + VM handler + toast; mapper test; extend VM test.
6. `:feature:stats` — `MealAward.toAwardCard()` + `HeroStats.toStreakCard()`; award + streak share buttons + intents + VM handler; mapper tests; extend VM test.
7. `shared` — wire the digest story's "end → share" CTA (depends on roadmap 2.4 being present).
8. **MANUAL — Android manifest:** add the `<provider>` `FileProvider` entry (`${applicationId}.fileprovider`, `@xml/file_paths`) to `androidApp/src/main/AndroidManifest.xml`.
9. **MANUAL — iOS plist:** add `instagram-stories` to `LSApplicationQueriesSchemes` in the iOS `Info.plist`.
10. Run the full host-test set (per CLAUDE.md "Build, run, test") + `:androidApp:assembleDebug` + `:shared:linkDebugFrameworkIosSimulatorArm64`; quote the green output.
11. **MANUAL — on-device share verification:** build/install on a real device (Android + iPhone), with Instagram installed, walk meal-detail → Share → confirm the Story opens with the card as the background; uninstall-Instagram path → confirm the system sheet appears with the PNG; quote the observation. This is the only verification of the real intent/URL-scheme path (the renderer + launcher actuals are not host-testable).
12. Add a "Recent decisions (2026-06-14) — Shareable story cards" entry to `CLAUDE.md` (what/why/how), following the dated-entry pattern.

## 16. Risks

- **Compose-to-bitmap reliability across CMP versions.** Off-screen capture is not a stable first-class API; `GraphicsLayer.toImageBitmap()` (Android) and `ImageComposeScene` (iOS) can regress between Compose Multiplatform bumps. Mitigation: keep the rendered surface tiny and static (three cards, fixed size, no animation/scroll), gate it behind the on-device smoke (§15 step 11), and pin the CMP version when this lands.
- **Instagram intent variability / availability.** `com.instagram.share.ADD_TO_STORY` and `instagram-stories://share` are Meta-controlled and have changed historically; Instagram may be absent. Mitigation: always `resolveActivity` / `canOpenURL` first and fall back to the system sheet — a share is never gated on Instagram being present, and `StoryShareOutcome` distinguishes the paths for the right toast.
- **FileProvider URI permissions (Android).** Forgetting `FLAG_GRANT_READ_URI_PERMISSION`, or a missing/incorrect `<provider>` authority, opens an empty Story or crashes with `SecurityException`. Mitigation: the manifest provider + the flag are explicit checklist items (§6.1, §15) and surface immediately in the on-device smoke.
- **Large-bitmap memory.** A `1080 × 1920` ARGB bitmap is ~8 MB; decoding the plate + the captured card + the PNG buffer simultaneously can spike memory on low-end devices. Mitigation: decode at the export size (Coil downsampling to the card's plate region), recycle the source bitmap after capture, and encode straight to a `ByteArray` without holding multiple copies.
- **iOS pasteboard size limits.** Very large PNGs on `UIPasteboard` can be dropped or slow; a malformed pasteboard item makes Instagram open with no background. Mitigation: keep the export at the spec'd resolution (well within limits), set a short pasteboard expiration, and fall back to `UIActivityViewController` if the URL scheme can't open.
- **Roadmap 2.4 coupling.** The digest-story entry point (§8.1 row 4) depends on the story player from roadmap 2.4. Mitigation: ship the three cards + the meal/award/streak entries independently; the digest CTA is the last, optional wiring step (§15 step 7) and does not block the rest.
