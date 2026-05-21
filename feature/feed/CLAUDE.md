# :feature:feed

Day-window view of a crew's meals (last 30 days) + meal detail screen with score / voter list / rating picker. Read-only consumer of `MealReadPort` + `MealRatingPort` (bound by `:feature:meal`). **No** Gradle dependency on `:feature:meal`.

## Authoritative references

- Spec — `docs/specs/2026-05-16-foodrats-ddd-kmp-design.md` §2.1 (Feed bounded context), §3.1 (cross-context read pattern — this module is the canonical consumer).
- Module README — `feature/feed/README.md` (screens, `FeedDay`, MVI state shape, navigation).
- Root `CLAUDE.md` — "Architectural rules" (cross-context reads via ports), **"FeedViewModel — true MVI single source of truth"** (this module is the reference pattern for the rest of the app).
- Recent change — "i18n covers separator glyphs" (commit `fbf5e40`).

## Local rules

- The day cursor (`FeedDay?`) lives **only** in `FeedState.day`. Feed the use case via `state.map { it.day }.filterNotNull().distinctUntilChanged()`. Never reintroduce a parallel `MutableStateFlow<FeedDay>` (that was the original bug fixed in `fbf5e40`).
- Coil 3 (`coil3.compose.AsyncImage`) for image loading — `ImageLoader` is installed by `installFeedImageLoader()` at app start on both platforms.
- This module stays on JVM 11 — no Firebase here.

## Test

`./gradlew :feature:feed:testAndroidHostTest`.
