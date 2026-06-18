# Store-release finalize — 2026-06-18

Goal (user): "completa todo lo restante para poder subir la app a app store y play store."

## Decisions (user, 2026-06-18)
1. **No store accounts yet** → prepare ALL codeable artifacts + deliver complete from-scratch runbook.
2. **Dirty tree (119 files)** → finalize, verify (host tests + assembleDebug + iOS link + functions), commit & push.
3. **Deploys** → attempt myself if creds/login present locally; confirm before destructive actions; else hand exact commands.

## Dirty-tree composition (understood)
- **minotaur-mode** easter egg — `:core:designsystem` (Fur.kt, MinotaurUnlock.kt, SemanticColors, FoodRatsTheme, FrCard) + catalogApp story. Device-verified per its PROGRESS; gates were green.
- **crew refactor** — CrewDataSource/CrewFirestoreDataSource/FirebaseCrewRepository/CrewModule/CreateCrew/JoinCrew/AcceptInvite/CrewPicker + tests. In-flight.
- **full-app-review auto-repairs** — ~30 fixes across modules (see 2026-06-17-full-app-review/REPAIR_PLAN.md). APPLIED but never verified/committed.
- CLAUDE.md + feature CLAUDE.md edits; LegacyDevCrewMigration.kt deleted.

## Plan
- [ ] P1 — Verify dirty tree green (host tests, assembleDebug, catalog, iOS link, functions). Fix breakage.
- [ ] P1 — Commit logically (minotaur / crew / review-repairs / docs) + push.
- [ ] P2 — Codeable store-prep: fastlane metadata (Play+ASC, en/es), verify privacy manifests, OG/unfurl page for /invite, version script sanity.
- [ ] P3 — Deploys (attempt if creds): functions, rules, storage, hosting, seed, IAM. Else exact commands.
- [ ] P4 — Consolidated from-scratch human runbook (accounts, signing, secrets, privacy declarations, on-device smoke, baseline profile).

## Status log
- (start) Mapped state; human.md + PUBLICATION.md + cicd-runbook.md are the gate sources.
- functions verify GREEN: tsc build OK + vitest 114/114 passed.
- Creds: firebase-tools logged in (foodrats-de4ec); gcloud authed (same project). ADC NOT set up (seed needs `gcloud auth application-default login`).
- Dirty tree did NOT build — auto-repairs introduced KMP breakage. Fixed:
  1. `ingredient/SelectIngredientsScreen.kt` — `remember` was inside a `LazyListScope` lambda → hoisted to composable scope (ingredient-02).
  2. `core/data/.../LocationPermissionLauncherHolderTest.kt` — FakeLauncher used wrong `ActivityOptionsCompat` pkg + outdated `getContract()` → fixed to `androidx.core.app.ActivityOptionsCompat` + `override val contract` (core-data-01 test).
  3. `core/domain/.../FrLog.kt` — bare `@Volatile` unresolved on Native → `import kotlin.concurrent.Volatile` (core-domain-05).
  4. `feature/stats/.../ObserveStatsUseCase.kt` — `dayTicker()` infinite `flow{while(true){emit;delay}}` spun 100% CPU forever under runTest+FixedClock (virtual-time advance). REVERTED stats-01 dayTicker; KEPT stats-02 HistoricResult error-surfacing. Removed rollover test + MutableClock/RecordingRead helpers; kept SplitRangeRead + historic_error test.
- Codeable store-prep DONE (independent of build): `docs/store-release/LISTING-COPY.md` (en/es Play+ASC copy), `docs/store-release/RELEASE-CHECKLIST.md` (master from-scratch runbook).
- firestore.indexes.json already declares authorId collection-group overrides (covers human.md §C).
</content>
