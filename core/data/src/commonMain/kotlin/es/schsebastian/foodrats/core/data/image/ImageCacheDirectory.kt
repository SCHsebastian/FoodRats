package es.schsebastian.foodrats.core.data.image

import coil3.PlatformContext
import okio.Path

internal expect fun imageCacheDirectory(context: PlatformContext): Path
