# Competitive stats redesign — design spec

**Status**: ready for plan
**Date**: 2026-05-21
**Author**: Sebastián (with Claude Code)
**Supersedes (partially)**: `feature/stats` snapshot from `docs/specs/2026-05-16-foodrats-ddd-kmp-design.md` §6 (stats v1). Streak math + ports stay; the screen + metric catalog change.

## 1. Goals

Replace the existing single-window stats screen (30-day rolling, four metrics) with a competitive, animated three-tab screen tuned for tiny closed crews (5-10 people).

1. **Three time windows as tabs**: Semana (ISO Mon–Sun), Mes (natural month), Histórico (last 365 days).
2. **Always-visible hero**: personal streak (with a reactive flame), crew streak, plates today, "you posted today" indicator.
3. **Per-window awards**: best plate (podium card with the photo), most-rated plate, best cook, most prolific, "most criticized" (roast, with humor + thresholds to avoid singling out infrequent posters).
4. **Rich motion**: count-up numbers, pulsing flame, podium reveal, shimmer skeletons, animated tab transitions. No story-mode (Wrapped-style) yet — deferred.

The MVP is a single PR contained to `:feature:stats` + 3 new atoms in `:core:designsystem`.

## 2. Non-goals / deferred

- **Story-mode weekly recap** (HorizontalPager auto-advance, share cards). Considered, deferred — gives the screen a stronger share moment but adds material scope and is not blocked by anything in this redesign.
- **Lottie animations**. The 9-pattern animation set in §6 is fully implementable with stock Compose APIs.
- **Reduced-motion accessibility flag**. Compose Multiplatform has no portable hook into platform a11y settings. MVP ships with `FrAnimations.enabled = true` hardcoded; the rest of the design is parameterised on this so a future setting can be wired without refactor.
- **Top dishes leaderboard**. Removed (see §10) — the by-`DishName` grouping was already unreliable (case-sensitive string match), and it didn't appear on the user's wishlist.
- **Comments / criticism volume as an award**. "Most criticized" is mapped to *lowest average score* with the ≥3-plate threshold, not comment volume. Comments-based awards considered, deferred.
- **Per-crew opt-in for negative awards**. Roast card is on by default for all crews; threshold (≥3 plates) is the only safety guard.
- **Backend changes**. `MealReadPort` is reused as-is. No new ports, no new Firestore collections, no security rule changes.

## 3. Architecture

### 3.1 Data shape

`feature/stats/domain/model/`:

```kotlin
enum class Tab { Week, Month, Historic }

data class StatsWindow(
    val tab: Tab,
    val from: LocalDate,
    val to: LocalDate,
    val days: Int,    // inclusive, = (to - from).days + 1
)

data class StatsSnapshot(
    val hero: HeroStats,
    val week: WindowStats,
    val month: WindowStats,
    val historic: WindowStats?,    // null until the user opens the Historic tab
)

data class HeroStats(
    val personalStreak: Streak,
    val crewStreak: Streak,
    val platesToday: Int,
    val iPostedToday: Boolean,
)

data class WindowStats(
    val window: StatsWindow,
    val totalMeals: Int,
    val avgPerDay: Double,             // totalMeals / window.days
    val bestMeal: MealAward?,          // null when window empty
    val mostVotedMeal: MealAward?,     // null when no rated meals
    val mostProlific: MemberCount?,    // null when window empty
    val bestCook: MemberAverage?,      // null when nobody hits ≥3 plates
    val mostCriticized: MemberAverage?,// null when nobody hits ≥3 plates
)

data class MealAward(
    val mealId: MealId,
    val photoUrl: String,
    val dish: DishName,
    val author: MealAuthor,
    val score: Double,
    val ratingCount: Int,
    val publishedAt: Instant,
    val day: MealDay,
)

data class MemberCount(
    val accountId: AccountId,
    val displayName: String,
    val avatarUrl: String?,
    val mealCount: Int,
)
```

`MemberAverage` already exists (`accountId, displayName, avatarUrl, averageScore, postCount`) and is reused unchanged for `bestCook` + `mostCriticized`.

### 3.2 Ports

`MealReadPort.observeRange(crewId, from, to)` is the only port used. No new ports.

### 3.3 Data loading — hybrid two-observer

