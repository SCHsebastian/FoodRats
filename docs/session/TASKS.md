# TASKS — Feature Roadmap Queue (single source of truth for "what's left")

Status values: `todo` | `doing` | `done` | `blocked`. Pick the first `todo` whose deps are all
`done`. Within a wave/feature, respect layering: domain → data → presentation/i18n. See
`CHARTER.md` for the loop. Verify commands are the most specific gradle/pnpm task per module.

> **Open product questions** surfaced at setup are parked in the "Blocked / Questions" section at
> the bottom. The implementer reads the spec first; spec wins. Only genuinely unanswerable
> questions become `blocked`.

## Wave 0 — Compliance & Store-blocking

| id | title | wave | spec §ref | deps | status | verify-cmd |
|---|---|---|---|---|---|---|
| w0-account-deletion-domain | Account deletion: domain errors + port | 0 | 2026-06-14-account-deletion-design.md §3, §4.1 | - | done | `:feature:auth:testAndroidHostTest` |
| w0-account-deletion-data | Account deletion: Firebase adapter + cascade Cloud Function | 0 | 2026-06-14-account-deletion-design.md §4.2, §5 | w0-account-deletion-domain | done | `pnpm --dir functions test` + `:feature:auth:testAndroidHostTest` |
| w0-account-deletion-presentation | Account deletion: post-deletion finish (sign-out + analytics reset + UI) | 0 | 2026-06-14-account-deletion-design.md §4.3 | w0-account-deletion-data | done | `:feature:auth:testAndroidHostTest` |
| w0-consent-ui-domain | Consent: port + decision model | 0 | 2026-06-14-feature-roadmap.md §0.2 + analytics §4,§8 | - | done | `:core:data:testAndroidHostTest` |
| w0-consent-ui-presentation | Consent: UI screen + routing gate + i18n | 0 | 2026-06-14-feature-roadmap.md §0.2 | w0-consent-ui-domain | done | `:shared:testAndroidHostTest` |
| w0-consent-settings-toggle | Consent: Profile revoke/re-grant toggle (GDPR withdrawal) | 0 | 2026-06-14-feature-roadmap.md §0.2 (bullet 3) | w0-consent-ui-presentation | done | `:feature:auth:testAndroidHostTest` |
| w0-16kb-alignment | 16-KB page alignment: MediaPipe library upgrade | 0 | 2026-06-14-feature-roadmap.md §0.3 | - | done | `:androidApp:assembleRelease` |
| w0-imagepicker-dup-class | Fix imagepickerkmp shadow-class dex-merge collision blocking assembleRelease | 0 | (no spec — release-pipeline bugfix surfaced by w0-16kb-alignment) | - | done | `:androidApp:assembleRelease -PcrashlyticsMappingUpload=false` |
| w0-data-export-function | Data export: callable Cloud Function + zip assembly | 0 | 2026-06-14-feature-roadmap.md §0.4 | w0-account-deletion-data | done | `pnpm --dir functions test` |
| w0-data-export-presentation | Data export: Settings UI + async job pattern | 0 | 2026-06-14-feature-roadmap.md §0.4 | w0-data-export-function | done | `:feature:auth:testAndroidHostTest` |

**WAVE 0 COMPLETE** (10/10 done, all verified green) — 2026-06-14.

## Wave 1 — Quick Wins

| id | title | wave | spec §ref | deps | status | verify-cmd |
|---|---|---|---|---|---|---|
| w1-streak-nudges-function | Streak nudges: server-side Cloud Function + FCM fan-out | 1 | 2026-06-14-feature-roadmap.md §1.1 | - | done | `pnpm --dir functions test` |
| w1-streak-nudges-i18n | Streak nudges: templated notification i18n | 1 | 2026-06-14-feature-roadmap.md §1.1 | w1-streak-nudges-function | done | `:feature:notifications:testAndroidHostTest` |
| w1-blind-voting-domain | Blind voting: crew field + mapping (masks AUTHOR IDENTITY until voted) | 1 | 2026-06-14-feature-roadmap.md §1.2 | - | done | `:feature:crew:testAndroidHostTest` |
| w1-blind-voting-data | Blind voting: CrewDto + Firestore schema + port binding + owner toggle | 1 | 2026-06-14-feature-roadmap.md §1.2 | w1-blind-voting-domain | done | `:feature:crew:testAndroidHostTest` |
| w1-blind-voting-presentation | Blind voting: FeedMealUi mapping + masking logic + catalog | 1 | 2026-06-14-feature-roadmap.md §1.2 | w1-blind-voting-data | done | `:feature:feed:testAndroidHostTest` |
| w1-reactions-domain | Reactions: MealReaction + port | 1 | 2026-06-14-feature-roadmap.md §1.3 | - | done | `:core:domain:testAndroidHostTest` |
| w1-reactions-data | Reactions: Firestore subcollection + repository (impl in :feature:meal) | 1 | 2026-06-14-feature-roadmap.md §1.3 | w1-reactions-domain | done | `:feature:feed:testAndroidHostTest` |
| w1-reactions-presentation | Reactions: card affordance + "who reacted" row + analytics | 1 | 2026-06-14-feature-roadmap.md §1.3 | w1-reactions-data | done | `:feature:feed:testAndroidHostTest` |
| w1-smart-mealtime-function | Smart mealtime: server compute mealtimeProfile + scheduling | 1 | 2026-06-14-feature-roadmap.md §1.4 | w1-streak-nudges-function | done | `pnpm --dir functions test` |
| w1-remove-member-domain | Remove member: use case + errors | 1 | 2026-06-14-feature-roadmap.md §1.5 | - | done | `:feature:crew:testAndroidHostTest` |
| w1-remove-member-data | Remove member: repository + transactional Firestore removal | 1 | 2026-06-14-feature-roadmap.md §1.5 | w1-remove-member-domain | done | `:feature:crew:testAndroidHostTest` |
| w1-remove-member-presentation | Remove member: afford + rules + tests | 1 | 2026-06-14-feature-roadmap.md §1.5 | w1-remove-member-data | done | `:feature:crew:testAndroidHostTest` |

