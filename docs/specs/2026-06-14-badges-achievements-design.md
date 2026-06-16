# Badges & Achievements: a deterministic, consent-free achievements engine

**Date:** 2026-06-14
**Status:** Design — pending implementation

## 1. Decision

Add a new feature module `:feature:achievements` that hosts an achievements engine. Most badges are **deterministic** from data the app already owns (the meal history for the active crew, the account, the active streak) — so they are **derived client-side** by a pure `AchievementEvaluator`. Only the **unlock timestamps** are persisted (Firestore `accounts/{uid}/achievements/{achievementId}`), so "earned on" dates survive reinstall and sync across devices. The achievement **catalog** is a compile-time constant in the feature, not server data.

Two structural choices are made deliberately, with defaults:

- **Client-derived evaluation + server-persisted unlock timestamps** (default), not server-computed unlocks. Evaluation reuses the same `MealReadPort` window stats already drives; persisting only the unlock instant keeps the write surface tiny and idempotent.
- **Both personal- and crew-scoped achievements** (default). The criterion itself declares its `AchievementScope`, so the evaluator does not need scope branching outside the criterion taxonomy.

This is foundational. The roadmap's **cuisine-passport** and **ingredient-bingo** features plug in as additional `AchievementCriterion` leaves + catalog entries; **streak milestones** reuse the same engine. A `CuisineVariety` criterion is declared now as a forward-hook (always locked until the cuisine-passport spec lands and supplies the cuisine signal).

## 2. Motivation

The app already computes rich per-crew signals (streaks, prolific cooks, best plate, most-used ingredient) in `:feature:stats`, but nothing rewards a member for crossing a threshold. Badges turn the existing data into a progression surface — the cheapest possible engagement lever because the data is already on-device.

Building this as a shared engine (rather than ad-hoc per-feature badges) means cuisine-passport and ingredient-bingo do not each re-implement "evaluate threshold → persist unlock → render earned/locked → celebrate". They declare a criterion and a catalog row.

Trade-offs accepted:

- **No server-authoritative anti-cheat.** A client could fabricate an unlock write. Acceptable: this is a closed-group beta with no leaderboard prize; the Firestore rule restricts writes to the owner's own subcollection, which is the same trust model as the rest of the app.
- **Re-evaluation cost grows with history.** Mitigated by evaluating over the same bounded windows stats uses and debouncing (see §7, §16).
- **Retroactive unlocks have no real "earned on".** A member who already has 60 plates when the feature ships unlocks "10 plates" / "50 plates" at first evaluation; their timestamp is "now", not the true historical moment (see §16).

## 3. Scope

In scope: the new `:feature:achievements` module (domain + data + presentation); a small set of new `:core:domain` types it depends on (`AchievementProgressPort` + its error); a new `Fr*` badge atom in `:core:designsystem` with catalog entries; the `accounts/{uid}/achievements` Firestore rule; one new `AnalyticsEvent` leaf; en/es strings; Koin wiring in `settings.gradle.kts` + the `shared` aggregator; and all tests.

Out of scope: server-side aggregation or Cloud-Functions verification of unlocks; push notifications on unlock (a future hook — the unlock effect is in-app only for the MVP); the cuisine-passport and ingredient-bingo features themselves (only their plug-in seams are defined here); a "share badge" export; backfilling true historical unlock dates.

## 4. Module + dependency graph

New module `:feature:achievements`, mirroring `:feature:stats` (read-only consumer of ports; no dependency on `:feature:meal` or any other feature).

**Ports consumed** (all from `:core:domain`, none from sibling features):

| Port | Path | Used for |
|---|---|---|
| `MealReadPort.observeRange` | `core/domain/src/commonMain/kotlin/es/schsebastian/foodrats/core/domain/meal/MealReadPort.kt` | The meal history the evaluator scores against |
| `ActiveCrewProvider.current` | `core/domain/.../crew/ActiveCrewProvider.kt` | The crew to evaluate (`Flow<CrewId?>`) |
| `SessionProvider.current` | `core/domain/.../session/SessionProvider.kt` | The current `Session.accountId` (whose achievements these are) |
| `Clock` + `TimeZone` | `core/domain/.../time/Clock.kt` | `today` + window bounds + the unlock instant |
| `AchievementProgressPort` | **new**, `core/domain/.../achievement/AchievementProgressPort.kt` | Read/write unlock timestamps |
| `AnalyticsPort` | `core/domain/.../analytics/AnalyticsPort.kt` | Fire `achievement_unlocked` |
| `CrashReporter` | `core/domain/.../telemetry/CrashReporter.kt` | Non-fatal on a persist failure |

`build.gradle.kts` mirrors `feature/stats/build.gradle.kts` exactly, including JVM target. The persistence path is Firebase (GitLive), so **this module targets `JvmTarget.JVM_17`** and adds the Firebase BOM to `androidMain` (per CLAUDE.md "Build conventions"); stats is JVM 11 only because it has no Firebase — achievements does, so it follows the `:feature:crew`/`:feature:meal` JVM-17 + BOM rule. `commonMain` deps: `projects.core.domain`, `projects.core.data`, `projects.core.designsystem`, `projects.core.presentation`, `projects.core.i18n`, `libs.bundles.feature.ui`, `libs.bundles.kotlinx.common`, the GitLive `firebase-firestore` artifact (as `:feature:crew` declares it). `commonTest`: `libs.bundles.feature.test`. `androidHostTest`: `libs.bundles.feature.hosttest`.

