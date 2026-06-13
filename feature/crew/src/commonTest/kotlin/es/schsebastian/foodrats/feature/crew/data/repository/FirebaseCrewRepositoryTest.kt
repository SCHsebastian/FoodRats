package es.schsebastian.foodrats.feature.crew.data.repository

import app.cash.turbine.test
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.time.FixedClock
import es.schsebastian.foodrats.feature.crew.data.firebase.AlreadyMemberException
import es.schsebastian.foodrats.feature.crew.data.firebase.CodeCollisionExhaustedException
import es.schsebastian.foodrats.feature.crew.data.firebase.CodeUnknownException
import es.schsebastian.foodrats.feature.crew.data.firebase.CrewDto
import es.schsebastian.foodrats.feature.crew.data.firebase.CrewErrorMapper
import es.schsebastian.foodrats.feature.crew.data.firebase.FakeCrewDataSource
import es.schsebastian.foodrats.feature.crew.data.firebase.FullException
import es.schsebastian.foodrats.feature.crew.data.firebase.MemberDto
import es.schsebastian.foodrats.feature.crew.data.firebase.NotFoundException
import es.schsebastian.foodrats.feature.crew.data.firebase.NotMemberException
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.model.Crew
import es.schsebastian.foodrats.feature.crew.domain.model.CrewCode
import es.schsebastian.foodrats.feature.crew.domain.model.Member
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseCrewRepositoryTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
    }
    private val nowMs = 1_700_000_000_000L
    private val clock = FixedClock(Instant.fromEpochMilliseconds(nowMs))
    private val ds = FakeCrewDataSource()

    private fun repo() = FirebaseCrewRepository(ds, dispatchers, CrewErrorMapper(), clock)

    private fun aid(raw: String): AccountId = (AccountId.of(raw) as Result.Ok).value
    private fun cid(raw: String): CrewId = (CrewId.of(raw) as Result.Ok).value
    private fun code(raw: String): CrewCode = (CrewCode.of(raw) as Result.Ok).value

    private val validDto = CrewDto(
        id = "c-1",
        name = "Test Crew",
        code = "ABCD23",
        ownerId = "owner",
        createdAtEpochMs = nowMs,
        memberIds = listOf("owner"),
        members = mapOf("owner" to MemberDto(joinedAtEpochMs = nowMs)),
    )

    private fun crewOwnedBy(ownerRaw: String): Crew = Crew.of(
        id = cid("c-1"),
        name = "Test Crew",
        code = code("ABCD23"),
        ownerId = aid(ownerRaw),
        createdAt = Instant.fromEpochMilliseconds(nowMs),
        members = listOf(Member(accountId = aid(ownerRaw), joinedAt = Instant.fromEpochMilliseconds(nowMs))),
    )

    // ---------------- create ----------------

    @Test
    fun create_maps_datasource_dto_to_domain_and_passes_clock_now() = runTest {
        ds.createResult = validDto
        val r = repo().create("Test Crew", aid("owner"), "Owner")
        assertIs<Result.Ok<Crew>>(r)
        assertEquals(cid("c-1"), r.value.id)
        assertEquals("Test Crew", r.value.name)
        assertEquals(aid("owner"), r.value.ownerId)
        // Repository sources the timestamp from the injected Clock, not the datasource.
        assertEquals(nowMs, ds.lastCreate?.nowMs)
    }

    @Test
    fun create_classifies_CodeCollisionExhausted_as_Create_CodeCollisionRetriesExhausted() = runTest {
        ds.createThrows = CodeCollisionExhaustedException
        val r = repo().create("Test Crew", aid("owner"), "Owner")
        assertEquals(Result.failure(CrewError.Create.CodeCollisionRetriesExhausted), r)
    }

    @Test
    fun create_classifies_permission_denied_throwable_as_Backend_PermissionDenied() = runTest {
        ds.createThrows = RuntimeException("PERMISSION_DENIED: missing permissions")
        val r = repo().create("Test Crew", aid("owner"), "Owner")
        assertEquals(Result.failure(CrewError.Backend.PermissionDenied), r)
    }

    @Test
    fun create_classifies_unknown_throwable_as_Backend_Unavailable() = runTest {
        ds.createThrows = RuntimeException("boom")
        val r = repo().create("Test Crew", aid("owner"), "Owner")
        assertEquals(Result.failure(CrewError.Backend.Unavailable), r)
    }

    @Test
    fun create_propagates_mapper_failure_when_returned_dto_is_malformed() = runTest {
        // Datasource "succeeds" but returns an un-mappable DTO (missing id) → toDomain failure surfaces.
        ds.createResult = validDto.copy(id = null)
        val r = repo().create("Test Crew", aid("owner"), "Owner")
        assertEquals(Result.failure(CrewError.Backend.Unavailable), r)
    }

    // ---------------- joinByCode ----------------

    @Test
    fun joinByCode_maps_datasource_dto_to_domain() = runTest {
        ds.joinResult = validDto.copy(
            memberIds = listOf("owner", "joiner"),
            members = mapOf(
                "owner" to MemberDto(joinedAtEpochMs = nowMs),
                "joiner" to MemberDto(joinedAtEpochMs = nowMs),
            ),
        )
        val r = repo().joinByCode(code("ABCD23"), aid("joiner"), "Joiner")
        assertIs<Result.Ok<Crew>>(r)
        assertEquals(2, r.value.members.size)
        assertEquals(code("ABCD23"), ds.lastJoin?.code)
    }

    @Test
    fun joinByCode_classifies_CodeUnknown_as_Invite_CodeUnknown() = runTest {
        ds.joinThrows = CodeUnknownException
        val r = repo().joinByCode(code("ABCD23"), aid("joiner"), "Joiner")
        assertEquals(Result.failure(CrewError.Invite.CodeUnknown), r)
    }

    @Test
    fun joinByCode_classifies_NotFound_as_Membership_NotFound() = runTest {
        ds.joinThrows = NotFoundException
        val r = repo().joinByCode(code("ABCD23"), aid("joiner"), "Joiner")
        assertEquals(Result.failure(CrewError.Membership.NotFound), r)
    }

    @Test
    fun joinByCode_classifies_Full_as_Membership_Full() = runTest {
        // The authoritative cap is enforced atomically inside the Firestore transaction; the
        // datasource surfaces it as FullException. The repository must classify it as Membership.Full.
        // (The in-memory cap itself is covered by CrewTest / CrewSizeTest, and the transactional
        // cap by the firestore emulator harness — not unit-testable through this fake.)
        ds.joinThrows = FullException
        val r = repo().joinByCode(code("ABCD23"), aid("joiner"), "Joiner")
        assertEquals(Result.failure(CrewError.Membership.Full), r)
    }

    @Test
    fun joinByCode_classifies_AlreadyMember_as_Membership_AlreadyMember() = runTest {
        ds.joinThrows = AlreadyMemberException
        val r = repo().joinByCode(code("ABCD23"), aid("joiner"), "Joiner")
        assertEquals(Result.failure(CrewError.Membership.AlreadyMember), r)
    }

    @Test
    fun joinByCode_classifies_network_throwable_as_Backend_Network() = runTest {
        ds.joinThrows = RuntimeException("network unavailable")
        val r = repo().joinByCode(code("ABCD23"), aid("joiner"), "Joiner")
        assertEquals(Result.failure(CrewError.Backend.Network), r)
    }

    // ---------------- leave ----------------

    @Test
    fun leave_returns_success_and_forwards_args() = runTest {
        val r = repo().leave(cid("c-1"), aid("owner"))
        assertEquals(Result.success(Unit), r)
        assertEquals(cid("c-1") to aid("owner"), ds.lastLeave)
    }

    @Test
    fun leave_classifies_NotFound_as_Membership_NotFound() = runTest {
        ds.leaveThrows = NotFoundException
        val r = repo().leave(cid("c-1"), aid("owner"))
        assertEquals(Result.failure(CrewError.Membership.NotFound), r)
    }

    @Test
    fun leave_classifies_NotMember_as_Membership_NotMember() = runTest {
        ds.leaveThrows = NotMemberException
        val r = repo().leave(cid("c-1"), aid("stranger"))
        assertEquals(Result.failure(CrewError.Membership.NotMember), r)
    }

    @Test
    fun leave_classifies_unknown_throwable_as_Backend_Unavailable() = runTest {
        ds.leaveThrows = RuntimeException("boom")
        val r = repo().leave(cid("c-1"), aid("owner"))
        assertEquals(Result.failure(CrewError.Backend.Unavailable), r)
    }

    // ---------------- renameCrew ----------------

    @Test
    fun renameCrew_owner_fetches_then_writes_with_new_name() = runTest {
        ds.fetchOnceResult = crewOwnedBy("owner")
        val r = repo().renameCrew(cid("c-1"), aid("owner"), "New Name")
        assertEquals(Result.success(Unit), r)
        assertEquals(cid("c-1"), ds.lastFetchOnce)
        assertEquals(cid("c-1") to "New Name", ds.lastRename)
    }

    @Test
    fun renameCrew_returns_NotFound_when_crew_absent_and_does_not_write() = runTest {
        ds.fetchOnceResult = null
        val r = repo().renameCrew(cid("c-1"), aid("owner"), "New Name")
        assertEquals(Result.failure(CrewError.Membership.NotFound), r)
        assertNull(ds.lastRename)
    }

    @Test
    fun renameCrew_returns_NotOwner_when_requester_is_not_owner_and_does_not_write() = runTest {
        ds.fetchOnceResult = crewOwnedBy("owner")
        val r = repo().renameCrew(cid("c-1"), aid("intruder"), "New Name")
        assertEquals(Result.failure(CrewError.Authorization.NotOwner), r)
        assertNull(ds.lastRename)
    }

    @Test
    fun renameCrew_propagates_datasource_write_failure() = runTest {
        ds.fetchOnceResult = crewOwnedBy("owner")
        ds.renameResult = Result.failure(CrewError.Backend.Network)
        val r = repo().renameCrew(cid("c-1"), aid("owner"), "New Name")
        assertEquals(Result.failure(CrewError.Backend.Network), r)
    }

    // ---------------- deleteCrew ----------------

    @Test
    fun deleteCrew_owner_fetches_then_deletes_with_crew_code() = runTest {
        ds.fetchOnceResult = crewOwnedBy("owner")
        val r = repo().deleteCrew(cid("c-1"), aid("owner"))
        assertEquals(Result.success(Unit), r)
        assertEquals(cid("c-1"), ds.lastFetchOnce)
        // Repository passes the crew's own code (read from fetchOnce) into the delete.
        assertEquals(cid("c-1") to code("ABCD23"), ds.lastDelete)
    }

    @Test
    fun deleteCrew_returns_NotFound_when_crew_absent_and_does_not_delete() = runTest {
        ds.fetchOnceResult = null
        val r = repo().deleteCrew(cid("c-1"), aid("owner"))
        assertEquals(Result.failure(CrewError.Membership.NotFound), r)
        assertNull(ds.lastDelete)
    }

    @Test
    fun deleteCrew_returns_NotOwner_when_requester_is_not_owner_and_does_not_delete() = runTest {
        ds.fetchOnceResult = crewOwnedBy("owner")
        val r = repo().deleteCrew(cid("c-1"), aid("intruder"))
        assertEquals(Result.failure(CrewError.Authorization.NotOwner), r)
        assertNull(ds.lastDelete)
    }

    @Test
    fun deleteCrew_propagates_datasource_delete_failure() = runTest {
        ds.fetchOnceResult = crewOwnedBy("owner")
        ds.deleteResult = Result.failure(CrewError.Backend.PermissionDenied)
        val r = repo().deleteCrew(cid("c-1"), aid("owner"))
        assertEquals(Result.failure(CrewError.Backend.PermissionDenied), r)
    }

    // ---------------- observeCrew (single) ----------------

    @Test
    fun observeCrew_maps_present_dto_to_Ok() = runTest {
        ds.observeCrewFlow = flowOf(validDto)
        repo().observeCrew(cid("c-1")).test {
            val r = awaitItem()
            assertIs<Result.Ok<Crew>>(r)
            assertEquals(cid("c-1"), r.value.id)
            awaitComplete()
        }
    }

    @Test
    fun observeCrew_maps_null_dto_to_Membership_NotFound() = runTest {
        ds.observeCrewFlow = flowOf(null)
        repo().observeCrew(cid("c-1")).test {
            assertEquals(Result.failure(CrewError.Membership.NotFound), awaitItem())
            awaitComplete()
        }
    }

    // ---------------- observeMyCrews ----------------

    @Test
    fun observeMyCrews_maps_dtos_and_drops_unmappable_entries() = runTest {
        ds.observeMyCrewsFlow = flowOf(listOf(validDto, validDto.copy(id = null)))
        repo().observeMyCrews(aid("owner")).test {
            val r = awaitItem()
            assertIs<Result.Ok<List<Crew>>>(r)
            // The malformed DTO (null id) is silently dropped; only the valid one survives.
            assertEquals(1, r.value.size)
            assertEquals(cid("c-1"), r.value.first().id)
            awaitComplete()
        }
    }

    @Test
    fun observeMyCrews_emits_empty_list_when_none() = runTest {
        ds.observeMyCrewsFlow = flowOf(emptyList())
        repo().observeMyCrews(aid("owner")).test {
            val r = awaitItem()
            assertIs<Result.Ok<List<Crew>>>(r)
            assertTrue(r.value.isEmpty())
            awaitComplete()
        }
    }
}
