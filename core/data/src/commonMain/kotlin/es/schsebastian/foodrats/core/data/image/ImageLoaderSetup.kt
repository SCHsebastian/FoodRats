package es.schsebastian.foodrats.core.data.image

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory

/**
 * Registers a singleton Coil 3 [ImageLoader] used by every `AsyncImage` in the app
 * (meal photos in the feed + uploaded avatars in comment rows).
 *
 * Memory cache: 20% of device max (Coil-recommended default; documented explicitly).
 * Disk cache: 50 MB, stored under the platform's OS-managed cache directory so the OS
 * may purge it under storage pressure. Avatars are tiny; meal photos dominate the budget.
 *
 * Call once per process from the platform entry point — Android: FoodRatsApplication.onCreate;
 * iOS: MainViewController(). `setSafe` makes repeat calls no-ops.
 */
fun installImageLoader() {
    SingletonImageLoader.setSafe { context: PlatformContext ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            .memoryCache {
                MemoryCache.Builder().maxSizePercent(context, 0.20).build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(imageCacheDirectory(context))
                    .maxSizeBytes(50L * 1024 * 1024)
                    .build()
            }
            .build()
    }
}
