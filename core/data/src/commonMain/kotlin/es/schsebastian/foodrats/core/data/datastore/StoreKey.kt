package es.schsebastian.foodrats.core.data.datastore

import androidx.datastore.preferences.core.Preferences

class StoreKey<T : Any>(internal val prefs: Preferences.Key<T>)
