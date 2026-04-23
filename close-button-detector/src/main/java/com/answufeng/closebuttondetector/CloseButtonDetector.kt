package com.answufeng.closebuttondetector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF

/**
 * Close button detector facade.
 *
 * This is the recommended entry for integration:
 * - Construct once (it loads the TFLite model and initializes interpreter).
 * - Call detection APIs by passing a [Bitmap].
 * - Call [close] when no longer needed.
 *
 * The library uses a bundled default model (`best_float32.tflite`) from `assets/`.
 *
 * Threading:
 * - This detector is **not** thread-safe. Do not call [detect] concurrently from multiple threads.
 * - If you need parallelism, create one detector instance per worker thread.
 */
class CloseButtonDetector @JvmOverloads constructor(
    context: Context,
    config: CloseButtonDetectorConfig = CloseButtonDetectorConfig(),
) : AutoCloseable {

    constructor(
        context: Context,
        scoreThreshold: Float = DEFAULT_SCORE_THRESHOLD,
        iouThreshold: Float = DEFAULT_IOU_THRESHOLD,
        preprocessMode: PreprocessMode = DEFAULT_PREPROCESS_MODE,
        numThreads: Int = DEFAULT_NUM_THREADS,
        enableLogging: Boolean = false,
    ) : this(
        context = context,
        config = CloseButtonDetectorConfig(
            scoreThreshold = scoreThreshold,
            iouThreshold = iouThreshold,
            preprocessMode = preprocessMode,
            numThreads = numThreads,
            enableLogging = enableLogging,
        )
    )

    private val engine = YoloCloseButtonDetector(
        context = context,
        scoreThreshold = config.scoreThreshold,
        iouThreshold = config.iouThreshold,
        preprocessMode = config.preprocessMode,
        enableLogging = config.enableLogging,
        numThreads = config.numThreads,
    )

    /**
     * Detect all candidate close buttons.
     *
     * @return a list of detections, sorted by descending confidence score.
     */
    fun detect(bitmap: Bitmap): List<Detection> = engine.detect(bitmap)

    /**
     * A convenience method that returns only bounding rectangles.
     */
    fun detectRects(bitmap: Bitmap): List<RectF> = engine.detectRects(bitmap)

    /**
     * Returns the highest-confidence detection, or `null` if none found.
     */
    fun detectTopOne(bitmap: Bitmap): Detection? = engine.detectTopOne(bitmap)

    /**
     * Returns the bounding rectangle of the highest-confidence detection, or `null` if none found.
     */
    fun detectTopRect(bitmap: Bitmap): RectF? = engine.detectTopRect(bitmap)

    /**
     * Pick the "best" close button candidate using a given strategy.
     *
     * - [BestCloseButtonStrategy.HIGHEST_SCORE]: simply pick the highest score.
     * - [BestCloseButtonStrategy.TOP_RIGHT]: prefer the top-right area (common for close buttons),
     *   then fallback to score.
     */
    fun detectBest(
        bitmap: Bitmap,
        strategy: BestCloseButtonStrategy = BestCloseButtonStrategy.HIGHEST_SCORE,
    ): Detection? {
        val detections = detect(bitmap)
        if (detections.isEmpty()) return null

        return when (strategy) {
            BestCloseButtonStrategy.HIGHEST_SCORE -> detections.maxByOrNull { it.score }
            BestCloseButtonStrategy.TOP_RIGHT -> detections.maxWithOrNull(
                compareBy<Detection> { it.box.right }
                    .thenBy { -it.box.top }
                    .thenBy { it.score }
            )
        }
    }

    /**
     * Same as [detectBest] but returns a [RectF].
     */
    fun detectBestRect(
        bitmap: Bitmap,
        strategy: BestCloseButtonStrategy = BestCloseButtonStrategy.HIGHEST_SCORE,
    ): RectF? = detectBest(bitmap, strategy)?.box?.let(::RectF)

    /**
     * Fast check for whether any close button was detected.
     */
    fun hasCloseButton(bitmap: Bitmap): Boolean = engine.hasCloseButton(bitmap)

    override fun close() {
        engine.close()
    }

    companion object {
        /**
         * Default score threshold (confidence) used by [CloseButtonDetectorConfig] and the convenience constructor.
         */
        const val DEFAULT_SCORE_THRESHOLD: Float = 0.25f

        /**
         * Default IOU threshold used by [CloseButtonDetectorConfig] and the convenience constructor.
         */
        const val DEFAULT_IOU_THRESHOLD: Float = 0.45f

        /**
         * Default number of interpreter threads.
         */
        const val DEFAULT_NUM_THREADS: Int = 4

        /**
         * Default preprocessing mode.
         */
        val DEFAULT_PREPROCESS_MODE: PreprocessMode = PreprocessMode.LETTERBOX
    }
}

