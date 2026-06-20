package es.schsebastian.foodrats.feature.meal.di

import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.storage.FirebaseStorage
import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.database.FoodRatsDatabase
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsPort
import es.schsebastian.foodrats.core.domain.config.FeatureFlagPort
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.crew.CrewMembershipPort
import es.schsebastian.foodrats.core.domain.cuisine.CuisineReadPort
import es.schsebastian.foodrats.core.domain.location.LocationProvider
import es.schsebastian.foodrats.core.domain.meal.IngredientReadPort
import es.schsebastian.foodrats.core.domain.meal.MealClassifierPort
import es.schsebastian.foodrats.core.domain.connectivity.ConnectivityPort
import es.schsebastian.foodrats.core.domain.moderation.TextModerationPort
import es.schsebastian.foodrats.core.domain.preferences.LocalePort
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.domain.telemetry.CrashReporter
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.meal.data.upload.MealUploadScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify
import kotlin.test.Test

/**
 * Static Koin graph check for [mealModule]: a missing or mis-typed binding fails here instead of at
 * app launch.
 *
 * [extraTypes] cover:
 *  - GitLive Firebase handles (`FirebaseFirestore`/`FirebaseStorage`/`FirebaseAuth` — the last
 *    backs the `MealAuthorIdentity` binding) + `AppPreferences`/`Json` (datasources + draft store,
 *    app-wide).
 *  - `:core:domain` infra: `Clock`, `TimeZone`, `DispatcherProvider`, `CrashReporter`,
 *    `SessionProvider`, `ActiveCrewProvider`, `LocationProvider`, `FeatureFlagPort`
 *    (the meal-AI kill-switch `ClassifyDraftPlateUseCase` reads; bound per-platform).
 *  - Cross-feature ports the composer/coordinator resolve at app composition: `MealClassifierPort`
 *    (`:feature:meal-ai`), `IngredientReadPort` (`:feature:ingredient`). (The local-streak-nudge
 *    `StreakNotificationPort` is no longer a coordinator dependency — the server `streakNudge`
 *    Cloud Function supersedes the local DailyInactivityWorker; see the coordinator's publish arm.)
 *  - `MealUploadScheduler` is the per-platform binding (`mealAndroidModule`/`mealIosModule`).
 *  - `CoroutineScope` is the app-lifetime `named("appScope")` scope (bound by `ingredientModule`,
 *    shared in the merged graph) that the offline-first `MealSyncEngine` runs its sync jobs on.
 *
 * `verify` is JVM-only, so this lives in androidHostTest.
 */
class MealModuleVerifyTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun meal_module_graph_is_complete() {
        mealModule.verify(
            extraTypes = listOf(
                FirebaseFirestore::class,
                FirebaseStorage::class,
                FirebaseAuth::class,
                AppPreferences::class,
                // Bound by :core:database's databaseModule; backs MealLocalStore.
                FoodRatsDatabase::class,
                Json::class,
                Clock::class,
                TimeZone::class,
                DispatcherProvider::class,
                CrashReporter::class,
                SessionProvider::class,
                ActiveCrewProvider::class,
                CrewMembershipPort::class,
                LocationProvider::class,
                MealClassifierPort::class,
                IngredientReadPort::class,
                CuisineReadPort::class,
                // UGC §3 — the advisory description filter + the LocalePort it reads the language from.
                TextModerationPort::class,
                LocalePort::class,
                FeatureFlagPort::class,
                MealUploadScheduler::class,
                ConnectivityPort::class,
                AnalyticsPort::class,
                // App-lifetime named("appScope") scope (bound by ingredientModule, shared in the
                // merged graph) the new MealSyncEngine runs its per-crew sync jobs on.
                CoroutineScope::class,
            ),
        )
    }
}
