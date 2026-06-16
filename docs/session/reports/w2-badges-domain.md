# Report — `w2-badges-domain`

**Task:** DOMAIN core of the badges/achievements feature — a new `:feature:achievements` module
holding the `AchievementCriterion` taxonomy + the pure `AchievementEvaluator`. Domain layer only.

**Spec:** `docs/specs/2026-06-14-badges-achievements-design.md` §5.2 (criterion taxonomy), §5.3/§5.5
(evaluator), §5.1/§5.4 (VOs + signals/status), §9 (catalog), §10 (i18n keys), §11 (error tree).

**Status: DONE.** Module compiles, all host tests green.

## Prior work check

None on disk — `feature/achievements/` did not exist. Built from scratch using `:feature:stats`
as the Firebase-free scaffold template.

## What was built

### Module + registration
- `feature/achievements/build.gradle.kts` — mirrors `:feature:stats` (KMP `iosArm64` +
  `iosSimulatorArm64` + `androidLibrary`, `withHostTest`, CMP resources, `kotlinxSerialization`
  plugin applied for forward-compat with the data DTO). commonMain deps: `core.domain`, `core.data`,
  `core.designsystem`, `core.presentation`, `core.i18n`, `bundles.feature.ui`,
  `bundles.kotlinx.common`. commonTest: `bundles.feature.test`. androidHostTest:
  `bundles.feature.hosttest`.
- `settings.gradle.kts` — added `include(":feature:achievements")` (accessor
  `projects.feature.achievements`).

### Domain (`commonMain/.../feature/achievements/domain/`)
- `model/AchievementId.kt` — `@JvmInline value class AchievementId(val value: String)`. No `of()`
  factory — it is a compile-time catalog constant (spec §5.1).
- `model/AchievementScope.kt` — `enum { Personal, Crew }`.
- `model/AchievementTier.kt` — `enum { Bronze, Silver, Gold }` (presentation-only metadata).
- `model/AchievementIcon.kt` — plain `enum` (Plate/Trophy/Ingredients/Streak/CrewStreak/Sunrise/
  Moon/Chef/Globe). Carries **no** Compose type so the domain `Achievement` stays vector-free; the
  design system maps each value → an `FrIcons` vector (spec §5.3/§8.4). Placed in `domain/model`
  (not `presentation/`) so `Achievement` is self-contained in the domain layer.
- `model/AchievementCriterion.kt` — the sealed taxonomy **exactly per §5.2**: `FirstPlate`,
  `MealCount(target)`, `IngredientVariety(target)`, `PersonalStreak(days)`, `CrewStreak(days)`,
  `EarlyBird(target)`, `NightOwl(target)`, `BestCook`, `CuisineVariety(target)` (forward-hook).
  `data object` for boolean leaves, `data class` for threshold leaves; each declares `scope`.
- `model/Achievement.kt` — catalog row `data class`: `id`, `titleKey`/`descriptionKey`
  (`AchievementStringKey`), `iconKey` (`AchievementIcon`), `criterion`, optional `tier`.
- `model/AchievementSignals.kt` — `data class` of everything the pure evaluator needs (accountId,
  crewMeals, personalStreakDays, crewStreakDays, bestCookAccountId). Resolved from ports by the
  caller (spec §5.4).
- `model/AchievementStatus.kt` — `AchievementProgress(current, target)` with `isMet` guarded by
  `target > 0`; `AchievementStatus(achievement, progress, unlockedAtEpochMs: Long? = null)`.
- `AchievementEvaluator.kt` — the **pure** engine **exactly per §5.5**: `evaluate(catalog, signals)
  → List<AchievementStatus>` with `unlockedAtEpochMs = null` (caller overlays persisted dates).
  Exhaustive `when` over the taxonomy (compile-time guard). Personal criteria filter to the
  member's own plates; `IngredientVariety` counts **distinct confirmed** ingredients and ignores
  `detectedIngredients`; `CuisineVariety` always `0/target` (forward-hook).
- `AchievementCatalog.kt` — `object AchievementCatalog { val all: List<Achievement> }`, the 15 rows
  of §9. `CuisineVariety` deliberately not shipped (declared leaf only).
- `error/AchievementError.kt` — sealed tree per §11: `Session.{NotSignedIn, NoActiveCrew}`,
  `Read.{Unauthorized, Unavailable}`.

