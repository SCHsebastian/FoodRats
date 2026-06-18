package es.schsebastian.foodrats.app.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Android applies the locale by mutating the active resources Configuration — that's what
 * Compose Resources reads on Android. Combined with the `key(languageTag)` recomposition in
 * [ProvideAppLocale], every `resolve(...)` re-resolves against [value].
 */
actual object LocalAppLocale {
    private var systemDefault: Locale? = null
    private val local = staticCompositionLocalOf { Locale.getDefault().toLanguageTag() }

    actual val current: String
        @Composable get() = local.current

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        val configuration = LocalConfiguration.current
        if (systemDefault == null) systemDefault = Locale.getDefault()
        val applied = when (value) {
            null -> systemDefault!!
            else -> Locale.forLanguageTag(value)
        }
        Locale.setDefault(applied)
        configuration.setLocale(applied)
        val resources = LocalContext.current.resources
        @Suppress("DEPRECATION")
        resources.updateConfiguration(configuration, resources.displayMetrics)
        return local.provides(applied.toLanguageTag())
    }
}
