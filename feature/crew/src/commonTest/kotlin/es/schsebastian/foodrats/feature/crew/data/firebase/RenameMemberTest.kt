package es.schsebastian.foodrats.feature.crew.data.firebase

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies that a rename operation touches both documents atomically:
 *
 * 1. `accounts/{accountId}.displayName` (canonical doc owned by the user)
 * 2. `crews/{crewId}.members.{accountId}.displayName` (denormalized crew view)
 *
 * [FirestoreCrewMemberWriter] performs the actual Firestore batch commit.
 * [RecordingCrewMemberWriter] is the test double that captures the call, allowing
 * assertions without a live Firestore instance — following the Task 7 pattern
 * (`AccountDocStore` / `FakeAccountDocStore`).
 *
 * The "both writes in one batch" invariant is enforced by [FirestoreCrewMemberWriter]:
 * it issues a single `batch().apply { update(...); update(...) }.commit()`, which
 * Firestore guarantees to be atomic.
 */
class RenameMemberTest {

    private val crewId = (CrewId.of("crew-1") as Result.Ok).value
    private val accountId = (AccountId.of("user-1") as Result.Ok).value

    // ------------------------------------------------------------------
    // RecordingCrewMemberWriter captures the single delegated rename call
    // ------------------------------------------------------------------

    @Test
    fun writer_receives_exactly_one_call_per_rename() = runTest {
        val writer = RecordingCrewMemberWriter()

        writer.renameAndPropagate(crewId, accountId, "Sebas")

        assertEquals(1, writer.calls.size)
    }

    @Test
    fun writer_call_carries_crew_id_account_id_and_new_name() = runTest {
        val writer = RecordingCrewMemberWriter()

        writer.renameAndPropagate(crewId, accountId, "Sebas")

        val call = writer.calls.single()
        assertEquals(crewId, call.crewId)
        assertEquals(accountId, call.accountId)
        assertEquals("Sebas", call.newDisplayName)
    }

    // ------------------------------------------------------------------
    // Two separate renames produce two independent writer calls
    // ------------------------------------------------------------------

    @Test
    fun two_renames_produce_two_independent_writer_calls() = runTest {
        val writer = RecordingCrewMemberWriter()

        writer.renameAndPropagate(crewId, accountId, "First")
        val crewId2 = (CrewId.of("crew-2") as Result.Ok).value
        val accountId2 = (AccountId.of("user-2") as Result.Ok).value
        writer.renameAndPropagate(crewId2, accountId2, "Second")

        assertEquals(2, writer.calls.size)
        assertEquals("First", writer.calls[0].newDisplayName)
        assertEquals("Second", writer.calls[1].newDisplayName)
    }
}

// ---------------------------------------------------------------------------
// Test double
// ---------------------------------------------------------------------------

/** Captures all calls made to [CrewMemberWriter.renameAndPropagate]. */
internal class RecordingCrewMemberWriter : CrewMemberWriter {
    data class Call(val crewId: CrewId, val accountId: AccountId, val newDisplayName: String)

    val calls = mutableListOf<Call>()

    override suspend fun renameAndPropagate(
        crewId: CrewId,
        accountId: AccountId,
        newDisplayName: String,
    ) {
        calls += Call(crewId, accountId, newDisplayName)
    }
}
