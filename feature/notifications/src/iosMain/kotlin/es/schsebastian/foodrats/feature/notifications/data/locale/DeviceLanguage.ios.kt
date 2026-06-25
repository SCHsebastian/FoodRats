package es.schsebastian.foodrats.feature.notifications.data.locale

import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

internal actual fun deviceLanguageTag(): String {
    // preferredLanguages reflects the user's app-language order ("es-ES", "en-US", …);
    // take the leading language subtag to match the server's "en"/"es" tables.
    val preferred = (NSLocale.preferredLanguages.firstOrNull() as? String).orEmpty()
    return preferred.substringBefore('-').substringBefore('_').lowercase().ifBlank { "en" }
}
