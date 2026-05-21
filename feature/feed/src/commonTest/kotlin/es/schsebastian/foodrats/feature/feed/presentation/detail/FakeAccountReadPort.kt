package es.schsebastian.foodrats.feature.feed.presentation.detail

import es.schsebastian.foodrats.core.domain.account.Account
import es.schsebastian.foodrats.core.domain.account.AccountReadPort
import es.schsebastian.foodrats.core.domain.model.AccountId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAccountReadPort : AccountReadPort {
    private val flows = mutableMapOf<AccountId, MutableStateFlow<Account?>>()
    val observeCount = mutableMapOf<AccountId, Int>()

    override fun observe(id: AccountId): Flow<Account?> {
        observeCount[id] = (observeCount[id] ?: 0) + 1
        return flows.getOrPut(id) { MutableStateFlow(null) }
    }

    fun set(id: AccountId, account: Account?) {
        flows.getOrPut(id) { MutableStateFlow(null) }.value = account
    }
}
