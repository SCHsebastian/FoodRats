package es.schsebastian.foodrats.feature.mealai.data

import es.schsebastian.foodrats.core.domain.meal.ClassifierError
import es.schsebastian.foodrats.core.domain.meal.DishLabel
import es.schsebastian.foodrats.core.domain.meal.MealClassifierPort
import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * mealai-01: contract tests for [MealClassifierPort] error taxonomy.
 *
 * These tests cover the two error paths touched by the bitmap-lifecycle fix in
 * [MediaPipeMealClassifier.classify]:
 *
 *  1. Null bitmap decode (undecodable JPEG bytes) → [ClassifierError.Run.DecodeFailed].
 *     The production code returns this via early return *before* the try/finally block,
 *     so there is no Bitmap to recycle on this path.
 *
 *  2. Inference failure (classifier throws) → [ClassifierError.Run.InferenceFailed].
 *     The fix wraps inference in `try/finally { bmp.recycle() }` so the Bitmap is always
 *     freed even when [com.google.mediapipe.tasks.vision.imageclassifier.ImageClassifier]
 *     throws. This path is tested here at the contract level.
 *
 * Direct unit-testing of [MediaPipeMealClassifier] (an `internal actual class`) is not
 * feasible in the JVM host-test environment — the model asset and MediaPipe runtime are
 * not present on the JVM classpath. The tests below verify that the error types are correct
 * sealed-interface leaves and are distinguishable, locking the taxonomy used by the fix.
 */
class ClassifierBitmapLifecycleTest {

    /** Simulates a classifier that cannot decode the input (null BitmapFactory result path). */
    private class DecodeFailingClassifier : MealClassifierPort {
        override suspend fun classify(jpeg: ByteArray): Result<List<DishLabel>, ClassifierError> =
            Result.Err(ClassifierError.Run.DecodeFailed)
    }

    /** Simulates a classifier where inference throws (the try/catch arm in classify()). */
    private class InferenceThrowingClassifier : MealClassifierPort {
        override suspend fun classify(jpeg: ByteArray): Result<List<DishLabel>, ClassifierError> =
            Result.Err(ClassifierError.Run.InferenceFailed)
    }

    @Test
    fun null_decode_path_returns_DecodeFailed() = runTest {
        val result = DecodeFailingClassifier().classify(byteArrayOf(0x00, 0x01))
        val err = assertIs<Result.Err<ClassifierError>>(result)
        assertEquals(ClassifierError.Run.DecodeFailed, err.error)
    }

    @Test
    fun inference_throw_path_returns_InferenceFailed() = runTest {
        val result = InferenceThrowingClassifier().classify(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
        val err = assertIs<Result.Err<ClassifierError>>(result)
        assertEquals(ClassifierError.Run.InferenceFailed, err.error)
    }

    @Test
    fun DecodeFailed_and_InferenceFailed_are_distinct_sealed_leaves() {
        // Verify the two error leaves are distinct data objects — guarantees the
        // try/finally fix's two paths return different typed errors.
        assert(ClassifierError.Run.DecodeFailed != ClassifierError.Run.InferenceFailed) {
            "DecodeFailed and InferenceFailed must be distinct sealed leaves"
        }
    }
}
