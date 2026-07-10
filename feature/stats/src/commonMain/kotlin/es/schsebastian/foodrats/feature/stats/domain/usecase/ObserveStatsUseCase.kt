package es.schsebastian.foodrats.feature.stats.domain.usecase

import es.schsebastian.foodrats.core.domain.account.BlockedAccountsPort
import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.crew.CrewScoreStyle
import es.schsebastian.foodrats.core.domain.crew.CrewWelcomePort
import es.schsebastian.foodrats.core.domain.crew.WeeklyChallengeSnapshot
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.cuisine.Cuisine
import es.schsebastian.foodrats.core.domain.cuisine.CuisinePassport
import es.schsebastian.foodrats.core.domain.cuisine.CuisineReadPort
import es.schsebastian.foodrats.core.domain.cuisine.CuisineSlug
import es.schsebastian.foodrats.core.domain.cuisine.deriveCuisinePassport
import es.schsebastian.foodrats.core.domain.meal.IngredientBingo
import es.schsebastian.foodrats.core.domain.meal.Ingredient
import es.schsebastian.foodrats.core.domain.meal.IngredientReadPort
import es.schsebastian.foodrats.core.domain.meal.IngredientSlug
import es.schsebastian.foodrats.core.domain.meal.deriveIngredientBingo
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealReadError
import es.schsebastian.foodrats.core.domain.meal.MealReadPort
import es.schsebastian.foodrats.core.domain.meal.MealWithRatings
import es.schsebastian.foodrats.core.domain.meal.ingredientNameResolver
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.stats.domain.compute.computeHeroStats
import es.schsebastian.foodrats.feature.stats.domain.compute.computeWindow
import es.schsebastian.foodrats.feature.stats.domain.compute.currentRangeFor
import es.schsebastian.foodrats.feature.stats.domain.compute.daysInclusive
import es.schsebastian.foodrats.feature.stats.domain.compute.startOfIsoWeek
import es.schsebastian.foodrats.feature.stats.domain.compute.startOfMonth
import es.schsebastian.foodrats.feature.stats.domain.error.StatsError
import es.schsebastian.foodrats.feature.stats.domain.model.StatsSnapshot
import es.schsebastian.foodrats.feature.stats.domain.model.StatsWindow
import es.schsebastian.foodrats.feature.stats.domain.model.Tab
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

