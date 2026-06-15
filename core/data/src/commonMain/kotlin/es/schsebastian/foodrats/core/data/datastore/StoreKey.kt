package es.schsebastian.foodrats.core.data.datastore

import androidx.datastore.preferences.core.Preferences

class StoreKey<T : Any>(internal val prefs: Preferences.Key<T>)

/** Pairs this key with [value] for an atomic [AppPreferences.setAll] write. */
infix fun <T : Any> StoreKey<T>.to(value: T): AppPreferences.Entry<T> = AppPreferences.Entry(this, value)
