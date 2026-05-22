package es.schsebastian.foodrats.feature.auth.presentation.profile

/**
 * Platform integration for changing the app's language.
 *
 * On Android, Compose Multiplatform Resources honors the in-app
 * `LocalAppLocale` override out of the box, so the language row shows an
 * in-app radio picker and this hook is a no-op.
 *
 * On iOS, Compose Resources doesn't reliably honor a per-app locale override
 * mid-session — the iOS convention is for each app to deep-link to its own
 * page in the system Settings app, where the language can be picked natively.
 * That's what [openAppLanguageSettings] does on iOS.
 *
 * Callers branch on [opensSystemSettingsForLanguage] to decide which UI to
 * present: an in-app picker (Android) or a "Open Settings" drilldown (iOS).
 */
expect val opensSystemSettingsForLanguage: Boolean

expect fun openAppLanguageSettings()