| Observer | Range | Subscription lifecycle |
|---|---|---|
| **Current** | `from = min(start_iso_week_of(today), start_of_month_of(today))` → `to = today`. Worst case: 35 days back (when day 1 of the month is a Sunday). | Subscribed whenever `StatsViewModel` is in scope. Feeds `hero`, `week`, `month`. |
| **Historic** | `from = today − 365d` → `to = today`. | Subscribed lazily the first time `state.selectedTab == Tab.Historic`. Once started, stays subscribed for the VM lifetime (cheap — same listener pattern as current). |

Why hybrid: the user goes through Semana/Mes most often (current month is typically <31 days of data); paying for 365 days upfront on every screen open is wasteful. The lazy historic observer is the only place that does a year-wide read, and only when the user asks for it.

ISO week math: `LocalDate.dayOfWeek.isoDayNumber` is 1 (Mon) → 7 (Sun). `start_iso_week_of(d) = d.minus(DatePeriod(days = d.dayOfWeek.isoDayNumber - 1))`. `kotlinx-datetime` provides `dayOfWeek` + `DatePeriod` directly; no extra dependency.

### 3.4 Use case

`ObserveStatsUseCase` is rewritten as **one method exposing one flow**, internally combining two observers:

```kotlin
class ObserveStatsUseCase(
    private val activeCrew: ActiveCrewProvider,
    private val session: SessionProvider,
    private val mealRead: MealReadPort,
    private val clock: Clock,
    private val zone: TimeZone,
) {
    operator fun invoke(
        historicEnabled: Flow<Boolean>,
        epoch: Flow<Int>,
    ): Flow<Result<StatsSnapshot, StatsError>>
}
```

The `historicEnabled` flow is driven by the VM (`state.map { it.selectedTab == Historic }.distinctUntilChanged()`). When false, the inner historic observer is not subscribed → historic stays null. When it flips true, `flatMapLatest` switches in the historic flow alongside the current one. `combine(current, historicOrNull)` produces the final snapshot. Concretely:

```kotlin
operator fun invoke(
    historicEnabled: Flow<Boolean>,
    epoch: Flow<Int>,
): Flow<Result<StatsSnapshot, StatsError>> =
    combine(activeCrew.current, session.current, epoch) { c, s, _ -> c to s }.flatMapLatest { (crewId, sess) ->
        when {
            sess == null -> flowOf(Result.failure(StatsError.Session.NotSignedIn))
            crewId == null -> flowOf(Result.failure(StatsError.Session.NoActiveCrew))
            else -> {
                val today = clock.now().toLocalDateTime(zone).date
                val currentRange = currentRangeFor(today)
                val current = mealRead.observeRange(crewId, MealDay(currentRange.first, zone), MealDay(today, zone))
                val historic = historicEnabled.flatMapLatest { enabled ->
                    if (!enabled) flowOf<List<MealWithRatings>?>(null)
                    else mealRead.observeRange(crewId, MealDay(today.minus(DatePeriod(days = 365)), zone), MealDay(today, zone))
                        .map { r -> when (r) { is Result.Ok -> r.value; is Result.Err -> null } }
                }
                combine(current, historic) { c, h -> compose(c, h, today, sess.accountId) }
            }
        }
    }
```

`compose` builds the snapshot via the pure compute functions (§3.5). A historic-only failure surfaces as `historic = null` in the snapshot; the VM separately tracks `historicError` via a small second flow that mirrors historic observer's error path (see §3.6). Week/Month errors still come through the top-level `Result.failure(StatsError.Read.*)`.

### 3.5 Compute functions (pure)

In `feature/stats/domain/compute/`:

- `computeHeroStats(meals, accountId, today): HeroStats` — uses existing `computePersonalStreak` + `computeCrewStreak` over the *current* meals slice.
- `computeWeekStats(currentMeals, today): WindowStats` — slices `currentMeals` to ISO-week-of-today, calls `computeWindow(slice, window)`.
- `computeMonthStats(currentMeals, today): WindowStats` — slices to month-of-today, calls `computeWindow`.
- `computeHistoricStats(historicMeals, today): WindowStats` — uses full 365-day slice.
- `computeWindow(meals: List<MealWithRatings>, window: StatsWindow): WindowStats` — the shared computation:
  - `totalMeals = meals.size`
  - `avgPerDay = totalMeals.toDouble() / window.days`
  - `bestMeal` = max by `(averageScore desc, ratingCount desc, publishedAt desc)`, requires `ratingCount ≥ 1`.
  - `mostVotedMeal` = max by `(ratingCount desc, averageScore desc, publishedAt desc)`, requires `ratingCount ≥ 1`.
  - `mostProlific` = author with max plate count, tie-break by `displayName asc`.
  - `bestCook` = author with max `averageScore`, filter `plateCount ≥ 3`, tie-break by `plateCount desc, displayName asc`.
  - `mostCriticized` = author with min `averageScore`, filter `plateCount ≥ 3`, tie-break by `plateCount desc, displayName asc`. If only one author qualifies (so `bestCook` and `mostCriticized` would be the same person) → `mostCriticized = null`. The roast card is hidden — we'd rather show no roast than name the same person twice in a crew where one cook dominates.

