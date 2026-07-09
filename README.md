# 墨阅 MarkLeaf

Android **本地优先** Markdown 阅读器。

**无账号 · 无广告 · 无云同步 · 不申请 INTERNET 权限**

## MVP 已支持

- 通过系统文件选择器导入本地 `.md` / `.markdown` / `.txt`
- 主页最近阅读列表（时间、大小、阅读进度）
- 删除历史记录（只删 Room，不删真实文件）
- 基础 Markdown 分块渲染（标题、段落、引用、列表、代码块、分割线）
- TOC 目录抽屉与点击跳转、当前标题高亮
- 阅读位置保存 / 恢复（`lastScrollIndex` / `lastScrollOffset` / `readingProgress`）
- 深色 / 浅色（跟随系统，支持 Dynamic Color）

## 暂不支持

- Markdown 编辑
- 文档内 / 全文搜索
- 云同步（WebDAV / GitHub 等）
- 代码语法高亮
- 图片渲染
- 表格渲染
- 行内样式完整渲染（如链接预览）

## 技术栈

- Kotlin
- Jetpack Compose
- Material 3
- Navigation Compose
- Room
- Coroutines / Flow
- Android Storage Access Framework（SAF）

## 运行

```bash
export ANDROID_HOME=/path/to/Android/Sdk
echo "sdk.dir=$ANDROID_HOME" > local.properties

./gradlew :app:assembleDebug
# 或
./gradlew :app:installDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

- 最低系统：Android 12（API 31）
- Android Studio：打开仓库根目录 → Run `app`

### arm64 本机构建说明

若 aarch64 主机上 AGP 自带 aapt2 不可用，可用仓库内 `tools/aapt2`（qemu 包装 SDK build-tools）。  
`gradle.properties` 中 `android.aapt2FromMavenOverride` 需为本机绝对路径；x86_64 / 常规 Android Studio 可删除该行。

## 测试样例

仓库内提供开发用样例（**不会打包进 APK**）：

- [docs/sample-large.md](docs/sample-large.md) — 覆盖标题 / 列表 / 引用 / 代码块 / 长行
- [docs/PRD.md](docs/PRD.md) — 产品需求文档

将样例拷到手机任意目录后，在 App 内通过「导入文件」选择即可。

## 文档

- [PRD](docs/PRD.md)
