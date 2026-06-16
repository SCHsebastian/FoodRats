package es.schsebastian.foodrats.app.recap

import es.schsebastian.foodrats.feature.achievements.domain.usecase.AchievementsSnapshot
import es.schsebastian.foodrats.feature.stats.domain.model.StatsSnapshot

/**
 * Pure scene assembly (no Compose, no ports): folds the already-computed [StatsSnapshot] and
 * [AchievementsSnapshot] into an ordered [WeeklyRecap]. Reuses the existing client read paths — it
 * RECOMPUTES nothing — so the story is just a different rendering of data the Stats + Achievements
 * features already produce (roadmap §2.4).
 *
 * Scene order (§2.4): cover → top meal → best cook → most prolific → streak → badges → cuisines →
 * "your week". Every award/streak/badge/cuisine scene is appended ONLY when its data exists, so a
 * quiet crew-week collapses gracefully to cover + your-week instead of a wall of empty cards.
 *
 * [weekLabel] is a pre-formatted, locale-resolved label (the ViewModel can't format dates without
 * resources; for the MVP it passes a plain ISO week-start or a resolved range — see the ViewModel).
 * [weekWindowStartEpochMs]/[weekWindowEndEpochMs] bound the "unlocked THIS week" badge filter.
 */
fun assembleWeeklyRecap(
    stats: StatsSnapshot,
    achievements: AchievementsSnapshot?,
    weekLabel: String,
    weekWindowStartEpochMs: Long,
    weekWindowEndEpochMs: Long,
): WeeklyRecap {
    val scenes = buildList {
        add(RecapScene.Cover(weekLabel = weekLabel))

        stats.week.bestMeal?.let { award ->
            add(
                RecapScene.TopMeal(
                    photoUrl = award.photoUrl,
                    dishName = award.dish.value,
                    authorName = award.author.displayName,
                    score = award.score,
                    ratingCount = award.ratingCount,
                ),
            )
        }

        stats.week.bestCook?.let { cook ->
            add(RecapScene.BestCook(memberName = cook.displayName, avgScore = cook.averageScore))
        }

        stats.week.mostProlific?.let { prolific ->
            add(RecapScene.MostProlific(memberName = prolific.displayName, postCount = prolific.mealCount))
        }

        val streakDays = stats.hero.personalStreak.days
        if (streakDays > 0) {
            add(RecapScene.Streak(streakDays = streakDays))
        }

        val weekBadgeKeys = achievements
            ?.statuses
            .orEmpty()
            .filter { status ->
                val at = status.unlockedAtEpochMs ?: return@filter false
                at in weekWindowStartEpochMs..weekWindowEndEpochMs
            }
            .map { it.achievement.titleKey }
        if (weekBadgeKeys.isNotEmpty()) {
            add(RecapScene.Badges(titleKeys = weekBadgeKeys))
        }

        stats.cuisinePassport?.let { passport ->
            if (passport.collectedCount > 0) {
                add(
                    RecapScene.Cuisines(
                        collectedCount = passport.collectedCount,
                        totalCount = passport.totalCount,
                    ),
                )
            }
        }

        add(
            RecapScene.YourWeek(
                streakDays = streakDays,
                cuisinesCollected = stats.cuisinePassport?.collectedCount ?: 0,
                ingredientsCollected = stats.ingredientBingo?.collectedCount ?: 0,
            ),
        )
    }
    return WeeklyRecap(scenes = scenes)
}
