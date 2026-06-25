package es.schsebastian.foodrats.core.data.outbox

import es.schsebastian.foodrats.core.domain.account.Bio
import es.schsebastian.foodrats.core.domain.account.DisplayName
import es.schsebastian.foodrats.core.domain.meal.CommentText
import es.schsebastian.foodrats.core.domain.meal.MealCommentId
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.ReactionKind
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.outbox.OutboxEntry
import es.schsebastian.foodrats.core.domain.outbox.OutboxEntryId
import es.schsebastian.foodrats.core.domain.outbox.OutboxEntryStatus
import es.schsebastian.foodrats.core.domain.outbox.PendingCommand
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.result.getOrNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Host-test (JVM) coverage of the SQLDelight-backed [OutboxLocalStore] (P3b-T6). Seeds a real
 * in-memory `outbox` table, then asserts the public surface (`observe`/`read`/`add`/`update`/`remove`)
 * behaves like the P2 DataStore-JSON store: coalescing on idempotency key, every command leaf round-
 * tripping through the flattened columns, and a `Failed` status preserving its error key + retryable.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OutboxLocalStoreTest {

    private lateinit var db: OutboxTestDb
    private lateinit var store: OutboxLocalStore

    @BeforeTest fun setUp() {
        db = OutboxTestDb()
        store = db.store()
    }

    @AfterTest fun tearDown() = db.close()

    private val crew = (CrewId.of("crew-1") as Result.Ok).value
    private val meal = (MealId.of("meal-1") as Result.Ok).value
    private val acc = (AccountId.of("acc-1") as Result.Ok).value

    private fun rateCommand(rater: String) = PendingCommand.RateMeal(
        crewId = crew,
        mealId = meal,
        raterId = (AccountId.of(rater) as Result.Ok).value,
        score = Score.of(4).getOrNull()!!,
    )

    private fun entry(idx: Int, cmd: PendingCommand) = OutboxEntry(
        id = OutboxEntryId("entry-$idx"),
        command = cmd,
        status = OutboxEntryStatus.Pending,
        attemptCount = 0,
        createdAt = Instant.fromEpochMilliseconds(1_000L + idx),
    )

    @Test
    fun adds_with_distinct_keys_all_survive() = runTest {
        // Distinct rater per entry → distinct idempotency keys → all 8 rows persist (the UNIQUE
        // index only coalesces same-key rows).
        repeat(8) { idx -> store.add(entry(idx, rateCommand("rater-$idx"))) }

        val survivors = store.read().map { it.id.value }.toSet()
        assertEquals((0 until 8).map { "entry-$it" }.toSet(), survivors)
    }

    @Test
    fun add_coalesces_on_idempotency_key_preserves_original_id() = runTest {
        // Same crew/meal/rater → same idempotency key for both commands.
        // M1: the second add updates the payload but preserves the original id/createdAt/attemptCount
        // (identity-preserving coalesce) rather than replacing the row (old INSERT OR REPLACE behavior).
        store.add(entry(1, rateCommand("rater-X")))
        store.add(entry(2, rateCommand("rater-X")))

        val all = store.read()
        assertEquals(1, all.size)
        assertEquals("entry-1", all.single().id.value) // M1: original id preserved
    }

    @Test
    fun round_trips_every_command_leaf_through_the_table() = runTest {
        val commands: List<PendingCommand> = listOf(
            rateCommand("rater-1"),
            PendingCommand.PostComment(
                crewId = crew, mealId = meal,
                commentId = MealCommentId("c-1"),
                text = CommentText.of("hello").getOrNull()!!,
                authorId = acc,
            ),
            PendingCommand.EditComment(
                crewId = crew, mealId = meal,
                commentId = MealCommentId("c-2"),
                text = CommentText.of("edited").getOrNull()!!,
            ),
            PendingCommand.DeleteComment(crewId = crew, mealId = meal, commentId = MealCommentId("c-3")),
            PendingCommand.ToggleReaction(
                crewId = crew, mealId = meal, reactorId = acc,
                reactionKindKey = ReactionKind.DailyGlyph.key, desiredPresent = true,
            ),
            PendingCommand.RenameCrew(crewId = crew, requestedBy = acc, newName = "New Crew"),
            PendingCommand.SetBlindVoting(crewId = crew, requestedBy = acc, enabled = true),
            PendingCommand.RemoveMember(
                crewId = crew, requestedBy = acc,
                target = (AccountId.of("acc-2") as Result.Ok).value,
            ),
            PendingCommand.LeaveCrew(crewId = crew, leaver = acc),
            // C-series crew-settings commands (offline-first extension): tagline/welcome/challenge
            // text reuse the `text` column; focal/setAt/styleKey use the new flattened columns.
            PendingCommand.SetCrewTagline(crewId = crew, requestedBy = acc, tagline = "only home-cooked"),
            PendingCommand.SetCrewWelcomeMessage(crewId = crew, requestedBy = acc, message = "Cook by 10!"),
            PendingCommand.SetCrewWeeklyChallenge(
                crewId = crew, requestedBy = acc, challenge = "Taco Tuesday", setAtMillis = 1_700_000_000_000L,
            ),
            PendingCommand.SetCrewScoreStyle(crewId = crew, requestedBy = acc, styleKey = "emoji"),
            PendingCommand.SetCrewBannerFocalY(crewId = crew, requestedBy = acc, focalY = 0.25f),
            // Offline-first profile-text commands: displayName + bio reuse the accountId/text columns.
            PendingCommand.SetDisplayName(accountId = acc, displayName = DisplayName.of("Sebas").getOrNull()!!),
            PendingCommand.SetBio(accountId = acc, bio = Bio.of("Home cook").getOrNull()),
        )
        commands.forEachIndexed { idx, cmd -> store.add(entry(idx, cmd)) }

        val restored = store.read().map { it.command }
        assertEquals(commands.toSet(), restored.toSet())
    }

    @Test
    fun round_trips_crew_settings_clear_commands_with_null_text() = runTest {
        // Tagline/welcome/challenge "clear" = null text. A null text column must round-trip as null
        // (not be mistaken for a malformed row that gets dropped).
        val commands: List<PendingCommand> = listOf(
            PendingCommand.SetCrewTagline(crewId = crew, requestedBy = acc, tagline = null),
            PendingCommand.SetCrewWelcomeMessage(crewId = crew, requestedBy = acc, message = null),
            PendingCommand.SetCrewWeeklyChallenge(
                crewId = crew, requestedBy = acc, challenge = null, setAtMillis = null,
            ),
            // Bio "clear" = null bio → null text column → must round-trip as a SetBio(null), not drop.
            PendingCommand.SetBio(accountId = acc, bio = null),
        )
        commands.forEachIndexed { idx, cmd -> store.add(entry(idx, cmd)) }

        assertEquals(commands.toSet(), store.read().map { it.command }.toSet())
    }

    @Test
    fun update_to_failed_round_trips_with_error_key_and_retryable() = runTest {
        store.add(entry(1, rateCommand("rater-1")))
        store.update(OutboxEntryId("entry-1")) {
            it.copy(status = OutboxEntryStatus.Failed(errorKey = "react.offline", retryable = true), attemptCount = 2)
        }

        val r = store.read().single()
        assertTrue(r.status is OutboxEntryStatus.Failed)
        assertEquals("react.offline", (r.status as OutboxEntryStatus.Failed).errorKey)
        assertTrue((r.status as OutboxEntryStatus.Failed).retryable)
        assertEquals(2, r.attemptCount)
    }

    @Test
    fun update_is_noop_when_id_absent() = runTest {
        store.update(OutboxEntryId("ghost")) { it.copy(attemptCount = 99) }
        assertTrue(store.read().isEmpty())
    }

    @Test
    fun remove_deletes_the_row_and_is_noop_safe() = runTest {
        store.add(entry(1, rateCommand("rater-1")))
        store.remove(OutboxEntryId("entry-1"))
        assertTrue(store.read().isEmpty())
        // No-op-safe on an already-removed id.
        store.remove(OutboxEntryId("entry-1"))
    }

    @Test
    fun observe_orders_by_created_at_and_starts_empty() = runTest {
        assertTrue(store.read().isEmpty())
        assertTrue(store.observe().first().isEmpty())

        store.add(entry(2, rateCommand("rater-B"))) // createdAt 1002
        store.add(entry(1, rateCommand("rater-A"))) // createdAt 1001
        val ordered = store.observe().first().map { it.id.value }
        assertEquals(listOf("entry-1", "entry-2"), ordered)
    }

    // ── L4: selectById keyed update ───────────────────────────────────────────

    @Test
    fun update_uses_keyed_lookup_and_does_not_clobber_other_entries() = runTest {
        // Two entries; update only entry-1 — entry-2 must be untouched.
        store.add(entry(1, rateCommand("rater-A")))
        store.add(entry(2, rateCommand("rater-B")))

        store.update(OutboxEntryId("entry-1")) {
            it.copy(
                status = OutboxEntryStatus.Failed(errorKey = "rate.offline", retryable = true),
                attemptCount = 3,
            )
        }

        val all = store.read().associateBy { it.id.value }
        val updated = all["entry-1"]!!
        assertEquals(3, updated.attemptCount, "L4: targeted update must increment entry-1 only")
        assertTrue(updated.status is OutboxEntryStatus.Failed)

        val untouched = all["entry-2"]!!
        assertEquals(0, untouched.attemptCount, "L4: entry-2 must not be affected")
        assertEquals(OutboxEntryStatus.Pending, untouched.status)
    }

    // ── M5: terminal pruning ──────────────────────────────────────────────────

    @Test
    fun prune_terminal_before_drops_only_non_retryable_failed_entries() = runTest {
        // Entry 1: terminally failed (retryable=false) — created early → should be pruned.
        store.add(entry(1, rateCommand("rater-A")))
        store.update(OutboxEntryId("entry-1")) {
            it.copy(status = OutboxEntryStatus.Failed(errorKey = "x", retryable = false))
        }

        // Entry 2: retryable-failed — must NOT be pruned (still active).
        store.add(entry(2, rateCommand("rater-B")))
        store.update(OutboxEntryId("entry-2")) {
            it.copy(status = OutboxEntryStatus.Failed(errorKey = "y", retryable = true))
        }

        // Entry 3: pending — must NOT be pruned.
        store.add(entry(3, rateCommand("rater-C")))

        // Prune everything with createdAt < 1_003 (entry-1 createdAt=1_001, entry-2=1_002, entry-3=1_003).
        store.pruneTerminalBefore(beforeEpochMs = 1_003L)

        val remaining = store.read().map { it.id.value }.toSet()
        assertEquals(
            setOf("entry-2", "entry-3"),
            remaining,
            "M5: only the non-retryable terminal entry older than the cutoff must be pruned",
        )
    }

    @Test
    fun prune_terminal_before_is_noop_when_no_entries_qualify() = runTest {
        // Terminal entry but createdAt > cutoff → NOT pruned.
        store.add(entry(10, rateCommand("rater-A"))) // createdAt = 1_010
        store.update(OutboxEntryId("entry-10")) {
            it.copy(status = OutboxEntryStatus.Failed(errorKey = "z", retryable = false))
        }

        store.pruneTerminalBefore(beforeEpochMs = 1_000L) // cutoff before entry's createdAt

        assertEquals(1, store.read().size, "M5: terminal entry newer than cutoff must not be pruned")
    }
}
