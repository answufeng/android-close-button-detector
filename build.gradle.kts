plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ktlint) apply false
}

// JitPack 預設會執行根專案的 `publishToMavenLocal`。
// 這裡提供一個聚合任務，轉而發布 library module。
tasks.register("publishToMavenLocal") {
    dependsOn(":close-button-detector:publishToMavenLocal")
}
