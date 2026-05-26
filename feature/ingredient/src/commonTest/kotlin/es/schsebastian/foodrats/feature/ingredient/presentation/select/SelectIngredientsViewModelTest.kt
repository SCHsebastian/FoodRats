package es.schsebastian.foodrats.feature.ingredient.presentation.select

import app.cash.turbine.test
import es.schsebastian.foodrats.core.domain.meal.DraftIngredients
import es.schsebastian.foodrats.core.domain.meal.Ingredient
import es.schsebastian.foodrats.core.domain.meal.IngredientCategory
import es.schsebastian.foodrats.core.domain.meal.IngredientReadPort
import es.schsebastian.foodrats.core.domain.meal.IngredientSlug
import es.schsebastian.foodrats.core.domain.meal.MealDraftIngredientsPort
import es.schsebastian.foodrats.feature.ingredient.domain.usecase.ObserveCatalogUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SelectIngredientsViewModelTest {

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test fun cap_blocks_31st_selection() = runTest {
        val vm = vmWith((1..40).map { veg("ing_$it") })
        repeat(30) { i -> vm.onIntent(SelectIngredientsIntent.Toggle(IngredientSlug("ing_${i + 1}"))) }
        vm.onIntent(SelectIngredientsIntent.Toggle(IngredientSlug("ing_31")))
        vm.state.test {
            val st = expectMostRecentItem()
            assertEquals(30, st.selected.size)
            assertTrue(st.capReached)
            assertFalse(IngredientSlug("ing_31") in st.selected)
        }
    }

    @Test fun toggle_off_works_at_cap() = runTest {
        val vm = vmWith((1..40).map { veg("ing_$it") })
        repeat(30) { i -> vm.onIntent(SelectIngredientsIntent.Toggle(IngredientSlug("ing_${i + 1}"))) }
        vm.onIntent(SelectIngredientsIntent.Toggle(IngredientSlug("ing_1")))
        vm.state.test {
            assertEquals(29, expectMostRecentItem().selected.size)
        }
    }

    @Test fun confirm_writes_selected_to_port_and_navigates_back() = runTest {
        val port = FakeDraftPort()
        val vm = SelectIngredientsViewModel(catalogUseCase((1..3).map { veg("ing_$it") }), port)
        vm.onIntent(SelectIngredientsIntent.Toggle(IngredientSlug("ing_1")))
        vm.onIntent(SelectIngredientsIntent.Toggle(IngredientSlug("ing_2")))
        vm.effects.test {
            vm.onIntent(SelectIngredientsIntent.ConfirmAndExit)
            assertEquals(SelectIngredientsEffect.NavigateBack, awaitItem())
        }
        assertEquals(listOf(IngredientSlug("ing_1"), IngredientSlug("ing_2")), port.saved)
    }

    @Test fun loads_detected_and_selected_from_port() = runTest {
        val port = FakeDraftPort(
            DraftIngredients(
                selected = listOf(IngredientSlug("ing_1")),
                detected = listOf(IngredientSlug("ing_1"), IngredientSlug("ing_2")),
            ),
        )
        val vm = SelectIngredientsViewModel(catalogUseCase((1..3).map { veg("ing_$it") }), port)
        vm.state.test {
            val st = expectMostRecentItem()
            assertEquals(setOf(IngredientSlug("ing_1")), st.selected)
            assertEquals(setOf(IngredientSlug("ing_1"), IngredientSlug("ing_2")), st.detected)
            assertFalse(st.loading)
        }
    }

    private fun veg(slug: String) = Ingredient(IngredientSlug(slug), slug, IngredientCategory.Vegetable)

    private fun catalogUseCase(catalog: List<Ingredient>) = ObserveCatalogUseCase(
        object : IngredientReadPort {
            override fun observeCatalog() = MutableStateFlow(catalog.associateBy { it.slug })
            override suspend fun findBySlugs(slugs: Set<IngredientSlug>) = catalog.filter { it.slug in slugs }
            override suspend fun suggestForDish(dishSlug: String) = emptyList<IngredientSlug>()
        },
    )

    private fun vmWith(catalog: List<Ingredient>) =
        SelectIngredientsViewModel(catalogUseCase(catalog), FakeDraftPort())

    private class FakeDraftPort(
        private val initial: DraftIngredients? = DraftIngredients(emptyList(), emptyList()),
    ) : MealDraftIngredientsPort {
        var saved: List<IngredientSlug> = emptyList()
        override fun observeDraftIngredients(): Flow<DraftIngredients?> = MutableStateFlow(initial)
        override suspend fun setIngredients(slugs: List<IngredientSlug>) { saved = slugs }
    }
}
