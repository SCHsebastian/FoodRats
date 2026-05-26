package es.schsebastian.foodrats.feature.ingredient.presentation.select

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.schsebastian.foodrats.core.designsystem.atoms.FrButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrButtonVariant
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.atoms.FrTextField
import es.schsebastian.foodrats.core.designsystem.templates.FrScreenScaffold
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.domain.meal.IngredientCategory
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.ingredient.domain.usecase.SearchIngredientsUseCase
import es.schsebastian.foodrats.feature.ingredient.i18n.IngredientStringKey
import es.schsebastian.foodrats.feature.ingredient.presentation.components.FrIngredientRow
import es.schsebastian.foodrats.feature.ingredient.presentation.toStringKey
import org.koin.compose.viewmodel.koinViewModel

private val CategoryOrder: List<IngredientCategory> = listOf(
    IngredientCategory.Vegetable,
    IngredientCategory.Fruit,
    IngredientCategory.Meat,
    IngredientCategory.Fish,
    IngredientCategory.Dairy,
    IngredientCategory.Grain,
    IngredientCategory.Legume,
    IngredientCategory.Sauce,
    IngredientCategory.Spice,
    IngredientCategory.Sweet,
    IngredientCategory.Beverage,
    IngredientCategory.Other,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectIngredientsScreen(
    onDone: () -> Unit,
    vm: SelectIngredientsViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.effects.collect { if (it is SelectIngredientsEffect.NavigateBack) onDone() }
    }

    val search = remember { SearchIngredientsUseCase() }
    val filtered = remember(state.query, state.catalog) { search(state.catalog, state.query) }

    FrScreenScaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(resolve(IngredientStringKey.SelectIngredientsTitle)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(imageVector = FrIcons.Back, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            FrTextField(
                value = state.query,
                onValueChange = { vm.onIntent(SelectIngredientsIntent.QueryChanged(it)) },
                placeholder = resolve(IngredientStringKey.SelectIngredientsSearchHint),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            )

            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val detected = filtered.filter { it.slug in state.detected }
                if (detected.isNotEmpty()) {
                    item(key = "detected-header") {
                        SectionHeader(resolve(IngredientStringKey.DetectedSectionTitle))
                    }
                    items(detected, key = { "detected-${it.slug.value}" }) { ing ->
                        FrIngredientRow(
                            name = ing.displayName,
                            iconKey = ing.iconKey,
                            selected = ing.slug in state.selected,
                            enabled = ing.slug in state.selected || !state.capReached,
                            onToggle = { vm.onIntent(SelectIngredientsIntent.Toggle(ing.slug)) },
                        )
                    }
                }

                CategoryOrder.forEach { category ->
                    val rows = filtered.filter { it.category == category }
                    if (rows.isEmpty()) return@forEach
                    val expanded = category in state.expandedCategories
                    item(key = "cat-${category::class.simpleName}") {
                        SectionHeader(
                            text = resolve(category.toStringKey()),
                            modifier = Modifier.clickable {
                                vm.onIntent(SelectIngredientsIntent.ToggleCategory(category))
                            },
                        )
                    }
                    if (expanded) {
                        items(rows, key = { "cat-${it.slug.value}" }) { ing ->
                            FrIngredientRow(
                                name = ing.displayName,
                                iconKey = ing.iconKey,
                                selected = ing.slug in state.selected,
                                enabled = ing.slug in state.selected || !state.capReached,
                                onToggle = { vm.onIntent(SelectIngredientsIntent.Toggle(ing.slug)) },
                            )
                        }
                    }
                }
            }

            FrButton(
                label = resolve(IngredientStringKey.SelectDone),
                onClick = { vm.onIntent(SelectIngredientsIntent.ConfirmAndExit) },
                variant = FrButtonVariant.Primary,
                modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    FrText(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    )
}
