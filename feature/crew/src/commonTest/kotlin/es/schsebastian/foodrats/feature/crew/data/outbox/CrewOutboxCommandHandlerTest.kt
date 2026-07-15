package es.schsebastian.foodrats.feature.crew.data.outbox

import es.schsebastian.foodrats.core.domain.account.Bio
import es.schsebastian.foodrats.core.domain.account.DisplayName
import es.schsebastian.foodrats.core.domain.crew.CrewScoreStyle
import es.schsebastian.foodrats.core.domain.meal.CommentText
import es.schsebastian.foodrats.core.domain.meal.MealCommentId
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.core.domain.outbox.OutboxExecuteResult
import es.schsebastian.foodrats.core.domain.outbox.PendingCommand
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.result.getOrNull
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.model.Crew
import es.schsebastian.foodrats.feature.crew.domain.model.CrewCode
import es.schsebastian.foodrats.feature.crew.domain.model.Member
import es.schsebastian.foodrats.feature.crew.domain.test.FakeCrewRepository
import es.schsebastian.foodrats.feature.crew.domain.test.aid
import es.schsebastian.foodrats.feature.crew.domain.test.cid
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CrewOutboxCommandHandlerTest {

    private val ownerId = aid("uid-owner")
    private val memberId = aid("uid-member")
    private val crewId = cid("c-1")
    private val mealId = (MealId.of("m-1") as Result.Ok).value
    private val sampleCrew = Crew.of(
        id = crewId,
        name = "Crew",
        code = (CrewCode.of("ABCD23") as Result.Ok).value,
        ownerId = ownerId,
        createdAt = Instant.fromEpochMilliseconds(0L),
        members = listOf(
            Member(ownerId, Instant.fromEpochMilliseconds(0L)),
            Member(memberId, Instant.fromEpochMilliseconds(0L)),
        ),
    )

    private fun handler(repo: FakeCrewRepository = FakeCrewRepository(listOf(sampleCrew))) =
        CrewOutboxCommandHandler(repo) to repo

    // ---- handles() ----

    @Test fun handles_every_crew_command() {
        val (h, _) = handler()
        assertTrue(h.handles(PendingCommand.RenameCrew(crewId, ownerId, "New")))
        assertTrue(h.handles(PendingCommand.SetBlindVoting(crewId, ownerId, true)))
        assertTrue(h.handles(PendingCommand.RemoveMember(crewId, ownerId, memberId)))
        assertTrue(h.handles(PendingCommand.LeaveCrew(crewId, ownerId)))
        assertTrue(h.handles(PendingCommand.SetCrewTagline(crewId, ownerId, "tag")))
        assertTrue(h.handles(PendingCommand.SetCrewWelcomeMessage(crewId, ownerId, "welcome")))
        assertTrue(h.handles(PendingCommand.SetCrewWeeklyChallenge(crewId, ownerId, "challenge", 0L)))
        assertTrue(h.handles(PendingCommand.SetCrewScoreStyle(crewId, ownerId, "stars")))
        assertTrue(h.handles(PendingCommand.SetCrewBannerFocalY(crewId, ownerId, 0.5f)))
    }

    @Test fun rejects_non_crew_commands() {
        val (h, _) = handler()
        val commentText = (CommentText.of("hi") as Result.Ok).value
        assertFalse(h.handles(PendingCommand.RateMeal(crewId, mealId, ownerId, (Score.of(4) as Result.Ok).value)))
        assertFalse(h.handles(PendingCommand.PostComment(crewId, mealId, MealCommentId("cm1"), commentText, ownerId)))
        assertFalse(h.handles(PendingCommand.EditComment(crewId, mealId, MealCommentId("cm1"), commentText)))
        assertFalse(h.handles(PendingCommand.DeleteComment(crewId, mealId, MealCommentId("cm1"))))
        assertFalse(h.handles(PendingCommand.ToggleReaction(crewId, mealId, ownerId, "glyph", true)))
        assertFalse(h.handles(PendingCommand.SetDisplayName(ownerId, DisplayName.of("Alice").getOrNull()!!)))
        assertFalse(h.handles(PendingCommand.SetBio(ownerId, Bio.of("hi").getOrNull())))
    }

    // ---- execute() success, one per command ----

    @Test fun rename_crew_success() = runTest {
        val (h, repo) = handler()
        assertEquals(OutboxExecuteResult.Success, h.execute(PendingCommand.RenameCrew(crewId, ownerId, "New Name")))
        assertEquals(crewId to "New Name", repo.lastRename)
    }

    @Test fun set_blind_voting_success() = runTest {
        val (h, repo) = handler()
        assertEquals(OutboxExecuteResult.Success, h.execute(PendingCommand.SetBlindVoting(crewId, ownerId, true)))
        assertEquals(crewId to true, repo.lastSetBlindVoting)
    }

    @Test fun remove_member_success() = runTest {
        val (h, repo) = handler()
        assertEquals(OutboxExecuteResult.Success, h.execute(PendingCommand.RemoveMember(crewId, ownerId, memberId)))
        assertEquals(Triple(crewId, ownerId, memberId), repo.lastRemoveMember)
    }

    @Test fun leave_crew_success() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew))
        repo.nextLeave = Result.success(Unit)
        val h = CrewOutboxCommandHandler(repo)
        assertEquals(OutboxExecuteResult.Success, h.execute(PendingCommand.LeaveCrew(crewId, memberId)))
        assertEquals(Triple(crewId, memberId, null), repo.lastLeave)
    }

    @Test fun set_tagline_success() = runTest {
        val (h, repo) = handler()
        assertEquals(
            OutboxExecuteResult.Success,
            h.execute(PendingCommand.SetCrewTagline(crewId, ownerId, "Only home-cooked")),
        )
        assertEquals(crewId to "Only home-cooked", repo.lastSetTagline)
    }

    @Test fun clear_tagline_success() = runTest {
        val (h, repo) = handler()
        assertEquals(OutboxExecuteResult.Success, h.execute(PendingCommand.SetCrewTagline(crewId, ownerId, null)))
        assertEquals(crewId to null, repo.lastSetTagline)
    }

    @Test fun set_welcome_message_success() = runTest {
        val (h, repo) = handler()
        assertEquals(
            OutboxExecuteResult.Success,
            h.execute(PendingCommand.SetCrewWelcomeMessage(crewId, ownerId, "Welcome!")),
        )
        assertEquals(crewId to "Welcome!", repo.lastSetWelcomeMessage)
    }

    @Test fun set_weekly_challenge_success() = runTest {
        val (h, repo) = handler()
        assertEquals(
            OutboxExecuteResult.Success,
            h.execute(PendingCommand.SetCrewWeeklyChallenge(crewId, ownerId, "Taco Tuesday", 1_000L)),
        )
        assertEquals(Triple(crewId, "Taco Tuesday", 1_000L), repo.lastSetWeeklyChallenge)
    }

    @Test fun set_score_style_success() = runTest {
        val (h, repo) = handler()
        assertEquals(OutboxExecuteResult.Success, h.execute(PendingCommand.SetCrewScoreStyle(crewId, ownerId, "emoji")))
        assertEquals(CrewScoreStyle.Emoji, repo.crews.value.first { it.id == crewId }.scoreStyle)
    }

    @Test fun set_score_style_unknown_key_is_terminal_without_touching_repository() = runTest {
        val (h, repo) = handler()
        val result = h.execute(PendingCommand.SetCrewScoreStyle(crewId, ownerId, "not-a-real-style"))
        assertEquals(OutboxExecuteResult.Terminal("crew.error.scoreStyleUnknown"), result)
        assertEquals(CrewScoreStyle.Stars, repo.crews.value.first { it.id == crewId }.scoreStyle)
    }

    @Test fun set_banner_focal_y_success() = runTest {
        val (h, repo) = handler()
        assertEquals(OutboxExecuteResult.Success, h.execute(PendingCommand.SetCrewBannerFocalY(crewId, ownerId, 0.2f)))
        assertEquals(crewId to 0.2f, repo.lastSetBannerFocal)
    }

    // ---- error classification ----

    @Test fun network_failure_is_retryable() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew))
        repo.nextSetBlindVoting = Result.failure(CrewError.Backend.Network)
        val h = CrewOutboxCommandHandler(repo)
        assertIs<OutboxExecuteResult.Retryable>(h.execute(PendingCommand.SetBlindVoting(crewId, ownerId, true)))
    }

    @Test fun backend_unavailable_is_retryable() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew))
        repo.nextRename = Result.failure(CrewError.Backend.Unavailable)
        val h = CrewOutboxCommandHandler(repo)
        assertIs<OutboxExecuteResult.Retryable>(h.execute(PendingCommand.RenameCrew(crewId, ownerId, "x")))
    }

    @Test fun permission_denied_is_terminal() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew))
        repo.nextSetTagline = Result.failure(CrewError.Backend.PermissionDenied)
        val h = CrewOutboxCommandHandler(repo)
        assertIs<OutboxExecuteResult.Terminal>(h.execute(PendingCommand.SetCrewTagline(crewId, ownerId, "x")))
    }

    @Test fun session_expired_is_terminal() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew))
        repo.nextSetWelcomeMessage = Result.failure(CrewError.Session.Expired)
        val h = CrewOutboxCommandHandler(repo)
        assertIs<OutboxExecuteResult.Terminal>(h.execute(PendingCommand.SetCrewWelcomeMessage(crewId, ownerId, "x")))
    }

    // ---- idempotent replay ----

    @Test fun remove_member_already_gone_is_already_applied_not_an_error() = runTest {
        val (h, _) = handler()
        // The member was already removed by an earlier attempt — a replay targets an account
        // no longer present. The goal is already met, so this must NOT surface as an error.
        val alreadyGone = aid("uid-gone")
        assertEquals(
            OutboxExecuteResult.AlreadyApplied,
            h.execute(PendingCommand.RemoveMember(crewId, ownerId, alreadyGone)),
        )
    }

    @Test fun leave_crew_no_longer_a_member_is_already_applied_not_an_error() = runTest {
        val repo = FakeCrewRepository(listOf(sampleCrew))
        repo.nextLeave = Result.failure(CrewError.Membership.NotMember)
        val h = CrewOutboxCommandHandler(repo)
        assertEquals(OutboxExecuteResult.AlreadyApplied, h.execute(PendingCommand.LeaveCrew(crewId, memberId)))
    }

    // ---- onTerminal() ----

    @Test fun on_terminal_is_a_no_op() = runTest {
        val (h, repo) = handler()
        // The crew handler does not override OutboxCommandHandler.onTerminal, so the interface's
        // default no-op applies: it must not touch the repository or throw.
        h.onTerminal(PendingCommand.RenameCrew(crewId, ownerId, "New Name"))
        assertEquals(null, repo.lastRename)
    }
}
