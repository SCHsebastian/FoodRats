package es.schsebastian.foodrats.feature.auth.domain.usecase.profile

import es.schsebastian.foodrats.core.domain.account.AccountWriteError
import es.schsebastian.foodrats.core.domain.crew.CrewMemberCacheWriteError
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.feature.auth.domain.error.ProfileError
import es.schsebastian.foodrats.feature.auth.testdoubles.FakeAccountWritePort
import es.schsebastian.foodrats.feature.auth.testdoubles.FakeCrewMemberCacheWritePort
import es.schsebastian.foodrats.feature.auth.testdoubles.FixedSessionProvider
import es.schsebastian.foodrats.feature.auth.testdoubles.RecordingCrashReporter
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UpdateMyDisplayNameUseCaseTest {
    private val accountId = (AccountId.of("u1") as Result.Ok).value
    private val crewId = (CrewId.of("c1") as Result.Ok).value
    private val signedIn = Session(accountId, crewId)

    @Test
    fun rejects_blank_name() = runTest {
        val r = build(signedIn).invoke("   ")
        assertTrue(r is Result.Err)
        assertEquals(ProfileError.Validation.DisplayNameBlank, r.error)
    }

    @Test
    fun rejects_too_long_name() = runTest {
        val r = build(signedIn).invoke("x".repeat(41))
        assertTrue(r is Result.Err)
        assertEquals(ProfileError.Validation.DisplayNameTooLong, r.error)
    }

    @Test
    fun signed_out_returns_session_error() = runTest {
        val r = build(session = null).invoke("Alice")
        assertTrue(r is Result.Err)
        assertEquals(ProfileError.Session.SignedOut, r.error)
    }

    @Test
    fun success_writes_account_then_cache() = runTest {
        val acc = FakeAccountWritePort()
        val cache = FakeCrewMemberCacheWritePort()
        val crash = RecordingCrashReporter()
        val uc = UpdateMyDisplayNameUseCase(acc, cache, FixedSessionProvider(signedIn), crash)

        val r = uc.invoke("Alice")
        assertTrue(r is Result.Ok)
        assertEquals(listOf(accountId to "Alice"), acc.displayNameCalls)
        assertEquals(listOf(Triple(crewId, accountId, "Alice")), cache.setDisplayNameCalls)
        assertTrue(crash.logs.isEmpty())
    }

    @Test
    fun success_with_no_active_crew_skips_cache() = runTest {
        val acc = FakeAccountWritePort()
        val cache = FakeCrewMemberCacheWritePort()
        val uc = UpdateMyDisplayNameUseCase(
            acc, cache,
            FixedSessionProvider(Session(accountId, null)),
            RecordingCrashReporter(),
        )

        val r = uc.invoke("Alice")
        assertTrue(r is Result.Ok)
        assertEquals(1, acc.displayNameCalls.size)
        assertTrue(cache.setDisplayNameCalls.isEmpty())
    }

    @Test
    fun account_write_failure_short_circuits() = runTest {
        val acc = FakeAccountWritePort().also { it.nextDisplayNameError = AccountWriteError.Backend.Unavailable }
        val cache = FakeCrewMemberCacheWritePort()
        val uc = UpdateMyDisplayNameUseCase(acc, cache, FixedSessionProvider(signedIn), RecordingCrashReporter())

        val r = uc.invoke("Alice")
        assertTrue(r is Result.Err)
        assertEquals(ProfileError.Backend.Unavailable, r.error)
        assertTrue(cache.setDisplayNameCalls.isEmpty())
    }

    @Test
    fun cache_write_failure_swallowed_and_logged() = runTest {
        val acc = FakeAccountWritePort()
        val cache = FakeCrewMemberCacheWritePort().also { it.nextDisplayNameError = CrewMemberCacheWriteError.Backend.Unavailable }
        val crash = RecordingCrashReporter()
        val uc = UpdateMyDisplayNameUseCase(acc, cache, FixedSessionProvider(signedIn), crash)

        val r = uc.invoke("Alice")
        assertTrue(r is Result.Ok)
        assertEquals(1, crash.logs.size)
        assertTrue(crash.logs.first().contains("setDisplayName"))
        assertTrue(crash.logs.first().contains(crewId.value))
        assertTrue(crash.logs.first().contains(accountId.value))
    }

    private fun build(session: Session?): UpdateMyDisplayNameUseCase =
        UpdateMyDisplayNameUseCase(
            FakeAccountWritePort(),
            FakeCrewMemberCacheWritePort(),
            FixedSessionProvider(session),
            RecordingCrashReporter(),
        )
}
