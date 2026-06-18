# feature-achievements repair

## achievements-01 (LOW perf)
Moved `.debounce(400.milliseconds)` from the end of the outer `flatMapLatest` chain to immediately after `combine(range, unlocks) { ... }` inside the `else` branch. Error flows (`flowOf(Result.failure(...))`) now emit without the 400 ms delay; only the success-path snapshot bursts are debounced. No tests added (LOW mechanical).

## achievements-03 (LOW cleanup)
Removed the unused `import androidx.compose.runtime.collectAsState` from `AchievementsScreen.kt`. State is collected via `collectAsStateWithLifecycle` (still present). No tests added.

## achievements-04 (LOW cleanup)
Removed both `@Suppress("DEPRECATION")` annotations from `EpochDayFormat.kt` lines 17 and 19. `LocalDate.monthNumber` and `LocalDate.dayOfMonth` are standard, non-deprecated `kotlinx-datetime` properties. No tests added.

## Skipped
None.

## Build risk
None — all changes are mechanical (import removal, annotation removal, operator chaining order shift that preserves logic).
