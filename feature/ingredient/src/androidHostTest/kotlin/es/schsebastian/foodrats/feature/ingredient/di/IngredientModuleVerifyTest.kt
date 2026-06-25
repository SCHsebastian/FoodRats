package es.schsebastian.foodrats.feature.ingredient.di

import es.schsebastian.foodrats.core.domain.analytics.AnalyticsPort
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.meal.MealDraftIngredientsPort
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify
import kotlin.test.Test

/**
 * Static Koin graph check for [ingredientModule]: a missing or mis-typed binding fails here instead
 * of at app launch.
 *
 * [extraTypes]:
 *  - `MealDraftIngredientsPort` is owned by `:feature:meal` (read/write the draft's ingredient set).
 *  - `DispatcherProvider` is bound app-wide in `coreDataModule`.
 *  - `Flow` covers `IngredientRepository.language: Flow<String>`, derived from `LocalePort` in the
 *    binding lambda (the lambda body isn't reflected — only the constructor is — so the language
 *    `Flow` shows up as a required type).
 *  - `AnalyticsPort` is bound per-platform (not in this module); `SelectIngredientsViewModel` needs it.
 *
 * `verify` is JVM-only, so this lives in androidHostTest.
 */
class IngredientModuleVerifyTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun ingredient_module_graph_is_complete() {
        ingredientModule.verify(
            extraTypes = listOf(
                MealDraftIngredientsPort::class,
                DispatcherProvider::class,
                Flow::class,
                AnalyticsPort::class,
            ),
        )
    }
}
