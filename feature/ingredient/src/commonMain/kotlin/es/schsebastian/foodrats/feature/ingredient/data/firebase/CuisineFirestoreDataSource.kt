package es.schsebastian.foodrats.feature.ingredient.data.firebase

import dev.gitlive.firebase.firestore.FirebaseFirestore

/**
 * Reads the cuisine catalog (`cuisines`) and the dish → cuisine map (`dishCuisineMap`) — the EXACT
 * shape of [IngredientFirestoreDataSource], since the seed handoff confirms the doc shapes match 1:1.
 */
interface CuisineDataSource {
    /** One-shot read of the whole static `cuisines` catalog (FIREST-4: no warm listener). */
    suspend fun loadCatalog(): List<CuisineDto>
    suspend fun loadDishCuisine(dishSlug: String): DishCuisineMapDto?
}

class CuisineFirestoreDataSource(private val db: FirebaseFirestore) : CuisineDataSource {

    override suspend fun loadCatalog(): List<CuisineDto> =
        db.collection("cuisines").get().documents.map { it.data<CuisineDto>() }

    override suspend fun loadDishCuisine(dishSlug: String): DishCuisineMapDto? {
        val doc = db.collection("dishCuisineMap").document(dishSlug).get()
        return if (doc.exists) doc.data<DishCuisineMapDto>() else null
    }
}
