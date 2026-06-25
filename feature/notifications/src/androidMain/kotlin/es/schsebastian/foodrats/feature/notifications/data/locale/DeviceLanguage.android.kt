package es.schsebastian.foodrats.feature.notifications.data.locale

import java.util.Locale

internal actual fun deviceLanguageTag(): String =
    Locale.getDefault().language.lowercase().ifBlank { "en" }
