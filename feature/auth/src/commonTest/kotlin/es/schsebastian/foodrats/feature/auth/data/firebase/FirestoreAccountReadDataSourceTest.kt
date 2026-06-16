package es.schsebastian.foodrats.feature.auth.data.firebase

import app.cash.turbine.test
import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.image.ImageUrlError
import es.schsebastian.foodrats.core.domain.image.ImageUrlPort
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FirestoreAccountReadDataSourceTest {
    private val crew = (CrewId.of("c1") as Result.Ok).value

    /** Resolves each avatar path to a deterministic `signed://{path}` URL. */
    private class FakeImageUrls(var fail: Boolean = false) : ImageUrlPort {
        override suspend fun resolve(
            crewId: CrewId,
            paths: List<String>,
        ): Result<Map<String, String>, ImageUrlError> =
            if (fail) Result.failure(ImageUrlError.Unavailable)
            else Result.success(paths.associateWith { "signed://$it" })
    }

    private class FakeActiveCrew(crewId: CrewId?) : ActiveCrewProvider {
        override val current = MutableStateFlow(crewId)
        override suspend fun set(crewId: CrewId) { current.value = crewId }
        override suspend fun clear() { current.value = null }
    }

    private val source = FakeAccountSource()
    private val imageUrls = FakeImageUrls()
    private val sut = FirestoreAccountReadDataSource(source, imageUrls, FakeActiveCrew(crew))
    private val id = (AccountId.of("user-1") as Result.Ok).value

    @Test fun emits_null_when_doc_missing() = runTest {
        sut.observe(id).test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun maps_dto_to_account_and_resolves_avatar_path_to_signed_url() = runTest {
        sut.observe(id).test {
            assertNull(awaitItem())
            source.emit("user-1", AccountDto(id = "user-1", displayName = "Sebas", avatarPath = "avatars/user-1.jpg"))
            val acc = awaitItem()
            assertEquals("Sebas", acc?.displayName)
            assertEquals("signed://avatars/user-1.jpg", acc?.avatarUrl)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun avatar_is_null_when_path_absent() = runTest {
        sut.observe(id).test {
            assertNull(awaitItem())
            source.emit("user-1", AccountDto(id = "user-1", displayName = "NoPic"))
            val acc = awaitItem()
            assertEquals("NoPic", acc?.displayName)
            assertNull(acc?.avatarUrl)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun avatar_is_null_when_resolution_fails() = runTest {
        val failing = FirestoreAccountReadDataSource(source, FakeImageUrls(fail = true), FakeActiveCrew(crew))
        failing.observe(id).test {
            assertNull(awaitItem())
            source.emit("user-1", AccountDto(id = "user-1", displayName = "Sebas", avatarPath = "avatars/user-1.jpg"))
            assertNull(awaitItem()?.avatarUrl)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun avatar_is_null_when_no_active_crew() = runTest {
        val noCrew = FirestoreAccountReadDataSource(source, imageUrls, FakeActiveCrew(null))
        noCrew.observe(id).test {
            assertNull(awaitItem())
            source.emit("user-1", AccountDto(id = "user-1", displayName = "Sebas", avatarPath = "avatars/user-1.jpg"))
            assertNull(awaitItem()?.avatarUrl)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun re_emits_on_doc_update() = runTest {
        sut.observe(id).test {
            assertNull(awaitItem())
            source.emit("user-1", AccountDto(id = "user-1", displayName = "Old"))
            assertEquals("Old", awaitItem()?.displayName)
            source.emit("user-1", AccountDto(id = "user-1", displayName = "New"))
            assertEquals("New", awaitItem()?.displayName)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
