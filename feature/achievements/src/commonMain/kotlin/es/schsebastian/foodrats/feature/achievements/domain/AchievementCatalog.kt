package es.schsebastian.foodrats.feature.achievements.domain

import es.schsebastian.foodrats.feature.achievements.domain.model.Achievement
import es.schsebastian.foodrats.feature.achievements.domain.model.AchievementCriterion
import es.schsebastian.foodrats.feature.achievements.domain.model.AchievementIcon
import es.schsebastian.foodrats.feature.achievements.domain.model.AchievementId
import es.schsebastian.foodrats.feature.achievements.domain.model.AchievementTier
import es.schsebastian.foodrats.feature.achievements.i18n.AchievementStringKey

/**
 * The compile-time achievement catalog — fifteen rows (spec §9). Not server data.
 *
 * `CuisineVariety` is intentionally **not** shipped here; it is a declared criterion leaf only,
 * added to the catalog by the cuisine-passport spec (spec §15).
 */
object AchievementCatalog {
    val all: List<Achievement> = listOf(
        Achievement(
            id = AchievementId("first_plate"),
            titleKey = AchievementStringKey.FirstPlateTitle,
            descriptionKey = AchievementStringKey.FirstPlateDesc,
            iconKey = AchievementIcon.FirstPlate,
            criterion = AchievementCriterion.FirstPlate,
        ),
        Achievement(
            id = AchievementId("meals_10"),
            titleKey = AchievementStringKey.Meals10Title,
            descriptionKey = AchievementStringKey.Meals10Desc,
            iconKey = AchievementIcon.Plate,
            criterion = AchievementCriterion.MealCount(10),
            tier = AchievementTier.Bronze,
        ),
        Achievement(
            id = AchievementId("meals_50"),
            titleKey = AchievementStringKey.Meals50Title,
            descriptionKey = AchievementStringKey.Meals50Desc,
            iconKey = AchievementIcon.Plate,
            criterion = AchievementCriterion.MealCount(50),
            tier = AchievementTier.Silver,
        ),
        Achievement(
            id = AchievementId("meals_100"),
            titleKey = AchievementStringKey.Meals100Title,
            descriptionKey = AchievementStringKey.Meals100Desc,
            iconKey = AchievementIcon.Plate,
            criterion = AchievementCriterion.MealCount(100),
            tier = AchievementTier.Gold,
        ),
        Achievement(
            id = AchievementId("ingredients_25"),
            titleKey = AchievementStringKey.Ingredients25Title,
            descriptionKey = AchievementStringKey.Ingredients25Desc,
            iconKey = AchievementIcon.Ingredients,
            criterion = AchievementCriterion.IngredientVariety(25),
            tier = AchievementTier.Bronze,
        ),
        Achievement(
            id = AchievementId("ingredients_50"),
            titleKey = AchievementStringKey.Ingredients50Title,
            descriptionKey = AchievementStringKey.Ingredients50Desc,
            iconKey = AchievementIcon.Ingredients,
            criterion = AchievementCriterion.IngredientVariety(50),
            tier = AchievementTier.Silver,
        ),
        Achievement(
            id = AchievementId("ingredients_100"),
            titleKey = AchievementStringKey.Ingredients100Title,
            descriptionKey = AchievementStringKey.Ingredients100Desc,
            iconKey = AchievementIcon.Ingredients,
            criterion = AchievementCriterion.IngredientVariety(100),
            tier = AchievementTier.Gold,
        ),
        Achievement(
            id = AchievementId("streak_personal_7"),
            titleKey = AchievementStringKey.StreakPersonal7Title,
            descriptionKey = AchievementStringKey.StreakPersonal7Desc,
            iconKey = AchievementIcon.Streak,
            criterion = AchievementCriterion.PersonalStreak(7),
            tier = AchievementTier.Bronze,
        ),
        Achievement(
            id = AchievementId("streak_personal_30"),
            titleKey = AchievementStringKey.StreakPersonal30Title,
            descriptionKey = AchievementStringKey.StreakPersonal30Desc,
            iconKey = AchievementIcon.Streak,
            criterion = AchievementCriterion.PersonalStreak(30),
            tier = AchievementTier.Silver,
        ),
        Achievement(
            id = AchievementId("streak_personal_100"),
            titleKey = AchievementStringKey.StreakPersonal100Title,
            descriptionKey = AchievementStringKey.StreakPersonal100Desc,
            iconKey = AchievementIcon.Streak,
            criterion = AchievementCriterion.PersonalStreak(100),
            tier = AchievementTier.Gold,
        ),
        Achievement(
            id = AchievementId("streak_crew_7"),
            titleKey = AchievementStringKey.StreakCrew7Title,
            descriptionKey = AchievementStringKey.StreakCrew7Desc,
            iconKey = AchievementIcon.CrewStreak,
            criterion = AchievementCriterion.CrewStreak(7),
            tier = AchievementTier.Bronze,
        ),
        Achievement(
            id = AchievementId("streak_crew_30"),
            titleKey = AchievementStringKey.StreakCrew30Title,
            descriptionKey = AchievementStringKey.StreakCrew30Desc,
            iconKey = AchievementIcon.CrewStreak,
            criterion = AchievementCriterion.CrewStreak(30),
            tier = AchievementTier.Silver,
        ),
        Achievement(
            id = AchievementId("best_cook"),
            titleKey = AchievementStringKey.BestCookTitle,
            descriptionKey = AchievementStringKey.BestCookDesc,
            iconKey = AchievementIcon.ChefHat,
            criterion = AchievementCriterion.BestCook,
        ),
        Achievement(
            id = AchievementId("early_bird_10"),
            titleKey = AchievementStringKey.EarlyBird10Title,
            descriptionKey = AchievementStringKey.EarlyBird10Desc,
            iconKey = AchievementIcon.Sunrise,
            criterion = AchievementCriterion.EarlyBird(10),
        ),
        Achievement(
            id = AchievementId("night_owl_10"),
            titleKey = AchievementStringKey.NightOwl10Title,
            descriptionKey = AchievementStringKey.NightOwl10Desc,
            iconKey = AchievementIcon.Moon,
            criterion = AchievementCriterion.NightOwl(10),
        ),
    )
}
