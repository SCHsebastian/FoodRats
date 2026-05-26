package es.schsebastian.foodrats.feature.ingredient.data

import java.util.Locale

internal actual fun deviceLanguageTag(): String =
    Locale.getDefault().language.lowercase().ifBlank { "en" }
