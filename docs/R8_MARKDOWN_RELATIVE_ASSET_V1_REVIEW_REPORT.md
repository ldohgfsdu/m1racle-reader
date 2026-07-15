# R8 Markdown Relative Asset V1 审查报告

**Active object:** `R8_MARKDOWN_RELATIVE_ASSET_RESOLUTION_V1`
**Branch:** `master`
**Baseline:** `f34b57d0dc929a5bd7f1ee0d4fd8f5e62450c726`
**审查日期:** 2026-07-15
**Commit:** 未提交（本轮不 commit / 不 push）

---

## 1. 审查结论

**APPROVED_FOR_DEVICE_ACCEPTANCE**

独立代码审查确认生产调用链接入 `MarkdownAssetResolver`；审查中发现并完成 **R8 直接相关** 最小安全修复（路径解码、未知 scheme、协议相对 URI、file URI 编码）；相关 JVM / lint / Debug+QA 构建门禁通过；真机 fixture 与人工清单已就绪。
**不**宣布 `device_accepted`（需人工用 fixture 验收）。

---

## 2. 生产调用链证据

| 步骤 | 文件 | 类/符号 | 行号（审查时） |
|------|------|---------|----------------|
| 文档 URI 注入 | `app/.../ui/viewer/ViewerScreen.kt` | `MarkdownBlockView(documentUri = state.document.uri)` | ~634 |
| 图片块分发 | `app/.../ui/viewer/components/MarkdownBlockView.kt` | `MarkdownBlock.Image` → `ImageBlockView` | ~73–78 |
| UI 加载触发 | `app/.../ui/viewer/components/ImageBlockView.kt` | `LaunchedEffect(block.source, documentUri)` → `LocalImageLoader.load` | ~58–69 |
| 统一解析 | `app/.../image/LocalImageLoader.kt` | `MarkdownAssetResolver.fromDocumentUri` + `resolve` | ~47–57 |
| 解析实现 | `app/.../markdown/MarkdownAssetResolver.kt` | `object MarkdownAssetResolver` | 全文件 |
| 读图 | `LocalImageLoader` | `loadAbsolute` / `loadResolved` → `decodeFile` / `decodeUri` / `decodeAsset` | ~65–100, 178+ |

**核验结论：**

- Resolver **不仅是测试代码**：`LocalImageLoader.load` 为生产路径唯一入口。
- 每张经 `ImageBlockView` 的本地图：先 `fromDocumentUri` + `resolve`，再按 `Absolute` / `Resolved` / 失败 sealed 分支处理。
- 旧拼接路径：`ZipPackageImporter.resolvePackageImage` **仅留在 packageimport 与测试**；`main` 加载路径 **不再**直接调用。
- Compose：`remember` + `LaunchedEffect` 键为 `(block.source, documentUri)`；键变更取消前一次 effect（`CancellationException` 再抛出），避免沿用上一文档 base。
- Lazy 列表 item key 含 `state.document.id`（`ViewerScreen` ~627–628），文档切换会重建项。
- 无 Toast/Dialog；失败仅图片位占位。
- 流：`openInputStream(...).use { }` 关闭；解码在 `Dispatchers.IO`；`inSampleSize` 限制最大边 1600。

**残余说明（不阻塞真机验收准备）：**

- `documentType` 未从 UI 传入；生产靠 `filesDir` 下 packages/library/inbox 探测 + `findPackageRoot`，对当前 app-owned 路径足够。
- 非协作点（`BitmapFactory.decodeFile`）取消粒度有限；effect 取消后不会把结果写回已取消协程（`withContext` 协作取消）。
- 错误 Icon 已设 `contentDescription = primaryMsg`（审查修复）；无障碍依赖可见文案 + 该描述。

---

## 3. Base 类型可达性

| Base 类型 | 生产可达 | 创建位置 | 当前产品语义 |
|-----------|:--------:|----------|--------------|
| `AppOwnedDirectory` | **是** | `fromDocumentUri` → `classifyFileDocument`：root 在 `filesDir` 下 | 包/库/inbox 内相对图，根内成功 |
| `FileDirectory` | **是** | 同上：`file://` 但不在 app filesDir 下 | 权限允许范围内相对图 |
| `SafTreeDirectory` | **否** | **仅测试可构造**；`fromDocumentUri` 从不创建 | **模型已预留，但 V1 当前产品流程不可达**；**不得宣称已支持真实 SAF 文件夹相对资源** |
| `SingleContentDocument` | **是** | `fromDocumentUri`：`content://` | 相对 → `Inaccessible(SINGLE_FILE_GRANT)` |
| `Unknown` | **是** | 空 URI / 非 file·content scheme | 相对 → `Inaccessible(UNKNOWN_BASE)` |

