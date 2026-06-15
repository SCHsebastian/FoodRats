# FoodRats — Feature Roadmap & Task Breakdown

_Created 2026-06-14 · branch `develop` · planning artifact, not a spec._

A task breakdown for 19 candidate features, grounded in the real module graph, ports, and
conventions. Checkboxes are actionable steps, not designs — each large feature that ships should
still get a `docs/specs/` design first (flagged with **needs spec**).

## How to read this

Leverage tag on each feature:
- 🟢 **leverages existing** — mostly a presentation/wiring layer over code+data already present.
- 🟡 **new module/port** — architecture is ready, but new domain surface.
- 🔴 **heavy** — new ML/platform/infra investment.

Suggested wave order (top = do first): compliance → quick wins → collection/identity →
growth → new domain concept → platform. Dependencies called out per feature.

**Specs written (2026-06-14)** — the four "needs spec" items now have designs in `docs/specs/`:
- 0.1 Account deletion → [`2026-06-14-account-deletion-design.md`](../specs/2026-06-14-account-deletion-design.md)
- 2.1 Badges / achievements → [`2026-06-14-badges-achievements-design.md`](../specs/2026-06-14-badges-achievements-design.md)
- 3.1 Shareable story cards → [`2026-06-14-shareable-story-cards-design.md`](../specs/2026-06-14-shareable-story-cards-design.md)
- 4.1 Meal post types (`Solo` now / `Together` deferred) → [`2026-06-14-meal-post-types-design.md`](../specs/2026-06-14-meal-post-types-design.md)

---

## Definition of Done (applies to every feature — don't repeat per task)

Every feature touching a module satisfies the house rules before it's "done":

- [ ] **Domain**: ports declared in `:core:domain`; errors are `sealed interface` + `data object` leaves (never enums, no `Unknown`); value objects are `@JvmInline value class` with `.of()` validation; no Firebase/Android/Compose in `:core:domain` (Konsist-enforced).
- [ ] **Data**: DTO + datasource + repository in `data/firebase/`; exactly one `withContext(dispatchers.io)` per public repo method; Firebase types never leak past the data layer.
- [ ] **Presentation**: `MviViewModel` subclass, `Contract` (State/Intent/Effect), single source of truth in `State` (no parallel `MutableStateFlow`); `<Feature>StringKey` enum; `<Feature>Error.toStringKey()` exhaustive `when` + `*ErrorToStringKeyTest`.
- [ ] **i18n**: en + es strings, **including glyphs/separators** (`•`, `★`, `(N)`); no hardcoded user-visible text.
- [ ] **Design system**: every public `Fr*` composable gets a `CatalogEntry` in the matching story file; meaning colors via `LocalFrSemanticColors.current`.
- [ ] **DI**: Koin module registered in `settings.gradle.kts` + `shared` aggregator; when injecting `AnalyticsPort` use explicit `viewModel { … analytics = get() }` (not `viewModelOf`) and add `AnalyticsPort::class` to the `*ModuleVerifyTest` `extraTypes`.
- [ ] **Analytics**: new `AnalyticsEvent` leaf (snake_case, past tense, no PII) fired **after** the use case returns `Ok`, never inside a use case.
- [ ] **Firebase-touching module?** JVM 17 target + Firebase BOM in `androidMain`.
- [ ] **Tests**: `commonTest` (kotlin.test, Turbine, coroutines-test); Konsist if domain touched.
- [ ] **Verify**: quote the passing `./gradlew :<module>:testAndroidHostTest` / `:androidApp:assembleDebug` / iOS-link output. Update `firestore.rules`/`storage.rules` when adding a collection, and re-run the device smoke on the **minified** AAB when touching release/R8.

---

# Wave 0 — Compliance & store-blocking (do first)

These gate any store submission. None are optional.

### 0.1 Account deletion 🟡 — spec: [`account-deletion-design.md`](../specs/2026-06-14-account-deletion-design.md)
**Goal:** the client UI already ships against `StubAccountDeletionPort` (returns `Backend.NotImplemented`) — only the backend `deleteAccount` Cloud Function + the adapter that replaces the stub remain. Required by App Store 5.1.1(v) + GDPR Art.17.
**Builds on:** `onMealDeleted` cascade pattern; `mintPlateUrls` signing infra; `SignOutPort`.

