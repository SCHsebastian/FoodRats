package es.schsebastian.foodrats.feature.meal.di

import es.schsebastian.foodrats.core.domain.meal.HasPostedTodayPort
import es.schsebastian.foodrats.core.domain.meal.MealCommentPort
import es.schsebastian.foodrats.core.domain.meal.MealDeletePort
import es.schsebastian.foodrats.core.domain.meal.MealRatingPort
import es.schsebastian.foodrats.core.domain.meal.MealReadPort
import es.schsebastian.foodrats.core.domain.meal.MealUploadCoordinator
import es.schsebastian.foodrats.core.domain.meal.MealUploadProgressPort
import es.schsebastian.foodrats.feature.meal.data.firebase.CommentFirestoreDataSource
import es.schsebastian.foodrats.feature.meal.data.firebase.HasPostedTodayAdapter
import es.schsebastian.foodrats.feature.meal.data.firebase.MealErrorMapper
import es.schsebastian.foodrats.feature.meal.data.firebase.MealFirestoreDataSource
import es.schsebastian.foodrats.feature.meal.data.repository.FirebaseCommentRepository
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
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val mealModule = module {
    singleOf(::MealFirestoreDataSource)
    singleOf(::CommentFirestoreDataSource)
    singleOf(::PlateStorageDataSource)
    singleOf(::MealDraftLocalStore)
    singleOf(::MealErrorMapper)
    single<MealRepository> {
        FirebaseMealRepository(
            firestore = get(),
            storage = get(),
            drafts = get(),
            dispatchers = get(),
            errorMapper = get(),
            clock = get(),
            auth = get(),
            zone = get(),
            accountRead = get<AccountReadPort>(),
        )
    }
    single<MealReadPort> { get<MealRepository>() }
    single<MealRatingPort> { get<MealRepository>() }
    single<MealDeletePort> { get<MealRepository>() }
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
        )
    }
    single<MealUploadCoordinator> { get<BackgroundMealUploadCoordinator>() }
    single<MealUploadProgressPort> { get<BackgroundMealUploadCoordinator>() }

    viewModelOf(::CaptureMealViewModel)
    viewModelOf(::ComposePlateViewModel)
}