### i18n (key contract defined now; copy in en + es)
- `i18n/AchievementStringKey.kt` — `enum … : StringKey` (StatsStringKey shape). One `…Title` +
  `…Desc` per catalog row + chrome (`ScreenTitle`, `EarnedSectionTitle`, `LockedSectionTitle`,
  `ProgressFormat`, `EarnedOnFormat`, `UnlockedToast`) + 4 error keys.
- `composeResources/values/strings.xml` + `values-es/strings.xml` — every key populated (en + es).
- `presentation/AchievementErrorToStringKey.kt` — exhaustive `AchievementError.toStringKey()`.

### Tests (`commonTest`, run on the Android host target)
- `AchievementEvaluatorTest` (17) — one+ per criterion leaf: earned / not-earned / boundary;
  Personal-only filtering; confirmed-vs-detected ingredients; streak threshold exactness; slot
  filtering; BestCook leader/non-leader/none; CuisineVariety always locked; full-catalog evaluate.
- `AchievementCatalogTest` (4) — 15 rows; unique ids; distinct title/desc keys (copy-paste guard);
  CuisineVariety not shipped.
- `AchievementProgressTest` (3) — `isMet` at/above/below target and the `target == 0` guard.
- `AchievementErrorToStringKeyTest` (4) — one `assertEquals` per error leaf (exhaustiveness lock).
- `TestFixtures.kt` — `meal(...)` builder mirroring stats' `TestFixtures` shape.

## Verification

```
./gradlew :feature:achievements:testAndroidHostTest
> Task :feature:achievements:testAndroidHostTest
BUILD SUCCESSFUL in 18s
90 actionable tasks: 90 executed
```

Per-suite (from `build/test-results/testAndroidHostTest/TEST-*.xml`) — 28 tests total, 0 failures,
0 errors:
- `AchievementCatalogTest` tests="4" failures="0" errors="0"
- `AchievementEvaluatorTest` tests="17" failures="0" errors="0"
- `AchievementProgressTest` tests="3" failures="0" errors="0"
- `AchievementErrorToStringKeyTest` tests="4" failures="0" errors="0"

No `:core:domain` changes were made (see Decisions), so `:core:domain:testAndroidHostTest` was not
required for this task.

## Decisions

1. **No `:core:domain` changes in this task.** The spec puts `AchievementProgressPort` +
   `AchievementProgressError` (§6.1) and the `AnalyticsEvent.AchievementUnlocked` leaf (§13) in
   `:core:domain`, but those are consumed only by the **data** (persistence) and **presentation**
   (write/celebrate) layers — out of scope here. The domain evaluator is fully testable without
   them. They are left for `w2-badges-data` / `w2-badges-presentation`. This keeps the domain task's
   blast radius to the new module + one `settings.gradle.kts` line.

2. **JVM 11, no Firebase, for now.** Spec §4 says the *module* ends up JVM 17 + Firebase BOM
   because of the Firestore repository. This task writes **zero** Firebase code, so adding the BOM /
   firestore artifact now would be dead weight, and the data task touches `build.gradle.kts` anyway.
   The build file carries a clearly-commented TODO: `w2-badges-data` must add the
   `firebase-firestore` GitLive artifact + Firebase BOM in `androidMain` and bump
   `jvmTarget = JvmTarget.JVM_17`. (Flagged in the handoff.)

3. **`AchievementIcon` lives in `domain/model`, not `presentation/`.** It is a payload-free name
   enum (no Compose type), so keeping it in the domain lets `Achievement` (a domain type) reference
   it without a domain→presentation import inside the module. The design system resolves the enum →
   vector. Preserves the "atoms never import domain; domain never imports Compose" rules.

4. **`AchievementStringKey` + en/es copy authored now.** The task asked to "define the key
   contract". Because the domain `Achievement` references `AchievementStringKey` directly (spec
   §5.3), the enum + generated `Res` strings must exist for the module to compile. Copy is final
   for all 15 rows; presentation may refine wording but the key set is locked.

5. **No use case / ViewModel / Koin module.** Those are §8/§14 (presentation). The module compiles
   and tests pass without any Koin wiring, so none was added to `:androidApp`/`shared`.

## Blockers

None.

## Notes for downstream tasks

See `docs/session/handoffs/w2-badges-domain.md` for the full type/signature contract.