```
:feature:achievements
  ├─(impl)→ :core:domain        // ports, Result, value objects, AnalyticsEvent
  ├─(impl)→ :core:data          // DispatcherProvider, Firestore factory
  ├─(impl)→ :core:designsystem  // FoodRatsTheme, LocalFrSemanticColors, FrBadge
  ├─(impl)→ :core:presentation  // MviViewModel base
  └─(impl)→ :core:i18n          // resolve(StringKey)
        ✗ NO dependency on :feature:* (cross-feature ban; reads go through ports)
```

## 5. Domain (`:feature:achievements/.../domain`)

### 5.1 Value objects

```kotlin
// feature/achievements/.../domain/model/AchievementId.kt
@JvmInline
value class AchievementId(val value: String)   // import kotlin.jvm.JvmInline
```

`AchievementId` is the compile-time catalog key (e.g. `AchievementId("first_plate")`). Unlike `MealId`/`CrewId` it is not user-supplied — it is a constant — so it takes no `of(): Result<…>` factory; it is constructed directly from the catalog.

```kotlin
// feature/achievements/.../domain/model/AchievementScope.kt
enum class AchievementScope { Personal, Crew }
```

`Personal` criteria evaluate over the current member's own meals only; `Crew` criteria evaluate over the whole crew. (Modeled as an enum, not a sealed-interface error — this is a closed presentation dimension with no payload, matching `MealSlot`.)

```kotlin
// feature/achievements/.../domain/model/AchievementTier.kt
enum class AchievementTier { Bronze, Silver, Gold }
```

Tier is optional metadata for visual treatment (a 10/50/100 family shares a concept across three tiers). It does not affect evaluation.

### 5.2 `AchievementCriterion` — the sealed taxonomy (the heart of the engine)

Each leaf carries its threshold as a typed field and declares its `scope`. Adding cuisine-passport / ingredient-bingo / new streak milestones = adding leaves here. **Never an enum** — leaves carry payloads.

```kotlin
// feature/achievements/.../domain/model/AchievementCriterion.kt
sealed interface AchievementCriterion {
    val scope: AchievementScope

    /** First published plate. */
    data object FirstPlate : AchievementCriterion { override val scope = Personal }
    /** Personal lifetime plate count ≥ [target] (10 / 50 / 100). */
    data class MealCount(val target: Int) : AchievementCriterion { override val scope = Personal }
    /** ≥ [target] DISTINCT user-confirmed ingredient slugs across the member's plates. */
    data class IngredientVariety(val target: Int) : AchievementCriterion { override val scope = Personal }
    /** Member's current personal streak ≥ [days] (7 / 30 / 100). */
    data class PersonalStreak(val days: Int) : AchievementCriterion { override val scope = Personal }
    /** Crew's current shared streak ≥ [days] (7 / 30). */
    data class CrewStreak(val days: Int) : AchievementCriterion { override val scope = Crew }
    /** ≥ [target] plates whose slot is Breakfast. */
    data class EarlyBird(val target: Int) : AchievementCriterion { override val scope = Personal }
    /** ≥ [target] plates whose slot is Dinner. */
    data class NightOwl(val target: Int) : AchievementCriterion { override val scope = Personal }
    /** Highest average score over a window (min 3 plates), i.e. "best cook". */
    data object BestCook : AchievementCriterion { override val scope = Crew }

    /** Forward-hook (§15): ≥ [target] distinct cuisines. Always LOCKED until cuisine-passport ships. */
    data class CuisineVariety(val target: Int) : AchievementCriterion { override val scope = Personal }
}
```

(`Personal`/`Crew` above are `AchievementScope.Personal`/`.Crew`, imported.)

### 5.3 `Achievement` — a catalog row

```kotlin
// feature/achievements/.../domain/model/Achievement.kt
data class Achievement(
    val id: AchievementId,
    val titleKey: AchievementStringKey,
    val descriptionKey: AchievementStringKey,
    val iconKey: AchievementIcon,        // presentation enum → an FrIcons vector
    val criterion: AchievementCriterion,
    val tier: AchievementTier? = null,
)
```

`titleKey`/`descriptionKey` are `StringKey`s (i18n, never raw strings). `iconKey` is a presentation enum resolved to a vector in the design system (so the domain `Achievement` holds no Compose type). The full catalog is `§9`.

### 5.4 Evaluation inputs + outputs

The evaluator is **pure** (no I/O, no Clock, no Flow). The ViewModel resolves the signals from ports and feeds them in; this keeps every criterion unit-testable against a `List<MealWithRatings>`.

```kotlin
// feature/achievements/.../domain/model/AchievementSignals.kt
/** Everything the evaluator needs, already resolved from ports by the caller. */
data class AchievementSignals(
    val accountId: AccountId,
    val crewMeals: List<MealWithRatings>,   // the active crew's window (whole crew)
    val personalStreakDays: Int,            // derived; see §7
    val crewStreakDays: Int,                // derived; see §7
    val bestCookAccountId: AccountId?,      // who currently leads avg score (min 3 plates)
)
```

