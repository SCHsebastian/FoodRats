package es.schsebastian.foodrats.feature.ingredient.data.firebase

import dev.gitlive.firebase.firestore.FirebaseFirestore

interface IngredientDataSource {
    /** One-shot read of the whole static `ingredients` catalog (FIREST-4: no warm listener). */
    suspend fun loadCatalog(): List<IngredientDto>
    suspend fun loadDishMap(dishSlug: String): DishIngredientMapDto?
}

class IngredientFirestoreDataSource(private val db: FirebaseFirestore) : IngredientDataSource {

    override suspend fun loadCatalog(): List<IngredientDto> =
        db.collection("ingredients").get().documents.map { it.data<IngredientDto>() }

    override suspend fun loadDishMap(dishSlug: String): DishIngredientMapDto? {
        val doc = db.collection("dishIngredientMap").document(dishSlug).get()
        return if (doc.exists) doc.data<DishIngredientMapDto>() else null
    }
}
