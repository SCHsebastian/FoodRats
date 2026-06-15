package es.schsebastian.foodrats.feature.stats.presentation.components

import es.schsebastian.foodrats.core.domain.meal.DailyEmote
import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.meal.MealAuthor
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.stats.domain.model.HeroStats
import es.schsebastian.foodrats.feature.stats.domain.model.MealAward
import es.schsebastian.foodrats.feature.stats.domain.model.Streak
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** §12: `MealAward.toAwardCard()` and `HeroStats.toStreakCard()` map straight through. */
class StatsShareMappersTest {

    private val zone = TimeZone.UTC
    private val day = MealDay(LocalDate(2026, 5, 21), zone)
    private val chef = (AccountId.of("chef") as Result.Ok).value

    private fun award() = MealAward(
        mealId = (MealId.of("m1") as Result.Ok).value,
        photoUrl = "https://signed/plate.jpg",
        dish = (DishName.of("Lasagna") as Result.Ok).value,
        author = MealAuthor(chef, "Chef Ana", "https://a/avatar.png"),
        score = 8.4,
        ratingCount = 5,
        publishedAt = Instant.parse("2026-05-21T12:00:00Z"),
        day = day,
    )

    @Test fun toAwardCard_maps_dish_author_score_and_day_emote() {
        val model = award().toAwardCard()
        assertEquals("m1", model.mealId)
        assertEquals("https://signed/plate.jpg", model.photoUrl)
        assertEquals("Lasagna", model.dishName)
        assertEquals("Chef Ana", model.authorName)
        assertEquals(8.4, model.score)
        assertEquals(5, model.ratingCount)
        assertEquals(DailyEmote.forDay(day), model.dayEmote)
    }

    @Test fun toStreakCard_reads_personal_streak_days() {
        val hero = HeroStats(
            personalStreak = Streak(7),
            crewStreak = Streak(3),
            platesToday = 1,
            iPostedToday = true,
        )
        val model = hero.toStreakCard(todayEmote = "🔥")
        assertEquals(7, model.streakDays)
        assertEquals("🔥", model.dayEmote)
    }

    @Test fun toStreakCard_handles_zero_streak() {
        val hero = HeroStats(
            personalStreak = Streak(0),
            crewStreak = Streak(0),
            platesToday = 0,
            iPostedToday = false,
        )
        val model = hero.toStreakCard(todayEmote = "🍽️")
        assertEquals(0, model.streakDays)
    }
}
