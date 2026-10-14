<p align="center">
  <img src="docs/assets/app-icon.svg" width="112" height="112" alt="M1racle Reader 应用图标" />
</p>

<h1 align="center">M1racle Reader</h1>

<p align="center">
  <strong>把阅读留在本地。</strong><br />
  面向 Android 的本地文档阅读器 · Markdown / PDF / EPUB<br />
  无账号 · 无广告 · 无联网权限
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-12%2B-3DDC84?logo=android&amp;logoColor=white" alt="Android 12 及以上" />
  <img src="https://img.shields.io/badge/Kotlin-2.3.21-7F52FF?logo=kotlin&amp;logoColor=white" alt="Kotlin 2.3.21" />
  <img src="https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4?logo=jetpackcompose&amp;logoColor=white" alt="Jetpack Compose 与 Material 3" />
  <img src="https://img.shields.io/badge/Status-Candidate-D49A35" alt="候选版本，持续验证中" />
</p>

<p align="center">
  <a href="#开始使用"><strong>开始使用</strong></a>
  &nbsp;·&nbsp;
  <a href="docs/CURRENT_STATUS.md">版本与验证</a>
  &nbsp;·&nbsp;
  <a href="CHANGELOG.md">更新日志</a>
  &nbsp;·&nbsp;
  <a href="CODE_WIKI.md">Code Wiki</a>
  &nbsp;·&nbsp;
  <a href="https://github.com/ldohgfsdu/m1racle-reader/issues">反馈问题</a>
</p>

---

## 目录

