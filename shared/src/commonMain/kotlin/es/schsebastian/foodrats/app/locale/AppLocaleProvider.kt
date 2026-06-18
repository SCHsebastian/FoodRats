package es.schsebastian.foodrats.app.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.key

/**
 * Cross-platform override for the locale that Compose Resources uses to resolve
 * `stringResource` / `resolve(StringKey)`. [languageTag] is a BCP-47 tag ("en", "es")
 * or `null` to follow the system locale.
 *
 * This is the link that was missing: persisting an [AppLocale][es.schsebastian.foodrats.core.domain.preferences.AppLocale]
 * never changed the UI because nothing re-applied it to string resolution. Changing the
 * platform locale alone does NOT invalidate Compose's cached string lookups, so
 * [ProvideAppLocale] re-keys the subtree on [languageTag] to force a recomposition that
 * resolves every `resolve(...)` against the new locale.
 */
expect object LocalAppLocale {
    val current: String @Composable get

    @Composable
    infix fun provides(value: String?): ProvidedValue<*>
}

/**
 * Applies [languageTag] to all string resolution inside [content] and recomposes it when
 * the tag changes. Place this BELOW any state that must survive a language switch (e.g. the
 * root NavController) so the back stack is preserved while only the UI re-resolves strings.
 */
@Composable
fun ProvideAppLocale(languageTag: String?, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppLocale provides languageTag) {
        key(languageTag) {
            content()
        }
    }
}
