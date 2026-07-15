package es.schsebastian.foodrats.core.data.preferences

import es.schsebastian.foodrats.core.domain.result.Result

/**
 * Shared shape for a preferences write: run [block], mapping success to [Result.success] and any
 * thrown failure to [error]. Callers still own their own `withContext(dispatchers.io)` boundary.
 */
internal inline fun <E> persistResult(error: () -> E, block: () -> Unit): Result<Unit, E> =
    try {
        block()
        Result.success(Unit)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Throwable) {
        Result.failure(error())
    }
