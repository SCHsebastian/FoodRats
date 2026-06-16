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
 * Memory cache: 25% of device max — the feed scrolls through many small thumbnails (roadmap §5.1),
 * so a slightly larger in-memory budget keeps recently-seen thumbs hot without re-decoding.
 * Disk cache: 128 MB, stored under the platform's OS-managed cache directory so the OS may purge it
 * under storage pressure. The pipeline now serves a small thumbnail per feed card plus the full
 * plate on detail, so the disk budget holds far more meals than the prior 50 MB. Avatars are tiny;
 * thumbnails + plates dominate.
 *
 * Call once per process from the platform entry point — Android: FoodRatsApplication.onCreate;
 * iOS: MainViewController(). `setSafe` makes repeat calls no-ops.
 */
fun installImageLoader() {
    SingletonImageLoader.setSafe { context: PlatformContext ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            .memoryCache {
                MemoryCache.Builder().maxSizePercent(context, 0.25).build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(imageCacheDirectory(context))
                    .maxSizeBytes(128L * 1024 * 1024)
                    .build()
            }
            .build()
    }
}
