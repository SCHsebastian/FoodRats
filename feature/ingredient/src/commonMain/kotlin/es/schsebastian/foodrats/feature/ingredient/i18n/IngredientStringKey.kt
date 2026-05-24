package es.schsebastian.foodrats.feature.ingredient.i18n

import es.schsebastian.foodrats.core.i18n.StringKey
import foodrats.feature.ingredient.generated.resources.Res
import foodrats.feature.ingredient.generated.resources.ingredient_catalog_empty
import foodrats.feature.ingredient.generated.resources.ingredient_catalog_load_failed
import foodrats.feature.ingredient.generated.resources.ingredient_category_beverage
import foodrats.feature.ingredient.generated.resources.ingredient_category_dairy
import foodrats.feature.ingredient.generated.resources.ingredient_category_fish
import foodrats.feature.ingredient.generated.resources.ingredient_category_fruit
import foodrats.feature.ingredient.generated.resources.ingredient_category_grain
import foodrats.feature.ingredient.generated.resources.ingredient_category_legume
import foodrats.feature.ingredient.generated.resources.ingredient_category_meat
import foodrats.feature.ingredient.generated.resources.ingredient_category_other
import foodrats.feature.ingredient.generated.resources.ingredient_category_sauce
import foodrats.feature.ingredient.generated.resources.ingredient_category_spice
import foodrats.feature.ingredient.generated.resources.ingredient_category_sweet
import foodrats.feature.ingredient.generated.resources.ingredient_category_vegetable
import foodrats.feature.ingredient.generated.resources.ingredient_detected_section_title
import foodrats.feature.ingredient.generated.resources.ingredient_retry_action
import foodrats.feature.ingredient.generated.resources.ingredient_search_hint
import foodrats.feature.ingredient.generated.resources.ingredient_select_title
import foodrats.feature.ingredient.generated.resources.ingredient_selection_full
import foodrats.feature.ingredient.generated.resources.ingredient_unknown
import org.jetbrains.compose.resources.StringResource

enum class IngredientStringKey(override val resourceId: StringResource) : StringKey {
    SelectIngredientsTitle(Res.string.ingredient_select_title),
    SelectIngredientsSearchHint(Res.string.ingredient_search_hint),
    DetectedSectionTitle(Res.string.ingredient_detected_section_title),
    SelectionFull(Res.string.ingredient_selection_full),
    CatalogLoadFailed(Res.string.ingredient_catalog_load_failed),
    CatalogEmpty(Res.string.ingredient_catalog_empty),
    RetryAction(Res.string.ingredient_retry_action),
    UnknownIngredient(Res.string.ingredient_unknown),
    CategoryVegetable(Res.string.ingredient_category_vegetable),
    CategoryFruit(Res.string.ingredient_category_fruit),
    CategoryMeat(Res.string.ingredient_category_meat),
    CategoryFish(Res.string.ingredient_category_fish),
    CategoryDairy(Res.string.ingredient_category_dairy),
    CategoryGrain(Res.string.ingredient_category_grain),
    CategoryLegume(Res.string.ingredient_category_legume),
    CategorySauce(Res.string.ingredient_category_sauce),
    CategorySpice(Res.string.ingredient_category_spice),
    CategorySweet(Res.string.ingredient_category_sweet),
    CategoryBeverage(Res.string.ingredient_category_beverage),
    CategoryOther(Res.string.ingredient_category_other),
}
