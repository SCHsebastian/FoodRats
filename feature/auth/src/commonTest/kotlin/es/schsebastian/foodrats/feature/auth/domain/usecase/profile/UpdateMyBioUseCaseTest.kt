package es.schsebastian.foodrats.feature.auth.domain.usecase.profile

import es.schsebastian.foodrats.core.domain.account.AccountWriteError
import es.schsebastian.foodrats.core.domain.account.Bio
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.feature.auth.domain.error.ProfileError
import es.schsebastian.foodrats.feature.auth.testdoubles.FakeAccountWritePort
import es.schsebastian.foodrats.feature.auth.testdoubles.FixedSessionProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class UpdateMyBioUseCaseTest {

    private val accountId = (AccountId.of("u1") as Result.Ok).value
    private val session = FixedSessionProvider(Session(accountId = accountId, activeCrewId = null))

    @Test fun valid_bio_persists_and_returns_ok() = runTest {
        val write = FakeAccountWritePort()
        val uc = UpdateMyBioUseCase(write, session)

        val result = uc("Home cook from Barcelona")

        assertIs<Result.Ok<Unit>>(result)
        assertEquals(1, write.bioCalls.size)
        val (calledId, calledBio) = write.bioCalls.first()
        assertEquals(accountId, calledId)
        assertEquals("Home cook from Barcelona", calledBio?.value)
    }

    @Test fun blank_bio_persists_null_and_returns_ok() = runTest {
        val write = FakeAccountWritePort()
        val uc = UpdateMyBioUseCase(write, session)

        val result = uc("   ")   // whitespace-only = clear

        assertIs<Result.Ok<Unit>>(result)
        assertEquals(1, write.bioCalls.size)
        assertNull(write.bioCalls.first().second)  // null = clear
    }

    @Test fun bio_exceeds_cap_returns_bio_too_long_without_calling_repo() = runTest {
        val write = FakeAccountWritePort()
        val uc = UpdateMyBioUseCase(write, session)

        val result = uc("a".repeat(Bio.MAX_LENGTH + 1))

        assertIs<Result.Err<ProfileError>>(result)
        assertEquals(ProfileError.Validation.BioTooLong, result.error)
        assertEquals(0, write.bioCalls.size)
    }

    @Test fun bio_exactly_at_cap_persists_ok() = runTest {
        val write = FakeAccountWritePort()
        val uc = UpdateMyBioUseCase(write, session)

        val atCap = "b".repeat(Bio.MAX_LENGTH)
        val result = uc(atCap)

        assertIs<Result.Ok<Unit>>(result)
        assertEquals(atCap, write.bioCalls.first().second?.value)
    }

    @Test fun backend_failure_maps_to_backend_unavailable() = runTest {
        val write = FakeAccountWritePort().also {
            it.nextBioError = AccountWriteError.Backend.Unavailable
        }
        val uc = UpdateMyBioUseCase(write, session)

        val result = uc("Nice bio")

        assertIs<Result.Err<ProfileError>>(result)
        assertEquals(ProfileError.Backend.Unavailable, result.error)
    }

    @Test fun bio_is_trimmed_before_persisting() = runTest {
        val write = FakeAccountWritePort()
        val uc = UpdateMyBioUseCase(write, session)

        val result = uc("  Trimmed bio  ")

        assertIs<Result.Ok<Unit>>(result)
        assertEquals("Trimmed bio", write.bioCalls.first().second?.value)
    }
}
