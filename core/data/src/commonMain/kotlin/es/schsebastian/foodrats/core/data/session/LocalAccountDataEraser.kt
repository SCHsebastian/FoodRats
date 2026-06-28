package es.schsebastian.foodrats.core.data.session

import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.data.datastore.Keys
import es.schsebastian.foodrats.core.database.FoodRatsDatabase
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.session.LocalDataEraser
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import kotlinx.coroutines.withContext

/**
 * Wipes this device's account-scoped local caches on sign-out (security #3): the SQLDelight
 * feed/crew/outbox cache and the account-scoped DataStore keys. Device/human-scoped preferences
 * (theme, locale, accepted-EULA version, analytics-consent, AI opt-out, accent palette,
 * notifications-allowed, reminder times) are deliberately preserved.
 *
 * One IO boundary for the whole erase (the data-layer rule); the inner SQLDelight `transaction`
 * makes the cache wipe all-or-nothing.
 */
class LocalAccountDataEraser(
    private val database: FoodRatsDatabase,
    private val prefs: AppPreferences,
    private val dispatchers: DispatcherProvider,
) : LocalDataEraser {

    override suspend fun eraseLocalAccountData() {
        withContext(dispatchers.io) {
            // 1. SQLDelight cache: the previous user's feed meals + ratings, cached crews, and the
            //    durable write outbox (queued mutations that must not replay under the next account).
            database.transaction {
                database.mealQueries.deleteAllRatings()
                database.mealQueries.deleteAllMeals()
                database.crewQueries.deleteAll()
                database.outboxQueries.deleteAllOutbox()
            }
            // 2. Account-scoped DataStore keys (cached drafts, publish/write queues, audience, sync
            //    stamps, dismissals, active crew, one-shot permission flag, legacy outbox JSON,
            //    persisted signed-image-URL cache).
            prefs.clearAll(
                Keys.SessionToken,
                Keys.ActiveCrewId,
                Keys.NotificationsPermissionPrompted,
                Keys.MealDraftJson,
                Keys.MealUploadPending,
                Keys.DraftQueueJson,
                Keys.IngredientCatalogJson,
                Keys.CuisineCatalogJson,
                Keys.OutboxJson,
                Keys.MealSyncTimestamps,
                Keys.DefaultAudienceCrewIds,
                Keys.DismissedWelcomes,
                Keys.PlateUrlCacheJson,
            )
            FrLog.d(FrLog.Tags.SignOut) { "local account data erased (cache + scoped prefs)" }
        }
    }
}