```kotlin
// feature/achievements/.../domain/model/AchievementStatus.kt
data class AchievementProgress(val current: Int, val target: Int) {
    val isMet: Boolean get() = target > 0 && current >= target
}

data class AchievementStatus(
    val achievement: Achievement,
    val progress: AchievementProgress,   // boolean criteria use target=1, current 0/1
    val unlockedAtEpochMs: Long?,        // null = locked OR met-but-not-yet-persisted
)
```

### 5.5 `AchievementEvaluator` — pure

```kotlin
// feature/achievements/.../domain/AchievementEvaluator.kt
class AchievementEvaluator {

    /** Pure: same inputs → same statuses. No persistence; unlock dates are merged in by the caller. */
    fun evaluate(
        catalog: List<Achievement>,
        signals: AchievementSignals,
    ): List<AchievementStatus> = catalog.map { achievement ->
        AchievementStatus(
            achievement = achievement,
            progress = progressFor(achievement.criterion, signals),
            unlockedAtEpochMs = null,   // caller overlays persisted timestamps (§6.3)
        )
    }

    private fun progressFor(
        criterion: AchievementCriterion,
        s: AchievementSignals,
    ): AchievementProgress {
        val mine = s.crewMeals.filter { it.meal.author.accountId == s.accountId }
        return when (criterion) {
            AchievementCriterion.FirstPlate ->
                AchievementProgress(if (mine.isNotEmpty()) 1 else 0, 1)
            is AchievementCriterion.MealCount ->
                AchievementProgress(mine.size, criterion.target)
            is AchievementCriterion.IngredientVariety ->
                // user-CONFIRMED only — detectedIngredients (AI, advisory) are excluded.
                AchievementProgress(
                    mine.flatMap { it.meal.ingredients }.distinct().size,
                    criterion.target,
                )
            is AchievementCriterion.PersonalStreak ->
                AchievementProgress(s.personalStreakDays, criterion.days)
            is AchievementCriterion.CrewStreak ->
                AchievementProgress(s.crewStreakDays, criterion.days)
            is AchievementCriterion.EarlyBird ->
                AchievementProgress(mine.count { it.meal.slot == MealSlot.Breakfast }, criterion.target)
            is AchievementCriterion.NightOwl ->
                AchievementProgress(mine.count { it.meal.slot == MealSlot.Dinner }, criterion.target)
            AchievementCriterion.BestCook ->
                AchievementProgress(if (s.bestCookAccountId == s.accountId) 1 else 0, 1)
            is AchievementCriterion.CuisineVariety ->
                AchievementProgress(0, criterion.target)   // forward-hook: always locked (§15)
        }
    }
}
```

The `when` is exhaustive over the sealed taxonomy — a new criterion leaf forces a compile error here, which is the intended guard. `MealSlot` is `Breakfast`/`Lunch`/`Dinner` (`core/domain/.../meal/MealSlot.kt`); `MealWithRatings.meal.ingredients` is the user-confirmed list; `detectedIngredients` is deliberately not read (matches the existing rule in `feature/stats/.../domain/compute/ComputeWindow.kt`, which counts `meal.ingredients` only).

## 6. Persistence

### 6.1 `AchievementProgressPort` — declared in `:core:domain`

Lives in `:core:domain` (not in the feature) because it is a cross-context contract the feature implements — same placement rationale as `MealReadPort`. Domain declares the port; the feature's data layer implements it.

```kotlin
// core/domain/src/commonMain/kotlin/.../core/domain/achievement/AchievementProgressPort.kt
package es.schsebastian.foodrats.core.domain.achievement

interface AchievementProgressPort {
    /** All persisted unlocks for [accountId], keyed by the raw achievement id. */
    fun observeUnlocks(accountId: AccountId): Flow<Result<Map<String, Long>, AchievementProgressError>>

    /**
     * Idempotent: writes unlock timestamps only for ids NOT already present, in one batch.
     * A no-op (returns Ok) when [newlyUnlocked] is empty or all ids already exist.
     */
    suspend fun recordUnlocks(
        accountId: AccountId,
        newlyUnlocked: Map<String, Long>,
    ): Result<Unit, AchievementProgressError>
}
```

```kotlin
// core/domain/.../achievement/AchievementProgressError.kt  (sealed, never enum)
sealed interface AchievementProgressError {
    data object Unauthorized : AchievementProgressError
    data object Unavailable  : AchievementProgressError
}
```

The map is keyed by the raw `String` id (the Firestore document id) rather than `AchievementId` so the port stays free of feature types — the feature maps `String → AchievementId` against its catalog on read (unknown ids from a future app version are ignored, mirroring the "drop-on-read" stance elsewhere).

### 6.2 Firestore shape + the single-`withContext` data method

Document path: `accounts/{uid}/achievements/{achievementId}`. One doc per unlocked achievement; locked achievements have **no** document (absence = locked). DTO:

```kotlin
// feature/achievements/.../data/firebase/AchievementUnlockDto.kt
@Serializable
data class AchievementUnlockDto(val unlockedAtEpochMs: Long = 0L)
```

Repository implements the port with **exactly one** `withContext(dispatchers.io)` per public method (the codebase rule; modeled on `feature/crew/.../data/repository/FirebaseCrewRepository.kt`):

