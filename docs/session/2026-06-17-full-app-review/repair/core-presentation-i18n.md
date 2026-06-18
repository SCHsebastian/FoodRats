# Repair: core-presentation-i18n

## cpi-01 — MviViewModelTest: UnconfinedTestDispatcher + expectMostRecentItem

**File:** `core/presentation/src/commonTest/.../mvi/MviViewModelTest.kt`

Added `@BeforeTest { Dispatchers.setMain(UnconfinedTestDispatcher()) }` and `@AfterTest { Dispatchers.resetMain() }` so the ViewModel's `viewModelScope` uses the test dispatcher. Changed the state-test assertion from `awaitItem()` to `expectMostRecentItem()` after the `Increment` intent — `awaitItem()` can race against transient intermediate emissions under `UnconfinedTestDispatcher`; `expectMostRecentItem()` is the recommended pattern per the project CLAUDE.md.

**Tests updated:** existing `intent_updates_state` test (uses `expectMostRecentItem` now); `intent_emits_effect` unchanged (effects channel, not coalesced state).

## cpi-04 — MviViewModel: close effects channel on onCleared

**File:** `core/presentation/src/commonMain/.../mvi/MviViewModel.kt`

Added `override fun onCleared()` that calls `super.onCleared()` then `_effects.close()`. Without this, the BUFFERED channel is never closed when the ViewModel is cleared, which can keep downstream `receiveAsFlow()` collectors suspended indefinitely and leak memory.

**Tests added:** none required (mechanical LOW-risk structural fix; no observable behavior change for correct consumers; the close just terminates the flow).

## cpi-05 — NumberFormatting: guard against NaN/Infinity

**File:** `core/i18n/src/commonMain/.../i18n/NumberFormatting.kt`

Added `require(isFinite()) { "toFixed requires a finite value, was $this" }` as the first check in `toFixed()`. Without this guard, passing `NaN` or `Infinity` silently produces garbage output (e.g. `"NaN.00"` or overflow in `roundToLong()`).

**File:** `core/i18n/src/commonTest/.../i18n/NumberFormattingTest.kt`

Added three new test cases: `nan_is_rejected`, `positive_infinity_is_rejected`, `negative_infinity_is_rejected` — all assert `IllegalArgumentException`.

## Skipped

- **cpi-02** (EventsEffect move): deferred per task instructions.
- **cpi-03** (StringKey sealed interface): correctly identified as invalid — `StringKey` is implemented by enums in 11+ separate Gradle modules; sealed interfaces require all implementors in the same compilation unit. Not applied.

## Build risk

None expected. All changes are additive (new override, new guard, new tests). No public API signatures changed. The `onCleared` override only adds resource cleanup. The `isFinite()` guard is a new precondition; any caller passing NaN/Infinity was already producing incorrect output.
