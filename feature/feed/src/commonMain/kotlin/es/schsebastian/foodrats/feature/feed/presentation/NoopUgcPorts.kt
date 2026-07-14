package es.schsebastian.foodrats.feature.feed.presentation

import es.schsebastian.foodrats.core.domain.account.BlockError
import es.schsebastian.foodrats.core.domain.account.BlockedAccountsPort
import es.schsebastian.foodrats.core.domain.crew.CrewScoreStyle
import es.schsebastian.foodrats.core.domain.crew.CrewWelcomePort
import es.schsebastian.foodrats.core.domain.crew.WeeklyChallengeSnapshot
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.moderation.ReportError
import es.schsebastian.foodrats.core.domain.moderation.ReportPort
import es.schsebastian.foodrats.core.domain.moderation.ReportReason
import es.schsebastian.foodrats.core.domain.moderation.ReportTarget
import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * No-op [ReportPort] used as a constructor default so the existing test surface keeps compiling.
 * The Koin binding always passes the real Firestore-backed port; production never sees this.
 */
internal object NoopUgcReportPort : ReportPort {
    override suspend fun report(
        reporter: AccountId,
        target: ReportTarget,
        reason: ReportReason,
    ): Result<Unit, ReportError> = Result.success(Unit)
}

/** No-op [BlockedAccountsPort] default (see [NoopUgcReportPort]). Reports nothing blocked. */
internal object NoopUgcBlockedAccountsPort : BlockedAccountsPort {
    override fun observeBlocked(owner: AccountId): Flow<Set<AccountId>> = flowOf(emptySet())
    override suspend fun block(
        owner: AccountId,
        target: AccountId,
    ): Result<Unit, BlockError> = Result.success(Unit)
    override suspend fun unblock(
        owner: AccountId,
        target: AccountId,
    ): Result<Unit, BlockError> = Result.success(Unit)
}

/** No-op [CrewWelcomePort] default (see [NoopUgcReportPort] for rationale). Always Stars (C8). */
internal object NoopUgcCrewWelcomePort : CrewWelcomePort {
    override fun observeWelcomeMessage(crewId: CrewId): Flow<String?> = flowOf(null)
    override fun isWelcomeDismissed(crewId: CrewId): Flow<Boolean> = flowOf(false)
    override suspend fun dismissWelcome(crewId: CrewId) = Unit
    override fun observeWeeklyChallenge(crewId: CrewId): Flow<WeeklyChallengeSnapshot?> = flowOf(null)
    override fun observeScoreStyle(crewId: CrewId): Flow<CrewScoreStyle> = flowOf(CrewScoreStyle.Stars)
    override fun observeBannerImageUrl(crewId: CrewId): Flow<String?> = flowOf(null)
    override fun observeBannerCacheKey(crewId: CrewId): Flow<String> = flowOf("")
    override fun observeBannerFocalY(crewId: CrewId): Flow<Float> = flowOf(0.5f)
}
