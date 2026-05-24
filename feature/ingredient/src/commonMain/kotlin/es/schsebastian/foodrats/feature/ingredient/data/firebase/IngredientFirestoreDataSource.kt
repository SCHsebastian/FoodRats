package es.schsebastian.foodrats.feature.ingredient.data.firebase

import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class IngredientFirestoreDataSource(private val db: FirebaseFirestore) {

    fun observeCatalog(): Flow<List<IngredientDto>> =
        db.collection("ingredients").snapshots.map { snap ->
            snap.documents.map { it.data<IngredientDto>() }
        }

    suspend fun loadDishMap(dishSlug: String): DishIngredientMapDto? {
        val doc = db.collection("dishIngredientMap").document(dishSlug).get()
        return if (doc.exists) doc.data<DishIngredientMapDto>() else null
    }
}
