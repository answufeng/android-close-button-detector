package com.answufeng.closebuttondetector

import android.graphics.RectF

/**
 * A single detection result.
 */
data class Detection(
    /**
     * Class label of this detection.
     */
    val label: String,

    /**
     * Class id (index in the label list).
     */
    val classId: Int,

    /**
     * Confidence score after combining objectness and class probability.
     */
    val score: Float,

    /**
     * Bounding box in the original bitmap coordinate space.
     */
    val box: RectF,
)

