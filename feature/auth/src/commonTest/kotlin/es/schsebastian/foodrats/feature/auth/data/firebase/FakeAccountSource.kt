package es.schsebastian.foodrats.feature.auth.data.firebase

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeAccountSource : AccountSnapshotSource {
    private val flows = mutableMapOf<String, MutableStateFlow<AccountDto?>>()
    override fun snapshots(uid: String): StateFlow<AccountDto?> =
        flows.getOrPut(uid) { MutableStateFlow(null) }
    fun emit(uid: String, dto: AccountDto?) {
        flows.getOrPut(uid) { MutableStateFlow(null) }.value = dto
    }
}
