# M1racle Reader

**一句话：** 面向 Android 的本地优先、无账号、无广告、无联网依赖的**本地文档阅读器**，当前支持 **Markdown、PDF 与有限 EPUB 阅读**。

当前候选与发布状态见 [项目状态](docs/CURRENT_STATUS.md)。

技术身份：

- Product / Brand：**M1racle Reader / M1racle**
- Android `applicationId` / Kotlin namespace：`com.m1racle.reader`
- Repository：`m1racle-reader`

## 隐私与安全

- **不申请 `INTERNET` 权限**，不发起网络请求
- **无账号、无广告、无统计 SDK、无云同步**
- 文件仅在你主动通过系统选择器或系统「打开方式 / 分享」授权后可读
- 删除「最近阅读」记录**永不删除你的源文件**；仅当记录对应的是 App 自管的私有副本（外部打开暂存的 `files/library`、`.zip` 包解压的 `files/packages`、EPUB 解压的 `files/epubs`）且无其他记录引用时，才会一并清理该副本

## 当前已支持

| 能力 | 说明 |
|------|------|
| 本地导入 | SAF 选择 `.md` / `.markdown` / `.txt` / `.pdf` / `.epub` / `.zip`，持久读权限 |
| 外部打开 | 系统「打开方式」与「分享」直接进入阅读（含旋转/进程恢复后的导航恢复） |
| 最近阅读 | 文件名、时间、大小、进度；可删历史 |
| Markdown 基础渲染 | 标题 H1–H6、段落、引用、列表、分割线 |
| Inline 样式 | 加粗、斜体、行内代码、链接文本（不联网预览） |
| 代码块 | 语言标签、复制、横向滚动、轻量语法高亮 |
| 表格 | 标准 pipe table、对齐、横向滚动 |
| 图片 | `asset://` 内置样例；SAF/file 本地图与**安全相对路径图片**；网络图显示「网络图片已禁用」占位 |
| TOC | H1–H3 目录抽屉、点击跳转、当前标题高亮、锚点跳转 |
| 文档内搜索 | 关键词、上一个/下一个、block 高亮 |
| 阅读位置 | 保存/恢复滚动位置与进度 |
| 阅读显示 | 字号 / 行高 / 边距（DataStore 持久化） |
| PDF 阅读 | 连续/单页模式、双指缩放平移、页面缩略图、教材目录大纲、章节导出（ZIP 包 / 标注 PDF） |
| EPUB 阅读 | 本地 reflowable 章节、分页、目录、图片及阅读位置；不支持 DRM、固定版式、音视频、搜索与批注 |
| Markdown 包 | 导入 `.zip` 打包的 Markdown + 相对图片（防 Zip-Slip / 炸弹条目） |
| 阅读资产 | 收藏、书签、Markdown / PDF 高亮与轻量笔记；按文档和 canonical anchor 恢复 |
| 本地搜索 | 跨文档搜索文档标题、高亮摘录与笔记，并跳回原文页/段 |
| 主题 | 跟随系统深浅色（夜间独立液态玻璃配方），Material 3 Dynamic Color |

## 明确不支持（当前版本）

- Markdown **编辑器**
- 文档正文的**全文索引**（当前跨文档搜索范围为标题、高亮与笔记）
- **云同步**（WebDAV / GitHub 等）
- **账号** / 登录 / 社区
- **网络图片**加载（`http`/`https` 显示「网络图片已禁用」）
- 行内图片、图片点击放大、文件夹批量导入
- 完整编译器级语法高亮、复杂 Excel 表格

## 安装方式

### 已构建的 Release APK（推荐）

每次成功构建会自动导出到仓库 outbox：

```text
.external_outbox/m1racle-reader-release-latest.apk
```

并同步交付到设备侧（可直接安装）：

```text
/storage/emulated/0/Download/m1racle-reader-release-latest.apk
```

详见 [docs/APK_DELIVERY.md](docs/APK_DELIVERY.md)；辅助脚本 `scripts/install-release-outbox.sh`。

### 从源码构建

```bash
export ANDROID_HOME=/path/to/Android/Sdk
echo "sdk.dir=$ANDROID_HOME" > local.properties

# 先安全注入正式 Release 签名环境，见 RELEASE_CHECKLIST.md
./gradlew :app:assembleRelease
# 只构建和交付 Release；签名缺失时停止打包，不改用 Debug
# 注意：本机默认 JDK 25 时需 export JAVA_HOME 指向 JDK 17
```

Windows 的 `local.properties` 使用属性文件转义，例如 `sdk.dir=C\:/Android/Sdk`；不要把本机路径提交。

- **最低系统：** Android 12（API 31）
- **包名：** `com.m1racle.reader`
- Android Studio：打开仓库根目录 → Run `app`

### arm64 本机构建说明

构建使用 JDK 17、Gradle 9.5.0、AGP 9.1.1、Android SDK Platform 37.0 与 Build Tools 36.0.0。
Windows / Linux x86_64 / macOS 默认使用 AGP 下载的原生 aapt2。只有 ARM 主机需要在本机 `~/.gradle/gradle.properties` 配置 `android.aapt2FromMavenOverride=/absolute/path/to/tools/aapt2`，经 qemu 运行仓库脚本；不要把该 override 提交到项目。Windows 使用 `gradlew.bat`，SDK 安装包名为 `platforms;android-37.0`。依赖优先使用官方仓库，镜像仅作后备。

## 如何导入文档

1. 打开 App → 点右下角 **导入** FAB；或从系统文件管理器 / 分享面板选择「M1racle Reader」打开。
2. 选择 `.md` / `.markdown` / `.txt` / `.pdf` / `.epub` / `.zip` 文件。
3. 导入成功后直接进入阅读页；返回主页可见「最近阅读」。

## 测试样例

| 文件 | 说明 |
|------|------|
| [docs/sample-large.md](docs/sample-large.md) | 综合验收样例（标题/列表/代码/表格/图片/搜索） |
| [docs/PRD.md](docs/PRD.md) | 产品需求文档 |
| [docs/ACCEPTANCE.md](docs/ACCEPTANCE.md) | MVP 验收清单记录 |

样例 **不会** 作为默认文档自动导入；请通过「导入文件」选择。
图片验证请使用样例中的：

```markdown
![内置本地图片](asset://sample-image.png "本地加载验证")
```

（内置资源，无需存储权限。）

## 技术栈

- Kotlin · Jetpack Compose · Material 3
- Navigation Compose · Room · DataStore
- Coroutines / Flow · SAF
- 自研 Markdown 块解析 / Inline / 轻量代码高亮 / 表格 / 本地图加载（含安全相对路径解析）
- PDF：Android `PdfRenderer` 渲染 + Apache PDFBox（元数据/大纲）+ `backdrop` 液态玻璃 chrome
- 测试：JVM 单测（Robolectric Compose 规则，QA 沙箱变体）+ 仪器测试 + Macrobenchmark 模块

## 文档

- [产品 PRD](docs/PRD.md)
- [验收清单 ACCEPTANCE](docs/ACCEPTANCE.md)
- [测试样例](docs/sample-large.md)

## 版本说明

| 标记 | 含义 |
|------|------|
| **v1.0.0 candidate** | M1racle Reader 首个公开候选；新 Android/Kotlin 身份为 `com.m1racle.reader` |
| **v0.1.0** | 本地 Markdown + PDF 阅读功能完整、可日常使用；编辑、同步、正文全文索引等明确不做 |
| release APK | minify+shrink；仅接受环境注入的独立 release signer，无 debug fallback |

欢迎在本地使用与反馈；后续迭代以隐私与阅读体验为先。
