package es.schsebastian.foodrats.feature.ingredient.presentation.select

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import es.schsebastian.foodrats.core.domain.meal.DraftIngredients
import es.schsebastian.foodrats.core.domain.meal.Ingredient
import es.schsebastian.foodrats.core.domain.meal.IngredientCategory
import es.schsebastian.foodrats.core.domain.meal.IngredientReadPort
import es.schsebastian.foodrats.core.domain.meal.IngredientSlug
import es.schsebastian.foodrats.core.domain.result.getOrNull
import es.schsebastian.foodrats.core.domain.meal.MealDraftIngredientsPort
import es.schsebastian.foodrats.feature.ingredient.domain.usecase.ObserveCatalogUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SelectIngredientsScreenTest {

    @get:Rule
    val rule = createComposeRule()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val tomato = Ingredient(IngredientSlug.of("tomato").getOrNull()!!, "Tomato", IngredientCategory.Vegetable)
    private val onion = Ingredient(IngredientSlug.of("onion").getOrNull()!!, "Onion", IngredientCategory.Vegetable)

    @Test
    fun renders_default_expanded_vegetables_and_confirm_writes_selection() {
        val port = FakeDraftPort(DraftIngredients(selected = listOf(tomato.slug), detected = emptyList()))
        val vm = SelectIngredientsViewModel(catalogUseCase(listOf(tomato, onion)), port)
        var doneCalled = false

        rule.setContent {
            FoodRatsTheme { SelectIngredientsScreen(onDone = { doneCalled = true }, vm = vm) }
        }

        // Vegetable category is expanded by default → its rows render.
        rule.onNodeWithText("Tomato").assertExists()
        rule.onNodeWithText("Onion").assertExists()

        // "Done" (en default locale) confirms: writes selection through the port + navigates back.
        rule.onNodeWithText("Done").performClick()
        assertTrue(doneCalled)
        assertEquals(listOf(tomato.slug), port.saved)
    }

    private fun catalogUseCase(catalog: List<Ingredient>) = ObserveCatalogUseCase(
        object : IngredientReadPort {
            override fun observeCatalog() = MutableStateFlow(catalog.associateBy { it.slug })
            override suspend fun findBySlugs(slugs: Set<IngredientSlug>) = catalog.filter { it.slug in slugs }
            override suspend fun suggestForDish(dishSlug: String) = emptyList<IngredientSlug>()
        },
    )

    private class FakeDraftPort(private val initial: DraftIngredients?) : MealDraftIngredientsPort {
        var saved: List<IngredientSlug> = emptyList()
        override fun observeDraftIngredients(): Flow<DraftIngredients?> = MutableStateFlow(initial)
        override suspend fun setIngredients(slugs: List<IngredientSlug>) { saved = slugs }
    }
}
