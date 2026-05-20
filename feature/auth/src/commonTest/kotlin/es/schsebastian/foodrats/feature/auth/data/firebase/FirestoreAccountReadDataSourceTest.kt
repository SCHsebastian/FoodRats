package es.schsebastian.foodrats.feature.auth.data.firebase

import app.cash.turbine.test
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FirestoreAccountReadDataSourceTest {
    private val source = FakeAccountSource()
    private val sut = FirestoreAccountReadDataSource(source)
    private val id = (AccountId.of("user-1") as Result.Ok).value

    @Test fun emits_null_when_doc_missing() = runTest {
        sut.observe(id).test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun maps_dto_to_account_when_doc_present() = runTest {
        sut.observe(id).test {
            assertNull(awaitItem())
            source.emit("user-1", AccountDto(id = "user-1", displayName = "Sebas", avatarUrl = "https://x/a.jpg"))
            val acc = awaitItem()
            assertEquals("Sebas", acc?.displayName)
            assertEquals("https://x/a.jpg", acc?.avatarUrl)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun re_emits_on_doc_update() = runTest {
        sut.observe(id).test {
            assertNull(awaitItem())
            source.emit("user-1", AccountDto(id = "user-1", displayName = "Old"))
            assertEquals("Old", awaitItem()?.displayName)
            source.emit("user-1", AccountDto(id = "user-1", displayName = "New"))
            assertEquals("New", awaitItem()?.displayName)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
