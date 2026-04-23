# 贡献指南

感谢你对 android-close-button-detector 的关注！欢迎提交 Issue 和 Pull Request。

## 提交 Issue

- Bug 报告：请包含复现步骤、预期行为和实际行为
- 功能请求：请描述使用场景和期望的 API 设计
- 提问：请先搜索已有 Issue，避免重复

## 提交 Pull Request

1. Fork 本仓库
2. 创建功能分支：`git checkout -b feature/my-feature`
3. 提交变更：`git commit -m 'Add some feature'`
4. 推送分支：`git push origin feature/my-feature`
5. 创建 Pull Request

## 代码规范

- 遵循 [Kotlin 编码规范](https://kotlinlang.org/docs/coding-conventions.html)
- 公共 API 必须有 KDoc 注释
- 新功能请在 `demo` 中提供可运行入口，或在 PR 中写明手测步骤
- 提交信息使用英文，格式参考 [Conventional Commits](https://www.conventionalcommits.org/)

## 开发环境

- JDK 17+
- Android SDK 35
- Kotlin 2.0+

## 构建与验证

```bash
./gradlew :close-button-detector:assembleRelease
./gradlew :demo:assembleDebug
```

## 发版前补充检查（R8）

Release 打包冒烟后，若收紧混淆可使用 `-printusage` 审查；调整本库 `consumer-rules.pro` 时避免过度 `-keep`，并同步验证 demo XML 自定义 View inflation。
