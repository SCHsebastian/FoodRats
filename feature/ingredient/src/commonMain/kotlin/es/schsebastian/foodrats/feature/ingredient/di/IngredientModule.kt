package es.schsebastian.foodrats.feature.ingredient.di

import es.schsebastian.foodrats.core.domain.cuisine.CuisineReadPort
import es.schsebastian.foodrats.core.domain.meal.IngredientReadPort
import es.schsebastian.foodrats.core.domain.preferences.AppLocale
import es.schsebastian.foodrats.core.domain.preferences.LocalePort
import es.schsebastian.foodrats.feature.ingredient.data.deviceLanguageTag
import es.schsebastian.foodrats.feature.ingredient.data.firebase.CuisineDataSource
import es.schsebastian.foodrats.feature.ingredient.data.firebase.CuisineFirestoreDataSource
import es.schsebastian.foodrats.feature.ingredient.data.firebase.CuisineRepository
import es.schsebastian.foodrats.feature.ingredient.data.firebase.IngredientDataSource
import es.schsebastian.foodrats.feature.ingredient.data.firebase.IngredientFirestoreDataSource
import es.schsebastian.foodrats.feature.ingredient.data.firebase.IngredientRepository
import es.schsebastian.foodrats.feature.ingredient.data.local.CatalogCache
import es.schsebastian.foodrats.feature.ingredient.data.local.CuisineCache
import es.schsebastian.foodrats.feature.ingredient.data.local.CuisineCatalogCache
import es.schsebastian.foodrats.feature.ingredient.data.local.IngredientCatalogCache
import es.schsebastian.foodrats.feature.ingredient.domain.usecase.ObserveCatalogUseCase
import es.schsebastian.foodrats.feature.ingredient.domain.usecase.SearchIngredientsUseCase
import es.schsebastian.foodrats.feature.ingredient.presentation.select.SelectIngredientsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

/** App-lifetime scope that keeps the cache-backed catalog `StateFlow` warm across screens. */
private val AppScope = named("appScope")

/**
 * Active display language: the explicit in-app override when set, else the device
 * language (System). The DTO mapper falls back to "en" when the resolved language
 * has no name for an ingredient.
 */
private fun languageTagFlow(locale: LocalePort): Flow<String> =
    locale.locale.map { l -> if (l == AppLocale.System) deviceLanguageTag() else l.tag }

/**
 * Wires the ingredient catalog + picker. The `IngredientRepository` is the single
 * `IngredientReadPort`; it owns an app-lifetime [AppScope] that keeps the cache-backed
 * catalog `StateFlow` warm and hosts the one-shot `get()` refresh (the catalog is static,
 * admin-seeded, so it's read once per launch into a DataStore cache — no warm Firestore
 * listener). `SelectIngredientsViewModel` reads/writes the draft through
 * `MealDraftIngredientsPort` (bound by `mealModule`), so this module never touches `:feature:meal`.
 */
val ingredientModule = module {
    single<CoroutineScope>(AppScope) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single<IngredientDataSource> { IngredientFirestoreDataSource(get()) }
    single<CatalogCache> { IngredientCatalogCache(prefs = get(), json = get()) }
    single {
        IngredientRepository(
            datasource = get(),
            cache = get(),
            dispatchers = get(),
            language = languageTagFlow(get()),
            scope = get(AppScope),
        )
    } bind IngredientReadPort::class
    factoryOf(::ObserveCatalogUseCase)
    factoryOf(::SearchIngredientsUseCase)
    // Explicit (not viewModelOf): the analytics param has a Noop default that
    // viewModelOf would short-circuit to. See the analytics-base convention in CLAUDE.md.
    viewModel {
        SelectIngredientsViewModel(
            observeCatalog = get(),
            draftIngredients = get(),
            analytics = get(),
        )
    }
}

/**
 * Wires the cuisine catalog read path — the EXACT shape of [ingredientModule], over the SAME
 * app-lifetime [AppScope] (bound by [ingredientModule], shared in the merged Koin graph) so the
 * `cuisines` snapshot listener stays warm. Binds the single [CuisineReadPort]
 * ([CuisineRepository]) consumed by the meal publish stamp (`:feature:meal`) and the stats passport
 * grid (`:feature:stats`). Registered in `shared` `appModules` alongside `ingredientModule`.
 */
val cuisineModule = module {
    single<CuisineDataSource> { CuisineFirestoreDataSource(get()) }
    single<CuisineCache> { CuisineCatalogCache(prefs = get(), json = get()) }
    single {
        CuisineRepository(
            datasource = get(),
            cache = get(),
            dispatchers = get(),
            language = languageTagFlow(get()),
            scope = get(AppScope),
        )
    } bind CuisineReadPort::class
}
