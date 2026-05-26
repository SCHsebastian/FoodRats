package es.schsebastian.foodrats.feature.mealai.data

import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.meal.ClassifierError
import es.schsebastian.foodrats.core.domain.meal.DishLabel
import es.schsebastian.foodrats.core.domain.meal.MealClassifierPort
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.telemetry.CrashReporter
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.create
import kotlin.coroutines.resume

/**
 * iOS food classifier. MediaPipe Tasks Vision on iOS ships as a CocoaPod /
 * XCFramework integrated in the Xcode project, so inference itself runs in Swift
 * ([MediaPipeClassifierBridge]) and is bridged into Kotlin through a lambda — the
 * same convention used for GoogleSignIn ([GoogleAuthClient]) and Crashlytics.
 *
 * The lambda is supplied by `mealAiIosModule(...)` from `iosApp/ContentView.swift`
 * via `MainViewController`. The Swift callback signature is
 * `(labels, errorCode) -> Unit` where exactly one side is non-null:
 *   - `labels`  → each entry is `"<dishSlug>|<confidence>"` (kept primitive so the
 *                 Swift boundary doesn't depend on a `:core:domain` type being
 *                 exported into the ObjC header — same primitive-only convention as
 *                 the GoogleSignIn / Crashlytics bridges). Ordered top-confidence
 *                 first, as MediaPipe returns them.
 *   - `errorCode == "load"`     → model missing / failed to load
 *   - `errorCode == "decode"`   → the JPEG could not be decoded into an image
 *   - `errorCode == "inference"`→ the classifier threw while running
 */
@OptIn(ExperimentalForeignApi::class)
internal actual class MediaPipeMealClassifier(
    private val dispatchers: DispatcherProvider,
    private val crashReporter: CrashReporter,
    private val classifyNative: (
        jpeg: NSData,
        completion: (labels: List<String>?, errorCode: String?) -> Unit,
    ) -> Unit,
) : MealClassifierPort {

    override suspend fun classify(jpeg: ByteArray): Result<List<DishLabel>, ClassifierError> =
        withContext(dispatchers.io) {
            suspendCancellableCoroutine { cont ->
                classifyNative(jpeg.toNSData()) { encoded, errorCode ->
                    if (encoded != null) {
                        cont.resume(Result.Ok(encoded.mapNotNull { it.toDishLabel() }))
                    } else {
                        cont.resume(Result.Err(errorCode.toClassifierError()))
                    }
                }
            }
        }
}

/** Parses a `"<dishSlug>|<confidence>"` entry from the Swift bridge. */
private fun String.toDishLabel(): DishLabel? {
    val score = substringAfterLast('|').toFloatOrNull() ?: return null
    return DishLabel(dishSlug = substringBeforeLast('|'), confidence = score)
}

private fun String?.toClassifierError(): ClassifierError = when (this) {
    "load"      -> ClassifierError.Load.ModelMissing
    "decode"    -> ClassifierError.Run.DecodeFailed
    "inference" -> ClassifierError.Run.InferenceFailed
    else        -> ClassifierError.Run.InferenceFailed
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData =
    if (isEmpty()) {
        NSData()
    } else {
        usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
        }
    }
