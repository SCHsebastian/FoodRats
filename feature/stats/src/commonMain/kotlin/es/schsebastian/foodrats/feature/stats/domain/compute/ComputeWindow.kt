package es.schsebastian.foodrats.feature.stats.domain.compute

import es.schsebastian.foodrats.core.domain.meal.IngredientSlug
import es.schsebastian.foodrats.core.domain.meal.MealWithRatings
import es.schsebastian.foodrats.core.domain.meal.mergedIngredientSlugs
import es.schsebastian.foodrats.feature.stats.domain.model.IngredientUsage
import es.schsebastian.foodrats.feature.stats.domain.model.MealAward
import es.schsebastian.foodrats.feature.stats.domain.model.MemberAverage
import es.schsebastian.foodrats.feature.stats.domain.model.MemberCount
import es.schsebastian.foodrats.feature.stats.domain.model.MemberIngredient
import es.schsebastian.foodrats.feature.stats.domain.model.StatsWindow
import es.schsebastian.foodrats.feature.stats.domain.model.WindowStats

private const val COOK_AWARD_MIN_PLATES = 3

fun computeWindow(
    meals: List<MealWithRatings>,
    window: StatsWindow,
    ingredientName: (IngredientSlug) -> String,
): WindowStats {
    val total = meals.size
    val avg = if (window.days <= 0) 0.0 else total.toDouble() / window.days.toDouble()

    val rated = meals.filter { it.ratings.isNotEmpty() }

    val best = rated.maxWithOrNull(
        compareBy<MealWithRatings> { it.averageScore ?: -1.0 }
            .thenBy { it.ratings.size }
            .thenBy { it.meal.publishedAt },
    )?.toAward()

    val mostVoted = rated.maxWithOrNull(
        compareBy<MealWithRatings> { it.ratings.size }
            .thenBy { it.averageScore ?: -1.0 }
            .thenBy { it.meal.publishedAt },
    )?.toAward()

    val byAuthor = meals.groupBy { it.meal.author.accountId }

    val mostProlific = byAuthor.entries
        .map { (id, list) ->
            val a = list.first().meal.author
            MemberCount(id, a.displayName, a.avatarUrl, list.size)
        }
        .takeIf { it.isNotEmpty() }
        ?.maxWithOrNull(
            compareBy<MemberCount> { it.mealCount }
                .thenByDescending { it.displayName },
        )

    val cookAverages = byAuthor.entries
        .filter { (_, list) -> list.size >= COOK_AWARD_MIN_PLATES }
        .mapNotNull { (id, list) ->
            val averages = list.mapNotNull { it.averageScore }
            if (averages.isEmpty()) null
            else {
                val a = list.first().meal.author
                MemberAverage(id, a.displayName, a.avatarUrl, averages.average(), list.size)
            }
        }

    val bestCook = cookAverages.maxWithOrNull(
        compareBy<MemberAverage> { it.averageScore }
            .thenBy { it.postCount }
            .thenByDescending { it.displayName },
    )

    val worstCook = cookAverages.minWithOrNull(
        compareBy<MemberAverage> { it.averageScore }
            .thenByDescending { it.postCount }
            .thenBy { it.displayName },
    )

    val mostCriticized = when {
        worstCook == null -> null
        cookAverages.size < 2 -> null
        worstCook.accountId == bestCook?.accountId -> null
        else -> worstCook
    }

    // Each ingredient counts at most once per meal (mergedIngredientSlugs is already deduped),
    // so a count is "number of meals using it". Tie-break: count desc, then name asc.
    val mostUsedIngredient = meals
        .flatMap { it.meal.mergedIngredientSlugs() }
        .groupingBy { it }
        .eachCount()
        .entries
        .map { (slug, count) -> IngredientUsage(ingredientName(slug), count) }
        .maxWithOrNull(
            compareBy<IngredientUsage> { it.mealCount }
                .thenByDescending { it.displayName },
        )

    val topByMember = byAuthor.entries
        .mapNotNull { (id, list) ->
            val top = list
                .flatMap { it.meal.mergedIngredientSlugs() }
                .groupingBy { it }
                .eachCount()
                .entries
                .maxWithOrNull(
                    compareBy<Map.Entry<IngredientSlug, Int>> { it.value }
                        .thenByDescending { ingredientName(it.key) },
                ) ?: return@mapNotNull null
            val a = list.first().meal.author
            MemberIngredient(id, a.displayName, a.avatarUrl, ingredientName(top.key), top.value)
        }
        .sortedWith(
            compareByDescending<MemberIngredient> { it.mealCount }
                .thenBy { it.ingredientName },
        )

    return WindowStats(
        window = window,
        totalMeals = total,
        avgPerDay = avg,
        bestMeal = best,
        mostVotedMeal = mostVoted,
        mostProlific = mostProlific,
        bestCook = bestCook,
        mostCriticized = mostCriticized,
        mostUsedIngredient = mostUsedIngredient,
        topByMember = topByMember,
    )
}

private fun MealWithRatings.toAward() = MealAward(
    mealId = meal.id,
    photoUrl = meal.photoUrl,
    dish = meal.dish,
    author = meal.author,
    score = averageScore ?: 0.0,
    ratingCount = ratings.size,
    publishedAt = meal.publishedAt,
    day = meal.day,
)
