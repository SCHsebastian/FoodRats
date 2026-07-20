package es.schsebastian.foodrats.app.recap

import es.schsebastian.foodrats.core.domain.meal.Meal
import es.schsebastian.foodrats.core.domain.meal.MealWithRatings
import es.schsebastian.foodrats.core.domain.model.AccountId
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
 *
 * [weekMeals] is the crew's meals for the same recap week (a `MealReadPort.observeRange` read the
 * ViewModel feeds in — advisory only, see below) and [myAccountId] is the signed-in member, used to
 * pick a plate photo for the scenes that render a full-bleed photo floor (TRACK B). Photo selection
 * is entirely best-effort: every scene degrades to `photoUrl = null` (its existing brand-motif brush)
 * when [weekMeals] is empty or a scene's subject has no matching meal — this function never fails.
 */
fun assembleWeeklyRecap(
    stats: StatsSnapshot,
    achievements: AchievementsSnapshot?,
    weekLabel: String,
    weekWindowStartEpochMs: Long,
    weekWindowEndEpochMs: Long,
    weekMeals: List<MealWithRatings> = emptyList(),
    myAccountId: AccountId? = null,
): WeeklyRecap {
    // Tracks photos already handed to an earlier scene so Badges/Cuisines prefer a fresh one when
    // one's easily available — see [newestPhotoUrl]'s `excluding` param. Not a hard uniqueness
    // guarantee (Cover/TopMeal deliberately can share the week's best plate).
    val usedPhotoUrls = mutableSetOf<String>()
    fun claim(url: String?): String? {
        if (url != null) usedPhotoUrls += url
        return url
    }

    val crewNewestPhoto = weekMeals.newestPhotoUrl()
    val myNewestOwnPhoto = myAccountId?.let { weekMeals.newestPhotoUrlOf(it) }

    val scenes = buildList {
        add(
            RecapScene.Cover(
                weekLabel = weekLabel,
                photoUrl = claim(stats.week.bestMeal?.photoUrl ?: crewNewestPhoto),
            ),
        )

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
            add(
                RecapScene.BestCook(
                    memberName = cook.displayName,
                    avgScore = cook.averageScore,
                    photoUrl = claim(weekMeals.highestScoredPhotoUrlOf(cook.accountId)),
                ),
            )
        }

        stats.week.mostProlific?.let { prolific ->
            add(
                RecapScene.MostProlific(
                    memberName = prolific.displayName,
                    postCount = prolific.mealCount,
                    photoUrl = claim(weekMeals.newestPhotoUrlOf(prolific.accountId)),
                ),
            )
        }

        val streakDays = stats.hero.personalStreak.days
        if (streakDays > 0) {
            add(RecapScene.Streak(streakDays = streakDays, photoUrl = claim(myNewestOwnPhoto ?: crewNewestPhoto)))
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
            val photo = claim(weekMeals.newestPhotoUrl(excluding = usedPhotoUrls) ?: crewNewestPhoto)
            add(RecapScene.Badges(titleKeys = weekBadgeKeys, photoUrl = photo))
        }

        stats.cuisinePassport?.let { passport ->
            if (passport.collectedCount > 0) {
                val photo = claim(weekMeals.newestPhotoUrl(excluding = usedPhotoUrls) ?: crewNewestPhoto)
                add(
                    RecapScene.Cuisines(
                        collectedCount = passport.collectedCount,
                        totalCount = passport.totalCount,
                        photoUrl = photo,
                    ),
                )
            }
        }

        add(
            RecapScene.YourWeek(
                streakDays = streakDays,
                cuisinesCollected = stats.cuisinePassport?.collectedCount ?: 0,
                ingredientsCollected = stats.ingredientBingo?.collectedCount ?: 0,
                photoUrl = myNewestOwnPhoto ?: crewNewestPhoto,
            ),
        )
    }
    return WeeklyRecap(scenes = scenes)
}

/**
 * The URL this meal's plate would render at if used as a full-bleed story floor: the full photo,
 * else the thumbnail, else the first multi-photo plate — the same fallback chain used for feed/stats
 * floors elsewhere in the app. `null` when the meal has no usable photo URL at all.
 */
private fun Meal.storyPhotoUrl(): String? {
    val url = photoUrl.ifBlank { thumbnailUrl }.ifBlank { plates.firstOrNull()?.photoUrl.orEmpty() }
    return url.ifBlank { null }
}

/** The crew's most recently published plate photo, optionally skipping already-claimed URLs. */
private fun List<MealWithRatings>.newestPhotoUrl(excluding: Set<String> = emptySet()): String? =
    sortedByDescending { it.meal.publishedAt }
        .firstNotNullOfOrNull { m -> m.meal.storyPhotoUrl()?.takeUnless { it in excluding } }

/** [accountId]'s most recently published plate photo this week. */
private fun List<MealWithRatings>.newestPhotoUrlOf(accountId: AccountId): String? =
    filter { it.meal.author.accountId == accountId }
        .sortedByDescending { it.meal.publishedAt }
        .firstNotNullOfOrNull { it.meal.storyPhotoUrl() }

/** [accountId]'s highest-scored plate photo this week (ties broken by most recent). */
private fun List<MealWithRatings>.highestScoredPhotoUrlOf(accountId: AccountId): String? =
    filter { it.meal.author.accountId == accountId }
        .sortedWith(
            compareByDescending<MealWithRatings> { it.averageScore ?: -1.0 }
                .thenByDescending { it.meal.publishedAt },
        )
        .firstNotNullOfOrNull { it.meal.storyPhotoUrl() }
