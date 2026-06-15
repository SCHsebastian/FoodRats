package es.schsebastian.foodrats.feature.meal.data.repository

import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.meal.CommentError
import es.schsebastian.foodrats.core.domain.meal.CommentText
import es.schsebastian.foodrats.core.domain.meal.MealCommentId
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.time.FixedClock
import es.schsebastian.foodrats.feature.meal.data.firebase.CommentDto
import es.schsebastian.foodrats.feature.meal.data.firebase.CommentFirestore
import es.schsebastian.foodrats.feature.meal.data.firebase.MealAuthorIdentity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Behavioral fake [CommentFirestore]: an in-memory ordered list of `comments/{id}` docs per meal,
 * plus an optional throwable to exercise the vendor-fault → typed-error seam. Mirrors
 * [FirebaseReactionRepositoryTest]'s FakeReactionFirestore.
 */
private class FakeCommentFirestore(
    var failWith: Throwable? = null,
) : CommentFirestore {
    val docs: MutableStateFlow<List<CommentDto>> = MutableStateFlow(emptyList())
    var lastDeletedId: String? = null
    private var nextId = 0

    override fun observe(crewId: CrewId, mealId: MealId): Flow<List<CommentDto>> =
        flow {
            failWith?.let { throw it }
            docs.collect { emit(it) }
        }

    override suspend fun create(crewId: CrewId, mealId: MealId, dto: CommentDto) {
        failWith?.let { throw it }
        docs.value = docs.value + dto.copy(id = dto.id ?: "doc-${nextId++}")
    }

    override suspend fun delete(crewId: CrewId, mealId: MealId, commentId: String) {
        failWith?.let { throw it }
        lastDeletedId = commentId
        docs.value = docs.value.filterNot { it.id == commentId }
    }
}

/**
 * Fake [MealAuthorIdentity]: `null` models "no live auth token", non-null models a signed-in user.
 * The comment repository only reads `uid`, so display fields are left blank.
 */
