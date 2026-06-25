package es.schsebastian.foodrats.feature.moderation.di

import es.schsebastian.foodrats.core.domain.account.BlockedAccountsPort
import es.schsebastian.foodrats.core.domain.moderation.ReportPort
import es.schsebastian.foodrats.core.domain.moderation.TextModerationPort
import es.schsebastian.foodrats.core.domain.moderation.WordlistTextModeration
import es.schsebastian.foodrats.feature.moderation.data.firebase.BlockDataSource
import es.schsebastian.foodrats.feature.moderation.data.firebase.BlockErrorMapper
import es.schsebastian.foodrats.feature.moderation.data.firebase.BlockFirestoreDataSource
import es.schsebastian.foodrats.feature.moderation.data.firebase.ReportDataSource
import es.schsebastian.foodrats.feature.moderation.data.firebase.ReportErrorMapper
import es.schsebastian.foodrats.feature.moderation.data.firebase.ReportFirestoreDataSource
import es.schsebastian.foodrats.feature.moderation.data.repository.BlockRepository
import es.schsebastian.foodrats.feature.moderation.data.repository.ReportRepository
import es.schsebastian.foodrats.feature.moderation.presentation.blocked.BlockedUsersViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Wires the moderation bounded context (UGC compliance §7.3). Binds the two `:core:domain` ports
 * ([BlockedAccountsPort], [ReportPort]) over Firestore so consumer features (feed/stats/meal) reach
 * block/report capability through the domain ports without a `:feature:moderation` dependency.
 *
 * `FirebaseFirestore`, `DispatcherProvider`, `Clock`, `SessionProvider`, `AccountReadPort`, and
 * `AnalyticsPort` are app-wide / feature-owned dependencies wired in the `shared` aggregator (declared
 * as `extraTypes` in `ModerationModuleVerifyTest`). The `viewModel` binding passes `analytics`
 * explicitly (NOT `viewModelOf`) so the `NoopAnalyticsTracker` default never short-circuits resolution.
 */
val moderationModule = module {
    // Block
    single<BlockDataSource> { BlockFirestoreDataSource(get()) }
    singleOf(::BlockErrorMapper)
    single<BlockedAccountsPort> { BlockRepository(get(), get(), get(), get()) }

    // Report
    single<ReportDataSource> { ReportFirestoreDataSource(get()) }
    singleOf(::ReportErrorMapper)
    single<ReportPort> { ReportRepository(get(), get(), get(), get()) }

    // Text moderation — the PURE on-device wordlist impl from :core:domain (no IO). A single for
    // cheap reuse; consumed by feed (MealDetailViewModel comment hard-block) and meal
    // (ComposePlateViewModel advisory banner) through the shared graph (UGC compliance §3/§7.3).
    single<TextModerationPort> { WordlistTextModeration() }

    viewModel {
        BlockedUsersViewModel(
            blocked = get(),
            accountRead = get(),
            session = get(),
            analytics = get(),
        )
    }
}
