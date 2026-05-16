package es.schsebastian.foodrats.core.domain.result

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResultTest {
    @Test fun success_holds_value() {
        val r: Result<Int, String> = Result.success(42)
        assertTrue(r is Result.Ok)
        assertEquals(42, (r as Result.Ok).value)
    }

    @Test fun failure_holds_error() {
        val r: Result<Int, String> = Result.failure("nope")
        assertTrue(r is Result.Err)
        assertEquals("nope", (r as Result.Err).error)
    }

    @Test fun fold_dispatches_to_ok_branch() {
        val r: Result<Int, String> = Result.success(2)
        val out = r.fold(onOk = { it * 10 }, onErr = { -1 })
        assertEquals(20, out)
    }

    @Test fun fold_dispatches_to_err_branch() {
        val r: Result<Int, String> = Result.failure("boom")
        val out = r.fold(onOk = { it * 10 }, onErr = { it.length })
        assertEquals(4, out)
    }

    @Test fun map_transforms_ok_value() {
        val r: Result<Int, String> = Result.success(3)
        assertEquals(Result.success(6), r.map { it * 2 })
    }

    @Test fun map_passes_err_through() {
        val r: Result<Int, String> = Result.failure("x")
        assertEquals(Result.failure("x"), r.map { it * 2 })
    }

    @Test fun mapError_transforms_err() {
        val r: Result<Int, String> = Result.failure("x")
        assertEquals(Result.failure(1), r.mapError { it.length })
    }

    @Test fun getOrElse_returns_value_on_ok() {
        assertEquals(7, Result.success<Int>(7).getOrElse { 0 })
    }

    @Test fun getOrElse_returns_default_on_err() {
        val r: Result<Int, String> = Result.failure("nope")
        assertEquals(0, r.getOrElse { 0 })
    }

    @Test fun getOrNull_returns_value_or_null() {
        assertEquals(5, Result.success<Int>(5).getOrNull())
        assertNull(Result.failure<String>("e").getOrNull())
    }
}
