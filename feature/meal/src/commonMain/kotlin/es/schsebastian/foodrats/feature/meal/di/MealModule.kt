package es.schsebastian.foodrats.feature.meal.di

import es.schsebastian.foodrats.core.domain.meal.FeedSyncStatusPort
import es.schsebastian.foodrats.core.domain.meal.HasPostedTodayPort
import es.schsebastian.foodrats.core.domain.meal.MealCommentPort
import es.schsebastian.foodrats.core.domain.meal.MealDeletePort
import es.schsebastian.foodrats.core.domain.meal.MealDraftIngredientsPort
import es.schsebastian.foodrats.core.domain.meal.MealRatingPort
import es.schsebastian.foodrats.core.domain.meal.MealReactionPort
import es.schsebastian.foodrats.core.domain.meal.MealReadPort
import es.schsebastian.foodrats.core.domain.meal.MealUploadCoordinator
import es.schsebastian.foodrats.core.domain.meal.MealUploadProgressPort
import es.schsebastian.foodrats.core.domain.meal.OptimisticMealWritePort
import es.schsebastian.foodrats.core.domain.meal.QueuedUploadActionsPort
import es.schsebastian.foodrats.core.domain.outbox.OutboxCommandHandler
import es.schsebastian.foodrats.core.domain.preferences.AppLocale
import es.schsebastian.foodrats.core.domain.preferences.LocalePort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import es.schsebastian.foodrats.feature.meal.data.firebase.CommentFirestore
import es.schsebastian.foodrats.feature.meal.data.firebase.CommentFirestoreDataSource
import es.schsebastian.foodrats.feature.meal.data.firebase.FirebaseAuthorIdentity
import es.schsebastian.foodrats.feature.meal.data.firebase.HasPostedTodayAdapter
import es.schsebastian.foodrats.feature.meal.data.firebase.MealAuthorIdentity
import es.schsebastian.foodrats.feature.meal.data.firebase.MealErrorMapper
import es.schsebastian.foodrats.feature.meal.data.firebase.MealFirestore
import es.schsebastian.foodrats.feature.meal.data.firebase.MealFirestoreDataSource
import es.schsebastian.foodrats.feature.meal.data.repository.FirebaseCommentRepository
import es.schsebastian.foodrats.feature.meal.data.firebase.PlateStorage
import es.schsebastian.foodrats.feature.meal.data.firebase.PlateStorageDataSource
import es.schsebastian.foodrats.feature.meal.data.firebase.ReactionFirestore
import es.schsebastian.foodrats.feature.meal.data.firebase.ReactionFirestoreDataSource
import es.schsebastian.foodrats.feature.meal.data.outbox.MealOutboxCommandHandler
import es.schsebastian.foodrats.core.domain.account.AccountReadPort
import es.schsebastian.foodrats.feature.meal.data.local.MealDraftLocalStore
import es.schsebastian.foodrats.feature.meal.data.local.MealLocalStore
import es.schsebastian.foodrats.feature.meal.data.local.OptimisticMealLocalWriter
import es.schsebastian.foodrats.feature.meal.data.queue.DraftQueueLocalStore
import es.schsebastian.foodrats.feature.meal.data.queue.DraftQueueRepository
import es.schsebastian.foodrats.feature.meal.data.queue.DraftRetryRunner
import es.schsebastian.foodrats.feature.meal.data.repository.FirebaseMealRepository
import es.schsebastian.foodrats.feature.meal.data.repository.FirebaseReactionRepository
import es.schsebastian.foodrats.feature.meal.data.sync.CachePruner
import es.schsebastian.foodrats.feature.meal.data.sync.MealSyncEngine
import es.schsebastian.foodrats.feature.meal.data.upload.BackgroundMealUploadCoordinator
import es.schsebastian.foodrats.feature.meal.domain.queue.DraftQueuePort
import es.schsebastian.foodrats.feature.meal.domain.queue.DraftRetryPolicy
import es.schsebastian.foodrats.feature.meal.domain.repository.MealRepository
import es.schsebastian.foodrats.feature.meal.domain.usecase.ClassifyDraftPlateUseCase
import es.schsebastian.foodrats.feature.meal.domain.usecase.DiscardMealDraftUseCase
import es.schsebastian.foodrats.feature.meal.domain.usecase.PublishMealUseCase
import es.schsebastian.foodrats.feature.meal.domain.usecase.StartMealDraftUseCase
import es.schsebastian.foodrats.feature.meal.domain.usecase.UpdateMealDraftUseCase
import es.schsebastian.foodrats.feature.meal.presentation.capture.CaptureMealViewModel
import es.schsebastian.foodrats.feature.meal.presentation.compose.ComposePlateViewModel
import es.schsebastian.foodrats.feature.meal.presentation.nudge.CaptureNudgeViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Qualifier for the BCP-47 active-language flow the advisory description filter reads (UGC §3). */
private val ModerationLanguageTag = named("mealModerationLanguageTag")

