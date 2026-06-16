# Handoff — `w2-badges-domain`

For `w2-badges-data`, `w2-badges-presentation`, and the cuisine-passport / ingredient-bingo tasks
that plug into this taxonomy. Domain layer is DONE and green.

## Module

- Path: `feature/achievements/` · Gradle accessor: `projects.feature.achievements`
- Base package: `es.schsebastian.foodrats.feature.achievements`
- Currently **JVM 11, no Firebase** (mirrors `:feature:stats`). Generated resources package:
  `foodrats.feature.achievements.generated.resources`.

### ⚠️ `w2-badges-data` MUST change the build file
In `feature/achievements/build.gradle.kts`:
- bump `compilerOptions { jvmTarget = JvmTarget.JVM_17 }` (Firebase BOM inline funcs need 17),
- add to `androidMain.dependencies`: `implementation(project.dependencies.platform(libs.firebase.bom))`,
- add `implementation(libs.firebase.firestore)` (or `libs.bundles.firebase.gitlive`) to `commonMain`.
There is a TODO comment at the `jvmTarget` line marking this.

## Domain types (all in `.../domain/model/` unless noted)

| Type | Shape |
|---|---|
| `AchievementId` | `@JvmInline value class AchievementId(val value: String)` — no `of()`, catalog constant; `.value` is the Firestore doc id |
| `AchievementScope` | `enum { Personal, Crew }` |
| `AchievementTier` | `enum { Bronze, Silver, Gold }` (presentation metadata) |
| `AchievementIcon` | `enum { Plate, Trophy, Ingredients, Streak, CrewStreak, Sunrise, Moon, Chef, Globe }` — no Compose type; presentation maps → `FrIcons` |
| `AchievementCriterion` | sealed interface, `val scope`; leaves: `FirstPlate`, `MealCount(target)`, `IngredientVariety(target)`, `PersonalStreak(days)`, `CrewStreak(days)`, `EarlyBird(target)`, `NightOwl(target)`, `BestCook`, `CuisineVariety(target)` |
| `Achievement` | `data class(id: AchievementId, titleKey: AchievementStringKey, descriptionKey: AchievementStringKey, iconKey: AchievementIcon, criterion: AchievementCriterion, tier: AchievementTier? = null)` |
| `AchievementSignals` | `data class(accountId: AccountId, crewMeals: List<MealWithRatings>, personalStreakDays: Int, crewStreakDays: Int, bestCookAccountId: AccountId?)` |
| `AchievementProgress` | `data class(current: Int, target: Int)`; `val isMet: Boolean get() = target > 0 && current >= target` |
| `AchievementStatus` | `data class(achievement: Achievement, progress: AchievementProgress, unlockedAtEpochMs: Long? = null)` |

## Catalog

`object AchievementCatalog { val all: List<Achievement> }` — `.../domain/AchievementCatalog.kt`.
15 rows: `first_plate`, `meals_10/50/100`, `ingredients_25/50/100`, `streak_personal_7/30/100`,
`streak_crew_7/30`, `best_cook`, `early_bird_10`, `night_owl_10`. `CuisineVariety` NOT shipped.

## Evaluator (pure — `.../domain/AchievementEvaluator.kt`)

```kotlin
class AchievementEvaluator {
    fun evaluate(catalog: List<Achievement>, signals: AchievementSignals): List<AchievementStatus>
}
```
- Pure: no I/O, no Clock, no Flow. Returns one status per row with `unlockedAtEpochMs = null`.
- **The caller overlays persisted unlock dates** (spec §6.3): for each status set
  `unlockedAtEpochMs = persisted[id.value]`; a status that `progress.isMet && unlockedAtEpochMs ==
  null` is newly-met → collect `id.value to now` and call `recordUnlocks` once.
- Personal criteria already filter to `signals.accountId`'s own plates internally.
- `IngredientVariety` counts distinct `meal.ingredients` (confirmed) only — `detectedIngredients`
  are ignored. `EarlyBird` = `MealSlot.Breakfast`, `NightOwl` = `MealSlot.Dinner`.

### Required inputs / ports (caller's job to resolve into `AchievementSignals`)
- `MealReadPort.observeRange(crewId, from = today-365, to = today)` → `crewMeals` (whole crew).
- `ActiveCrewProvider.current` → which crew; `SessionProvider.current` → `accountId`.
- `personalStreakDays` / `crewStreakDays` / `bestCookAccountId`: **re-derive feature-locally** (do
  NOT depend on `:feature:stats`). Port the algorithm shape from
  `feature/stats/.../domain/compute/{PersonalStreak,CrewStreak,ComputeWindow}.kt` into a
  feature-local `AchievementSignalsBuilder` (spec §7). `bestCook` min-3-plates.
- New port `AchievementProgressPort` (declare in `:core:domain`, spec §6.1) for unlock read/write —
  **not yet created** (data task owns it).

## Error contract

`.../domain/error/AchievementError.kt` (sealed): `Session.NotSignedIn`, `Session.NoActiveCrew`,
`Read.Unauthorized`, `Read.Unavailable`. Map `MealReadError` + `AchievementProgressError` into
`Read.*` in the use case (a `toAchievementError()` ext, mirroring stats). Mapper to i18n:
`.../presentation/AchievementErrorToStringKey.kt` `fun AchievementError.toStringKey()`. The
exhaustiveness test is `AchievementErrorToStringKeyTest` — extend it if you add error leaves.

## i18n key contract

`enum class AchievementStringKey(override val resourceId: StringResource) : StringKey` —
`.../i18n/AchievementStringKey.kt`. Keys present (en + es populated in
`composeResources/values{,-es}/strings.xml`):
- Chrome: `ScreenTitle`, `EarnedSectionTitle`, `LockedSectionTitle`, `ProgressFormat` (`%1$d / %2$d`),
  `EarnedOnFormat` (`Earned %1$s`), `UnlockedToast`.
- Per catalog row: `<Row>Title` + `<Row>Desc` — `FirstPlate`, `Meals10/50/100`,
  `Ingredients25/50/100`, `StreakPersonal7/30/100`, `StreakCrew7/30`, `BestCook`,
  `EarlyBird10`, `NightOwl10`.
- Errors: `ErrorNotSignedIn`, `ErrorNoActiveCrew`, `ErrorUnauthorized`, `ErrorUnavailable`.

## Still owned by other tasks (NOT done here)

- `w2-badges-data`: `AchievementProgressPort` + `AchievementProgressError` in `:core:domain` (§6.1);
  `AchievementUnlockDto`, `FirebaseAchievementRepository`, `AchievementErrorMapper` (§6.2); the
  `accounts/{uid}/achievements` Firestore rule (§16, manual deploy); the build.gradle JVM-17 bump.
- `w2-badges-presentation`: `AnalyticsEvent.AchievementUnlocked` leaf in `:core:domain` (§13);
  `AchievementsContract`/`ViewModel`/`Screen`, `ObserveAchievementsUseCase`, `AchievementSignalsBuilder`,
  `FrBadge` atom + catalog story (§8, §12), Koin `achievementsModule` + register in `shared` +
  `AchievementsModuleVerifyTest` (§14).
