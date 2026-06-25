package es.schsebastian.foodrats.feature.auth.data.firebase

import app.cash.turbine.test
import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.image.ImageUrlError
import es.schsebastian.foodrats.core.domain.image.ImageUrlPort
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.feature.auth.testdoubles.FixedSessionProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FirestoreAccountReadDataSourceTest {
    private val crew = (CrewId.of("c1") as Result.Ok).value
    private val id = (AccountId.of("user-1") as Result.Ok).value
    private val otherId = (AccountId.of("viewer") as Result.Ok).value

    /**
     * Resolves crew-scoped paths to `signed://{path}` and own-avatar paths to `self://{path}` so
     * tests can tell the two resolution routes apart.
     */
    private class FakeImageUrls(var fail: Boolean = false) : ImageUrlPort {
        override suspend fun resolve(
            crewId: CrewId,
            paths: List<String>,
        ): Result<Map<String, String>, ImageUrlError> =
            if (fail) Result.failure(ImageUrlError.Unavailable)
            else Result.success(paths.associateWith { "signed://$it" })

        override suspend fun resolveOwnAvatar(path: String): Result<String?, ImageUrlError> =
            if (fail) Result.failure(ImageUrlError.Unavailable)
            else Result.success("self://$path")
    }

    private class FakeActiveCrew(crewId: CrewId?) : ActiveCrewProvider {
        override val current = MutableStateFlow(crewId)
        override suspend fun set(crewId: CrewId) { current.value = crewId }
        override suspend fun clear() { current.value = null }
    }

    private val source = FakeAccountSource()
    private val imageUrls = FakeImageUrls()

    /** Default: the viewer is someone OTHER than the observed account, with an active crew. */
    private fun sut(
        imageUrls: ImageUrlPort = this.imageUrls,
        crewId: CrewId? = crew,
        viewerId: AccountId = otherId,
    ) = FirestoreAccountReadDataSource(
        source,
        imageUrls,
        FakeActiveCrew(crewId),
        FixedSessionProvider(Session(accountId = viewerId, activeCrewId = crewId)),
    )

    @Test fun emits_null_when_doc_missing() = runTest {
        sut().observe(id).test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun maps_dto_to_account_and_resolves_avatar_path_to_signed_url() = runTest {
        sut().observe(id).test {
            assertNull(awaitItem())
            source.emit("user-1", AccountDto(id = "user-1", displayName = "Sebas", avatarPath = "avatars/user-1.jpg"))
            val acc = awaitItem()
            assertEquals("Sebas", acc?.displayName)
            assertEquals("signed://avatars/user-1.jpg", acc?.avatarUrl)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun avatar_is_null_when_path_absent() = runTest {
        sut().observe(id).test {
            assertNull(awaitItem())
            source.emit("user-1", AccountDto(id = "user-1", displayName = "NoPic"))
            val acc = awaitItem()
            assertEquals("NoPic", acc?.displayName)
            assertNull(acc?.avatarUrl)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun avatar_is_null_when_resolution_fails() = runTest {
        sut(imageUrls = FakeImageUrls(fail = true)).observe(id).test {
            assertNull(awaitItem())
            source.emit("user-1", AccountDto(id = "user-1", displayName = "Sebas", avatarPath = "avatars/user-1.jpg"))
            assertNull(awaitItem()?.avatarUrl)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun other_users_avatar_is_null_without_active_crew() = runTest {
        sut(crewId = null).observe(id).test {
            assertNull(awaitItem())
            source.emit("user-1", AccountDto(id = "user-1", displayName = "Sebas", avatarPath = "avatars/user-1/abc.jpg"))
            assertNull(awaitItem()?.avatarUrl)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun own_avatar_resolves_without_active_crew() = runTest {
        // Viewer IS the observed account and has no crew yet: the own-avatar path must still resolve.
        sut(crewId = null, viewerId = id).observe(id).test {
            assertNull(awaitItem())
            source.emit("user-1", AccountDto(id = "user-1", displayName = "Sebas", avatarPath = "avatars/user-1/abc.jpg"))
            assertEquals("self://avatars/user-1/abc.jpg", awaitItem()?.avatarUrl)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun re_emits_on_doc_update() = runTest {
        sut().observe(id).test {
            assertNull(awaitItem())
            source.emit("user-1", AccountDto(id = "user-1", displayName = "Old"))
            assertEquals("Old", awaitItem()?.displayName)
            source.emit("user-1", AccountDto(id = "user-1", displayName = "New"))
            assertEquals("New", awaitItem()?.displayName)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
