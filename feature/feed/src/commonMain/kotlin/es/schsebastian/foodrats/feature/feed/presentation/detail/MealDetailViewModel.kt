package es.schsebastian.foodrats.feature.feed.presentation.detail

import androidx.lifecycle.viewModelScope
import es.schsebastian.foodrats.core.domain.account.Account
import es.schsebastian.foodrats.core.domain.account.AccountReadPort
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsEvent
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsPort
import es.schsebastian.foodrats.core.domain.analytics.NoopAnalyticsTracker
import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
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
import es.schsebastian.foodrats.feature.feed.presentation.components.toFeedUi
import es.schsebastian.foodrats.feature.feed.presentation.components.toRelative
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

class MealDetailViewModel(
    private val mealId: String,
    private val dayIso: String,
    observeFeed: ObserveFeedUseCase,
    private val rateMeal: RateMealUseCase,
    private val commentPort: MealCommentPort,
    private val accountReadPort: AccountReadPort,
    private val ingredientRead: IngredientReadPort,
    private val activeCrew: ActiveCrewProvider,
    private val session: SessionProvider,
    private val clock: Clock,
    private val zone: TimeZone,
    private val deleteMeal: DeleteMealUseCase,
    private val deleteMyMeal: DeleteMyMealUseCase,
    private val deleteComment: DeleteCommentUseCase,
    private val crewOwner: CrewOwnerPort,
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
                ) { r, catalog -> r to catalog }.collect { (r, catalog) ->
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
                                        )
                            val canDeleteMeal = match != null && viewerId != null &&
                                (match.authorId == viewerId.value || ownerId == viewerId)
                            update {
                                it.copy(
                                    isLoading = false,
                                    meal = match,
                                    notFound = match == null,
                                    error = null,
                                    canDeleteMeal = canDeleteMeal,
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
        val viewerId = session.current.first()?.accountId
        val ownerId = activeCrew.current.first()?.let { crewOwner.observeOwner(it).first() }
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
        commentsFlow
            .flatMapLatest { r ->
                when (r) {
                    is Result.Err -> flowOf(CommentRowsResult.Err(r.error))
                    is Result.Ok -> {
                        val uniqueIds = r.value.map { it.authorId }.toSet()
                        val perAuthorFlows = uniqueIds.map { id ->
                            authorFlows.getOrPut(id) {
                                accountReadPort.observe(id)
                                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
                            }
                        }
                        if (perAuthorFlows.isEmpty()) {
                            flowOf(CommentRowsResult.Ok(joinRows(r.value, emptyMap(), viewerId, ownerId)))
                        } else {
                            combine(perAuthorFlows) { snapshots ->
                                val map = uniqueIds.zip(snapshots.toList()).toMap()
                                CommentRowsResult.Ok(joinRows(r.value, map, viewerId, ownerId))
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

    private fun joinRows(
        comments: List<MealComment>,
        authors: Map<AccountId, Account?>,
        viewerId: AccountId?,
        ownerId: AccountId?,
    ): List<CommentRowUi> = comments.map { c ->
        val account = authors[c.authorId]
        val resolved = authors.containsKey(c.authorId)
        val isDeleted = resolved && account == null
        val canDelete = !isDeleted && viewerId != null &&
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
        update { it.copy(isPostingComment = true, commentWriteError = null) }
        val r = commentPort.post(crewId, parsedMealId, text)
        if (r is Result.Ok) analytics.track(AnalyticsEvent.CommentPosted(parsedMealId))
        update {
            when (r) {
                is Result.Ok  -> it.copy(isPostingComment = false, commentInput = "")
                is Result.Err -> it.copy(isPostingComment = false, commentWriteError = r.error)
            }
        }
    }
}
