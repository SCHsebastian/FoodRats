package es.schsebastian.foodrats.feature.feed.data.image

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory

/**
 * Registers a singleton Coil 3 [ImageLoader] that fetches meal photos via the Ktor 3
 * network fetcher. Call this from each platform's app entry point exactly once before
 * any [coil3.compose.AsyncImage] composes — Android: FoodRatsApplication.onCreate(),
 * iOS: MainViewController() (via configure { }).
 *
 * Using `setSafe` instead of `setUnsafe` makes repeat calls no-ops, so it's safe even
 * if both an Application class and an Activity attempt to initialise the loader.
 */
fun installFeedImageLoader() {
    SingletonImageLoader.setSafe { context: PlatformContext ->
        ImageLoader.Builder(context)
            .components {
                add(KtorNetworkFetcherFactory())
            }
            .build()
    }
}
