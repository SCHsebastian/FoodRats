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
import kotlinx.coroutines.withContext

internal actual class MediaPipeMealClassifier(
    private val dispatchers: DispatcherProvider,
    private val crashReporter: CrashReporter,
) : MealClassifierPort {

    private var cached: ImageClassifier? = null

    /** One-shot lifecycle hook called from `FoodRatsApplication.onCreate()` (wired in T30). */
    fun init(context: Context) {
        if (cached != null) return
        try {
            val base = BaseOptions.builder().setModelAssetPath("files/food101.tflite").build()
            val options = ImageClassifierOptions.builder()
                .setBaseOptions(base)
                .setMaxResults(5)
                .build()
            cached = ImageClassifier.createFromOptions(context, options)
        } catch (t: Throwable) {
            crashReporter.recordNonFatal(t, "mealai-load")
        }
    }

    override suspend fun classify(jpeg: ByteArray): Result<List<DishLabel>, ClassifierError> =
        withContext(dispatchers.io) {
            val classifier = cached
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
