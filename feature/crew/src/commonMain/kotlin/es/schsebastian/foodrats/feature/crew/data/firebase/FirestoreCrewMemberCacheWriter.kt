package es.schsebastian.foodrats.feature.crew.data.firebase

import es.schsebastian.foodrats.core.domain.crew.CrewMemberCacheWriteError
import es.schsebastian.foodrats.core.domain.crew.CrewMemberCacheWritePort
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.result.mapError

/**
 * [CrewMemberCacheWritePort] implementation backed by [CrewFirestoreDataSource]. Maps the
 * crew-specific [CrewError] to the narrower [CrewMemberCacheWriteError] expected by the
 * port — keeping crew's error vocabulary out of :core:domain.
 */
class FirestoreCrewMemberCacheWriter(
    private val dataSource: CrewFirestoreDataSource,
) : CrewMemberCacheWritePort {

    override suspend fun setDisplayName(
        crewId: CrewId,
        accountId: AccountId,
        name: String,
    ): Result<Unit, CrewMemberCacheWriteError> =
        dataSource.setMemberDisplayName(crewId, accountId, name)
            .mapError { CrewMemberCacheWriteError.Backend.Unavailable }

    override suspend fun setAvatarUrl(
        crewId: CrewId,
        accountId: AccountId,
        url: String,
    ): Result<Unit, CrewMemberCacheWriteError> =
        dataSource.updateMemberAvatarUrl(crewId, accountId, url)
            .mapError { CrewMemberCacheWriteError.Backend.Unavailable }
}
