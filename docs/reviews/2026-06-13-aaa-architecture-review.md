# FoodRats — AAA+ Architecture Review

## 1. Executive summary

**Overall grade: A-** (reference-grade Clean/DDD/MVI KMP architecture with enforcement & verification gaps).

FoodRats is, candidly, one of the stronger Kotlin Multiplatform codebases a senior architect will see: the dependency graph is strictly inward and grep-verified, the domain layer pairs a custom `Result<T,E>` with deeply nested sealed-interface error trees and near-uniform value-object discipline, the MVI base is textbook, vendor isolation is real (Firebase and Compose are absent from `core:domain`), and the structured-concurrency hygiene around Kotlin/Native failure modes is genuinely senior-level. The team clearly thinks carefully — the KDoc routinely documents bugs they already fought (the login-flash `shareIn` contract, `navigateTopLevel`'s back-stack math). The core is at or near the AAA+ bar.

What keeps it from A/A+ is **not redesign work — it is enforcement and verification.** Three systemic themes recur: (1) the load-bearing architectural invariants (feature isolation, dispatcher boundary, designsystem purity, catalog coverage, auth-route classification) are guarded by **code review alone** — exactly one Konsist rule exists, scoped to one module, and several conventions have *already* drifted; (2) the **entire iOS surface is unverified by automation** — `iosSimulatorArm64Test` runs nowhere, CI tests only Android, and "builds on iOS" is repeatedly conflated with "verified on iOS"; (3) there is **no `build-logic`/convention-plugin layer**, leaving ~1100 LOC of duplicated build config and a fragile hand-maintained JVM 11/17 split that underlies findings filed in five separate dimensions.

The single highest-leverage theme is **"convention-enforced-by-review, not by fitness function."** The architecture is clean today purely by discipline — one careless commit (a feature→feature import, a `withContext` in a ViewModel, an uncatalogued component) merges green. Converting the six documented-but-unenforced invariants into Konsist/CI fitness functions is the cheapest, most durable move toward AAA+.

One issue is genuinely urgent and sits outside the original 12 dimensions: the completeness critic surfaced a **P0 Firestore security defect** — the `accounts/{uid}` read rule is logically inverted and makes every user's profile (including email) world-readable to any authenticated user. That, plus a P1 meal-create rule that lets authors self-stuff award-winning aggregates, must be fixed before any further polish.

## 2. Scorecard

### AAA+ pillars

| Pillar | Grade | One-line |
|---|---|---|
| **A1** Boundaries & dependency direction | A- | Strictly-inward, grep-verified graph with idiomatic ports and a real vendor-swap seam; docked for convention-only enforcement, one misplaced port, and dead seams. |
| **A2** Domain modeling (DDD) | A- | Exemplary typed errors + VO discipline; Crew is anemic, key meal/rating invariants live only in Firestore, IngredientSlug breaks the VO convention. |
| **A3** Concurrency correctness | A- | Dispatcher boundary honored with zero exceptions; one real freeze bug, and the central rule has no fitness function. |
| **A4** Platform/KMP discipline | B+ | All expect/actual paired and clean Swift bridges; dead iOS share, English-only push copy, iOS upload durability downgrade. |
| **A5** Testability & verification | B- | AAA+ unit coverage of the pure core, but one arch test, zero repo-impl tests, no port contracts, iOS tested nowhere. |
| **A6** Evolvability & build hygiene | B | Excellent version catalog & Gradle perf; no build-logic layer, hand-maintained JVM split, 16-KB Play blocker. |
| **A7** Consistency | A- | Conventions hold well across all 17 modules; cracks are real but minor (catalog drift, mixed error-typing, ShareController, double-bound TimeZone). |

### Dimension grades

| Dimension | Grade |
|---|---|
| Module boundaries, dependency direction & feature isolation | A- |
| Domain modeling & DDD | A- |
| Concurrency, dispatcher discipline & structured concurrency | A- |
| MVI base & presentation-layer correctness | A- |
| Data layer, repository pattern & Firebase adapter isolation | B+ |
| KMP/expect-actual discipline, iOS parity & native interop | A- |
| Dependency injection (Koin) | B+ |
| Design-system architecture | A- |
| Internationalization | B+ |
| Navigation architecture | A- |
| Testing architecture & fitness functions | B- |
| Build/Gradle architecture & evolvability | B |
| **Security rules** (surfaced by completeness critic) | **C** |

## 3. What is already AAA+

These are genuine strengths worth preserving — they are the reason the overall grade is A- and not lower.

- **Typed `Result<T,E>` + sealed-interface error trees.** `core/domain/.../result/Result.kt` defines a sealed `Result` with a complete combinator set (fold/map/flatMap/mapError/getOrElse). No stdlib `kotlin.Result` leaks. Every error is a nested `sealed interface` with `data object` leaves (`MealError`, `CrewError`, `AuthError`, `ProfileError`); `ProfileError.kt:60-90` even ships exhaustive KDoc'd cross-error port mappers — the reference shape for translation.

- **Strictly-inward, grep-verified dependency graph.** `core/domain` imports nothing outside itself; `core/data` imports zero feature packages; all 8 features have zero cross-feature edges. `feature/meal` (the rich exemplar) talks to the classifier/catalog only through `MealClassifierPort`/`IngredientReadPort`/`MealDraftIngredientsPort` declared in `core:domain`.

- **Dispatcher discipline is near-perfect.** Verified by grep across all 17 modules: every `withContext(dispatchers.io)` lives in a data-layer method; **zero** in any of the 33 use cases or any ViewModel. The `DispatcherProvider` iOS actual correctly aliases `Dispatchers.Default` for IO with an accurate comment about coroutines 1.10.x Native internals.

- **MVI base is textbook.** `core/presentation/.../mvi/MviViewModel.kt` is a ~50-LOC core: one private `MutableStateFlow` exposed read-only, a BUFFERED `Channel` for one-shot effects, `update{}` as the only mutation path, `currentState` for synchronous reads, structured `FrLog` tracing. `FeedViewModel`/`StatsViewModel` are clean exemplars of feeding use cases from state via `state.map{}.filterNotNull().distinctUntilChanged()`.

- **`AccountReadPort` is AAA+ port design.** `core/domain/account/AccountReadPort.kt:26` ships a default `observeMany()` with an explicit documented scaling note ("fine for crew sizes ≤ 8; override with a batched implementation") *and* a contract test — a genuinely exemplary port-contract pattern.

- **Native-safe structured concurrency.** Every long-lived scope carries `SupervisorJob() + injected dispatcher + CoroutineExceptionHandler` for a documented reason (a Firestore PERMISSION_DENIED after sign-out would otherwise SIGABRT the iOS process), with `WhileSubscribed(5s) + replay=1` per-key memoized hot flows behind a `Mutex`. The `shareIn(replay=1)` "first emission authoritative" auth-resolving contract (`FirebaseAuthRepository.kt:58-76`) is precisely reasoned.

- **Vendor isolation holds at the import level.** A grep for `import dev.gitlive` hits only `data/firebase/` and platform FCM token providers — the "swap Firebase without touching domain" claim holds structurally. DTOs are tolerant (`@Serializable`, nullable defaults, silently ignore legacy `tags`) with total `toDomain()` mappers returning typed errors.

- **i18n at the design-system boundary is exemplary.** `interface StringKey { val resourceId: StringResource }` + two `resolve()` overloads is the entire surface. Atoms take primitive `String`; `resolve(StringKey)` happens at the call site; en/es key parity is byte-for-byte perfect across all 9 string-owning modules; the error-tree→StringKey exhaustiveness is **compiler-enforced** (exhaustive `when`, no `else`) and double-locked by 12 test files. Even glyph separators (`★`, `•`, `(N)`) are resource-keyed.

- **Navigation core is reference-quality.** Typed `@Serializable` routes with R8 keep rules; a pure, total, scheme-agnostic `parseDeepLink` (no `android.net.Uri`) locked by 9 cross-target tests; a `Mutex`-serialized intercept-then-resume `RootNavViewModel` that emits effects (never navigates) and is testable without a device.

- **Build/Gradle fundamentals.** Zero version literals outside `libs.versions.toml`; zero `api()` leaks; config-cache + build-cache + parallel all on; JVM auto-provisioned via foojay toolchain; the catalog documents its pins with engineering rationale.

## 4. Prioritized roadmap

| # | Finding | Severity | How | Why (AAA+ payoff) | Effort |
|---|---|---|---|---|---|
| 1 | Broken `accounts/{uid}` read rule — all profiles world-readable | **P0** | Drop the inverted `exists(crews/$(uid))==false` branch; gate on real crew membership; add emulator tests | Live PII confidentiality breach of the app's core crew-only promise | S |
| 2 | Meal CREATE rule doesn't bound `ratingSum`/`voterCount` | **P1** | Assert `ratingSum==0 && voterCount==0 && ratings.size()==0` on create; compute digest awards from the ratings map | Author can self-stuff server-attested weekly awards | S |
| 3 | No build-logic / convention plugins | **P1** | `build-logic` with `foodrats.kmp.library`/`.feature`/`.firebase`; JVM target as plugin param; `[bundles]` first step | Collapses ~1100 LOC duplication + the JVM-split trap across 5 dimensions | L |
| 4 | Architecture invariants convention-only | **P1** | One `scopeFromProject()` Konsist module: feature isolation, dispatcher boundary, designsystem purity, catalog coverage; wire into CI | Turns 6 load-bearing rules into regression-locked guarantees | M |
| 5 | AI-detected ingredients auto-confirmed | **P1** | `SetDetected` sets only `detectedIngredients`; seed picker from it; stop persisting on Meal; add publish invariant test | Meals/stats record food the user never attested | M |
| 6 | `MealDetailViewModel.observeComments` nested collect freezes comments | **P1** | `flatMapLatest` over the comment Result flow; WhileSubscribed per-author | Reactive screen silently freezes after first batch (reproduced) | M |
| 7 | iOS tested nowhere; CI skips 5 modules | **P2** | Add `shared` host-test set + nav tests; run non-Firebase `iosSimulatorArm64Test` on the Mac runner; add missing modules to CI | Native lowering + half the value proposition is unasserted | L |
| 8 | Error mappers bucket by exception-message substrings | **P1** | Rethrow typed internal exceptions in datasources; map by type; one `classify()` helper; fix publish/rate mis-mappings | Biggest threat to the Firebase→server swap; wrong recovery UX today | M |
| 9 | iOS share is a dead no-op; iOS `startKoin` unguarded | **P2** | `shareIosModule(share:)` + UIActivityViewController bridge; guard `startKoin` with `GlobalContext.getOrNull()` | Core onboarding loop dead on iPhone; latent recreation crash | S |
| 10 | Push text hardcoded English; dead localized resources; no in-app bus consumer | **P2** | Resolve `NotificationStringKey` via injected suspending resolver; data-only push; wire `NotificationBus.stream` consumer | On-device localization layer stubbed English-only | M |
| 11 | Crew anemic; meal/rating invariants only in Firestore | **P2** | `Crew.of()`/`addMember()` referencing `CrewSize.MAX`; `raterId` on `MealRatingPort` + `RateMealUseCase` | The one true aggregate invariant + self-vote lost on vendor swap | M |
| 12 | Zero repository-impl tests; no port contract tests | **P1** | Extract pure orchestration behind thin interfaces; fake-test publish/rate/delete; one canonical fake per port | The exact seam the server swap depends on is unverified | L |
| 13 | 16-KB `.so` alignment Play blocker | **P1** | Bump `tasks-vision` to a 16-KB-aligned release; verify with APK Analyzer; sync the pod; hoist to catalog | Release AAB rejected by Play (targetSdk 36) | M |
| 14 | No production observability; dead analytics dep; no feature flags | **P2** | Wire `FrLog` to a release sink or remove `firebase-analytics-ktx`; add analytics + RemoteConfig kill-switch ports | Can't diagnose prod or kill a misbehaving classifier | M |
| 15 | Storage objects readable by any signed-in user | **P2** | Gate plate reads on membership via `firestore.get(.../crews/$(crewId))`; emulator rules tests | Private photos fetchable by non-members who know IDs | M |
| 16 | IngredientSlug/CrewName VO + validation gaps | **P2** | `IngredientSlug.of(): Result`; `CrewName` VO shared by Create/Rename use cases | Removes stdlib-exception leak + duplicated validation | M |
| 17 | `NavigateDeepLink` clobbers back stack | **P2** | Mirror the `alreadyInAuthedContent` guard; `launchSingleTop`; back-stack test | FCM tap mid-flow discards user context | S |
| 18 | No Koin verify; route markers unenforced; dead seams | **P2** | Per-module `verify()`; exhaustive `Route.requiresSession()`; delete `CrewMembersPort`/orphan use case; de-dup `TimeZone` | DI validated only at launch; dead contract surface | M |
| 19 | weeklyDigest unbounded crew scan; accessibility unaudited | **P2** | Paginate/fan-out the digest; a11y content-description + touch-target pass via `resolve(StringKey)` | Server job won't scale; ~half of imagery unlabeled | M |
| 20 | P3 consistency & write-path hygiene cluster | **P3** | Move ShareController port to domain; inject dispatchers into scopes; best-effort Storage cleanup on publish failure; Account/Ingredient mapper tests; restrict Maps key; real plurals; `state.value`→`currentState` | Clears the A7 backlog; Maps-key billing mitigation | M |

## 5. Findings by dimension

### 5.1 Module boundaries, dependency direction & feature isolation — A-

- **Fitness functions exist only for `core:domain` (P1).** `KonsistRulesTest.kt` is the only arch test and scopes solely to `core:domain` imports. Feature-isolation, dispatcher-boundary, and designsystem-purity are convention-only (currently held, grep-verified). *Fix:* one `scopeFromProject()` Konsist module scoping rules by **package** (not class-name suffix — that false-positives on legit data/adapter files like `FirestoreAccountWriter`, `HasPostedTodayAdapter`, the MediaPipe classifiers); wire it into `ci.yml`'s host-tests job, which currently also omits `:feature:ingredient` and `:feature:meal-ai`. *(Merged with concurrency P2 and i18n/testing/design-system findings — see Cross-cutting themes.)*
- **No convention plugins (P2, re-graded from P1).** All 8 feature build scripts duplicate the identical core-dependency block, `androidLibrary{}` config, and JVM-split literal. *(See roadmap #3; build-script DRY debt, not a runtime violation.)*
- **ShareController port lives in `core:data`, not `core:domain` (P3, re-graded from P2).** It's a pure capability interface (`fun shareText(String)`) consumed by `feature:crew`'s presentation, but unlike every sibling port (`LocationProvider`, `LocalePort`, `ThemeModePort`) it's in the adapter module. *Fix:* move the interface to `core/domain/share/`, keep `ShareControllerAndroid/Ios` in `core:data` (mirrors `LocationProvider`).
- **`feature:meal-ai` over-declares 4 unused core deps + orphan `ClassifyPlateUseCase` (P3).** It imports only `core.domain`; trim to its real deps (NOT "domain only" — it legitimately needs compose-resources + koin for the classifier). Delete the never-bound `ClassifyPlateUseCase` (the composer correctly uses meal-owned `ClassifyDraftPlateUseCase`).
- **androidApp↔feature dependency asymmetry (P3).** `androidApp` directly depends on most features but not `:feature:ingredient` (aggregated via `shared`). The real fix: centralize platform feature-DI edges into a `shared` aggregator; auth/crew/feed/stats are themselves redundant direct edges with no androidMain module.
- **Dead `CrewMembersPort` on the contract surface (P3).** Bound via an inline anonymous object over the dead `observeMembersRaw`; zero consumers (identity resolves via `AccountReadPort`). Delete it, the binding, the datasource method, and the denormalized `crews/{id}.members.{uid}.displayName/avatarUrl` fields.

### 5.2 Domain modeling & DDD — A-

- **AI detections auto-confirmed in the draft reducer (P1, re-graded from P0).** `UpdateMealDraftUseCase.kt:20-24` `SetDetected` stamps classifier output into BOTH `detectedIngredients` AND user-confirmed `ingredients` in one `copy()`; the picker is optional, so detections silently become attested data. The CLAUDE.md note points at the deleted `Meal.mergedIngredientSlugs()` — that display union is already gone; the live cause is here. *(Roadmap #5; this and the MVI/data findings on the same bug are merged.)*
- **Crew is an anemic aggregate (P2, re-graded from P1).** The 3..8 membership cap — the invariant this context exists to protect — lives in Firestore rules + a hardcoded `>= 8` literal in `CrewFirestoreDataSource.kt:116` that bypasses `CrewSize.MAX/canAdd` (dead, only called from tests). *Fix:* `Crew.of()`/`addMember()` referencing `CrewSize.MAX`; keep the authoritative cap inside the `runTransaction` (the join MUST stay atomic — a load-then-add-in-use-case path reintroduces TOCTOU). *(Roadmap #11.)*
- **IngredientSlug breaks the VO factory convention (P2, re-graded from P1).** Public ctor with throwing `require()`, swallowed by `runCatching{}.getOrNull()` at 5 data-layer sites — leaking a stdlib-exception idiom and silently dropping malformed slugs. `MealCommentId` is also unvalidated vs `MealId`. `Streak`/`StatsWindow` `require()` is defensible (internal construction only). *(Roadmap #16.)*
- **Rating invariants live in the Firestore datasource, not the domain (P2, re-graded from P1).** Self-vote/already-rated are enforced only in the transaction + rules (genuine defense-in-depth, no runtime hole); `MealRatingPort.rate()` doesn't even take `raterId`. *Fix:* add `raterId` + a thin `RateMealUseCase`, keeping rules as the concurrency backstop. *(Publish-uniqueness is ALREADY a domain guard at `PublishMealUseCase.kt:24` — the finding's claim it's only in Firestore is refuted for publish.)*
- **Primitive obsession in Account / read models (P3, re-graded from P2).** `handle`/`email`/`displayName`/`avatarUrl`/`photoUrl` are raw strings. But `handle` is vestigial (auto-generated, zero readers), `email` is already validated at the input ViewModel + provider, and read-model display strings are legitimately raw projections. *Fix:* document the raw-string projection decision; an Email VO only when email becomes a first-class displayed concept.
- **Use-case altitude inconsistency (P2).** `CreateCrewUseCase` + `RenameCrewUseCase` duplicate name validation with two separate `MAX=40` constants — a `CrewName` VO (mirroring the existing `CrewCode.of()`) eliminates both. `LeaveCrewUseCase`/`RemoveMemberUseCase` are thin/stub (acceptable). *(Roadmap #16.)*
- **`detectedIngredients` persisted to Meal despite no reader (P3).** Drop it from the published `Meal`/`MealDto`/persist path; keep it on `MealDraft` as the picker seed. *(Folds into roadmap #5.)*

### 5.3 Concurrency, dispatcher discipline & structured concurrency — A-

- **Nested terminal collect in `MealDetailViewModel.observeComments` (P1).** *(Roadmap #6 — empirically reproduced.)* Also the `authorFlows` map uses `SharingStarted.Eagerly` and never evicts — bounded by unique authors in a 3-8 crew but contradicts the WhileSubscribed discipline used everywhere else.
- **No fitness function for the dispatcher boundary (P2).** *(Merged into roadmap #4.)* The `...domain.usecase`/`...presentation` "zero withContext" half is trivial and high-value; the "exactly one per repo method" half must exclude Flow-returning methods and the legit nested `IngredientRepository` case.
- **FCM Service scope missing SupervisorJob, never cancelled (P3, re-graded from P2).** `FoodRatsFirebaseMessagingService.kt:20` is the only scope lacking `SupervisorJob()`; `bus.publish` is a near-instant buffered `emit`, so impact is low. *Fix:* add `SupervisorJob()`, cancel in `onDestroy()`, or use `goAsync()`.
- **App-lifetime scopes hand-rolled, never cancelled (P3).** Only `:feature:ingredient` injects a named `appScope`; auth/crew/meal construct private scopes. *Fix:* promote a named `appScope` into `coreDataModule` carrying the SIGABRT-guard handler (the ingredient exemplar is actually the *only* one missing that handler — don't propagate it verbatim). *(Roadmap #20.)*
- **`FoodRatsApplication.onCreate` uses raw `Dispatchers.IO` (P3).** Cosmetic — DataStore self-confines IO; the bootstrap layer isn't governed by the repo-only rule. Wrap in `runCatching` for the migration write.
- **`IngredientRepository` flow-internal IO side-effect (P3).** Legitimate gray zone; document the exemption or move the cache write into the datasource (NOT `flowOn`, which would shift the whole upstream).

### 5.4 MVI base & presentation-layer correctness — A-

- **`MealDetailViewModel` nested collect (P1).** *(Same as 5.3; roadmap #6.)*
- **Feature screens bypass the lifecycle-aware `EventsEffect` (P2, re-graded from P1).** All 7 use bare `LaunchedEffect(Unit){ vm.effects.collect }`. The headline hazard is largely mitigated — the two riskiest screens (SignIn, NotificationPermission) route real navigation through RootNav's `EventsEffect` via deliberate no-op callbacks, and the BUFFERED channel prevents drops. *Fix (defense-in-depth + DRY):* promote `EventsEffect` to `core:presentation` and adopt it uniformly.
- **`CaptureMealViewModel` swallows errors into `println()` (P2).** Four error branches print and early-return with no error state, no StringKey, no FrLog — the camera flow silently dead-ends. *Fix:* route through `update{}` + the existing `MealErrorToStringKey`; add the missing test.
- **Error-state typing inconsistency (P3, re-graded from P2).** Most contracts hold the typed domain error and map at the Screen; `ProfileState`/`NotificationPermissionState` hold pre-mapped `StringKey?` and map in the VM. Zero runtime impact. *Fix:* document the boundary in `core/presentation/CLAUDE.md` ("prefer typed error in State, mapped at Screen; pre-mapped StringKey acceptable only when no typed error exists" — Profile's 7 independently-failing ops genuinely warrant the exception); reconcile the contradicting global `mobile-mvi-clean.md` rule. Skip the lint rule.
- **`CrewSettingsViewModel.kt:77` reads `state.value` not `currentState` (P3).** The sole deviation; functionally identical. One-line fix. *(Roadmap #20.)*
- **ProfileViewModel optimistic-rollback untested (P3, re-graded from P2).** The headline "MealDetail untested" claim is refuted — `MealDetailCommentIdentityTest.kt` covers it via the recommended Turbine pattern. Only `ProfileViewModel.doSetNotifications` rollback is a genuine gap.
- **`ComposePlateViewModel` AI-ingredient promotion (refuted, P3).** The display-side union (`mergedIngredientSlugs`) was deleted in 693a51c; feed/stats read `ingredients` alone. The remaining concern is the reducer auto-stamp, tracked under domain-ddd P1 (roadmap #5).

### 5.5 Data layer, repository pattern & Firebase adapter isolation — B+

- **Error mappers bucket by exception-message substrings (P1).** *(Roadmap #8.)* Crew already proves the typed-exception pattern; the publish→`Read.Unauthorized` and rate→`RatingWindowClosed` mis-mappings reach the user with wrong recovery copy today.
- **`deleteCrew` orphans the meals subcollection (P2, re-graded from P1).** Batch-deletes only the crew + code docs; `meals/**`, comments/ratings, and Storage blobs are orphaned (no `onCrewDeleted` function). Invisible to users but a cost + privacy concern. *Fix:* add `onCrewDeleted` using `recursiveDelete` + Storage cleanup, mirroring `onMealDeleted`.
- **Dispatcher boundary violated in `renameCrew`/`deleteCrew` + the upload coordinator (P3, re-graded from P2).** The `fetchOnce` read leg has no repo-level `withContext` (write leg's boundary is in the datasource); `BackgroundMealUploadCoordinator` builds its scope on raw `Dispatchers.Default`. No runtime hazard (the flow runs on `obsScope`'s dispatcher). *Fix:* MOVE the boundary to wrap the full repo body; inject `DispatcherProvider`. *(Roadmap #20.)*
- **Publish persists unconfirmed `detectedIngredients` (P3, re-graded from P2).** Data-minimization concern; no display impact (read side fixed). *(Folds into roadmap #5.)*
- **Non-atomic publish (Storage then Firestore) (P3, re-graded from P2).** Failed Firestore write orphans the blob; deterministic path prevents duplicates. *Fix:* best-effort `delete()` on write failure (PlateStorageDataSource has none) — NOT the "write Firestore first with a pending flag" approach, which inverts the orphan into a user-visible broken-image. *(Roadmap #20.)*
- **`AccountReadPort.observeMany` N-listener fan-out (P3).** Bounded + intentional (live rename/avatar propagation); WhileSubscribed means idle uids hold zero live listeners. LRU near-zero value at ≤8-member scale.
- **Account & Ingredient mappers untested (P2).** Unlike Meal/Crew/Comment. Both have silent-failure fallback logic (blank→default, language fallback, category-string parse). *Fix:* add the two `*MapperTest`s using `CrewMapperTest` as template. *(Roadmap #20.)*
- **No offline-persistence stance; ratingSum trusted from client (P3).** `FirebaseInitializer` is bare; the only real hole is no rule asserting `ratingSum` equals the derived sum — but the leaderboard recomputes from the ratings map, so only the weekly-digest "Top Cook" is gameable. *Fix:* one rules clause pinning `ratingSum` to the read-modify-write delta. *(Intersects roadmap #2.)*

### 5.6 KMP/expect-actual discipline, iOS parity & native interop — A-

- **iOS share is a silent `println` no-op (P2, re-graded from P1).** Mitigated by a working cross-platform copy affordance, but the Share button is dead on iPhone. *(Roadmap #9.)*
- **iOS upload scheduler is a durability downgrade (P2).** `InProcessMealUploadScheduler.schedule()/cancel() = Unit`; recovery only on next app launch via `init` auto-resume. The meal is not lost (persisted pending flag), but the UX says "watch the progress bar." *Fix:* a BGTaskScheduler-backed (or URLSession background-upload) iosMain actual via the existing port seam; at minimum honest "will finish when you reopen" copy.
- **No iOS CI / no `iosX64` / Firebase test-link failure (P3).** *(Merged into roadmap #7.)* Non-Firebase modules CAN run on Apple Silicon today; drop the `iosX64` suggestion (no Intel runners).
- **Catalog drift: feature-level Fr* uncatalogued (P3, re-graded from P2).** The "uncatalogable by construction" claim is overstated — several (FrStatTile, FrMealCard, FrFeedDayHeader) import zero domain types and are simply misplaced. *Fix:* relocate the genuinely-pure shells into `core:designsystem`; document i18n-coupled cards as out-of-scope.
- **SKIE/NativeCoroutines absence (refuted, P3).** Swift consumes nothing from the framework (UI is Compose-in-Kotlin; interop is inbound closures), so SKIE would have nothing to wrap. Coroutine resumption is re-dispatched through the Main-confined `viewModelScope` interceptor. *Residual:* document each `*Native` bridge's once-only/threading contract; guard with `if (cont.isActive)`.
- **material-icons-extended exclude (refuted, P3).** imagepickerkmp transitively pins 1.7.3, which DOES ship iOS klibs; `:feature:meal:compileKotlinIosSimulatorArm64` builds green. The CMP-1.11.0 hazard is a different coordinate.
- **`FirebaseInitializer` no-op-iOS `expect object` (P3).** Only one caller (`FoodRatsApplication`, Android); nothing common consumes it. *Fix:* delete the expect/actual triple, inline an androidMain `initFirebase()` (cleaner than interface+DI given it runs before `startKoin`). *(Roadmap #18.)*

### 5.7 Dependency injection (Koin) — B+

- **Zero Koin module-verification despite koin-test in 11 modules (P2, re-graded from P1).** No `verify()`/`checkModules()`; the graph is validated only at app launch. A naive `verify(appModules)` would false-positive on per-platform-only bindings (CrashReporter, MealClassifierPort). *Fix:* per-module `verify()` in each feature's commonTest against fakes for cross-module ports (uses the already-declared dep), OR delete the 11 dead declarations. *(Roadmap #18.)*
- **iOS `startKoin` no idempotency guard (P2, re-graded from P1).** Latent `KoinApplicationAlreadyStartedException` on view-controller recreation; not steady-state. *(Roadmap #9.)*
- **App-lifetime scopes hand-rolled (P3).** *(Same as 5.3; roadmap #20.)*
- **`single<TimeZone>` double-bound (P3, re-graded from P2).** Both `coreDataModule` and `feedModule` bind it; Koin's default `allowOverride=true` silently last-writer-wins, but both evaluate to `currentSystemDefault()` (zero current impact). *Fix:* keep `coreDataModule`'s, drop `feedModule`'s, have notifications inject it. *(Roadmap #20.)*
- **Wide positional `get()` chains (P3, re-graded from P2).** MealDetailViewModel uses 12 (not 13) positional `get()`; all params are distinct types so no current mis-bind. The real signal is the 14-dep VM (SRP smell). *Fix:* named args in the lambda; the cited `checkModules()` safety net doesn't exist yet.
- **ingredientModule loads only via `shared` transitively (P3).** Real asymmetry but auth/crew/feed/stats are *also* redundant direct androidApp edges. *Fix:* treat `shared` as the single feature-aggregation point.
- **CrewMembersPort/CrewOwnerPort bound as inline anonymous objects (P3).** CrewOwnerPort's binding holds DTO→domain mapping logic in the module. Extract `CrewOwnerAdapter` (named, testable); DELETE the dead CrewMembersPort rather than refactor it. *(Roadmap #18.)*

### 5.8 Design-system architecture — A-

- **Catalog-coverage contract convention-only AND violated (P3, re-graded from P2).** `FrSettingsPicker` is genuinely uncatalogued (a real public molecule used in ProfileScreen). `FrSettingsDivider` is in fact rendered in context — the finding's evidence there is wrong, and "make it internal" is incorrect. *Fix:* add one FrSettingsPicker story + the catalog Konsist rule (roadmap #4).
- **No arch test for designsystem-no-domain or catalog coverage (P2).** *(Merged into roadmap #4.)* commonMain is currently clean of domain imports — a regression guard, not an active violation. The fitness function must exclude `@Preview`/`*Preview` helpers.
- **`FrLogo` hardcodes raw `Color(0x..)` defaults (P3, re-graded from P2).** The only raw colors outside the palette; they equal the LIGHT-theme values, so the splash logo renders light-on-charcoal in dark mode *today*. *Fix:* `@Composable`-resolved defaults (`surface`/`secondary`).
- **Mixed token vs literal spacing (P3, re-graded from P2).** Only ~4 production lines (2-4dp inner gaps); 3 of 5 cited locations are `@Preview` bodies. *Fix:* swap the 4 lines to `Spacing.xxs/xs`; do NOT add a lint rule (the project forbids introducing one without asking).
- **Preview tooling in commonMain main artifact (P3).** Real but minor — it's `ui-tooling-preview` (tiny annotation artifact), the heavy `ui-tooling` is already debug-gated, previews are `private`, and R8 strips them from the release binary. CMP commonMain `@Preview` genuinely can't be debug-only. *Fix:* document the accepted tradeoff.
- **No convention plugin / JVM split (P3, re-graded from P2).** The JVM_11-consumed-by-JVM_17 "inconsistency" is actually the only valid direction. The real item is the build duplication (roadmap #3).

### 5.9 Internationalization — B+

- **Push text hardcoded English; localized resources dead (P2, re-graded from P1).** *(Roadmap #10.)* The user-visible OS lock-screen text comes from the *server* `notification{}` block (also English); `PushPayloadMapper`'s output feeds `NotificationBus.stream`, which has no Composable consumer — so today the dead es resources display nowhere. Both the resolver wiring AND a bus consumer are needed.
- **Hand-faked plurals via `if (n == 1)` (P3, re-graded from P2).** Correct for en+es (both CLDR one/other); only latent for pl/ru/ar. *Fix:* a `resolvePlural()` helper delegating to `pluralStringResource` (CMP 1.11.0 supports it). *(Roadmap #20.)*
- **StringKey/exhaustiveness contract convention-only; MealDeleteError untested (P3, re-graded from P2).** It's the only error tree with a mapper but no exhaustiveness test, and ships a `TODO(i18n)` reusing comment-error copy. The `when` is still compiler-exhaustive. *Fix:* add `MealDeleteErrorToStringKeyTest` + dedicated `feed_delete_error_*` strings. *(Folds into roadmap #4's fitness function.)*
- **Mapper return-type inconsistency (P3).** Some return `<Feature>StringKey`, three return bare `StringKey` — and the finding's stated cause (multi-enum mapping) is refuted; all three map into a single enum. *Fix:* standardize on the concrete type.
- **Distinct error leaves collapse to identical copy (P3).** `ProfileError.Session.SignedOut` → "Network unavailable. Try again." (wrong + non-actionable; a signed-out user needs re-auth). *Fix:* a distinct `ProfileSessionSignedOut` StringKey; comment the genuinely-shared collapses.
- **Ingredient names localized via a separate runtime track (P3).** Dual-track is legitimate (catalog data can't be static resources), but the in-app En/Es override drives ingredient names while static text + categories stay in device language — a guaranteed mixed-language screen, because no code applies `AppLocale` to Compose Resources at all (the in-app picker is effectively dead for chrome). *Fix:* one active-language SSOT driving both Compose Resources locale and the catalog Flow.

### 5.10 Navigation architecture — A-

- **`NavigateDeepLink` clobbers the back stack (P2, re-graded from P1).** *(Roadmap #17.)* Recoverable reset on a secondary path; the sibling `NavigateTopLevel` already has the guard.
- **Public/Protected classification not compiler-exhaustive (P2, re-graded from P1).** Sold as "exhaustive at compile time" but consumed by one runtime `is Route.Public` check — and since `parseDeepLink` only yields Protected routes, that branch is dead code (gating reduces to `if (ready)`). No auth bypass (runtime stage machine gates correctly). *Fix:* an exhaustive `Route.requiresSession()` routed through all gating, or demote the markers to docs. *(Roadmap #18.)*
- **Login-flash "no placeholder" contract on 1 of 3 combine inputs (P3, re-graded from P1).** The predicted flash is NOT reproducible — the DataStore-backed `activeCrew`/`prompted` flows don't emit synthetic placeholders (no `stateIn(initialValue=…)`), and `combine` gates on all three. *Fix (regression-prevention only):* mirror the no-placeholder KDoc onto the other two ports so a future re-impl can't reintroduce the trap.
- **No UI/back-stack tests for NavGraph/navigateTopLevel/EventsEffect (P2, re-graded from P1).** `shared` already has `withHostTest` enabled at the AGP level — only the `androidHostTest` dir + deps + tests are missing. The riskiest pure logic IS unit-tested; the untested surface is the imperative effect-applying glue. *(Roadmap #7.)*
- **Feature screens use raw effect collection (P2).** *(Same as 5.4.)*
- **Polymorphic nav routes without SerializersModule (refuted, P3).** Compose Navigation derives route patterns + arg NavTypes, not polymorphic Json dispatch — the SerializersModule gotcha doesn't apply on the nav hot path; confirmed running on a physical iPhone. *Residual:* add an iOS-target `navigate()`/`toRoute()` round-trip test (the actual arg-encoding path is untested on Native).
- **DeepLinkBus CONFLATED can coalesce a burst (P3).** "Last link wins" is acceptable UX; the consumer never suspends, so the window is tiny. *Fix:* document the policy, or switch to a small buffered channel.

### 5.11 Testing architecture & fitness functions — B-

- **Architecture fitness functions near-absent (P2, re-graded from P1).** *(Roadmap #4.)* Feature-isolation is partly build-graph-enforced (a feature→feature import won't compile without also adding the dep); the genuinely unguarded rules are dispatcher-boundary, designsystem-no-domain, catalog coverage, hardcoded-strings.
- **Zero repository-impl tests (P1).** *(Roadmap #12.)* The vendor-translation + IO seam — the exact swap dependency — is unverified, and it contains the live dual-ingredient-write bug.
- **iOS tested nowhere; ~58% of commonTest can't link on iOS (P2, re-graded from P1).** *(Roadmap #7.)* Also surfaces that CI's host-tests job is unverified on BOTH host and iOS for 5 modules.
- **No port contract tests; ad-hoc fakes for 7 of 21 ports (P2, re-graded from P1).** A common-test contract the *real* Firebase adapters could run doesn't exist (needs a live Firestore/emulator). *Achievable fix:* co-locate fakes under one `domain/test/` package per feature; one canonical behavioural fake per shared port. Adapter-vs-contract verification belongs in a Firebase-emulator integration test (larger task). *(Roadmap #12.)*
- **mergedIngredientSlugs bug regression test (refuted, P3).** The bug + method were removed in 693a51c, which ADDED `shows_only_user_confirmed_ingredients` and `ai_detected_ingredients_are_excluded_from_stats`. `ComposePlateViewModelTest:139` pins the intentional seed contract, not the bug. Only a focused `ClassifyDraftPlateUseCaseTest` for confidence/unmapped edges has merit.
- **CI runs 8 of ~13 testable modules (P2).** *(Roadmap #7.)* The recommended command is partly wrong: `:core:data`/`:core:presentation` lack a `withHostTest` block, so their `testAndroidHostTest` task doesn't exist — add the block first. ingredient/meal-ai/shared are immediately addable and secret-free.
- **5 ViewModels + ~17 use cases untested (P3, re-graded from P2).** The flagship "MealDetail untested" is a false positive (`MealDetailCommentIdentityTest` covers it). The destructive DeleteMeal/DeleteComment use cases carry no client RBAC (server-side per their KDoc). Genuine gaps: `ProfileViewModel` + `DeleteMyAccountUseCase` (real phrase/session logic) + `HandleIncomingPushUseCase`.
- **Catalog-coverage Konsist (P3).** *(Same as 5.8.)* The rule must live in `catalogApp` (which has no host-test set yet), not `core:designsystem` (would invert the dep edge).

### 5.12 Build/Gradle architecture & evolvability — B

- **No convention plugins — ~1100 LOC duplicated (P2, re-graded from P1).** *(Roadmap #3.)*
- **Firebase BOM `33.5.1` hardcoded in 8 files (P3, re-graded from P2).** Plus 4 version-less native coords outside the catalog. *Fix:* a catalog `platform` alias + the `foodrats.firebase` plugin. *(Folds into roadmap #3.)*
- **JVM 11/17 split hand-maintained (P2).** A break-on-new-import trap (`feature:meal-ai` forced to 17 purely to dodge a transitive break, with zero Firebase imports). *Fix:* standardize on JVM_17 via the convention plugin (nothing requires 11). *(Roadmap #3.)*
- **16-KB `.so` Play blocker (P1).** *(Roadmap #13.)*
- **R8 keep rules don't pin nav Route serial names (refuted, P3).** Typed-route matching is internally consistent within one obfuscated APK (producer + consumer read serialName from the same serializer); deep links use the pure path-segment parser, not navDeepLink URI matching. *Residual:* harmless `@SerialName` future-proofing only if navDeepLink/cross-version saved-state restore is later added.
- **MediaPipe version coupled by comment only (P3).** Drift has ALREADY happened (Android pinned 0.10.14, iOS `Podfile.lock` resolved 0.10.35). *Fix:* exact-pin both sides, hoist to catalog, CI grep-diff. Impact bounded by the shared `.tflite` + advisory classification. *(Folds into roadmap #13/#20.)*
- **No `iosX64` target (P3).** Apple-Silicon-only is fine; make it an explicit choice in the convention plugin. Drop the iosX64 suggestion. *(Roadmap #7.)*

### 5.13 Security rules (surfaced by the completeness critic) — C

This surface was not in the original 12 dimensions but is the actual authZ boundary and contains the most severe issues in the corpus.

- **`accounts/{uid}` read rule logically broken — all profiles world-readable (P0).** *(Roadmap #1.)*
- **Meal CREATE rule doesn't bound award-determining aggregates (P1).** *(Roadmap #2.)*
- **Storage objects readable by any signed-in user (P2).** *(Roadmap #15.)*
- **No rate-limiting on comments/meals/ratings; comment fan-out is an unthrottled push amplifier (P3).** Low severity in a closed trusted crew; name it as an accepted risk or debounce the push fan-out in `onCommentCreated`.
- **Maps API key embedded in BuildConfig with no documented restriction (P3).** *Fix:* restrict to app package + SHA-1, Static Maps API only; note in `cicd-runbook`. `GOOGLE_SERVER_CLIENT_ID` is a public OAuth id by design. *(Roadmap #20.)*

The root cause across all of these is the **absence of a `@firebase/rules-unit-testing` emulator test harness** — there are zero rules tests, which is precisely why the inverted `accounts` predicate and the unbounded create rule shipped. Standing up that harness (roadmap #15) is the durable fix.

## 6. Cross-cutting themes

1. **Convention-enforced-by-review, not by fitness function.** The single most recurring theme (module-boundaries P1, concurrency P2, MVI P2, design-system P2, i18n P3, testing P2, navigation P2). One Konsist rule exists, scoped to `core:domain`. Feature isolation, dispatcher boundary, designsystem purity, catalog coverage, no-hardcoded-strings, and Public/Protected routing are convention-only — and several have already drifted (FrSettingsPicker, MealDeleteError, the dead route-marker check). Clean today by discipline; one commit from silent regression. *(Roadmap #4.)*

2. **Denormalized fields trusted from the client, enforced inconsistently.** Recurs in data-firebase (ratingSum update), domain-ddd (detectedIngredients), and surfaces sharply in security (ratingSum/voterCount unbounded at *create*). The system repeatedly denormalizes aggregates and relies on the client or a partial rule to keep them honest — the rule guards the rating *update* path but not *create*, and the weekly digest trusts exactly these spoofable fields. *(Roadmap #2, #5.)*

3. **No `build-logic`/convention-plugin layer is the root of findings in five dimensions.** The same duplicated build config, hand-maintained JVM split, and copy-pasted scaffolds underlie build-gradle, module-boundaries, kmp-platform, design-system, and di findings. One `build-logic` introduction collapses a large fraction of the A6/A7 debt. *(Roadmap #3.)*

4. **iOS is structurally unverified.** Recurs across kmp-platform, testing, di, and navigation. "Builds and links on iOS" is repeatedly conflated with "verified on iOS"; the entire expect/actual + Native-lowering surface has zero automated assertions despite being half the value proposition. *(Roadmap #7.)*

5. **Design-intent documented in prose the code doesn't enforce.** The `accounts` rule comment ("any crew you're in can read your profile") vs the broken predicate; "Public/Protected exhaustive at compile time" vs one runtime check; "every public Fr* has a catalog entry" vs uncatalogued components; the no-placeholder session contract on 1 of 3 inputs; "detected ≠ confirmed" intent vs the auto-stamping reducer. Comments are aspirational where enforcement is absent.

6. **Stale / self-contradicting tech-debt records.** CLAUDE.md still describes the deleted `Meal.mergedIngredientSlugs()` as the ingredient-bug source, mis-pointing two dimensions at the wrong line; the global `mobile-mvi-clean.md` rule ("VM maps errors to StringKey") contradicts the dominant in-code pattern (map at Screen). The documentation drifts from the merged code and becomes its own review hazard.

## 7. Coverage gaps the review flags for follow-up

These surfaces were not assessed as first-class architectural dimensions and warrant a dedicated pass:

- **Security rules as the authZ boundary** — partially closed here via the critic's findings, but a full audit + emulator test suite is the durable fix.
- **Production observability & feature flags** — `FrLog` is debug-only println; `firebase-analytics-ktx` ships dead (zero `logEvent`); no RemoteConfig/kill-switch. *(Roadmap #14.)*
- **Offline-first / conflict resolution** — no explicit Firestore persistence stance, no conflict strategy for contended rating aggregates, no documented optimistic-UI/rollback contract.
- **Server-side fan-out scalability** — the weeklyDigest unbounded crew scan and the 365-day Historic snapshot listener; no cold-start/frame/memory budgets defined. *(Roadmap #19.)*
- **Accessibility** — entirely unreviewed; ~half of imagery unlabeled; touch-target/dynamic-type/contrast unaudited. *(Roadmap #19.)*
- **Data-migration / schema-evolution** — no schema versioning or backfill plan for the denormalized fields ahead of the stated Firebase→owned-server swap; deprecated `ratings` subcollection still has live rules.
- **Runtime secret scoping** — Maps key + OAuth client id baked into BuildConfig with no documented Maps key restriction. *(Roadmap #20.)*
- **Error-recovery UX & degraded-mode behavior** — no documented user-facing retry/backoff; foreground `NotificationBus` has no in-app consumer; the CaptureMeal `println` dead-end shows the recovery story is inconsistent.

## 8. Appendix: Considered & Rejected / Debatable

These proposals were raised by dimension reviewers but **refuted or materially down-graded by verification**, surfaced here transparently so nothing is hidden.

- **[REFUTED] `ComposePlateViewModel` promotes AI ingredients into display (mvi P1).** `Meal.mergedIngredientSlugs()` was deleted in commit 693a51c (merged to `main`); feed detail and stats read `meal.ingredients` alone — detected-but-deselected ingredients cannot display. The remaining real issue (the draft-reducer auto-stamp) is tracked as domain-ddd P1 (roadmap #5). The recommendation to "stop copying into draftIngredients" would break the intended pre-checked-picker UX.

- **[REFUTED] No SKIE/NativeCoroutines is a P1 iOS-interop hazard (kmp-platform P1).** Swift consumes no suspend/Flow APIs from the framework (the iOS UI is Compose-in-Kotlin; interop is inbound closures only), so SKIE would have nothing to wrap. Resumption is re-dispatched through the Main-confined coroutine interceptor; the MediaPipe path is additionally `withContext(io)`-wrapped with a synchronous bridge. Residual is documentation/`if (cont.isActive)` hardening (P3).

- **[REFUTED] material-icons-extended exclude is a latent iOS build break (kmp-platform P3).** imagepickerkmp transitively pins 1.7.3, which ships iOS klibs; `:feature:meal:compileKotlinIosSimulatorArm64` builds green (verified). The "no iOS publication" hazard is a different coordinate (CMP 1.11.0). Proactively adding the exclude could regress the build.

- **[REFUTED] Polymorphic nav routes need a SerializersModule on iOS (navigation P2).** Compose Navigation derives route patterns + per-argument NavTypes, not polymorphic Json dispatch — the gotcha doesn't apply on the nav hot path; confirmed running on a physical iPhone. Residual: add an iOS-target `navigate()`/`toRoute()` round-trip test (P3).

- **[REFUTED] R8 obfuscation can desync typed-route matching (build-gradle P2).** Matching is internally consistent within one obfuscated APK (producer + consumer compute serialName from the same serializer), and deep links use the pure path-segment parser, not navDeepLink URI matching. Harmless `@SerialName` future-proofing only (P3).

- **[REFUTED] Login-flash contract gap can reappear via crew/prompted (navigation P1 → P3).** The predicted flash is not reproducible — the DataStore flows emit no synthetic placeholder, and `combine` gates on all three. The surviving value is regression-prevention KDoc on the two ports.

- **[REFUTED] mergedIngredientSlugs bug lacks a regression test (testing P2 → P3).** The bug + method were removed and the fix commit ADDED two regression tests. `ComposePlateViewModelTest:139` pins the intentional seed contract, not the bug.

- **[REFUTED] MealDetailViewModel / "two most-complex VMs" untested (mvi P2 → P3, testing P2 → P3).** `MealDetailCommentIdentityTest.kt` covers MealDetail via the exact recommended Turbine pattern (7 cases). The naming heuristic (`*ViewModelTest`) produced a false positive on the flagship example. Only `ProfileViewModel`'s optimistic rollback is a genuine gap.

- **[DEBATABLE] Crew cap "lives exclusively in Firestore rules" (domain-ddd P1 → P2).** Refuted in part: there IS a client-side `>= 8` check in `CrewFirestoreDataSource.kt:116` (inside the join transaction) plus the rule — defense-in-depth, not a runtime hole. The true defect is the hardcoded literal bypassing `CrewSize.MAX`, and the recommended "load-then-addMember-in-use-case" path would reintroduce a TOCTOU race. Keep the cap in the transaction.

- **[DEBATABLE] Make `core:designsystem` JVM_17 to match consumers (kmp-platform/design-system).** Refuted: JVM_11 bytecode consumed by a JVM_17 module is the correct/safe direction; the split is principled (Firebase inline functions). The real item is the build duplication, not the target value.

- **[DEBATABLE] Catalog "uncatalogable by construction" framing (kmp-platform P2 → P3).** Overstated — several feature Fr* (FrStatTile, FrMealCard) import zero domain types and are simply misplaced, not coupled; the rest couple only via per-feature i18n StringKeys, not Firebase/Koin.

- **[DOWN-GRADED, multiple]** A large number of P1/P2 findings were re-graded down by verification (build-duplication P1→P2, several DDD/data P1→P2/P3, di P1→P2, navigation P1→P2). The corrected severities are reflected in §4 and §5. The pattern is honest reviewer enthusiasm for maintainability debt; the verifier correctly reserved P1 for issues with material near-term blast radius (live data exposure, release blockers, the freeze bug, the swap-dependent untested seam).