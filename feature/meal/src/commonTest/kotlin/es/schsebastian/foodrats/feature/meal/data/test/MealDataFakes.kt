package es.schsebastian.foodrats.feature.meal.data.test

import es.schsebastian.foodrats.core.domain.account.Account
import es.schsebastian.foodrats.core.domain.account.AccountReadPort
import es.schsebastian.foodrats.core.domain.image.ImageUrlError
import es.schsebastian.foodrats.core.domain.image.ImageUrlPort
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.meal.data.firebase.MealAuthorIdentity
import es.schsebastian.foodrats.feature.meal.data.firebase.MealDto
import es.schsebastian.foodrats.feature.meal.data.firebase.MealFirestore
import es.schsebastian.foodrats.feature.meal.data.firebase.PlateStorage
import es.schsebastian.foodrats.feature.meal.data.local.LocalMeal
import es.schsebastian.foodrats.feature.meal.data.local.MealLocalStore
import es.schsebastian.foodrats.feature.meal.domain.model.Plate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
    // When non-null, `mealExists` reports these slots AFTER a write has thrown — models a
    // concurrent publish that created the doc between the free-slot pre-check and this rejected
    // write (the double-fire race). Lets a test assert the orphan-cleanup is skipped for a blob
    // that now backs a live doc.
    var existingSlotsAfterWriteFault: Set<MealSlot>? = null
    private var writeFaultRaised = false

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
    ): Boolean {
        val slots = if (writeFaultRaised) (existingSlotsAfterWriteFault ?: existingSlots) else existingSlots
        return slot in slots
    }

    override suspend fun takenSlots(crewId: CrewId, authorId: AccountId, dayKey: String): Set<MealSlot> =
        existingSlots

    override suspend fun deleteMeal(crewId: CrewId, mealId: String) {
        deleteCalls += crewId to mealId
        deleteFault?.let { throw it }
    }

    override suspend fun write(dto: MealDto, docId: String) {
        writeFault?.let { writeFaultRaised = true; throw it }
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
    var deleteFault: Throwable? = null
    var url: String = "https://fake/plate.jpg"

    data class UploadCall(val crewId: CrewId, val mealId: String, val plate: Plate)
    val uploads = mutableListOf<UploadCall>()
    val deletes = mutableListOf<Pair<CrewId, String>>()

    override suspend fun upload(crewId: CrewId, mealId: String, plate: Plate): String {
        uploads += UploadCall(crewId, mealId, plate)
        uploadFault?.let { throw it }
        return url
    }

    override suspend fun delete(crewId: CrewId, mealId: String) {
        deletes += crewId to mealId
        deleteFault?.let { throw it }
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

/**
 * Behavioral fake for [ImageUrlPort]. By default resolves each requested path to a
 * deterministic `signed://{path}` URL so callers can assert resolution; set [fail] to model
 * a backend failure (the resolver contract degrades to "no URL" on the consumer side).
 */
internal class FakeImageUrlPort(
    var fail: Boolean = false,
) : ImageUrlPort {
    val calls = mutableListOf<Pair<CrewId, List<String>>>()

    override suspend fun resolve(
        crewId: CrewId,
        paths: List<String>,
    ): Result<Map<String, String>, ImageUrlError> {
        calls += crewId to paths
        if (fail) return Result.failure(ImageUrlError.Unavailable)
        return Result.success(paths.associateWith { "signed://$it" })
    }
}

/**
 * Behavioral fake for [AccountReadPort]: resolves live identity per account so the repository's
 * read-path enrichment (which overrides the denormalized snapshots with live `accounts/{id}`) can be
 * exercised. Unset ids emit `null` (the "deleted user" case). Mirrors :feature:feed's equivalent —
 * it can't be shared across module boundaries.
 */
internal class FakeAccountReadPort : AccountReadPort {
    private val flows = mutableMapOf<AccountId, MutableStateFlow<Account?>>()

    override fun observe(id: AccountId): Flow<Account?> =
        flows.getOrPut(id) { MutableStateFlow(null) }

    fun set(id: AccountId, account: Account?) {
        flows.getOrPut(id) { MutableStateFlow(null) }.value = account
    }
}

/**
 * Read-only override-fake for [MealLocalStore] (offline-first P3a-T4 read-path test double). It uses
 * the no-DB protected constructor and returns canned [LocalMeal]s so the repository's enrichment
 * (signed URLs + live identity → `toMealWithRatings`) is verified WITHOUT a SQLDelight driver
 * (feature:meal commonTest has none; the real JVM-backed store is covered in androidHostTest).
 * [observeRange] serves all reads, replaying its current rows on subscribe; the repository filters
 * by dayKey/range downstream.
 */
internal class FakeMealLocalStore(rows: List<LocalMeal> = emptyList()) : MealLocalStore() {
    private val rowsFlow = MutableStateFlow(rows)
    val rangeCalls = mutableListOf<Triple<String, String, String>>()

    fun emit(rows: List<LocalMeal>) { rowsFlow.value = rows }

    override fun observeRange(crewId: String, fromKey: String, toKey: String): Flow<List<LocalMeal>> {
        rangeCalls += Triple(crewId, fromKey, toKey)
        return rowsFlow
    }

    override fun observeFeed(crewId: String, dayKey: String): Flow<List<LocalMeal>> = rowsFlow
}
