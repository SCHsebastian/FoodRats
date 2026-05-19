package es.schsebastian.foodrats.core.domain.meal

import es.schsebastian.foodrats.core.domain.model.AccountId

data class MealWithRatings(
    val meal: Meal,
    val ratings: List<MealRating>,
) {
    val ratingCount: Int get() = ratings.size

    val averageScore: Double? get() =
        if (ratings.isEmpty()) null
        else ratings.map { it.score.value }.average()

    fun ratingBy(accountId: AccountId): MealRating? =
        ratings.firstOrNull { it.raterId == accountId }
}
