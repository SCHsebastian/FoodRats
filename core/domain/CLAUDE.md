# :core:domain

Pure-Kotlin contracts shared by every feature: `Result<T, E>`, sealed-interface errors, shared VOs (`AccountId`, `CrewId`, `MealId`, `MealDay`, `Score`), cross-context read ports (`MealReadPort`, `ActiveCrewProvider`, `AccountReadPort`, `CrewMembersPort`), `Clock`, `DispatcherProvider`, `CrashReporter`.

## Authoritative references

- Spec — `docs/specs/2026-05-16-foodrats-ddd-kmp-design.md` §3 (module layout), §5 (domain conventions), §5.6 (`Result<T,E>`), §5.7 (sealed-interface errors), §6 (dispatchers).
- Root `CLAUDE.md` — "Architectural rules" (sealed-interface errors, no stdlib `Result`, DispatcherProvider rules, no-Firebase/no-Android/no-Compose in domain).
- Recent change — "Domain errors uniformly sealed" (commit `fbf5e40`).

## Allowed dependencies

`kotlin-stdlib`, `kotlinx-datetime`, `kotlinx-coroutines-core`. Nothing else. Enforced by `KonsistRulesTest` in `src/androidHostTest/`.

## Test

`./gradlew :core:domain:testAndroidHostTest` — runs `commonTest` (KMP) + Konsist architecture tests.
