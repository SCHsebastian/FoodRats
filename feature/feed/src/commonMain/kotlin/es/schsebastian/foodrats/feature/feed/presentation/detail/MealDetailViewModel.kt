package es.schsebastian.foodrats.feature.feed.presentation.detail

import androidx.lifecycle.viewModelScope
import es.schsebastian.foodrats.core.domain.account.Account
import es.schsebastian.foodrats.core.domain.account.AccountReadPort
import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.crew.CrewOwnerPort
import es.schsebastian.foodrats.core.domain.meal.IngredientReadPort
import es.schsebastian.foodrats.core.domain.meal.ingredientNameResolver
import es.schsebastian.foodrats.core.domain.meal.mergedIngredientSlugs
import es.schsebastian.foodrats.core.domain.meal.CommentError
import es.schsebastian.foodrats.core.domain.meal.CommentText
import es.schsebastian.foodrats.core.domain.meal.CommentValidationError
import es.schsebastian.foodrats.core.domain.meal.MealComment
import es.schsebastian.foodrats.core.domain.meal.MealCommentId
import es.schsebastian.foodrats.core.domain.meal.MealCommentPort
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealRatingPort
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.result.getOrElse
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.core.presentation.mvi.MviViewModel
import es.schsebastian.foodrats.feature.feed.domain.model.FeedDay
import es.schsebastian.foodrats.feature.feed.domain.usecase.DeleteCommentUseCase
import es.schsebastian.foodrats.feature.feed.domain.usecase.DeleteMealUseCase
import es.schsebastian.foodrats.feature.feed.domain.usecase.ObserveFeedUseCase
import es.schsebastian.foodrats.feature.feed.presentation.components.CommentRowUi
import es.schsebastian.foodrats.feature.feed.presentation.components.toFeedUi
import es.schsebastian.foodrats.feature.feed.presentation.components.toRelative
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
    private val ratingPort: MealRatingPort,
    private val commentPort: MealCommentPort,
    private val accountReadPort: AccountReadPort,
    private val ingredientRead: IngredientReadPort,
    private val activeCrew: ActiveCrewProvider,
    private val session: SessionProvider,
    private val clock: Clock,
    private val zone: TimeZone,
    private val deleteMeal: DeleteMealUseCase,
    private val deleteComment: DeleteCommentUseCase,
    private val crewOwner: CrewOwnerPort,
) : MviViewModel<MealDetailState, MealDetailIntent, MealDetailEffect>(MealDetailState()) {

    /** Deduped per-author flows: at most one active Firestore listener per unique authorId. */
    private val authorFlows = mutableMapOf<AccountId, SharedFlow<Account?>>()

    init {
        val parsedDay = runCatching { LocalDate.parse(dayIso) }.getOrNull()
        if (parsedDay == null) {
            update { it.copy(isLoading = false, notFound = true, commentsLoading = false) }
        } else {
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
                            val nameFor = ingredientNameResolver(catalog)
                            val match = if (viewerId == null || matched == null) null
                                        else matched.toFeedUi(
                                            viewerId,
                                            todayMealDay,
                                            ingredientNames = matched.meal.mergedIngredientSlugs().map(nameFor),
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

        commentsFlow.collect { r ->
            when (r) {
                is Result.Err -> update {
                    it.copy(commentsLoading = false, commentReadError = r.error)
                }
                is Result.Ok -> {
                    val uniqueIds = r.value.map { it.authorId }.toSet()
                    val perAuthorFlows = uniqueIds.map { id ->
                        authorFlows.getOrPut(id) {
                            accountReadPort.observe(id)
                                .stateIn(viewModelScope, SharingStarted.Eagerly, null)
                        }
                    }
                    val joined: Flow<List<CommentRowUi>> = if (perAuthorFlows.isEmpty()) {
                            flowOf(joinRows(r.value, emptyMap(), viewerId, ownerId))
                        } else {
                            combine(perAuthorFlows) { snapshots ->
                                val map = uniqueIds.zip(snapshots.toList()).toMap()
                                joinRows(r.value, map, viewerId, ownerId)
                            }
                        }
                    joined.collect { rows ->
                        update {
                            it.copy(commentRows = rows, commentsLoading = false, commentReadError = null)
                        }
                    }
                }
            }
        }
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
        is MealDetailIntent.RateMeal               -> rateMeal(intent.score)
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
        val crewId = activeCrew.current.first() ?: return
        val parsedMealId = MealId.of(mealId).getOrElse { return }
        update { it.copy(isDeletingMeal = true, mealDeleteError = null) }
        val r = deleteMeal(crewId, parsedMealId)
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

    private suspend fun rateMeal(scoreRaw: Int) {
        val crewId = activeCrew.current.first() ?: return
        val parsedMealId = MealId.of(mealId).getOrElse { return }
        val score = Score.of(scoreRaw).getOrElse { return }
        update { it.copy(pendingRate = true, rateError = null) }
        val r = ratingPort.rate(crewId, parsedMealId, score)
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
        update {
            when (r) {
                is Result.Ok  -> it.copy(isPostingComment = false, commentInput = "")
                is Result.Err -> it.copy(isPostingComment = false, commentWriteError = r.error)
            }
        }
    }
}
