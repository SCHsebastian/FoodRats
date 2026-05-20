package es.schsebastian.foodrats.feature.meal.di

import es.schsebastian.foodrats.core.domain.meal.MealRatingPort
import es.schsebastian.foodrats.core.domain.meal.MealReadPort
import es.schsebastian.foodrats.feature.meal.data.firebase.MealErrorMapper
import es.schsebastian.foodrats.feature.meal.data.firebase.MealFirestoreDataSource
import es.schsebastian.foodrats.feature.meal.data.firebase.MealRatingsFirestoreDataSource
import es.schsebastian.foodrats.feature.meal.data.firebase.PlateStorageDataSource
import es.schsebastian.foodrats.core.domain.crew.CrewMembersPort
import es.schsebastian.foodrats.feature.meal.data.local.MealDraftLocalStore
import es.schsebastian.foodrats.feature.meal.data.repository.FirebaseMealRepository
import es.schsebastian.foodrats.feature.meal.domain.repository.MealRepository
import es.schsebastian.foodrats.feature.meal.domain.usecase.DiscardMealDraftUseCase
import es.schsebastian.foodrats.feature.meal.domain.usecase.ObserveMealDraftUseCase
import es.schsebastian.foodrats.feature.meal.domain.usecase.PublishMealUseCase
import es.schsebastian.foodrats.feature.meal.domain.usecase.StartMealDraftUseCase
import es.schsebastian.foodrats.feature.meal.domain.usecase.UpdateMealDraftUseCase
import es.schsebastian.foodrats.feature.meal.presentation.capture.CaptureMealViewModel
import es.schsebastian.foodrats.feature.meal.presentation.compose.ComposePlateViewModel
import es.schsebastian.foodrats.feature.meal.presentation.publish.PublishMealViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val mealModule = module {
    singleOf(::MealFirestoreDataSource)
    singleOf(::MealRatingsFirestoreDataSource)
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
            crewMembers = get(),
        )
    }
    single<MealReadPort> { get<MealRepository>() }
    single<MealRatingPort> { get<MealRepository>() }

    factoryOf(::StartMealDraftUseCase)
    factoryOf(::UpdateMealDraftUseCase)
    factoryOf(::PublishMealUseCase)
    factoryOf(::DiscardMealDraftUseCase)
    factoryOf(::ObserveMealDraftUseCase)

    viewModelOf(::CaptureMealViewModel)
    viewModelOf(::ComposePlateViewModel)
    viewModelOf(::PublishMealViewModel)
}