class ObserveStatsUseCase(
    private val activeCrew: ActiveCrewProvider,
    private val session: SessionProvider,
    private val mealRead: MealReadPort,
    private val ingredientRead: IngredientReadPort,
    private val cuisineRead: CuisineReadPort,
    private val blockedAccounts: BlockedAccountsPort,
    private val clock: Clock,
    private val zone: TimeZone,
    // C8b — active crew's Score vocabulary so the stats leaderboard cards render Stars/Emoji/Numeric.
    // DEFAULTED so all existing use-case tests stay green without constructor changes.
    private val welcomePort: CrewWelcomePort = NoopStatsWelcomePort,
    // CONSCIOUS EXCEPTION to "dispatchers live in repositories only" (root CLAUDE.md §6): `compose()`
    // re-runs the full week+month+365-day-historic computation (streaks, leaderboards, passport,
    // bingo over up to a year of MealWithRatings) inside the combine transform below, which would
    // otherwise execute on the collector's dispatcher — the ViewModel's Main. That is pure CPU
    // compute over already-fetched domain data, NOT I/O, so the repository boundary doesn't cover
    // it; hopping inside the MealReadPort impl instead would push presentation-shaped compute into
    // the data layer. Injectable (defaulted to Default) only so tests can pass a test dispatcher
    // and stay deterministic.
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(
        historicEnabled: Flow<Boolean>,
        epoch: Flow<Int>,
    ): Flow<Result<StatsSnapshot, StatsError>> =
        combine(activeCrew.current, session.current, epoch) { c, s, _ -> c to s }
            .flatMapLatest { (crewId, sess) ->
                when {
                    sess == null   -> flowOf(Result.failure(StatsError.Session.NotSignedIn))
                    crewId == null -> flowOf(Result.failure(StatsError.Session.NoActiveCrew))
                    else -> {
                        // `today` is captured per (re)start; a manual `epoch` refresh rebuilds it.
                        val today: LocalDate = clock.now().toLocalDateTime(zone).date
                        val (fromCurrent, _) = currentRangeFor(today)
                        val current = mealRead.observeRange(
                            crewId,
                            MealDay(fromCurrent, zone),
                            MealDay(today, zone),
                        )
                        val historic: Flow<HistoricResult> = historicEnabled
                            .distinctUntilChanged()
                            .flatMapLatest { enabled ->
                                if (!enabled) flowOf(HistoricResult.Disabled)
                                else mealRead.observeRange(
                                    crewId,
                                    MealDay(today.minus(DatePeriod(days = 365)), zone),
                                    MealDay(today, zone),
                                ).map { r ->
                                    when (r) {
                                        is Result.Ok  -> HistoricResult.Ok(r.value)
                                        is Result.Err -> HistoricResult.Err(r.error.toStatsError())
                                    }
                                }
                            }
                        val catalog: Flow<Map<IngredientSlug, Ingredient>> = ingredientRead.observeCatalog()
                        val cuisineCatalog: Flow<Map<CuisineSlug, Cuisine>> = cuisineRead.observeCatalog()
                        // UGC compliance §5 — blocked authors are excluded from every ranking. The set
                        // is combined in so a new block re-emits and recomputes the leaderboards.
                        val blocked: Flow<Set<AccountId>> = blockedAccounts.observeBlocked(sess.accountId)
                        // C8b — Score style: re-subscribes on crew switch; defaults Stars when absent.
                        val scoreStyle: Flow<CrewScoreStyle> = welcomePort.observeScoreStyle(crewId)
                        // Chain the 6th flow via a nested combine so we stay on the typed 5-arity
                        // overloads (the vararg overload works but needs unchecked casts).
                        val baseFlow = combine(current, historic, catalog, cuisineCatalog, blocked) { c, h, cat, cuisines, blockedSet ->
                            Quintuple(c, h, cat, cuisines, blockedSet)
                        }
                        combine(baseFlow, scoreStyle) { q, style ->
                            when (val c = q.a) {
                                is Result.Err -> Result.failure(c.error.toStatsError())
                                is Result.Ok  -> Result.success(
                                    compose(c.value, q.b, today, sess.accountId, q.c, q.d, q.e, style),
                                )
                            }
                        }
                            // Keeps the heavy `compose()` transform off the ViewModel's Main —
                            // see the `computeDispatcher` constructor comment for the rationale.
                            .flowOn(computeDispatcher)
                    }
                }
            }

    private fun compose(
        currentMealsRaw: List<MealWithRatings>,
        historic: HistoricResult,
        today: LocalDate,
        accountId: AccountId,
        ingredientCatalog: Map<IngredientSlug, Ingredient>,
        cuisineCatalog: Map<CuisineSlug, Cuisine>,
        blocked: Set<AccountId>,
        scoreStyle: CrewScoreStyle = CrewScoreStyle.Stars,
    ): StatsSnapshot {
        // UGC compliance §5 — drop blocked authors' meals BEFORE any ranking is computed so they
        // never appear in streaks / leaderboards / collections.
        val currentMeals = currentMealsRaw.filterNot { it.meal.author.accountId in blocked }
        val historicMeals: List<MealWithRatings>? = (historic as? HistoricResult.Ok)?.meals
            ?.filterNot { it.meal.author.accountId in blocked }
        val historicError: StatsError? = (historic as? HistoricResult.Err)?.error
        val nameFor = ingredientNameResolver(ingredientCatalog)
        val weekFrom = startOfIsoWeek(today)
        val monthFrom = startOfMonth(today)
        val weekWindow = StatsWindow(Tab.Week, weekFrom, today, daysInclusive(weekFrom, today))
        val monthWindow = StatsWindow(Tab.Month, monthFrom, today, daysInclusive(monthFrom, today))
        val historicWindow = StatsWindow(
            tab = Tab.Historic,
            from = today.minus(DatePeriod(days = 365)),
            to = today,
            days = 366,
        )
        val weekMeals = currentMeals.filter { it.meal.day.date in weekWindow.from..weekWindow.to }
        val monthMeals = currentMeals.filter { it.meal.day.date in monthWindow.from..monthWindow.to }
        // Collections (passport + bingo): both derive over the signed-in member's OWN meals across
        // the loaded window. Each is `null` until its catalog has emitted (an empty catalog has no
        // cells to render). Compute the member's-own-meals list once and reuse it.
        val myMeals = (historicMeals ?: currentMeals)
            .filter { it.meal.author.accountId == accountId }
            .map { it.meal }
        // Passport reads the STAMPED Meal.cuisine (always confirmed — stamped at publish).
        val cuisinePassport: CuisinePassport? = if (cuisineCatalog.isEmpty()) {
            null
        } else {
            deriveCuisinePassport(catalog = cuisineCatalog, confirmedMeals = myMeals)
        }
        // Bingo reads the CONFIRMED Meal.ingredients only (AI detectedIngredients excluded — §2.3).
        val ingredientBingo: IngredientBingo? = if (ingredientCatalog.isEmpty()) {
            null
        } else {
            deriveIngredientBingo(catalog = ingredientCatalog, meals = myMeals)
        }
        return StatsSnapshot(
            hero = computeHeroStats(currentMeals, accountId, today),
            week = computeWindow(weekMeals, weekWindow, nameFor),
            month = computeWindow(monthMeals, monthWindow, nameFor),
            historic = historicMeals?.let { computeWindow(it, historicWindow, nameFor) },
            historicError = historicError,
            cuisinePassport = cuisinePassport,
            ingredientBingo = ingredientBingo,
            scoreStyle = scoreStyle,
        )
    }
}

