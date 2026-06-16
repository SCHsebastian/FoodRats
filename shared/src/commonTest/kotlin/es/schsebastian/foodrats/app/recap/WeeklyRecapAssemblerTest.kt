package es.schsebastian.foodrats.app.recap

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.getOrNull
import es.schsebastian.foodrats.feature.achievements.domain.AchievementCatalog
import es.schsebastian.foodrats.feature.achievements.domain.model.AchievementProgress
import es.schsebastian.foodrats.feature.achievements.domain.model.AchievementStatus
import es.schsebastian.foodrats.feature.achievements.domain.usecase.AchievementsSnapshot
import es.schsebastian.foodrats.feature.stats.domain.model.HeroStats
import es.schsebastian.foodrats.feature.stats.domain.model.StatsSnapshot
import es.schsebastian.foodrats.feature.stats.domain.model.StatsWindow
import es.schsebastian.foodrats.feature.stats.domain.model.Streak
import es.schsebastian.foodrats.feature.stats.domain.model.Tab
import es.schsebastian.foodrats.feature.stats.domain.model.WindowStats
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeeklyRecapAssemblerTest {

    private val accountId: AccountId = AccountId.of("acc-1").getOrNull()!!

    private val week = StatsWindow(
        tab = Tab.Week,
        from = LocalDate(2026, 6, 8),
        to = LocalDate(2026, 6, 14),
        days = 7,
    )

    private fun emptyWindow() = WindowStats(
        window = week,
        totalMeals = 0,
        avgPerDay = 0.0,
        bestMeal = null,
        mostVotedMeal = null,
        mostProlific = null,
        bestCook = null,
        mostCriticized = null,
    )

    private fun statsWith(streakDays: Int) = StatsSnapshot(
        hero = HeroStats(
            personalStreak = Streak(streakDays),
            crewStreak = Streak(0),
            platesToday = 0,
            iPostedToday = false,
        ),
        week = emptyWindow(),
        month = emptyWindow(),
        historic = null,
        cuisinePassport = null,
        ingredientBingo = null,
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
}
