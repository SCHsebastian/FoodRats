package es.schsebastian.foodrats.feature.crew.di

import es.schsebastian.foodrats.core.domain.image.ImageUrlPort
import es.schsebastian.foodrats.core.domain.result.getOrNull
import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.crew.CrewBlindVotingPort
import es.schsebastian.foodrats.core.domain.crew.CrewMembershipPort
import es.schsebastian.foodrats.core.domain.crew.CrewOwnerPort
import es.schsebastian.foodrats.core.domain.crew.CrewScoreStyle
import es.schsebastian.foodrats.core.domain.crew.CrewWelcomePort
import es.schsebastian.foodrats.core.domain.crew.WeeklyChallengeSnapshot
import es.schsebastian.foodrats.core.data.preferences.WelcomeDismissalRepository
import es.schsebastian.foodrats.core.domain.crew.CrewSummary
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.outbox.OutboxCommandHandler
import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.coroutines.flow.map
import es.schsebastian.foodrats.feature.crew.data.firebase.CrewCodeGenerator
import es.schsebastian.foodrats.feature.crew.data.firebase.CrewDataSource
import es.schsebastian.foodrats.feature.crew.data.firebase.CrewErrorMapper
import es.schsebastian.foodrats.feature.crew.data.firebase.CrewFirestoreDataSource
import es.schsebastian.foodrats.feature.crew.data.local.ActiveCrewLocalStore
import es.schsebastian.foodrats.feature.crew.data.local.CrewLocalStore
import es.schsebastian.foodrats.feature.crew.data.outbox.CrewOutboxCommandHandler
import es.schsebastian.foodrats.feature.crew.data.sync.CrewSyncEngine
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
import es.schsebastian.foodrats.feature.crew.domain.usecase.SetCrewScoreStyleUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.SetCrewTaglineUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.SetCrewWelcomeMessageUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.SetCrewWeeklyChallengeUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.SetCrewBannerUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.RemoveCrewBannerUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.SwitchActiveCrewUseCase
import es.schsebastian.foodrats.feature.crew.data.firebase.CrewBannerStorageDataSource
import es.schsebastian.foodrats.feature.crew.presentation.invite.AcceptInviteViewModel
import es.schsebastian.foodrats.feature.crew.presentation.picker.CrewPickerViewModel
import es.schsebastian.foodrats.feature.crew.presentation.settings.CrewSettingsViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import org.koin.dsl.module
import kotlin.random.Random

