package es.schsebastian.foodrats.feature.ingredient.data

/**
 * The device's current UI language as a bare ISO-639 tag ("en", "es", …).
 *
 * Used to resolve catalog ingredient names (Firestore `names[lang]`) when the
 * in-app locale is [AppLocale.System][es.schsebastian.foodrats.core.domain.preferences.AppLocale.System]
 * — i.e. the user hasn't picked an explicit in-app language, so names should
 * follow the OS language, mirroring how Compose Resources resolve UI strings.
 * Unsupported languages fall back to "en" via the DTO mapper.
 */
internal expect fun deviceLanguageTag(): String
