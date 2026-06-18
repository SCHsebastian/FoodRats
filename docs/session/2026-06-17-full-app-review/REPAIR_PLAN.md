# FoodRats Full-App Review — Repair Plan

Ordered, grouped **by module** so one repair agent per module touches disjoint files.
Per module: **AUTO-APPLY** (autoFixable && safeToAutoFix && NOT in DIRTY_FILES — mechanical, behavior-preserving) and **MANUAL** (human judgment / dirty files / risky / cross-layer).
Each fix must be verified with the module's host-test task before claiming done.

> DIRTY files (do NOT auto-apply): see DIRTY_FILES.txt. Affected here: feature/crew/* (CrewFirestoreDataSource, FirebaseCrewRepository), shared/MainViewController.kt, catalogApp AtomStories.kt.

---

## Cross-module fixes (cannot be done module-locally — sequence first or assign to a lead)
- **cpi-02 — Move EventsEffect to :core:presentation + migrate 9 feature screens.** Touches shared/ + 7 feature modules. One agent owns the move; feature agents must NOT also edit their screen's effect collector until this lands. Per-screen judgment (all 9 are navigation callbacks => all should use EventsEffect).
- **Dead deletion-error leaf (core-domain-02 + auth-04).** Delete AccountDeletionError.Backend.NotImplemented AND ProfileError.Delete.NotImplemented + mapping arm + AuthStringKey entry + strings together (exhaustive-when spans :core:domain and :feature:auth). Single coordinated change; verify both module host tests.
- **Untyped upload error key (core-domain-04 + meal-04).** Optional, LOW: introduce typed MealUploadErrorKey in :core:domain, thread through BackgroundMealUploadCoordinator/DraftRetryRunner/DraftQueue*. Dead at consumer end => defer unless touching the queue. If skipped, still do meal-04 (extract the duplicated mapper into one internal file inside :feature:meal — module-local).
- **Locale-unsafe uppercase (crew-06 + feed-06).** Same fix in two modules; each agent applies locally (uppercase(Locale.ROOT)).

---

## core-domain  (verify: ./gradlew :core:domain:testAndroidHostTest)
AUTO-APPLY:
- core-domain-01 SessionProvider.kt:25 — rename FirebaseUnavailable -> ProviderUnavailable (+ update feature/auth AuthSignOutPort.kt:18,45 — note: that file is in :feature:auth, run that agent or do as a 2-file edit).
- core-domain-03 AnalyticsEvent.kt:114 — count(streakDays) -> text(streakDays.toString()).
- core-domain-07 AccountReadPort.kt:30 — add .distinctUntilChanged().
- core-domain-05 FrLog.kt — @Volatile on enabled + sink. (LOW, unverified)
MANUAL:
- core-domain-02 — dead leaf (cross-module, see above).
- core-domain-04 — typed upload key (cross-module, see above).

## core-data  (verify: ./gradlew :core:data:testAndroidHostTest)
AUTO-APPLY:
- core-data-01 LocationPermissionLauncherHolder.kt:34 — getAndSet(deferred)?.complete(false).
- core-data-02 AppDataStore.ios.kt:21 — ?: error("NSDocumentDirectory unavailable"); guard docDir.path.
- core-data-05 ForegroundActivityHolder.kt:24 — @Volatile on installed. (LOW)
MANUAL:
- core-data-03 ConsentGatedAnalytics.kt:61 — make applyConsent/resetData no-ops; requires migrating feature/auth ProfileViewModel:365 to consent.deny() first (ordering vs track/setUserId). Coordinate with feature-auth agent.
- core-data-04 FirebaseImageUrlResolver — typed FunctionsExceptionCode classification. (LOW)

## core-designsystem  (verify: ./gradlew :core:designsystem:testAndroidHostTest)
AUTO-APPLY:
- ds-01 FrStoryScaffold.kt:120 — Color.White -> LocalFrSemanticColors.current.onScrim.
- ds-03 FrBadge.kt:160 — Color.White -> semantic.onScrim (thread param). (LOW)
MANUAL:
- ds-02 FrStoryScaffold.kt:123 — remove "Close" fallback; changes public param + 3 callers.

## core-presentation  (verify: ./gradlew :core:presentation:testAndroidHostTest)
AUTO-APPLY:
- cpi-01 MviViewModelTest.kt — add setMain/resetMain + expectMostRecentItem.
- cpi-04 MviViewModel.kt — onCleared { _effects.close() }. (LOW)
MANUAL:
- cpi-02 EventsEffect move (cross-module, see top).

## core-i18n  (verify: ./gradlew :shared:testAndroidHostTest or consuming module)
AUTO-APPLY:
- cpi-03 StringKey.kt:5 — interface -> sealed interface.
- cpi-05 NumberFormatting.kt — require(isFinite()) + tests. (LOW)

## feature-auth  (verify: ./gradlew :feature:auth:testAndroidHostTest)
AUTO-APPLY:
- auth-02 DeleteAccountScreen.kt:60 — raw IconButton -> FrIconButton.
- auth-03 ProfileScreen.kt:70 — println -> FrLog.w. (LOW)
MANUAL:
- auth-01 ProfileViewModel.kt:406 — ES delete phrase fix; cross-layer (Screen computes phrase via resolve(template), carry into confirm intent). HIGH-risk; add VM test.
- auth-05 AuthErrorToStringKey.kt:17 — add ErrorSessionExpired (multi-file copy). (LOW)
- auth-04 dead ProfileError leaf (cross-module, see top).
- (Also handles core-data-03 ProfileViewModel:365 migration + core-domain-01 AuthSignOutPort edit if not done by those agents.)

## feature-crew  (verify: ./gradlew :feature:crew:testAndroidHostTest)  -- MANY FILES DIRTY
AUTO-APPLY:
- crew-06 CrewSettingsScreen.kt:527 — uppercase(Locale.ROOT). (not dirty)
MANUAL:
- crew-01 CrewFirestoreDataSource.kt:144,204,212,223 — move IO boundary to repo. DIRTY + proposed fix unsound (shareIn pins Default; fetchOnce read not captured). Needs design.
- crew-03 RemoveMemberUseCase.kt:31 — remove observeCrew().first() pre-flight + dup auth; delegate to repo. (not dirty, but verify against repo behavior)
- crew-05 FirebaseCrewRepository.kt:112 — log/surface dropped DTOs. DIRTY.
- crew-04 CrewFirestoreDataSource internal. DIRTY (defer until file clean).

## feature-meal  (verify: ./gradlew :feature:meal:testAndroidHostTest)
AUTO-APPLY:
- meal-01 DraftQueueLocalStore.kt:80 — add Mutex.withLock around mutate; fix false KDoc.
- meal-04 BackgroundMealUploadCoordinator.kt:235 / DraftRetryRunner.kt:167 — extract duplicated uploadErrorKey() into one internal file. (LOW)
- meal-05 ComposePlateScreen.kt:87 — key on photoBytes?.contentHashCode(). (LOW)
MANUAL:
- meal-03 CaptureMealViewModel.kt — add error state + surface 4 arms + replace println; StringKey/effect decision.
- meal-02 UpdateMealDraftUseCase.kt:13 — add MealError.Publish.NoDraftFound (touches exhaustive whens). (LOW)
- meal-06 FirebaseFault.kt:47 — tighten "storage" match. (LOW)

## feature-feed  (verify: ./gradlew :feature:feed:testAndroidHostTest)
AUTO-APPLY:
- feed-04 MealDeleteErrorToStringKey.kt:11 — 3 dedicated FeedStringKey + en/es + update when; remove TODO.
- feed-03 FeedViewModel.kt:67 — reactionFlows.keys.retainAll(parsed.toSet()) before getOrPut. (LOW)
- feed-06 MealDetailScreen.kt:635 — uppercase(Locale.ROOT). (LOW)
MANUAL (coordinate feed-02 + feed-05 — same MealDetailViewModel viewer/owner resolution):
- feed-05 MealDetailViewModel.kt:101 — snapshot viewer/owner once before collect (the "safe" half).
- feed-02 MealDetailViewModel.kt:141 — derive viewer/owner reactively; flow-topology change + regression test. (Doing feed-02 properly subsumes feed-05.)

## feature-stats  (verify: ./gradlew :feature:stats:testAndroidHostTest)
AUTO-APPLY:
- stats-03 FrPokedexCell.kt:73 — Color.White -> semantic.onCelebration.
- stats-04 — extract single formatOneDecimal; delete 4 copies. (LOW)
MANUAL (both in ObserveStatsUseCase — coordinate as one edit):
- stats-01 ObserveStatsUseCase.kt:66 — daily-epoch signal so query bounds + today rebuild on midnight; add rollover test.
- stats-02 ObserveStatsUseCase.kt:83 — sealed HistoricResult; set historicError + clear loading in VM (also retires stats-05 dead field).

## feature-notifications  (verify: ./gradlew :feature:notifications:testAndroidHostTest)
AUTO-APPLY:
- notif-05 IosLocalReminderScheduler.kt:45 — suspendCancellableCoroutine + Schedule.Failed.
- notif-02 PermissionLauncherHolder.kt:32 — compareAndSet(null,deferred) return false; pass enabled=!isRequesting to Allow button. (LOW)
- notif-03 AndroidLocalReminderScheduler.kt:26 — inject core.domain Clock; update Koin binding. (LOW)
- notif-06 IosFcmTokenProvider.kt:20 — remove println. (LOW)
- notif-07 — delete dead DeliveryWindow + StreakTitle/StreakBody keys + strings. (LOW)
MANUAL:
- notif-04 NotificationChannels.kt:17 — i18n channel name (ensure() signature/suspend change). (LOW)
- notif-01 AndroidNotificationPermissionGateway.kt:21 — Denied/DeniedForever needs a persisted has-requested flag (design); cosmetic impact. (LOW)

## feature-meal-ai  (verify: ./gradlew :feature:meal-ai:testAndroidHostTest)
AUTO-APPLY:
- mealai-01 MediaPipeMealClassifier.android.kt:62 — try/finally { bmp.recycle() }; handle decode-null.
MANUAL:
- mealai-03 ClassifierError.Load.ModelCorrupt — remove dead leaf (touches :core:domain mapper+test). (LOW)

## feature-ingredient  (verify: ./gradlew :feature:ingredient:testAndroidHostTest)
AUTO-APPLY:
- ingredient-01 IngredientRepository.kt:50 — runCatching around suggestForDish; ?: emptyList().
- ingredient-02 SelectIngredientsScreen.kt:132 — exclude detectedSlugs from category filter.
- ingredient-03 IngredientRepository.kt:37 — scope.launch { withContext(io){cache.save} } out of onEach. (LOW)
- ingredient-06 IngredientRepository.kt:34 — .distinctUntilChanged() after merge. (LOW)
MANUAL:
- ingredient-04 — wire or delete dead IngredientError (design). (LOW)

## feature-achievements  (verify: ./gradlew :feature:achievements:testAndroidHostTest)
AUTO-APPLY:
- achievements-01 ObserveAchievementsUseCase.kt:101 — move debounce inside else-branch after combine.
- achievements-03 AchievementsScreen.kt:23 — remove unused collectAsState import. (LOW)
- achievements-04 EpochDayFormat.kt:17,19 — remove stale @Suppress. (LOW)

## app-shell (shared)  (verify: ./gradlew :shared:testAndroidHostTest)
AUTO-APPLY:
- app-shell-04 RootNavViewModel.kt:122 — simplify to if(ready) + KDoc. (LOW)
MANUAL:
- app-shell-03 NavGraph.kt:66,286 — kill double screen_view (suppress Route.Main or guard tab effect); GA4 event-shape decision.
- app-shell-01 InAppPushBanner.kt:65 — SharedStringKey.PushNotificationSeparator + resolve at @Composable; helper takes separator param + fix 3 test assertions. (LOW)
- app-shell-02 MainViewController.kt:104 — move installSink inside Koin guard. DIRTY (defer until clean).

## catalogApp  (verify: ./gradlew :catalogApp:assembleDebug)
AUTO-APPLY:
- catalog-02 CatalogScene.kt:169-183 — delete dead CatalogTag. (LOW)
MANUAL:
- catalog-01 AtomStories.kt:377 — add public FrIcons.entries (32) then use it. DIRTY (defer until clean).

## functions  (verify: pnpm --dir functions test ; pnpm --dir functions build ; firebase deploy --only firestore:rules after review)
AUTO-APPLY (still require deploy + review):
- functions-01 firestore.rules:170 — ratingSum delta guard.
- functions-02 firestore.rules:287 — crewCodes create membership check.
- functions-03 mintPlateUrls.ts:88 — MAX_PATHS=200 + slice.
- functions-04 push.ts:63 — parallelize sendToCrew. (LOW)
MANUAL:
- functions-05 deleteAccount.ts:213 — bounded per-crew meal scan (orderBy+limit+index). (LOW)
