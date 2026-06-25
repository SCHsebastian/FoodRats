package es.schsebastian.foodrats.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
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
            FrLog.d(FrLog.Tags.Prefs) { "observe(${key.prefs.name}) emit=${v ?: "null"}" }
        }

    suspend fun <T : Any> set(key: StoreKey<T>, value: T) {
        FrLog.d(FrLog.Tags.Prefs) { "set(${key.prefs.name}) = $value" }
        store.edit { it[key.prefs] = value }
    }

    /**
     * Writes multiple key/value pairs in a single DataStore transaction so observers never see a
     * partially-applied set. Use this when several keys must update atomically (e.g. a consent
     * decision's state + version + timestamp).
     */
    suspend fun setAll(vararg entries: Entry<*>) {
        FrLog.d(FrLog.Tags.Prefs) { "setAll(${entries.joinToString { it.key.prefs.name }})" }
        store.edit { prefs -> entries.forEach { it.applyTo(prefs) } }
    }

    /** A typed key/value pair for [setAll]. Build with [StoreKey.to]. */
    class Entry<T : Any> internal constructor(val key: StoreKey<T>, private val value: T) {
        internal fun applyTo(prefs: MutablePreferences) {
            prefs[key.prefs] = value
        }
    }

    suspend fun <T : Any> clear(key: StoreKey<T>) {
        FrLog.d(FrLog.Tags.Prefs) { "clear(${key.prefs.name})" }
        store.edit { it.remove(key.prefs) }
    }

    /**
     * Removes several keys in a single DataStore transaction (atomic — observers never see a
     * partially-cleared state). Used by the sign-out local-data wipe (security #3).
     */
    suspend fun clearAll(vararg keys: StoreKey<*>) {
        FrLog.d(FrLog.Tags.Prefs) { "clearAll(${keys.joinToString { it.prefs.name }})" }
        store.edit { prefs -> keys.forEach { prefs.remove(it.prefs) } }
    }
}