val crewModule = module {
    single { CrewCodeGenerator(random = Random.Default) }
    singleOf(::CrewErrorMapper)
    single<CrewDataSource> { CrewFirestoreDataSource(get(), get(), get(), get()) }
    // C9 — banner storage adapter; FirebaseStorage is bound in :core:data's coreDataModule.
    singleOf(::CrewBannerStorageDataSource)
    single<ActiveCrewProvider> { ActiveCrewLocalStore(get()) }   // DataStore<Preferences> from coreDataModule
    // Offline-first local read source-of-truth for the crew list (P3b §P3b-T7). Holds the
    // FoodRatsDatabase queries (FoodRatsDatabase is bound by :core:database's databaseModule); the
    // repository's observeMyCrews reads through this store and the CrewSyncEngine writes into it.
    // Explicit `single` (not singleOf): the ctor params are nullable to admit the override-only
    // commonTest fake, so singleOf's nullable-param `getOrNull` would silently bind null.
    single { CrewLocalStore(database = get(), dispatchers = get()) }
    // Offline-first crew-list sync engine (P3b §P3b-T7): the ONLY consumer of the Firestore
    // crew-list listener. Mirrors the signed-in member's crew list into CrewLocalStore (full
    // replace). `createdAtStart = true` + `start()` so it subscribes to SessionProvider at app
    // boot — the cached picker must stay fresh without any screen having resolved it first. Runs on
    // the app-lifetime named("appScope") scope (bound by ingredientModule), like the MealSyncEngine.
    single(createdAtStart = true) {
        CrewSyncEngine(
            session = get(),
            dataSource = get<CrewDataSource>(),
            local = get(),
            appScope = get(named("appScope")),
        ).also { it.start() }
    }
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
        // (dataSource, dispatchers, errorMapper, clock, local, bannerStorage)
        FirebaseCrewRepository(get(), get(), get(), get(), get(), get())
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

    // Live welcome message + dismissal state for a crew (C6 §6). Consumed by :feature:feed to
    // show a dismissible banner to new joiners without a :feature:crew dependency. Reads the
    // welcome message from CrewRepository (live listener) and dismissal state from
    // WelcomeDismissalRepository (DataStore). Bound here — not in coreDataModule — because it
    // combines two sources: the crew repo (in this module) and the dismissal store (in :core:data).
    single<CrewWelcomePort> {
        val repo = get<CrewRepository>()
        val dismissal = get<WelcomeDismissalRepository>()
        val imageUrls = get<ImageUrlPort>()
        object : CrewWelcomePort {
            override fun observeWelcomeMessage(crewId: CrewId): Flow<String?> =
                repo.observeCrew(crewId).map { r ->
                    when (r) {
                        is Result.Ok  -> r.value.welcomeMessage?.value
                        is Result.Err -> null
                    }
                }

            override fun isWelcomeDismissed(crewId: CrewId): Flow<Boolean> =
                dismissal.observeDismissed().map { dismissed -> crewId.value in dismissed }

            override suspend fun dismissWelcome(crewId: CrewId) = dismissal.dismiss(crewId)

            override fun observeWeeklyChallenge(crewId: CrewId): Flow<WeeklyChallengeSnapshot?> =
                repo.observeCrew(crewId).map { r ->
                    when (r) {
                        is Result.Ok -> {
                            val crew = r.value
                            val text = crew.weeklyChallenge?.value
                            val setAtMs = crew.weeklyChallengeSetAt?.toEpochMilliseconds()
                            if (text != null && setAtMs != null) WeeklyChallengeSnapshot(text, setAtMs)
                            else null
                        }
                        is Result.Err -> null
                    }
                }

            // C8: live score-style per crew. Defaults to Stars on read failure or absent field
            // (pre-C8 crews). Never emits null — callers can unconditionally treat the value.
            override fun observeScoreStyle(crewId: CrewId): Flow<CrewScoreStyle> =
                repo.observeCrew(crewId).map { r ->
                    when (r) {
                        is Result.Ok  -> r.value.scoreStyle
                        is Result.Err -> CrewScoreStyle.Stars
                    }
                }

            // C9: crew hero/banner image URL. Resolves the Storage PATH stored in bannerPath to a
            // short-lived signed URL via ImageUrlPort. Emits null when no banner is set, the crew is
            // unreadable, or URL resolution fails (safe fallback = no banner shown).
            override fun observeBannerImageUrl(crewId: CrewId): Flow<String?> =
                repo.observeCrew(crewId).map { r ->
                    when (r) {
                        is Result.Ok -> {
                            val path = r.value.bannerPath ?: return@map null
                            imageUrls.resolve(crewId, listOf(path)).getOrNull()?.get(path)
                        }
                        is Result.Err -> null
                    }
                }
        }
    }

    // Offline-first write outbox (P2 §1 T6): replays the crew-admin commands
    // (rename / set-blind-voting / remove-member / leave) against CrewRepository.
    // Contributed to the cross-feature OutboxRunner (in :core:data) via Koin
    // getAll(); the runner never imports :feature:crew.
    // Named qualifier so this and MealOutboxCommandHandler register at distinct Koin indices — see
    // the matching note in MealModule. getAll<OutboxCommandHandler>() collects across qualifiers.
    single<OutboxCommandHandler>(named("crewOutboxHandler")) {
        CrewOutboxCommandHandler(crews = get<CrewRepository>())
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
    factoryOf(::SetCrewTaglineUseCase)
    factoryOf(::SetCrewWelcomeMessageUseCase)
    factoryOf(::SetCrewWeeklyChallengeUseCase)
    factoryOf(::SetCrewScoreStyleUseCase)
    factoryOf(::RemoveMemberUseCase)
    // C9 — crew banner use cases
    factoryOf(::SetCrewBannerUseCase)
    factoryOf(::RemoveCrewBannerUseCase)

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
            setCrewTagline = get(),
            setCrewWelcomeMessage = get(),
            setCrewWeeklyChallenge = get(),
            setCrewScoreStyle = get(),
            leaveCrew = get(),
            removeMember = get(),
            setCrewBanner = get(),
            removeCrewBanner = get(),
            session = get(),
            accountRead = get(),
            analytics = get(),
        )
    }
}
