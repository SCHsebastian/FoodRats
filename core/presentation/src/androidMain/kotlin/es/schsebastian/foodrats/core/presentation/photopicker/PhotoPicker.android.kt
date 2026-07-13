package es.schsebastian.foodrats.core.presentation.photopicker

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Android [PhotoPicker]: `TakePicture` into a cache-dir temp file exposed via the
 * `${applicationId}.photopicker` FileProvider, and the system Photo Picker
 * (`PickVisualMedia`) for the gallery. No runtime permission needed for either.
 */
@Composable
actual fun rememberPhotoPicker(onResult: (PhotoPickResult) -> Unit): PhotoPicker {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val currentOnResult by rememberUpdatedState(onResult)

    // The system camera can kill our process while capturing; the pending temp-file
    // path must survive so the restored result callback can still find the photo.
    var pendingCapturePath by rememberSaveable { mutableStateOf<String?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) {
            currentOnResult(PhotoPickResult.Cancelled)
        } else {
            scope.launch {
                val result = withContext(Dispatchers.Default) {
                    try {
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        if (bytes == null) {
                            PhotoPickResult.Failed("Unreadable image: $uri")
                        } else {
                            // Read EXIF from the RAW bytes — normalization below re-encodes the
                            // image (baking in rotation), which drops EXIF metadata entirely.
                            val metadata = readExifMetadata(bytes)
                            PhotoPickResult.Picked(normalizeExifRotation(bytes), PhotoSource.Gallery, metadata)
                        }
                    } catch (t: Throwable) {
                        PhotoPickResult.Failed(t.message)
                    }
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

            override fun launchGallery() {
                galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            }
        }
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