`computeCrewStreak` continues to take `memberIds = meals.map { it.author.accountId }.distinct()`. This is the same behaviour as today and the same limitation (member set is reconstructed from posters, not from the canonical crew member list). The active-member-list refactor is tracked as tech debt in `CLAUDE.md` and is out of scope here.

### 3.6 ViewModel

```kotlin
data class State(
    val selectedTab: Tab = Tab.Week,
    val snapshot: StatsSnapshot? = null,
    val historicLoading: Boolean = false,
    val historicError: StatsError? = null,
    val error: StatsError? = null,    // current observer error
    val isRefreshing: Boolean = false,
    val epoch: Int = 0,               // bumped by Refresh; upstream flatMapLatest re-subscribes
)

sealed interface Intent {
    data class SelectTab(val tab: Tab) : Intent
    data object Refresh : Intent
}
```

`StatsViewModel : MviViewModel<Intent, State>`. Single source of truth: state lives only in `State` (no parallel `MutableStateFlow`). The `historicEnabled` flow fed into the use case is derived: `state.map { it.selectedTab == Tab.Historic }.distinctUntilChanged()`.

`historicLoading = true` between `SelectTab(Historic)` and the first historic emission. `historicError` is populated only when the historic observer fails (separate from `error`, which covers current week/month).

`Refresh` increments `state.epoch` via the reducer. The VM derives an epoch flow from state (`state.map { it.epoch }.distinctUntilChanged()`) and feeds it into the use case alongside `historicEnabled`. The use case's outer `flatMapLatest` re-subscribes when epoch changes. No parallel `MutableStateFlow` — everything routes through `State`.

Reference pattern: `FeedViewModel` (state-driven derived flows, no parallel `MutableStateFlow`, `currentState` for synchronous reads).

## 4. Metric catalog

| Section | Metric | Week | Month | Historic | Source |
|---|---|---|---|---|---|
| Hero (sticky-ish, outside tabs) | Personal streak (days) | ✓ | ✓ | ✓ | `computePersonalStreak` |
| | Crew streak (days) | ✓ | ✓ | ✓ | `computeCrewStreak` |
| | Plates today (crew) | ✓ | ✓ | ✓ | `currentMeals` filtered to `today` |
| | "You posted today" indicator | ✓ | ✓ | ✓ | `currentMeals.any { author == me && day == today }` |
| Summary | Total plates | ✓ | ✓ | ✓ | `meals.size` |
| | Avg per day | ✓ | ✓ | ✓ | `meals.size / window.days` |
| Podio | Best plate (score, ≥1 rating) — full-bleed photo card | ✓ | ✓ | ✓ | `computeWindow.bestMeal` |
| Highlight | Most-rated plate (rating count) — compact card | ✓ | ✓ | ✓ | `computeWindow.mostVotedMeal` |
| Cocineros | Best cook (avg, ≥3 plates) | ✓ | ✓ | ✓ | `computeWindow.bestCook` |
| | Most prolific (plate count) | ✓ | ✓ | ✓ | `computeWindow.mostProlific` |
| Roast | Most criticized (lowest avg, ≥3 plates, ≠ best cook tie) | ✓ | ✓ | ✓ | `computeWindow.mostCriticized` |

### 4.1 Empty / threshold UX

