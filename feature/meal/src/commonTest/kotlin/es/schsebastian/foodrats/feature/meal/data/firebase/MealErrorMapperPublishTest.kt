package es.schsebastian.foodrats.feature.meal.data.firebase

import es.schsebastian.foodrats.core.domain.telemetry.NoopCrashReporter
import es.schsebastian.foodrats.feature.meal.domain.error.MealError
import kotlin.test.Test
import kotlin.test.assertEquals

class MealErrorMapperPublishTest {
    private val mapper = MealErrorMapper(NoopCrashReporter)

    // --- #8 regression: a publish rejected by a security rule is a *publish* failure,
    // not a *read* failure. It must NOT map to MealError.Read.Unauthorized. ---
    @Test fun permission_denied_underscore_maps_to_publish_unavailable_not_read_unauthorized() {
        val t = RuntimeException("PERMISSION_DENIED: Missing or insufficient permissions.")
        assertEquals(MealError.Publish.PublishUnavailable, mapper.mapPublish(t))
    }

    @Test fun permission_denied_hyphen_maps_to_publish_unavailable_not_read_unauthorized() {
        val t = RuntimeException("permission-denied: rules rejected the write.")
        assertEquals(MealError.Publish.PublishUnavailable, mapper.mapPublish(t))
    }

    @Test fun already_exists_maps_to_already_posted_today() {
        val t = RuntimeException("ALREADY_EXISTS: a meal already exists for this slot")
        assertEquals(MealError.Publish.AlreadyPostedToday, mapper.mapPublish(t))
    }

    @Test fun storage_failure_maps_to_photo_upload_failed() {
        val t = RuntimeException("Storage: upload failed with code 13")
        assertEquals(MealError.Publish.PhotoUploadFailed, mapper.mapPublish(t))
    }

    @Test fun unavailable_maps_to_publish_unavailable() {
        val t = RuntimeException("Status{code=UNAVAILABLE, description=network down}")
        assertEquals(MealError.Publish.PublishUnavailable, mapper.mapPublish(t))
    }

    @Test fun unknown_maps_to_publish_unavailable() {
        val t = RuntimeException("Something completely unexpected happened.")
        assertEquals(MealError.Publish.PublishUnavailable, mapper.mapPublish(t))
    }
}
