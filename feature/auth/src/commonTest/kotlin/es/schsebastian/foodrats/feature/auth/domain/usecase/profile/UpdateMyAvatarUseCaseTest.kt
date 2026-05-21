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

class UpdateMyAvatarUseCaseTest {
    private val accountId = (AccountId.of("u1") as Result.Ok).value
    private val crewId = (CrewId.of("c1") as Result.Ok).value
    private val session = Session(accountId, crewId)

    @Test
    fun rejects_empty_bytes() = runTest {
        val r = UpdateMyAvatarUseCase(
            FakeAccountWritePort(), FakeCrewMemberCacheWritePort(),
            FixedSessionProvider(session), RecordingCrashReporter(),
        ).invoke(ByteArray(0))
        assertTrue(r is Result.Err)
        assertEquals(ProfileError.Validation.EmptyBytes, r.error)
    }

    @Test
    fun signed_out_returns_session_error() = runTest {
        val r = UpdateMyAvatarUseCase(
            FakeAccountWritePort(), FakeCrewMemberCacheWritePort(),
            FixedSessionProvider(null), RecordingCrashReporter(),
        ).invoke(ByteArray(100) { 1 })
        assertTrue(r is Result.Err)
        assertEquals(ProfileError.Session.SignedOut, r.error)
    }

    @Test
    fun success_returns_url_and_writes_cache() = runTest {
        val acc = FakeAccountWritePort().also { it.nextAvatarUrl = "https://example.test/avatar.jpg" }
        val cache = FakeCrewMemberCacheWritePort()
        val uc = UpdateMyAvatarUseCase(acc, cache, FixedSessionProvider(session), RecordingCrashReporter())

        val r = uc.invoke(ByteArray(100) { 1 })
        assertTrue(r is Result.Ok)
        assertEquals("https://example.test/avatar.jpg", r.value)
        assertEquals(listOf(Triple(crewId, accountId, "https://example.test/avatar.jpg")), cache.setAvatarUrlCalls)
    }

    @Test
    fun upload_failure_short_circuits() = runTest {
        val acc = FakeAccountWritePort().also { it.nextAvatarError = AccountWriteError.Backend.Unavailable }
        val cache = FakeCrewMemberCacheWritePort()
        val uc = UpdateMyAvatarUseCase(acc, cache, FixedSessionProvider(session), RecordingCrashReporter())

        val r = uc.invoke(ByteArray(100) { 1 })
        assertTrue(r is Result.Err)
        assertEquals(ProfileError.Backend.Unavailable, r.error)
        assertTrue(cache.setAvatarUrlCalls.isEmpty())
    }

    @Test
    fun cache_write_failure_swallowed_and_logged() = runTest {
        val acc = FakeAccountWritePort().also { it.nextAvatarUrl = "https://example.test/avatar.jpg" }
        val cache = FakeCrewMemberCacheWritePort().also { it.nextAvatarUrlError = CrewMemberCacheWriteError.Backend.Unavailable }
        val crash = RecordingCrashReporter()
        val uc = UpdateMyAvatarUseCase(acc, cache, FixedSessionProvider(session), crash)

        val r = uc.invoke(ByteArray(100) { 1 })
        assertTrue(r is Result.Ok)
        assertEquals(1, crash.logs.size)
        assertTrue(crash.logs.first().contains("setAvatarUrl"))
    }
}
