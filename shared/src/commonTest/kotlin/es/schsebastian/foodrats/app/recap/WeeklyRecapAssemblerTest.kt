package es.schsebastian.foodrats.app.recap

import es.schsebastian.foodrats.core.domain.meal.Description
import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.meal.Meal
import es.schsebastian.foodrats.core.domain.meal.MealAuthor
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealRating
import es.schsebastian.foodrats.core.domain.meal.MealWithRatings
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.getOrNull
import es.schsebastian.foodrats.feature.achievements.domain.AchievementCatalog
import es.schsebastian.foodrats.feature.achievements.domain.model.AchievementProgress
import es.schsebastian.foodrats.feature.achievements.domain.model.AchievementStatus
import es.schsebastian.foodrats.feature.achievements.domain.usecase.AchievementsSnapshot
import es.schsebastian.foodrats.feature.stats.domain.model.HeroStats
import es.schsebastian.foodrats.feature.stats.domain.model.MemberAverage
import es.schsebastian.foodrats.feature.stats.domain.model.MemberCount
import es.schsebastian.foodrats.feature.stats.domain.model.StatsSnapshot
import es.schsebastian.foodrats.feature.stats.domain.model.StatsWindow
import es.schsebastian.foodrats.feature.stats.domain.model.Streak
import es.schsebastian.foodrats.feature.stats.domain.model.Tab
import es.schsebastian.foodrats.feature.stats.domain.model.WindowStats
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class WeeklyRecapAssemblerTest {

    private val accountId: AccountId = AccountId.of("acc-1").getOrNull()!!
    private val crewId: CrewId = CrewId.of("crew-1").getOrNull()!!

    private val week = StatsWindow(
        tab = Tab.Week,
        from = LocalDate(2026, 6, 8),
        to = LocalDate(2026, 6, 14),
        days = 7,
    )

    private fun emptyWindow(
        bestCook: MemberAverage? = null,
        mostProlific: MemberCount? = null,
    ) = WindowStats(
        window = week,
        totalMeals = 0,
        avgPerDay = 0.0,
        bestMeal = null,
        mostVotedMeal = null,
        mostProlific = mostProlific,
        bestCook = bestCook,
        mostCriticized = null,
    )

    private fun statsWith(streakDays: Int, week: WindowStats = emptyWindow()) = StatsSnapshot(
        hero = HeroStats(
            personalStreak = Streak(streakDays),
            crewStreak = Streak(0),
            platesToday = 0,
            iPostedToday = false,
        ),
        week = week,
        month = emptyWindow(),
        historic = null,
        cuisinePassport = null,
        ingredientBingo = null,
    )

    /** A meal by [author] published at [publishedAt], carrying [photoUrl] and optional [ratings]. */
    private fun meal(
        id: String,
        author: AccountId,
        photoUrl: String,
        publishedAt: Instant,
        ratings: List<MealRating> = emptyList(),
    ): MealWithRatings {
        val m = Meal(
            id = MealId.of(id).getOrNull()!!,
            author = MealAuthor(author, "Chef $id", null),
            crewId = crewId,
            day = MealDay(LocalDate(2026, 6, 10), kotlinx.datetime.TimeZone.UTC),
            slot = null,
            photoUrl = photoUrl,
            dish = DishName.of("Dish $id").getOrNull()!!,
            description = Description.EMPTY,
            publishedAt = publishedAt,
        )
        return MealWithRatings(m, ratings)
    }

    private fun rating(score: Int) = MealRating(
        raterId = AccountId.of("rater").getOrNull()!!,
        raterDisplayName = "Rater",
        raterAvatarUrl = null,
        score = Score.of(score).getOrNull()!!,
        ratedAt = Instant.parse("2026-06-10T12:00:00Z"),
    )

    @Test
    fun cover_and_your_week_are_always_present() {
        val recap = assembleWeeklyRecap(
            stats = statsWith(streakDays = 0),
            achievements = null,
            weekLabel = "2026-06-08",
            weekWindowStartEpochMs = 0,
            weekWindowEndEpochMs = 0,
        )
        // With no awards, no streak, no badges, no cuisines → just cover + your-week.
        assertEquals(
            listOf(RecapSceneKind.Cover, RecapSceneKind.YourWeek),
            recap.scenes.map { it.kind },
        )
        assertTrue(recap.scenes.first() is RecapScene.Cover)
        assertTrue(recap.scenes.last() is RecapScene.YourWeek)
    }

    @Test
    fun a_positive_streak_adds_a_streak_scene_in_order() {
        val recap = assembleWeeklyRecap(
            stats = statsWith(streakDays = 5),
            achievements = null,
            weekLabel = "2026-06-08",
            weekWindowStartEpochMs = 0,
            weekWindowEndEpochMs = 0,
        )
        assertEquals(
            listOf(RecapSceneKind.Cover, RecapSceneKind.Streak, RecapSceneKind.YourWeek),
            recap.scenes.map { it.kind },
        )
        val streak = recap.scenes.filterIsInstance<RecapScene.Streak>().single()
        assertEquals(5, streak.streakDays)
    }

    @Test
    fun only_badges_unlocked_inside_the_week_window_appear() {
        val inWeek = AchievementStatus(
            achievement = AchievementCatalog.all[0],
            progress = AchievementProgress(current = 1, target = 1),
            unlockedAtEpochMs = 1_500L, // inside [1000, 2000]
        )
        val outOfWeek = AchievementStatus(
            achievement = AchievementCatalog.all[1],
            progress = AchievementProgress(current = 1, target = 1),
            unlockedAtEpochMs = 500L, // before the window
        )
        val locked = AchievementStatus(
            achievement = AchievementCatalog.all[2],
            progress = AchievementProgress(current = 0, target = 1),
            unlockedAtEpochMs = null, // not unlocked
        )
        val recap = assembleWeeklyRecap(
            stats = statsWith(streakDays = 0),
            achievements = AchievementsSnapshot(accountId, listOf(inWeek, outOfWeek, locked)),
            weekLabel = "2026-06-08",
            weekWindowStartEpochMs = 1_000L,
            weekWindowEndEpochMs = 2_000L,
        )
        val badges = recap.scenes.filterIsInstance<RecapScene.Badges>().single()
        assertEquals(listOf(inWeek.achievement.titleKey), badges.titleKeys)
        assertEquals(
            listOf(RecapSceneKind.Cover, RecapSceneKind.Badges, RecapSceneKind.YourWeek),
            recap.scenes.map { it.kind },
        )
    }

    @Test
    fun no_badge_scene_when_none_unlocked_in_window() {
        val locked = AchievementStatus(
            achievement = AchievementCatalog.all[0],
            progress = AchievementProgress(current = 0, target = 1),
            unlockedAtEpochMs = null,
        )
        val recap = assembleWeeklyRecap(
            stats = statsWith(streakDays = 0),
            achievements = AchievementsSnapshot(accountId, listOf(locked)),
            weekLabel = "2026-06-08",
            weekWindowStartEpochMs = 1_000L,
            weekWindowEndEpochMs = 2_000L,
        )
        assertTrue(recap.scenes.none { it is RecapScene.Badges })
    }

    // ───────────────────────────── TRACK B: photo floors ─────────────────────────────

    @Test
    fun no_week_meals_means_every_scene_has_a_null_photo() {
        val recap = assembleWeeklyRecap(
            stats = statsWith(streakDays = 3),
            achievements = null,
            weekLabel = "2026-06-08",
            weekWindowStartEpochMs = 0,
            weekWindowEndEpochMs = 0,
            weekMeals = emptyList(),
            myAccountId = accountId,
        )
        assertNull((recap.scenes.first() as RecapScene.Cover).photoUrl)
        assertNull((recap.scenes.filterIsInstance<RecapScene.Streak>().single()).photoUrl)
        assertNull((recap.scenes.last() as RecapScene.YourWeek).photoUrl)
    }

    @Test
    fun cover_prefers_the_weeks_best_meal_photo_else_the_crew_newest() {
        val newer = meal("m2", accountId, "https://p/newest.jpg", Instant.parse("2026-06-10T12:00:00Z"))
        val older = meal("m1", accountId, "https://p/older.jpg", Instant.parse("2026-06-09T12:00:00Z"))
        val recap = assembleWeeklyRecap(
            stats = statsWith(streakDays = 0),
            achievements = null,
            weekLabel = "2026-06-08",
            weekWindowStartEpochMs = 0,
            weekWindowEndEpochMs = 0,
            weekMeals = listOf(older, newer),
            myAccountId = null,
        )
        val cover = recap.scenes.first() as RecapScene.Cover
        assertEquals("https://p/newest.jpg", cover.photoUrl)
    }

    @Test
    fun best_cook_photo_is_that_cooks_highest_scored_meal_not_just_their_newest() {
        val cookId = AccountId.of("cook").getOrNull()!!
        val lowScoreNewer = meal(
            "m2", cookId, "https://p/low.jpg", Instant.parse("2026-06-10T12:00:00Z"),
            ratings = listOf(rating(2)),
        )
        val highScoreOlder = meal(
            "m1", cookId, "https://p/high.jpg", Instant.parse("2026-06-09T12:00:00Z"),
            ratings = listOf(rating(5)),
        )
        val recap = assembleWeeklyRecap(
            stats = statsWith(
                streakDays = 0,
                week = emptyWindow(bestCook = MemberAverage(cookId, "Cook", null, 5.0, 2)),
            ),
            achievements = null,
            weekLabel = "2026-06-08",
            weekWindowStartEpochMs = 0,
            weekWindowEndEpochMs = 0,
            weekMeals = listOf(lowScoreNewer, highScoreOlder),
            myAccountId = null,
        )
        val bestCook = recap.scenes.filterIsInstance<RecapScene.BestCook>().single()
        assertEquals("https://p/high.jpg", bestCook.photoUrl)
    }

    @Test
    fun most_prolific_photo_is_that_members_newest_meal() {
        val prolificId = AccountId.of("prolific").getOrNull()!!
        val older = meal("m1", prolificId, "https://p/older.jpg", Instant.parse("2026-06-09T12:00:00Z"))
        val newer = meal("m2", prolificId, "https://p/newer.jpg", Instant.parse("2026-06-10T12:00:00Z"))
        val recap = assembleWeeklyRecap(
            stats = statsWith(
                streakDays = 0,
                week = emptyWindow(mostProlific = MemberCount(prolificId, "Prolific", null, 2)),
            ),
            achievements = null,
            weekLabel = "2026-06-08",
            weekWindowStartEpochMs = 0,
            weekWindowEndEpochMs = 0,
            weekMeals = listOf(older, newer),
            myAccountId = null,
        )
        val prolific = recap.scenes.filterIsInstance<RecapScene.MostProlific>().single()
        assertEquals("https://p/newer.jpg", prolific.photoUrl)
    }

    @Test
    fun streak_and_your_week_prefer_the_signed_in_members_own_newest_plate_over_the_crews() {
        val other = AccountId.of("other").getOrNull()!!
        val crewNewer = meal("m2", other, "https://p/crew.jpg", Instant.parse("2026-06-11T12:00:00Z"))
        val myOwn = meal("m1", accountId, "https://p/mine.jpg", Instant.parse("2026-06-09T12:00:00Z"))
        val recap = assembleWeeklyRecap(
            stats = statsWith(streakDays = 4),
            achievements = null,
            weekLabel = "2026-06-08",
            weekWindowStartEpochMs = 0,
            weekWindowEndEpochMs = 0,
            weekMeals = listOf(myOwn, crewNewer),
            myAccountId = accountId,
        )
        val streak = recap.scenes.filterIsInstance<RecapScene.Streak>().single()
        assertEquals("https://p/mine.jpg", streak.photoUrl)
        val yourWeek = recap.scenes.last() as RecapScene.YourWeek
        assertEquals("https://p/mine.jpg", yourWeek.photoUrl)
    }

    @Test
    fun streak_falls_back_to_the_crews_newest_plate_when_the_member_has_none_of_their_own() {
        val other = AccountId.of("other").getOrNull()!!
        val crewMeal = meal("m1", other, "https://p/crew.jpg", Instant.parse("2026-06-11T12:00:00Z"))
        val recap = assembleWeeklyRecap(
            stats = statsWith(streakDays = 2),
            achievements = null,
            weekLabel = "2026-06-08",
            weekWindowStartEpochMs = 0,
            weekWindowEndEpochMs = 0,
            weekMeals = listOf(crewMeal),
            myAccountId = accountId,
        )
        val streak = recap.scenes.filterIsInstance<RecapScene.Streak>().single()
        assertEquals("https://p/crew.jpg", streak.photoUrl)
    }
}
