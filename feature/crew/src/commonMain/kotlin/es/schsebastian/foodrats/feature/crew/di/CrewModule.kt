package es.schsebastian.foodrats.feature.crew.di

import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.crew.CrewMemberCacheWritePort
import es.schsebastian.foodrats.core.domain.crew.CrewMembersPort
import es.schsebastian.foodrats.core.domain.crew.CrewMemberView
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.feature.crew.data.firebase.CrewCodeGenerator
import es.schsebastian.foodrats.feature.crew.data.firebase.CrewErrorMapper
import es.schsebastian.foodrats.feature.crew.data.firebase.CrewFirestoreDataSource
import es.schsebastian.foodrats.feature.crew.data.firebase.CrewMemberWriter
import es.schsebastian.foodrats.feature.crew.data.firebase.FirestoreCrewMemberCacheWriter
import es.schsebastian.foodrats.feature.crew.data.firebase.FirestoreCrewMemberWriter
import es.schsebastian.foodrats.feature.crew.data.local.ActiveCrewLocalStore
import es.schsebastian.foodrats.feature.crew.data.repository.FirebaseCrewRepository
import es.schsebastian.foodrats.feature.crew.domain.repository.CrewRepository
import es.schsebastian.foodrats.feature.crew.domain.usecase.CreateCrewUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.JoinCrewByCodeUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.LeaveCrewUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.ObserveCrewUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.ObserveMyCrewsUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.DeleteCrewUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.RemoveMemberUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.RenameCrewUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.SwitchActiveCrewUseCase
import es.schsebastian.foodrats.feature.crew.presentation.picker.CrewPickerViewModel
import es.schsebastian.foodrats.feature.crew.presentation.settings.CrewSettingsViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import kotlinx.coroutines.flow.Flow
import org.koin.dsl.module
import kotlin.random.Random

val crewModule = module {
    single { CrewCodeGenerator(random = Random.Default) }
    singleOf(::CrewErrorMapper)
    single<CrewMemberWriter> { FirestoreCrewMemberWriter(firestore = get()) }
    singleOf(::CrewFirestoreDataSource)
    single<CrewMemberCacheWritePort> { FirestoreCrewMemberCacheWriter(dataSource = get()) }
    single<ActiveCrewProvider> { ActiveCrewLocalStore(get()) }   // DataStore<Preferences> from coreDataModule
    single<CrewMembersPort> {
        object : CrewMembersPort {
            private val ds = get<CrewFirestoreDataSource>()
            override fun observeMembers(crewId: CrewId): Flow<List<CrewMemberView>> = ds.observeMembersRaw(crewId)
        }
    }
    single<CrewRepository> {
        // (firestore, dispatchers, errorMapper, clock)
        FirebaseCrewRepository(get(), get(), get(), get())
    }

    factoryOf(::CreateCrewUseCase)
    factoryOf(::JoinCrewByCodeUseCase)
    factoryOf(::LeaveCrewUseCase)
    factoryOf(::ObserveMyCrewsUseCase)
    factoryOf(::ObserveCrewUseCase)
    factoryOf(::SwitchActiveCrewUseCase)
    factoryOf(::RenameCrewUseCase)
    factoryOf(::DeleteCrewUseCase)
    factoryOf(::RemoveMemberUseCase)

    viewModelOf(::CrewPickerViewModel)
    viewModel { (crewId: CrewId) ->
        CrewSettingsViewModel(crewId, get(), get(), get(), get(), get(), get(), get())
    }
}
