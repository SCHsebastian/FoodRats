package es.schsebastian.foodrats.feature.notifications.data.locale

/**
 * The device's current UI language as a bare ISO-639 tag ("en", "es", …).
 *
 * Used by [AppLanguageTag] to resolve the effective language when the in-app locale is
 * [AppLocale.System][es.schsebastian.foodrats.core.domain.preferences.AppLocale.System] — mirroring
 * how Compose Resources resolve UI strings from the OS language. Mirrors `:feature:ingredient`'s
 * `deviceLanguageTag()`; kept local to avoid a cross-feature dependency.
 */
internal expect fun deviceLanguageTag(): String
