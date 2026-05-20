# :feature:feed

Bounded context: a crew's day-window of meals to view, vote on, and dive into.
Consumes `MealReadPort` and `MealRatingPort` from `:core:domain` (bound by
`:feature:meal`'s `mealModule`). No direct Gradle dependency on `:feature:meal`.

## Screens

- **`FeedScreen`** (`presentation/feed/`) — compact per-day list of meals for
  the active crew. Each row is `FrFeedMealRow`: a 64dp Coil thumbnail (Coil
  downsamples to the layout size, so the list stays fast on long crews + slow
  networks), dish name + author on two lines, average score badge (or
  "no votes yet" pill) on the right. Tapping a row navigates to the detail
  screen. The day cursor at the top scrolls back over a 30-day window via
  `FeedDay.previous()` / `next()`.

- **`MealDetailScreen`** (`presentation/detail/`) — full-quality padded photo
  (16dp inset, rounded), author row, dish title, tag chips, score summary,
  voter list, and the star-rating picker if the viewer is eligible. Loaded via
  `MealDetailViewModel(mealId, dayIso, …)` — observes the day's feed through
  the existing `ObserveFeedUseCase` and filters to the matching `mealId`. If
  the meal is gone (e.g. deleted between feed and detail), the screen shows
  `DetailNotFound`.

Both screens compose against `FrScreenScaffold` from `:core:designsystem` and
use the `FrFeedMealRow` molecule from `presentation/components/`. The older
`FrFeedMealCard` (full-bleed Instagram-style card) was the previous feed
visual; it's no longer referenced from FeedScreen but the file remains for
reference until the next cleanup pass.

## Domain layer

- **`FeedDay`** (`domain/model/`) — wraps `MealDay` with bounded prev/next
  helpers. MVP window is the last 30 days inclusive (`FeedDay.WINDOW_DAYS`).
- **`ObserveFeedUseCase`** (`domain/usecase/`) — combines `ActiveCrewProvider`
  + day flow, calls `MealReadPort.observeFeed`, maps read errors to
  `FeedError.Read.*`.
- **`FeedError`** (`domain/error/`) — sealed interface with `Session` and
  `Read` groups. Mapped to string keys in `presentation/FeedErrorToStringKey`
  (exhaustively tested in `FeedErrorToStringKeyTest`).

## MVI

- `FeedState` is day-centric: a single `FeedDay?` cursor + `meals:
  List<FeedMealUi>` for that day. The day cursor lives **only** in state;
  `ObserveFeedUseCase` is fed by `state.map { it.day }.filterNotNull()
  .distinctUntilChanged()`. This is the reference pattern for the rest of the
  app — see CLAUDE.md "FeedViewModel — true MVI single source of truth".
- `MealDetailState` carries the matched `FeedMealUi?`, `isLoading`, `notFound`,
  `pendingRate`, and `rateError`.

## i18n

`FeedStringKey` covers titles, day-navigation labels, empty / no-crew states,
read + rate error variants, voter and rating summaries (parameterised so
locales can re-order the `★ · count` glyphs), and the detail-screen specific
`DetailBackCta`, `DetailTitle`, `DetailNotFound`. All five language groups
(`feed_*`) live in `composeResources/values/strings.xml` and the `values-es/`
counterpart.

## Navigation

Routes are defined in `:shared/.../navigation/Route.kt`:

- `Route.Main` → `FeedScreen` (inside the `MainTab.Feed` inner graph).
- `Route.MealDetail(mealId, dayIso)` → `MealDetailScreen`. Back returns to the
  Feed tab via `controller.popBackStack()`.
