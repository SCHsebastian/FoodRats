# Meal ingredients on the feed detail + ingredient stats

Date: 2026-05-26
Status: approved
Branch: `feat/meal-ai-ingredient-classification`
Supersedes/relates: deferred spec §13 items "ingredient chips on feed" and "IngredientFrequency stat" from `docs/specs/2026-05-24-meal-ai-ingredient-classification-design.md`. This design intentionally renders a **list** (not chips) and lands two concrete ingredient stats instead of the abstract `IngredientFrequency`.

## Problem

A published `Meal` carries `ingredients: List<IngredientSlug>` (user-confirmed) and `detectedIngredients: List<IngredientSlug>` (AI-detected). Both are persisted to Firestore but surfaced **nowhere** in the UI. The data is invisible to viewers and unused by stats.

## Goals

1. **Meal detail** shows the meal's ingredients as a vertical list (one per line, not chips).
2. **Stats** gains, per window (Week / Month / Historic): the crew-wide **most-used ingredient**, and the **top ingredient per member**.

Non-goals: ingredient chips on the compact scroll row; cloud LLM fallback; consent UI; iOS on-device smoke (link check only).

## Decisions (confirmed with user)

- "Per each" = **per crew member** (mirrors the existing best-cook / most-prolific per-member leaderboards).
- Feed location = **meal detail screen + the (legacy, currently unwired) `FrFeedMealCard`**. The compact `FrFeedMealRow` in the scrolling list stays clean.
- Ingredient set = **confirmed + detected, merged and deduped** (confirmed first, then detected not already present).

## Cross-cutting: slug → localized display name

Both surfaces must turn an `IngredientSlug` into a human display name. Only `:feature:ingredient` owns names, and **features cannot depend on features**, so resolution goes through the existing port:

```
// :core:domain
interface IngredientReadPort {
    fun observeCatalog(): Flow<Map<IngredientSlug, Ingredient>>   // names already localized by active language
    suspend fun findBySlugs(slugs: Set<IngredientSlug>): List<Ingredient>
    suspend fun suggestForDish(dishSlug: String): List<IngredientSlug>
}
```

- `:feature:feed` and `:feature:stats` inject `IngredientReadPort`. It is a **pure domain port** (no Firebase, no Android), so neither module needs the JVM-17 bump — both stay on JVM 11.
- The binding (`IngredientRepository`, in `ingredientModule`, already registered in `shared` `appModules`) lives in the same Koin graph, so a plain `get()` resolves it.
- **Resolver fallback:** when a slug is absent from the catalog map (catalog still loading, or an unknown slug), humanize the slug value — replace `_`/`-` with spaces and capitalize the first letter (`chicken_breast` → `Chicken breast`). The catalog re-emits when it loads, upgrading the fallback to the real localized name.

**Per-meal merge rule** (shared helper, pure):

```
fun mergedIngredientSlugs(meal: Meal): List<IngredientSlug> =
    (meal.ingredients + meal.detectedIngredients).distinct()   // confirmed first, dedupe preserves first occurrence
```

## Part 1 — Feed / meal detail

### Data flow

`MealDetailViewModel` already builds its `FeedMealUi` via the shared `MealWithRatings.toFeedUi(...)` mapper and stores it in `MealDetailState.meal`. `FeedMealUi` is therefore the single seam.

- `FeedMealUi` gains `val ingredients: List<String> = emptyList()` (resolved display names; default keeps every other call site compiling).
- `toFeedUi(...)` gains a parameter `ingredientNames: List<String> = emptyList()` and assigns it through. The mapper stays pure — it does **not** call the port.
- `MealDetailViewModel` injects `IngredientReadPort` and `combine`s its existing `observeFeed(...)` flow with `ingredientRead.observeCatalog()`. For the matched meal it computes `mergedIngredientSlugs(meal)`, resolves each via the catalog map (+ humanize fallback), and passes the names into `toFeedUi(..., ingredientNames = …)`.
- `FeedViewModel` calls `toFeedUi(...)` **without** `ingredientNames` (default empty) → the compact list row renders nothing extra and takes on no new catalog subscription.

### UI

