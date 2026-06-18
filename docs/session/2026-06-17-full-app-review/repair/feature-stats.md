# Repair report — feature-stats (2026-06-17)

## stats-01 + stats-02 (ObserveStatsUseCase.kt) — done as one coherent edit

### stats-01 (MEDIUM, correctness): midnight rollover
- `today` was captured once via `clock.now()` inside `flatMapLatest`, so the 30-day current
  window, the 365-day historic window, and streak math all went stale across midnight.
- Added a private `dayTicker(): Flow<LocalDate>` that emits the current local date, then `delay`s
  until the next local midnight (`today + 1 day` `atStartOfDayIn(zone)`), re-emits, and repeats;
  `.distinctUntilChanged()` so a same-day re-check is a no-op. A non-positive wait falls back to a
  1-minute poll (clock-skew guard).
- Folded the ticker into the OUTER `combine(activeCrew.current, session.current, epoch, dayTicker())`
  so BOTH the query bounds and `today` rebuild on rollover. `today` now flows from the ticker into
  `flatMapLatest`/`compose` instead of being read from the clock inside the block.
- New imports: `delay`, `flow`, `atStartOfDayIn`, `plus`, `Duration.Companion.minutes`.

### stats-02 (MEDIUM, correctness): swallowed historic failures
- Introduced `private sealed interface HistoricResult { Disabled; Ok(meals); Err(error) }` and
  threaded it through the inner `combine`. The historic read no longer maps `Result.Err -> null`
  (which the UI couldn't distinguish from "not loaded yet" — the tab spun forever on failure).
- `compose` now derives `historicMeals` from `Ok` and `historicError` from `Err`.
- Added `StatsSnapshot.historicError: StatsError? = null` (default keeps the constructor's named-arg
  call sites and all existing tests green).
- `StatsViewModel`: the `Result.Ok` reducer now sets `historicError = r.value.historicError` and
  clears `historicLoading` when the historic read resolves either way (populated OR failed) — only
  keeps spinning while the Historic tab is open and we have neither yet. This retires the previously
  dead `StatsState.historicError` field (stats-05); `DismissError` already cleared it.
- `StatsScreen`: the Historic `window == null` branch now shows `FrErrorBanner(resolve(error.toStringKey()))`
  when `state.historicError != null` instead of `HistoricLoading()` forever. All text via `resolve`.

## stats-03 (MEDIUM, architecture) — FrPokedexCell.kt
- `Color.White` -> `LocalFrSemanticColors.current.onCelebration`. Renamed the local `celebration`
  val to `semantic` (now `semantic.celebration` / `semantic.onCelebration`). Dropped the unused
  `androidx.compose.ui.graphics.Color` import.

## stats-04 (LOW, cleanup) — decimal formatter de-duplication
- Created `presentation/components/ScoreFormatting.kt` with a single
  `internal fun formatOneDecimal(v: Float): String` (identical body to the 4 copies).
- Deleted the 4 private duplicates: `FrWindowSummaryCard.formatOneDecimal`,
  `FrBestPlatePodium.formatScore`, `FrRoastCard.formatScore`, `FrCookAwardCard.formatScore`.
  Call sites now use `formatOneDecimal`. Removed the now-unused `import kotlin.math.round` from all
  four files.

## Tests added/updated (ObserveStatsUseCaseTest.kt)
- `historic_error_surfaces_as_historicError_not_swallowed` — new `SplitRangeRead` fake serves the
  current window and the 365-day historic window from separate flows (span > 300 days = historic);
  historic read fails with `Unavailable`, asserts the snapshot stays `Result.Ok` with
  `historicError == StatsError.Read.Unavailable` and `historic == null` (locks stats-02).
- `window_and_today_roll_over_at_midnight` — starts the clock at 23:00, asserts `to == 05-21`, then
  moves the clock past midnight + `advanceTimeBy(61min)` and asserts `to == 05-22` (locks stats-01).
  Uses new `MutableClock` and `RecordingRead` fakes; added `advanceTimeBy` import.

## Skipped
- None. No DIRTY files touched.

## Build risk to watch
- Cannot run gradle here (parallel-build rule). The two new tests rely on virtual-time:
  `runTest` is StandardTestDispatcher-backed, the rollover delay is exactly 1h and we advance
  61min, so the tick should fire. If `advanceTimeBy` semantics differ on the pinned coroutines
  version, the rollover test is the one to check.
- `LocalDate.toEpochDays()` returns `Long` on the pinned kotlinx-datetime — subtraction vs `Int 300`
  compiles. Confirmed the symbol exists in the built core:domain artifact.
- `StatsScreen` historic-error branch is the only new UI path; it reuses existing `FrErrorBanner` +
  `toStringKey` (no new StringKeys needed — `StatsError.Read.*` already mapped).