```kotlin
// feature/achievements/.../data/repository/FirebaseAchievementRepository.kt
class FirebaseAchievementRepository(
    private val firestore: FirebaseFirestore,        // GitLive, injected
    private val dispatchers: DispatcherProvider,
    private val errorMapper: AchievementErrorMapper, // vendor-exception → typed error
) : AchievementProgressPort {

    private fun col(uid: String) =
        firestore.collection("accounts").document(uid).collection("achievements")

    override fun observeUnlocks(accountId: AccountId) =
        col(accountId.value).snapshots.map { snap ->
            runCatching {
                snap.documents.associate { it.id to it.data<AchievementUnlockDto>().unlockedAtEpochMs }
            }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { Result.failure(errorMapper.map(it)) },
            )
        }   // a cold Flow; no withContext on the observe path (matches MealReadPort impls)

    override suspend fun recordUnlocks(
        accountId: AccountId,
        newlyUnlocked: Map<String, Long>,
    ): Result<Unit, AchievementProgressError> = withContext(dispatchers.io) {
        runCatching {
            val batch = firestore.batch()
            newlyUnlocked.forEach { (id, ms) ->
                batch.set(col(accountId.value).document(id), AchievementUnlockDto(ms))
            }
            batch.commit()
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(errorMapper.map(it)) },
        )
    }
}
```

Note the asymmetry, which matches the existing convention: the streaming read (`observeUnlocks`) returns a cold `Flow` with no `withContext` (exactly like the `MealReadPort` Firestore implementations), and only the one-shot write (`recordUnlocks`) carries the single `withContext(dispatchers.io)`.

### 6.3 Overlaying persisted dates onto evaluation

The ViewModel combines the pure evaluation with the persisted unlock map: for each `AchievementStatus`, set `unlockedAtEpochMs = persisted[id.value]`. Any criterion that `isMet` but is absent from the persisted map is a **newly-satisfied** achievement → its id+now is collected and passed to `recordUnlocks` once (§7). Display uses `unlockedAtEpochMs != null` as the "earned" predicate, so a freshly-met-but-not-yet-written badge renders as locked-with-full-progress for the one frame before the write round-trips, then flips to earned — no flicker risk because progress already reads 100%.

## 7. Evaluation flow (when it runs)

Evaluation is **reactive**, driven off the same inputs stats uses, and **debounced** so a burst of Firestore snapshot emissions collapses to one evaluation.

```
ActiveCrewProvider.current  ┐
SessionProvider.current     ├─ combine ─→ flatMapLatest ─→ MealReadPort.observeRange(crew, from=today-365, to=today)
                            ┘                                AchievementProgressPort.observeUnlocks(accountId)
                                                             │  (combine + debounce(400ms))
                                                             ▼
                                          derive personalStreakDays / crewStreakDays / bestCook
                                                             ▼
                                          AchievementEvaluator.evaluate(catalog, signals)
                                                             ▼
                                          overlay persisted dates → State.statuses
                                                             ▼
                                          collect newly-met ids → recordUnlocks(once) → emit Unlocked effect
```

- **Window.** Lifetime-count criteria need the whole history, but the read surface is `observeRange`; the MVP uses a **365-day** range (the bound `ObserveStatsUseCase` already uses for its historic tab, `feature/stats/.../domain/usecase/ObserveStatsUseCase.kt`). Approximation of "lifetime" — see §20.
- **Derived signals.** `personalStreakDays`, `crewStreakDays`, `bestCookAccountId` are computed inside `:feature:achievements` from `crewMeals` — the feature must **not** depend on `:feature:stats` (cross-feature ban). Port the small pure algorithm shape from `feature/stats/.../domain/compute/{PersonalStreak,CrewStreak,ComputeWindow}.kt` into a feature-local `AchievementSignalsBuilder` (re-implemented, not imported); min-3-plates matches `COOK_AWARD_MIN_PLATES`.
- **Debounce + idempotency.** `debounce(400.milliseconds)` collapses a meal-publish's snapshot deltas to one evaluation/write; `recordUnlocks` writes only ids absent from the persisted map, so steady-state re-evaluation writes nothing (§20).

## 8. Presentation (`:feature:achievements/.../presentation`)

MVI, mirroring `StatsViewModel`/`StatsContract`. The `MviViewModel` base is `MviViewModel<S, I, E>(initial)` with `update { it.copy(...) }`, `currentState`, and `emit(effect)` (`core/presentation/.../mvi/MviViewModel.kt`).

### 8.1 `AchievementsContract`

```kotlin
// feature/achievements/.../presentation/AchievementsContract.kt
data class AchievementsState(
    val statuses: List<AchievementStatus> = emptyList(),
    val selected: AchievementStatus? = null,   // tapped badge → detail sheet
    val error: AchievementError? = null,
    val isLoading: Boolean = true,
) : MviState

sealed interface AchievementsIntent : MviIntent {
    data class SelectBadge(val id: AchievementId) : AchievementsIntent
    data object DismissDetail : AchievementsIntent
    data object DismissError : AchievementsIntent
}

sealed interface AchievementsEffect : MviEffect {
    /** Drives the unlock celebration (toast/confetti) at the screen. */
    data class Unlocked(val titleKey: AchievementStringKey) : AchievementsEffect
}
```

### 8.2 `AchievementsViewModel`

