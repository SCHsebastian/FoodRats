package es.schsebastian.foodrats.feature.ingredient.data.firebase

import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Reads the cuisine catalog (`cuisines`) and the dish → cuisine map (`dishCuisineMap`) — the EXACT
 * shape of [IngredientFirestoreDataSource], since the seed handoff confirms the doc shapes match 1:1.
 */
interface CuisineDataSource {
    fun observeCatalog(): Flow<List<CuisineDto>>
    suspend fun loadDishCuisine(dishSlug: String): DishCuisineMapDto?
}

class CuisineFirestoreDataSource(private val db: FirebaseFirestore) : CuisineDataSource {

    override fun observeCatalog(): Flow<List<CuisineDto>> =
        db.collection("cuisines").snapshots.map { snap ->
            snap.documents.map { it.data<CuisineDto>() }
        }

    override suspend fun loadDishCuisine(dishSlug: String): DishCuisineMapDto? {
        val doc = db.collection("dishCuisineMap").document(dishSlug).get()
        return if (doc.exists) doc.data<DishCuisineMapDto>() else null
    }
}