- [功能亮点](#功能亮点)
- [开始使用](#开始使用)
- [隐私与文件安全](#隐私与文件安全)
- [当前边界](#当前边界)
- [技术栈](#技术栈)
- [系统与构建](#系统与构建)
- [项目结构](#项目结构)
- [反馈与贡献](#反馈与贡献)
- [文档导航](#文档导航)
- [致谢与许可](#致谢与许可)

---

## 功能亮点

### 阅读体验

- **多格式阅读** — Markdown / TXT / PDF / 可重排 EPUB，支持导入包含 Markdown 与配套图片的 ZIP 包。
- **自研 PDF 阅读层** — 连续页面布局、视口坐标、双指缩放与拖动控制，支持连续滚动和横向分页。
- **自研高清瓦片调度** — 底图与细节瓦片分层显示，优先渲染可见区域，结合缓存管理与过期结果校验。
- **Markdown 文档排版** — 标题、列表、引用、表格、本地图片与目录跳转；代码块支持复制、横向滚动和轻量语法高亮。
- **3 种 EPUB 阅读模式** — 滑动翻页 / 连续阅读 / 卷页，共用文字锚点保存阅读位置。
- **字体与排版调节** — Markdown 支持字号、行高和页边距；EPUB 内置宋体，支持字号调整与导入 TTF / OTF 字体。
- **选字、高亮与笔记** — Markdown / PDF 支持文字选择和阅读批注，记录保存在本地。
- **阅读位置恢复** — 保存阅读进度与书签；PDF 支持恢复页内位置、缩放和偏移。
- **日夜阅读** — 系统深浅色与动态配色、PDF 反色，以及随阅读滚动收起的控件。

> PDF 阅读层由项目自主实现，底层页面绘制使用 Android `PdfRenderer`，文本和文档处理使用 PDFBox Android，详见 [PDF 子系统](docs/code-wiki/06-pdf.md)。EPUB 连续与卷页模式仍需完成完整场景验收。

### PDF 文档工具

- **创建与处理** — 本地创建、合并、加密 PDF，处理结果另存为新文件。
- **章节与标注导出** — 按章节导出 PDF 或章节包，输出包含支持的阅读批注的 PDF。
- **文字提取与有限编辑** — 将可提取文字转为 Markdown，支持受限范围的 PDF 正文编辑。

> PDF 转 Markdown 不包含 OCR，也不保留复杂版式。文档工具及完整操作流程的验收边界见[功能验收矩阵](docs/STABILIZATION_20261013.md#核心功能验收矩阵)。

### 资料管理与阅读记录

- **文件导入** — 系统文件选择器、系统「打开方式」或分享面板；支持 `.md`、`.markdown`、`.txt`、`.pdf`、`.epub`、`.zip`。
- **本地书库** — 最近阅读、收藏、封面、书库条目改名、文件信息与原文件导出。
- **阅读记录** — 保存进度、书签、高亮和笔记；本地搜索可查找文档标题、高亮摘录与笔记，并跳回原文。
- **文件扫描** — 用户主动授权后扫描共享存储中的支持格式，不进入其他应用私有目录。

### 界面与系统体验

- **原生界面** — Jetpack Compose 与 Material 3，支持系统深浅色和动态配色。
- **液态玻璃控件** — 底栏与阅读控件采样实际背景，配合按压、拖动和回弹反馈。
- **系统滚动截图适配** — 接入 Android 滚动捕获；Markdown 与 PDF 连续视口已有原生协议验证，厂商截图面板仍需分别确认。

> 当前为持续验证中的候选版本。上述功能介绍说明当前实现范围；最新构建、自动检查、模拟器验证和待验项目统一记录在[项目状态](docs/CURRENT_STATUS.md)。

---

## 开始使用

**需要 Android 12 或更高版本。** 当前源码已公开，尚未发布可下载的 GitHub APK Release；候选包与验收状态见[项目状态](docs/CURRENT_STATUS.md)。开发者可按下方说明自行构建。

安装后：

1. 打开应用，点击底栏中央的 **＋** 导入文件。
2. 选择本地文档；也可以在系统文件管理器中选择「用 M1racle Reader 打开」，或通过分享面板导入。
3. 导入后进入阅读页，返回主页可继续访问最近阅读的文档。

想试读 Markdown，可使用[综合样例](docs/sample-large.md)。样例不会自动加入书库，需要手动导入；带相对路径图片的文档建议将文档和图片一起打包为 ZIP。

## 隐私与文件安全

- **离线运行** — 应用不申请 `INTERNET` 权限，不加载网络图片，不提供账号、广告、统计 SDK 或云同步。
- **显式授权** — 常规导入使用系统文件选择器；共享存储扫描需要用户另行授予「所有文件访问」权限。
- **删除记录与删除原文件分开** — 删除最近阅读记录不会删除原始文件；无其他记录引用的应用自管副本会按规则清理。
- **工具输出另存** — PDF 处理工具通过用户选择的位置保存新文件，不直接覆盖输入文件。

## 当前边界

- EPUB 目前仅支持有限的可重排阅读，不支持 DRM、固定版式、音视频、书内搜索或 EPUB 批注。
- Markdown 编辑暂未开放入口，等待 M1racle Note；当前专注阅读、选字和批注。
- 跨文档搜索覆盖标题、高亮和笔记，尚不提供正文全文索引。
- 当前不提供网络书源、WebDAV / 云同步、在线翻译、AI 问答或 OCR。
- 最新候选版的物理手机安装与最终视觉验收仍有待完成；系统版本与设备覆盖情况以[项目状态](docs/CURRENT_STATUS.md)为准。

---

## 技术栈

| 层 | 实现 |
|---|---|
| 界面 | Kotlin · Jetpack Compose · Material 3 · Navigation Compose |
| 状态与异步 | ViewModel · Kotlin Coroutines · Flow |
| 本地数据 | Room · DataStore · Android Storage Access Framework |
| Markdown | 块与行内解析、文本投影、代码高亮、表格和本地图加载 |
| PDF 阅读层 | 自主实现连续视口、页面几何、缩放与拖动控制、瓦片调度与缓存、位置恢复 |
| PDF 底层能力 | Android `PdfRenderer` 页面与图块绘制 · PDFBox Android 文本、大纲与文档处理 |
| EPUB | 原生文本排版与分页 · 私有字体库 · PageCurl 卷页组件 |
| 玻璃材质 | Kyant Backdrop / AndroidLiquidGlass · 统一材质与动效参数 |
| 验证 | JVM 单测 · Robolectric · Android 仪器测试 · Macrobenchmark 模块 |

## 系统与构建

| 项目 | 要求 |
|---|---|
| 运行系统 | Android 12+（minSdk 31，targetSdk 35） |
| 应用包名 | `com.m1racle.reader` |
| JDK | 17 |
| 构建工具 | Gradle 9.5.0（自带 Wrapper）· Android Gradle Plugin 9.1.1 |
| Android SDK | compileSdk 37 · Build Tools 36.0.0 |

```bash
git clone https://github.com/ldohgfsdu/m1racle-reader.git
cd m1racle-reader
```

配置 JDK 17 和 Android SDK，在本机 `local.properties` 中设置 `sdk.dir`。该文件不提交到仓库。Release 构建还需要通过环境变量注入签名信息：

| 环境变量 | 内容 |
|---|---|
| `M1RACLE_RELEASE_STORE_FILE` | 签名密钥库路径 |
| `M1RACLE_RELEASE_STORE_PASSWORD` | 密钥库密码 |
| `M1RACLE_RELEASE_KEY_ALIAS` | 密钥别名 |
| `M1RACLE_RELEASE_KEY_PASSWORD` | 密钥密码 |

签名材料不随源码提供；请使用自己的密钥。自行签名的 APK 不能覆盖安装使用不同证书签名的版本。不要将密钥或密码写入仓库或构建命令参数。

```bash
# macOS / Linux：构建 Release
./gradlew :app:assembleRelease

# Windows：构建 Release
.\gradlew.bat :app:assembleRelease
```

成功构建后，APK 自动导出到 `.external_outbox/`，最新包为 `m1racle-reader-release-latest.apk`。完整交付流程见[构建与交付说明](docs/APK_DELIVERY.md)。

<details>
<summary>开发验证与特殊环境</summary>

Windows 可使用仓库内的统一验证入口：

```powershell
# 阅读相关快速回归
.\scripts\verify-reader.ps1 -Mode Reading

# 全量 QA / Release 单测与 Release Lint
.\scripts\verify-reader.ps1 -Mode Full
```

跨平台也可直接使用 Wrapper：

```bash
./gradlew :app:testQaUnitTest :app:testReleaseUnitTest :app:lintRelease
```

QA 使用独立应用沙箱，仅用于测试。设备测试、正式签名和发布门禁见[功能分层与验证入口](docs/FEATURE_LAYERS_AND_VERIFICATION.md)及[发布清单](RELEASE_CHECKLIST.md)。

ARM 主机构建时，需要在本机 Gradle 配置中提供适配当前主机的 `aapt2`；不要将本机路径写入项目配置。常规 Windows / Linux x86_64 / macOS 构建使用 AGP 提供的工具。

</details>

---

## 项目结构

```text
app/src/main/java/com/m1racle/reader/
├── data/             # 数据库、设置、仓库与阅读锚点
├── documents/        # 本地文档与 PDF 处理工具
├── epub/             # EPUB 解析与内容模型
├── identity/         # 文档身份与内容匹配
├── image/            # 本地图片解析与加载
├── markdown/         # Markdown 解析与文本投影
├── packageimport/    # 文档包导入
├── pdf/              # PDF 页面、图块与视口状态
├── search/           # 本地搜索
├── share/            # 系统打开与分享导入
└── ui/               # 书库、阅读器、批注、导航与材质

app/src/test/         # 公共 JVM 测试
app/src/testQa/       # QA 专用 JVM 测试
app/src/testRelease/  # Release 专用 JVM 测试
app/src/androidTest/  # Android 仪器测试
benchmark/           # 性能测试模块
docs/                # 产品、设计、使用及验证文档
```

模块关系、调用链和源码索引见 [Code Wiki](CODE_WIKI.md)。

## 反馈与贡献

- **反馈问题** — 请提交 [GitHub Issue](https://github.com/ldohgfsdu/m1racle-reader/issues)，附上应用版本、设备型号、Android 版本、复现步骤，以及方便分享的截图或最小样例。
- **提出改进** — 描述实际阅读场景、当前操作和预期结果；较大改动建议先通过 Issue 讨论。
- **提交代码** — 先阅读[工程约定](AGENTS.md)，为行为变更提供相关验证，并区分自动测试、模拟器验证与实机验收。

上传样例和日志前，请移除个人信息；私人文档可用能复现问题的简化样例代替。

## 文档导航

| 文档 | 内容 |
|---|---|
| [项目状态](docs/CURRENT_STATUS.md) | 当前候选、验证结果与待验事项 |
| [更新日志](CHANGELOG.md) | 历次功能与修复记录 |
| [产品范围](docs/PRD.md) | 产品定位与需求边界 |
| [设计规范](design.md) | 页面、排版、材质和交互要求 |
| [Code Wiki](CODE_WIKI.md) | 项目结构、模块职责与源码入口 |
| [构建与验证](docs/FEATURE_LAYERS_AND_VERIFICATION.md) | 开发检查和交付流程 |
| [文档索引](docs/PROJECT_INDEX.md) | 当前文档入口与历史报告导航 |

## 致谢与许可

感谢 Android / Jetpack Compose、PDFBox Android、Kyant Backdrop / AndroidLiquidGlass、PageCurl 和 Noto 字体等项目提供的基础能力。

第三方组件的许可与来源声明见 [LICENSES](LICENSES/) 和[随包许可文件](app/src/main/assets/licenses/)。仓库目前尚未提供项目级 `LICENSE` 文件。

---

<p align="center">
  <sub>M1racle Reader · 专注本地文档与阅读体验</sub>
</p>
