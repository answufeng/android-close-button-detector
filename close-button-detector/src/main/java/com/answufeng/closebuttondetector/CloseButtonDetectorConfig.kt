package com.answufeng.closebuttondetector

/**
 * Configuration for [CloseButtonDetector].
 */
data class CloseButtonDetectorConfig(
    /**
     * Drop detections with score below this threshold.
     */
    val scoreThreshold: Float = CloseButtonDetector.DEFAULT_SCORE_THRESHOLD,

    /**
     * IOU threshold for NMS suppression, per class.
     */
    val iouThreshold: Float = CloseButtonDetector.DEFAULT_IOU_THRESHOLD,

    /**
     * How to resize/crop the bitmap before inference.
     */
    val preprocessMode: PreprocessMode = CloseButtonDetector.DEFAULT_PREPROCESS_MODE,

    /**
     * Number of interpreter threads.
     */
    val numThreads: Int = CloseButtonDetector.DEFAULT_NUM_THREADS,

    /**
     * Enable verbose logs for debugging integration issues.
     */
    val enableLogging: Boolean = false,
)

/**
 * How to resize/crop the bitmap before inference.
 */
enum class PreprocessMode {
    /**
     * Resize image to model input size directly (aspect ratio is NOT preserved).
     */
    STRETCH,

    /**
     * Resize with aspect ratio preserved and pad with a constant background.
     */
    LETTERBOX,
}

/**
 * Strategy for selecting a single "best" detection from multiple candidates.
 */
enum class BestCloseButtonStrategy {
    /**
     * Select the detection with the highest confidence score.
     */
    HIGHEST_SCORE,

    /**
     * Prefer detections closer to top-right (common position for close buttons).
     */
    TOP_RIGHT,
}

