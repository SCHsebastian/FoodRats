# :core:presentation

The shared MVI plumbing: `MviViewModel<State, Intent, Effect>` base (~80 LOC). Every feature's ViewModel extends `MviViewModel`; every feature's `<Feature>Error.toStringKey()` extension function maps its error tree to a `StringKey`.

## Authoritative references

- Spec — `docs/specs/2026-05-16-foodrats-ddd-kmp-design.md` §4.2 (`:core:presentation`), §4.5 (MVI base).
- Root `CLAUDE.md` — "Architectural rules" (MVI single source of truth — state lives only in `MviViewModel`'s `State`, no parallel `MutableStateFlow`).
- Recent change — "FeedViewModel — true MVI single source of truth" (commit `fbf5e40`).

## Local rules

- `handle(intent)` is intentionally impure (suspends, calls use cases). Test against state-flow emissions via Turbine.
- Reducers go through `update { it.copy(...) }`. Reads use `currentState`. Never expose a `MutableStateFlow` from a subclass — derive flows from `state.map { … }.distinctUntilChanged()` instead. `FeedViewModel` is the reference pattern.
- Tests using `UnconfinedTestDispatcher` should `expectMostRecentItem()` rather than awaiting intermediate emissions — `MviViewModel` coalesces updates.

## Test

`./gradlew :core:presentation:testAndroidHostTest` (host task enabled 2026-07-13; also runs on the iOS sim lane via `:core:presentation:iosSimulatorArm64Test`).
