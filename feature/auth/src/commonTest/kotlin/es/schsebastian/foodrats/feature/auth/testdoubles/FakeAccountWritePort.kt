package es.schsebastian.foodrats.feature.auth.testdoubles

import es.schsebastian.foodrats.core.domain.account.AccountWriteError
import es.schsebastian.foodrats.core.domain.account.AccountWritePort
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.Result

class FakeAccountWritePort : AccountWritePort {
    val displayNameCalls: MutableList<Pair<AccountId, String>> = mutableListOf()
    val avatarUploads: MutableList<Pair<AccountId, ByteArray>> = mutableListOf()
    var nextDisplayNameError: AccountWriteError? = null
    var nextAvatarError: AccountWriteError? = null
    var nextAvatarUrl: String = "https://example.test/avatar.jpg"

    override suspend fun updateDisplayName(
        accountId: AccountId,
        name: String,
    ): Result<Unit, AccountWriteError> {
        displayNameCalls += accountId to name
        return nextDisplayNameError?.let { Result.failure(it) } ?: Result.success(Unit)
    }

    override suspend fun uploadAndSetAvatar(
        accountId: AccountId,
        bytes: ByteArray,
    ): Result<String, AccountWriteError> {
        avatarUploads += accountId to bytes
        return nextAvatarError?.let { Result.failure(it) } ?: Result.success(nextAvatarUrl)
    }
}
