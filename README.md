## android-close-button-detector

[![](https://jitpack.io/v/answufeng/android-close-button-detector.svg)](https://jitpack.io/#answufeng/android-close-button-detector)

一个用于 **在 Android 设备上，根据图片/截图定位“关闭按钮”位置** 的轻量库（TensorFlow Lite 推理）。

内置模型基于 **3000+ 张图片/截图样本** 训练，面向常见 App UI 场景中的“关闭按钮”检测与定位。

如果你只想最快接入并跑通第一个检测结果，直接看下面的「5 分钟上手」即可；其它内容都可以后置按需查阅。

---

## 5 分钟上手（最小接入）

### 1) 添加依赖（JitPack）

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

// app/build.gradle.kts
dependencies {
    implementation("com.github.answufeng:android-close-button-detector:1.0.2")
}
```

版本号与 GitHub Release / Tag 保持一致（当前：`1.0.2`）。

### 2) 模型文件（assets）

本库使用 **随 AAR 一起发布的默认模型**，默认文件名为 `best_float32.tflite`（当前不暴露运行时替换入口）。

你在「使用方 App」里 **不需要** 额外放模型文件；只要正常添加依赖即可。

### 3) 最小调用（输入 Bitmap → 输出检测框）

```kotlin
val detector = CloseButtonDetector(
    context = context,
    scoreThreshold = 0.8f,
    iouThreshold = 0.45f,
    preprocessMode = PreprocessMode.LETTERBOX,
    enableLogging = false
)

val detections: List<Detection> = detector.detect(bitmap)
val bestRect: RectF? = detector.detectBestRect(bitmap, BestCloseButtonStrategy.TOP_RIGHT)

detector.close()
```

---

## 目录（按常见需求跳转）

| 想做什么 | 跳转到 |
|----------|--------|
| 最短时间跑通依赖与第一个检测结果 | [5 分钟上手（最小接入）](#5-分钟上手最小接入) · [环境要求](#环境要求) |
| 看能力列表与可配置项 | [功能概览](#功能概览) · [可配置项](#可配置项) |
| 线程/性能注意事项 | [协程与线程（并发说明）](#协程与线程并发说明) |
| 本地跑 Demo | [运行 Demo](#运行-demo) |
| 常见问题（缺模型、Bitmap 格式等） | [常见问题](#常见问题) |

---

## 环境要求

| 项目 | 最低版本 |
|------|----------|
| Android minSdk | 21 |
| JDK | 17（AGP 8.x） |

---

## 功能概览

- **输入**：`Bitmap`
- **输出**：关闭按钮候选框 `Detection`（含 `score`、`RectF`）
- **最佳候选策略**：
  - `BestCloseButtonStrategy.HIGHEST_SCORE`：最高分
  - `BestCloseButtonStrategy.TOP_RIGHT`：更偏右上角（更贴近“关闭按钮”常见布局）
- **可配置**：阈值、NMS IOU、预处理方式（拉伸/letterbox）、线程数、日志开关

---

## 可配置项

构造参数（示例见「5 分钟上手」）常用的几项：

- **`scoreThreshold`**：过滤低置信度候选
- **`iouThreshold`**：NMS 抑制阈值（避免重复框）
- **`preprocessMode`**：`PreprocessMode.STRETCH` / `PreprocessMode.LETTERBOX`
- **`numThreads`**（如有）：TFLite 推理线程数
- **`enableLogging`**：调试时建议打开

---

## 协程与线程（并发说明）

- **线程安全**：`CloseButtonDetector` **不支持并发调用**；如需并行处理，请每个线程创建一个 detector（或自行做串行队列）。
- **Bitmap 格式**：推荐传入 `ARGB_8888`，否则内部可能会复制一份（额外内存与耗时）。
- **资源释放**：用完务必调用 `close()`（实现了 `AutoCloseable` 时可用 `use { }` 风格）。

---

## 运行 Demo

本仓库包含两个模块：

- **`close-button-detector`**：可发布到 JitPack 的库模块
- **`demo`**（目录名为 `app/`）：最小可运行示例，演示如何加载 assets 图片并画框

直接运行 `demo` 模块，启动后可以在内置的几张测试图之间切换并查看检测结果：

```bash
./gradlew :demo:installDebug
```

---

## 公开 API

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

## 常见问题

### Q: 启动时报 `Missing TFLite model asset: best_float32.tflite`？

这通常意味着打包产物里没有包含模型文件。

如果你是 **库作者/在本仓库开发**，请确认模型文件存在于：

- `close-button-detector/src/main/assets/best_float32.tflite`

如果你是 **依赖方 App**，请先确认：

- 依赖版本是否是你预期的版本（例如 `1.0.2`）
- 是否有做 AAR/资源裁剪、或自定义打包流程导致 assets 被移除

---

## 许可证

Apache License 2.0，见 `LICENSE`。