- New section in `MealDetailScreen`'s body, shown only when `meal.ingredients.isNotEmpty()`:
  - Heading `resolve(FeedStringKey.IngredientsHeading)` ("Ingredients" / "Ingredientes").
  - One ingredient per line (`FrText` per item), mirroring the existing voter-list layout. No chips.
- `FrFeedMealCard` (legacy, currently unwired per `feature/feed/README.md`) gets the same section so the "rich card" is covered if it is ever re-wired.

## Part 2 — Stats

### Domain models (new, in `:feature:stats/domain/model`)

```
data class IngredientUsage(
    val displayName: String,
    val mealCount: Int,
)

data class MemberIngredient(
    val accountId: AccountId,
    val displayName: String,
    val avatarUrl: String?,
    val ingredientName: String,
    val mealCount: Int,
)
```

`WindowStats` gains:

```
val mostUsedIngredient: IngredientUsage? = null,
val topByMember: List<MemberIngredient> = emptyList(),
```

### Computation (`computeWindow`)

`computeWindow` stays a **pure function** but gains a resolver param:

```
fun computeWindow(
    meals: List<MealWithRatings>,
    window: StatsWindow,
    ingredientName: (IngredientSlug) -> String,
): WindowStats
```

- **Counting:** an ingredient's count = number of meals in the window whose `mergedIngredientSlugs` contains it (presence per meal — a slug counts at most once per meal).
- **Most-used (crew-wide):** flatten counts across all meals, take the max. Tie-break: `mealCount` desc, then `ingredientName` asc. `null` when no meal has any ingredient.
- **Top per member:** reuse the existing `byAuthor = meals.groupBy { it.meal.author.accountId }`. For each author, compute their own per-ingredient counts and take their top (same tie-break), resolving the author identity from `list.first().meal.author`. Emit the list sorted by `mealCount` desc, then `ingredientName` asc. Authors with zero ingredients are omitted.

### Use case wiring

`ObserveStatsUseCase` injects `IngredientReadPort`, folds `observeCatalog()` into its existing flow `combine`, and builds the resolver `{ catalog[it]?.displayName ?: humanize(it.value) }`, passing it into every `computeWindow(...)` call (week / month / historic). `computeHeroStats` is unchanged.

### Presentation

- After the cooks / roast section in `TabBody`:
  - `window.mostUsedIngredient?.let { FrMostUsedIngredientCard(it) }` — title + "name · N meals".
  - When `window.topByMember` is non-empty: a section title + a `FrMemberIngredientRow` per member (avatar + member name + their top ingredient + count).
- New `StatsStringKey` entries + en/es strings.

## i18n keys

- Feed (`FeedStringKey` + `feature/feed` strings): `IngredientsHeading`.
- Stats (`StatsStringKey` + `feature/stats` strings):
  - `MostUsedIngredientTitle`
  - `MostUsedIngredientMetricFormat` = `"%1$s · %2$d meals"` (es: `"%1$s · %2$d comidas"`)
  - `TopIngredientByMemberTitle`
  - `MemberTopIngredientFormat` = `"%1$s · %2$d"`

(The separator `·` and counts are part of the templated string per the i18n-covers-separators rule. Ingredient names themselves are localized catalog data, not static copy, so they are not `StringKey`s.)

## Tests

- `feature/stats` `ComputeWindowTest`: extend the `mealWithRatings` fixture with an `ingredients: List<String> = emptyList()` param (mapped to `IngredientSlug`s). New cases:
  - no ingredients → `mostUsedIngredient == null`, `topByMember.isEmpty()`.
  - crew-wide most-used picks the highest meal-count ingredient; tie broken by name asc.
  - per-member returns each author's own top ingredient.
  - Tests pass a trivial resolver `{ it.value }`.
- `feature/feed`: a pure unit test for `mergedIngredientSlugs` + name resolution helper (confirmed-first ordering, detected appended, dedupe, humanize fallback).
- No new error leaves → existing `*ErrorToStringKeyTest`s are untouched.

## Verification

```
./gradlew :core:domain:testAndroidHostTest :feature:feed:testAndroidHostTest :feature:stats:testAndroidHostTest
./gradlew :androidApp:assembleDebug
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

Then the user runs the app on Android + iOS: open a meal that has ingredients → list shows on detail; open Stats → most-used ingredient + per-member rows render for each window.
