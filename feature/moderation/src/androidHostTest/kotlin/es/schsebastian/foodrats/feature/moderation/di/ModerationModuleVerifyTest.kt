package es.schsebastian.foodrats.feature.moderation.di

import dev.gitlive.firebase.firestore.FirebaseFirestore
import es.schsebastian.foodrats.core.domain.account.AccountReadPort
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsPort
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.domain.time.Clock
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify
import kotlin.test.Test

/**
 * Static Koin graph check for [moderationModule]: a missing or mis-typed binding fails here instead of
 * at app launch.
 *
 * [extraTypes] are the cross-module dependencies moderation consumes but does not bind — all wired in
 * the `shared` aggregator:
 *  - `FirebaseFirestore` backs both Firestore data sources;
 *  - `DispatcherProvider` + `Clock` back both repositories;
 *  - `SessionProvider` + `AccountReadPort` + `AnalyticsPort` back `BlockedUsersViewModel`.
 *
 * `verify` is JVM-only, so this lives in androidHostTest.
 */
class ModerationModuleVerifyTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun moderation_module_graph_is_complete() {
        moderationModule.verify(
            extraTypes = listOf(
                FirebaseFirestore::class,
                DispatcherProvider::class,
                Clock::class,
                SessionProvider::class,
                AccountReadPort::class,
                AnalyticsPort::class,
            ),
        )
    }
}
