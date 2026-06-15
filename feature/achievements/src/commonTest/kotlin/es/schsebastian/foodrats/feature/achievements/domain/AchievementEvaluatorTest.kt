package es.schsebastian.foodrats.feature.achievements.domain

import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.meal.MealWithRatings
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.feature.achievements.domain.model.Achievement
import es.schsebastian.foodrats.feature.achievements.domain.model.AchievementCriterion
import es.schsebastian.foodrats.feature.achievements.domain.model.AchievementIcon
import es.schsebastian.foodrats.feature.achievements.domain.model.AchievementId
import es.schsebastian.foodrats.feature.achievements.domain.model.AchievementSignals
import es.schsebastian.foodrats.feature.achievements.domain.model.AchievementStatus
import es.schsebastian.foodrats.feature.achievements.i18n.AchievementStringKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AchievementEvaluatorTest {

    private val evaluator = AchievementEvaluator()
    private val me = acct("me")

    private fun signals(
        meals: List<MealWithRatings> = emptyList(),
        personalStreakDays: Int = 0,
        crewStreakDays: Int = 0,
        bestCookAccountId: AccountId? = null,
        accountId: AccountId = me,
    ) = AchievementSignals(
        accountId = accountId,
        crewMeals = meals,
        personalStreakDays = personalStreakDays,
        crewStreakDays = crewStreakDays,
        bestCookAccountId = bestCookAccountId,
    )

    /** Evaluates a single-row catalog for [criterion] against [signals] and returns the status. */
    private fun statusOf(
        criterion: AchievementCriterion,
        signals: AchievementSignals,
    ): AchievementStatus {
        val row = Achievement(
            id = AchievementId("under_test"),
            titleKey = AchievementStringKey.FirstPlateTitle,
            descriptionKey = AchievementStringKey.FirstPlateDesc,
            iconKey = AchievementIcon.Plate,
            criterion = criterion,
        )
        return evaluator.evaluate(listOf(row), signals).single()
    }

    // ── FirstPlate ──

    @Test
    fun firstPlate_locked_with_no_plates() {
        val s = statusOf(AchievementCriterion.FirstPlate, signals(meals = emptyList()))
        assertEquals(0, s.progress.current)
        assertEquals(1, s.progress.target)
        assertFalse(s.progress.isMet)
    }

    @Test
    fun firstPlate_met_with_one_own_plate() {
        val s = statusOf(AchievementCriterion.FirstPlate, signals(meals = listOf(meal("m1", "me"))))
        assertTrue(s.progress.isMet)
    }

    @Test
    fun firstPlate_not_met_when_only_crewmate_plates() {
        val s = statusOf(AchievementCriterion.FirstPlate, signals(meals = listOf(meal("m1", "other"))))
        assertFalse(s.progress.isMet)
    }

    // ── MealCount ──

    @Test
    fun mealCount_below_threshold_is_locked_with_progress() {
        val meals = (1..9).map { meal("m$it", "me") }
        val s = statusOf(AchievementCriterion.MealCount(10), signals(meals = meals))
        assertEquals(9, s.progress.current)
        assertEquals(10, s.progress.target)
        assertFalse(s.progress.isMet)
    }

    @Test
    fun mealCount_at_threshold_is_met() {
        val meals = (1..10).map { meal("m$it", "me") }
        val s = statusOf(AchievementCriterion.MealCount(10), signals(meals = meals))
        assertTrue(s.progress.isMet)
    }

    @Test
    fun mealCount_counts_only_own_plates() {
        // 10 own + 50 crew-mate plates → a Personal criterion counts only the 10 own ones.
        val mine = (1..10).map { meal("mine$it", "me") }
        val theirs = (1..50).map { meal("theirs$it", "other") }
        val s = statusOf(AchievementCriterion.MealCount(50), signals(meals = mine + theirs))
        assertEquals(10, s.progress.current)
        assertFalse(s.progress.isMet)
    }

    // ── IngredientVariety ──

    @Test
    fun ingredientVariety_counts_distinct_confirmed_slugs() {
        val meals = listOf(
            meal("m1", "me", ingredients = listOf("egg", "milk", "egg")), // egg deduped
            meal("m2", "me", ingredients = listOf("flour")),
        )
        val s = statusOf(AchievementCriterion.IngredientVariety(3), signals(meals = meals))
        assertEquals(3, s.progress.current) // egg, milk, flour
        assertTrue(s.progress.isMet)
    }

    @Test
    fun ingredientVariety_ignores_detected_ingredients() {
        val meals = listOf(
            meal(
                "m1",
                "me",
                ingredients = listOf("egg"),
                detectedIngredients = listOf("milk", "flour", "sugar"), // AI advisory — excluded
            ),
        )
        val s = statusOf(AchievementCriterion.IngredientVariety(2), signals(meals = meals))
        assertEquals(1, s.progress.current) // only the confirmed "egg"
        assertFalse(s.progress.isMet)
    }

    // ── PersonalStreak / CrewStreak ──

    @Test
    fun personalStreak_met_exactly_at_threshold() {
        assertFalse(statusOf(AchievementCriterion.PersonalStreak(7), signals(personalStreakDays = 6)).progress.isMet)
        assertTrue(statusOf(AchievementCriterion.PersonalStreak(7), signals(personalStreakDays = 7)).progress.isMet)
    }

    @Test
    fun crewStreak_met_exactly_at_threshold() {
        assertFalse(statusOf(AchievementCriterion.CrewStreak(7), signals(crewStreakDays = 6)).progress.isMet)
        assertTrue(statusOf(AchievementCriterion.CrewStreak(7), signals(crewStreakDays = 7)).progress.isMet)
    }

    // ── EarlyBird / NightOwl ──

    @Test
    fun earlyBird_counts_only_breakfast_slots() {
        val meals = listOf(
            meal("b1", "me", slot = MealSlot.Breakfast),
            meal("b2", "me", slot = MealSlot.Breakfast),
            meal("l1", "me", slot = MealSlot.Lunch),
            meal("d1", "me", slot = MealSlot.Dinner),
        )
        val s = statusOf(AchievementCriterion.EarlyBird(2), signals(meals = meals))
        assertEquals(2, s.progress.current)
        assertTrue(s.progress.isMet)
    }

    @Test
    fun nightOwl_counts_only_dinner_slots() {
        val meals = listOf(
            meal("d1", "me", slot = MealSlot.Dinner),
            meal("b1", "me", slot = MealSlot.Breakfast),
        )
        val s = statusOf(AchievementCriterion.NightOwl(2), signals(meals = meals))
        assertEquals(1, s.progress.current)
        assertFalse(s.progress.isMet)
    }

    // ── BestCook ──

    @Test
    fun bestCook_met_when_account_leads() {
        assertTrue(statusOf(AchievementCriterion.BestCook, signals(bestCookAccountId = me)).progress.isMet)
    }

    @Test
    fun bestCook_locked_when_someone_else_leads() {
        val s = statusOf(AchievementCriterion.BestCook, signals(bestCookAccountId = acct("other")))
        assertFalse(s.progress.isMet)
    }

    @Test
    fun bestCook_locked_when_no_leader() {
        assertFalse(statusOf(AchievementCriterion.BestCook, signals(bestCookAccountId = null)).progress.isMet)
    }

    // ── CuisineVariety (forward-hook) ──

    @Test
    fun cuisineVariety_always_locked() {
        val meals = (1..200).map { meal("m$it", "me", ingredients = listOf("ing$it")) }
        val s = statusOf(AchievementCriterion.CuisineVariety(1), signals(meals = meals))
        assertEquals(0, s.progress.current)
        assertFalse(s.progress.isMet)
    }

    // ── evaluate() over the full catalog ──

    @Test
    fun evaluate_returns_one_status_per_catalog_row_all_locked_by_default() {
        val statuses = evaluator.evaluate(AchievementCatalog.all, signals())
        assertEquals(AchievementCatalog.all.size, statuses.size)
        // Pure evaluation never sets unlock timestamps — the caller overlays them.
        assertTrue(statuses.all { it.unlockedAtEpochMs == null })
    }
}
