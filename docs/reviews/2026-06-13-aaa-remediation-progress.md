# AAA+ Remediation — COMPLETE (2026-06-13 → 2026-06-14)

Roadmap from `docs/reviews/2026-06-13-aaa-architecture-review.md` (overall grade **A-**).
Order requested by the user: security → build-logic → fan out. **All 20 roadmap items are
addressed** (a few sub-items deliberately deferred — see the table). Every change is on its own
`fix/*`/`chore/*`/`test/*`/`feat/*` branch off `main`, and all of them are composed into one
integrated branch with a green full gate.

## THE DELIVERABLE

- **`aaa-remediation-complete`** (`a2db9bd`) — the single branch with **all** fixes + the Konsist
  fitness functions + the #20 storage-cleanup leftover. 43 commits ahead of `main`. Built as:
  - `aaa-remediation`  (`52c2c4c`) — first 11 branches merged, full gate green (480 host tests).
  - `aaa-remediation-2` (`1d3b1d3`) — + 8 more branches, full gate green (552 host tests).
  - `feat/fitness-functions` (`f9df790`) — + #4 fitness functions (557 host tests).
  - `chore/storage-cleanup-on-publish-fail` (`a2db9bd`) — + #20 orphan-blob cleanup (meal 79/0).
- Nothing was pushed and nothing was merged to `develop`/`main` — that's left to the user's
  develop→main PR flow. The per-item branches are preserved so you can open a PR per fix OR just
  merge `aaa-remediation-complete`.

## FINAL GATE (on `f9df790`, all green)
- 10-module Android host suite + the 5 new fitness functions: **557 tests, 0 failures**
  (`:core:domain :core:designsystem :feature:{auth,crew,meal,feed,stats,notifications,ingredient} :shared`).
- `:androidApp:assembleDebug` — BUILD SUCCESSFUL.
- `:shared:linkDebugFrameworkIosSimulatorArm64` — BUILD SUCCESSFUL.
- `functions`: `tsc --noEmit` clean + vitest **20/20**.
- `firestore-tests`: **26/26** (rules emulator).

## ROADMAP STATUS

| # | Sev | Item | Branch | Status |
|---|---|---|---|---|
| 1 | P0 | accounts read-rule PII leak | `fix/firestore-security-rules` (216e8a8) | ✅ done |
| 2 | P1 | meal-create award self-stuffing + digest recompute | `fix/firestore-security-rules` | ✅ done |
| 3 | P1 | build-logic / catalog bundles | `chore/build-logic-catalog-bundles` (44fe923) | ✅ done (convention-plugin layer still deferred — AGP 9 DSL risk) |
| 4 | P1 | **Konsist fitness functions + CI** | `feat/fitness-functions` (f9df790) | ✅ done — 5 rules, teeth-proven; surfaced+fixed the uncatalogued `FrSettingsPicker` |
| 5 | P1 | AI detections auto-confirmed | `fix/ingredient-confirm-only` (48e374a) | ✅ done |
| 6 | P1 | MealDetail comment-stream freeze | `fix/meal-detail-comment-freeze` (b4dd7ea) | ✅ done |
| 7 | P2 | iOS tested nowhere; CI skips modules | (partial) | ⚠️ partial — `shared` host tests + `RouteAccessTest` added; `:feature:ingredient` added to CI; **iOS sim tests on a Mac runner still need the self-hosted runner + Firebase SPM** (infra, not codeable here) |
| 8 | P1 | error mappers by message-substring | `fix/typed-error-mappers` (00b371e) | ✅ done — typed `FirebaseFault`/`AuthFault` seam; publish & rate mis-mappings fixed+locked |
| 9 | P2 | iOS share dead; startKoin unguarded | `fix/ios-share-and-koin-guard` (567da15) | ✅ done — Kotlin link-verified; Swift glue needs a device build |
| 10 | P2 | push English-only; no in-app bus consumer | `fix/notification-inapp-consumer` (4f90758) | ✅ done (foreground consumer + i18n); server data-only push left as a deliberate follow-up |
| 11 | P2 | Crew anemic; rating raterId | `fix/crew-aggregate-raterid` (2abb4de) | ✅ done — cap single-sourced to `CrewSize.MAX`, stays atomic in the txn |
| 12 | P1 | zero repository-impl tests | `test/meal-repository-impl` (bf6f0b0), `test/crew-repository-impl` (8a8273a) | ✅ done — port seams + canonical fakes; 18 meal + 27 crew repo tests |
| 13 | P1 | 16-KB `.so` Play blocker | `fix/mediapipe-16kb-alignment` (6d61d93) | ✅ done — tasks-vision 0.10.14→0.10.35; `.so` LOAD `p_align=0x4000` PASS; **user must `pod install`** |
| 14 | P2 | no observability/flags; dead analytics | `feat/observability-killswitch` (f6e93e5) | ✅ done — dead dep removed; FrLog→Crashlytics release sink; `FeatureFlagPort` kill-switch (iOS RemoteConfig is a default-on TODO) |
| 15 | P2 | storage objects world-readable | — | ⏭️ SKIPPED by design — plate/avatar reads use `getDownloadUrl()` token URLs that bypass Storage rules; real fix = drop download tokens / signed URLs (larger, deferred) |
| 16 | P2 | IngredientSlug/CrewName VOs | `fix/ingredient-crewname-vos` (5d2dec0) | ✅ done — `IngredientSlug.of`/`CrewName.of`; ~5 prod + ~46 test call-sites converted |
| 17 | P2 | NavigateDeepLink clobbers back stack | `fix/navigate-deeplink-guard` (02fb014) | ✅ done — guard mirrored + launchSingleTop |
| 18 | P2 | no Koin verify; route markers; dead seams | `chore/remove-dead-seams` (e3263dc) + `fix/koin-verify-route-session` (fcca515) | ✅ done — dead CrewMembersPort/ClassifyPlateUseCase/dup TimeZone removed; exhaustive `Route.requiresSession()`; per-module `verify()` ×7 |
| 19 | P2 | weeklyDigest unbounded scan; a11y | `fix/weekly-digest-pagination` (a1530b7) + `fix/a11y-content-descriptions` (79e1e09) | ✅ done — cursor pagination @200/page; bounded a11y content-description pass |
| 20 | P3 | consistency & write-path hygiene cluster | `chore/consistency-quickwins` (bcd9653) + `chore/p3-hygiene` (b142c5d) + `chore/storage-cleanup-on-publish-fail` (a2db9bd) | ✅ done — currentState, FrLogo dark mode, ShareController→domain, Account/Ingredient mapper tests, resolvePlural, FCM SupervisorJob, coordinator dispatcher, Maps-key runbook doc, **and** best-effort Storage delete() on publish-write failure (orphan-blob cleanup, error-preserving) |