private class FakeAuthorIdentity(private val uid: String?) : MealAuthorIdentity {
    override fun current(): MealAuthorIdentity.Author? =
        uid?.let { MealAuthorIdentity.Author(uid = it, displayName = null, avatarUrl = null) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseCommentRepositoryTest {
    private val crewId = (CrewId.of("crew1") as Result.Ok).value
    private val mealId = (MealId.of("meal1") as Result.Ok).value
    private val text = (CommentText.of("hola crew") as Result.Ok).value

    private fun repository(
        fake: FakeCommentFirestore,
        authorIdentity: MealAuthorIdentity = FakeAuthorIdentity("uid-author"),
    ): FirebaseCommentRepository {
        val testDispatcher = UnconfinedTestDispatcher()
        val dispatchers = object : DispatcherProvider {
            override val main: CoroutineDispatcher = testDispatcher
            override val default: CoroutineDispatcher = testDispatcher
            override val io: CoroutineDispatcher = testDispatcher
        }
        return FirebaseCommentRepository(
            ds = fake,
            authorIdentity = authorIdentity,
            clock = FixedClock(Instant.parse("2026-06-14T12:00:00Z")),
            dispatchers = dispatchers,
        )
    }

    // --- observe ---------------------------------------------------------------------------------

    @Test fun observe_maps_dtos_to_domain_in_order() = runTest {
        val fake = FakeCommentFirestore()
        fake.docs.value = listOf(
            CommentDto(id = "c1", authorId = "uid-a", text = "first", createdAtEpochMs = 1L),
            CommentDto(id = "c2", authorId = "uid-b", text = "second", createdAtEpochMs = 2L),
        )
        val repo = repository(fake)

        val emitted = repo.observe(crewId, mealId).first()
        assertTrue(emitted is Result.Ok)
        val comments = (emitted as Result.Ok).value
        assertEquals(2, comments.size)
        assertEquals("c1", comments[0].id.value)
        assertEquals("first", comments[0].text.value)
        assertEquals("c2", comments[1].id.value)
    }

    @Test fun observe_drops_malformed_docs() = runTest {
        val fake = FakeCommentFirestore()
        fake.docs.value = listOf(
            CommentDto(id = "ok", authorId = "uid-a", text = "valid", createdAtEpochMs = 1L),
            // Blank text fails CommentText.of → toDomain() returns a failure → dropped.
            CommentDto(id = "bad-text", authorId = "uid-b", text = "   ", createdAtEpochMs = 2L),
            // Missing id → dropped.
            CommentDto(id = null, authorId = "uid-c", text = "no id", createdAtEpochMs = 3L),
        )
        val repo = repository(fake)

        val comments = (repo.observe(crewId, mealId).first() as Result.Ok).value
        assertEquals(1, comments.size)
        assertEquals("ok", comments[0].id.value)
    }

    @Test fun observe_failure_maps_to_unavailable() = runTest {
        val fake = FakeCommentFirestore(failWith = RuntimeException("network unreachable"))
        val repo = repository(fake)

        val emitted = repo.observe(crewId, mealId).first()
        assertTrue(emitted is Result.Err)
        assertEquals(CommentError.Read.Unavailable, (emitted as Result.Err).error)
    }

    // --- post ------------------------------------------------------------------------------------

    @Test fun post_creates_doc_with_author_uid_and_text() = runTest {
        val fake = FakeCommentFirestore()
        val repo = repository(fake, FakeAuthorIdentity("uid-author"))

        val r = repo.post(crewId, mealId, text)
        assertTrue(r is Result.Ok)
        assertEquals(1, fake.docs.value.size)
        val created = fake.docs.value.single()
        assertEquals("uid-author", created.authorId)
        assertEquals("hola crew", created.text)
        assertEquals(Instant.parse("2026-06-14T12:00:00Z").toEpochMilliseconds(), created.createdAtEpochMs)
    }

    @Test fun post_without_auth_returns_unauthorized_and_writes_nothing() = runTest {
        val fake = FakeCommentFirestore()
        val repo = repository(fake, FakeAuthorIdentity(uid = null))

        val r = repo.post(crewId, mealId, text)
        assertTrue(r is Result.Err)
        assertEquals(CommentError.Write.Unauthorized, (r as Result.Err).error)
        assertTrue(fake.docs.value.isEmpty())
    }

    @Test fun post_permission_denied_maps_to_unauthorized() = runTest {
        val fake = FakeCommentFirestore(failWith = RuntimeException("PERMISSION_DENIED: nope"))
        val repo = repository(fake)

        val r = repo.post(crewId, mealId, text)
        assertTrue(r is Result.Err)
        assertEquals(CommentError.Write.Unauthorized, (r as Result.Err).error)
    }

    @Test fun post_unauthenticated_maps_to_unauthorized() = runTest {
        val fake = FakeCommentFirestore(failWith = RuntimeException("UNAUTHENTICATED token expired"))
        val repo = repository(fake)

        val r = repo.post(crewId, mealId, text)
        assertTrue(r is Result.Err)
        assertEquals(CommentError.Write.Unauthorized, (r as Result.Err).error)
    }

    @Test fun post_other_fault_maps_to_unavailable() = runTest {
        val fake = FakeCommentFirestore(failWith = RuntimeException("network unreachable"))
        val repo = repository(fake)

        val r = repo.post(crewId, mealId, text)
        assertTrue(r is Result.Err)
        assertEquals(CommentError.Write.Unavailable, (r as Result.Err).error)
    }

    // CommentError.Write.Blank / TooLong are deliberately NOT exercised here: they are produced by
    // CommentText.of pre-validation (in MealDetailViewModel), never mapped from a Firestore fault.
    // The repository only ever receives an already-valid CommentText, so it cannot return them.

    // --- delete ----------------------------------------------------------------------------------

    @Test fun delete_removes_doc() = runTest {
        val fake = FakeCommentFirestore()
        fake.docs.value = listOf(
            CommentDto(id = "c1", authorId = "uid-a", text = "x", createdAtEpochMs = 1L),
        )
        val repo = repository(fake)

        val r = repo.delete(crewId, mealId, MealCommentId("c1"))
        assertTrue(r is Result.Ok)
        assertEquals("c1", fake.lastDeletedId)
        assertTrue(fake.docs.value.isEmpty())
    }

    @Test fun delete_without_auth_returns_not_author_or_owner_and_deletes_nothing() = runTest {
        val fake = FakeCommentFirestore()
        fake.docs.value = listOf(
            CommentDto(id = "c1", authorId = "uid-a", text = "x", createdAtEpochMs = 1L),
        )
        val repo = repository(fake, FakeAuthorIdentity(uid = null))

        val r = repo.delete(crewId, mealId, MealCommentId("c1"))
        assertTrue(r is Result.Err)
        assertEquals(CommentError.Delete.NotAuthorOrOwner, (r as Result.Err).error)
        assertNull(fake.lastDeletedId)
        assertEquals(1, fake.docs.value.size)
    }

    @Test fun delete_permission_denied_maps_to_not_author_or_owner() = runTest {
        val fake = FakeCommentFirestore(failWith = RuntimeException("PERMISSION_DENIED: nope"))
        val repo = repository(fake)

        val r = repo.delete(crewId, mealId, MealCommentId("c1"))
        assertTrue(r is Result.Err)
        assertEquals(CommentError.Delete.NotAuthorOrOwner, (r as Result.Err).error)
    }

    @Test fun delete_not_found_maps_to_not_found() = runTest {
        val fake = FakeCommentFirestore(failWith = RuntimeException("not-found: no such comment"))
        val repo = repository(fake)

        val r = repo.delete(crewId, mealId, MealCommentId("c1"))
        assertTrue(r is Result.Err)
        assertEquals(CommentError.Delete.NotFound, (r as Result.Err).error)
    }

    @Test fun delete_other_fault_maps_to_unavailable() = runTest {
        val fake = FakeCommentFirestore(failWith = RuntimeException("network unreachable"))
        val repo = repository(fake)

        val r = repo.delete(crewId, mealId, MealCommentId("c1"))
        assertTrue(r is Result.Err)
        assertEquals(CommentError.Delete.Unavailable, (r as Result.Err).error)
    }
}
