package es.schsebastian.foodrats.feature.meal.data.firebase

import dev.gitlive.firebase.auth.FirebaseAuth

/**
 * The slice of the signed-in user the meal repository needs at publish/rate time: the
 * auth uid (presence gates the operation) plus the denormalized author display fields
 * stamped onto a new meal. Expressed as a thin data-layer port over [FirebaseAuth] so the
 * repository's auth-gate orchestration is verifiable in `commonTest` and the vendor type
 * never reaches the repository.
 *
 * Data-layer-private: never leaves `data/firebase/`.
 */
internal interface MealAuthorIdentity {
    /** The current signed-in author, or `null` when no live auth token is present. */
    fun current(): Author?

    data class Author(val uid: String, val displayName: String?, val avatarUrl: String?)
}

/** [MealAuthorIdentity] backed by GitLive [FirebaseAuth]. The only Firebase-touching impl. */
internal class FirebaseAuthorIdentity(private val auth: FirebaseAuth) : MealAuthorIdentity {
    override fun current(): MealAuthorIdentity.Author? {
        val user = auth.currentUser ?: return null
        return MealAuthorIdentity.Author(
            uid = user.uid,
            displayName = user.displayName,
            avatarUrl = user.photoURL,
        )
    }
}
