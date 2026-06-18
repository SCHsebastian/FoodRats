# Repair report — feature-feed

## feed-04 (MEDIUM, applied)
MealDeleteError now maps to 3 dedicated FeedStringKey entries instead of collapsing onto
the comment-error strings; TODO removed.
- `i18n/FeedStringKey.kt`: added `DeleteMealErrorUnauthorized`, `DeleteMealErrorNotFound`,
  `DeleteMealErrorUnavailable` (+ 3 resource imports).
- `composeResources/values/strings.xml` + `values-es/strings.xml`: added
  `feed_delete_meal_error_{unauthorized,not_found,unavailable}` (en + es).
- `presentation/MealDeleteErrorToStringKey.kt`: rewrote the `when` to map each arm to its
  own key; deleted the TODO comment.
- Test: created `commonTest/.../presentation/MealDeleteErrorToStringKeyTest.kt` — per-arm
  assertions + an exhaustiveness-locking `when` over all three leaves (compile-fails if a
  new leaf is added unmapped).

## feed-03 (LOW perf, applied)
`presentation/feed/FeedViewModel.kt`: before the per-meal `getOrPut` loop inside
`observeReactions`' flatMapLatest, added `reactionFlows.keys.retainAll(parsed.toSet())` so the
dedup cache is capped to currently-visible meals (was growing unbounded across day/crew
navigation). Placed inside the non-empty `else` branch where `parsed` is in scope.

## feed-02 (MEDIUM correctness, applied — SUBSUMES feed-05)
`presentation/detail/MealDetailViewModel.kt` `observeComments()`: replaced the one-shot
`session.current.first()` / `crewOwner.observeOwner(...).first()` snapshots with reactive
flows — `viewerIdFlow` (map over `session.current`), `ownerIdFlow` (flatMapLatest over
`activeCrew.current` → `crewOwner.observeOwner`), combined into `rbacFlow`, then combined with
the comments flow so the identity-join re-derives row deletability whenever viewer or owner
changes. Added `map` + `distinctUntilChanged` imports.
- Test: added `comment_deletability_updates_when_crew_owner_changes` to
  `MealDetailCommentIdentityTest.kt` (+ a `MutableCrewOwnerPort` fake): a row authored by
  someone else flips from not-deletable to deletable the moment the viewer becomes crew owner.

## feed-06 (LOW — SKIPPED, false premise for commonMain)
`MealDetailScreen.kt:635` `text.uppercase()` is in **commonMain**. Kotlin common stdlib has no
`uppercase(Locale)` overload (`java.util.Locale` is JVM-only); the no-arg `String.uppercase()`
is already the locale-independent (Locale.ROOT-equivalent) form — it is the correct call. The
literal instruction `uppercase(Locale.ROOT)` would not compile here. Left unchanged. (Same
reasoning would apply to crew-06 / stats if those files are also commonMain — verified no
commonMain file in the repo uses `Locale.ROOT`.)

## feed-05 (SKIPPED — intentionally subsumed by feed-02)
feed-05 was the inferior one-shot-snapshot half; feed-02's reactive derivation supersedes it
per the task instructions. Not applied.

## Build risk
- feed-02 changes the comment-stream wiring shape (extra `combine` + reactive owner/viewer).
  `MealDetailCommentIdentityTest` (incl. rename-propagation, dedupe, second-batch, read-error)
  should still pass; watch for any change in emission cadence under UnconfinedTestDispatcher.
- New string resources require a feed module resource-gen; the generated `Res.string.*` symbols
  are referenced from `FeedStringKey.kt` — a clean build regenerates them.
