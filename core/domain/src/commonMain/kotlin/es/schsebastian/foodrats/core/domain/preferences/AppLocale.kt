package es.schsebastian.foodrats.core.domain.preferences

import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * The set of in-app languages the user can pick from. [System] defers to the OS
 * locale at startup; the explicit codes override it for the whole UI tree.
 *
 * `tag` is BCP-47 (`en`, `es`); [System.tag] is empty by convention.
 */
enum class AppLocale(val tag: String) {
    System(""),
    En("en"),
    Es("es"),
    ;

    companion object {
        fun fromTagOrSystem(tag: String?): AppLocale = when (tag) {
            En.tag -> En
            Es.tag -> Es
            else -> System
        }
    }
}

interface LocalePort {
    val locale: Flow<AppLocale>
    fun supported(): List<AppLocale> = listOf(AppLocale.System, AppLocale.En, AppLocale.Es)
    suspend fun set(locale: AppLocale): Result<Unit, LocalePreferenceError>
}

sealed interface LocalePreferenceError {
    sealed interface Persist : LocalePreferenceError {
        data object Unavailable : Persist
    }
}