- **Window empty** (0 plates in the window): the tab content is replaced with a single `FrEmptyState` rendering "Sin platos esta semana — sube el primero" (or "este mes" / "en los últimos 365 días"). Hero + tab row stay visible.
- **Threshold not met** for best-cook / most-criticized (no member at ≥3 plates): the individual award card renders an inline empty state — avatar placeholder + "Faltan N platos para entrar al ranking" where N is the gap to the closest qualifying member.
- **Single qualifying cook**: only `bestCook` renders, `mostCriticized` is hidden (no roast when there's nobody else to compare with).
- **Tie**: tie-break rules in §3.5 are deterministic. No "shared 1st" UI.

## 5. UI

### 5.1 Screen structure

```
FrScreenScaffold
└─ LazyColumn(contentPadding = lg, verticalArrangement = spacedBy(md))
   ├─ item { FrStreakHeroCard(...) }
   ├─ item { FrTodayStripe(...) }
   ├─ stickyHeader { FrStatsTabRow(selected = selectedTab, onSelect = …) }
   ├─ item { AnimatedContent(targetState = selectedTab) { tab -> TabContent(tab, snapshot) } }
```

`TabContent(tab, snapshot)` is its own composable that renders the per-tab `Column` of cards in the order: `FrWindowSummaryCard` → `FrBestPlatePodium` → `FrMostVotedPlate` → `FrCookAwardCard(BestCook)` → `FrCookAwardCard(MostProlific)` → `FrRoastCard`. If the window is empty, it renders `FrEmptyState` instead.

The whole screen scrolls as one `LazyColumn`. The hero is *not* sticky — it scrolls off normally. The tab row uses `stickyHeader` so it stays pinned at the top while the cards scroll.

### 5.2 Domain-aware composables

Live in `feature/stats/presentation/components/`. They take domain types directly (`HeroStats`, `WindowStats`, `MealAward`, `MemberAverage`, `MemberCount`) and resolve their own `StringKey`s.

- **`FrStreakHeroCard(hero: HeroStats)`** — full-width card, ~120dp tall, gradient background using `FrSemanticColors.streakHot` overlaid with a darker overlay for legibility. Left: a `FrFlameIcon` (atom, animated). Right: counter in `displayLarge` (count-up) + label "racha". Second row below: crew streak in `bodyMedium`. When `personalStreak.days == 0`, renders the "no streak yet" copy instead of the counter.
- **`FrTodayStripe(hero: HeroStats)`** — compact pill-shaped chip, ~40dp tall, with "%d platos hoy en la crew" text and a small dot on the right that fills (`FrSemanticColors.success`) when `iPostedToday == true`.
- **`FrStatsTabRow(selected, onSelect)`** — Material 3 `PrimaryTabRow` wrapper with three tabs.
- **`FrWindowSummaryCard(window: WindowStats)`** — two `FrStatTile` side by side: total plates, avg per day. Numbers animate via `animateIntAsState` / `animateFloatAsState` on tab switch (key the animation on `window.tab`).
- **`FrBestPlatePodium(award: MealAward?)`** — full-bleed card, aspect-ratio 1.6:1, `AsyncImage` for the photo, gradient overlay (`Brush.verticalGradient` from transparent to `Color.Black.copy(alpha = 0.6f)`) for legibility. Score in the centre, displayLarge size, count-up. `FrCrownBadge` (atom) top-right. Author + dish name + day in bodyMedium at the bottom. When `award == null` (window empty or no rated meals), the card is omitted entirely.
- **`FrMostVotedPlate(award: MealAward?)`** — row, ~80dp tall, with 64dp square `AsyncImage` thumbnail on the left, dish + author + "%d votantes" stacked on the right. When `award == null`, omitted.
- **`FrCookAwardCard(award: MemberAverage | MemberCount, variant: BestCook | MostProlific)`** — row with `FrAvatar(imageUrl = avatarUrl, initials = …)`, name + metric stacked. Variant determines the metric label + badge colour (gold for `BestCook` via `MaterialTheme.colorScheme.primary`, bronze for `MostProlific` via `MaterialTheme.colorScheme.tertiary`). When the award is null (threshold not met), renders the inline empty state ("Faltan N platos para entrar").
- **`FrRoastCard(award: MemberAverage?)`** — same shape as `FrCookAwardCard` but with greyscaled avatar tint and copy from `MostCriticizedTitle` / `MostCriticizedMetric`. When `award == null` (threshold not met or single qualifying cook), the card is omitted.

### 5.3 New atoms in `:core:designsystem`

Generic (no domain types), each with a catalog entry:

- **`FrCrownBadge`** — small badge icon. Vendored in `FrIcons.kt` via the existing `materialIcon { materialPath { … } }` pattern (`CrownVector`). Catalog: `atom.crownbadge`, scenes: default size, on dark background, on light background.
- **`FrFlameIcon`** — flame vector + scale animation parameter. Catalog: `atom.flameicon`, scenes: static, animating slow, animating fast. The animation is built-in to the composable (parameter: `urgency: Float` in 0f..1f). Vendored `FlameVector` in `FrIcons.kt`.
- **`FrShimmerBox`** — generic shimmer rectangle for skeletons. Implemented with `Brush.linearGradient` + `rememberInfiniteTransition` shifting the gradient offset. Catalog: `atom.shimmerbox`, scenes: rectangle, rounded rectangle, circle.

`accompanist-placeholder` is **not** added — it's Android-only and the project is KMP.

### 5.4 Colours

- Flame: `FrSemanticColors.streakHot` (`#D45A14`, forge orange — already part of Iron & Ember).
- "You posted today" dot: `FrSemanticColors.success`.
- Best-cook badge: `MaterialTheme.colorScheme.primary` (deep olive `#4F6E2B` light / moss `#A8BC85` dark).
- Most-prolific badge: `MaterialTheme.colorScheme.tertiary` (rust / clay).
- Roast avatar tint: `MaterialTheme.colorScheme.onSurfaceVariant` with `alpha = 0.6f`.
- Podio overlay: `Color.Black.copy(alpha = 0.6f)` for the gradient bottom; legibility-only, not a brand colour.

No raw `Color(0x…)` in feature code.

## 6. Animations (Rich)

| Where | Animation | API |
|---|---|---|
| Flame in hero | Scale pulses; period inversely tied to hours-elapsed-today (urgency cue) | `rememberInfiniteTransition` + `animateFloat` with `infiniteRepeatable(tween(800 − hoursElapsed * 30))` |
| Streak counter | Count-up on load / change | `animateIntAsState(targetValue = streak.days, animationSpec = tween(800))` |
| Tab switch | Slide-horizontal + fade | `AnimatedContent(targetState = selectedTab)` with `slideInHorizontally + fadeIn togetherWith slideOutHorizontally + fadeOut` |
| Tab indicator | Indicator slides between tabs | Built-in `PrimaryTabRow` |
| Window summary (total + avg) | Count-up keyed on tab | `animateIntAsState(targetValue = window.totalMeals, label = window.tab.name)`, `animateFloatAsState(targetValue = window.avgPerDay.toFloat())` |
| Best plate photo | Crossfade-in | Coil `AsyncImage` default `placeholder` → image |
| Best plate score | Count-up, slower easing | `animateFloatAsState(targetValue = score.toFloat(), animationSpec = tween(1200, easing = FastOutSlowInEasing))` |
| Cook award metric | Count-up | `animateIntAsState` / `animateFloatAsState` |
| Roast badge | One-shot subtle shake on appear | `animatable.animateTo` triggered in `LaunchedEffect(award)` |
| Loading | Shimmer where `state.snapshot == null` (cards become `FrShimmerBox` of matching shape) | `FrShimmerBox` |
| Pull-to-refresh | Material 3 spinner | `PullToRefreshBox` |

Reduced-motion: every animation in the table is gated behind `FrAnimations.enabled` (a top-level `const val` in `:core:designsystem`). MVP: hardcoded `true`. If a user setting is wired later, only this flag needs to change.

## 7. i18n

`StatsStringKey` is largely rewritten. Entries that disappear: `LeaderboardSection`, `LeaderboardRow`, `TopDishesSection`, `DishTallyRow`, `PostCount`, `MealsConsidered`. Entries that stay: `Title`, `CrewStreakLabel`, `PersonalStreakLabel`, `StreakUnitSingular`, `StreakUnitPlural`, `EmptyHeadline`, `EmptySubtext`, the five error keys.

New entries (selected — the full list lands during implementation):

| Key | en | es |
|---|---|---|
| `TabWeek` / `TabMonth` / `TabHistoric` | "Week" / "Month" / "All time" | "Semana" / "Mes" / "Histórico" |
| `HeroPersonalStreakSingular` / `Plural` | "%1$d day streak" / "%1$d-day streak" | "%1$d día de racha" / "%1$d días de racha" |
| `HeroCrewStreakSingular` / `Plural` | "Crew: %1$d day" / "Crew: %1$d days" | "Crew: %1$d día" / "Crew: %1$d días" |
| `HeroPlatesTodaySingular` / `Plural` | "%1$d plate today" / "%1$d plates today" | "%1$d plato hoy" / "%1$d platos hoy" |
| `HeroIPostedToday` | "You posted today ✓" | "Has subido hoy ✓" |
| `HeroNoStreak` | "No streak yet — post your first plate" | "Sin racha — sube tu primer plato" |
| `WindowEmptyWeek` / `Month` / `Historic` | "No plates this week yet" / etc. | "Sin platos esta semana" / etc. |
| `SummaryTotalPlatesLabel` | "Plates" | "Platos" |
| `SummaryAvgPerDayLabel` | "Per day" | "Por día" |
| `BestPlateTitle` | "Top plate" | "Mejor plato" |
| `BestPlateScoreFormat` | "%1$s ★" | "%1$s ★" |
| `BestPlateAuthorFormat` | "by %1$s" | "de %1$s" |
| `MostVotedPlateTitle` | "Most rated plate" | "Plato más votado" |
| `MostVotedPlateVotersSingular` / `Plural` | "%1$d voter" / "%1$d voters" | "%1$d votante" / "%1$d votantes" |
| `CooksSectionTitle` | "Cooks" | "Cocineros" |
| `BestCookTitle` | "Top cook" | "Mejor cocinero" |
| `BestCookMetricFormat` | "%1$s ★ avg · %2$d plates" | "%1$s ★ media · %2$d platos" |
| `MostProlificTitle` | "Most prolific" | "Más prolífico" |
| `MostProlificMetricFormat` | "%1$d plates" | "%1$d platos" |
| `RoastSectionTitle` | "Roast" | "Roast" |
| `MostCriticizedTitle` | "Most criticized" | "El más criticado" |
| `MostCriticizedMetricFormat` | "%1$s ★ avg · ouch" | "%1$s ★ media · ay" |
| `CookAwardNeedsMoreSingular` / `Plural` | "%1$d more plate to qualify" / plural | "Falta %1$d plato para entrar" / "Faltan %1$d platos para entrar" |

"Roast" is kept as a borrowed English term in es-ES — deliberate voice/brand choice. All glyphs (`★`, `·`, `✓`) live inside the resource strings, never assembled in Kotlin.

## 8. Errors

`StatsError` is unchanged:

```
StatsError.Session.NotSignedIn / NoActiveCrew
StatsError.Read.Unauthorized / CrewNotFound / Unavailable
```

`StatsErrorToStringKey` mapper + `StatsErrorToStringKeyTest` are unchanged.

Per-window separation: `State.error` only carries failures from the current observer (affects Week + Month). `State.historicError` carries failures from the historic observer. A failure in one does **not** hide the other tabs — the affected tab renders `FrErrorBanner` inline at the top of its content.

## 9. Testing

| Test | Layer | Covers |
|---|---|---|
| `WindowComputeWeekTest` | commonTest | ISO-Mon-start; week crossing month boundary; empty window; tie-break in `bestMeal`; ≥3-plate threshold for `bestCook` + `mostCriticized`; `bestCook == mostCriticized` exclusion in single-qualifier crews. |
| `WindowComputeMonthTest` | commonTest | Month start day; 30 vs 31 days; avg-per-day division. |
| `WindowComputeHistoricTest` | commonTest | 365-day slice; empty; multi-cook ranking. |
| `HeroComputeTest` | commonTest | `platesToday` count; `iPostedToday`; combined with existing streak compute tests. |
| `ObserveStatsUseCaseTest` | commonTest | Rewritten. Fake `MealReadPort` + `Clock` + `ActiveCrewProvider` + `SessionProvider`. Verifies: current observer feeds week+month; historic observer not subscribed until `historicEnabled` flips true; per-window error propagation; `epoch` increment triggers a re-emission. Uses Turbine + `UnconfinedTestDispatcher`. |
| `StatsViewModelTest` | commonTest | `SelectTab` transitions; lazy historic load (`historicLoading` true then false on first emission); per-window error; `Refresh` intent. `expectMostRecentItem()` on terminal states. |
| `StatsErrorToStringKeyTest` | commonTest | Existing exhaustiveness check; updated to cover any new key (none expected in MVP). |
| `FrStreakHeroCardTest` | androidHostTest | Renders the counter; renders "no streak" copy when days == 0; presence of flame node. No animation assertion. |
| `FrBestPlatePodiumTest` | androidHostTest | Renders photo + score + author when award present; renders nothing when award null. |
| `FrCookAwardCardTest` | androidHostTest | Variant `BestCook` vs `MostProlific` render different titles; null award renders the "needs N more" empty state. |
| `FrRoastCardTest` | androidHostTest | Renders when award present; renders nothing when null. |
| `FrShimmerBoxTest` | androidHostTest | Renders a node with the shimmer background brush. |

Patterns follow the existing project conventions: `createComposeRule()` v2, Robolectric `sdk=33` with display qualifiers, Turbine + `UnconfinedTestDispatcher`, `expectMostRecentItem()` to coalesce intermediate emissions.

Konsist (`core/domain/src/androidHostTest/.../KonsistRulesTest.kt`) is not affected — no domain layer touched.

## 10. Deletions

Files to delete in the implementation PR:

- `feature/stats/src/commonMain/.../domain/model/Leaderboard.kt`
- `feature/stats/src/commonMain/.../domain/model/DishTally.kt`
- `feature/stats/src/commonMain/.../domain/compute/Leaderboard.kt`
- `feature/stats/src/commonMain/.../domain/compute/TopDishes.kt`
- `feature/stats/src/commonMain/.../presentation/components/FrDishTallyRow.kt`
- `feature/stats/src/commonMain/.../presentation/components/FrLeaderboardRow.kt`
- `feature/stats/src/commonTest/.../domain/compute/LeaderboardTest.kt`
- `feature/stats/src/commonTest/.../domain/compute/TopDishesTest.kt`

String resource entries to remove from `feature/stats/src/commonMain/composeResources/values/strings.xml` + `values-es/strings.xml`:

- `stats_top_dishes_section`, `stats_dish_tally_row`, `stats_leaderboard_section`, `stats_leaderboard_row`, `stats_post_count`, `stats_meals_considered`

Matching `StatsStringKey` enum entries are removed in lockstep (the `*ErrorToStringKey` exhaustiveness test does not catch removed-but-unreferenced keys; visual review).

## 11. Migration

None. Stats does not persist data locally. The screen re-derives everything from `MealReadPort` (which reads `meals/{crewId}/{...}` + ratings live). Old meals' Firestore documents already contain everything needed (author, score, ratings, day, photoUrl, dish, description); no field is added or renamed.

## 12. Open sub-decisions deferred to implementation

1. **Pull-to-refresh placement**: above the LazyColumn or wrapping it. Material 3 `PullToRefreshBox` documentation will resolve this during implementation.
2. **`FrFlameIcon` urgency curve**: linear vs cubic interpolation between "morning slow" and "late-night fast". Pick the one that *feels right* by eye in the catalog story; not load-bearing.
3. **Roast card shake amplitude**: visual tuning. Default to a 2dp horizontal jitter over 200ms; adjust if it feels too aggressive on small screens.
4. **Best cook label when window is the user themself**: "Top cook" reads odd when it's you. Acceptable for MVP; a "Top cook · that's you!" variant is a polish iteration.
5. **Catalog entries for the three new atoms**: scene list in §5.3 is the starting point; implementation may add edge-case scenes (zero state, max scale, etc.).

## 13. PR scope

One PR. Touches: `:feature:stats` (most of it rewritten), `:core:designsystem` (three new atoms + their catalog entries). No changes to `:core:domain`, `:core:data`, `:core:i18n`, or any other feature.

## 14. References

- [Healthy Iron & Ember design system (2026-05-20)](2026-05-19-healthy-design-system-design.md) — palette + `FrSemanticColors` source of truth.
- [FoodRats DDD/KMP design (2026-05-16)](2026-05-16-foodrats-ddd-kmp-design.md) §6 — stats v1 (this spec supersedes).
- [Meal description replaces tags (2026-05-21)](2026-05-21-meal-description-replaces-tags-design.md) — context for why top-dishes was already wobbly.
- Duolingo: *Animating the Duolingo Streak*, *Leagues mechanics*.
- Apple HIG: *Activity Rings*.
- Strava: *Your Year in Sport*.
- Beli: design-critique writeup (IXD@Pratt).
- Coil 3 + Compose Multiplatform navigation typed routes — existing project conventions.