Constructor injects the use case + `AchievementProgressPort` (for the write) + `AnalyticsPort` (default `NoopAnalyticsTracker` so existing-style tests stay green) + `Clock`. It collects the evaluation flow, writes newly-unlocked ids exactly once, fires the analytics event and the `Unlocked` effect **after** the persist `Result` resolves `Ok`, and exposes earned/locked statuses in `State`. Single source of truth: everything lives in `AchievementsState`; reads use `currentState`, updates go through `update { it.copy(...) }` — no parallel `MutableStateFlow` (the `FeedViewModel` rule).

```kotlin
class AchievementsViewModel(
    observeAchievements: ObserveAchievementsUseCase,
    private val progress: AchievementProgressPort,
    private val clock: Clock,
    private val analytics: AnalyticsPort = NoopAnalyticsTracker,
) : MviViewModel<AchievementsState, AchievementsIntent, AchievementsEffect>(AchievementsState()) {

    // init: collect observeAchievements(); on Ok → update { statuses }, then persistAndCelebrate(); on Err → update { error }.

    /** The only side-effecting step: persist newly-met unlocks ONCE, then (and only on Ok) celebrate. */
    private suspend fun persistAndCelebrate(accountId: AccountId, statuses: List<AchievementStatus>) {
        val newlyMet = statuses.filter { it.progress.isMet && it.unlockedAtEpochMs == null }
        if (newlyMet.isEmpty()) return
        val now = clock.now().toEpochMilliseconds()
        val write = progress.recordUnlocks(accountId, newlyMet.associate { it.achievement.id.value to now })
        if (write is Result.Ok) {
            newlyMet.forEach {
                analytics.track(AnalyticsEvent.AchievementUnlocked(it.achievement.id.value))
                emit(AchievementsEffect.Unlocked(it.achievement.titleKey))
            }
        }   // on Err: leave them met-but-locked; the next snapshot retries the write
    }

    override suspend fun handle(intent: AchievementsIntent) = when (intent) {
        is AchievementsIntent.SelectBadge ->
            update { s -> s.copy(selected = s.statuses.firstOrNull { it.achievement.id == intent.id }) }
        AchievementsIntent.DismissDetail -> update { it.copy(selected = null) }
        AchievementsIntent.DismissError  -> update { it.copy(error = null) }
    }
}
```

`ObserveAchievementsUseCase` returns `Flow<Result<AchievementsSnapshot, AchievementError>>`, where `AchievementsSnapshot(val accountId: AccountId, val statuses: List<AchievementStatus>)`. The use case is **pure orchestration** (no `withContext`): it combines the ports' flows, builds `AchievementSignals`, calls the evaluator, and overlays persisted dates.

### 8.3 `AchievementsScreen`

A scrollable grid of badges. Earned badges render **vivid**; locked badges render **dimmed** with a progress indicator (`current / target`). Tapping a badge opens a detail sheet showing the criterion description (`resolve(descriptionKey)`) and, when earned, the formatted earned-on date. The `Unlocked` effect drives a celebration overlay tinted with `LocalFrSemanticColors.current.celebration` (a confetti/toast — the semantic palette already exposes `celebration`/`onCelebration`, `core/designsystem/.../theme/SemanticColors.kt`). Streak-family badges may tint with `streakHot`. All text via `resolve(AchievementStringKey.*)`.

### 8.4 `FrBadge` — new design-system atom

A pure, domain-free atom in `:core:designsystem` (atoms never import domain types — it takes primitives + a presentation enum):

```kotlin
// core/designsystem/.../atoms/FrBadge.kt
@Composable
fun FrBadge(
    icon: ImageVector,
    title: String,
    earned: Boolean,
    progressFraction: Float,   // 0f..1f; ring around the icon when !earned
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
)
```

When `earned`, the icon renders at full saturation with `tint`; when locked, it renders at reduced alpha with a progress ring. The achievement-aware wrapper (`FrAchievementCard`, which maps an `AchievementStatus` → `FrBadge` + i18n) lives in the **feature's** `presentation/components/` (domain-aware components live in the owning feature, not `:core:designsystem` — same rule as `FrMealCard`). New icons that aren't in `material-icons-core` are vendored into `core/designsystem/.../atoms/FrIcons.kt` via the `materialIcon { materialPath { … } }` DSL (the existing pattern — `PhotoCameraVector` etc.), so iOS (which lacks `material-icons-extended`) still links.

## 9. Initial badge catalog

Compile-time constant `AchievementCatalog.all: List<Achievement>` in `feature/achievements/.../domain/AchievementCatalog.kt`. Fifteen entries:

| id | criterion | scope |
|---|---|---|
| `first_plate` | `FirstPlate` | Personal |
| `meals_10` | `MealCount(10)` | Personal |
| `meals_50` | `MealCount(50)` | Personal |
| `meals_100` | `MealCount(100)` | Personal |
| `ingredients_25` | `IngredientVariety(25)` | Personal |
| `ingredients_50` | `IngredientVariety(50)` | Personal |
| `ingredients_100` | `IngredientVariety(100)` | Personal |
| `streak_personal_7` | `PersonalStreak(7)` | Personal |
| `streak_personal_30` | `PersonalStreak(30)` | Personal |
| `streak_personal_100` | `PersonalStreak(100)` | Personal |
| `streak_crew_7` | `CrewStreak(7)` | Crew |
| `streak_crew_30` | `CrewStreak(30)` | Crew |
| `best_cook` | `BestCook` | Crew |
| `early_bird_10` | `EarlyBird(10)` | Personal |
| `night_owl_10` | `NightOwl(10)` | Personal |

