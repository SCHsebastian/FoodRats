package es.schsebastian.foodrats.feature.crew.di

import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.feature.crew.data.firebase.CrewCodeGenerator
import es.schsebastian.foodrats.feature.crew.data.firebase.CrewErrorMapper
import es.schsebastian.foodrats.feature.crew.data.firebase.CrewFirestoreDataSource
import es.schsebastian.foodrats.feature.crew.data.local.ActiveCrewLocalStore
import es.schsebastian.foodrats.feature.crew.data.repository.FirebaseCrewRepository
import es.schsebastian.foodrats.feature.crew.domain.repository.CrewRepository
import es.schsebastian.foodrats.feature.crew.domain.usecase.CreateCrewUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.JoinCrewByCodeUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.LeaveCrewUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.ObserveCrewUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.ObserveMyCrewsUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.DeleteCrewUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.RenameCrewUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.RenameMemberUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.SwitchActiveCrewUseCase
import es.schsebastian.foodrats.feature.crew.presentation.picker.CrewPickerViewModel
import es.schsebastian.foodrats.feature.crew.presentation.settings.CrewSettingsViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import kotlin.random.Random

val crewModule = module {
    single { CrewCodeGenerator(random = Random.Default) }
    singleOf(::CrewErrorMapper)
    singleOf(::CrewFirestoreDataSource)
    single<ActiveCrewProvider> { ActiveCrewLocalStore(get()) }   // DataStore<Preferences> from coreDataModule
    single<CrewRepository> { FirebaseCrewRepository(get(), get(), get(), get()) }

    factoryOf(::CreateCrewUseCase)
    factoryOf(::JoinCrewByCodeUseCase)
    factoryOf(::LeaveCrewUseCase)
    factoryOf(::ObserveMyCrewsUseCase)
    factoryOf(::ObserveCrewUseCase)
    factoryOf(::SwitchActiveCrewUseCase)
    factoryOf(::RenameCrewUseCase)
    factoryOf(::RenameMemberUseCase)
    factoryOf(::DeleteCrewUseCase)

    viewModelOf(::CrewPickerViewModel)
    viewModel { (crewId: es.schsebastian.foodrats.core.domain.model.CrewId) ->
        CrewSettingsViewModel(crewId, get(), get(), get(), get(), get(), get(), get())
    }
}
