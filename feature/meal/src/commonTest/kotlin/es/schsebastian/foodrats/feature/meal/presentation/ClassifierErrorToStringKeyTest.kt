package es.schsebastian.foodrats.feature.meal.presentation

import es.schsebastian.foodrats.core.domain.meal.ClassifierError
import kotlin.test.Test
import kotlin.test.assertNotNull

class ClassifierErrorToStringKeyTest {
    @Test fun all_leaves_mapped() {
        val leaves = listOf<ClassifierError>(
            ClassifierError.Load.ModelMissing,
            ClassifierError.Load.ModelCorrupt,
            ClassifierError.Run.DecodeFailed,
            ClassifierError.Run.InferenceFailed,
            ClassifierError.Run.LowConfidence,
            ClassifierError.Run.DishUnmapped,
        )
        leaves.forEach { assertNotNull(it.toStringKey()) }
    }
}
