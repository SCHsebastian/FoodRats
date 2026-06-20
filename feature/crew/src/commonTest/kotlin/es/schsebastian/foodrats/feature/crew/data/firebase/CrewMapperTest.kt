package es.schsebastian.foodrats.feature.crew.data.firebase

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.model.Crew
import es.schsebastian.foodrats.feature.crew.domain.model.CrewCode
import es.schsebastian.foodrats.feature.crew.domain.model.CrewTagline
import es.schsebastian.foodrats.feature.crew.domain.model.Member
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CrewMapperTest {

    private fun aid(raw: String): AccountId = (AccountId.of(raw) as Result.Ok).value
    private fun cid(raw: String): CrewId = (CrewId.of(raw) as Result.Ok).value

    private val validDto = CrewDto(
        id = "c-1",
        name = "Test Crew",
        code = "ABCD23",
        ownerId = "uid-1",
        createdAtEpochMs = 1_700_000_000_000L,
        memberIds = listOf("uid-1", "uid-2"),
        members = mapOf(
            "uid-1" to MemberDto(joinedAtEpochMs = 1_700_000_000_000L),
            "uid-2" to MemberDto(joinedAtEpochMs = 1_700_000_500_000L),
        ),
    )

    @Test fun toDomain_succeeds_on_well_formed_dto() {
        val r = validDto.toDomain()
        assertIs<Result.Ok<Crew>>(r)
        val crew = r.value
        assertEquals(cid("c-1"), crew.id)
        assertEquals("Test Crew", crew.name)
        assertEquals("ABCD23", crew.code.value)
        assertEquals(aid("uid-1"), crew.ownerId)
        assertEquals(Instant.fromEpochMilliseconds(1_700_000_000_000L), crew.createdAt)
        assertEquals(2, crew.members.size)
        assertEquals(setOf(aid("uid-1"), aid("uid-2")), crew.members.map { it.accountId }.toSet())
    }

    @Test fun toDomain_fails_on_missing_id() {
        assertEquals(
            Result.failure(CrewError.Backend.Unavailable),
            validDto.copy(id = null).toDomain(),
        )
    }

    @Test fun toDomain_fails_on_malformed_code() {
        assertEquals(
            Result.failure(CrewError.Validation.CodeMalformed),
            validDto.copy(code = "xx").toDomain(),
        )
    }

    @Test fun toDomain_defaults_blindVoting_to_false_when_absent() {
        val r = validDto.toDomain()
        assertIs<Result.Ok<Crew>>(r)
        assertEquals(false, r.value.blindVoting)
    }

    @Test fun toDomain_carries_blindVoting_true_when_set() {
        val r = validDto.copy(blindVoting = true).toDomain()
        assertIs<Result.Ok<Crew>>(r)
        assertEquals(true, r.value.blindVoting)
    }

    @Test fun toDomain_skips_member_entries_with_missing_accountId_join_data() {
        // memberIds in but no matching members map entry — drop silently.
        val dto = validDto.copy(members = mapOf("uid-1" to MemberDto(joinedAtEpochMs = 1L)))
        val r = dto.toDomain()
        assertIs<Result.Ok<Crew>>(r)
        assertEquals(1, r.value.members.size)
    }

    @Test fun toDomain_defaults_tagline_to_null_when_absent() {
        val r = validDto.toDomain()
        assertIs<Result.Ok<Crew>>(r)
        assertEquals(null, r.value.tagline)
    }

    @Test fun toDomain_maps_tagline_when_set() {
        val r = validDto.copy(tagline = "only home-cooked").toDomain()
        assertIs<Result.Ok<Crew>>(r)
        assertEquals("only home-cooked", r.value.tagline?.value)
    }

    @Test fun toDomain_maps_tagline_at_max_length() {
        val maxTagline = "x".repeat(CrewTagline.MAX_LEN)
        val r = validDto.copy(tagline = maxTagline).toDomain()
        assertIs<Result.Ok<Crew>>(r)
        assertEquals(maxTagline, r.value.tagline?.value)
    }

    @Test fun toDomain_silently_drops_malformed_tagline() {
        // A tagline that's too long (shouldn't happen for well-formed data, but tolerant read).
        val overlong = "x".repeat(CrewTagline.MAX_LEN + 1)
        val r = validDto.copy(tagline = overlong).toDomain()
        assertIs<Result.Ok<Crew>>(r)
        assertEquals(null, r.value.tagline)   // silently ignored
    }
}