(`CuisineVariety` is intentionally **not** in the shipped catalog — it is a declared criterion leaf only, added to the catalog by the cuisine-passport spec; see §15.)

## 10. i18n

New `AchievementStringKey` enum (`feature/achievements/.../i18n/AchievementStringKey.kt`) implementing the sealed `StringKey` interface (the `StatsStringKey` shape: `enum class … (override val resourceId: StringResource) : StringKey`). The full set is one `…Title` + one `…Desc` key per catalog row (15 × 2) plus the chrome rows below; both `composeResources/values/strings.xml` and `values-es/strings.xml` get every key. Representative subset:

| Key | en | es | Notes |
|---|---|---|---|
| `ScreenTitle` | `Badges` | `Insignias` | Screen header |
| `EarnedSectionTitle` / `LockedSectionTitle` | `Earned` / `Locked` | `Conseguidas` / `Bloqueadas` | Grid section labels |
| `ProgressFormat` | `%1$d / %2$d` | `%1$d / %2$d` | Locked-badge progress; current / target |
| `EarnedOnFormat` | `Earned %1$s` | `Conseguida el %1$s` | Detail sheet; arg = formatted date |
| `UnlockedToast` | `Badge unlocked!` | `¡Insignia desbloqueada!` | Celebration toast |
| `FirstPlateTitle` / `FirstPlateDesc` | `First Plate` / `Publish your first meal.` | `Primer plato` / `Publica tu primera comida.` | catalog `first_plate` |
| `IngredientExplorer25Desc` | `Use 25 different ingredients.` | `Usa 25 ingredientes distintos.` | confirmed only |
| `BestCookTitle` / `BestCookDesc` | `Top Chef` / `Hold the highest average score.` | `Chef estrella` / `Mantén la mejor nota media.` | catalog `best_cook` |
| `ErrorUnauthorized` | `You're not allowed to see these.` | `No tienes permiso para verlas.` | maps `AchievementError.Read.Unauthorized` |
| `ErrorUnavailable` | `Couldn't load badges. Try again.` | `No se pudieron cargar las insignias. Inténtalo otra vez.` | maps `…Unavailable` |

(Pluralized progress like "N days" can use the `StatsPluralKey` pattern — a `PluralStringKey` enum over `Res.plurals.*` — if a plural reads better than templated `%1$d / %2$d`; the MVP uses the simple format.)

## 11. Error model + mapper + test

```kotlin
// feature/achievements/.../domain/error/AchievementError.kt  (sealed, never enum)
sealed interface AchievementError {
    sealed interface Session : AchievementError {
        data object NotSignedIn : Session
        data object NoActiveCrew : Session
    }
    sealed interface Read : AchievementError {
        data object Unauthorized : Read
        data object Unavailable  : Read
    }
}
```

