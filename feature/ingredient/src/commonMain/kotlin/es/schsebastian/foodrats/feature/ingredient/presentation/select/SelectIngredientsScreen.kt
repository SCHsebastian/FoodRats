package es.schsebastian.foodrats.feature.ingredient.presentation.select

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.schsebastian.foodrats.core.designsystem.atoms.FrButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrButtonVariant
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcon
import es.schsebastian.foodrats.core.designsystem.atoms.FrIconButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrShimmerBox
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.atoms.FrTextField
import es.schsebastian.foodrats.core.designsystem.layout.frContentWidth
import es.schsebastian.foodrats.core.designsystem.molecules.FrEmptyState
import es.schsebastian.foodrats.core.designsystem.molecules.FrErrorBanner
import es.schsebastian.foodrats.core.designsystem.motion.frRiseIn
import es.schsebastian.foodrats.core.designsystem.templates.FrScreenScaffold
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.domain.meal.IngredientCategory
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.ingredient.domain.usecase.SearchIngredientsUseCase
import es.schsebastian.foodrats.feature.ingredient.i18n.IngredientStringKey
import es.schsebastian.foodrats.feature.ingredient.presentation.components.FrIngredientRow
import es.schsebastian.foodrats.feature.ingredient.presentation.toStringKey
import org.koin.compose.viewmodel.koinViewModel

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

    // Fold the catalog into a search index once per snapshot; re-search per keystroke.
    val search = remember { SearchIngredientsUseCase() }
    val index = remember(state.catalog) { search.index(state.catalog) }
    val filtered = remember(state.query, index) { index.search(state.query) }
    val searching = state.query.isNotBlank()

    FrScreenScaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    FrText(
                        text = resolve(IngredientStringKey.SelectIngredientsTitle),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    FrIconButton(
                        icon = FrIcons.Back,
                        onClick = onDone,
                        contentDescription = resolve(IngredientStringKey.SelectIngredientsTitle),
                    )
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
                // Search query — no auto-capitalization (matches against lowercase ingredient names).
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            )

            // Group the (already-remembered) filtered catalog once per search/detected change instead
            // of re-running these O(categories × catalog) filters inside the LazyColumn content lambda
            // on every recomposition (selection toggles, cap changes, expand/collapse all recompose it).
            val detectedRows = remember(filtered, state.detected) {
                filtered.filter { it.slug in state.detected }
            }
            val categoryRows = remember(filtered, state.detected) {
                IngredientCategory.all.mapNotNull { category ->
                    val rows = filtered.filter { it.category == category && it.slug !in state.detected }
                    if (rows.isEmpty()) null else category to rows
                }
            }
            val selected = state.selected
            val capReached = state.capReached
            when {
                state.loading && state.catalog.isEmpty() -> {
                    IngredientPickerSkeleton(modifier = Modifier.weight(1f).frContentWidth())
                }
                state.catalog.isEmpty() -> {
                    FrEmptyState(
                        icon = FrIcons.Warning,
                        headline = resolve(IngredientStringKey.CatalogEmpty),
                        subtext = resolve(IngredientStringKey.CatalogLoadFailed),
                        modifier = Modifier.weight(1f).frContentWidth(),
                    )
                }
                else -> {
                    LazyColumn(modifier = Modifier.weight(1f).frContentWidth()) {
                        // A single running counter cascades the whole list — headers and rows
                        // alike — so the picker feels assembled top-to-bottom on load. The
                        // `% 6` window keeps later items popping promptly when scrolled in.
                        var cascade = 0
                        if (detectedRows.isNotEmpty()) {
                            val headerDelay = (cascade++ % 6) * 40
                            item(key = "detected-header") {
                                SectionHeader(
                                    text = resolve(IngredientStringKey.DetectedSectionTitle),
                                    modifier = Modifier.frRiseIn(delayMillis = headerDelay),
                                )
                            }
                            detectedRows.forEach { ing ->
                                val delay = (cascade++ % 6) * 40
                                item(key = "detected-${ing.slug.value}") {
                                    FrIngredientRow(
                                        name = ing.displayName,
                                        iconKey = ing.iconKey,
                                        selected = ing.slug in selected,
                                        enabled = ing.slug in selected || !capReached,
                                        onToggle = { vm.onIntent(SelectIngredientsIntent.Toggle(ing.slug)) },
                                        modifier = Modifier.frRiseIn(delayMillis = delay),
                                    )
                                }
                            }
                        }

                        categoryRows.forEach { (category, rows) ->
                            // While searching, force every matching group open so results are
                            // never hidden inside a collapsed section.
                            val expanded = searching || category in state.expandedCategories
                            val headerDelay = (cascade++ % 6) * 40
                            item(key = "cat-${category::class.simpleName}") {
                                SectionHeader(
                                    text = resolve(category.toStringKey()),
                                    expanded = expanded,
                                    modifier = Modifier
                                        .frRiseIn(delayMillis = headerDelay)
                                        .clickable {
                                            vm.onIntent(SelectIngredientsIntent.ToggleCategory(category))
                                        },
                                )
                            }
                            if (expanded) {
                                rows.forEach { ing ->
                                    val delay = (cascade++ % 6) * 40
                                    item(key = "cat-${ing.slug.value}") {
                                        FrIngredientRow(
                                            name = ing.displayName,
                                            iconKey = ing.iconKey,
                                            selected = ing.slug in selected,
                                            enabled = ing.slug in selected || !capReached,
                                            onToggle = { vm.onIntent(SelectIngredientsIntent.Toggle(ing.slug)) },
                                            modifier = Modifier.frRiseIn(delayMillis = delay),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (capReached) {
                FrErrorBanner(
                    text = resolve(IngredientStringKey.SelectionFull),
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                )
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

/** Number of placeholder rows shown while the catalog loads. */
private const val SkeletonRowCount = 7

/** Height of a placeholder text-line bar — roughly a single line of body text. */
private val SkeletonLineHeight = 18.dp

/**
 * Decorative loading silhouette for the ingredient picker: a stack of rows that
 * mimic [FrIngredientRow] — a round leading glyph plus a flexible text-line bar.
 * No strings; purely visual while the catalog snapshot resolves.
 */
@Composable
private fun IngredientPickerSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        repeat(SkeletonRowCount) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            ) {
                FrShimmerBox(
                    modifier = Modifier.size(Sizes.iconMd),
                    shape = CircleShape,
                )
                FrShimmerBox(
                    modifier = Modifier
                        .padding(start = Spacing.md)
                        .fillMaxWidth(0.6f)
                        .height(SkeletonLineHeight),
                    shape = RoundedCornerShape(Radius.sm),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    expanded: Boolean? = null,
) {
    // A low-contrast filled band sets each section apart from its rows without
    // competing with the (now bolder) selected-row fill above it. The chevron
    // rotates on a spring so expanding a category reads as a single gesture.
    val chevronRotation by animateFloatAsState(targetValue = if (expanded == true) 90f else 0f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
            .clip(RoundedCornerShape(Radius.sm))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        FrText(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.semantics { heading() },   // WCAG 2.4.10 heading navigation
        )
        if (expanded != null) {
            FrIcon(
                image = FrIcons.ChevronRight,
                modifier = Modifier.rotate(chevronRotation),
            )
        }
    }
}
