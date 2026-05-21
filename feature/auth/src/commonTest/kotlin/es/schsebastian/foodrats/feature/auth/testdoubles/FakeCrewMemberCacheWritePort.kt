package es.schsebastian.foodrats.feature.auth.testdoubles

import es.schsebastian.foodrats.core.domain.crew.CrewMemberCacheWriteError
import es.schsebastian.foodrats.core.domain.crew.CrewMemberCacheWritePort
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result

class FakeCrewMemberCacheWritePort : CrewMemberCacheWritePort {
    val setDisplayNameCalls: MutableList<Triple<CrewId, AccountId, String>> = mutableListOf()
    val setAvatarUrlCalls: MutableList<Triple<CrewId, AccountId, String>> = mutableListOf()
    var nextDisplayNameError: CrewMemberCacheWriteError? = null
    var nextAvatarUrlError: CrewMemberCacheWriteError? = null

    override suspend fun setDisplayName(
        crewId: CrewId,
        accountId: AccountId,
        name: String,
    ): Result<Unit, CrewMemberCacheWriteError> {
        setDisplayNameCalls += Triple(crewId, accountId, name)
        return nextDisplayNameError?.let { Result.failure(it) } ?: Result.success(Unit)
    }

    override suspend fun setAvatarUrl(
        crewId: CrewId,
        accountId: AccountId,
        url: String,
    ): Result<Unit, CrewMemberCacheWriteError> {
        setAvatarUrlCalls += Triple(crewId, accountId, url)
        return nextAvatarUrlError?.let { Result.failure(it) } ?: Result.success(Unit)
    }
}
