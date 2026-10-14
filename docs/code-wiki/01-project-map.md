# 01 项目与仓库地图

[返回目录](README.md)

## 项目身份与范围

M1racle Reader 是 Android 本地文档阅读器，应用包名和 Kotlin namespace 均为 `com.m1racle.reader`。主要格式为 Markdown/纯文本、PDF、有限 reflowable EPUB，以及带相对资源的 Markdown ZIP 包。最低系统 API 31，target API 35，compile API 37。

主要能力是最近阅读与书库、文内 Markdown 搜索、标题/摘录/笔记的跨文档搜索、收藏与书签、Markdown/PDF 高亮与笔记、PDF 目录与印刷页映射、章节导出、有限 PDF 正文编辑、EPUB 三种阅读模式和字体设置。代码还包含 PDF 新建、合并、加密、文字提取等文档工具。

当前 Manifest 不申请 `INTERNET`，未配置账户、广告、分析或云同步 SDK。构建下载依赖仍需访问 Maven/Gradle 仓库，“离线运行”与“首次离线构建”是不同条件。

**权限细节：**当前 [Manifest](../../app/src/main/AndroidManifest.xml) 声明了 `MANAGE_EXTERNAL_STORAGE`。通常导入走系统文件选择器；文档工具的全盘扫描需要用户在系统设置授予所有文件访问权限。因此不能沿用旧 README 中“只有主动选中文件才能读取”的绝对描述。

不应推断的能力：正文全文索引、OCR、云同步、完整 HTML/CSS 浏览器渲染、完整 EPUB 标准支持、通用 PDF 排版编辑器。Markdown 编辑相关源文件存在，但当前导航图未接入对应页面；详见 [UI 与工具](08-ui-and-tools.md)。

## 工作区和 Git 工程

```text
D:/03_M1racle Reader/
├── reader/                      Git 仓库与 Android Gradle 工程
├── reader-env-local.ps1          本机环境配置
├── reader-build-local.ps1        本机构建、签名注入、条件式设备交付
├── reader-emulator-local.ps1     模拟器与隔离 QA 准备
├── Reader构建.cmd / Reader模拟调试.cmd  上述脚本的薄包装
├── .build-tools/                 本机工具链、SDK、缓存等
├── signing/                      本机签名材料；不是文档或源码输入
├── apk/ source/ git/ backups/     APK、源码和 Git 备份
├── output/                      日志、截图、实验与第三方研究产物
└── novel-audit-1.10.0/           独立 Python 工具，未接入 Android 构建
```

外围目录不是 Gradle 模块。位于 `output/` 的 Readium/其他阅读器源码不代表主应用依赖了它们。工作区根原有 `CODE_WIKI.md` 是先前文档，当前入口为 [仓库内 Code Wiki](../../CODE_WIKI.md)。

## 工程内部布局

```text
reader/
├── app/                     应用模块
│   ├── build.gradle.kts     Android 配置、依赖、门禁、Outbox
│   ├── version.properties   显式版本身份
│   ├── schemas/             Room v6 起的导出快照
│   └── src/
│       ├── main/            公共生产代码、资源、Manifest
│       ├── production/      debug/release/benchmark 共用替代实现
│       ├── qa/              QA 页面、测试 Activity、fixture provider
│       ├── debug/           视口测试 Activity；也被 QA 源集纳入
│       ├── test/            JVM 测试
│       ├── testQa/          仅依赖 QA 夹具/调参 UI 的 JVM 测试
│       ├── testRelease/     Release 专属标志契约测试
│       └── androidTest/     设备仪器测试
├── benchmark/               独立 com.android.test 性能模块
├── scripts/                 验证、安装、夹具、备份与性能脚本
├── testdata/                文档、相对资源等测试输入
├── docs/                    产品、设计、技术交接、阶段证据
├── .memory/                 当前对象与状态入口
└── .external_outbox/        构建产物；不提交 Git
```

`settings.gradle.kts` 只包含 `:app` 与 `:benchmark`。以下是 **app 内的包级模块**，不是独立 Gradle library。

| 包/目录 | 主要职责 | 主要入口 |
|---|---|---|
| 根包 | Application 依赖组装、Activity、刷新率策略 | `M1racleReaderApplication`、`MainActivity` |
| `data/local` | Room 实体、DAO、迁移 | `AppDatabase` |
| `data/repository` | 文档、阅读资产和搜索写入边界 | `DocumentRepository`、`ReadingRepository`、`SearchRepository` |
| `data/anchor` | 跨格式位置模型、编码和投影 | `DocumentAnchor`、`AnchorCodec`、`BookmarkTarget` |
| `data/settings` | DataStore、阅读设置和导入字体 | `ReaderSettingsRepository`、`ReaderFontRepository` |
| `identity` | 字节级身份、暂存和一次性历史数据转换 | `ContentIdentityResolver` |
| `share` | 外部 Intent、元数据和打开状态机 | `ExternalOpenCoordinator`、`ExternalDocumentImporter` |
| `packageimport` | Markdown ZIP 安全解包与入口选择 | `ZipPackageImporter` |
| `markdown`、`highlight`、`image`、`search` | Markdown 文本模型、语法着色、本地图和文内搜索 | `MarkdownParser`、`LocalImageLoader`、`DocumentSearch` |
| `epub` | EPUB 包、XHTML、内容锚点和恢复 | `EpubPackageImporter`、`EpubReadingAnchors` |
| `pdf`、`pdf/edit`、`pdf/export` | 渲染、几何、资源管理、正文编辑和导出 | `PdfPageRenderer`、`PdfBodyEditor`、`ChapterPackageExporter` |
| `documents` | 新建/合并/加密/转换、扫描、草稿存储 | `DocumentTools`、`DocumentDiscovery` |
| `ui` | Compose 页面、ViewModel、导航、控制器、主题与材质 | `M1racleReaderNavGraph` |

## 文档冲突的处理

产品范围读 [PRD](../PRD.md) 与 [全清单授权](../FULL_BACKLOG_20261009.md)；视觉要求读 [design.md](../../design.md)；当前交付和验证状态读 [CURRENT_STATUS](../CURRENT_STATUS.md)。阶段报告用于解释某次变更，不自动代表现状。

静态代码对旧概述的几项纠正：PDF 位图后端为平台 `PdfRenderer`；PDFBox 也用于文字、编辑和导出，不能仅写“元数据”；EPUB 已有 Slide/Continuous/Curl；Markdown 编辑源文件存在但当前无导航入口；所有文件访问权限已声明；源码依赖并非严格的五层单向图。
