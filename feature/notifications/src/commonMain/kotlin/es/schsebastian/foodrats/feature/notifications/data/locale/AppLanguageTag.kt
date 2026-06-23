package es.schsebastian.foodrats.feature.notifications.data.locale

import es.schsebastian.foodrats.core.domain.preferences.AppLocale
import es.schsebastian.foodrats.core.domain.preferences.LocalePort
import es.schsebastian.foodrats.feature.notifications.domain.repository.EffectiveLanguageTag
import kotlinx.coroutines.flow.first

/**
 * [EffectiveLanguageTag] backed by [LocalePort]: an explicit in-app language wins; otherwise we
 * follow the OS via [deviceLanguageTag]. Mirrors the resolution `:feature:ingredient` does for
 * catalog name lookups, so notifications and ingredient names speak the same language.
 */
internal class AppLanguageTag(
    private val localePort: LocalePort,
) : EffectiveLanguageTag {
    override suspend fun invoke(): String {
        val locale = localePort.locale.first()
        return if (locale == AppLocale.System) deviceLanguageTag() else locale.tag
    }
}
