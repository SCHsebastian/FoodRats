package es.schsebastian.foodrats.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

expect fun providePreferencesDataStore(): DataStore<Preferences>

internal const val DATASTORE_FILE_NAME = "foodrats.preferences_pb"
