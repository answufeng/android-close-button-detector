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

    /**
     * How raw class/objectness logits are mapped to probabilities before thresholding.
     */
    val scoreNormalization: ScoreNormalizationMode = ScoreNormalizationMode.AUTO_SIGMOID,

    /**
     * When non-null, detections are kept only for these YOLO class indices (after NMS).
     * When null, only class id `0` (close_button in the bundled model) is returned.
     */
    val outputClassIds: Set<Int>? = null,

    /**
     * Prefer Android NNAPI delegate when supported (device/TFLite build dependent).
     */
    val useNnApi: Boolean = false,
)

/**
 * Normalization for YOLO-style logits in [0,1] or unbounded ranges.
 */
enum class ScoreNormalizationMode {
    /** Values already in `[0,1]` pass through; otherwise apply sigmoid. */
    AUTO_SIGMOID,

    /** Always apply sigmoid. */
    ALWAYS_SIGMOID,

    /** Clamp to `[0,1]` without sigmoid. */
    RAW_CLIP,
}

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

