package es.schsebastian.foodrats.feature.ingredient.di

import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify
import kotlin.test.Test

/**
 * Static Koin graph check for [cuisineModule]: a missing or mis-typed binding fails here instead of
 * at app launch.
 *
 * [extraTypes]:
 *  - `CoroutineScope` is the app-lifetime `named("appScope")` scope bound by [ingredientModule]
 *    (both modules share one graph in `shared`); the cuisine repository reads it.
 *  - `DispatcherProvider` is bound app-wide in `coreDataModule`.
 *  - `Flow` covers `CuisineRepository.language: Flow<String>`, derived from `LocalePort` in the
 *    binding lambda (the lambda body isn't reflected — only the constructor is — so the language
 *    `Flow` shows up as a required type).
 *
 * `verify` is JVM-only, so this lives in androidHostTest.
 */
class CuisineModuleVerifyTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun cuisine_module_graph_is_complete() {
        cuisineModule.verify(
            extraTypes = listOf(
                CoroutineScope::class,
                DispatcherProvider::class,
                Flow::class,
            ),
        )
    }
}
