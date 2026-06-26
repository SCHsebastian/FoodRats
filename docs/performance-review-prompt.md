# FoodRats — Performance Review Prompt

Paste the block below into Claude Code (Opus, ultracode/high effort) to run a full performance pass.
It is pre-loaded with the app's real hotspots (verified against the code on 2026-06-26) so the
reviewer starts from evidence, not a blank page. Re-run it whenever the UI or read paths change.

---

## PROMPT (copy from here)

You are doing a **performance review** of FoodRats — a Compose Multiplatform (Android + iOS) closed-group
meal-sharing app. The goal: make the app **go like a charm** — 60/120 fps scrolling with no jank, fast cold
start, low memory, and low Firestore/Coil egress. This is a *measure-then-fix* review, not a guesswork pass.

### Non-negotiable constraints (a fix that breaks these is not a fix)
- Obey every rule in `CLAUDE.md` and `~/.claude/rules/*`: custom `Result<T,E>`, sealed-interface errors,
  exactly one `withContext(dispatchers.io)` per public repo method (zero in use cases/ViewModels), features
  never depend on features (cross-context via `:core:domain` ports), no Firebase/Android/Compose in `:core:domain`,
  all user-visible text via `resolve(StringKey)`, MVI single source of truth in `MviViewModel.State`.
- **Verify before claiming done.** Every fix must be paired with a verification command whose output you quote.
  Acceptable: the relevant host-test task, `:androidApp:assembleDebug`, `:shared:linkDebugFrameworkIosSimulatorArm64`,
  a Macrobenchmark run, or an on-device measurement (`gfxinfo` / Perfetto / recomposition counts). "Looks faster"
  is not allowed.
- Don't regress correctness or the offline-first outbox. Don't introduce a linter/formatter.

### Method (do this in order)
1. **Measure first.** Establish a baseline before touching anything:
   - Cold start: run the existing Macrobenchmark — `./gradlew :baselineprofile:pixel6Api34BenchmarkAndroidTest`
     (or `connectedBenchmarkAndroidTest` on a device) — and quote the `StartupTimingMetric` median.
   - Jank/frames while scrolling the feed: `adb shell dumpsys gfxinfo es.schsebastian.foodrats reset`, scroll
     a populated feed, then read back `dumpsys gfxinfo` (janky-frames %, 95th/99th percentile). Or capture a
     Perfetto trace and look for long `Choreographer#doFrame` / `RecomposeScope` slices.
   - Recomposition counts: use Layout Inspector recomposition counts (or a `Modifier.recompositionHighlighter`)
     on the feed bento and comments list. Note which nodes recompose on unrelated state changes.
   - Firestore/egress: enable debug logging or use the Firebase console Usage tab; note reads per feed open,
     per day-nav, per meal-detail open, and at cold start (eager singles).
