package es.schsebastian.foodrats.feature.crew.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

class ActiveCrewLocalStore(
    private val dataStore: DataStore<Preferences>,
) : ActiveCrewProvider {

    private val key = stringPreferencesKey("active_crew_id")

    override val current: Flow<CrewId?> = dataStore.data
        .map { prefs ->
            prefs[key]?.let { raw -> (CrewId.of(raw) as? es.schsebastian.foodrats.core.domain.result.Result.Ok)?.value }
        }
        // `dataStore.data` re-emits the WHOLE preferences snapshot on every write to this DataStore —
        // including unrelated keys (theme, locale, notifications, analytics consent). Without this,
        // the active crew id re-emits an identical value on each of those writes, which floods the
        // RootNav stage `combine` and can drive spurious top-level re-navigations mid-screen (the
        // white-screen back-stack collapse). Only surface an actual change of the active crew.
        .distinctUntilChanged()
        .onEach { id ->
            FrLog.d(FrLog.Tags.ActiveCrew) { "current emit=${id?.value ?: "null"}" }
        }

    override suspend fun set(crewId: CrewId) {
        FrLog.d(FrLog.Tags.ActiveCrew) { "set(${crewId.value})" }
        dataStore.edit { it[key] = crewId.value }
    }

    override suspend fun clear() {
        FrLog.d(FrLog.Tags.ActiveCrew) { "clear()" }
        dataStore.edit { it.remove(key) }
    }
}
