package es.schsebastian.foodrats.feature.achievements.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import es.schsebastian.foodrats.core.designsystem.atoms.FrBadgeTier
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.feature.achievements.domain.model.AchievementIcon
import es.schsebastian.foodrats.feature.achievements.domain.model.AchievementTier

/**
 * Maps the domain-only [AchievementIcon] enum (which holds no Compose type) to a concrete `FrIcons`
 * vector. Lives in the **feature** (not `:core:designsystem`) because it touches a domain type — the
 * same rule that keeps `FrMealCard` out of the design system.
 */
internal fun AchievementIcon.toVector(): ImageVector = when (this) {
    AchievementIcon.Plate -> FrIcons.Restaurant
    AchievementIcon.Trophy -> FrIcons.Trophy
    AchievementIcon.Ingredients -> FrIcons.Eco
    AchievementIcon.Streak -> FrIcons.Flame
    AchievementIcon.CrewStreak -> FrIcons.Group
    AchievementIcon.Sunrise -> FrIcons.Sun
    AchievementIcon.Moon -> FrIcons.Moon
    AchievementIcon.Chef -> FrIcons.Crown
    AchievementIcon.Globe -> FrIcons.Public
}

/** Maps the optional domain [AchievementTier] to the presentation [FrBadgeTier]. */
internal fun AchievementTier?.toBadgeTier(): FrBadgeTier = when (this) {
    AchievementTier.Bronze -> FrBadgeTier.Bronze
    AchievementTier.Silver -> FrBadgeTier.Silver
    AchievementTier.Gold -> FrBadgeTier.Gold
    null -> FrBadgeTier.None
}

/**
 * The meaning tint for a badge family. Streak families read with the `streakHot` semantic color;
 * everything else uses `celebration` — both from [LocalFrSemanticColors] (never a Material brand role
 * aliased for meaning, never a raw `Color(0x…)`).
 */
@Composable
internal fun AchievementIcon.tint(): Color {
    val semantic = LocalFrSemanticColors.current
    return when (this) {
        AchievementIcon.Streak, AchievementIcon.CrewStreak -> semantic.streakHot
        else -> semantic.celebration
    }
}
