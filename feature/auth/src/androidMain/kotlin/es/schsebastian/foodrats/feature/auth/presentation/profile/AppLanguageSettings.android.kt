package es.schsebastian.foodrats.feature.auth.presentation.profile

actual val opensSystemSettingsForLanguage: Boolean = false

/** No-op on Android — the in-app picker handles language switching. */
actual fun openAppLanguageSettings() = Unit
