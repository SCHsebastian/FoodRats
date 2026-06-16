package es.schsebastian.foodrats.feature.meal.data.firebase

import es.schsebastian.foodrats.core.domain.meal.MealReadError
import es.schsebastian.foodrats.core.domain.meal.RateError
import es.schsebastian.foodrats.core.domain.telemetry.CrashReporter
import es.schsebastian.foodrats.feature.meal.domain.error.MealError

/**
 * Maps a raw backend throwable to a typed domain error by first classifying it into a
 * [FirebaseFault] (the single message-inspection seam) and then matching on the fault
 * **type**. No mapper here inspects `t.message` directly — see [FirebaseFault].
 */
class MealErrorMapper(private val crashReporter: CrashReporter) {

    fun mapPublish(t: Throwable): MealError = when (val fault = t.toFirebaseFault()) {
        // A publish rejected by a security rule is a publish failure, not a *read*
        // failure. (Previously mis-mapped to MealError.Read.Unauthorized — fixed #8.)
        FirebaseFault.PermissionDenied -> MealError.Publish.PublishUnavailable
        FirebaseFault.Unauthenticated  -> MealError.Publish.PublishUnavailable
        FirebaseFault.Unavailable      -> MealError.Publish.PublishUnavailable
        FirebaseFault.AlreadyExists    -> MealError.Publish.AlreadyPostedToday
        FirebaseFault.StorageFailure   -> MealError.Publish.PhotoUploadFailed
        FirebaseFault.NotFound         -> MealError.Publish.PublishUnavailable
        is FirebaseFault.Unknown -> {
            crashReporter.recordNonFatal(fault.cause, tag = "meal-publish-unmapped")
            MealError.Publish.PublishUnavailable
        }
    }

    fun mapRead(t: Throwable): MealReadError {
        // Behavior preserved verbatim from the original mapper: every read failure is
        // surfaced as Unavailable and recorded as a non-fatal. Left untouched to keep
        // this change scoped to the two publish/rate mis-mappings (no read mis-mapping
        // was reported).
        crashReporter.recordNonFatal(t, tag = "meal-read")
        return MealReadError.Unavailable
    }

    fun mapRate(t: Throwable): RateError = when (val fault = t.toFirebaseFault()) {
        FirebaseFault.AlreadyExists    -> RateError.AlreadyRated
        FirebaseFault.Unauthenticated  -> RateError.Unauthorized
        // A PERMISSION_DENIED is an authorization rejection, NOT a closed rating window.
        // (Previously mis-mapped to RateError.RatingWindowClosed — fixed #8.) The
        // rating-window-closed condition has no distinct Firebase signal today, so it's
        // never inferred from a raw fault.
        FirebaseFault.PermissionDenied -> RateError.RateUnavailable
        FirebaseFault.Unavailable      -> RateError.Offline
        FirebaseFault.NotFound         -> RateError.RateUnavailable
        FirebaseFault.StorageFailure   -> RateError.RateUnavailable
        is FirebaseFault.Unknown -> {
            crashReporter.recordNonFatal(fault.cause, tag = "meal-rate-unmapped")
            RateError.RateUnavailable
        }
    }
}