## INTEGRATION MERGE ORDER (for reference / re-doing)
Pass 1 → `aaa-remediation` (from `main`): build-logic, weekly-digest-pagination (contains
firestore-security-rules), remove-dead-seams, crew-aggregate-raterid, ingredient-crewname-vos,
ingredient-confirm-only, typed-error-mappers, meal-detail-comment-freeze, navigate-deeplink-guard,
notification-inapp-consumer, consistency-quickwins.
Pass 2 → `aaa-remediation-2` (from `aaa-remediation`): mediapipe-16kb-alignment,
crew-repository-impl, meal-repository-impl, koin-verify-route-session, observability-killswitch,
ios-share-and-koin-guard, p3-hygiene, a11y-content-descriptions. (Then `feat/fitness-functions`.)
Hotspots resolved "keep both": `shared/iosMain/MainViewController.kt` (#9 share+koin-guard + #14
FrLog sink), `gradle/libs.versions.toml` (#13 mediapipe + #14 firebase-config), stats `StatsStringKey`
+ strings (#20 plurals + #19b PlatePhotoFormat), `MealModule.kt` (#12 ports + #20 coordinator
dispatcher). One follow-up fix needed: meal Koin `verify()` extraTypes += `FeatureFlagPort`,
`FirebaseAuth`.

## REMAINING MANUAL STEPS (not codeable in this environment)
1. **Merge** the branches (security first), or merge `aaa-remediation-complete` as one.
2. **`pod install`** in `iosApp/` to refresh `Podfile.lock` for MediaPipeTasksVision 0.10.35 (#13).
3. **Deploy the security rules**: `pnpm dlx firebase-tools deploy --only firestore:rules --project foodrats-de4ec` (#1/#2). Consider wiring `firestore-tests` into CI.
4. **iOS device build/smoke**: the Swift glue for #9 (share sheet) and #10 (bridge) is NOT xcodebuild-verified here; tap-through on a device. Also the on-device 16-KB confirmation (#13).
5. **Cloud Console**: restrict the Maps API key per the new `docs/cicd-runbook.md` note (#20).
6. **iOS RemoteConfig** for the classifier kill-switch (Android is wired; iOS is a default-on TODO) (#14).
7. **CLAUDE.md tech-debt** trim once merged — the "AI-detected ingredients persist" note's *cause* attribution is stale (the `mergedIngredientSlugs` display-union was already deleted in #23; the live cause was the draft reducer, fixed by `fix/ingredient-confirm-only`); the 16-KB note closes after the device check.

## WORKTREE CLEANUP
~18 agent worktrees remain under `.claude/worktrees/agent-*` (one per fix; all committed, branches
persist independently). Remove anytime with `git worktree list` then
`git worktree remove --force <path>` per entry (branches are NOT deleted).
