package es.schsebastian.foodrats.app.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import platform.Foundation.NSLocale
import platform.Foundation.NSUserDefaults
import platform.Foundation.preferredLanguages

/**
 * iOS applies the locale via the `AppleLanguages` user-default, which Compose Resources reads.
 * Combined with the `key(languageTag)` recomposition in [ProvideAppLocale], strings re-resolve.
 *
 * Note: the app currently routes iOS language changes to the system Settings app (see
 * `AppLanguageSettings.ios.kt`), so in practice this is invoked with the system tag and the
 * write below is a no-op. It is implemented so the in-app path works if iOS later opts into it.
 */
actual object LocalAppLocale {
    private const val APPLE_LANGUAGES = "AppleLanguages"
    private val systemDefault: String =
        (NSLocale.preferredLanguages.firstOrNull() as? String) ?: "en"
    private val local = staticCompositionLocalOf { systemDefault }

    actual val current: String
        @Composable get() = local.current

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        val defaults = NSUserDefaults.standardUserDefaults
        val applied = if (value == null) {
            defaults.removeObjectForKey(APPLE_LANGUAGES)
            systemDefault
        } else {
            defaults.setObject(listOf(value), APPLE_LANGUAGES)
            value
        }
        return local.provides(applied)
    }
}
