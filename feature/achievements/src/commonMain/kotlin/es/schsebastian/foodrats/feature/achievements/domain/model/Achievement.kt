package es.schsebastian.foodrats.feature.achievements.domain.model

import es.schsebastian.foodrats.feature.achievements.i18n.AchievementStringKey

/**
 * A single catalog row: an [id] (compile-time constant), its i18n keys (never raw strings), an
 * [iconKey] (a presentation enum the design system resolves to a vector — so the domain holds no
 * Compose type), the [criterion] that decides when it is earned, and an optional visual [tier].
 * (spec §5.3)
 */
data class Achievement(
    val id: AchievementId,
    val titleKey: AchievementStringKey,
    val descriptionKey: AchievementStringKey,
    val iconKey: AchievementIcon,
    val criterion: AchievementCriterion,
    val tier: AchievementTier? = null,
)
