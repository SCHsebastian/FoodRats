package es.schsebastian.foodrats.feature.ingredient.domain

sealed interface IngredientError {
    sealed interface Load : IngredientError {
        data object Offline : Load
        data object Empty   : Load
    }
}
