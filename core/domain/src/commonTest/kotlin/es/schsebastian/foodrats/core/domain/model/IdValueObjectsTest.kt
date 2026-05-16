package es.schsebastian.foodrats.core.domain.model

import es.schsebastian.foodrats.core.domain.result.Result
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IdValueObjectsTest {
    @Test fun accountId_rejects_blank() {
        val r = AccountId.of("   ")
        assertTrue(r is Result.Err)
    }
    @Test fun accountId_accepts_nonblank() {
        val r = AccountId.of("abc123")
        assertTrue(r is Result.Ok)
        assertEquals("abc123", (r as Result.Ok).value.value)
    }
    @Test fun crewId_rejects_blank() {
        val r = CrewId.of("")
        assertTrue(r is Result.Err)
    }
    @Test fun crewId_accepts_nonblank() {
        val r = CrewId.of("crew-1")
        assertTrue(r is Result.Ok)
        assertEquals("crew-1", (r as Result.Ok).value.value)
    }
}
