package es.schsebastian.foodrats.feature.meal.data.firebase

import es.schsebastian.foodrats.core.domain.meal.MealReadError
import es.schsebastian.foodrats.core.domain.meal.RateError
import es.schsebastian.foodrats.core.domain.telemetry.CrashReporter
import es.schsebastian.foodrats.feature.meal.domain.error.MealError

class MealErrorMapper(private val crashReporter: CrashReporter) {
    fun mapPublish(t: Throwable): MealError {
        val message = t.message.orEmpty().lowercase()
        return when {
            "permission" in message || "permission_denied" in message  -> MealError.Read.Unauthorized
            "unavailable" in message || "network" in message           -> MealError.Publish.PublishUnavailable
            "already-exists" in message || "already_exists" in message -> MealError.Publish.AlreadyPostedToday
            "storage" in message || "upload" in message                -> MealError.Publish.PhotoUploadFailed
            else -> {
                crashReporter.recordNonFatal(t, tag = "meal-publish-unmapped")
                MealError.Publish.PublishUnavailable
            }
        }
    }

    fun mapRead(t: Throwable): MealReadError {
        crashReporter.recordNonFatal(t, tag = "meal-read")
        return MealReadError.Unavailable
    }

    fun mapRate(t: Throwable): RateError {
        val message = t.message.orEmpty().lowercase()
        return when {
            "already-exists" in message || "already_exists" in message -> RateError.AlreadyRated
            "permission" in message || "permission_denied" in message -> RateError.RateUnavailable
            "unavailable" in message || "network" in message           -> RateError.RateUnavailable
            else -> {
                crashReporter.recordNonFatal(t, tag = "meal-rate-unmapped")
                RateError.RateUnavailable
            }
        }
    }
}
