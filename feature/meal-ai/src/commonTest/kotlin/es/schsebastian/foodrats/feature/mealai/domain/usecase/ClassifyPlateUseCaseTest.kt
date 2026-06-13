package es.schsebastian.foodrats.feature.mealai.domain.usecase

import es.schsebastian.foodrats.core.domain.meal.ClassifierError
import es.schsebastian.foodrats.core.domain.meal.DishLabel
import es.schsebastian.foodrats.core.domain.meal.Ingredient
import es.schsebastian.foodrats.core.domain.meal.IngredientReadPort
import es.schsebastian.foodrats.core.domain.meal.IngredientSlug
import es.schsebastian.foodrats.core.domain.result.getOrNull
import es.schsebastian.foodrats.core.domain.meal.MealClassifierPort
import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClassifyPlateUseCaseTest {
    @Test fun returns_slugs_for_top_dish_above_threshold() = runTest {
        val classifier = FakeClassifier(Result.Ok(listOf(DishLabel("lasagna", 0.85f))))
        val port = FakeReadPort(suggestions = mapOf("lasagna" to listOf(IngredientSlug.of("pasta").getOrNull()!!)))
        val result = ClassifyPlateUseCase(classifier, port)(ByteArray(0))
        assertTrue(result is Result.Ok)
        assertEquals(listOf(IngredientSlug.of("pasta").getOrNull()!!), result.value.slugs)
        assertEquals("lasagna", result.value.dishSlug)
    }

    @Test fun returns_low_confidence_error_below_threshold() = runTest {
        val classifier = FakeClassifier(Result.Ok(listOf(DishLabel("lasagna", 0.20f))))
        val port = FakeReadPort()
        val result = ClassifyPlateUseCase(classifier, port)(ByteArray(0))
        assertEquals(Result.Err(ClassifierError.Run.LowConfidence), result)
    }

    @Test fun returns_low_confidence_error_when_no_labels() = runTest {
        val classifier = FakeClassifier(Result.Ok(emptyList()))
        val port = FakeReadPort()
        val result = ClassifyPlateUseCase(classifier, port)(ByteArray(0))
        assertEquals(Result.Err(ClassifierError.Run.LowConfidence), result)
    }

    @Test fun returns_unmapped_when_dish_not_in_map() = runTest {
        val classifier = FakeClassifier(Result.Ok(listOf(DishLabel("ramen", 0.91f))))
        val port = FakeReadPort(suggestions = emptyMap())
        val result = ClassifyPlateUseCase(classifier, port)(ByteArray(0))
        assertEquals(Result.Err(ClassifierError.Run.DishUnmapped), result)
    }

    @Test fun propagates_inference_failed() = runTest {
        val classifier = FakeClassifier(Result.Err(ClassifierError.Run.InferenceFailed))
        val port = FakeReadPort()
        val result = ClassifyPlateUseCase(classifier, port)(ByteArray(0))
        assertEquals(Result.Err(ClassifierError.Run.InferenceFailed), result)
    }

    private class FakeClassifier(
        val response: Result<List<DishLabel>, ClassifierError>,
    ) : MealClassifierPort {
        override suspend fun classify(jpeg: ByteArray) = response
    }

    private class FakeReadPort(
        val suggestions: Map<String, List<IngredientSlug>> = emptyMap(),
    ) : IngredientReadPort {
        override fun observeCatalog() = flowOf(emptyMap<IngredientSlug, Ingredient>())
        override suspend fun findBySlugs(slugs: Set<IngredientSlug>) = emptyList<Ingredient>()
        override suspend fun suggestForDish(dishSlug: String) = suggestions[dishSlug] ?: emptyList()
    }
}
