# 023 · w2-badges-domain

**Status:** done

**Summary (≤6 lines):**
- New `:feature:achievements` module (mirrors `:feature:stats`, Firebase-free, JVM 11) with the full domain core: `AchievementCriterion` sealed taxonomy, `Achievement`/`AchievementCatalog`, pure `AchievementEvaluator` over `AchievementSignals`, `AchievementStatus`, `AchievementError`, i18n key contract (en/es authored).
- Files: `settings.gradle.kts`; new `feature/achievements/` (build.gradle.kts; domain `model/*`, `AchievementEvaluator.kt`, `AchievementCatalog.kt`, `error/AchievementError.kt`; `i18n/AchievementStringKey.kt` + en/es strings; `presentation/AchievementErrorToStringKey.kt`; 4 commonTest files).
- Decisions: no `:core:domain` edits (port/error §6.1 + analytics leaf §13 are data/presentation scope); stayed JVM 11 / zero Firebase with a TODO for data task to bump JVM 17 + BOM; `AchievementIcon` payload-free enum in domain.
- Blockers: none.

**Verify (quoted):**
```
> Task :feature:achievements:testAndroidHostTest
BUILD SUCCESSFUL in 18s
(28 tests, 0 failures across 4 suites)
```

**Data handoff:** declare `AchievementProgressPort`+error in `:core:domain`, Firestore DTO/repo/mapper, bump build to JVM 17 + Firebase BOM, add `accounts/{uid}/achievements` rule.

Report: `docs/session/reports/w2-badges-domain.md` · Handoff: `docs/session/handoffs/w2-badges-domain.md`
