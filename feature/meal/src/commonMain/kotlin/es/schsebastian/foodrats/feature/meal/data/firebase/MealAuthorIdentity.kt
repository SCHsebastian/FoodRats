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

/**
 * Server-enforced cap on the denormalized `authorName` field (firestore.rules, 2026-07-19
 * hardening sweep: meal create + comment create/update reject `authorName.size() > 120`).
 * The value comes verbatim from the auth provider's `displayName`, which this app never
 * bounds (Google/Apple supply it; Apple Sign-In names are user-editable free text), so the
 * wire seam must truncate — otherwise one long provider name permanently rejects every meal
 * publish and comment post for that user.
 */
internal const val AUTHOR_NAME_WIRE_MAX = 120

/**
 * Truncates a display name to [AUTHOR_NAME_WIRE_MAX] UTF-16 units for the Firestore write.
 * Kotlin `length` counts UTF-16 units, which is >= the rule's character count, so the result
 * always passes the rule. Surrogate-safe: never cuts a pair in half (an unpaired surrogate
 * would corrupt the UTF-8 serialization of the write payload).
 */
internal fun String.toWireAuthorName(): String {
    if (length <= AUTHOR_NAME_WIRE_MAX) return this
    val cut = take(AUTHOR_NAME_WIRE_MAX)
    return if (cut.last().isHighSurrogate()) cut.dropLast(1) else cut
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
