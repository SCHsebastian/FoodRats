package es.schsebastian.foodrats.core.data.datastore

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object Keys {
    val SessionToken         = StoreKey(stringPreferencesKey("session_token"))
    val ActiveCrewId         = StoreKey(stringPreferencesKey("active_crew_id"))
    val NotificationsAllowed = StoreKey(booleanPreferencesKey("notifications_allowed"))
    val LocaleTag            = StoreKey(stringPreferencesKey("locale_tag"))
    val MealDraftJson        = StoreKey(stringPreferencesKey("meal_draft_json"))

    /**
     * True iff a meal-upload was enqueued and has not yet succeeded. Survives
     * process death so the coordinator can resume the upload on next launch
     * (in addition to WorkManager retries on Android). Cleared by the
     * coordinator on successful publish; left set on failure so retries can
     * pick it up.
     */
    val MealUploadPending    = StoreKey(booleanPreferencesKey("meal_upload_pending"))
}
