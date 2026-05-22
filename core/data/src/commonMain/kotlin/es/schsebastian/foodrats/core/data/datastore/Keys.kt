package es.schsebastian.foodrats.core.data.datastore

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object Keys {
    val SessionToken         = StoreKey(stringPreferencesKey("session_token"))
    val ActiveCrewId         = StoreKey(stringPreferencesKey("active_crew_id"))
    val NotificationsAllowed = StoreKey(booleanPreferencesKey("notifications_allowed"))

    /**
     * One-shot flag set after the user has completed the post-signin notification
     * permission screen (whether they tapped Allow, Skip, or Open Settings). Drives
     * the RootNavViewModel gate that routes new accounts through that screen exactly
     * once. Cleared by signOut so a different account on the same device sees the
     * prompt again.
     */
    val NotificationsPermissionPrompted = StoreKey(booleanPreferencesKey("notifications_permission_prompted"))
    val LocaleTag            = StoreKey(stringPreferencesKey("locale_tag"))
    val ThemeMode            = StoreKey(stringPreferencesKey("theme_mode"))
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
