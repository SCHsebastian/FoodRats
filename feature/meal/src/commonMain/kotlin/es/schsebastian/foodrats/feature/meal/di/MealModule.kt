package es.schsebastian.foodrats.feature.meal.di

import es.schsebastian.foodrats.core.domain.meal.HasPostedTodayPort
import es.schsebastian.foodrats.core.domain.meal.MealCommentPort
import es.schsebastian.foodrats.core.domain.meal.MealDeletePort
import es.schsebastian.foodrats.core.domain.meal.MealDraftIngredientsPort
import es.schsebastian.foodrats.core.domain.meal.MealRatingPort
import es.schsebastian.foodrats.core.domain.meal.MealReadPort
import es.schsebastian.foodrats.core.domain.meal.MealUploadCoordinator
import es.schsebastian.foodrats.core.domain.meal.MealUploadProgressPort
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
import es.schsebastian.foodrats.core.domain.account.AccountReadPort
import es.schsebastian.foodrats.feature.meal.data.local.MealDraftLocalStore
import es.schsebastian.foodrats.feature.meal.data.repository.FirebaseMealRepository
import es.schsebastian.foodrats.feature.meal.data.upload.BackgroundMealUploadCoordinator
import es.schsebastian.foodrats.feature.meal.domain.repository.MealRepository
import es.schsebastian.foodrats.feature.meal.domain.usecase.ClassifyDraftPlateUseCase
import es.schsebastian.foodrats.feature.meal.domain.usecase.DiscardMealDraftUseCase
import es.schsebastian.foodrats.feature.meal.domain.usecase.ObserveMealDraftUseCase
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
import org.koin.dsl.module

val mealModule = module {
    singleOf(::MealFirestoreDataSource)
    singleOf(::CommentFirestoreDataSource)
    singleOf(::PlateStorageDataSource)
    singleOf(::MealDraftLocalStore)
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
            dispatchers = get(),
            errorMapper = get(),
            clock = get(),
            authorIdentity = get<MealAuthorIdentity>(),
            zone = get(),
            accountRead = get<AccountReadPort>(),
            imageUrls = get(),
        )
    }
    single<MealReadPort> { get<MealRepository>() }
    single<MealRatingPort> { get<MealRepository>() }
    single<MealDeletePort> { get<MealRepository>() }
    // Exposed so the ingredient picker (:feature:ingredient) reads/edits the draft's
    // ingredient slugs without depending on :feature:meal (spec §7.2).
    single<MealDraftIngredientsPort> { get<MealRepository>() }
    single<MealCommentPort> {
        FirebaseCommentRepository(
            ds = get(),
            auth = get(),
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

    factoryOf(::StartMealDraftUseCase)
    factoryOf(::UpdateMealDraftUseCase)
    factoryOf(::PublishMealUseCase)
    factoryOf(::DiscardMealDraftUseCase)
    factoryOf(::ObserveMealDraftUseCase)
    // Resolves MealClassifierPort (bound by :feature:meal-ai) + IngredientReadPort
    // (bound by :feature:ingredient) at app composition — see shared/ aggregator.
    factoryOf(::ClassifyDraftPlateUseCase)

    // Single instance holds both read (status flow) and write (enqueue) sides
    // so Feed/Stats observe the same coordinator that the composer kicks off.
    // MealUploadScheduler comes from the platform module (WorkManager on
    // Android, in-process no-op on iOS).
    single {
        BackgroundMealUploadCoordinator(
            repository = get(),
            publishMeal = get(),
            streakNotifications = get(),
            prefs = get(),
            scheduler = get(),
            dispatchers = get(),
            analytics = get(),
        )
    }
    single<MealUploadCoordinator> { get<BackgroundMealUploadCoordinator>() }
    single<MealUploadProgressPort> { get<BackgroundMealUploadCoordinator>() }

    viewModelOf(::CaptureMealViewModel)
    viewModel {
        ComposePlateViewModel(
            updateDraft = get(), repository = get(), crewMembership = get(),
            uploadCoordinator = get(), locationProvider = get(), classifyPlate = get(),
            clock = get(), zone = get(), analytics = get(),
        )
    }
    viewModelOf(::CaptureNudgeViewModel)
}
