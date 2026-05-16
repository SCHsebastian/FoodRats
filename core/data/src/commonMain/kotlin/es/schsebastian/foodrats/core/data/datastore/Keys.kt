package es.schsebastian.foodrats.core.data.datastore

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object Keys {
    val SessionToken         = StoreKey(stringPreferencesKey("session_token"))
    val ActiveCrewId         = StoreKey(stringPreferencesKey("active_crew_id"))
    val NotificationsAllowed = StoreKey(booleanPreferencesKey("notifications_allowed"))
    val LocaleTag            = StoreKey(stringPreferencesKey("locale_tag"))
    val MealDraftJson        = StoreKey(stringPreferencesKey("meal_draft_json"))
}
