# FoodRats Full-App Code Review — Findings

**Date:** 2026-06-17
**Scope:** all modules — core/{domain,data,designsystem,presentation,i18n}, feature/{auth,crew,meal,feed,stats,notifications,meal-ai,ingredient,achievements}, app-shell (shared/androidApp), catalogApp, functions.
**Method:** per-module review JSON (already adversarially verified) + greppable scans (scan/*.md) + per-module reports (findings/*.md). "Confirmed" = passed adversarial verification. "Refuted" = refuted. "LOW (unverified)" = low cleanups not individually re-verified.

---

## Scorecard

### Confirmed findings by adjusted severity
| Severity | Count |
|---|---|
| HIGH | 0 |
| MEDIUM | 13 |
| LOW (confirmed, adjusted down) | 13 |
| **Total confirmed** | **26** |

Every finding initially tagged HIGH was adversarially **downgraded** on verification (real but bounded). No surviving HIGH issues. The headline list is ordered by real-world impact, not original label.

### Confirmed by category
| Category | Count |
|---|---|
| correctness | 12 |
| architecture | 8 |
| security | 2 |
| performance | 3 |
| cleanup | 1 |

### Module health
| Module | Health | Note |
|---|---|---|
| core-domain | A | Uniform DDD; vendor-name leak + analytics-type mismatch + dead leaf. |
| core-data | A- | Dispatcher boundary clean; one race + iOS force-unwrap + consent passthrough. |
| core-designsystem | A | Iron & Ember routed right; two raw-white nits + i18n fallback. |
| core-presentation/i18n | A- | Clean MVI; missing test setMain, non-sealed StringKey, EventsEffect stranded. |
| feature-auth | A- | ES-locale delete-confirmation UX bug; raw IconButton; println. |
| feature-crew | A- | IO boundary in data source not repo; redundant use-case read. FILES DIRTY. |
| feature-meal | A | Durable-queue RMW race; CaptureMeal silent errors; wrong null-draft error. |
| feature-feed | A | MVI reference; stale comment-delete snapshot; unbounded reaction cache; wrong delete copy. |
| feature-stats | A- | today captured once (stale across midnight); historic error swallowed; raw white. |
| feature-notifications | A | Permission gateway never returns Denied; launcher race; iOS schedule error ignored; hardcoded channel. |
| feature-meal-ai | A | Android Bitmap leak; dead leaf; no tests. |
| feature-ingredient | A- | suggestForDish no guard; detected ingredients render twice; dead error type. |
| feature-achievements | A | Strong pure design; debounce mis-placed delays error paths. |
| app-shell | A- | Mutex-serialized nav; hardcoded em-dash; iOS installSink outside guard; double screen_view. SOME DIRTY. |
| catalogApp | A | Clean APK; icons story shows 10 of 32. FILE DIRTY. |
| functions | A | Best rules in repo; ratingSum unverified; crewCodes create unguarded; mintPlateUrls unbounded. |

Scans: cross-feature coupling = 0; i18n = 0 hardcoded (app-shell em-dash is the lone exception); colors = clean (two scan-level Material-role nits in FrErrorBanner/RecapScenes not promoted).

---

## Headline issues (top 10, by real impact)
1. MEDIUM — feature-crew — IO withContext lives in the data source, not the repository (remove/rename/delete/setBlindVoting). Layering violation; files DIRTY; naive fix unsound (shareIn pins Default). Manual.
2. MEDIUM — feature-meal — DraftQueueLocalStore.mutate read-modify-write not atomic; concurrent enqueue/status-flip can clobber a queued draft. Add Mutex.
3. MEDIUM — feature-auth — Spanish users can't confirm account deletion via the on-screen instruction (expected phrase hardcoded "DELETE", ES says "BORRAR"). Cross-layer.
4. MEDIUM — feature-stats — today captured once at flatMapLatest subscription; streaks/window bounds stale across midnight until manual refresh.
5. MEDIUM — functions — vote update rule doesn't verify ratingSum; a voter can corrupt the feed aggregate. One-line delta guard.
6. MEDIUM — functions — crewCodes create rule doesn't check crew membership; any user mints codes for any crew. One-line membership guard.
7. MEDIUM — feature-ingredient — suggestForDish has no exception guard; a Firestore fault crashes the classification coroutine.
8. MEDIUM — feature-meal — CaptureMealViewModel swallows all errors with println; user stranded on a frozen camera screen.
9. MEDIUM — feature-ingredient — detected ingredients render twice in SelectIngredientsScreen. One-line exclusion filter.
10. MEDIUM — feature-meal-ai — Android Bitmap never recycled; native-heap leak per classify. finally { bmp.recycle() }.

Also notable: app-shell double screen_view (GA4 inflation); core-data iOS DataStore force-unwrap crash path; notifications iOS schedule error ignored (false "scheduled").

---

## Cross-module / de-duplicated root causes
- Raw-string upload error key (1 cause, 2 sites): core-domain-04 (MealUploadStatus.Failed(errorKey:String)) + meal-04 (duplicated uploadErrorKey() mapper). One workstream; dead at consumer end => LOW.
- Locale-unsafe uppercase() (2 sites): crew-06 (CrewSettingsScreen:527) + feed-06 (MealDetailScreen:635). Fix: uppercase(Locale.ROOT).
- println leftovers (3 files): auth-03 (ProfileScreen:70), meal-03 (CaptureMealViewModel x4, also a correctness bug), notif-06 (IosFcmTokenProvider:20).
- CompletableDeferred orphaning (2 sites): core-data-01 (LocationPermissionLauncherHolder) + notif-02 (PermissionLauncherHolder). set vs getAndSet/compareAndSet.
- Stale dead error leaf (1 cause, 2 files): core-domain-02 (AccountDeletionError.Backend.NotImplemented) + auth-04 (ProfileError.Delete.NotImplemented). Delete together.
- Raw Color.White for onScrim/onCelebration (3 sites): ds-01 (FrStoryScaffold), ds-03 (FrBadge), stats-03 (FrPokedexCell — visible dark-mode bug).

---

## CONFIRMED findings (grouped by adjusted severity)

### MEDIUM
- **core-domain-01** core-domain · SessionProvider.kt:25 · architecture — FirebaseUnavailable leaks vendor name into vendor-free domain; sole mapping AuthSignOutPort.kt:45. Fix: rename ProviderUnavailable. autoFixable:yes risk:low
- **core-domain-03** core-domain · AnalyticsEvent.kt:114 · correctness — StreakShared item_id=count vs text on all other share events; wrong BigQuery column type. Fix: text(streakDays.toString()). autoFixable:yes risk:low
- **core-data-01** core-data · LocationPermissionLauncherHolder.kt:34 · correctness — pending.set overwrites in-flight deferred; first caller hangs. Fix: getAndSet(deferred)?.complete(false). autoFixable:yes risk:low
- **core-data-02** core-data · AppDataStore.ios.kt:21 · correctness — URLForDirectory(...)!! SIGABRT before UI on protected volumes. Fix: ?: error(...). autoFixable:yes risk:low
- **core-data-03** core-data · ConsentGatedAnalytics.kt:61 · security — public applyConsent/resetData bypass consent gate. Fix: make Unit; ProfileViewModel:365 -> consent.deny(). Coordinated. autoFixable:no risk:medium
- **cpi-02** core-presentation/app-shell · EventsEffect.kt (+9 screens) · architecture — RESUMED-gate stranded in shared; 9 screens use bare lifecycle-unaware collect. Fix: move to :core:presentation, migrate. Cross-module. autoFixable:no risk:medium
- **auth-01** feature-auth · ProfileViewModel.kt:406 · correctness — ES delete instruction says BORRAR but expected phrase hardcoded DELETE. Fix: compute phrase in Screen via resolve(template), carry into intent. Cross-layer. autoFixable:no risk:high
- **feed-04** feature-feed · MealDeleteErrorToStringKey.kt:11 · cleanup — meal-delete errors reuse comment-error copy. Fix: 3 dedicated FeedStringKey + en/es. autoFixable:yes(safe) risk:low
- **feed-05** feature-feed · MealDetailViewModel.kt:101 · performance — canDeleteMeal re-opens observeOwner listener per feed emission. Fix: snapshot viewer/owner once. autoFixable:yes(safe) risk:medium
- **stats-01** feature-stats · ObserveStatsUseCase.kt:66 · correctness — today captured once; stale across midnight (anchors query bounds + windows). Fix: daily-epoch into outer combine + test. Design. autoFixable:no risk:medium
- **stats-02** feature-stats · ObserveStatsUseCase.kt:83 · correctness — historic Err->null; infinite shimmer offline; historicError never set. Fix: sealed HistoricResult. Design. autoFixable:no risk:low
- **stats-03** feature-stats · FrPokedexCell.kt:73 · architecture — Color.White wrong in dark mode. Fix: semantic.onCelebration. autoFixable:yes risk:low
- **notif-05** feature-notifications · IosLocalReminderScheduler.kt:45 · correctness — addNotificationRequest error ignored; false success. Fix: suspendCancellableCoroutine + Schedule.Failed. autoFixable:yes(safe) risk:low
- **mealai-01** feature-meal-ai · MediaPipeMealClassifier.android.kt:62 · performance — Bitmap never recycled. Fix: finally { bmp.recycle() }. autoFixable:yes risk:low
- **ingredient-01** feature-ingredient · IngredientRepository.kt:50 · correctness — suggestForDish no runCatching; Firestore fault crashes classify coroutine. Fix: runCatching{}?:emptyList(). autoFixable:yes(safe) risk:low
- **ingredient-02** feature-ingredient · SelectIngredientsScreen.kt:132 · correctness — detected ingredients render twice. Fix: exclude detectedSlugs from category filter. autoFixable:yes(safe) risk:low
- **functions-01** functions · firestore.rules:159-171 · security — vote rule doesn't verify ratingSum. Fix: delta-by-own-score guard. autoFixable:yes(safe) risk:medium
- **functions-02** functions · firestore.rules:287 · security — crewCodes create no membership check. Fix: auth.uid in crews/{crewId}.memberIds. autoFixable:yes(safe) risk:medium
- **functions-03** functions · mintPlateUrls.ts:88 · performance — unbounded paths fan-out. Fix: MAX_PATHS=200, slice. autoFixable:yes(safe) risk:low
- **app-shell-03** app-shell · NavGraph.kt:66,286 · performance — double screen_view (main+feed) per Feed landing. Fix: suppress Route.Main or guard tab effect. Design. autoFixable:no risk:low
- **meal-01** feature-meal · DraftQueueLocalStore.kt:80 · correctness — RMW not atomic; enqueue vs scheduleRetry can lose update; false KDoc. Fix: Mutex.withLock. autoFixable:yes(safe) risk:medium
- **meal-03** feature-meal · CaptureMealViewModel.kt:25,30,32,40 · correctness — 4 println, no state/effect; frozen camera. Fix: error state + banner + FrLog. Needs StringKey/effect decision. autoFixable:no risk:low
- **feed-02** feature-feed · MealDetailViewModel.kt:141 · correctness — observeComments snapshots viewer/owner once; canDelete stale on crew switch (server enforces RBAC). Fix: derive reactively + test. Coordinate with feed-05. autoFixable:no risk:medium

### LOW (confirmed, adjusted down)
- core-domain-04 · MealUploadStatus.kt:18 · architecture — untyped errorKey, dead at consumer end (pairs meal-04). autoFixable:no
- ds-01 · FrStoryScaffold.kt:120 · architecture — Color.White -> onScrim. autoFixable:yes
- ds-02 · FrStoryScaffold.kt:123 · architecture — "Close" EN fallback; prod caller already resolved. autoFixable:no
- cpi-01 · MviViewModelTest.kt · correctness — missing Dispatchers.setMain. autoFixable:yes
- cpi-03 · StringKey.kt:5 · architecture — interface -> sealed interface. autoFixable:yes
- auth-02 · DeleteAccountScreen.kt:60 · architecture — raw IconButton -> FrIconButton. autoFixable:yes
- auth-05 · AuthErrorToStringKey.kt:17 · correctness — TokenExpired/NotSignedIn -> ErrorUnknown (near-dead). autoFixable:no
- crew-01 · CrewFirestoreDataSource.kt:144,204,212,223 · architecture — IO in data source not repo. DIRTY; fix unsound. autoFixable:no
- crew-03 · RemoveMemberUseCase.kt:31 · architecture — observeCrew().first() IO-adjacent + dup auth. autoFixable:yes
- crew-05 · FirebaseCrewRepository.kt:112 · correctness — observeMyCrews drops malformed DTOs. DIRTY. autoFixable:no
- meal-02 · UpdateMealDraftUseCase.kt:13 · correctness — null draft returns Publish.NotToday. autoFixable:no
- ingredient-03 · IngredientRepository.kt:37 · architecture — withContext(io) inside onEach in private flow. autoFixable:yes
- ingredient-04 · IngredientError.kt · cleanup — dead error type. autoFixable:no
- achievements-01 · ObserveAchievementsUseCase.kt:101 · performance — debounce outside flatMapLatest delays error paths. autoFixable:yes
- app-shell-01 · InAppPushBanner.kt:65 · architecture — hardcoded " — " (resolve is @Composable; signature change). autoFixable:no
- app-shell-02 · MainViewController.kt:104 · correctness — installSink outside Koin guard. DIRTY. autoFixable:no
- catalog-01 · AtomStories.kt:377 · cleanup — icons story 10 of 32; needs FrIcons.entries. DIRTY. autoFixable:no

---

## Refuted / false-positive appendix
- **ds-04** — FrScoreBadge crash on out-of-range score: only caller is a preview; prod uses StarScoreBadge with coerceIn(1,10). No dynamic path.
- **crew-02** — renameCrew/etc NetworkOnMainThreadException: fetchOnce -> observeCrew runs in shareIn(obsScope=Default), async listener. Premise false.
- **feed-01** — matchedMeal data race: all access on single-threaded Main.immediate; proposed fix doesn't fix the (benign) TOCTOU; worst case returns a typed error.

---

## LOW cleanups (unverified — opportunistic)
core-domain-05 FrLog not @Volatile; core-domain-06 AnalyticsTaxonomyTest manual list; core-domain-07 observeMany no distinctUntilChanged; core-data-04 image error by substring; core-data-05 ForegroundActivityHolder.installed not @Volatile; cpi-04 _effects Channel never closed; cpi-05 toFixed no NaN guard; auth-03 println ProfileScreen:70; auth-04 dead ProfileError.Delete.NotImplemented (pair core-domain-02); crew-04 CrewFirestoreDataSource should be internal (DIRTY); crew-06 / feed-06 uppercase locale-unsafe; meal-04 duplicated uploadErrorKey (pair core-domain-04); meal-05 ByteArray-identity LaunchedEffect key; meal-06 FirebaseFault "storage" substring; stats-04 formatScore x4 dup; stats-05 historicError dead until stats-02; mealai-02 failed-load re-reads tflite; mealai-03 dead ModelCorrupt leaf; mealai-04 no iOS parser tests; mealai-05 unused deps; ingredient-05 SearchIngredientsUseCase remember{} + unused Koin; ingredient-06 merge double-fire; achievements-02 bestCook KDoc lie; achievements-03 unused import; achievements-04 stale @Suppress; notif-01 gateway never Denied (1 redundant tap, needs stored flag); notif-02 launcher orphans deferred (compareAndSet); notif-03 scheduler system clock not injected; notif-04 hardcoded channel name (signature change); notif-06 println IosFcmTokenProvider; notif-07 dead DeliveryWindow + keys; app-shell-04 dead requiresSession branch; catalog-02..05 dead CatalogTag / raw hex samplePlate / dup settings stories / nested-theme minotaur; functions-04 sequential sendToCrew; functions-05 unbounded deleteAccount scan.

Scan-level color nits (not promoted): FrErrorBanner uses errorContainer (consider FrSemanticColors.danger); RecapScenes:123 uses colorScheme.secondary as scene background (consider surface role / named token).