---

## 4. 安全审查

### root containment
- `canonicalFile` + `ZipPackageImporter.isUnder(target, rootCanon)`（resolver ~252–259）。
- 越界 → `Invalid(OUTSIDE_ROOT)`；缺文件 → `Inaccessible(NOT_FOUND)`，不抛到主线程。

### traversal
- 支持受控 `../`（仍在 root 内）；`%2e%2e` 单次解码后走同一沙箱。
- `%252e%252e` 只解码一层，不构成 `..`（安全测试覆盖）。

### encoding
- **审查修复前：** `URLDecoder` 会把 `+` 变空格（form 语义，错误）。
- **修复后：** `percentDecodeOnce` 只解 `%XX`，**不**把 `+` 当空格；**单次** decode。
- query/fragment：相对路径先 strip `#`/`?` 再定位；仅 query/fragment → `Invalid`。
- `Resolved` 经 `File.toURI()` 编码，回读时再单次 decode，避免 `%` 文件名双重破坏。

### symlink
- 解析使用 `canonicalFile`，指向 root 外的 symlink → `OUTSIDE_ROOT`（`MarkdownAssetResolverSecurityTest.symlink_escape_rejected_when_supported`；环境不支持 symlink 时 assume skip）。

### scheme
- 允许绝对透传：http/https/data/file/content/android.resource（+ 内部 asset）。
- 大小写：scheme 比较用 lower；原文串保留在 `Absolute.uri`。
- `//host/path` → `Invalid(UNSUPPORTED_SCHEME)`（**审查修复**）。
- `javascript:` / `ftp://` / 未知 scheme → `Invalid(UNSUPPORTED_SCHEME)`，**不**进加载器（**审查修复**）。

### IO / thread / cancellation
- IO：`Dispatchers.IO`；流 `use` 关闭。
- 大图：bounds + `inSampleSize`，非整文件无界 `ByteArray` 读入。
- 失败：catch → `Error` 文案；无 Toast 风暴。
- 多图失败：各自占位，无 Dialog。

### 绝对 file:// 产品策略
- 绝对 `file://` **不**改写为相对，按现有策略尝试解码；**不**经文档 root 沙箱。与产品「绝对 URI 透传」一致；属已知能力边界（非 V1 相对资源漏洞）。

---

## 5. 修复记录

本审查轮对 R8 的最小修复：

| 项 | 说明 |
|----|------|
| `percentDecodeOnce` | 替换 `URLDecoder`，避免 `+`→空格 |
| 未知/危险 scheme | `Invalid(UNSUPPORTED_SCHEME)` |
| 协议相对 `//…` | 拒绝 |
| `fileToUriString` | `File.toURI()` 正确编码 `%`/空格等 |
| 失败占位 a11y | `Icon.contentDescription = primaryMsg` |
| 测试 | 新增 `MarkdownAssetResolverSecurityTest`（18）并强化矩阵 |

无：降断言、删测试、改 External Open 生命周期、PDF/H1/schema/tree picker。

---

## 6. 自动门禁

