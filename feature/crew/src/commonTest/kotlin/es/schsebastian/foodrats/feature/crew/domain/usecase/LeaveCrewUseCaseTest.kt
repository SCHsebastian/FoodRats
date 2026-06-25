package es.schsebastian.foodrats.feature.crew.domain.usecase

import es.schsebastian.foodrats.core.domain.outbox.PendingCommand
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.test.FakeConnectivityPort
import es.schsebastian.foodrats.feature.crew.domain.test.FakeCrewRepository
import es.schsebastian.foodrats.feature.crew.domain.test.RecordingOutboxPort
import es.schsebastian.foodrats.feature.crew.domain.test.aid
import es.schsebastian.foodrats.feature.crew.domain.test.cid
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LeaveCrewUseCaseTest {

    private fun useCase(
        repo: FakeCrewRepository,
        connectivity: FakeConnectivityPort = FakeConnectivityPort(online = true),
        outbox: RecordingOutboxPort = RecordingOutboxPort(),
    ) = LeaveCrewUseCase(repo, connectivity, outbox)

    @Test fun delegates_to_repo_when_online() = runTest {
        val repo = FakeCrewRepository().apply { nextLeave = Result.success(Unit) }
        val outbox = RecordingOutboxPort()
        val r = useCase(repo, outbox = outbox).invoke(cid("c-1"), aid("uid"))
        assertIs<Result.Ok<Unit>>(r)
        assertTrue(outbox.enqueued.isEmpty())
    }

    @Test fun propagates_non_connectivity_error() = runTest {
        val repo = FakeCrewRepository().apply {
            nextLeave = Result.failure(CrewError.Membership.NotMember)
        }
        val r = useCase(repo).invoke(cid("c-1"), aid("uid"))
        assertEquals(
            Result.failure(CrewError.Membership.NotMember),
            r,
        )
    }

    @Test fun offline_enqueues_and_returns_ok() = runTest {
        val repo = FakeCrewRepository().apply { nextLeave = Result.failure(CrewError.Backend.Unavailable) }
        val outbox = RecordingOutboxPort()
        val r = useCase(repo, connectivity = FakeConnectivityPort(online = false), outbox = outbox)
            .invoke(cid("c-1"), aid("uid"))
        assertIs<Result.Ok<Unit>>(r)
        assertEquals(
            listOf<PendingCommand>(PendingCommand.LeaveCrew(cid("c-1"), aid("uid"))),
            outbox.enqueued,
        )
    }

    @Test fun connectivity_class_error_falls_back_to_outbox() = runTest {
        val repo = FakeCrewRepository().apply { nextLeave = Result.failure(CrewError.Backend.Network) }
        val outbox = RecordingOutboxPort()
        val r = useCase(repo, outbox = outbox).invoke(cid("c-1"), aid("uid"))
        assertIs<Result.Ok<Unit>>(r)
        assertEquals(
            listOf<PendingCommand>(PendingCommand.LeaveCrew(cid("c-1"), aid("uid"))),
            outbox.enqueued,
        )
    }
}
