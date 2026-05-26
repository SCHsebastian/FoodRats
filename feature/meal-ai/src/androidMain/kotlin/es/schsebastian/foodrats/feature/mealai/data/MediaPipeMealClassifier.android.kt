package es.schsebastian.foodrats.feature.mealai.data

import android.content.Context
import android.graphics.BitmapFactory
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.imageclassifier.ImageClassifier
import com.google.mediapipe.tasks.vision.imageclassifier.ImageClassifier.ImageClassifierOptions
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.meal.ClassifierError
import es.schsebastian.foodrats.core.domain.meal.DishLabel
import es.schsebastian.foodrats.core.domain.meal.MealClassifierPort
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.telemetry.CrashReporter
import foodrats.feature.meal_ai.generated.resources.Res
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

internal actual class MediaPipeMealClassifier(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val crashReporter: CrashReporter,
) : MealClassifierPort {

    private val loadLock = Mutex()
    private var cached: ImageClassifier? = null

    /**
     * Lazily builds the classifier on first use. The model ships through Compose
     * Resources (`composeResources/files/food101.tflite`), not the raw `assets/` root,
     * so it must be read via [Res.readBytes] and handed to MediaPipe as a direct
     * `ByteBuffer` — `setModelAssetPath("files/…")` resolves against the AssetManager
     * root, where the file does not exist, and a missing model makes the native graph
     * SIGSEGV instead of throwing. Reading is suspending, so this runs on the IO
     * dispatcher inside [classify], never on the main thread during composition.
     */
    private suspend fun classifier(): ImageClassifier? = loadLock.withLock {
        cached ?: try {
            val model = Res.readBytes("files/food101.tflite")
            val buffer = ByteBuffer.allocateDirect(model.size).apply {
                put(model)
                rewind()
            }
            val base = BaseOptions.builder().setModelAssetBuffer(buffer).build()
            val options = ImageClassifierOptions.builder()
                .setBaseOptions(base)
                .setMaxResults(5)
                .build()
            ImageClassifier.createFromOptions(context, options).also { cached = it }
        } catch (t: Throwable) {
            crashReporter.recordNonFatal(t, "mealai-load")
            null
        }
    }

    override suspend fun classify(jpeg: ByteArray): Result<List<DishLabel>, ClassifierError> =
        withContext(dispatchers.io) {
            val classifier = classifier()
                ?: return@withContext Result.Err(ClassifierError.Load.ModelMissing)
            val bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
                ?: return@withContext Result.Err(ClassifierError.Run.DecodeFailed)
            try {
                val mpImage = BitmapImageBuilder(bmp).build()
                val out = classifier.classify(mpImage)
                val labels = out.classificationResult().classifications().firstOrNull()
                    ?.categories().orEmpty()
                    .map { DishLabel(it.categoryName(), it.score()) }
                Result.Ok(labels)
            } catch (t: Throwable) {
                Result.Err(ClassifierError.Run.InferenceFailed)
            }
        }
}