| 门禁 | 命令 | 结果 | 数量/摘要 |
|------|------|------|-----------|
| JVM 相关单测 | `./gradlew :app:testDebugUnitTest --tests 'com.markleaf.app.markdown.*' --tests 'com.markleaf.app.image.*' --tests 'com.markleaf.app.packageimport.*' --tests 'com.markleaf.app.share.*'` | **PASS** | **133** tests, 0 fail；~16s；报告 `app/build/reports/tests/testDebugUnitTest/` |
| 其中 resolver 矩阵 | `MarkdownAssetResolverTest` | **PASS** | 22 |
| 其中安全矩阵 | `MarkdownAssetResolverSecurityTest` | **PASS** | 18 |
| 桥接 | `RelativeImageResolveTest` | **PASS** | 4 |
| 包图集成 | `PackageImageIntegrationTest` | **PASS** | 3 |
| share 回归 | ExternalOpen* 等 | **PASS** | 含在 133 内 |
| lint | `./gradlew :app:lintDebug` | **PASS** | 0 errors, 42 warnings（含既有 UnusedResources）；~74s；`app/build/reports/lint-results-debug.html` |
| Debug APK | `./gradlew :app:assembleDebug` | **PASS** | `app/build/outputs/apk/debug/app-debug.apk` (~75MB) |
| QA APK | `./gradlew :app:assembleQa` | **PASS** | `app/build/outputs/apk/qa/app-qa.apk` (~74MB)；与 debug 合计 ~20s |
| QA AndroidTest 组装 | `./gradlew :app:assembleQaAndroidTest` | **PASS** | `app/build/outputs/apk/androidTest/qa/app-qa-androidTest.apk`；~10s |
| AGP connectedQaAndroidTest | `connectedQaAndroidTest` + MD table class | **FAIL（基础设施）** | AGP ADB Bridge 无法创建（SDK adb 路径/版本）；非 R8 断言失败 |
| 设备 am instrument（MD 表） | `adb install` QA+test 成功；`am instrument …MarkdownResponsiveTableInstrumentedTest` | **未完成** | 进程挂起 >20min，日志无结果；已 force-stop；**非 R8 相对图专用用例** |

**未覆盖（明确）：**

- 全量 `testDebugUnitTest` 全库未重跑（定向相关 133 全过）。
- 全量 QA AndroidTest 套件（含 PDF/H1）未跑。
- **无** R8 相对图片专用 instrumented 测试；相对路径安全由 JVM 矩阵覆盖。
- 真机人工场景 A–D **待人工**（见 §7）。

---

## 7. 真机 fixture

根目录：`testdata/r8_relative_asset_v1/`
清单：`testdata/r8_relative_asset_v1/HUMAN_ACCEPTANCE_CHECKLIST.md`
建议包：`app-qa.apk`（已构建）。

| Fixture | 路径 | 内容摘要 | 预期 |
|---------|------|----------|------|
| **A** 应用自有 | `fixture_A_app_owned/` + `fixture_A_app_owned.zip` | README、chapter、same/child/parent、空格/中文、missing、manifest | 根内相对成功；missing 占位；https 网络禁用 |
| **B** file 树 | `fixture_B_file_tree/` | doc 同/子目录；sibling/outside 越界；伪 png / txt | same/child 成功；越界与非图失败 |
| **C** 单文件 External Open | `fixture_C_external_open_single/` | `only_markdown.md` + 旁路 `neighbor.png` | **仅选 md**；正文正常；相对图「无法访问相对图片」（单文件/未导入邻文件） |
| **D** 绝对 | `fixture_D_absolute/absolute.md` | http/https/data/file 绝对 | 不相对改写；http(s) 禁用；正文在 |

无隐私数据；均为合成小 PNG / 文本。

---

## 8. 已知限制

1. **External Open / SAF 单文件授权无法访问未授权相邻资源**（受控失败，不伪造 sibling URI）。
2. **`SafTreeDirectory` 无生产入口**：模型预留，V1 产品流程不可达；**不得宣称当前可用真实 SAF 文件夹相对资源**。
3. **文件夹授权 / 工作区模式不在 V1**（无 `ACTION_OPEN_DOCUMENT_TREE`）。
4. 绝对 `file://` / `content://` 按绝对策略处理，不经文档 root 沙箱。
5. `data:` 当前不解码展示（受控失败），与「不相对改写」一致。

---

## 9. 工作区边界

| 项 | 状态 |
|----|------|
| H1.5A | **未触碰** 源码 |
| PDF 手势 / Tile | **未触碰** |
| External Open 生命周期 | **未修改** coordinator/session（share 测试仅回归跑） |
| remote / push / commit | **未执行** |
| 无关未提交文件 | **未整理**（dist、R7 文档、gradle.properties 等仍在工作区，非本审查范围） |

**本审查有意变更：** resolver/loader/ImageBlockView、R8 测试、active_object/CURRENT_STATE、实施/审查报告、`testdata/r8_relative_asset_v1/`。

---

## 最终结论

**APPROVED_FOR_DEVICE_ACCEPTANCE**

停止：不 commit、不 push、不宣布 device_accepted。
下一步：人工按 `testdata/r8_relative_asset_v1/HUMAN_ACCEPTANCE_CHECKLIST.md` 在 QA 包上验收。
