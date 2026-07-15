package es.schsebastian.foodrats.feature.feed.domain.usecase

import es.schsebastian.foodrats.core.domain.meal.CommentError
import es.schsebastian.foodrats.core.domain.meal.CommentText
import es.schsebastian.foodrats.core.domain.meal.MealComment
import es.schsebastian.foodrats.core.domain.meal.MealCommentId
import es.schsebastian.foodrats.core.domain.meal.MealCommentPort
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.outbox.PendingCommand
import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class EditRecordingCommentPort : MealCommentPort {
    data class EditCall(val crewId: String, val mealId: String, val commentId: String, val text: String)
    val editCalls = mutableListOf<EditCall>()
    var nextEdit: Result<Unit, CommentError.Edit> = Result.success(Unit)

    override fun observe(crewId: CrewId, mealId: MealId, limit: Int): Flow<Result<List<MealComment>, CommentError.Read>> =
        flowOf(Result.success(emptyList()))

    override suspend fun post(
        crewId: CrewId,
        mealId: MealId,
        commentId: MealCommentId,
        text: CommentText,
        mentions: List<AccountId>,
    ): Result<Unit, CommentError.Write> = Result.success(Unit)

    override suspend fun edit(
        crewId: CrewId,
        mealId: MealId,
        commentId: MealCommentId,
        text: CommentText,
        mentions: List<AccountId>,
    ): Result<Unit, CommentError.Edit> {
        editCalls += EditCall(crewId.value, mealId.value, commentId.value, text.value)
        return nextEdit
    }

    override suspend fun delete(
        crewId: CrewId,
        mealId: MealId,
        commentId: MealCommentId,
    ): Result<Unit, CommentError.Delete> = Result.success(Unit)
}

class EditCommentUseCaseTest {
    private val crew = (CrewId.of("crew-1") as Result.Ok).value
    private val mealId = (MealId.of("meal-1") as Result.Ok).value
    private val commentId = MealCommentId("cmt-1")
    private val text = (CommentText.of("edited words") as Result.Ok).value

    private fun useCase(
        port: EditRecordingCommentPort = EditRecordingCommentPort(),
        connectivity: FakeConnectivityPort = FakeConnectivityPort(online = true),
        outbox: RecordingOutboxPort = RecordingOutboxPort(),
    ) = EditCommentUseCase(port, connectivity, outbox)

    @Test fun online_delegates_to_port_and_leaves_outbox_untouched() = runTest {
        val port = EditRecordingCommentPort()
        val outbox = RecordingOutboxPort()
        val r = useCase(port = port, outbox = outbox)(crew, mealId, commentId, text)
        assertTrue(r is Result.Ok)
        assertEquals(1, port.editCalls.size)
        assertTrue(outbox.enqueued.isEmpty())
    }

    @Test fun offline_enqueues_and_returns_ok_without_calling_port() = runTest {
        val port = EditRecordingCommentPort()
        val outbox = RecordingOutboxPort()
        val r = useCase(
            port = port,
            connectivity = FakeConnectivityPort(online = false),
            outbox = outbox,
        )(crew, mealId, commentId, text)
        assertTrue(r is Result.Ok)
        assertTrue(port.editCalls.isEmpty())
        assertEquals(
            listOf<PendingCommand>(PendingCommand.EditComment(crew, mealId, commentId, text)),
            outbox.enqueued,
        )
    }

    @Test fun connectivity_class_error_falls_back_to_outbox() = runTest {
        val port = EditRecordingCommentPort().apply {
            nextEdit = Result.failure(CommentError.Edit.Unavailable)
        }
        val outbox = RecordingOutboxPort()
        val r = useCase(port = port, outbox = outbox)(crew, mealId, commentId, text)
        assertTrue(r is Result.Ok)
        assertEquals(1, port.editCalls.size)
        assertEquals(
            listOf<PendingCommand>(PendingCommand.EditComment(crew, mealId, commentId, text)),
            outbox.enqueued,
        )
    }

    @Test fun non_connectivity_error_is_surfaced() = runTest {
        val port = EditRecordingCommentPort().apply {
            nextEdit = Result.failure(CommentError.Edit.NotAuthor)
        }
        val outbox = RecordingOutboxPort()
        val r = useCase(port = port, outbox = outbox)(crew, mealId, commentId, text)
        assertEquals(Result.failure(CommentError.Edit.NotAuthor), r)
        assertTrue(outbox.enqueued.isEmpty())
    }
}