`MealReadError` (`Unauthorized`/`CrewNotFound`/`Unavailable`) and `AchievementProgressError` (`Unauthorized`/`Unavailable`) are mapped into `AchievementError.Read.*` inside the use case (a `toAchievementError()` extension, mirroring stats' `MealReadError.toStatsError()`). Absent active crew / session → `AchievementError.Session.*`.

```kotlin
// feature/achievements/.../presentation/AchievementErrorToStringKey.kt
fun AchievementError.toStringKey(): AchievementStringKey = when (this) {
    AchievementError.Session.NotSignedIn  -> AchievementStringKey.ErrorNotSignedIn
    AchievementError.Session.NoActiveCrew -> AchievementStringKey.ErrorNoActiveCrew
    AchievementError.Read.Unauthorized    -> AchievementStringKey.ErrorUnauthorized
    AchievementError.Read.Unavailable     -> AchievementStringKey.ErrorUnavailable
}
```

`AchievementErrorToStringKeyTest` (`commonTest`) asserts one mapping per leaf — the exhaustiveness lock, exactly the `StatsErrorToStringKeyTest` shape (one `assertEquals` per error leaf).

## 12. Catalog entries

`FrBadge` is a public `Fr*` atom, so it ships a `catalogApp` entry (the design-review-surface contract). Because `catalogApp` depends **only** on `:core:designsystem` (confirmed: `catalogApp/build.gradle.kts` declares `implementation(projects.core.designsystem)` and no feature/Firebase), the entries render the *atom* with literal props — they do **not** import the feature's `AchievementStatus` or i18n. Add to `catalogApp/src/main/kotlin/.../stories/AtomStories.kt`:

```kotlin
CatalogEntry("atom.badge", CatalogGroup.ATOMS, "FrBadge", "Earned (vivid) vs locked (dimmed + progress ring)") { BadgeStory() }
```

with a `BadgeStory()` that uses `CatalogSceneSplit` to show an earned badge and a locked badge at 60% progress side by side. The feature-owned `FrAchievementCard` is **not** cataloged (it resolves feature i18n + domain types — same reason `FrMealCard`/`FrFeedMealCard` are not cataloged; the catalog is designsystem-only by deliberate design).

## 13. Analytics

One new leaf in `core/domain/.../analytics/AnalyticsEvent.kt`, placed in a new `// ── achievements ──` section, following the existing leaf shape (snake_case past-tense `name`, typed `params`, **no PII** — the id is a catalog slug, not user data):

```kotlin
/** Fired once per achievement, after its unlock timestamp is durably persisted (Ok). */
data class AchievementUnlocked(val achievementId: String) : AnalyticsEvent {
    override val name = "achievement_unlocked"
    override val params = mapOf("achievement_id" to text(achievementId))
}
```

Fired in `AchievementsViewModel.persistAndCelebrate` **after** `recordUnlocks` returns `Ok` (never before persistence; never inside a use case). `AnalyticsTaxonomyTest` (`:core:domain`) automatically covers it (the test walks all leaves). The tracking plan `docs/analytics/TRACKING_PLAN.md` gains a row.

## 14. Koin wiring + ModuleVerifyTest

```kotlin
// feature/achievements/.../di/AchievementsModule.kt
val achievementsModule = module {
    single { AchievementEvaluator() }
    factoryOf(::ObserveAchievementsUseCase)
    single<AchievementProgressPort> { FirebaseAchievementRepository(get(), get(), get()) }
    single { AchievementErrorMapper() }
    // explicit viewModel (NOT viewModelOf) so the AnalyticsPort default doesn't short-circuit graph resolution:
    viewModel {
        AchievementsViewModel(
            observeAchievements = get(),
            progress = get(),
            clock = get(),
            analytics = get(),
        )
    }
}
```

`FirebaseFirestore` (GitLive) is provided by `coreDataModule` (same source as `:feature:crew`/`:feature:meal`). Register in:

- `settings.gradle.kts`: `include(":feature:achievements")` (typesafe accessor `projects.feature.achievements`; `enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")` is already on).
- `shared/.../app/di/AppModule.kt`: `import …achievements.di.achievementsModule` and add `achievementsModule` to the `appModules` list (alongside `statsModule`, `feedModule`, etc.).
- `:androidApp` gains an `implementation(projects.feature.achievements)` if the nav graph references the screen directly (mirrors how `:androidApp` added `projects.feature.mealAi`).

`AchievementsModuleVerifyTest` (androidHostTest) mirrors `StatsModuleVerifyTest`, listing the cross-module types the feature consumes-but-doesn't-bind in `extraTypes`:

```kotlin
achievementsModule.verify(
    extraTypes = listOf(
        ActiveCrewProvider::class,
        SessionProvider::class,
        MealReadPort::class,
        Clock::class,
        FirebaseFirestore::class,
        DispatcherProvider::class,
        AnalyticsPort::class,   // required: the explicit viewModel injects it via get()
    ),
)
```

## 15. Forward-hooks (cuisine-passport & ingredient-bingo)

- **Cuisine-passport** adds `AchievementCriterion.CuisineVariety` to `AchievementCatalog.all`, supplies a cuisine signal on `AchievementSignals` (e.g. `distinctCuisines: Int`), and replaces the placeholder `AchievementProgress(0, target)` arm in the evaluator with a real count. No engine change beyond the new field + the one `when` arm.
- **Ingredient-bingo** adds a `BingoCard`-style criterion leaf (e.g. `IngredientBingo(card: Set<IngredientSlug>)`) and renders its own surface that reuses `FrBadge`/`AchievementStatus`. It declares the criterion; the engine evaluates it.
- **Streak milestones** are already first-class (`PersonalStreak`/`CrewStreak` leaves + catalog rows); new milestone thresholds are new catalog rows only.

These prove the engine is the shared substrate: each roadmap feature is "a criterion leaf + catalog rows + (optionally) its own screen", never a re-implementation of evaluate/persist/celebrate.

## 16. Firestore security rules

Add a subcollection rule under the existing `accounts/{uid}` block (`firestore.rules`, after the `devices` match at lines 31–33), owner-only — a member may read/write only their own unlock timestamps:

```
match /achievements/{achievementId} {
  allow read, write: if request.auth != null && request.auth.uid == uid;
}
```

This is stricter than `accounts/{uid}` itself (which allows authenticated read of public profile fields): achievements are private to the owner, like `private/` and `devices/`. Deploy with `pnpm dlx firebase-tools deploy --only firestore:rules --project foodrats-de4ec` (per CLAUDE.md). **Until deployed, `observeUnlocks`/`recordUnlocks` are denied** — the feature shows the read error and persists nothing.

## 17. Tests

`commonTest`:

- **`AchievementEvaluatorTest`** — one focused test per criterion leaf, against hand-built `AchievementSignals`/`MealWithRatings` fixtures (reuse the `feature/stats/.../commonTest/.../domain/compute/TestFixtures.kt` fixture shape):
  - `FirstPlate`: 0 plates → locked; 1 plate → met.
  - `MealCount(10)`: 9 own plates → `progress 9/10` locked; 10 → met; **only the member's own plates count** (a crew-mate's plates do not advance a Personal criterion).
  - `IngredientVariety(25)`: counts **distinct confirmed** slugs; asserts `detectedIngredients` are ignored even when present on the meal.
  - `PersonalStreak(7)` / `CrewStreak(7)`: met exactly at the threshold day count.
  - `EarlyBird(10)` / `NightOwl(10)`: only `Breakfast` / `Dinner` slots count.
  - `BestCook`: met iff `bestCookAccountId == accountId`.
  - `CuisineVariety`: always locked (forward-hook).
