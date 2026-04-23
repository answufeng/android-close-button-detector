## android-close-button-detector

一个用于 **在 Android 设备上，根据图片/截图定位“关闭按钮”位置** 的轻量库（TensorFlow Lite 推理）。

本仓库包含两个模块：
- **`close-button-detector`**：可发布到 JitPack 的库模块
- **`demo`**（目录名仍为 `app/`）：最小可运行示例，演示如何加载 assets 图片并画框

---

### 1. 功能特性

- **输入**：`Bitmap`
- **输出**：关闭按钮候选框 `Detection`（含 `score`、`RectF`）
- **策略**：支持“最高分”/“更偏右上角”两种最佳候选选取策略
- **可配置**：阈值、NMS IOU、预处理方式（拉伸/letterbox）、线程数、日志开关

### 1.1 环境要求

- Android **minSdk 21**
- JDK **17**（AGP 8.x）

---

### 2. 通过 JitPack 集成

#### Step 1：添加 JitPack 仓库

在根 `settings.gradle(.kts)` 的 `dependencyResolutionManagement.repositories` 中加入：

```kotlin
repositories {
    mavenCentral()
    google()
    maven(url = "https://jitpack.io")
}
```

#### Step 2：添加依赖

```kotlin
dependencies {
    implementation("com.github.answufeng:android-close-button-detector:<tag>")
}
```

`<tag>` 使用 GitHub Release / Tag 版本号，例如 `1.0.0`。

---

### 3. 模型文件（assets）

本库使用 **随 AAR 一起发布的默认模型**，默认文件名固定为 `best_float32.tflite`（不暴露运行时替换入口）。

你需要把模型文件放到：

- `close-button-detector/src/main/assets/best_float32.tflite`

如果模型文件较大，建议使用 Git LFS 管理。

---

### 3.1 常见问题

#### Q: 启动时报 `Missing TFLite model asset: best_float32.tflite`？

A: 确认模型文件已放在：

- `close-button-detector/src/main/assets/best_float32.tflite`

并重新编译安装（确保它被打包进最终 APK 的 `assets/`）。

### 4. 快速使用

```kotlin
val detector = CloseButtonDetector(
    context = context,
    scoreThreshold = 0.8f,
    iouThreshold = 0.45f,
    preprocessMode = PreprocessMode.LETTERBOX,
    enableLogging = false,
)

val detections: List<Detection> = detector.detect(bitmap)
val bestRect: RectF? = detector.detectBestRect(bitmap, BestCloseButtonStrategy.TOP_RIGHT)

detector.close()
```

### 4.1 使用建议（性能/线程）

- **Bitmap 格式**：推荐传入 `ARGB_8888`，否则内部会复制一份（会有额外内存与耗时）。
- **线程安全**：`CloseButtonDetector` **不支持并发调用**；如需并行处理，请每个线程创建一个 detector。

---

### 5. 运行 Demo

直接运行 `demo` 模块（目录名为 `app/`），启动后可以在内置的几张测试图之间切换并查看检测结果。

```bash
./gradlew :demo:installDebug
```

---

### 6. 公开 API

- `CloseButtonDetector`
  - `detect(bitmap): List<Detection>`
  - `detectBest(bitmap, strategy): Detection?`
  - `detectBestRect(bitmap, strategy): RectF?`
  - `hasCloseButton(bitmap): Boolean`
- `CloseButtonDetectorConfig`
- `Detection`
- `BestCloseButtonStrategy`
- `PreprocessMode`

---

### 7. License

Apache-2.0（见 `LICENSE`）。

