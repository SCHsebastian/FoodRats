package es.schsebastian.foodrats.feature.achievements.presentation.components

import androidx.compose.ui.graphics.vector.ImageVector
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.feature.achievements.domain.model.AchievementIcon

/**
 * Maps the domain-only [AchievementIcon] enum (which holds no Compose type) to a concrete `FrIcons`
 * vector. Lives in the **feature** (not `:core:designsystem`) because it touches a domain type — the
 * same rule that keeps `FrMealCard` out of the design system.
 */
internal fun AchievementIcon.toVector(): ImageVector = when (this) {
    AchievementIcon.FirstPlate -> FrIcons.Star
    AchievementIcon.Plate -> FrIcons.Restaurant
    AchievementIcon.Trophy -> FrIcons.Trophy
    AchievementIcon.Ingredients -> FrIcons.Eco
    AchievementIcon.Streak -> FrIcons.Flame
    AchievementIcon.CrewStreak -> FrIcons.Group
    AchievementIcon.Sunrise -> FrIcons.Sun
    AchievementIcon.Moon -> FrIcons.Moon
    AchievementIcon.Chef -> FrIcons.Crown
    AchievementIcon.ChefHat -> FrIcons.ChefHat
    AchievementIcon.Globe -> FrIcons.Public
}
