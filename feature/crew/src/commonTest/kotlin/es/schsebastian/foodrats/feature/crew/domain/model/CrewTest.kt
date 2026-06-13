package es.schsebastian.foodrats.feature.crew.domain.model

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class CrewTest {

    private val code = (CrewCode.of("ABCD23") as Result.Ok).value
    private val owner = account("owner")
    private val now = Instant.fromEpochMilliseconds(0)

    private fun account(id: String) = (AccountId.of(id) as Result.Ok).value
    private fun member(id: String) = Member(accountId = account(id), joinedAt = now)

    private fun crewOf(memberIds: List<String>) = Crew.of(
        id = (CrewId.of("crew-1") as Result.Ok).value,
        name = "Crew",
        code = code,
        ownerId = owner,
        createdAt = now,
        members = memberIds.map { member(it) },
    )

    @Test fun addMember_below_cap_succeeds() {
        val crew = crewOf(listOf("a", "b"))
        val result = crew.addMember(member("c"))
        assertTrue(result is Result.Ok)
        assertEquals(3, result.value.size)
    }

    @Test fun addMember_at_cap_minus_one_succeeds() {
        val crew = crewOf(listOf("a", "b", "c", "d", "e", "f", "g"))
        val result = crew.addMember(member("h"))
        assertTrue(result is Result.Ok)
        assertEquals(CrewSize.MAX, result.value.size)
    }

    @Test fun addMember_at_cap_fails_with_Full() {
        val crew = crewOf(listOf("a", "b", "c", "d", "e", "f", "g", "h"))
        assertEquals(CrewSize.MAX, crew.size)
        val result = crew.addMember(member("i"))
        assertEquals(Result.failure(CrewError.Membership.Full), result)
    }

    @Test fun addMember_existing_member_fails_with_AlreadyMember() {
        val crew = crewOf(listOf("a", "b"))
        val result = crew.addMember(member("a"))
        assertEquals(Result.failure(CrewError.Membership.AlreadyMember), result)
    }
}
