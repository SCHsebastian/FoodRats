package es.schsebastian.foodrats.feature.feed.presentation.detail

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import es.schsebastian.foodrats.core.domain.meal.Description
import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.meal.Meal
import es.schsebastian.foodrats.core.domain.meal.MealAuthor
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealPlate
import es.schsebastian.foodrats.core.domain.meal.MealWithRatings
import es.schsebastian.foodrats.core.domain.meal.PlateSource
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.feed.presentation.components.toFeedUi
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Instant

/**
 * multi-photo-crew15: locks the meal-detail photo pager end-to-end from a real [Meal] through
 * [toFeedUi] into [MealDetailBody] — a 3-photo meal renders the "Photo 1 of 3" pager
 * content-description + one [es.schsebastian.foodrats.core.designsystem.structural.FrPagerDots]
 * dot per page; a legacy/single-photo meal renders neither (page 1 stays pixel/semantics
 * identical to the pre-multi-photo header). Drives [MealDetailBody] directly with a hand-built
 * [MealDetailState] — [MealDetailViewModel]'s real port graph (comments, ingredients, delete/edit
 * use cases, story sharing, ...) is far larger than what this pager wiring needs, and
 * [MealDetailBody] itself takes no ports (state-in / intent-out only), which is why it was made
 * `internal` instead of `private` (see its kdoc).
 */
@RunWith(AndroidJUnit4::class)
class MealDetailScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val zone = TimeZone.UTC
    private val account = (AccountId.of("acc-1") as Result.Ok).value
    private val crew = (CrewId.of("crew-1") as Result.Ok).value
    private val day = MealDay(LocalDate(2026, 7, 13), zone)

    private fun baseMeal(plateSource: PlateSource, plates: List<MealPlate> = emptyList()) = Meal(
        id = (MealId.of("meal-1") as Result.Ok).value,
        author = MealAuthor(account, "Author", null),
        crewId = crew,
        day = day,
        slot = null,
        photoUrl = "https://example.test/p0.jpg",
        dish = (DishName.of("Pasta") as Result.Ok).value,
        description = Description.EMPTY,
        publishedAt = Instant.parse("2026-07-13T10:00:00Z"),
        plateSource = plateSource,
        plates = plates,
    )

    private fun renderDetail(meal: Meal) {
        val feedUi = MealWithRatings(meal, emptyList()).toFeedUi(account, day)
        rule.setContent {
            FoodRatsTheme {
                MealDetailBody(
                    state = MealDetailState(meal = feedUi, isLoading = false),
                    onIntent = {},
                    onBack = {},
                    onRequestDeleteMeal = {},
                )
            }
        }
        rule.waitForIdle()
    }

    @Test fun multi_photo_meal_shows_pager_cd_and_one_dot_per_page() {
        val meal = baseMeal(
            plateSource = PlateSource.Camera,
            plates = listOf(
                MealPlate(photoUrl = "https://example.test/p0.jpg", source = PlateSource.Camera),
                MealPlate(photoUrl = "https://example.test/p1.jpg", source = PlateSource.Camera),
                MealPlate(photoUrl = "https://example.test/p2.jpg", source = PlateSource.Camera),
            ),
        )
        renderDetail(meal)

        rule.onNodeWithContentDescription("Photo 1 of 3").assertExists()
        rule.onAllNodesWithTag("pagerDot").assertCountEquals(3)
    }

    @Test fun single_photo_meal_has_no_pager_cd_and_no_dots() {
        // Legacy shape: Meal.plates empty -> toFeedUi synthesizes exactly one page.
        val meal = baseMeal(plateSource = PlateSource.Camera)
        renderDetail(meal)

        rule.onNodeWithContentDescription("Photo 1 of 1").assertDoesNotExist()
        rule.onAllNodesWithTag("pagerDot").assertCountEquals(0)
    }

    @Test fun gallery_sourced_primary_photo_shows_gallery_chip_at_rest() {
        // plates[0] mirrors the meal-level plateSource by construction (Wave 1/2 invariant).
        val meal = baseMeal(
            plateSource = PlateSource.Gallery,
            plates = listOf(
                MealPlate(photoUrl = "https://example.test/p0.jpg", source = PlateSource.Gallery),
                MealPlate(photoUrl = "https://example.test/p1.jpg", source = PlateSource.Camera),
            ),
        )
        renderDetail(meal)

        rule.onNodeWithContentDescription("Photo from gallery").assertExists()
    }

    @Test fun camera_sourced_primary_photo_shows_no_gallery_chip_at_rest() {
        val meal = baseMeal(
            plateSource = PlateSource.Camera,
            plates = listOf(
                MealPlate(photoUrl = "https://example.test/p0.jpg", source = PlateSource.Camera),
                MealPlate(photoUrl = "https://example.test/p1.jpg", source = PlateSource.Gallery),
            ),
        )
        renderDetail(meal)

        // Page 0 (at rest) is camera-sourced even though page 1 is gallery-sourced — the
        // provenance chip must track the CURRENT page, not "any page in the meal".
        rule.onNodeWithContentDescription("Photo from gallery").assertDoesNotExist()
    }
}
