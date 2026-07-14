package es.schsebastian.foodrats.core.presentation.photopicker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Absolute cap on a single multi-select gallery pick. `PickMultipleVisualMedia`'s own limit is
 * fixed at launcher REGISTRATION time (the Android contract can't change it per-launch), so this
 * is the ceiling every `launchGallery(maxItems)` call is trimmed to — see [requestedGalleryMax]
 * below for how a smaller per-call request is still honored. Defined independently of
 * `MealPublishPolicy.MAX_PHOTOS_PER_MEAL` (`:core:domain`) — this picker is meal-agnostic (also
 * used by the avatar/crew-banner single pickers) — but currently the same value by design.
 */
private const val GALLERY_MULTI_SELECT_LIMIT = 10

/**
 * Android [PhotoPicker]: `TakePicture` into a cache-dir temp file exposed via the
 * `${applicationId}.photopicker` FileProvider, and the system Photo Picker
 * (`PickVisualMedia` / `PickMultipleVisualMedia`) for the gallery. No runtime permission needed
 * for any of these.
 */
@Composable
actual fun rememberPhotoPicker(onResult: (PhotoPickResult) -> Unit): PhotoPicker {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val currentOnResult by rememberUpdatedState(onResult)

    // The system camera can kill our process while capturing; the pending temp-file
    // path must survive so the restored result callback can still find the photo.
    var pendingCapturePath by rememberSaveable { mutableStateOf<String?>(null) }

    // The multi-select contract's OWN limit is fixed at registration (GALLERY_MULTI_SELECT_LIMIT,
    // the absolute app cap); a caller may request a smaller max, which the system picker UI has no
    // way to enforce — so the requested value is remembered here at launch and used to trim the
    // returned list once the (out-of-process, possibly process-death-surviving) result arrives.
    var requestedGalleryMax by rememberSaveable { mutableStateOf(1) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) {
            currentOnResult(PhotoPickResult.Cancelled)
        } else {
            scope.launch {
                val result = withContext(Dispatchers.Default) { pickGalleryUri(context, uri) }
                currentOnResult(result)
            }
        }
    }

    val galleryMultiLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(GALLERY_MULTI_SELECT_LIMIT),
    ) { uris ->
        if (uris.isEmpty()) {
            currentOnResult(PhotoPickResult.Cancelled)
        } else {
            val trimmed = uris.take(requestedGalleryMax)
            scope.launch {
                val result = withContext(Dispatchers.Default) {
                    // Same per-URI pipeline as the single-select path, run in ORDER (uris is
                    // already selection-ordered) — partial per-item failures are dropped, keeping
                    // only the successes; an all-failed batch surfaces as Failed.
                    val picks = coroutineScope {
                        trimmed.map { uri -> async { pickGalleryUri(context, uri) } }.awaitAll()
                    }
                        .filterIsInstance<PhotoPickResult.Picked>()
                        .map { picked -> PickedPhoto(picked.bytes, picked.source, picked.metadata) }
                    if (picks.isEmpty()) PhotoPickResult.Failed("Unreadable images") else PhotoPickResult.PickedMultiple(picks)
                }
                currentOnResult(result)
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { captured ->
        val path = pendingCapturePath
        pendingCapturePath = null
        if (path == null) {
            currentOnResult(PhotoPickResult.Failed("Capture result arrived with no pending file"))
        } else {
            scope.launch {
                val result = withContext(Dispatchers.Default) {
                    val file = File(path)
                    try {
                        when {
                            !captured -> PhotoPickResult.Cancelled
                            !file.exists() -> PhotoPickResult.Failed("Captured photo missing: $path")
                            else -> try {
                                PhotoPickResult.Picked(normalizeExifRotation(file.readBytes()), PhotoSource.Camera, null)
                            } catch (t: Throwable) {
                                PhotoPickResult.Failed(t.message)
                            }
                        }
                    } finally {
                        file.delete()
                    }
                }
                currentOnResult(result)
            }
        }
    }

    return remember {
        object : PhotoPicker {
            override fun launchCamera() {
                try {
                    val dir = File(context.cacheDir, "photopicker").apply { mkdirs() }
                    val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.photopicker",
                        file,
                    )
                    pendingCapturePath = file.absolutePath
                    cameraLauncher.launch(uri)
                } catch (t: Throwable) {
                    pendingCapturePath = null
                    currentOnResult(PhotoPickResult.Failed(t.message))
                }
            }

            override fun launchGallery(maxItems: Int) {
                if (maxItems <= 1) {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                } else {
                    requestedGalleryMax = maxItems
                    galleryMultiLauncher.launch(
                        // The request's own `maxItems` further narrows the CONTRACT's registered
                        // ceiling (GALLERY_MULTI_SELECT_LIMIT) — the system picker UI applies
                        // min(registered, requested), so a caller asking for fewer than 10 sees
                        // the correct cap in the picker itself, not just a post-hoc trim.
                        PickVisualMediaRequest(
                            mediaType = ActivityResultContracts.PickVisualMedia.ImageOnly,
                            maxItems = maxItems,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * Reads, EXIF-tags, and orientation-normalizes ONE gallery [uri] — the per-item pipeline shared by
 * the single- and multi-select gallery launchers. Always [PhotoPickResult.Picked] or
 * [PhotoPickResult.Failed], never [PhotoPickResult.Cancelled] (there's no per-item "cancel"; an
 * empty overall selection is the caller's own Cancelled).
 */
private fun pickGalleryUri(context: Context, uri: Uri): PhotoPickResult {
    return try {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        if (bytes == null) {
            PhotoPickResult.Failed("Unreadable image: $uri")
        } else {
            // Read EXIF from the RAW bytes — normalization below re-encodes the image (baking in
            // rotation), which drops EXIF metadata entirely.
            val metadata = readExifMetadata(bytes)
            PhotoPickResult.Picked(normalizeExifRotation(bytes), PhotoSource.Gallery, metadata)
        }
    } catch (t: Throwable) {
        PhotoPickResult.Failed(t.message)
    }
}

/**
 * Bakes the EXIF orientation into the pixels (re-encoding as JPEG q95) so downstream
 * consumers never need to read EXIF. Best-effort: any decode failure returns the
 * original bytes unchanged, matching the project's image-compressor style.
 */
private fun normalizeExifRotation(bytes: ByteArray): ByteArray {
    return try {
        val orientation = ByteArrayInputStream(bytes).use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }
        // Transpose/transverse are rotation+flip; treating them as plain 90/270 keeps
        // the image upright, which is all the composer needs.
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bytes
        val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
        val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, Matrix().apply { postRotate(degrees) }, true)
        val out = ByteArrayOutputStream()
        rotated.compress(Bitmap.CompressFormat.JPEG, 95, out)
        if (rotated !== src) rotated.recycle()
        src.recycle()
        out.toByteArray()
    } catch (_: Throwable) {
        bytes
    }
}

/**
 * Best-effort EXIF read from the RAW (pre-normalization) picked bytes: capture time (device-zone
 * epoch millis) + GPS lat/long, when present. The Android system photo picker frequently redacts
 * GPS by design — that's expected, [PhotoMetadata]'s fields are nullable for exactly this. Any
 * failure (corrupt/absent EXIF) yields an all-null [PhotoMetadata] rather than failing the pick.
 */
private fun readExifMetadata(rawBytes: ByteArray): PhotoMetadata {
    return try {
        val exif = ByteArrayInputStream(rawBytes).use { ExifInterface(it) }
        val latLong = exif.latLong
        val dateString = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
        val takenAtEpochMs = dateString?.let { raw ->
            val position = ParsePosition(0)
            // Fresh SimpleDateFormat per call (it's not thread-safe); "yyyy:MM:dd HH:mm:ss" is
            // the fixed EXIF datetime format (TAG_DATETIME[_ORIGINAL]), parsed in the JVM
            // default (device) timezone, matching the "EXIF datetime interpreted in device
            // zone" convention.
            SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
                .apply { isLenient = false } // reject degenerate EXIF dates like 0000:00:00 …
                .parse(raw, position)
                ?.takeIf { position.index > 0 }
                ?.time
        }
        PhotoMetadata(
            takenAtEpochMs = takenAtEpochMs,
            latitude = latLong?.getOrNull(0),
            longitude = latLong?.getOrNull(1),
        )
    } catch (_: Throwable) {
        PhotoMetadata(takenAtEpochMs = null, latitude = null, longitude = null)
    }
}
