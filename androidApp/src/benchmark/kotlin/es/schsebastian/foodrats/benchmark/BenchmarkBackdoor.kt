package es.schsebastian.foodrats.benchmark

import es.schsebastian.foodrats.core.domain.analytics.AnalyticsConfig
import es.schsebastian.foodrats.core.domain.analytics.ConsentDecision
import es.schsebastian.foodrats.core.domain.analytics.ConsentPort
import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.meal.Description
import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.meal.Meal
import es.schsebastian.foodrats.core.domain.meal.MealAuthor
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealRating
import es.schsebastian.foodrats.core.domain.meal.MealReadError
import es.schsebastian.foodrats.core.domain.meal.MealReadPort
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.meal.MealWithRatings
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.preferences.EulaPort
import es.schsebastian.foodrats.core.domain.preferences.NoopEulaAcceptance
import es.schsebastian.foodrats.core.domain.preferences.NotificationsPreferenceError
import es.schsebastian.foodrats.core.domain.preferences.NotificationsPreferencePort
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.result.getOrNull
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.core.domain.session.SessionError
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.time.Instant

// ===========================================================================
// BENCHMARK-ONLY FAKE SESSION BACKDOOR (STARTUP-4).
//
// SECURITY-CRITICAL: this whole file is compiled ONLY into the `benchmark`
// build type (it lives in androidApp/src/benchmark/, which AGP attaches solely
// to that build-type variant). It is NEVER part of `release` or `debug`, so the
// fake signed-in session below is unreachable from any shipped or local app —
// the only thing that installs it is [BenchmarkBackdoorInstaller], a
// ContentProvider declared only in androidApp/src/benchmark/AndroidManifest.xml.
//
// Purpose: let the :baselineprofile macrobenchmark drive a populated, authed
// Feed WITHOUT real Google Sign-In or any network, so the Baseline Profile
// captures the real authenticated-startup code path (RootNav → Main → Feed).
//
// It reuses the REAL :core:domain ports (SessionProvider, ActiveCrewProvider,
// MealReadPort, NotificationsPreferencePort, ConsentPort, EulaPort) — see
// RootNavViewModel.observeStage(): all six gates must resolve to "signed in,
// prompted, has crew, consent settled, EULA accepted" for the app to reach
// RootStage.Ready and land on Main/Feed.
// ===========================================================================

/** Fixed identifiers the whole fake graph shares. Value-class ctors are internal → build via `of`. */
internal object BenchmarkSeed {
    val accountId: AccountId = AccountId.of("benchmark-account").getOrNull()!!
    val crewId: CrewId = CrewId.of("benchmark-crew").getOrNull()!!
    val session: Session = Session(accountId = accountId, activeCrewId = crewId)
}

/** Always signed in, immediately — first emission is authoritative (SessionProvider contract). */
internal object FakeSessionProvider : SessionProvider {
    override val current: Flow<Session?> = flowOf(BenchmarkSeed.session)
    override suspend fun requireCurrent(): Result<Session, SessionError> =
        Result.success(BenchmarkSeed.session)
}

/** Fixed active crew so the Feed has a crew to query and RootNav clears the NeedsCrew gate. */
internal object FakeActiveCrewProvider : ActiveCrewProvider {
    override val current: Flow<CrewId?> = flowOf(BenchmarkSeed.crewId)
    override suspend fun set(crewId: CrewId) = Unit
    override suspend fun clear() = Unit
}

/** Already prompted → clears RootNav's post-signin notification-permission gate. */
internal object FakeNotificationsPreference : NotificationsPreferencePort {
    override val enabled: Flow<Boolean> = flowOf(true)
    override val prompted: Flow<Boolean> = flowOf(true)
    override suspend fun set(enabled: Boolean): Result<Unit, NotificationsPreferenceError> =
        Result.success(Unit)
    override suspend fun markPrompted(): Result<Unit, NotificationsPreferenceError> =
        Result.success(Unit)
}

