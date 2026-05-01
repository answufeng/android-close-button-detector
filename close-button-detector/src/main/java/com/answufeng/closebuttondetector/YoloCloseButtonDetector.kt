package com.answufeng.closebuttondetector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileNotFoundException
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A lightweight YOLO-style close button detector implementation backed by TensorFlow Lite.
 *
 * Notes:
 * - The model must accept NHWC float32 input in range [0,1].
 * - Output tensor layout differs across model exports; this implementation handles common variants:
 *   `[1, numPred, numFeat]` and `[1, numFeat, numPred]`, with or without objectness field.
 *
 * Prefer using [CloseButtonDetector] unless you need direct control over this class.
 */
internal class YoloCloseButtonDetector(
    context: Context,
    modelAssetName: String = DEFAULT_MODEL_ASSET_NAME,
    private val scoreThreshold: Float = 0.25f,
    private val iouThreshold: Float = 0.45f,
    private val preprocessMode: PreprocessMode = PreprocessMode.LETTERBOX,
    private val enableLogging: Boolean = false,
    numThreads: Int = 4,
    private val scoreNormalization: ScoreNormalizationMode = ScoreNormalizationMode.AUTO_SIGMOID,
    private val outputClassIds: Set<Int>? = null,
    useNnApi: Boolean = false,
) : AutoCloseable {

    private val tag = "CloseButtonDetector"
    private val labels = DEFAULT_LABELS
    private val channels = 3
    private val closeButtonClassId = DEFAULT_CLOSE_BUTTON_CLASS_ID

    private val interpreter: Interpreter
    private val inputWidth: Int
    private val inputHeight: Int

    private var cachedOutputShape: IntArray? = null
    private var cachedOutputBuffer: Array<Array<FloatArray>>? = null

    companion object {
        /**
         * Default model file name that is bundled with this library.
         */
        const val DEFAULT_MODEL_ASSET_NAME: String = "best_float32.tflite"

        /**
         * Default label list that matches the bundled model.
         */
        val DEFAULT_LABELS: List<String> = listOf("close_button", "right_arrow", "left_arrow")

        /**
         * Class id of "close_button" in [DEFAULT_LABELS].
         *
         * This library intentionally does not expose model/label overriding to avoid integration misuse.
         */
        const val DEFAULT_CLOSE_BUTTON_CLASS_ID: Int = 0

        private fun findAssetPathByName(context: Context, fileName: String, maxDepth: Int = 3): String? {
            fun walk(prefix: String, depth: Int): String? {
                val entries = runCatching { context.assets.list(prefix)?.toList().orEmpty() }.getOrDefault(emptyList())
                if (entries.isEmpty()) return null
                if (fileName in entries) return if (prefix.isEmpty()) fileName else "$prefix/$fileName"
                if (depth >= maxDepth) return null
                for (entry in entries) {
                    val path = if (prefix.isEmpty()) entry else "$prefix/$entry"
                    val children = runCatching { context.assets.list(path) }.getOrNull()
                    if (!children.isNullOrEmpty()) {
                        val found = walk(path, depth + 1)
                        if (found != null) return found
                    }
                }
                return null
            }
            return walk(prefix = "", depth = 0)
        }
    }

    init {
        require(this.labels.isNotEmpty()) { "labels must not be empty" }

        val model = try {
            context.assets.openFd(modelAssetName).use { afd ->
                FileInputStream(afd.fileDescriptor).channel.use { channel ->
                    channel.map(
                        FileChannel.MapMode.READ_ONLY,
                        afd.startOffset,
                        afd.declaredLength,
                    )
                }
            }
        } catch (e: FileNotFoundException) {
            // Try locating the model file under sub-directories (e.g. assets/images/).
            val resolved = findAssetPathByName(context, modelAssetName)
            if (resolved != null) {
                logWarn("Model asset not found at root. Resolved '$modelAssetName' to '$resolved'.")
                context.assets.openFd(resolved).use { afd ->
                    FileInputStream(afd.fileDescriptor).channel.use { channel ->
                        channel.map(
                            FileChannel.MapMode.READ_ONLY,
                            afd.startOffset,
                            afd.declaredLength,
                        )
                    }
                }
            } else {
            // Asset missing is the #1 integration error. Throw a clear message instead of a cryptic crash.
            val available = runCatching { context.assets.list("")?.toList().orEmpty() }.getOrDefault(emptyList())
            throw IllegalStateException(
                buildString {
                    append("Missing TFLite model asset: '$modelAssetName'. ")
                    append("This library expects the model to be packaged under assets as '$DEFAULT_MODEL_ASSET_NAME'. ")
                    append("Place it at: close-button-detector/src/main/assets/$DEFAULT_MODEL_ASSET_NAME (for library publishing), ")
                    append("or ensure your final APK contains it in assets/. ")
                    if (available.isNotEmpty()) {
                        append("Available root assets in current APK: $available")
                    }
                },
                e
            )
            }
        }

        interpreter = Interpreter(
            model,
            Interpreter.Options().apply {
                setNumThreads(numThreads)
                if (useNnApi) {
                    setUseNNAPI(true)
                }
            }
        )

        val inputShape = interpreter.getInputTensor(0).shape()
        require(inputShape.size == 4) { "Unexpected input tensor rank: ${inputShape.contentToString()}" }
        require(inputShape[3] == channels) {
            "Unexpected input channels: ${inputShape.contentToString()}"
        }
        inputHeight = inputShape[1]
        inputWidth = inputShape[2]

        logDebug(
            "Loaded model=$modelAssetName, input=${inputShape.contentToString()}, " +
                "output=${interpreter.getOutputTensor(0).shape().contentToString()}"
        )
    }

    override fun close() {
        cachedOutputBuffer = null
        cachedOutputShape = null
        interpreter.close()
    }

    fun detect(bitmap: Bitmap): List<Detection> = detectInternal(bitmap)

    fun detectTopOne(bitmap: Bitmap): Detection? = detect(bitmap).maxByOrNull { it.score }

    fun detectRects(bitmap: Bitmap): List<RectF> = detect(bitmap).map { RectF(it.box) }

    fun detectTopRect(bitmap: Bitmap): RectF? = detectTopOne(bitmap)?.box?.let(::RectF)

    fun hasCloseButton(bitmap: Bitmap): Boolean = detect(bitmap).isNotEmpty()

    private fun detectInternal(bitmap: Bitmap): List<Detection> {
        require(!bitmap.isRecycled) { "Bitmap is recycled" }
        val recycleArgb = bitmap.config != Bitmap.Config.ARGB_8888
        val argbBitmap = if (recycleArgb) {
            bitmap.copy(Bitmap.Config.ARGB_8888, true)
        } else {
            bitmap
        }

        val preprocessed = preprocess(argbBitmap)
        return try {
            detectOnPreprocessed(preprocessed, bitmap.width, bitmap.height)
        } finally {
            for (b in preprocessed.recycleBitmaps) {
                if (!b.isRecycled && b !== bitmap) b.recycle()
            }
            if (recycleArgb && !argbBitmap.isRecycled) {
                argbBitmap.recycle()
            }
        }
    }

    private fun detectOnPreprocessed(
        preprocessed: LetterboxedBitmap,
        srcWidth: Int,
        srcHeight: Int,
    ): List<Detection> {
        val input = bitmapToFloat32Nhwc(preprocessed.bitmap)

        val outputShape = interpreter.getOutputTensor(0).shape()
        val layout = resolveOutputLayout(outputShape)
        logDebug("Output=${outputShape.contentToString()}, layout=$layout")

        val outputBuffer = obtainOutputBuffer(outputShape)
        interpreter.run(input, outputBuffer)

        fun valueAt(predIndex: Int, featIndex: Int): Float {
            return if (layout.channelFirst) {
                outputBuffer[0][featIndex][predIndex]
            } else {
                outputBuffer[0][predIndex][featIndex]
            }
        }

        val candidates = ArrayList<Detection>()

        for (i in 0 until layout.numPred) {
            var x = valueAt(i, 0)
            var y = valueAt(i, 1)
            var w = valueAt(i, 2)
            var h = valueAt(i, 3)

            x *= inputWidth
            y *= inputHeight
            w *= inputWidth
            h *= inputHeight

            if (w <= 0f || h <= 0f) continue

            val objectness = if (layout.hasObjectness) normalizeScore(valueAt(i, 4)) else 1f
            if (objectness <= 0f) continue

            var bestClass = -1
            var bestProb = -1f
            for (c in labels.indices) {
                val clsProb = normalizeScore(valueAt(i, layout.classStart + c))
                if (clsProb > bestProb) {
                    bestProb = clsProb
                    bestClass = c
                }
            }

            val finalScore = bestProb * objectness
            if (bestClass < 0 || finalScore < scoreThreshold) continue

            val rawLeft = x - w / 2f
            val rawTop = y - h / 2f
            val rawRight = x + w / 2f
            val rawBottom = y + h / 2f

            val mapped = clampRect(
                preprocessed.mapRect(rawLeft, rawTop, rawRight, rawBottom),
                srcWidth,
                srcHeight,
            )

            candidates.add(
                Detection(
                    label = labels[bestClass],
                    classId = bestClass,
                    score = finalScore,
                    box = mapped,
                )
            )
        }

        val finalDetections = nms(candidates, iouThreshold)
            .asSequence()
            .filter { d ->
                outputClassIds?.contains(d.classId) ?: (d.classId == closeButtonClassId)
            }
            .filter {
                val boxWidth = it.box.width()
                val boxHeight = it.box.height()
                boxWidth >= 4f && boxHeight >= 4f
            }
            .sortedByDescending { it.score }
            .toList()

        logDebug("Candidates=${candidates.size}, kept=${finalDetections.size}, thr=$scoreThreshold, iou=$iouThreshold")
        return finalDetections
    }

    private fun sigmoid(x: Float): Float = 1f / (1f + kotlin.math.exp(-x))

    private fun normalizeScore(raw: Float): Float = when (scoreNormalization) {
        ScoreNormalizationMode.AUTO_SIGMOID -> if (raw in 0f..1f) raw else sigmoid(raw)
        ScoreNormalizationMode.ALWAYS_SIGMOID -> sigmoid(raw)
        ScoreNormalizationMode.RAW_CLIP -> raw.coerceIn(0f, 1f)
    }

    private fun obtainOutputBuffer(outputShape: IntArray): Array<Array<FloatArray>> {
        if (cachedOutputShape != null &&
            cachedOutputBuffer != null &&
            cachedOutputShape.contentEquals(outputShape)
        ) {
            return cachedOutputBuffer!!
        }
        val buf = Array(outputShape[0]) { Array(outputShape[1]) { FloatArray(outputShape[2]) } }
        cachedOutputShape = outputShape.copyOf()
        cachedOutputBuffer = buf
        return buf
    }

    private data class OutputLayout(
        val channelFirst: Boolean,
        val numPred: Int,
        val numFeat: Int,
        val classStart: Int,
        val hasObjectness: Boolean,
    )

    private data class LetterboxedBitmap(
        val bitmap: Bitmap,
        val scaleX: Float,
        val scaleY: Float,
        val padX: Int,
        val padY: Int,
        /** Bitmaps allocated only for inference; caller recycles after [detectOnPreprocessed]. */
        val recycleBitmaps: List<Bitmap> = emptyList(),
    ) {
        fun mapRect(left: Float, top: Float, right: Float, bottom: Float): RectF {
            return RectF(
                (left - padX) / scaleX,
                (top - padY) / scaleY,
                (right - padX) / scaleX,
                (bottom - padY) / scaleY,
            )
        }
    }

    private fun resolveOutputLayout(outputShape: IntArray): OutputLayout {
        require(outputShape.size == 3) { "Unexpected output tensor rank: ${outputShape.contentToString()}" }

        val featureCountWithoutObjectness = 4 + labels.size
        val featureCountWithObjectness = 5 + labels.size

        fun createLayout(channelFirst: Boolean, numPred: Int, numFeat: Int): OutputLayout {
            val hasObjectness = numFeat == featureCountWithObjectness
            return OutputLayout(
                channelFirst = channelFirst,
                numPred = numPred,
                numFeat = numFeat,
                classStart = if (hasObjectness) 5 else 4,
                hasObjectness = hasObjectness,
            )
        }

        val dim1 = outputShape[1]
        val dim2 = outputShape[2]

        return when {
            dim1 == featureCountWithoutObjectness || dim1 == featureCountWithObjectness -> {
                createLayout(channelFirst = true, numPred = dim2, numFeat = dim1)
            }

            dim2 == featureCountWithoutObjectness || dim2 == featureCountWithObjectness -> {
                createLayout(channelFirst = false, numPred = dim1, numFeat = dim2)
            }

            dim1 < dim2 -> {
                logWarn("Cannot infer output layout; fallback channel-first: ${outputShape.contentToString()}")
                createLayout(channelFirst = true, numPred = dim2, numFeat = dim1)
            }

            else -> {
                logWarn("Cannot infer output layout; fallback channel-last: ${outputShape.contentToString()}")
                createLayout(channelFirst = false, numPred = dim1, numFeat = dim2)
            }
        }
    }

    private fun preprocess(source: Bitmap): LetterboxedBitmap {
        return when (preprocessMode) {
            PreprocessMode.STRETCH -> {
                val resized = Bitmap.createScaledBitmap(source, inputWidth, inputHeight, true)
                LetterboxedBitmap(
                    bitmap = resized,
                    scaleX = inputWidth / source.width.toFloat(),
                    scaleY = inputHeight / source.height.toFloat(),
                    padX = 0,
                    padY = 0,
                    recycleBitmaps = listOf(resized),
                )
            }

            PreprocessMode.LETTERBOX -> {
                val scale = min(inputWidth / source.width.toFloat(), inputHeight / source.height.toFloat())
                val resizedWidth = max(1, (source.width * scale).roundToInt())
                val resizedHeight = max(1, (source.height * scale).roundToInt())
                val dw = inputWidth - resizedWidth
                val dh = inputHeight - resizedHeight
                val padX = ((dw / 2f) - 0.1f).roundToInt()
                val padY = ((dh / 2f) - 0.1f).roundToInt()

                val resized = Bitmap.createScaledBitmap(source, resizedWidth, resizedHeight, true)
                val out = Bitmap.createBitmap(inputWidth, inputHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(out)
                canvas.drawColor(Color.rgb(114, 114, 114))
                canvas.drawBitmap(resized, padX.toFloat(), padY.toFloat(), null)

                LetterboxedBitmap(
                    bitmap = out,
                    scaleX = scale,
                    scaleY = scale,
                    padX = padX,
                    padY = padY,
                    recycleBitmaps = listOf(resized, out),
                )
            }
        }
    }

    private fun nms(dets: List<Detection>, iouThr: Float): List<Detection> {
        val sorted = dets.sortedByDescending { it.score }
        val suppressed = BooleanArray(sorted.size)
        val kept = ArrayList<Detection>()
        for (i in sorted.indices) {
            if (suppressed[i]) continue
            val best = sorted[i]
            kept.add(best)
            for (j in i + 1 until sorted.size) {
                if (suppressed[j]) continue
                val d = sorted[j]
                if (best.classId == d.classId && iou(best.box, d.box) >= iouThr) {
                    suppressed[j] = true
                }
            }
        }
        return kept
    }

    private fun iou(a: RectF, b: RectF): Float {
        val interLeft = max(a.left, b.left)
        val interTop = max(a.top, b.top)
        val interRight = min(a.right, b.right)
        val interBottom = min(a.bottom, b.bottom)
        val interW = max(0f, interRight - interLeft)
        val interH = max(0f, interBottom - interTop)
        val interArea = interW * interH
        val union = a.width() * a.height() + b.width() * b.height() - interArea
        return if (union <= 0f) 0f else interArea / union
    }

    private fun clampRect(r: RectF, w: Int, h: Int): RectF {
        return RectF(
            r.left.coerceIn(0f, w.toFloat()),
            r.top.coerceIn(0f, h.toFloat()),
            r.right.coerceIn(0f, w.toFloat()),
            r.bottom.coerceIn(0f, h.toFloat()),
        )
    }

    private fun bitmapToFloat32Nhwc(bmp: Bitmap): ByteBuffer {
        val bytes = 4 * inputWidth * inputHeight * channels
        val buffer = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder())

        val pixels = IntArray(inputWidth * inputHeight)
        bmp.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)

        var idx = 0
        repeat(inputHeight) {
            repeat(inputWidth) {
                val p = pixels[idx++]
                val r = ((p shr 16) and 0xFF) / 255f
                val g = ((p shr 8) and 0xFF) / 255f
                val b = (p and 0xFF) / 255f
                buffer.putFloat(r)
                buffer.putFloat(g)
                buffer.putFloat(b)
            }
        }
        buffer.rewind()
        return buffer
    }

    private fun logDebug(message: String) {
        if (enableLogging) Log.d(tag, message)
    }

    private fun logWarn(message: String) {
        if (enableLogging) Log.w(tag, message)
    }
}

