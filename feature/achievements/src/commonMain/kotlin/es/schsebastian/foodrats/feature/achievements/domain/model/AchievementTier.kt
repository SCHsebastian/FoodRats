package es.schsebastian.foodrats.feature.achievements.domain.model

/**
 * Optional visual-treatment metadata for an achievement that belongs to a tiered family
 * (e.g. a 10 / 50 / 100 meal-count family shares a concept across three tiers).
 *
 * Tier does **not** affect evaluation — it is presentation-only.
 */
enum class AchievementTier { Bronze, Silver, Gold }
