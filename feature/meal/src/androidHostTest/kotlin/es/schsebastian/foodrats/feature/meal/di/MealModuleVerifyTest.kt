package es.schsebastian.foodrats.feature.meal.di

import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.storage.FirebaseStorage
import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.location.LocationProvider
import es.schsebastian.foodrats.core.domain.meal.IngredientReadPort
import es.schsebastian.foodrats.core.domain.meal.MealClassifierPort
import es.schsebastian.foodrats.core.domain.notifications.StreakNotificationPort
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.domain.telemetry.CrashReporter
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.meal.data.upload.MealUploadScheduler
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
 *  - GitLive Firebase handles + `AppPreferences`/`Json` (datasources + draft store, app-wide).
 *  - `:core:domain` infra: `Clock`, `TimeZone`, `DispatcherProvider`, `CrashReporter`,
 *    `SessionProvider`, `ActiveCrewProvider`, `LocationProvider`.
 *  - Cross-feature ports the composer/coordinator resolve at app composition: `MealClassifierPort`
 *    (`:feature:meal-ai`), `IngredientReadPort` (`:feature:ingredient`), `StreakNotificationPort`
 *    (`:feature:notifications`).
 *  - `MealUploadScheduler` is the per-platform binding (`mealAndroidModule`/`mealIosModule`).
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
                AppPreferences::class,
                Json::class,
                Clock::class,
                TimeZone::class,
                DispatcherProvider::class,
                CrashReporter::class,
                SessionProvider::class,
                ActiveCrewProvider::class,
                LocationProvider::class,
                MealClassifierPort::class,
                IngredientReadPort::class,
                StreakNotificationPort::class,
                MealUploadScheduler::class,
            ),
        )
    }
}