/** Local 5-tuple carrier used to chain a 6th flow without the unchecked-cast vararg overload. */
private data class Quintuple<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)

/**
 * Outcome of the Historic-tab 365-day read, threaded through the combine so a failure is surfaced
 * (as [StatsSnapshot.historicError]) instead of being collapsed into `null` (which the UI can't
 * tell apart from "not loaded yet" — that was the swallowed-error bug).
 */
private sealed interface HistoricResult {
    /** The Historic tab has not been opened yet (sticky toggle is still false). */
    data object Disabled : HistoricResult
    data class Ok(val meals: List<MealWithRatings>) : HistoricResult
    data class Err(val error: StatsError) : HistoricResult
}

private fun MealReadError.toStatsError() = when (this) {
    MealReadError.Unauthorized -> StatsError.Read.Unauthorized
    MealReadError.CrewNotFound -> StatsError.Read.CrewNotFound
    MealReadError.Unavailable  -> StatsError.Read.Unavailable
}

/**
 * No-op [CrewWelcomePort] default for [ObserveStatsUseCase] — always emits [CrewScoreStyle.Stars]
 * so existing use-case tests stay green without constructor changes (C8b).
 */
private object NoopStatsWelcomePort : CrewWelcomePort {
    override fun observeWelcomeMessage(crewId: CrewId): Flow<String?> = flowOf(null)
    override fun isWelcomeDismissed(crewId: CrewId): Flow<Boolean> = flowOf(false)
    override suspend fun dismissWelcome(crewId: CrewId) = Unit
    override fun observeWeeklyChallenge(crewId: CrewId): Flow<WeeklyChallengeSnapshot?> = flowOf(null)
    override fun observeScoreStyle(crewId: CrewId): Flow<CrewScoreStyle> = flowOf(CrewScoreStyle.Stars)
    override fun observeBannerImageUrl(crewId: CrewId): Flow<String?> = flowOf(null)
    override fun observeBannerCacheKey(crewId: CrewId): Flow<String> = flowOf("")
    override fun observeBannerFocalY(crewId: CrewId): Flow<Float> = flowOf(0.5f)
}
