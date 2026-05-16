package es.schsebastian.foodrats.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import java.io.File

private var injectedFilesDir: File? = null

fun installAndroidDataStoreContext(filesDir: File) { injectedFilesDir = filesDir }

actual fun providePreferencesDataStore(): DataStore<Preferences> {
    val dir = injectedFilesDir
        ?: error("Call installAndroidDataStoreContext(context.filesDir) from Application.onCreate before Koin starts")
    return PreferenceDataStoreFactory.create(produceFile = { File(dir, DATASTORE_FILE_NAME) })
}
