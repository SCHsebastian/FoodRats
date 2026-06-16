package es.schsebastian.foodrats.feature.feed.presentation.components

import es.schsebastian.foodrats.core.domain.meal.Description
import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.meal.Meal
import es.schsebastian.foodrats.core.domain.meal.MealAuthor
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealRating
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.meal.MealWithRatings
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class FeedMealUiTest {
    private val zone = TimeZone.UTC
    private val today = MealDay(LocalDate.parse("2026-05-19"), zone)
    private val mealDay = MealDay(LocalDate.parse("2026-05-19"), zone)

    private val authorId = (AccountId.of("u-author") as Result.Ok).value
    private val viewerId = (AccountId.of("u-viewer") as Result.Ok).value

    private val sampleMeal = Meal(
        id = (MealId.of("m1") as Result.Ok).value,
        author = MealAuthor(authorId, "Author", null),
        crewId = (CrewId.of("c1") as Result.Ok).value,
        day = mealDay,
        slot = MealSlot.Lunch,
        photoUrl = "https://example.com/p.jpg",
        dish = (DishName.of("Pasta") as Result.Ok).value,
        description = Description.EMPTY,
        publishedAt = Instant.parse("2026-05-19T12:00:00Z"),
    )

    private fun rating(rater: AccountId, score: Int) = MealRating(
        raterId = rater,
        raterDisplayName = "Rater",
        raterAvatarUrl = null,
        score = (Score.of(score) as Result.Ok).value,
        ratedAt = Instant.parse("2026-05-19T13:00:00Z"),
    )

    @Test fun viewer_is_author_cannot_rate() {
        val ui = MealWithRatings(sampleMeal, emptyList()).toFeedUi(authorId, today)
        assertFalse(ui.canRate)
        assertNull(ui.viewerRating)
    }

    @Test fun viewer_already_rated_cannot_rate_again() {
        val ui = MealWithRatings(sampleMeal, listOf(rating(viewerId, 4))).toFeedUi(viewerId, today)
        assertFalse(ui.canRate)
        assertEquals(4, ui.viewerRating)
    }

    @Test fun viewer_can_rate_when_open() {
        val ui = MealWithRatings(sampleMeal, emptyList()).toFeedUi(viewerId, today)
        assertTrue(ui.canRate)
    }

    @Test fun window_closed_two_days_later() {
        val twoLater = MealDay(LocalDate.parse("2026-05-21"), zone)
        val ui = MealWithRatings(sampleMeal, emptyList()).toFeedUi(viewerId, twoLater)
        assertFalse(ui.canRate)
    }

    @Test fun average_null_when_no_votes() {
        val ui = MealWithRatings(sampleMeal, emptyList()).toFeedUi(viewerId, today)
        assertNull(ui.averageScore)
        assertEquals(0, ui.ratingCount)
    }

    @Test fun description_flows_into_feed_ui() {
        val withDesc = sampleMeal.copy(
            description = (Description.of("Tortilla, just a bit runny") as Result.Ok).value,
        )
        val ui = MealWithRatings(withDesc, emptyList()).toFeedUi(viewerId, today)
        assertEquals("Tortilla, just a bit runny", ui.description)
    }

    @Test fun blind_voting_off_author_never_masked() {
        val ui = MealWithRatings(sampleMeal, emptyList())
            .toFeedUi(viewerId, today, blindVoting = false)
        assertFalse(ui.authorMasked)
    }

    @Test fun blind_voting_on_not_voted_not_author_masked() {
        val ui = MealWithRatings(sampleMeal, emptyList())
            .toFeedUi(viewerId, today, blindVoting = true)
        assertTrue(ui.authorMasked)
    }

    @Test fun blind_voting_on_after_voting_revealed() {
        val ui = MealWithRatings(sampleMeal, listOf(rating(viewerId, 5)))
            .toFeedUi(viewerId, today, blindVoting = true)
        assertFalse(ui.authorMasked)
    }

    @Test fun blind_voting_on_own_meal_revealed() {
        val ui = MealWithRatings(sampleMeal, emptyList())
            .toFeedUi(authorId, today, blindVoting = true)
        assertFalse(ui.authorMasked)
    }

    @Test fun blind_voting_on_window_closed_revealed() {
        val twoLater = MealDay(LocalDate.parse("2026-05-21"), zone)
        val ui = MealWithRatings(sampleMeal, emptyList())
            .toFeedUi(viewerId, twoLater, blindVoting = true)
        assertFalse(ui.authorMasked)
    }

    @Test fun reactions_default_to_empty() {
        val ui = MealWithRatings(sampleMeal, emptyList()).toFeedUi(viewerId, today)
        assertEquals(0, ui.reactionCount)
        assertFalse(ui.viewerReacted)
    }

    @Test fun day_emote_is_the_reaction_glyph() {
        val ui = MealWithRatings(sampleMeal, emptyList()).toFeedUi(viewerId, today)
        // The react affordance renders ui.dayEmote (the meal-day's DailyEmote) — deterministic.
        assertEquals(
            es.schsebastian.foodrats.core.domain.meal.DailyEmote.forDay(mealDay),
            ui.dayEmote,
        )
    }

    @Test fun with_reactions_merges_count_and_viewer_flag() {
        val ui = MealWithRatings(sampleMeal, emptyList()).toFeedUi(viewerId, today)
            .withReactions(count = 3, viewerReacted = true)
        assertEquals(3, ui.reactionCount)
        assertTrue(ui.viewerReacted)
    }

    @Test fun thumbnail_and_thumbhash_flow_into_feed_ui() {
        val withThumb = sampleMeal.copy(
            thumbnailUrl = "https://example.com/p_thumb.jpg",
            thumbHash = "1QcSHQRnh493V4dIh4eXh1h4kJUI",
        )
        val ui = MealWithRatings(withThumb, emptyList()).toFeedUi(viewerId, today)
        assertEquals("https://example.com/p_thumb.jpg", ui.thumbnailUrl)
        assertEquals("1QcSHQRnh493V4dIh4eXh1h4kJUI", ui.thumbHash)
        assertEquals("https://example.com/p.jpg", ui.photoUrl)
    }

    @Test fun feed_image_url_prefers_thumbnail_when_present() {
        val withThumb = sampleMeal.copy(thumbnailUrl = "https://example.com/p_thumb.jpg")
        val ui = MealWithRatings(withThumb, emptyList()).toFeedUi(viewerId, today)
        // Feed cards load the small/fast thumbnail; detail still loads photoUrl (full).
        assertEquals("https://example.com/p_thumb.jpg", ui.feedImageUrl)
        assertEquals("https://example.com/p.jpg", ui.photoUrl)
    }

    @Test fun feed_image_url_falls_back_to_full_when_no_thumbnail() {
        // Pre-pipeline meal (or the few-seconds window before the thumbnail exists).
        val ui = MealWithRatings(sampleMeal, emptyList()).toFeedUi(viewerId, today)
        assertEquals("", ui.thumbnailUrl)
        assertNull(ui.thumbHash)
        assertEquals("https://example.com/p.jpg", ui.feedImageUrl)
    }

    @Test fun average_computed_from_ratings() {
        val ui = MealWithRatings(
            sampleMeal,
            listOf(rating(viewerId, 2), rating((AccountId.of("u-x") as Result.Ok).value, 4)),
        ).toFeedUi((AccountId.of("u-y") as Result.Ok).value, today)
        assertEquals(3.0, ui.averageScore!!, 1e-9)
        assertEquals(2, ui.ratingCount)
    }
}
