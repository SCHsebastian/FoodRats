package es.schsebastian.foodrats.core.domain.outbox

/**
 * Dispatch surface the `OutboxRunner` uses to replay a [PendingCommand] (P2 §1 T1).
 *
 * `:core:data`'s runner must NEVER import a `:feature:*` module, so it cannot
 * know how to execute a `RateMeal` or a `RenameCrew` directly. Instead it
 * dispatches through an injected `List<OutboxCommandHandler>` (Koin `getAll()`);
 * the concrete handlers — which DO know the feature ports — live in the feature
 * modules (`MealOutboxCommandHandler` in `:feature:meal`,
 * `CrewOutboxCommandHandler` in `:feature:crew`). The runner picks the first
 * handler whose [handles] returns `true`.
 */
interface OutboxCommandHandler {

    /** Whether this handler knows how to [execute] the given [cmd]. */
    fun handles(cmd: PendingCommand): Boolean

    /**
     * Replay [cmd] against the underlying feature port and classify the outcome.
     * The runner only calls this when [handles] returned `true` for [cmd].
     *
     * Must be idempotent: a command already applied on a previous attempt (or
     * directly online) returns [OutboxExecuteResult.AlreadyApplied], not a
     * failure.
     */
    suspend fun execute(cmd: PendingCommand): OutboxExecuteResult

    /**
     * Called by the runner whenever a command this handler [handles] reaches a
     * TERMINAL state by either route:
     *  - the handler itself returns [OutboxExecuteResult.Terminal],
     *  - the retry budget is exhausted ([OutboxExecuteResult.Retryable] after
     *    `maxAttempts` with `retryable = false`).
     *
     * (The no-handler-found case marks the entry terminal without a handler to
     * notify, so `onTerminal` cannot — and need not — fire there.)
     *
     * Default is a no-op. Feature handlers override to clean up side-effects that
     * were applied optimistically before the command was enqueued (e.g., rolling
     * back a phantom rating star when [PendingCommand.RateMeal] lands terminal).
     * The runner stays feature-free because it only calls this interface method —
     * it never imports the feature's cleanup logic directly.
     */
    suspend fun onTerminal(command: PendingCommand) {}
}

/**
 * Outcome of replaying a [PendingCommand] (P2 §1 T1).
 *
 * A `sealed interface` (not an enum) so the retryable/terminal leaves can carry
 * an `errorKey` payload. [Success] and [AlreadyApplied] both mean "remove the
 * entry"; they are kept distinct so the runner/analytics can tell a real apply
 * from a dedup.
 */
sealed interface OutboxExecuteResult {
    /** The command applied successfully. The runner removes the entry. */
    data object Success : OutboxExecuteResult

    /**
     * The command was already applied (idempotency / dedup — e.g. the comment doc
     * already exists, or the member was already removed). Treated as success:
     * the runner removes the entry.
     */
    data object AlreadyApplied : OutboxExecuteResult

    /**
     * A transient failure (e.g. network/backend unavailable). The runner records
     * an [OutboxEntryStatus.Failed] via [OutboxTransitions] and, if the budget
     * allows, schedules another attempt after [OutboxRetryPolicy.nextDelay].
     *
     * @property errorKey opaque token for the presentation layer.
     */
    data class Retryable(val errorKey: String) : OutboxExecuteResult

    /**
     * A permanent failure that retrying cannot fix (e.g. authorization, invalid
     * input). The runner records a terminal [OutboxEntryStatus.Failed]
     * (`retryable = false`) for the user — no further attempts.
     *
     * @property errorKey opaque token for the presentation layer.
     */
    data class Terminal(val errorKey: String) : OutboxExecuteResult
}
