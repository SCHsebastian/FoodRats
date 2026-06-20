package es.schsebastian.foodrats.feature.crew.di

import dev.gitlive.firebase.firestore.FirebaseFirestore
import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.data.preferences.WelcomeDismissalRepository
import es.schsebastian.foodrats.core.database.FoodRatsDatabase
import es.schsebastian.foodrats.core.domain.account.AccountReadPort
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsPort
import es.schsebastian.foodrats.core.domain.connectivity.ConnectivityPort
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.outbox.OutboxPort
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify
import kotlin.test.Test
import kotlin.random.Random

/**
 * Static Koin graph check for [crewModule]: a missing or mis-typed binding fails here instead of at
 * app launch.
 *
 * [extraTypes]:
 *  - `FirebaseFirestore`, `DispatcherProvider`, `SessionProvider`, `AccountReadPort` are app-wide /
 *    `:feature:auth`-owned dependencies wired in the `shared` aggregator.
 *  - `Random` backs `CrewCodeGenerator` (passed as `Random.Default` in the binding lambda; the ctor
 *    is still reflected).
 *  - `CrewId` is the runtime parameter of `CrewSettingsViewModel` (`viewModel { (crewId) -> ... }`);
 *    `verify` reflects the VM constructor including that parameter, so the type must be declared.
 *  - `FoodRatsDatabase` (bound by `:core:database`'s `databaseModule`) backs the offline-first P3b
 *    `CrewLocalStore`; `CoroutineScope` is the app-lifetime `named("appScope")` scope bound in
 *    `ingredientModule` and consumed by the `CrewSyncEngine`. `AppPreferences` + `Json` remain
 *    app-wide deps wired in the `shared` aggregator (datasource / DataStore plumbing).
 *
 * `verify` is JVM-only, so this lives in androidHostTest.
 */
class CrewModuleVerifyTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun crew_module_graph_is_complete() {
        crewModule.verify(
            extraTypes = listOf(
                FirebaseFirestore::class,
                DispatcherProvider::class,
                SessionProvider::class,
                AccountReadPort::class,
                Random::class,
                CrewId::class,
                String::class,
                AnalyticsPort::class,
                AppPreferences::class,
                Json::class,
                // Bound by :core:database's databaseModule; backs CrewLocalStore.
                FoodRatsDatabase::class,
                CoroutineScope::class,
                ConnectivityPort::class,
                OutboxPort::class,
                // C6 — WelcomeDismissalRepository is bound in coreDataModule; CrewWelcomePort consumes it.
                WelcomeDismissalRepository::class,
            ),
        )
    }
}
