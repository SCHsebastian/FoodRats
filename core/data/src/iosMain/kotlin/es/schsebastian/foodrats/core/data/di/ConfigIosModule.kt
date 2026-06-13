package es.schsebastian.foodrats.core.data.di

import es.schsebastian.foodrats.core.domain.config.DefaultOnFeatureFlags
import es.schsebastian.foodrats.core.domain.config.FeatureFlagPort
import org.koin.dsl.module

/**
 * iOS-side Koin module binding [FeatureFlagPort] to the default-on implementation.
 *
 * iOS ships without a Remote Config adapter for now (TODO(RemoteConfig): wire the native
 * FirebaseRemoteConfig SDK via the SPM bridge if iOS ever needs the kill-switch). Default-on
 * means current behavior is unchanged — the meal-AI classifier stays enabled.
 */
val configIosModule = module {
    single<FeatureFlagPort> { DefaultOnFeatureFlags }
}
