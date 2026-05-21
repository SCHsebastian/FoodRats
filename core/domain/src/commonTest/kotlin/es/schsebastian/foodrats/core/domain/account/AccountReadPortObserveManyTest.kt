package es.schsebastian.foodrats.core.domain.account

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountReadPortObserveManyTest {

    private fun aid(raw: String): AccountId = (AccountId.of(raw) as Result.Ok).value
    private fun acc(id: AccountId, name: String): Account =
        Account(id = id, handle = name.lowercase(), displayName = name, email = null, avatarUrl = null)

    private class FakePort(private val store: Map<AccountId, MutableStateFlow<Account?>>) : AccountReadPort {
        override fun observe(id: AccountId): Flow<Account?> =
            store[id] ?: MutableStateFlow(null)
    }

    @Test
    fun empty_id_set_emits_empty_map() = runTest {
        val port = FakePort(emptyMap())
        assertEquals(emptyMap(), port.observeMany(emptySet()).first())
    }

    @Test
    fun resolves_each_id_to_its_current_account_value() = runTest {
        val a = aid("a")
        val b = aid("b")
        val port = FakePort(
            mapOf(
                a to MutableStateFlow<Account?>(acc(a, "Alice")),
                b to MutableStateFlow<Account?>(acc(b, "Bob")),
            ),
        )

        val map = port.observeMany(setOf(a, b)).first()
        assertEquals(2, map.size)
        assertEquals("Alice", map[a]?.displayName)
        assertEquals("Bob", map[b]?.displayName)
    }

    @Test
    fun missing_account_maps_to_null() = runTest {
        val a = aid("a")
        val ghost = aid("ghost")
        val port = FakePort(mapOf(a to MutableStateFlow<Account?>(acc(a, "Alice"))))

        val map = port.observeMany(setOf(a, ghost)).first()
        assertEquals("Alice", map[a]?.displayName)
        assertEquals(null, map[ghost])
    }
}