/** Current-version grant → consent gate is settled (needsDecision == false). */
internal object FakeConsentPort : ConsentPort {
    override val decision: Flow<ConsentDecision> = flowOf(
        ConsentDecision.Granted(
            version = AnalyticsConfig.CURRENT_CONSENT_VERSION,
            at = Instant.fromEpochSeconds(BENCHMARK_EPOCH_SECONDS),
        ),
    )
    override suspend fun grant() = Unit
    override suspend fun deny() = Unit
    override suspend fun revoke() = Unit
}

/**
 * Seeded, network-free Feed read path. Ignores the requested day/range and returns a few stable
 * meals stamped with the queried [MealDay] so the Feed renders immediately. Image URLs are empty —
 * cards fall back to their placeholder, keeping the journey offline (no Coil network fetch).
 */
internal object SeededMealReadPort : MealReadPort {
    override fun observeFeed(
        crewId: CrewId,
        day: MealDay,
    ): Flow<Result<List<MealWithRatings>, MealReadError>> =
        flowOf(Result.success(seededMeals(crewId, day)))

    override fun observeRange(
        crewId: CrewId,
        from: MealDay,
        to: MealDay,
    ): Flow<Result<List<MealWithRatings>, MealReadError>> =
        flowOf(Result.success(seededMeals(crewId, from)))

    private fun seededMeals(crewId: CrewId, day: MealDay): List<MealWithRatings> =
        (0 until SEED_MEAL_COUNT).map { n -> seededMeal(crewId, day, n) }

    private fun seededMeal(crewId: CrewId, day: MealDay, n: Int): MealWithRatings {
        val author = MealAuthor(
            accountId = AccountId.of("benchmark-author-$n").getOrNull()!!,
            displayName = "Benchmark Chef $n",
            avatarUrl = null,
        )
        val meal = Meal(
            id = MealId.of("benchmark-meal-$n").getOrNull()!!,
            author = author,
            crewId = crewId,
            day = day,
            slot = MealSlot.entries[n % MealSlot.entries.size],
            // Empty so the feed renders its placeholder instead of fetching over the network.
            photoUrl = "",
            dish = DishName.of("Seeded Dish $n").getOrNull()!!,
            description = Description.EMPTY,
            publishedAt = Instant.fromEpochSeconds(BENCHMARK_EPOCH_SECONDS + n),
        )
        val ratings = listOf(
            MealRating(
                raterId = BenchmarkSeed.accountId,
                raterDisplayName = "Benchmark Rater",
                raterAvatarUrl = null,
                score = Score.of((n % Score.MAX) + 1).getOrNull()!!,
                ratedAt = Instant.fromEpochSeconds(BENCHMARK_EPOCH_SECONDS + 100 + n),
            ),
        )
        return MealWithRatings(meal = meal, ratings = ratings)
    }
}

private const val SEED_MEAL_COUNT = 6
private const val BENCHMARK_EPOCH_SECONDS = 1_700_000_000L

/**
 * Koin override loaded by [BenchmarkBackdoorInstaller] AFTER the real graph starts. Each `single`
 * re-binds the same domain port the feature modules bind, so Koin replaces the real definition
 * (override is allowed by default) and every subsequent resolution — RootNavViewModel, FeedViewModel —
 * gets the fake. EULA reuses the domain's [NoopEulaAcceptance] (it pre-accepts CURRENT_EULA_VERSION).
 */
val benchmarkSessionOverrideModule: Module = module {
    single<SessionProvider> { FakeSessionProvider }
    single<ActiveCrewProvider> { FakeActiveCrewProvider }
    single<NotificationsPreferencePort> { FakeNotificationsPreference }
    single<ConsentPort> { FakeConsentPort }
    single<EulaPort> { NoopEulaAcceptance }
    single<MealReadPort> { SeededMealReadPort }
}
