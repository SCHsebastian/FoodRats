package es.schsebastian.foodrats.feature.crew.domain.usecase

import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.model.Crew
import es.schsebastian.foodrats.feature.crew.domain.model.CrewCode
import es.schsebastian.foodrats.feature.crew.domain.model.Member
import es.schsebastian.foodrats.feature.crew.domain.test.FakeCrewRepository
import es.schsebastian.foodrats.feature.crew.domain.test.aid
import es.schsebastian.foodrats.feature.crew.domain.test.cid
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CreateCrewUseCaseTest {

    private val founderId = aid("uid-founder")
    private val sampleCrew = Crew(
        id = cid("c-1"),
        name = "Test Crew",
        code = (CrewCode.of("ABCD23") as Result.Ok).value,
        ownerId = founderId,
        createdAt = Instant.fromEpochMilliseconds(0L),
        members = listOf(
            Member(founderId, Instant.fromEpochMilliseconds(0L)),
        ),
    )

    @Test fun blank_name_returns_NameBlank() = runTest {
        val repo = FakeCrewRepository()
        val r = CreateCrewUseCase(repo).invoke("   ", founderId, "Sam")
        assertEquals(Result.failure(CrewError.Validation.NameBlank), r)
    }

    @Test fun overlong_name_returns_NameTooLong() = runTest {
        val repo = FakeCrewRepository()
        val r = CreateCrewUseCase(repo).invoke("x".repeat(41), founderId, "Sam")
        assertEquals(Result.failure(CrewError.Validation.NameTooLong), r)
    }

    @Test fun valid_name_delegates_to_repo() = runTest {
        val repo = FakeCrewRepository().apply { nextCreate = Result.success(sampleCrew) }
        val r = CreateCrewUseCase(repo).invoke("  Test Crew  ", founderId, "Sam")
        assertIs<Result.Ok<Crew>>(r)
        assertEquals(sampleCrew, r.value)
    }
}
