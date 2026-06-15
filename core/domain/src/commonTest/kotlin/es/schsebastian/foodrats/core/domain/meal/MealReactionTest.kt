package es.schsebastian.foodrats.core.domain.meal

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class MealReactionTest {

    private val crew = CrewId("crew-1")
    private val meal = MealId.forDaySlot(
        crewId = crew,
        authorId = AccountId("author-1"),
        day = MealDay(LocalDate(2026, 5, 18), TimeZone.UTC),
        slot = MealSlot.Lunch,
    )
    private val alice = AccountId("alice")
    private val bob = AccountId("bob")
    private val t0 = Instant.fromEpochSeconds(1_700_000_000)

    private fun reaction(reactor: AccountId) = MealReaction(
        mealId = meal,
        crewId = crew,
        reactorId = reactor,
        kind = ReactionKind.DailyGlyph,
        reactedAt = t0,
    )

    @Test
    fun empty_reactions_has_no_count_and_nobody_reacted() {
        val reactions = MealReactions.empty(meal)
        assertEquals(0, reactions.count)
        assertFalse(reactions.hasReacted(alice))
        assertNull(reactions.reactionBy(alice))
    }

    @Test
    fun count_reflects_number_of_reactors() {
        val reactions = MealReactions(meal, listOf(reaction(alice), reaction(bob)))
        assertEquals(2, reactions.count)
    }

    @Test
    fun reactionBy_returns_only_that_members_reaction() {
        val reactions = MealReactions(meal, listOf(reaction(alice), reaction(bob)))
        assertEquals(alice, reactions.reactionBy(alice)?.reactorId)
        assertTrue(reactions.hasReacted(alice))
        assertTrue(reactions.hasReacted(bob))
    }

    @Test
    fun hasReacted_is_false_for_a_member_who_did_not_react() {
        val reactions = MealReactions(meal, listOf(reaction(alice)))
        assertFalse(reactions.hasReacted(bob))
        assertNull(reactions.reactionBy(bob))
    }

    @Test
    fun one_per_member_view_picks_the_first_when_data_has_duplicates() {
        // The adapter enforces uniqueness by uid; the read model defends the invariant
        // by surfacing a single reaction per member even if duplicates ever leak through.
        val first = reaction(alice).copy(reactedAt = t0)
        val second = reaction(alice).copy(reactedAt = Instant.fromEpochSeconds(1_700_000_500))
        val reactions = MealReactions(meal, listOf(first, second))
        assertEquals(t0, reactions.reactionBy(alice)?.reactedAt)
    }
}
