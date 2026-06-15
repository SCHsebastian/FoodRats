# Report — w5-fix-stats-meal-fixtures

**Status:** DONE (test-compile fix; no production change).

## Root cause

`:feature:stats:compileAndroidHostTest` was RED with type-mismatch + missing-arg errors on
~15 lines across two test files. The cause was a **positional-argument shift**, not missing
defaults.

The `Meal` data class (`core/domain/.../meal/Meal.kt`) has recently-added fields inserted
**mid-constructor**, between `photoUrl` and `dish`:

```
id, author, crewId, day, slot, photoUrl,
thumbnailUrl = "",      // inserted (defaulted)
thumbHash    = null,    // inserted (defaulted)  <-- new fields, NOT at the end
dish, description, publishedAt, coordinates = null, ingredients = …, …, cuisine = null, kind = MealKind.Solo
```

The stats fixtures constructed `Meal(...)` positionally:
`…, "u" /*photoUrl*/, DishName("Pasta"), Description.EMPTY, Instant(0)`.

After the insertion the positional slots slid by two:
- `DishName` ("Pasta") landed on `thumbnailUrl: String` → "actual type is 'DishName', but 'String' was expected"
- `Description.EMPTY` landed on `thumbHash: String?` → "'Description' but 'String?' expected"
- `Instant(0)` landed on `dish: DishName` → "'Instant' but 'DishName' expected"
- `description` and `publishedAt` then had no value → "No value passed for parameter".

The recently-added fields DO all have sensible defaults — the issue was purely **ordering**
(defaulted fields inserted not-at-the-end) interacting with positional fixture calls.
`thumbnailUrl`/`thumbHash` come from the storage/thumbnail/thumbhash roadmap work; `cuisine`
and `kind` (both at the end, already used with named args in one fixture) are harmless.

## Fix chosen

Did NOT reorder `Meal.kt` — reordering defaulted fields to the end could perturb other
positional callers across the repo and touches the production domain model for a test-only
failure. Instead, **hardened the two stats fixtures to named arguments** (the brief's preferred,
future-proof option). Named all positional args up to and including the shifted ones so any
future mid-constructor field addition can never silently mis-bind again.

Files changed (test-only):
- `feature/stats/src/commonTest/kotlin/.../domain/compute/PersonalStreakTest.kt`
  — `meal(...)` fixture → fully named args.
- `feature/stats/src/commonTest/kotlin/.../presentation/stats/StatsViewModelTest.kt`
  — both `makeMealMine(...)` and `makeRatedMeal(...)` fixtures → named args.

No production code touched. `Meal.kt` was read only to confirm the constructor shape; left
unchanged.

## Verification

`./gradlew :feature:stats:testAndroidHostTest`
```
> Task :feature:stats:testAndroidHostTest
BUILD SUCCESSFUL in 2s
90 actionable tasks: 8 executed, 82 up-to-date
```
(Remaining output is pre-existing `Unnecessary non-null assertion (!!)` warnings, not errors.)

Broad sibling confirm —
`./gradlew :core:domain:testAndroidHostTest :feature:feed:testAndroidHostTest :feature:meal:testAndroidHostTest :feature:stats:testAndroidHostTest`
```
> Task :feature:feed:testAndroidHostTest
BUILD SUCCESSFUL in 2s
167 actionable tasks: 8 executed, 159 up-to-date
```

All four modules green. No siblings broken.

## Hardening note

Yes — converted the broken positional fixture construction to **named arguments** in all three
fixtures. This is the robustness improvement the brief requested: future `Meal` field additions
(at any position, with defaults) will no longer break these fixtures.