val mealModule = module {
    // Advisory language hint for the on-device description filter. The default filter screens ALL
    // supported languages regardless of this tag (see WordlistTextModeration / Wordlists.ALL). Named
    // distinctly from the feed module's same-purpose flow so the two don't collide in the merged graph.
    single<Flow<String>>(ModerationLanguageTag) {
        get<LocalePort>().locale.map { if (it == AppLocale.System) "en" else it.tag }
    }
    singleOf(::MealFirestoreDataSource)
    singleOf(::CommentFirestoreDataSource)
    singleOf(::ReactionFirestoreDataSource)
    // Explicit single (not singleOf): the PlateCompressor ctor param uses its default — Koin's
    // constructor-reflection `singleOf` would otherwise try to resolve PlateCompressor from the graph.
    single { PlateStorageDataSource(storage = get()) }
    singleOf(::MealDraftLocalStore)
    // Offline-first local read source-of-truth (P3a §3.1). Holds the FoodRatsDatabase queries
    // (FoodRatsDatabase is bound by :core:database's databaseModule); the repository's read path
    // (P3a-T4) and the sync engine (P3a-T3) talk to the feed through this store. Explicit `single`
    // (not singleOf): the ctor params are nullable to admit the override-only commonTest fake, so
    // singleOf's nullable-param `getOrNull` would silently bind null if a dependency were missing.
    single { MealLocalStore(database = get(), dispatchers = get()) }
    // Offline-first sync engine (P3a §3.2): the ONLY Firestore feed-listener consumer. Mirrors each
    // active crew's rolling 30-day window into MealLocalStore (delete-by-absence in-window; older
    // rows persist). `createdAtStart = true` + `start()` so it subscribes to ActiveCrewProvider at
    // app boot — the cached feed must stay fresh without any screen having resolved it first. Runs
    // on the app-lifetime named("appScope") scope (bound by ingredientModule), like the OutboxRunner.
    single(createdAtStart = true) {
        MealSyncEngine(
            firestore = get<MealFirestore>(),
            local = get(),
            activeCrew = get(),
            clock = get(),
            zone = get(),
            appScope = get(named("appScope")),
        ).also { it.start() }
    }
    // Feed freshness + manual refresh seam (P4-T2): the engine IS the FeedSyncStatusPort impl
    // (its lastSyncedAt/refresh signatures match). :feature:feed consumes the port to render
    // "synced X ago" and drive pull-to-refresh without depending on :feature:meal.
    single<FeedSyncStatusPort> { get<MealSyncEngine>() }
    // Offline-first cache pruner (P4-T1): bounds local DB growth. The sync engine's delete-by-absence
    // is window-scoped (30 days), so meals that age out of the window accumulate forever; this drops
    // rows older than 90 days ONCE at app start. `createdAtStart = true` + `start()` so it runs at
    // boot on the app-lifetime named("appScope"), like the sync engine.
    single(createdAtStart = true) {
        CachePruner(
            local = get(),
            clock = get(),
            zone = get(),
            appScope = get(named("appScope")),
        ).also { it.start() }
    }
    singleOf(::MealErrorMapper)
    // The repository depends on data-layer ports, not the concrete Firebase data sources
    // (so its publish/rate/delete orchestration is fakeable in commonTest and the
    // Firebase→own-server swap re-binds these three lines, not the repository).
    single<MealFirestore> { get<MealFirestoreDataSource>() }
    single<PlateStorage> { get<PlateStorageDataSource>() }
    single<MealAuthorIdentity> { FirebaseAuthorIdentity(auth = get()) }
    single<MealRepository> {
        FirebaseMealRepository(
            firestore = get<MealFirestore>(),
            storage = get<PlateStorage>(),
            drafts = get(),
            local = get(),
            dispatchers = get(),
            errorMapper = get(),
            clock = get(),
            authorIdentity = get<MealAuthorIdentity>(),
            zone = get(),
            accountRead = get<AccountReadPort>(),
            imageUrls = get(),
            cuisineRead = get(),
        )
    }
    single<MealReadPort> { get<MealRepository>() }
    single<MealRatingPort> { get<MealRepository>() }
    // Offline-first optimistic RATE seam (P3b §P3b-T5): lets :feature:feed's RateMealUseCase write a
    // pending mealRating row into the local feed store (the feed reads from it) WITHOUT depending on
    // :feature:meal. The meal SYNC path overwrites that pending row with server truth on next snapshot.
    single<OptimisticMealWritePort> { OptimisticMealLocalWriter(local = get(), clock = get()) }
    single<MealDeletePort> { get<MealRepository>() }
    // Exposed so the ingredient picker (:feature:ingredient) reads/edits the draft's
    // ingredient slugs without depending on :feature:meal (spec §7.2).
    single<MealDraftIngredientsPort> { get<MealRepository>() }
    // Comments live behind the data-layer CommentFirestore seam (fakeable in commonTest),
    // mirroring MealFirestore/ReactionFirestore. The auth uid is read through the shared
    // MealAuthorIdentity seam so the vendor FirebaseAuth type never reaches the repository.
    single<CommentFirestore> { get<CommentFirestoreDataSource>() }
    single<MealCommentPort> {
        FirebaseCommentRepository(
            ds = get<CommentFirestore>(),
            authorIdentity = get<MealAuthorIdentity>(),
            clock = get(),
            dispatchers = get(),
        )
    }
    // Reactions live behind the data-layer ReactionFirestore seam (fakeable in commonTest),
    // mirroring MealFirestore. Consumed by :feature:feed via the :core:domain port.
    single<ReactionFirestore> { get<ReactionFirestoreDataSource>() }
    single<MealReactionPort> {
        FirebaseReactionRepository(
            firestore = get<ReactionFirestore>(),
            clock = get(),
            dispatchers = get(),
        )
    }
    single<HasPostedTodayPort> {
        HasPostedTodayAdapter(
            firestore = get(),
            dispatchers = get(),
        )
    }

    // Offline-first write outbox (P2 §1 T6): replays the meal-bounded-context
    // commands (rate / comment / delete-comment / toggle-reaction) against the
    // domain write ports. Contributed to the cross-feature OutboxRunner (in
    // :core:data) via Koin getAll(); the runner never imports :feature:meal.
    // Named qualifier so this and CrewOutboxCommandHandler register at DISTINCT Koin indices —
    // a plain single<OutboxCommandHandler> for both would collide at OutboxCommandHandler::_root_
    // and one would override the other, so getAll() returns only one handler and half the outbox
    // commands silently never replay. getAll<OutboxCommandHandler>() collects across qualifiers.
    single<OutboxCommandHandler>(named("mealOutboxHandler")) {
        MealOutboxCommandHandler(
            rating = get<MealRatingPort>(),
            comments = get<MealCommentPort>(),
            reactions = get<MealReactionPort>(),
        )
    }

    factoryOf(::StartMealDraftUseCase)
    factoryOf(::UpdateMealDraftUseCase)
    factoryOf(::PublishMealUseCase)
    factoryOf(::DiscardMealDraftUseCase)
    // Resolves MealClassifierPort (bound by :feature:meal-ai) + IngredientReadPort
    // (bound by :feature:ingredient) at app composition — see shared/ aggregator.
    factoryOf(::ClassifyDraftPlateUseCase)

    // Offline-first durable publish queue (roadmap §5.2). DraftQueueLocalStore
    // persists the JSON list (incl. base64 plate bytes) so a process death / airplane
    // mode never loses a composed plate; DraftQueueRepository is the IO boundary;
    // DraftRetryRunner drains it idempotently on connectivity-return.
    // ConnectivityPort is the app-wide :core:data binding (ConnectivityManager on
    // Android via connectivityAndroidModule, NWPathMonitor on iOS via connectivityIosModule).
    singleOf(::DraftQueueLocalStore)
    single<DraftQueuePort> {
        DraftQueueRepository(store = get(), clock = get(), dispatchers = get())
    }
    single { DraftRetryPolicy() }
    single {
        DraftRetryRunner(
            queue = get<DraftQueuePort>(),
            publish = get<MealRepository>(),
            connectivity = get(),
            policy = get(),
            // The queue is the single publish executor, so the true publish-outcome analytics
            // (meal_published / meal_publish_failed) are emitted here, not in the coordinator.
            analytics = get(),
        )
    }

    // Single instance holds both read (status flow) and write (enqueue) sides
    // so Feed/Stats observe the same coordinator that the composer kicks off.
    // MealUploadScheduler comes from the platform module (WorkManager on
    // Android, in-process no-op on iOS).
    single {
        BackgroundMealUploadCoordinator(
            repository = get(),
            publishMeal = get(),
            prefs = get(),
            scheduler = get(),
            dispatchers = get(),
            analytics = get(),
            draftQueue = get<DraftQueuePort>(),
            retryRunner = get(),
        )
    }
    single<MealUploadCoordinator> { get<BackgroundMealUploadCoordinator>() }
    single<MealUploadProgressPort> { get<BackgroundMealUploadCoordinator>() }
    single<QueuedUploadActionsPort> { get<BackgroundMealUploadCoordinator>() }

    // Explicit (not viewModelOf): the trailing analytics: AnalyticsPort param has a NoopAnalyticsTracker
    // default, so viewModelOf would short-circuit graph resolution and inject the no-op instead of the
    // real per-platform tracker. See the analytics-base convention in CLAUDE.md.
    viewModel {
        CaptureMealViewModel(
            startDraft = get(), updateDraft = get(),
            sessionProvider = get(), crewMembership = get(),
            analytics = get(),
        )
    }
    viewModel {
        ComposePlateViewModel(
            updateDraft = get(), repository = get(), crewMembership = get(),
            uploadCoordinator = get(), locationProvider = get(), classifyPlate = get(),
            clock = get(), zone = get(),
            // UGC §3 — explicit (default is a Clean no-op; the binding must inject the real port + tag).
            textModeration = get(), languageTag = get<Flow<String>>(ModerationLanguageTag),
            analytics = get(),
        )
    }
    viewModelOf(::CaptureNudgeViewModel)
}