- **`AchievementsViewModelTest`** (Turbine + `UnconfinedTestDispatcher`, `expectMostRecentItem()`): newly-met achievements call `recordUnlocks` once and emit `Unlocked` + a `RecordingAnalyticsTracker` records `achievement_unlocked`; an **already-persisted** achievement does **not** re-fire (idempotency); a `recordUnlocks` `Err` leaves the badge met-but-locked and fires no analytics; `SelectBadge` populates `selected`.
- **`AchievementErrorToStringKeyTest`** — one `assertEquals` per `AchievementError` leaf.
- **`AchievementCatalogTest`** — every `Achievement.id` is unique and every `titleKey`/`descriptionKey` is a distinct `AchievementStringKey` (catches copy-paste catalog rows).

`androidHostTest`:

- **`AchievementsModuleVerifyTest`** (per §14).
- **`KonsistRulesTest` re-run** for `:core:domain` (the new port + error live there — confirm they import nothing forbidden; see §18).

## 18. Konsist / arch tests

- The new `:core:domain` additions (`AchievementProgressPort`, `AchievementProgressError`) use only `kotlin.stdlib`, `kotlinx.coroutines` (`Flow`), the in-module `Result`, and `AccountId` — so the existing `KonsistRulesTest` (`core/domain/src/androidHostTest/.../KonsistRulesTest.kt`, no-Firebase/no-Android/no-Compose) still passes. Run it.
- No new arch rule is required. `:feature:achievements` has no Gradle dependency on any `:feature:*` (the dependency graph in §4 enforces the cross-feature ban structurally); the only feature-to-feature signal it needs (streaks, best cook) is re-derived locally rather than imported from `:feature:stats`.
- `FrBadge` (`:core:designsystem` atom) imports no domain types (takes `ImageVector`/`String`/`Boolean`/`Float`/`Color`) — preserves the "atoms never import domain" rule.

## 19. Order of work (for the implementation plan)

1. `:core:domain` — `AchievementProgressPort` + `AchievementProgressError`; re-run `KonsistRulesTest`. Add the `AchievementUnlocked` analytics leaf.
2. `:feature:achievements` skeleton — module + `build.gradle.kts` (JVM 17 + Firebase BOM), `settings.gradle.kts` entry.
3. Domain — value objects, `AchievementCriterion`, `Achievement`, `AchievementCatalog`, `AchievementSignals`, `AchievementEvaluator` + `AchievementEvaluatorTest` + `AchievementCatalogTest`.
4. Signal builder — feature-local `AchievementSignalsBuilder` (streak/best-cook re-derivation) + its unit test.
5. Data — `AchievementUnlockDto`, `FirebaseAchievementRepository`, `AchievementErrorMapper`.
6. Presentation — contract, `ObserveAchievementsUseCase`, `AchievementsViewModel`, `AchievementStringKey` + en/es strings, `AchievementErrorToStringKey` + test, `AchievementsViewModelTest`.
7. Design system — `FrBadge` (+ vendored icons if needed) + the `catalogApp` story; feature-owned `FrAchievementCard` + `AchievementsScreen`.
8. Koin — `achievementsModule`, register in `shared` aggregator + `:androidApp`; `AchievementsModuleVerifyTest`.
9. Firestore — add the `accounts/{uid}/achievements` rule; note the manual deploy step.
10. Run the full host-test set (per CLAUDE.md "Build, run, test", adding `:feature:achievements:testAndroidHostTest`) + the Konsist test; quote the green output. Build `:androidApp:assembleDebug` and `:shared:linkDebugFrameworkIosSimulatorArm64`.
11. Add a "Recent decisions (2026-06-14) — Badges & achievements engine" entry to `CLAUDE.md` (what/why/how), following the dated-entry pattern.

## 20. Risks

- **Evaluation cost / 365-day window.** Counting/distinct over a 365-day `crewMeals` list on every (debounced) snapshot is trivial for a crew of 3–8 (low-thousands of items at most); mitigated by the bounded window, `debounce(400ms)`, and the allocation-free pure evaluator. The 365-day bound also *approximates* "lifetime" for count criteria — safe while crews are < 1 year old. Both are removed by the same follow-up: server-maintained lifetime counters behind the unchanged `AchievementProgressPort`.
- **Double-unlock idempotency.** Two near-simultaneous snapshots could each see an achievement as newly-met before the first write lands. `recordUnlocks` uses `batch.set` (last-write-wins, no duplicate doc) and writes only ids absent from the last-observed persisted map; the next `observeUnlocks` emission carries the written doc, so the second pass emits nothing. The analytics event fires at most once per (account, achievement) in steady state; a rare write-race duplicate is acceptable.
- **Retroactive unlocks.** A member with 60 plates at ship time unlocks `meals_10`/`meals_50` immediately with `unlockedAtEpochMs = now`, not the true historical instant — accepted for beta. The bundle of first-launch celebrations is acceptable (even pleasant); if noisy, add a `firstRun` guard that suppresses the `Unlocked` effect for unlocks written in the first post-install evaluation and only celebrates subsequent live ones.
- **Rule-deploy ordering.** Shipping the feature before the §16 Firestore rule is deployed denies all reads/writes → `ErrorUnavailable`. The deploy is a called-out manual step in the order-of-work; run it first.
