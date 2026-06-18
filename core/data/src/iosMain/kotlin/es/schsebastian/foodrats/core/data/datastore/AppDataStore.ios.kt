package es.schsebastian.foodrats.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
actual fun providePreferencesDataStore(): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(produceFile = {
        val docDir = NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = false,
            error = null,
        ) ?: error("NSFileManager cannot resolve NSDocumentDirectory — cannot create DataStore")
        val docPath = docDir.path
            ?: error("NSDocumentDirectory URL has no path component — cannot create DataStore")
        ("$docPath/$DATASTORE_FILE_NAME").toPath()
    })