- [ ] Implement `AccountDeletionPort.requestDeletion()` in data layer → calls a new callable Cloud Function `deleteAccount` (region `europe-west3`).
- [ ] Cloud Function: cascade-delete account doc, device tokens (`devices/{uid}`), avatar blob, all the user's meals/plates/comments/ratings across every crew (reuse `onMealDeleted` recursive cleanup).
- [ ] **Decision (owned crews):** transfer ownership to oldest member vs. delete the crew if owner leaves. Default: if sole member → delete crew; else → reassign to earliest `joinedAt`.
- [ ] Wire post-deletion: `AnalyticsPort.resetData()` + `setUserId(null)` + `SignOutPort.signOut()`.
- [ ] UI: Settings → "Delete account" → `FrConfirmDialog(destructive)` with typed confirmation → progress → sign out.
- [ ] `firestore.rules` / `storage.rules`: allow the function (admin) path; deny client direct deletes of others' data.
- [ ] Tests: callable function vitest (cascade completeness); use-case test.
- **Verify:** `pnpm --dir functions test` + `:feature:auth:testAndroidHostTest`.

### 0.2 Consent UI 🟢
**Goal:** build the consent screen the analytics base is waiting on — until it exists, analytics is a permanent no-op (the whole product-analytics investment is dark).
**Builds on:** `ConsentPort.grant()/deny()/revoke()`, `ConsentGatedAnalytics`, reserved `Account.dataConsentVersion`.

