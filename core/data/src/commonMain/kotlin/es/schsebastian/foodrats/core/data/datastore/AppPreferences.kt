package es.schsebastian.foodrats.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AppPreferences(private val store: DataStore<Preferences>) {
    fun <T : Any> observe(key: StoreKey<T>): Flow<T?> = store.data.map { prefs -> prefs[key.prefs] }
    suspend fun <T : Any> set(key: StoreKey<T>, value: T) { store.edit { it[key.prefs] = value } }
    suspend fun <T : Any> clear(key: StoreKey<T>) { store.edit { it.remove(key.prefs) } }
}
