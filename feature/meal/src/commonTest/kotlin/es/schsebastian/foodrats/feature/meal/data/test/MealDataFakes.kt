package es.schsebastian.foodrats.feature.meal.data.test

import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.feature.meal.data.firebase.MealAuthorIdentity
import es.schsebastian.foodrats.feature.meal.data.firebase.MealDto
import es.schsebastian.foodrats.feature.meal.data.firebase.MealFirestore
import es.schsebastian.foodrats.feature.meal.data.firebase.PlateStorage
import es.schsebastian.foodrats.feature.meal.domain.model.Plate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * The canonical behavioral fake for the [MealFirestore] seam — one per port, shared across
 * the repository-impl tests. It records every write/rate/delete call and can be primed to
 * throw a typed-fault [Throwable] per operation so the repository's vendor-translation
 * (`MealErrorMapper` / `FirebaseFault`) is exercised end-to-end.
 *
 * Faults are raised as `RuntimeException`s whose message classifies via `toFirebaseFault()`
 * — the same single message-inspection seam production uses — so a test that primes
 * `"PERMISSION_DENIED"` reproduces exactly what the live SDK surfaces.
 */
internal class FakeMealFirestore : MealFirestore {
    var writeFault: Throwable? = null
    var deleteFault: Throwable? = null
    var rateFault: Throwable? = null
    var rateOutcome: MealFirestore.RateOutcome = MealFirestore.RateOutcome.Ok
    var existingSlots: Set<MealSlot> = emptySet()

    data class WriteCall(val dto: MealDto, val docId: String)
    val writes = mutableListOf<WriteCall>()

    data class RateCall(
        val crewId: CrewId,
        val mealId: String,
        val raterUid: String,
        val score: Int,
        val nowEpochMs: Long,
    )
    val rateCalls = mutableListOf<RateCall>()
    val deleteCalls = mutableListOf<Pair<CrewId, String>>()

    override fun observeForRange(crewId: CrewId, from: MealDay, to: MealDay): Flow<List<MealDto>> =
        flowOf(emptyList())

    override suspend fun mealExists(
        crewId: CrewId,
        authorId: AccountId,
        dayKey: String,
        slot: MealSlot,
    ): Boolean = slot in existingSlots

    override suspend fun takenSlots(crewId: CrewId, authorId: AccountId, dayKey: String): Set<MealSlot> =
        existingSlots

    override suspend fun deleteMeal(crewId: CrewId, mealId: String) {
        deleteCalls += crewId to mealId
        deleteFault?.let { throw it }
    }

    override suspend fun write(dto: MealDto, docId: String) {
        writeFault?.let { throw it }
        writes += WriteCall(dto, docId)
    }

    override suspend fun rateMeal(
        crewId: CrewId,
        mealId: String,
        raterUid: String,
        score: Int,
        nowEpochMs: Long,
    ): MealFirestore.RateOutcome {
        rateCalls += RateCall(crewId, mealId, raterUid, score, nowEpochMs)
        rateFault?.let { throw it }
        return rateOutcome
    }
}

/**
 * The canonical behavioral fake for the [PlateStorage] seam. Records the single upload call
 * and can be primed to fail so the repository maps the storage failure to the photo-upload
 * error rather than a generic one.
 */
internal class FakePlateStorage : PlateStorage {
    var uploadFault: Throwable? = null
    var url: String = "https://fake/plate.jpg"

    data class UploadCall(val crewId: CrewId, val mealId: String, val plate: Plate)
    val uploads = mutableListOf<UploadCall>()

    override suspend fun upload(crewId: CrewId, mealId: String, plate: Plate): String {
        uploads += UploadCall(crewId, mealId, plate)
        uploadFault?.let { throw it }
        return url
    }
}

/**
 * The canonical behavioral fake for the [MealAuthorIdentity] seam. Defaults to a signed-in
 * author; set [author] to `null` to model "no live auth token".
 */
internal class FakeMealAuthorIdentity(
    var author: MealAuthorIdentity.Author? =
        MealAuthorIdentity.Author(uid = "acc-1", displayName = "Author Name", avatarUrl = "https://fake/a.png"),
) : MealAuthorIdentity {
    override fun current(): MealAuthorIdentity.Author? = author
}
