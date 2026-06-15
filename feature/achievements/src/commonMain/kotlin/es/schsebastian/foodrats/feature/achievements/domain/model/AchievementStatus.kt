package es.schsebastian.foodrats.feature.achievements.domain.model

/**
 * How far the member is toward an achievement. Boolean criteria use [target] = 1 and [current] 0/1.
 * (spec §5.4)
 */
data class AchievementProgress(val current: Int, val target: Int) {
    val isMet: Boolean get() = target > 0 && current >= target
}

/**
 * The evaluated state of one [Achievement]: its [progress] and, once persisted, when it was
 * unlocked. [unlockedAtEpochMs] is `null` when the achievement is locked OR met-but-not-yet-written
 * — display uses `unlockedAtEpochMs != null` as the "earned" predicate (spec §6.3).
 */
data class AchievementStatus(
    val achievement: Achievement,
    val progress: AchievementProgress,
    val unlockedAtEpochMs: Long? = null,
)
