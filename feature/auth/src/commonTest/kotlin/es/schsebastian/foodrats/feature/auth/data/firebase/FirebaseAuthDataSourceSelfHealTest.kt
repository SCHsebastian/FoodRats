package es.schsebastian.foodrats.feature.auth.data.firebase

import es.schsebastian.foodrats.core.domain.time.FixedClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class FirebaseAuthDataSourceSelfHealTest {

    private val store = FakeAccountDocStore()
    private val clock = FixedClock(Instant.fromEpochMilliseconds(1_000_000L))

    private fun sut(name: String = "Sebas H") = AccountDocSelfHealer(
        store = store,
        clock = clock,
        googleName = { name },
    )

    @Test
    fun creates_account_with_google_name_when_doc_missing() = runTest {
        sut().ensureAccountDoc(uid = "u-1")
        assertEquals("Sebas H", store.read("u-1")?.displayName)
    }

    @Test
    fun overwrites_rat_placeholder_with_google_name() = runTest {
        store.write("u-1", AccountDto(id = "u-1", handle = "u-1", displayName = "Rat"))
        sut().ensureAccountDoc(uid = "u-1")
        assertEquals("Sebas H", store.read("u-1")?.displayName)
    }

    @Test
    fun overwrites_blank_displayName_with_google_name() = runTest {
        store.write("u-1", AccountDto(id = "u-1", handle = "u-1", displayName = ""))
        sut().ensureAccountDoc(uid = "u-1")
        assertEquals("Sebas H", store.read("u-1")?.displayName)
    }

    @Test
    fun leaves_user_chosen_displayName_alone() = runTest {
        store.write("u-1", AccountDto(id = "u-1", handle = "u-1", displayName = "MyAlias"))
        sut().ensureAccountDoc(uid = "u-1")
        assertEquals("MyAlias", store.read("u-1")?.displayName)
    }
}

private class FakeAccountDocStore : AccountDocStore {
    private val docs = mutableMapOf<String, AccountDto>()
    override suspend fun read(uid: String): AccountDto? = docs[uid]
    override suspend fun upsert(uid: String, dto: AccountDto) { docs[uid] = dto }
    fun write(uid: String, dto: AccountDto) { docs[uid] = dto }
}
