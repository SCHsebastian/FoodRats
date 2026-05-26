package es.schsebastian.foodrats.core.domain.meal

sealed interface ClassifierError {
    sealed interface Load : ClassifierError {
        data object ModelMissing : Load
        data object ModelCorrupt : Load
    }
    sealed interface Run : ClassifierError {
        data object DecodeFailed : Run
        data object InferenceFailed : Run
        data object LowConfidence : Run
        data object DishUnmapped : Run
    }
}
