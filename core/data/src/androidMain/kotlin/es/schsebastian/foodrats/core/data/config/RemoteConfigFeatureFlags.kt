package es.schsebastian.foodrats.core.data.config

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import es.schsebastian.foodrats.core.domain.config.FeatureFlagPort

/**
 * [FeatureFlagPort] backed by Firebase Remote Config.
 *
 * Remote Config has no GitLive KMP binding, so this concrete adapter lives in androidMain
 * and is bound in Koin from the Android app bootstrap (mirroring [AndroidCrashReporter]
 * [es.schsebastian.foodrats.core.data.telemetry.AndroidCrashReporter]).
 *
 * Reads ([isMealAiEnabled]) are synchronous — they return the activated/default cached
 * value, never blocking on the network. A background `fetchAndActivate()` is kicked off at
 * construction so a freshly toggled flag takes effect on the next launch (or sooner if the
 * fetch lands during the session). Local defaults are set to the safe value so behavior is
 * unchanged when no remote value has been fetched.
 */
class RemoteConfigFeatureFlags(
    private val remoteConfig: FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance(),
) : FeatureFlagPort {

    init {
        // Safe defaults: meal-AI on. Used until a remote value is fetched + activated.
        remoteConfig.setDefaultsAsync(mapOf(KEY_MEAL_AI_ENABLED to true))
        // Non-blocking refresh; the result is cached for the next synchronous read/launch.
        remoteConfig.fetchAndActivate()
    }

    override fun isMealAiEnabled(): Boolean = remoteConfig.getBoolean(KEY_MEAL_AI_ENABLED)

    private companion object {
        const val KEY_MEAL_AI_ENABLED = "meal_ai_enabled"
    }
}
