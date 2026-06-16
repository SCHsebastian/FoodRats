package es.schsebastian.foodrats.feature.achievements.domain.model

/**
 * A presentation-facing icon key for an [Achievement]. Deliberately a plain enum carrying **no**
 * Compose type, so the domain `Achievement` stays free of `ImageVector` — the design-system /
 * presentation layer maps each value to an `FrIcons` vector (spec §5.3, §8.4).
 */
enum class AchievementIcon {
    FirstPlate,
    Plate,
    Trophy,
    Ingredients,
    Streak,
    CrewStreak,
    Sunrise,
    Moon,
    Chef,
    ChefHat,
    Globe,
}
