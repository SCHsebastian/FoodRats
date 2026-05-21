# :feature:stats

Client-side stats over the last 30 days for the active crew: streaks, leaderboards, three-tab competitive layout. Read-only consumer of `MealReadPort` (bound by `:feature:meal`). No Gradle dependency on `:feature:meal`.

## Authoritative references

- Spec — `docs/specs/2026-05-16-foodrats-ddd-kmp-design.md` §2.1 (Stats bounded context), §3.1 (cross-context read pattern).
- Latest design — `docs/specs/2026-05-20-competitive-stats-redesign-design.md` (three-tab + animations, landed `0043ada`).
- Module README — `feature/stats/README.md`.
- Root `CLAUDE.md` — "Architectural rules" (cross-context reads via ports), "Recent decisions: Description replaces tags" (the `TagVariety` leaderboard was removed with the tag concept — see commit `690e50a`).

## Local rules

- No tag-based stats — `FoodTag` is gone. Don't reintroduce a leaderboard whose key is free-text.
- Computations stay **client-side** for the MVP (operates over the 30-day `MealReadPort` window). Don't add a server aggregation path without a fresh spec.
- This module stays on JVM 11 — no Firebase here.

## Test

`./gradlew :feature:stats:testAndroidHostTest`.
