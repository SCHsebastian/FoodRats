package es.schsebastian.foodrats.core.data.preferences

import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.data.datastore.Keys
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.preferences.AppLocale
import es.schsebastian.foodrats.core.domain.preferences.LocalePort
import es.schsebastian.foodrats.core.domain.preferences.LocalePreferenceError
import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class LocaleRepository(
    private val prefs: AppPreferences,
    private val dispatchers: DispatcherProvider,
) : LocalePort {

    override val locale: Flow<AppLocale> = prefs.observe(Keys.LocaleTag).map { stored ->
        AppLocale.fromTagOrSystem(stored)
    }

    override suspend fun set(locale: AppLocale): Result<Unit, LocalePreferenceError> =
        withContext(dispatchers.io) {
            runCatching {
                if (locale == AppLocale.System) prefs.clear(Keys.LocaleTag)
                else prefs.set(Keys.LocaleTag, locale.tag)
            }.fold(
                onSuccess = { Result.success(Unit) },
                onFailure = { Result.failure(LocalePreferenceError.Persist.Unavailable) },
            )
        }
}
