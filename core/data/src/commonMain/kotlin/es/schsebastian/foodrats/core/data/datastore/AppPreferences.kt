package es.schsebastian.foodrats.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

class AppPreferences(private val store: DataStore<Preferences>) {
    fun <T : Any> observe(key: StoreKey<T>): Flow<T?> = store.data
        .map { prefs -> prefs[key.prefs] }
        .onEach { v ->
            FrLog.d(FrLog.Channel.Prefs) { "observe(${key.prefs.name}) emit=${v ?: "null"}" }
        }

    suspend fun <T : Any> set(key: StoreKey<T>, value: T) {
        FrLog.d(FrLog.Channel.Prefs) { "set(${key.prefs.name}) = $value" }
        store.edit { it[key.prefs] = value }
    }

    suspend fun <T : Any> clear(key: StoreKey<T>) {
        FrLog.d(FrLog.Channel.Prefs) { "clear(${key.prefs.name})" }
        store.edit { it.remove(key.prefs) }
    }
}
