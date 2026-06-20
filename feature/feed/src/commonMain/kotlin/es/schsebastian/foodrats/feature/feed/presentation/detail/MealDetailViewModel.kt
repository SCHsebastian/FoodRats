package es.schsebastian.foodrats.feature.feed.presentation.detail

import androidx.lifecycle.viewModelScope
import es.schsebastian.foodrats.core.designsystem.molecules.FrReportReasonOption
import es.schsebastian.foodrats.core.domain.account.Account
import es.schsebastian.foodrats.core.domain.account.AccountReadPort
import es.schsebastian.foodrats.core.domain.account.BlockedAccountsPort
import es.schsebastian.foodrats.core.data.share.StoryShareController
import es.schsebastian.foodrats.core.data.share.StoryShareOutcome
import es.schsebastian.foodrats.core.designsystem.templates.ShareCardFormat
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsEvent
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsPort
import es.schsebastian.foodrats.core.domain.analytics.NoopAnalyticsTracker
import es.schsebastian.foodrats.core.domain.connectivity.ConnectivityPort
import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.crew.CrewBlindVotingPort
import es.schsebastian.foodrats.core.domain.crew.CrewOwnerPort
import es.schsebastian.foodrats.core.domain.meal.IngredientReadPort
import es.schsebastian.foodrats.core.domain.meal.ingredientNameResolver
import es.schsebastian.foodrats.core.domain.meal.CommentError
import es.schsebastian.foodrats.core.domain.meal.CommentText
import es.schsebastian.foodrats.core.domain.meal.CommentValidationError
import es.schsebastian.foodrats.core.domain.meal.Meal
import es.schsebastian.foodrats.core.domain.meal.MealComment
import es.schsebastian.foodrats.core.domain.meal.MealCommentId
import es.schsebastian.foodrats.core.domain.meal.MealCommentPort
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.moderation.ReportPort
import es.schsebastian.foodrats.core.domain.moderation.ReportReason
import es.schsebastian.foodrats.core.domain.moderation.ReportTarget
import es.schsebastian.foodrats.core.domain.moderation.TextModerationPort
import es.schsebastian.foodrats.core.domain.moderation.TextModerationVerdict
import es.schsebastian.foodrats.core.domain.outbox.OutboxPort
import es.schsebastian.foodrats.core.domain.outbox.PendingCommand
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.result.getOrElse
import es.schsebastian.foodrats.core.domain.result.getOrNull
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.core.presentation.mvi.MviViewModel
import es.schsebastian.foodrats.feature.feed.domain.model.FeedDay
import es.schsebastian.foodrats.feature.feed.domain.usecase.DeleteCommentUseCase
import es.schsebastian.foodrats.feature.feed.domain.usecase.DeleteMealUseCase
import es.schsebastian.foodrats.feature.feed.domain.usecase.DeleteMyMealUseCase
import es.schsebastian.foodrats.feature.feed.domain.usecase.ObserveFeedUseCase
import es.schsebastian.foodrats.feature.feed.domain.usecase.RateMealUseCase
import es.schsebastian.foodrats.feature.feed.presentation.components.CommentRowUi
import es.schsebastian.foodrats.feature.feed.presentation.components.PlateShareCardContent
import es.schsebastian.foodrats.feature.feed.presentation.components.toFeedUi
import es.schsebastian.foodrats.feature.feed.presentation.components.toRelative
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class MealDetailViewModel(
    private val mealId: String,
    private val dayIso: String,
    observeFeed: ObserveFeedUseCase,
    private val rateMeal: RateMealUseCase,
    private val commentPort: MealCommentPort,
    private val connectivity: ConnectivityPort,
    private val outbox: OutboxPort,
    private val accountReadPort: AccountReadPort,
    private val ingredientRead: IngredientReadPort,
    private val activeCrew: ActiveCrewProvider,
    private val blindVoting: CrewBlindVotingPort,
    private val session: SessionProvider,
    private val clock: Clock,
    private val zone: TimeZone,
    private val deleteMeal: DeleteMealUseCase,
    private val deleteMyMeal: DeleteMyMealUseCase,
    private val deleteComment: DeleteCommentUseCase,
    private val crewOwner: CrewOwnerPort,
    private val storyShareController: StoryShareController,
    // UGC compliance §3/§4/§5 — comment text filter, report, and block. Defaults keep the large
    // existing test surface compiling; the Koin binding passes the real ports explicitly.
    private val textModeration: TextModerationPort = TextModerationPort { _, _ -> TextModerationVerdict.Clean },
    private val languageTag: Flow<String> = flowOf("en"),
    private val reportPort: ReportPort = NoopReportPort,
    private val blockedAccounts: BlockedAccountsPort = NoopBlockedAccountsPort,
    private val analytics: AnalyticsPort = NoopAnalyticsTracker,
) : MviViewModel<MealDetailState, MealDetailIntent, MealDetailEffect>(MealDetailState()) {

    /** Deduped per-author flows: at most one active Firestore listener per unique authorId. */
    private val authorFlows = mutableMapOf<AccountId, SharedFlow<Account?>>()

    /**
     * The currently-displayed meal (domain), stashed from the feed stream so the delete
     * action can read its author / day / slot. Drives the author-vs-owner delete split:
     * the author removes the post from every crew, an owner only from the crew in view.
     */
    private var matchedMeal: Meal? = null

    /**
     * Live "is blind voting on?" for the active crew — mirrors [FeedViewModel.blindVotingFlow] so
     * the detail screen masks the author identically to the feed (the leak was that detail never
     * fed this into `toFeedUi`, defaulting `blindVoting` to false). Emits `false` when no crew.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val blindVotingFlow =
        activeCrew.current
            .distinctUntilChanged()
            .flatMapLatest { crewId ->
                if (crewId == null) flowOf(false)
                else blindVoting.observeBlindVoting(crewId)
            }
            .distinctUntilChanged()

    init {
        val parsedDay = runCatching { LocalDate.parse(dayIso) }.getOrNull()
        if (parsedDay == null) {
            update { it.copy(isLoading = false, notFound = true, commentsLoading = false) }
        } else {
            // select_content (open) — fired once per screen open; the meal id is known up front.
            MealId.of(mealId).getOrNull()?.let { analytics.track(AnalyticsEvent.MealOpened(it)) }
            val feedDay = FeedDay(MealDay(parsedDay, zone))
            viewModelScope.launch {
                combine(
                    observeFeed(flowOf(feedDay)),
                    ingredientRead.observeCatalog(),
                    blindVotingFlow,
                ) { r, catalog, blind -> Triple(r, catalog, blind) }.collect { (r, catalog, blind) ->
                    when (r) {
                        is Result.Ok -> {
                            val viewerId = session.current.first()?.accountId
                            val ownerId = activeCrew.current.first()
                                ?.let { crewOwner.observeOwner(it).first() }
                            val todayMealDay = MealDay.today(clock, zone)
                            val matched = r.value.firstOrNull { it.meal.id.value == mealId }
                            matchedMeal = matched?.meal
                            val nameFor = ingredientNameResolver(catalog)
                            val match = if (viewerId == null || matched == null) null
                                        else matched.toFeedUi(
                                            viewerId,
                                            todayMealDay,
                                            ingredientNames = matched.meal.ingredients.map(nameFor),
                                            blindVoting = blind,
                                        )
                            val canDeleteMeal = match != null && viewerId != null &&
                                (match.authorId == viewerId.value || ownerId == viewerId)
                            // Report/block the author only on someone else's meal (UGC §4/§5).
                            val canModerateMeal = match != null && viewerId != null &&
                                match.authorId != viewerId.value
                            update {
                                it.copy(
                                    isLoading = false,
                                    meal = match,
                                    notFound = match == null,
                                    error = null,
                                    canDeleteMeal = canDeleteMeal,
                                    canModerateMeal = canModerateMeal,
                                )
                            }
                        }
                        is Result.Err -> update {
                            it.copy(isLoading = false, error = r.error)
                        }
                    }
                }
            }
            observeComments()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeComments() = viewModelScope.launch {
        val parsedMealId = MealId.of(mealId).getOrElse {
            update { it.copy(commentsLoading = false, commentReadError = CommentError.Read.Unavailable) }
            return@launch
        }
        // Viewer + owner are derived REACTIVELY (not snapshotted once at subscription) so
        // comment-row deletability tracks a sign-in/account change or a crew-owner handover
        // instead of going stale for the lifetime of the screen.
        val viewerIdFlow = session.current
            .map { it?.accountId }
            .distinctUntilChanged()
        val ownerIdFlow = activeCrew.current
            .flatMapLatest { crewId ->
                if (crewId == null) flowOf(null) else crewOwner.observeOwner(crewId)
            }
            .distinctUntilChanged()
        // UGC compliance §5 — the viewer's live block list, so blocked commenters vanish reactively.
        val blockedFlow = viewerIdFlow
            .flatMapLatest { viewerId ->
                if (viewerId == null) flowOf(emptySet()) else blockedAccounts.observeBlocked(viewerId)
            }
            .distinctUntilChanged()
        val rbacFlow = combine(viewerIdFlow, ownerIdFlow, blockedFlow) { viewerId, ownerId, blocked ->
            CommentRbac(viewerId, ownerId, blocked)
        }

        val commentsFlow = activeCrew.current
            .flatMapLatest { crewId ->
                if (crewId == null) flowOf<Result<List<MealComment>, CommentError.Read>>(
                    Result.failure(CommentError.Read.Unauthorized)
                )
                else commentPort.observe(crewId, parsedMealId)
            }

        // flatMapLatest so each new comment batch supersedes the previous identity-join
        // and feeds a single terminal collect. A nested `collect` inside the outer
        // collector (the old shape) suspended the outer collector forever on the first
        // batch, freezing the comment stream — new comments never arrived.
        combine(commentsFlow, rbacFlow) { r, rbac -> r to rbac }
            .flatMapLatest { (r, rbac) ->
                val (viewerId, ownerId, blocked) = rbac
                when (r) {
                    is Result.Err -> flowOf(CommentRowsResult.Err(r.error))
                    is Result.Ok -> {
                        // UGC compliance §5 — drop comments authored by a blocked user.
                        val visible = r.value.filterNot { it.authorId in blocked }
                        val uniqueIds = visible.map { it.authorId }.toSet()
                        val perAuthorFlows = uniqueIds.map { id ->
                            authorFlows.getOrPut(id) {
                                accountReadPort.observe(id)
                                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
                            }
                        }
                        if (perAuthorFlows.isEmpty()) {
                            flowOf(CommentRowsResult.Ok(joinRows(visible, emptyMap(), viewerId, ownerId)))
                        } else {
                            combine(perAuthorFlows) { snapshots ->
                                val map = uniqueIds.zip(snapshots.toList()).toMap()
                                CommentRowsResult.Ok(joinRows(visible, map, viewerId, ownerId))
                            }
                        }
                    }
                }
            }
            .collect { result ->
                when (result) {
                    is CommentRowsResult.Err -> update {
                        it.copy(commentsLoading = false, commentReadError = result.error)
                    }
                    is CommentRowsResult.Ok -> update {
                        it.copy(commentRows = result.rows, commentsLoading = false, commentReadError = null)
                    }
                }
            }
    }

    /** Internal carrier so the identity-join flatMapLatest can propagate either rows or a read error. */
    private sealed interface CommentRowsResult {
        data class Ok(val rows: List<CommentRowUi>) : CommentRowsResult
        data class Err(val error: CommentError.Read) : CommentRowsResult
    }

    /** Reactive RBAC + block context the comment join reads (viewer, crew owner, blocked set). */
    private data class CommentRbac(
        val viewerId: AccountId?,
        val ownerId: AccountId?,
        val blocked: Set<AccountId>,
    )

    private fun joinRows(
        comments: List<MealComment>,
        authors: Map<AccountId, Account?>,
        viewerId: AccountId?,
        ownerId: AccountId?,
    ): List<CommentRowUi> = comments.map { c ->
        val account = authors[c.authorId]
        val resolved = authors.containsKey(c.authorId)
        val isDeleted = resolved && account == null
        // RBAC: viewer may delete a comment row iff they authored it OR own the crew.
        // Whether the *author's account doc* has resolved (`isDeleted`) is identity-join
        // state, unrelated to the viewer's delete permission — it must not gate it.
        val canDelete = viewerId != null &&
            (c.authorId == viewerId || ownerId == viewerId)
        CommentRowUi(
            id = c.id,
            displayName = account?.displayName.orEmpty(),
            avatarUrl = account?.avatarUrl,
            text = c.text.value,
            relative = c.createdAt.toRelative(clock.now()),
            loading = !resolved,
            isDeleted = isDeleted,
            canDelete = canDelete,
            authorId = c.authorId.value,
            // Report/block is offered on everyone else's comments (UGC §4/§5) — never your own.
            canModerate = viewerId != null && c.authorId != viewerId,
        )
    }

    override suspend fun handle(intent: MealDetailIntent) = when (intent) {
        is MealDetailIntent.RateMeal               -> rate(intent.score)
        MealDetailIntent.DismissError              -> update {
            it.copy(
                error = null,
                rateError = null,
                commentWriteError = null,
                mealDeleteError = null,
                commentDeleteError = null,
            )
        }
        is MealDetailIntent.CommentInputChanged    -> update {
            it.copy(commentInput = intent.value, commentWriteError = null)
        }
        MealDetailIntent.PostComment               -> postComment()
        MealDetailIntent.DeleteMeal                -> deleteMealAction()
        is MealDetailIntent.DeleteComment          -> deleteCommentAction(intent.id)
        MealDetailIntent.ShareTapped               -> shareMeal()
        MealDetailIntent.DismissShareOutcome       -> update { it.copy(shareOutcome = null) }
        is MealDetailIntent.OpenReport             -> update {
            it.copy(reportTarget = intent.target, reportError = null)
        }
        is MealDetailIntent.SubmitReport           -> submitReport(intent.reason)
        MealDetailIntent.DismissReport             -> update { it.copy(reportTarget = null, reportError = null) }
        MealDetailIntent.DismissReportSuccess      -> update { it.copy(reportSuccess = false) }
        MealDetailIntent.BlockAuthor               -> blockAuthor()
        is MealDetailIntent.BlockCommentAuthor     -> blockAccount(intent.commentAuthorId)
    }

    /** Maps the presentation reason option to the domain [ReportReason]. */
    private fun FrReportReasonOption.toReason(): ReportReason = when (this) {
        FrReportReasonOption.SPAM       -> ReportReason.Spam
        FrReportReasonOption.HARASSMENT -> ReportReason.Harassment
        FrReportReasonOption.HATE       -> ReportReason.Hate
        FrReportReasonOption.SEXUAL     -> ReportReason.Sexual
        FrReportReasonOption.VIOLENCE   -> ReportReason.Violence
        FrReportReasonOption.OTHER      -> ReportReason.Other
    }

    /** Submits a report for the currently-open [MealDetailState.reportTarget] (UGC compliance §4). */
    private suspend fun submitReport(reasonOption: FrReportReasonOption) {
        val target = currentState.reportTarget ?: return
        val crewId = activeCrew.current.first() ?: return
        val reporter = session.current.first()?.accountId ?: return
        val parsedMealId = MealId.of(mealId).getOrNull() ?: return
        // The account self-report guard lives in the rule (`accountId != reporter`) and the repository
        // pre-flight; meal/comment authorization is by crew membership + target existence, so the report
        // target no longer carries the reported-content author.
        val domainTarget: ReportTarget = when (target) {
            ReportTargetUi.Meal   -> ReportTarget.Meal(parsedMealId, crewId)
            ReportTargetUi.Author -> ReportTarget.Account(matchedMeal?.author?.accountId ?: return)
            is ReportTargetUi.Comment ->
                ReportTarget.Comment(parsedMealId, crewId, target.commentId)
        }
        update { it.copy(reportSubmitting = true, reportError = null) }
        when (val r = reportPort.report(reporter, domainTarget, reasonOption.toReason())) {
            is Result.Ok  -> update {
                it.copy(reportSubmitting = false, reportTarget = null, reportSuccess = true)
            }
            is Result.Err -> update { it.copy(reportSubmitting = false, reportError = r.error) }
        }
    }

    /** Blocks the displayed meal's author; their content disappears reactively (UGC compliance §5). */
    private suspend fun blockAuthor() {
        val authorId = matchedMeal?.author?.accountId ?: return
        blockAccount(authorId.value)
    }

    /** Blocks [rawAccountId]; the feed/detail/comment streams re-emit without their content. */
    private suspend fun blockAccount(rawAccountId: String) {
        val owner = session.current.first()?.accountId ?: return
        val target = AccountId.of(rawAccountId).getOrNull() ?: return
        when (val r = blockedAccounts.block(owner, target)) {
            is Result.Ok  -> Unit // content vanishes via observeBlocked re-emission.
            is Result.Err -> update { it.copy(blockError = r.error) }
        }
    }

    /**
     * Shares the displayed plate to Instagram Stories (spec §8.2). The renderer/decoder own their IO
     * (no `withContext` here); the off-screen card content resolves its own i18n in composition. The
     * `share` analytics event fires ONLY when the launcher actually opened Instagram or the fallback
     * sheet — never on `Failed`, never in a use case (CHARTER §9).
     */
    private suspend fun shareMeal() {
        val meal = currentState.meal ?: return
        if (currentState.isPreparingShare) return
        val parsedMealId = MealId.of(meal.mealId).getOrNull()
        update { it.copy(isPreparingShare = true, shareOutcome = null) }
        val outcome = storyShareController.share(
            plateUrl = meal.photoUrl,
            format = ShareCardFormat.Story,
        ) { plate -> PlateShareCardContent(meal, plate) }
        if (outcome != StoryShareOutcome.Failed && parsedMealId != null) {
            analytics.track(AnalyticsEvent.PlateShared(parsedMealId))
        }
        update {
            it.copy(
                isPreparingShare = false,
                shareOutcome = when (outcome) {
                    StoryShareOutcome.OpenedInstagram    -> ShareOutcomeUi.Succeeded
                    StoryShareOutcome.OpenedFallbackSheet -> ShareOutcomeUi.OpenedSheet
                    StoryShareOutcome.Failed             -> ShareOutcomeUi.Failed
                },
            )
        }
    }

    private suspend fun deleteMealAction() {
        val parsedMealId = MealId.of(mealId).getOrElse { return }
        val viewerId = session.current.first()?.accountId
        val meal = matchedMeal
        update { it.copy(isDeletingMeal = true, mealDeleteError = null) }
        // The author owns the post across every crew it was shared to, so deleting it as the
        // author fans out to all of them. A crew owner (not the author) can only moderate the
        // copy in the crew currently in view.
        val byAuthor = meal != null && viewerId != null && meal.author.accountId == viewerId
        val r = if (byAuthor) {
            deleteMyMeal(viewerId, meal.day, meal.slot)
        } else {
            val crewId = activeCrew.current.first()
            if (crewId == null) {
                update { it.copy(isDeletingMeal = false) }
                return
            }
            deleteMeal(crewId, parsedMealId)
        }
        if (r is Result.Ok) analytics.track(AnalyticsEvent.MealDeleted(byAuthor = byAuthor))
        update {
            when (r) {
                is Result.Ok  -> it.copy(isDeletingMeal = false, mealDeleted = true)
                is Result.Err -> it.copy(isDeletingMeal = false, mealDeleteError = r.error)
            }
        }
    }

    private suspend fun deleteCommentAction(id: MealCommentId) {
        val crewId = activeCrew.current.first() ?: return
        val parsedMealId = MealId.of(mealId).getOrElse { return }
        val r = deleteComment(crewId, parsedMealId, id)
        if (r is Result.Err) update { it.copy(commentDeleteError = r.error) }
    }

    private suspend fun rate(scoreRaw: Int) {
        val crewId = activeCrew.current.first() ?: return
        val raterId = session.current.first()?.accountId ?: return
        val parsedMealId = MealId.of(mealId).getOrElse { return }
        val score = Score.of(scoreRaw).getOrElse { return }
        update { it.copy(pendingRate = true, rateError = null) }
        val r = rateMeal(crewId, parsedMealId, raterId, score)
        if (r is Result.Ok) analytics.track(AnalyticsEvent.MealRated(parsedMealId, scoreRaw))
        update {
            when (r) {
                is Result.Ok  -> it.copy(pendingRate = false)
                is Result.Err -> it.copy(pendingRate = false, rateError = r.error)
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun postComment() {
        val crewId = activeCrew.current.first()
            ?: return update { it.copy(commentWriteError = CommentError.Write.Unauthorized) }
        val parsedMealId = MealId.of(mealId).getOrElse {
            return update { it.copy(commentWriteError = CommentError.Write.Unavailable) }
        }
        val text = when (val v = CommentText.of(currentState.commentInput)) {
            is Result.Ok  -> v.value
            is Result.Err -> return update {
                val writeErr = when (v.error) {
                    CommentValidationError.Blank   -> CommentError.Write.Blank
                    CommentValidationError.TooLong -> CommentError.Write.TooLong
                }
                it.copy(commentWriteError = writeErr)
            }
        }
        val authorId = session.current.first()?.accountId
            ?: return update { it.copy(commentWriteError = CommentError.Write.Unauthorized) }
        // UGC compliance §3 — HARD block: screen the comment on-device BEFORE the online/outbox branch
        // so an objectionable comment never reaches Firestore OR the durable outbox.
        val verdict = textModeration.evaluate(text.value, languageTag.first())
        if (verdict is TextModerationVerdict.Objectionable) {
            return update { it.copy(isPostingComment = false, commentWriteError = CommentError.Write.Objectionable) }
        }
        update { it.copy(isPostingComment = true, commentWriteError = null) }
        // Client-minted id so an offline replay (T7) sets the SAME doc — `.set()` is idempotent.
        val commentId = MealCommentId(Uuid.random().toString())
        // OFFLINE-FIRST (P2 §0.5): offline — or a connectivity-class write failure — durably
        // parks the comment in the outbox (with this same minted id) and treats it as posted.
        if (!connectivity.isOnline().first()) {
            return enqueueComment(crewId, parsedMealId, commentId, text, authorId)
        }
        val r = commentPort.post(crewId, parsedMealId, commentId, text)
        if (r is Result.Ok) analytics.track(AnalyticsEvent.CommentPosted(parsedMealId))
        if (r is Result.Err && r.error == CommentError.Write.Unavailable) {
            return enqueueComment(crewId, parsedMealId, commentId, text, authorId)
        }
        update {
            when (r) {
                is Result.Ok  -> it.copy(isPostingComment = false, commentInput = "")
                is Result.Err -> it.copy(isPostingComment = false, commentWriteError = r.error)
            }
        }
    }

    private suspend fun enqueueComment(
        crewId: CrewId,
        mealId: MealId,
        commentId: MealCommentId,
        text: CommentText,
        authorId: AccountId,
    ) {
        outbox.enqueue(PendingCommand.PostComment(crewId, mealId, commentId, text, authorId))
        update { it.copy(isPostingComment = false, commentInput = "") }
    }
}

/**
 * No-op [ReportPort] used as the constructor default so the existing test surface keeps compiling.
 * The Koin binding always passes the real Firestore-backed port; production never sees this.
 */
private object NoopReportPort : ReportPort {
    override suspend fun report(
        reporter: AccountId,
        target: ReportTarget,
        reason: ReportReason,
    ): Result<Unit, es.schsebastian.foodrats.core.domain.moderation.ReportError> = Result.success(Unit)
}

/** No-op [BlockedAccountsPort] default (see [NoopReportPort]). Reports nothing blocked. */
private object NoopBlockedAccountsPort : BlockedAccountsPort {
    override fun observeBlocked(owner: AccountId): kotlinx.coroutines.flow.Flow<Set<AccountId>> =
        flowOf(emptySet())
    override suspend fun block(
        owner: AccountId,
        target: AccountId,
    ): Result<Unit, es.schsebastian.foodrats.core.domain.account.BlockError> = Result.success(Unit)
    override suspend fun unblock(
        owner: AccountId,
        target: AccountId,
    ): Result<Unit, es.schsebastian.foodrats.core.domain.account.BlockError> = Result.success(Unit)
}
