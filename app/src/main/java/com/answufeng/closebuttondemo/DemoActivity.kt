package com.answufeng.closebuttondemo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.answufeng.closebuttondetector.CloseButtonDetector
import com.answufeng.closebuttondetector.Detection
import java.util.Locale

/**
 * Minimal demo for `android-close-button-detector`.
 *
 * It loads a list of images from `assets/test_images/`, runs detection, and renders
 * the result with red bounding boxes.
 */
class DemoActivity : AppCompatActivity() {

    private val imageAssetList = listOf(
        "test_images/img_0351.jpg",
        "test_images/img_0459.jpg",
        "test_images/img_0627.jpg",
        "test_images/img_0656.jpg",
    )

    private var currentIndex = 0
    private lateinit var detector: CloseButtonDetector
    private lateinit var imageView: ImageView
    private lateinit var infoText: TextView
    private lateinit var btnPrev: Button
    private lateinit var btnNext: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_close_button_test)

        imageView = findViewById(R.id.imageView)
        infoText = findViewById(R.id.infoText)
        btnPrev = findViewById(R.id.btnPrev)
        btnNext = findViewById(R.id.btnNext)

        detector = try {
            CloseButtonDetector(
                context = this,
                scoreThreshold = 0.8f,
                iouThreshold = 0.45f,
                enableLogging = false,
            )
        } catch (e: IllegalStateException) {
            infoText.text = buildString {
                append("Failed to init detector.\n\n")
                append(e.message ?: e.toString())
            }
            btnPrev.isEnabled = false
            btnNext.isEnabled = false
            return
        }

        btnPrev.setOnClickListener {
            if (currentIndex > 0) {
                currentIndex--
                detectCurrentImage()
            }
        }

        btnNext.setOnClickListener {
            if (currentIndex < imageAssetList.size - 1) {
                currentIndex++
                detectCurrentImage()
            }
        }

        detectCurrentImage()
    }

    private fun detectCurrentImage() {
        val options = BitmapFactory.Options().apply {
            inScaled = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val imageAssetPath = imageAssetList[currentIndex]
        val bitmap = assets.open(imageAssetPath).use { input ->
            BitmapFactory.decodeStream(input, null, options)
        } ?: error("Failed to decode image from assets: $imageAssetPath")

        Log.d("CloseButtonDemo", "Image=$imageAssetPath size=${bitmap.width}x${bitmap.height} config=${bitmap.config}")

        val results = detector.detect(bitmap)

        btnPrev.isEnabled = currentIndex > 0
        btnNext.isEnabled = currentIndex < imageAssetList.size - 1

        val imageName = imageAssetPath.substringAfterLast('/')
        val sizeText = "${bitmap.width}x${bitmap.height}"

        if (results.isEmpty()) {
            infoText.text = "Image: $imageName ($sizeText)\nNo close button detected\n${currentIndex + 1}/${imageAssetList.size}"
            imageView.setImageBitmap(bitmap)
        } else {
            infoText.text = buildString {
                append("Image: $imageName ($sizeText)\n")
                append("Detections: ${results.size}\n")
                results.forEachIndexed { idx, d ->
                    append("[$idx] ${d.label} score=${String.format(Locale.US, "%.3f", d.score)} ")
                    append("box=(${d.box.left.toInt()}, ${d.box.top.toInt()}, ${d.box.right.toInt()}, ${d.box.bottom.toInt()})\n")
                }
                append("${currentIndex + 1}/${imageAssetList.size}")
            }

            imageView.setImageBitmap(drawDetectionsRedBoxes(bitmap, results))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::detector.isInitialized) detector.close()
    }
}

private fun drawDetectionsRedBoxes(source: Bitmap, detections: List<Detection>): Bitmap {
    val out = source.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = android.graphics.Canvas(out)

    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.RED
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = maxOf(3f, source.width / 300f)
        isAntiAlias = true
    }

    detections.forEach { d -> canvas.drawRect(d.box, paint) }
    return out
}

