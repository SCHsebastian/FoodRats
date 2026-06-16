package es.schsebastian.foodrats.feature.auth.domain.usecase.profile

import es.schsebastian.foodrats.core.domain.account.DataExportPort
import es.schsebastian.foodrats.core.domain.account.ExportReady
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.auth.domain.error.ProfileError
import es.schsebastian.foodrats.feature.auth.domain.error.toProfileError

/**
 * Requests a GDPR data-portability export of the current account. Pure orchestration: forwards to
 * the [DataExportPort] (the function derives the caller server-side) and maps the typed export
 * error onto the Profile surface. No I/O here — the dispatcher boundary lives in the adapter.
 */
class ExportMyDataUseCase(
    private val export: DataExportPort,
) {
    suspend operator fun invoke(): Result<ExportReady, ProfileError> =
        when (val r = export.exportMyData()) {
            is Result.Ok -> Result.success(r.value)
            is Result.Err -> Result.failure(r.error.toProfileError())
        }
}