3. **Classify** every finding as `impact: high|med|low`, `effort: S|M|L`, `confidence: high|med|low`. Fix
   high-impact/high-confidence first; list (don't blindly apply) the speculative ones.
4. **Fix on a branch**, verify each fix with a quoted command, and re-measure against the baseline to prove the win.
5. **Write the report** to `docs/session/<today>-performance/REPORT.md`: baseline numbers, each finding
   (file:line, why it costs, the fix, before/after measurement), and anything deferred with the reason.

### Known suspect areas — verify each against current code; some may already be fixed
These were found in recon; confirm they still exist and measure before/after. Don't assume — a few are bounded
and may not be worth touching.

**A. UI rendering / Compose (highest-signal for "smoothness")**
1. **Feed loads FULL-res plates into 150–230dp bento tiles** with no `.size()` decode cap and no central
   downsample policy — `feature/feed/.../FeedMealUi.kt:173` (`bentoImageUrl` returns the full `photoUrl`),
   consumed at `feature/feed/.../FeedScreen.kt:474-482`; loader has no global size policy
   (`core/data/.../image/ImageLoaderSetup.kt`). Decoding multi-MP bitmaps into small slots burns memory + GC +
   decode time. Consider per-target `.size(...)` hints (Coil sizes to the node, but the *source* still decodes
   large without a hint) or a sized derivative. Measure memory cache churn and decode time.
2. **Feed / meal-detail / stats render eagerly in `Column(verticalScroll)`, not Lazy** —
   `FeedScreen.kt:159`, `MealDetailScreen.kt:392/896`, `StatsScreen.kt:165`; the bento `FrBentoGrid`
   (`core/designsystem/.../structural/FrBentoGrid.kt:60`) is a plain Column/Row. A 30-meal day composes and
   decodes every tile at once. Evaluate lazy virtualization (`LazyColumn`/`LazyVerticalGrid`/`LazyVerticalStaggeredGrid`
   with stable `key=`) — but weigh it against the bento's asymmetric custom layout (may need a custom lazy layout).
3. **`FeedState.meals: List<FeedMealUi>` is unstable** (`feature/feed/.../FeedContract.kt:21`; `FeedMealUi`
   holds `List<RaterVoteUi>`/`List<String>`). `FeedScreen` reads the whole `state`, so the feed subtree can't
   skip on unrelated changes (toasts, `syncPending`). Consider `kotlinx.collections.immutable` (ImmutableList)
   + `@Immutable`/`@Stable`, and pass only the slice each composable needs. Verify with recomposition counts.
4. **Work done directly in composition, re-run every recomposition** — `FeedScreen.kt:126-127`
   (`.filter{}.maxByOrNull{}` to pick the floor hero), `:414-415` (`.sortedByDescending{}.mapIndexed{}` builds a
   fresh `List<FrBentoItem>` with composable lambdas), `:438-439` (per-tile lambda allocation), `:478`
   (`stablePlateRequest()` is `@Composable` but not `remember`ed — new `ImageRequest` every pass:
   `feature/feed/.../StablePlateRequest.kt:28`). Hoist into `remember(key)`.
5. **Ingredient picker re-filters the catalog inside the LazyColumn item builder** every recomposition —
   `feature/ingredient/.../SelectIngredientsScreen.kt:137,162` (O(categories × catalog)). Move the grouping/filter
   into a `remember(...)` keyed on search+detected.
6. **Full-screen `Modifier.blur` floor** (`core/designsystem/.../structural/FrMediaFloor.kt:80-81`, Feed requests
   `StructuralBlur.Heavy`) + **large per-tile soft shadows** (`FrGlassTile.kt:122`, up to 30dp in dark) are the
   GPU-heavy effects. The over-scale is correctly gated to blur-only and there's no per-tile backdrop blur (good).
   Measure GPU frame time with/without; consider a pre-blurred/static floor or cheaper shadow radii if it janks
   on mid-tier devices (min SDK 30 — many such devices in the base).
7. **Per-item entrance animations** allocate a spring per newly-composed row — `frRiseIn`
   (`core/designsystem/.../motion/FrMotion.kt:38`) on every ingredient/crew-settings row; `stampIn` 3D rotation
   per passport cell (`PassportScreen.kt:172`). Fine for short lists; confirm they don't fire during fling.
8. **Avatars decode full bytes into 24–40dp circles** with no size hint, and `FrAvatar` uses
   `SubcomposeAsyncImage` (subcomposition per avatar) — `core/designsystem/.../atoms/FrAvatar.kt:49`,
   `FrGlassAvatar.kt` callers in feed/detail/stats. Add size hints; prefer `AsyncImage` where subcomposition
   isn't needed.
9. **Always-on bottom-bar capture pulse** — `shared/.../navigation/MainBottomBar.kt:202-220` runs two
   `animateFloat`s continuously while `pulsing`; confirm it's gated off when not needed (it's present on every
   primary screen).