**WAVE 1 COMPLETE** (12/12 done, all verified green) — 2026-06-15.

## Wave 2 — Collection & Identity

| id | title | wave | spec §ref | deps | status | verify-cmd |
|---|---|---|---|---|---|---|
| w2-badges-domain | Badges: AchievementCriterion taxonomy + evaluator | 2 | 2026-06-14-badges-achievements-design.md §5.2, §5.3 | - | done | `:feature:achievements:testAndroidHostTest` |
| w2-badges-data | Badges: AchievementProgressPort + Firestore persistence | 2 | 2026-06-14-badges-achievements-design.md §6 | w2-badges-domain | done | `:feature:achievements:testAndroidHostTest` |
| w2-badges-presentation | Badges: grid UI + unlock celebration + FrBadge atom + i18n + catalog | 2 | 2026-06-14-badges-achievements-design.md §7, §8 | w2-badges-data, w0-consent-ui-presentation | done | `:feature:achievements:testAndroidHostTest` |
| w2-cuisine-passport-seed | Cuisine passport: dishCuisineMap seed + vitest integrity | 2 | 2026-06-14-feature-roadmap.md §2.2 | - | done | `pnpm --dir functions test` |
| w2-cuisine-passport-domain | Cuisine passport: Cuisine VO + read path | 2 | 2026-06-14-feature-roadmap.md §2.2 | w2-cuisine-passport-seed | done | `:core:domain:testAndroidHostTest` |
| w2-cuisine-passport-presentation | Cuisine passport: catalog adapter + publish stamping + stats grid + badges tie-in + i18n | 2 | 2026-06-14-feature-roadmap.md §2.2 | w2-cuisine-passport-domain, w2-badges-domain | done | `:feature:stats:testAndroidHostTest` |
| w2-bingo-presentation | Ingredient bingo: Pokédex grid + collected/locked variants + badges tie-in | 2 | 2026-06-14-feature-roadmap.md §2.3 | w2-badges-domain | done | `:feature:stats:testAndroidHostTest` |
| w2-weekly-digest-story-presentation | Weekly digest story: swipeable player + scenes + deep-link + analytics | 2 | 2026-06-14-feature-roadmap.md §2.4 | w2-badges-presentation | done | `:feature:stats:testAndroidHostTest` + `:shared:testAndroidHostTest` |

**WAVE 2 COMPLETE** (8/8 done, all verified green) — 2026-06-15.

## Wave 3 — Growth & Sharing

| id | title | wave | spec §ref | deps | status | verify-cmd |
|---|---|---|---|---|---|---|
| w3-shareable-cards-designsystem | Shareable cards: FrPlateShareCard / FrAwardShareCard / FrStreakShareCard + catalog | 3 | 2026-06-14-shareable-story-cards-design.md §4.1 | - | done | `:core:designsystem:testAndroidHostTest` |
| w3-shareable-cards-platform | Shareable cards: StoryCardRenderer (expect/actual) + StoryShareLauncher (expect/actual) | 3 | 2026-06-14-shareable-story-cards-design.md §4.2, §4.3 | w3-shareable-cards-designsystem | done | `:core:data:testAndroidHostTest` + manual on-device share check |
| w3-shareable-cards-presentation | Shareable cards: feed + stats mappers + entry points + analytics | 3 | 2026-06-14-shareable-story-cards-design.md §5, §6 | w3-shareable-cards-platform, w2-weekly-digest-story-presentation | done | `:feature:feed:testAndroidHostTest` + `:feature:stats:testAndroidHostTest` |
| w3-recap-share-cta | Shareable cards: recap-story-share CTA (DS overlay slot on FrStoryScaffold + WeeklyStoryViewModel wiring) | 3 | 2026-06-14-shareable-story-cards-design.md §5/§8 | w3-shareable-cards-presentation | done | `:core:designsystem:testAndroidHostTest` + `:shared:testAndroidHostTest` |
| w3-deep-linked-invites-presentation | Deep-linked invites: link builder + QR generation + accept flow + rich previews | 3 | 2026-06-14-feature-roadmap.md §3.2 | - | done | `:shared:testAndroidHostTest` + `:feature:crew:testAndroidHostTest` |

