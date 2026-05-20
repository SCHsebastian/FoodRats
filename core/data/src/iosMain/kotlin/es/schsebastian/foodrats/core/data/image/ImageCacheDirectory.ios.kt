package es.schsebastian.foodrats.core.data.image

import coil3.PlatformContext
import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
internal actual fun imageCacheDirectory(context: PlatformContext): Path {
    val cacheUrl = NSFileManager.defaultManager.URLForDirectory(
        directory = NSCachesDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    ) ?: error("NSCachesDirectory unavailable")
    val base = cacheUrl.path ?: error("NSCachesDirectory path unresolvable")
    return "$base/image_cache".toPath()
}