- [ ] First-run consent screen (after sign-in, before Main): privacy explanation + Grant/Deny, i18n copy en/es.
- [ ] On grant → `ConsentPort.grant()` (writes `ConsentDecision.Granted(version=1)`) → `AnalyticsPort.applyConsent(true)`; stamp `Account.dataConsentVersion`/`dataConsentGrantedAt` via `AccountWritePort`.
- [ ] Settings toggle to revoke/re-grant later; re-prompt when `AnalyticsConfig.consentVersion` increments.
- [ ] Gate routing: `Route` is `Public`/`Protected`; insert a consent gate in `RootNavViewModel` stage machine after Ready (don't drop the effect — see `EventsEffect` `repeatOnLifecycle(RESUMED)`).
- [ ] **Manual (track, not codeable here):** Play Data Safety form + iOS `PrivacyInfo.xcprivacy` (`NSPrivacyTracking=false`) + manifest/plist collection-disabled defaults.
- **Verify:** `:core:data:testAndroidHostTest` (ConsentGatedAnalytics) + `:shared:testAndroidHostTest`.

### 0.3 16-KB page alignment 🟡
**Goal:** fix `lib/arm64-v8a/libmediapipe_tasks_vision_jni.so` not aligned to 16 KB — blocks Play updates targeting Android 15+ (since Nov 1 2025).
**Builds on:** `:feature:meal-ai` MediaPipe dependency.

- [ ] Bump `com.google.mediapipe:tasks-vision` to a 16-KB-aligned release (check latest); if none, repackage the `.so` 16-KB-aligned.
- [ ] AGP/NDK: ensure `android.packaging.jniLibs.useLegacyPackaging = false` and AGP ≥ 8.5.1 / NDK r28 zipalign to 16 KB.
- [ ] Verify with `zipalign -c -P 16 -v 4 androidApp-release.aab`-equivalent / the alignment check tool; test on a 16-KB emulator image.
- **Verify:** `:androidApp:assembleRelease` + alignment check output quoted.

### 0.4 Data export 🟡
**Goal:** GDPR portability — "download all my plates + data."
**Builds on:** `mintPlateUrls` signing; callable-function pattern.

- [ ] Callable Cloud Function `exportMyData` → assemble JSON (account, crews, meals, comments, ratings) + manifest of plate image URLs; write a zip to Storage; return a 15-min signed download URL.
- [ ] Async/job pattern (export may be slow): enqueue → push/email when ready.
- [ ] UI: Settings → "Export my data" → request → "ready" notification → download.
- [ ] Rules: function-only write path; user can read only their own export blob.
- **Verify:** `pnpm --dir functions test`.

---

# Wave 1 — Quick wins (leverage existing data/ports)

Small, high-leverage, mostly client-side. Good momentum after compliance.

### 1.1 Social-proof streak nudges (only if you haven't posted) 🟢
**Goal:** "3 of your 5 crewmates already posted today 👀" — but only to members who haven't posted. Shares infra with 1.4.
**Builds on:** `HasPostedTodayPort`, `HeroStats.platesToday`, `DailyInactivityWorker`, FCM fan-out.

- [ ] Decide channel: **server-side** scheduled function (per crew, at mealtime) that computes who hasn't posted + the posted-count, pushes only to non-posters. (Preferred — avoids each client polling.)
- [ ] Cloud Function `streakNudge` (scheduler, `europe-west3`): for each crew, count today's posters; for each non-poster with a live token, `sendToUid` with `data.postedCount`/`crewSize`.
- [ ] Suppress if recipient already posted (re-check at send time) and if crew streak isn't actually at risk.
- [ ] i18n the templated body (`%1$d of %2$d posted`), reuse `NotificationStringKey`.
- [ ] Analytics: `streak_nudge_sent` is server-side; client logs `notification_opened` on tap (deep-link to Feed).
- **Verify:** `pnpm --dir functions test`.

### 1.2 Blind voting 🟢
**Goal:** hide author identity until the viewer has rated (removes bias).
**Builds on:** `FeedMealUi.canRate`/`viewerRating`/`votes`; 48h rating window already enforced.

- [ ] **Decision:** per-crew toggle (`Crew.blindVoting: Boolean`, owner-set) vs. global default-on. Default: per-crew, off by default.
- [ ] Domain: add `blindVoting` to `Crew` + `CrewDto` (no migration needed — pre-launch).
- [ ] Presentation: in `FeedMealUi` mapping, when `blindVoting && !isAuthor && viewerRating == null` → mask `authorName`/`authorAvatarUrl` to a placeholder; reveal after rating or window close.
- [ ] Author always sees their own meal un-masked; voters' identities in `votes` unaffected.
- [ ] Catalog: add a "blind" scenario to the `FrFeedMealCard` story.
- [ ] Tests: `FeedMealUi` mapping test (masked vs revealed).
- **Verify:** `:feature:feed:testAndroidHostTest`.

### 1.3 Lightweight reactions 🟡
**Goal:** one tasteful react (today's `DailyEmote`) so plates are expressive without a like-counter.
**Builds on:** `DailyEmote` (deterministic daily glyph), comment subcollection pattern, FCM.

- [ ] Domain: `MealReaction` (mealId, crewId, reactorId, reactedAt) + `MealReactionPort` (`observe`, `toggle`) in `:core:domain`. **Decision:** single fixed daily glyph vs. small fixed set (😋🔥🤤). Default: today's `DailyEmote` only — reinforces the daily ritual.
- [ ] Data: Firestore `crews/{crewId}/meals/{mealId}/reactions/{uid}` + repository in `:feature:feed`.
- [ ] Rules: member-only, one doc per uid, toggle own only.
- [ ] Presentation: react affordance + "who reacted" row on `FrFeedMealCard`; reflect in `FeedMealUi`.
- [ ] Analytics: `meal_reacted` leaf.
- [ ] **Decision:** push author on reaction? Default **no** (noise) — reactions are ambient.
- **Verify:** `:feature:feed:testAndroidHostTest` + rules test.

### 1.4 Smart crew mealtime prompt 🟢→🟡
**Goal:** replace the fixed 14:00 `DailyInactivityWorker` time with a per-crew learned slot from historical `publishedHour`/`publishedMinute`. Pairs with 1.1.
**Builds on:** `publishedHour`/`Minute` on every meal; `weeklyDigest` cron pattern.

- [ ] Server-side compute: a scheduled/triggered function writes per-crew preferred prompt windows (e.g. median posting hour per slot) to `crews/{id}.mealtimeProfile`.
- [ ] Schedule the nudge (1.1) at the learned hour instead of a constant; fall back to 14:00 when < N data points.
- [ ] **Decision:** server-scheduled push (preferred, unifies with 1.1) vs. client WorkManager re-scheduling from a `CrewMealtimeProfile` port. Default: server.
- [ ] Analytics: tag nudge sends with the source slot for later A/B (uses `FeatureFlagPort`).
- **Verify:** `pnpm --dir functions test`.

### 1.5 Finish remove-member 🟡
**Goal:** unblock `RemoveMemberUseCase` (returns `CrewError.NotImplemented.RemoveMember` today). The owner-only "Remove" affordance + confirm dialog already exist.
**Builds on:** existing CrewSettings UI, `CrewError` tree, `LeaveCrewUseCase` write pattern.

- [ ] Domain: implement use case → new `CrewRepository.removeMember(crewId, requestedBy, target)` (or a `CrewMemberWritePort`); owner-only, cannot remove self, atomic.
- [ ] Data: Firestore transactional member removal; delete `NotImplemented.RemoveMember` leaf + its StringKey.
- [ ] Rules: `firestore.rules` owner-only member removal; can't remove self.
- [ ] **Decision:** keep or delete the removed member's meals in that crew. Default: keep (their plates stay; identity resolves live via `AccountReadPort`).
- [ ] Cloud Function: notify removed member (push), or silent. Default: silent.
- [ ] Tests: use-case (owner/non-owner/self), rule test.
- **Verify:** `:feature:crew:testAndroidHostTest`.

---

# Wave 2 — Collection, recap & identity surfaces

Badges first — it's the engine the next three plug into.

### 2.1 Badges / achievements 🟡 — **foundational** · spec: [`badges-achievements-design.md`](../specs/2026-06-14-badges-achievements-design.md)
**Goal:** an achievements engine; most badges are deterministic from existing data (first plate, 100 meals, 50 ingredients, 7/30/100-day streaks, cuisine explorer).
**Builds on:** stats engine, ingredient catalog, `celebration` semantic color.

- [ ] Domain: `Achievement` (id, name, description, iconKey, criteria), `AchievementProgress`, and an `AchievementEvaluator` that derives unlocked/locked from meals + stats (client-computed where possible).
- [ ] New module `:feature:achievements` (or fold into `:feature:stats`). **Decision:** client-derived only vs. server-persisted unlock timestamps. Default: client-derived, persist unlock dates for "earned on" display.
- [ ] Presentation: achievements grid (earned/locked), unlock celebration toast (reuse `celebration`/confetti), badge atoms → `Fr*` + catalog entries.
- [ ] Analytics: `achievement_unlocked(id)`.
- [ ] Tests: evaluator unit tests per badge criterion.
- **Verify:** `:feature:achievements:testAndroidHostTest` (or `:feature:stats:…`).

### 2.2 Cuisine passport 🟡
**Goal:** derive a cuisine per dish → a "diversity" collectible. Anti-diet (celebrates variety).
**Builds on:** Food-101 dish labels, `dishIngredientMap` seed pattern, achievements (2.1).

- [ ] Seed `dishCuisineMap` (Food-101 dish → cuisine) into Firestore, mirroring `dishIngredientMap` (seed JSON + `seed-catalog.ts` + vitest referential-integrity check).
- [ ] Domain: `Cuisine` VO + read path (extend `IngredientReadPort` or new `CuisineReadPort`). **Decision:** stamp `cuisine` on `Meal` at publish vs. derive at read from dish. Default: stamp at publish (stable, survives map changes).
- [ ] Stats/passport: distinct-cuisine count over window; passport grid (collected vs locked).
- [ ] Tie to badges (cuisine-explorer milestones).
- [ ] Rules: `dishCuisineMap` public-read, no client write.
- **Verify:** `pnpm --dir functions test` + stats host tests.

### 2.3 Ingredient bingo (Pokédex) 🟢
**Goal:** Pokédex grid over the 226-ingredient catalog — "collected" = distinct confirmed ingredients across the user's (and/or crew's) meals.
**Builds on:** `IngredientReadPort` catalog, user meal `ingredients`, achievements (2.1).

- [ ] Compute collected set: distinct **confirmed** `ingredients` (exclude AI-`detectedIngredients` — same rule stats already uses) across the user's meals; "142/226."
- [ ] Presentation: Pokédex grid (collected vivid / locked dimmed), category sections, progress bar; ingredient detail = first/most-recent meal that "caught" it.
- [ ] **Decision:** personal Pokédex vs. crew-shared Pokédex. Default: both tabs (personal + crew).
- [ ] Tie to badges (collection milestones).
- [ ] Catalog entries for the grid cell `Fr*`.
- **Verify:** stats/collection host tests.

> ⚠️ Note the open bug to fix alongside 2.2/2.3: AI `detectedIngredients` currently persist + merge into display even when unconfirmed (`Meal.mergedIngredientSlugs`). Passport/bingo must count **confirmed only** — fixing the merge bug first keeps these honest.

### 2.4 Weekly digest as a swipeable story 🟡
**Goal:** render the weekly digest as an in-app Instagram-Stories recap instead of just a push line.
**Builds on:** `weeklyDigest` Cloud Function (already computes bestMeal/bestCook/mostProlific/mostVoted/mostCriticized); client `ObserveStatsUseCase` already computes the same window.

- [ ] Reuse client-side stats for the "last week" window (avoids a new read path) OR persist `digests/{crewId}/{weekStart}` from the function and read it.
- [ ] Design system: a swipeable **story player** template (auto-advance, tap-to-skip, progress bars) → `Fr*` templates + catalog entries.
- [ ] Story scenes: cover (DailyEmote motif), best meal, best cook, most prolific, streak, "your week." 
- [ ] Deep link the weekly digest push → opens the story (extend `parseDeepLink` with `…/digest/{weekStart}`; `Route.Protected`).
- [ ] Analytics: `digest_story_opened`, `digest_story_completed`.
- **Verify:** `:feature:stats:testAndroidHostTest` + `:shared:testAndroidHostTest` (parser).

---

# Wave 3 — Growth & sharing

### 3.1 Shareable plate / award / streak cards to Instagram Stories 🟡 — spec: [`shareable-story-cards-design.md`](../specs/2026-06-14-shareable-story-cards-design.md)
**Goal:** render a plate/award/streak as an image and share to IG Stories (and generic share sheet). Built-in viral loop.
**Builds on:** design system, `DailyEmote` motif, stats awards, story player (2.4).

- [ ] Card composables (`Fr*` templates): plate card, award card, streak card — branded, square + 9:16 variants.
- [ ] Off-screen render to bitmap: `expect/actual StoryCardRenderer` (Android: Compose graphics-layer capture; iOS: render the Compose/UIView to `UIImage`).
- [ ] `expect/actual StoryShareLauncher`: Android intent to `com.instagram.android` story (sticker + background asset); iOS `instagram-stories://share` via pasteboard; fallback to system share sheet.
- [ ] Entry points: meal detail "share", award in stats, streak milestone celebration, end of digest story.
- [ ] Analytics: reuse `share` event (`content_type` = `plate|award|streak`).
- [ ] Catalog entries for each card.
- **Verify:** `:feature:feed:testAndroidHostTest` (mapping) + manual on-device share check (quote observation).

### 3.2 Deep-linked invites + QR + rich previews 🟡
**Goal:** turn crew codes into shareable deep links with QR + rich link previews.
**Builds on:** `foodrats://app/crew/{id}` + `https://foodrats.app/crew/...` already parsed to `Route.CrewSettings`; `CrewCode`; partial App Links/Universal Links setup.

- [ ] Invite link builder: crew code → canonical URL (custom scheme + `https` fallback); share-sheet entry from CrewSettings.
- [ ] QR generation: render the invite link as a QR (KMP-friendly lib or `expect/actual`); "show QR" + "scan QR to join."
- [ ] Accept-invite flow from link: map the link to the existing join-by-code path (prefill code → `JoinCrewByCodeUseCase`).
- [ ] Rich previews: Firebase Hosting invite page at `/.well-known`-adjacent route with dynamic OG/Twitter meta (crew name) via a Cloud Function or static template.
- [ ] **Manual (track):** host `assetlinks.json` + `apple-app-site-association`; wire Associated Domains in Xcode (already noted as pending).
- [ ] Analytics: reuse `share` (`content_type=crew_invite`) + `join_group(method=invite_link)`.
- **Verify:** `:shared:testAndroidHostTest` (parser cases) + `:feature:crew:testAndroidHostTest`.

---

# Wave 4 — New domain concept

### 4.1 Meal post types — `Solo` now, multi-author `Together` deferred 🟡 seam / 🔴 Together — spec: [`meal-post-types-design.md`](../specs/2026-06-14-meal-post-types-design.md)
**Goal:** keep single-author meals fully intact, but introduce a `MealKind` discriminator now so a future multi-author **`Together`** meal (one dish, many cooks, **everyone rates** — "all can punctuate") lands as a *pure extension*: no invariant rewrite, no migration. Per your call — **don't build multi-author now; save the possibility as another post type.**

**Why reframed:** multi-author breaks two invariants at once — the deterministic single-author `MealId` and `canRate = !isAuthor`. The seam isolates that risk to a future, deliberate change instead of paying it now.

**Build now — the inert seam (behaviourally a no-op; every meal is `Solo`):**
- [ ] `:core:domain` — add `MealKind` sealed interface (`Solo` the only live leaf); add `kind: MealKind = MealKind.Solo` to `Meal`.
- [ ] `:feature:meal` data — `MealDto.kind` (default `"solo"`), `MealMapper` arm (unknown → `Solo`), `publish()` stamps `Solo`.
- [ ] Tests — default `kind == Solo`; DTO round-trip; old/unknown docs read as `Solo`.
- **Verify:** `:core:domain:testAndroidHostTest` + `:feature:meal:testAndroidHostTest` + `:androidApp:assembleDebug`.

**Deferred — `Together` (fully designed in spec §5; build when product-ready):** the multi-author id scheme, the relaxed scoring guard, stats attribution (counts per co-author, excluded from `bestCook`), creator/owner delete rights, the "Together" compose mode, the `onMealCreated` push fan-out, and the `firestore.rules` branch. The open decisions that gate this build (final name, do co-authors rate, etc.) live in spec §13.

---

# Wave 5 — Platform & engineering excellence

### 5.1 Image pipeline upgrade 🟡
**Goal:** faster, lighter feed — placeholders, thumbnails, compression.
**Builds on:** Coil 3 `AsyncImage`, `ImageUrlPort` signed URLs, `onMealCreated`/Storage triggers.

- [ ] BlurHash/ThumbHash: Cloud Function on plate upload computes a hash → store on the meal doc; render as placeholder in `FrFeedMealCard` (Coil placeholder).
- [ ] Thumbnails: generate resized variants (Storage Resize extension or custom function); feed loads thumb, detail loads full.
- [ ] On-device compression before upload (cap dimension/quality; WebP/AVIF where supported) in the meal upload path.
- [ ] Coil cache tuning (disk/memory) in `installFeedImageLoader()` (both platforms).
- **Verify:** `:feature:feed:testAndroidHostTest` + manual feed-scroll observation; `pnpm --dir functions test`.

### 5.2 Offline-first compose 🟡
**Goal:** draft + queue plates offline; auto-publish on reconnect.
**Builds on:** `MealUploadCoordinator` (already out-of-band), `MealUploadProgressPort`, deterministic `MealId` (idempotent re-publish).

- [ ] Persist drafts locally (DataStore or SQLDelight) + a durable upload queue.
- [ ] Domain: `DraftQueuePort` (enqueue/observe/retry); retry with connectivity constraints (Android WorkManager `NetworkType.CONNECTED`; iOS background task / URLSession background).
- [ ] Surface queued/pending drafts in feed top bar (extend `MealUploadProgressPort` states).
- [ ] Idempotency: rely on deterministic ids so a retried publish can't duplicate; reconcile on success.
- [ ] Tests: queue persistence, retry, idempotent re-publish.
- **Verify:** `:feature:meal:testAndroidHostTest`.

### 5.3 Baseline Profiles + startup traces + crash-free-rate release gates 🟡
**Goal:** faster cold start + automated release-health gating.
**Builds on:** Crashlytics already wired; CI release pipeline + protected `production` environment; classify-latency already logged to analytics.

- [ ] Add a `:baselineprofile` Macrobenchmark module; generate a Baseline Profile; consume it in `:androidApp` release.
- [ ] Startup + key-journey traces: Firebase Performance SDK (Android + iOS) or Macrobenchmark startup test; trace feed-load and classify latency.
- [ ] CI gate: step in `release-production.yml` that reads Crashlytics crash-free rate / Play release-health and **holds/fails** below SLO (the protected env already provides the human-approval control).
- [ ] **Decision:** crash-free SLO threshold (e.g. ≥ 99.5% sessions). 
- **Verify:** `:baselineprofile:` benchmark output quoted + a CI dry-run of the gate.

---

## Consolidated open decisions (resolve before coding the affected item)

1. **`Together` post type (4.1):** the `MealKind` seam is specced + buildable now; the multi-author *decisions* (final name, do co-authors rate, stats attribution, delete rights, id scheme) gate only the **deferred** build — see [`meal-post-types-design.md`](../specs/2026-06-14-meal-post-types-design.md) §13.
2. **Account deletion (0.1):** owned-crew fate (reassign vs delete).
3. **Reactions (1.3):** daily-emote-only vs small fixed set; push-on-react (default no).
4. **Blind voting (1.2):** per-crew toggle vs global default.
5. **Badges (2.1):** client-derived vs server-persisted unlocks.
6. **Cuisine (2.2):** stamp-at-publish vs derive-at-read.
7. **Smart mealtime / social nudges (1.1, 1.4):** server-scheduled (preferred) vs client WorkManager.
8. **Crash-free SLO (5.3):** threshold.

## Dependency notes

- **2.1 Badges before 2.2/2.3** (cuisine + bingo are achievement surfaces).
- **2.4 story player before 3.1** (shareable cards reuse the renderer).
- **1.1 + 1.4 share the nudge infra** — build together.
- **Fix the `detectedIngredients` merge bug before 2.2/2.3** (so collection counts are honest).
- **0.2 Consent UI unblocks all analytics** — without it every new analytics event is dark.
