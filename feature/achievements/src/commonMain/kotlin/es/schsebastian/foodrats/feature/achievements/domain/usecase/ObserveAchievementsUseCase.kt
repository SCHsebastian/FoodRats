package es.schsebastian.foodrats.feature.achievements.domain.usecase

import es.schsebastian.foodrats.core.domain.achievement.AchievementProgressError
import es.schsebastian.foodrats.core.domain.achievement.AchievementProgressPort
import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealReadError
import es.schsebastian.foodrats.core.domain.meal.MealReadPort
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.achievements.domain.AchievementCatalog
import es.schsebastian.foodrats.feature.achievements.domain.AchievementEvaluator
import es.schsebastian.foodrats.feature.achievements.domain.AchievementReconciler
import es.schsebastian.foodrats.feature.achievements.domain.AchievementSignalsBuilder
import es.schsebastian.foodrats.feature.achievements.domain.error.AchievementError
import es.schsebastian.foodrats.feature.achievements.domain.model.AchievementStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.milliseconds

/**
 * Result of one evaluation pass: the [accountId] (so the ViewModel can persist against the right
 * member) and the reconciled [statuses] (persisted unlock dates already overlaid). A status that is
 * `progress.isMet && unlockedAtEpochMs == null` is newly-met → the ViewModel persists it once and
 * celebrates (spec §8.2). Keeping persistence in the ViewModel (not here) preserves the rule that a
 * use case is pure orchestration with no side effects.
 */
data class AchievementsSnapshot(
    val accountId: AccountId,
    val statuses: List<AchievementStatus>,
)

/**
 * Pure orchestration (spec §7, §8.2): combines the active crew + session, observes the crew's
 * 365-day meal window and the member's persisted unlocks, debounces a snapshot burst into one
 * evaluation, builds [AchievementSignals], runs the pure [AchievementEvaluator], overlays persisted
 * dates via [AchievementReconciler], and emits a [AchievementsSnapshot]. No `withContext` — the IO
 * boundary lives in the ports' repositories.
 */
class ObserveAchievementsUseCase(
    private val activeCrew: ActiveCrewProvider,
    private val session: SessionProvider,
    private val mealRead: MealReadPort,
    private val progress: AchievementProgressPort,
    private val evaluator: AchievementEvaluator,
    private val reconciler: AchievementReconciler,
    private val signalsBuilder: AchievementSignalsBuilder,
    private val clock: Clock,
    private val zone: TimeZone,
) {
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    operator fun invoke(): Flow<Result<AchievementsSnapshot, AchievementError>> =
        combine(activeCrew.current, session.current) { crewId, sess -> crewId to sess }
            .flatMapLatest { (crewId, sess) ->
                when {
                    sess == null -> flowOf(Result.failure(AchievementError.Session.NotSignedIn))
                    crewId == null -> flowOf(Result.failure(AchievementError.Session.NoActiveCrew))
                    else -> {
                        val today: LocalDate = clock.now().toLocalDateTime(zone).date
                        val range = mealRead.observeRange(
                            crewId,
                            MealDay(today.minus(DatePeriod(days = 365)), zone),
                            MealDay(today, zone),
                        )
                        val unlocks = progress.observeUnlocks(sess.accountId)
                        combine(range, unlocks) { mealsResult, unlocksResult ->
                            when (mealsResult) {
                                is Result.Err -> Result.failure(mealsResult.error.toAchievementError())
                                is Result.Ok -> when (unlocksResult) {
                                    is Result.Err -> Result.failure(unlocksResult.error.toAchievementError())
                                    is Result.Ok -> {
                                        val signals = signalsBuilder.build(sess.accountId, mealsResult.value, today)
                                        val evaluated = evaluator.evaluate(AchievementCatalog.all, signals)
                                        val reconciled = reconciler.reconcile(
                                            evaluated = evaluated,
                                            persisted = unlocksResult.value,
                                            now = clock.now().toEpochMilliseconds(),
                                        )
                                        Result.success(
                                            AchievementsSnapshot(sess.accountId, reconciled.statuses),
                                        )
                                    }
                                }
                            }
                        }.debounce(400.milliseconds)
                    }
                }
            }
}

private fun MealReadError.toAchievementError(): AchievementError = when (this) {
    MealReadError.Unauthorized -> AchievementError.Read.Unauthorized
    MealReadError.CrewNotFound -> AchievementError.Read.Unavailable
    MealReadError.Unavailable -> AchievementError.Read.Unavailable
}

private fun AchievementProgressError.toAchievementError(): AchievementError = when (this) {
    AchievementProgressError.Unauthorized -> AchievementError.Read.Unauthorized
    AchievementProgressError.Unavailable -> AchievementError.Read.Unavailable
}
