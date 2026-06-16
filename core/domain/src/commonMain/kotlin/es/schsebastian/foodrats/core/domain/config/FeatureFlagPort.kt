package es.schsebastian.foodrats.core.domain.config

/**
 * Remote-controllable feature flags / kill-switches.
 *
 * Reads are synchronous and non-suspending: callers get the value cached from the
 * last refresh (or the safe default if no refresh has happened). A flag never
 * blocks on the network, so consulting it can never gate or slow a user action.
 *
 * Backed per-platform in the data/adapter layer (Firebase Remote Config on Android,
 * a default-on implementation on iOS) — `:core:domain` declares only this port and
 * never imports a vendor SDK. Defaults are chosen so the current behavior holds when
 * remote config is absent or unreachable.
 */
interface FeatureFlagPort {
    /**
     * Whether on-device meal-AI classification is enabled. Defaults to `true` so the
     * existing advisory-classification behavior is unchanged; flip the remote flag to
     * `false` to kill a misbehaving classifier in production. When `false`, the
     * classify path skips inference and yields no detections (never an error — the
     * feature is advisory).
     */
    fun isMealAiEnabled(): Boolean
}

/** Default-on [FeatureFlagPort] — every flag returns its safe default. */
object DefaultOnFeatureFlags : FeatureFlagPort {
    override fun isMealAiEnabled(): Boolean = true
}
