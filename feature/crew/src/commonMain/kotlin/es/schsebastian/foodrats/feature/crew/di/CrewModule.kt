package es.schsebastian.foodrats.feature.crew.di

import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.crew.CrewBlindVotingPort
import es.schsebastian.foodrats.core.domain.crew.CrewMembershipPort
import es.schsebastian.foodrats.core.domain.crew.CrewOwnerPort
import es.schsebastian.foodrats.core.domain.crew.CrewSummary
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.coroutines.flow.map
import es.schsebastian.foodrats.feature.crew.data.firebase.CrewCodeGenerator
import es.schsebastian.foodrats.feature.crew.data.firebase.CrewDataSource
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
import es.schsebastian.foodrats.feature.crew.domain.usecase.RemoveMemberUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.RenameCrewUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.ResolveCrewByCodeUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.SetBlindVotingUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.SwitchActiveCrewUseCase
import es.schsebastian.foodrats.feature.crew.presentation.invite.AcceptInviteViewModel
import es.schsebastian.foodrats.feature.crew.presentation.picker.CrewPickerViewModel
import es.schsebastian.foodrats.feature.crew.presentation.settings.CrewSettingsViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import kotlinx.coroutines.flow.Flow
import org.koin.dsl.module
import kotlin.random.Random

val crewModule = module {
    single { CrewCodeGenerator(random = Random.Default) }
    singleOf(::CrewErrorMapper)
    single<CrewDataSource> { CrewFirestoreDataSource(get(), get(), get(), get()) }
    single<ActiveCrewProvider> { ActiveCrewLocalStore(get()) }   // DataStore<Preferences> from coreDataModule
    single<CrewOwnerPort> {
        object : CrewOwnerPort {
            private val ds = get<CrewDataSource>()
            override fun observeOwner(crewId: CrewId): Flow<AccountId?> =
                ds.observeCrew(crewId).map { dto ->
                    dto?.ownerId?.let { (AccountId.of(it) as? Result.Ok)?.value }
                }
        }
    }
    single<CrewRepository> {
        // (firestore, dispatchers, errorMapper, clock)
        FirebaseCrewRepository(get(), get(), get(), get())
    }
    // Live "is blind voting on?" for a crew, consumed by :feature:feed to mask author
    // identity without a :feature:crew dep. Mirrors CrewOwnerPort: reads the crew read
    // model and defaults any failure / unknown crew to `false` (safe = un-blind).
    single<CrewBlindVotingPort> {
        object : CrewBlindVotingPort {
            private val repo = get<CrewRepository>()
            override fun observeBlindVoting(crewId: CrewId): Flow<Boolean> =
                repo.observeCrew(crewId).map { r ->
                    when (r) {
                        is Result.Ok  -> r.value.blindVoting
                        is Result.Err -> false
                    }
                }
        }
    }
    // The meal-audience picker (:feature:meal) reads a member's crews through this port,
    // never through CrewRepository directly — keeping :feature:meal free of a :feature:crew dep.
    single<CrewMembershipPort> {
        object : CrewMembershipPort {
            private val repo = get<CrewRepository>()
            override fun observeMyCrews(accountId: AccountId): Flow<List<CrewSummary>> =
                repo.observeMyCrews(accountId).map { r ->
                    when (r) {
                        is Result.Ok  -> r.value.map { CrewSummary(it.id, it.name) }
                        is Result.Err -> emptyList()
                    }
                }
        }
    }

    factoryOf(::CreateCrewUseCase)
    factoryOf(::JoinCrewByCodeUseCase)
    factoryOf(::ResolveCrewByCodeUseCase)
    factoryOf(::LeaveCrewUseCase)
    factoryOf(::ObserveMyCrewsUseCase)
    factoryOf(::ObserveCrewUseCase)
    factoryOf(::SwitchActiveCrewUseCase)
    factoryOf(::RenameCrewUseCase)
    factoryOf(::DeleteCrewUseCase)
    factoryOf(::SetBlindVotingUseCase)
    factoryOf(::RemoveMemberUseCase)

    viewModel {
        CrewPickerViewModel(
            session = get(), observeMyCrews = get(), createCrew = get(), joinCrew = get(),
            switchActive = get(), analytics = get(),
        )
    }
    viewModel { (code: String) ->
        AcceptInviteViewModel(
            code = code,
            session = get(),
            resolveCrew = get(),
            joinCrew = get(),
            switchActive = get(),
            analytics = get(),
        )
    }
    viewModel { (crewId: CrewId) ->
        CrewSettingsViewModel(
            crewId = crewId,
            observeCrew = get(),
            renameCrew = get(),
            deleteCrew = get(),
            setBlindVoting = get(),
            leaveCrew = get(),
            removeMember = get(),
            session = get(),
            accountRead = get(),
            analytics = get(),
        )
    }
}