**B. Data / IO / egress (cost + battery + perceived speed)**
1. **`AppPreferences.observe` has no `distinctUntilChanged`** — `core/data/.../datastore/AppPreferences.kt:13` —
   `store.data.map { it[key] }` re-emits on *every write to any key* in the shared store. Only
   `ActiveCrewLocalStore` compensates locally. Push `distinctUntilChanged()` into `observe` itself; this fans
   out redundant state updates → recompositions app-wide.
2. **Ingredient catalog is a full-collection *live listener*** (226 docs) — `feature/ingredient/.../
   IngredientFirestoreDataSource.kt:14`. DataStore cache covers cold start, but the persistent listener
   re-downloads the set on any single change and stays warm app-wide. Consider a one-shot `get()` + cache, or
   tear the listener down when no picker is open.
3. **`observeMany` opens one `accounts/{id}` listener per distinct author + rater** —
   `core/domain/.../AccountReadPort.kt:27` (`combine(ids.map { observe(it) })`), driven by feed enrichment
   (`feature/meal/.../FirebaseMealRepository.kt:136-150`). Per-uid memo caps duplicates within a screen, but every
   rater across 30 days becomes a live listener. Bound it (visible window only) or batch via `whereIn`.
4. **Stats combines 6 live flows + reads a 365-day Historic range** — `feature/stats/.../ObserveStatsUseCase.kt:107`
   and `:89`; any one emission re-runs `compose()` over the full window. Check the recompute cost and whether the
   365-day read can be narrowed/cached.
5. **Unbounded comments & reactions subcollection listeners** (no `.limit()`) —
   `feature/meal/.../CommentFirestoreDataSource.kt:18`, `ReactionFirestoreDataSource.kt:22`. Add a sane `.limit()`
   + pagination for hot threads.
6. **`MealLocalStore.write` runs a per-meal ratings re-query inside the sync transaction** —
   `feature/meal/.../MealLocalStore.kt:236-239` — one `selectRatingsForMeals` per meal per snapshot. Consider a
   set-based diff instead of per-row.
7. **`OutboxLocalStore` queries are synchronous with no dispatcher** — `core/data/.../OutboxLocalStore.kt:64,187` —
   correctness relies on every caller going through the repo's `withContext(io)`. Audit for any direct off-Main caller.
8. **`BackgroundMealUploadCoordinator` uses `SharingStarted.Eagerly`** — `:95` — the only eager sharing; confirm it
   must stay hot for the app lifetime.

**C. Startup / cold start**
1. **`FirebaseApp.initializeApp` + the full Koin graph build run synchronously on the main thread before first
   frame** — `androidApp/.../FoodRatsApplication.kt:51` and `:58-78`. Eager singles wire on-main but push real IO
   to `Dispatchers.Default` (good). Confirm nothing in an eager-single constructor blocks; consider App Startup
   ordering / deferring non-critical singles.
2. **Baseline profile only covers Splash→SignIn** (unauthenticated) — `baselineprofile/.../BaselineProfileGenerator.kt`.
   The authenticated feed/composer path — the hot path — isn't profiled. Extend the journey if test creds allow;
   this is often the single biggest startup/scroll win for free.
3. **iOS bundles `food101.tflite` twice (~2 MB double-count)** — `iosApp/iosApp/food101.tflite` *and* the
   Compose-resources copy both land in the `.app`. De-dupe to one source.
4. MediaPipe classifier init is correctly lazy + kept warm (Android `MediaPipeMealClassifier.android.kt`, iOS Swift
   static). CPU delegate only — note whether a GPU/NNAPI delegate would help if classification latency shows up.

### Output
A prioritized `REPORT.md` with baseline vs. after numbers, plus the applied diffs on a branch. Lead with the 3–5
fixes that moved a measured metric. Be honest about anything you couldn't measure in this environment and say why.

## (end of prompt)

---

### How to run it as a multi-agent workflow (optional, ultracode)
For a deeper pass, the three review dimensions (UI rendering · data/IO · startup) are independent — fan them out
as parallel finder agents, then have a verify stage re-measure each proposed fix before it's accepted, and a
synthesis stage write the report. The dimension list above maps 1:1 to the finder prompts.
