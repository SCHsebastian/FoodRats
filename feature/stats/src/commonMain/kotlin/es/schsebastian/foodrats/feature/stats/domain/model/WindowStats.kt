package es.schsebastian.foodrats.feature.stats.domain.model

data class WindowStats(
    val window: StatsWindow,
    val totalMeals: Int,
    val avgPerDay: Double,
    val bestMeal: MealAward?,
    val mostVotedMeal: MealAward?,
    val mostProlific: MemberCount?,
    val bestCook: MemberAverage?,
    val mostCriticized: MemberAverage?,
    val mostUsedIngredient: IngredientUsage? = null,
    val topByMember: List<MemberIngredient> = emptyList(),
    /** Meals-per-day across the window, oldest → newest with zero-filled gaps. Drives the trend sparkline. */
    val dailyMeals: List<Int> = emptyList(),
)
