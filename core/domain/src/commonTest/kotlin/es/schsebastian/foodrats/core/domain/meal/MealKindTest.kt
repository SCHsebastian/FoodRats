package es.schsebastian.foodrats.core.domain.meal

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class MealKindTest {
    private val authorId = (AccountId.of("u-author") as Result.Ok).value

    private val author = MealAuthor(
        accountId = authorId,
        displayName = "Author",
        avatarUrl = null,
    )

    private fun meal(kind: MealKind? = null): Meal = Meal(
        id = (MealId.of("m1") as Result.Ok).value,
        author = author,
        crewId = (CrewId.of("c1") as Result.Ok).value,
        day = MealDay(LocalDate.parse("2026-06-14"), TimeZone.UTC),
        slot = MealSlot.Lunch,
        photoUrl = "https://example.com/p.jpg",
        dish = (DishName.of("Pasta") as Result.Ok).value,
        description = Description.EMPTY,
        publishedAt = kotlin.time.Instant.parse("2026-06-14T12:00:00Z"),
    ).let { base -> if (kind == null) base else base.copy(kind = kind) }

    @Test fun meal_constructed_without_kind_defaults_to_solo() {
        // The seam is behaviorally inert: every existing construction site reads Solo for free.
        assertSame(MealKind.Solo, meal().kind)
    }

    @Test fun meal_kind_is_exhaustive_over_solo_today() {
        // Locks the one-leaf seam: this `when` is exhaustive WITHOUT an `else`. When the deferred
        // Together leaf is added (spec §5), this stops compiling until its arm is handled.
        val kind: MealKind = meal().kind
        val label = when (kind) {
            MealKind.Solo -> "solo"
        }
        assertEquals("solo", label)
    }

    @Test fun solo_meal_has_exactly_one_author_the_existing_author() {
        // §4.2 invariant, encoded as a pure function: a Solo meal's author set is exactly {author}.
        assertEquals(setOf(authorId), MealKind.Solo.authorIds(authorId))
    }
}
