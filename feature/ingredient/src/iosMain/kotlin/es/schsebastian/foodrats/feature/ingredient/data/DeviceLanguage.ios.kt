package es.schsebastian.foodrats.feature.ingredient.data

import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

internal actual fun deviceLanguageTag(): String {
    // preferredLanguages reflects the user's app-language order ("es-ES", "en-US", …);
    // take the leading language subtag to match the catalog's "en"/"es" name keys.
    val preferred = (NSLocale.preferredLanguages.firstOrNull() as? String).orEmpty()
    return preferred.substringBefore('-').substringBefore('_').lowercase().ifBlank { "en" }
}
