package es.schsebastian.foodrats.feature.meal.domain.usecase

import es.schsebastian.foodrats.core.domain.config.FeatureFlagPort
import es.schsebastian.foodrats.core.domain.meal.ClassifierError
import es.schsebastian.foodrats.core.domain.meal.DishLabel
import es.schsebastian.foodrats.core.domain.meal.Ingredient
import es.schsebastian.foodrats.core.domain.meal.IngredientReadPort
import es.schsebastian.foodrats.core.domain.meal.IngredientSlug
import es.schsebastian.foodrats.core.domain.meal.MealClassifierPort
import es.schsebastian.foodrats.core.domain.preferences.AiPreferenceError
import es.schsebastian.foodrats.core.domain.preferences.AiPreferencePort
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.result.getOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ClassifyDraftPlateUseCaseTest {

    private fun slug(value: String) = IngredientSlug.of(value).getOrNull()!!

    private fun useCase(
        classifyResult: (ByteArray) -> Result<List<DishLabel>, ClassifierError> =
            { Result.success(listOf(DishLabel("pizza", 0.9f))) },
        dishMap: Map<String, List<String>> = mapOf("pizza" to listOf("tomato", "cheese")),
        mealAiEnabled: Boolean = true,
        aiEnabled: Boolean = true,
    ) = ClassifyDraftPlateUseCase(
        classifier = FakeClassifier(classifyResult),
        ingredients = FakeIngredients(dishMap),
        featureFlags = FakeFeatureFlags(mealAiEnabled),
        aiPreference = FakeAiPreferencePort(aiEnabled),
    )

    @Test fun killswitch_off_returns_disabled_with_no_detections() = runTest {
        var classifierCalled = false
        val classify = ClassifyDraftPlateUseCase(
            classifier = FakeClassifier { classifierCalled = true; Result.success(listOf(DishLabel("pizza", 0.9f))) },
            ingredients = FakeIngredients(mapOf("pizza" to listOf("tomato"))),
            featureFlags = FakeFeatureFlags(mealAiEnabled = false),
            aiPreference = FakeAiPreferencePort(enabled = true),
        )

        val result = classify(bytes("plate"))

        assertEquals(Result.Ok(ClassifyDraftPlateUseCase.DISABLED), result)
        val value = (result as Result.Ok).value
        assertEquals("", value.dishSlug)
        assertEquals(emptyList(), value.ingredients)
        assertEquals("", value.version)
        assertFalse(classifierCalled, "kill-switch off must never invoke the on-device classifier")
    }

    @Test fun empty_labels_returns_low_confidence() = runTest {
        val classify = useCase(classifyResult = { Result.success(emptyList()) })

        val result = classify(bytes("plate"))

        assertEquals(Result.Err(ClassifierError.Run.LowConfidence), result)
    }

    @Test fun confidence_below_threshold_returns_low_confidence() = runTest {
        val classify = useCase(
            classifyResult = {
                Result.success(listOf(DishLabel("pizza", ClassifyDraftPlateUseCase.MIN_CONFIDENCE - 0.01f)))
            },
        )

        val result = classify(bytes("plate"))

        assertEquals(Result.Err(ClassifierError.Run.LowConfidence), result)
    }

    @Test fun unmapped_dish_returns_dish_unmapped() = runTest {
        val classify = useCase(
            classifyResult = { Result.success(listOf(DishLabel("pizza", 0.9f))) },
            dishMap = emptyMap(),
        )

        val result = classify(bytes("plate"))

        assertEquals(Result.Err(ClassifierError.Run.DishUnmapped), result)
    }

    @Test fun valid_inputs_return_classification_with_version_and_mapped_ingredients() = runTest {
        val classify = useCase(
            classifyResult = { Result.success(listOf(DishLabel("pizza", 0.9f))) },
            dishMap = mapOf("pizza" to listOf("tomato", "cheese")),
        )

        val result = classify(bytes("plate"))

        val value = (result as Result.Ok).value
        assertEquals("pizza", value.dishSlug)
        assertEquals(listOf(slug("tomato"), slug("cheese")), value.ingredients)
        assertEquals(ClassifyDraftPlateUseCase.MODEL_VERSION, value.version)
    }

    private fun bytes(s: String) = s.encodeToByteArray()

    private class FakeClassifier(
        private val result: (ByteArray) -> Result<List<DishLabel>, ClassifierError>,
    ) : MealClassifierPort {
        override suspend fun classify(jpeg: ByteArray) = result(jpeg)
    }

    private class FakeIngredients(private val dishMap: Map<String, List<String>>) : IngredientReadPort {
        override fun observeCatalog(): Flow<Map<IngredientSlug, Ingredient>> = MutableStateFlow(emptyMap())
        override suspend fun findBySlugs(slugs: Set<IngredientSlug>): List<Ingredient> = emptyList()
        override suspend fun suggestForDish(dishSlug: String): List<IngredientSlug> =
            dishMap[dishSlug].orEmpty().map { IngredientSlug.of(it).getOrNull()!! }
    }

    private class FakeFeatureFlags(private val mealAiEnabled: Boolean) : FeatureFlagPort {
        override fun isMealAiEnabled(): Boolean = mealAiEnabled
    }

    private class FakeAiPreferencePort(enabled: Boolean) : AiPreferencePort {
        override val enabled: Flow<Boolean> = flowOf(enabled)
        override suspend fun set(enabled: Boolean): Result<Unit, AiPreferenceError> = Result.success(Unit)
    }

    @Test fun user_opted_out_returns_disabled_and_classifier_never_invoked() = runTest {
        var classifierCallCount = 0
        val classify = ClassifyDraftPlateUseCase(
            classifier = FakeClassifier { classifierCallCount++; Result.success(listOf(DishLabel("pizza", 0.9f))) },
            ingredients = FakeIngredients(mapOf("pizza" to listOf("tomato"))),
            featureFlags = FakeFeatureFlags(mealAiEnabled = true),
            aiPreference = FakeAiPreferencePort(enabled = false),
        )

        val result = classify(bytes("plate"))

        assertEquals(Result.Ok(ClassifyDraftPlateUseCase.DISABLED), result)
        assertEquals(0, classifierCallCount, "user opted out — classifier must never be invoked")
    }
}