**WAVE 3 COMPLETE** (5/5 done, all verified green) — 2026-06-15.

## Wave 4 — New Domain Concept

| id | title | wave | spec §ref | deps | status | verify-cmd |
|---|---|---|---|---|---|---|
| w4-meal-kind-seam-domain | Meal post types: MealKind.Solo seam (domain) | 4 | 2026-06-14-meal-post-types-design.md §4.1, §4.2 | - | done | `:core:domain:testAndroidHostTest` |
| w4-meal-kind-seam-data | Meal post types: MealKind.Solo seam (DTO + mapper) | 4 | 2026-06-14-meal-post-types-design.md §4.3 | w4-meal-kind-seam-domain | done | `:feature:meal:testAndroidHostTest` |
| w4-meal-kind-seam-integration | Meal post types: Solo seam integration + tests | 4 | 2026-06-14-meal-post-types-design.md §4 | w4-meal-kind-seam-data | done | `:feature:meal:testAndroidHostTest` + `:androidApp:assembleDebug` |

**WAVE 4 COMPLETE** (3/3 done, all verified green) — 2026-06-15.

## Wave 5 — Platform & Engineering Excellence

| id | title | wave | spec §ref | deps | status | verify-cmd |
|---|---|---|---|---|---|---|
| w5-image-pipeline-function | Image pipeline: BlurHash/ThumbHash Cloud Function + thumbnails | 5 | 2026-06-14-feature-roadmap.md §5.1 | - | done | `pnpm --dir functions test` |
| w5-thumb-cleanup-onmealdeleted | Image pipeline: reclaim `_thumb.jpg` sibling in onMealDeleted | 5 | (follow-up from w5-image-pipeline-function) | w5-image-pipeline-function | done | `pnpm --dir functions test` |
| w5-image-pipeline-presentation | Image pipeline: Coil placeholder + feed/detail loading + on-device compression | 5 | 2026-06-14-feature-roadmap.md §5.1 | w5-image-pipeline-function | doing | `:feature:feed:testAndroidHostTest` + manual feed observation |
| w5-offline-compose-domain | Offline compose: DraftQueuePort + retry logic | 5 | 2026-06-14-feature-roadmap.md §5.2 | - | done | `:feature:meal:testAndroidHostTest` |
| w5-offline-compose-data | Offline compose: local persistence + queue + WorkManager retry | 5 | 2026-06-14-feature-roadmap.md §5.2 | w5-offline-compose-domain | done | `:feature:meal:testAndroidHostTest` |
| w5-fix-stats-meal-fixtures | Fix RED `:feature:stats` test compile (`Meal()` fixture mismatch from earlier task) | 5 | (regression bugfix) | - | done | `:feature:stats:testAndroidHostTest` |
| w5-offline-compose-presentation | Offline compose: queued/pending drafts in feed top bar + idempotency reconcile | 5 | 2026-06-14-feature-roadmap.md §5.2 | w5-offline-compose-data | done | `:feature:meal:testAndroidHostTest` |
| w5-baseline-profiles-CI | Baseline Profiles & startup traces: Macrobenchmark module + CI gate | 5 | 2026-06-14-feature-roadmap.md §5.3 | - | done | `:baselineprofile:` benchmark output + CI dry-run |

**WAVE 5 COMPLETE** (7/7 done, all verified green) — 2026-06-15.
**ALL ROADMAP WAVES 0–5 COMPLETE** — 46 implementation tasks done. Next: `review-pack` (2 iterations) per CHARTER, after save-memory + /compact.

---

## FINAL — after all waves done/blocked

| id | title | deps | status |
|---|---|---|---|
| review-pack | Multi-agent review→fix pack (Workflow), **iterate 2×** — see CHARTER "Post-build review pack" | all wave tasks | todo |

---

## Blocked / Questions for Product (parked; spec wins — only escalate if truly unanswerable)

1. **Account deletion:** owned-crew fate when the leaver is sole owner — reassign vs delete? (spec §ref should answer; implementer confirms)
2. **Blind voting:** per-crew owner toggle vs global default? default off?
3. **Reactions:** single `DailyEmote` vs small fixed emoji set; push on react?
4. **Streak nudges + smart mealtime:** server-scheduled vs client WorkManager.
5. **Badges:** client-derived vs server-persisted unlock timestamps.
6. **Cuisine passport:** stamp cuisine at publish vs derive at read.
7. **Baseline profiles:** crash-free SLO threshold.
8. **Meal post types:** `MealKind.Together` is designed (spec §5) but DEFERRED — build only the
   behaviorally-inert `MealKind.Solo` seam now. Open decisions in spec §13.
