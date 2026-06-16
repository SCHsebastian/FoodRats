# Score/coordinate strings rendered a literal `%` instead of the value

**Date:** 2026-06-15
**Reported as:** "votation points sometimes get wrong values with % and so."

## Root cause (confirmed by reading the library source)

`resolve(StringKey, vararg args)` → Compose Resources `stringResource(id, *args)`. In CMP **1.11.0**
this does NOT delegate to platform `String.format`. On **every** target it runs its own substitution
(`org.jetbrains.compose.resources.replaceWithArgs`, commonMain):

```kotlin
private val SimpleStringFormatRegex = Regex("""%(\d+)\$[ds]""")
internal fun String.replaceWithArgs(args: List<String>) =
    SimpleStringFormatRegex.replace(this) { args[it.groupValues[1].toInt() - 1] }
```

It matches **only** `%n$d` and `%n$s`. A `%n$.Nf` float placeholder never matches → it is emitted
**verbatim** (you see a literal `%1$.1f` on screen) and its float argument is dropped. Args are also
`.toString()`-ed first, so there is no crash — just the wrong, `%`-bearing text. Cross-platform
(Android + iOS), not locale-specific.

Note: `Double.toString()` is locale-independent in Kotlin, so the decimal-separator theory was a red
herring — the bug is purely the unsupported `%f` placeholder.

## Affected strings (the only 4 unsupported placeholders in the whole repo)

| string | was | now |
|---|---|---|
| `recap_top_meal_score` | `%1$.1f ★ · %2$d votes` | `%1$s ★ · %2$d votes` |
| `recap_best_cook_subtitle` | `%1$s · %2$.1f ★ average` | `%1$s · %2$s ★ average` |
| `meal_compose_coordinates_format` | `%1$.5f, %2$.5f` | `%1$s, %2$s` |

(en + es each.) Every other `%`-string already used `%n$d`/`%n$s` and worked — the feed score
strings (`feed_rating_summary` etc.) already pre-format the average to a String, which is why feed
was fine and only recap/coordinates broke.

## Fix

1. New locale-independent formatter `Double.toFixed(decimals)` in
   `core/i18n/.../NumberFormatting.kt` (KDoc documents the `%f` trap so it isn't reintroduced).
2. Switched the 3 strings (6 XML entries) from `%f` → `%s`.
3. Pre-format the Double args at the 4 call sites:
   - `shared/.../recap/RecapShareCard.kt` — `card.score.toFixed(1)`
   - `shared/.../recap/RecapScenes.kt` — `scene.score.toFixed(1)`, `scene.avgScore.toFixed(1)`
   - `feature/meal/.../compose/ComposePlateScreen.kt` — `lat/lon.toFixed(5)`
4. Enabled `withHostTest` on `:core:i18n` + `NumberFormattingTest` (5 cases) to lock the formatter,
   including the floating-point tie cases.

## Verification

- `:core:i18n:testAndroidHostTest` → `tests="5" skipped="0" failures="0" errors="0"`.
- `:androidApp:assembleDebug` → `BUILD SUCCESSFUL` (compiles shared + feature:meal call-site changes).
- `:shared:testAndroidHostTest` + `:feature:meal:testAndroidHostTest` → `BUILD SUCCESSFUL` (no regression).

## Convention going forward

Never put a `%f` (or any conversion other than `%n$d`/`%n$s`) in a `strings.xml`. Pre-format every
decimal with `toFixed(n)` and pass it to a `%n$s` placeholder.
