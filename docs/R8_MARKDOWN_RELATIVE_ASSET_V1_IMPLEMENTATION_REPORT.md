# R8 Markdown Relative Asset V1 实施报告

**Active object:** `R8_MARKDOWN_RELATIVE_ASSET_RESOLUTION_V1`
**Baseline:** `f34b57d0dc929a5bd7f1ee0d4fd8f5e62450c726`
**Status:** `IMPLEMENTATION_READY_FOR_REVIEW`
**Date:** 2026-07-15
**Commit:** 未提交（等待人工审查）

---

## 范围

采用 **能力分级 + 安全降级**，不在缺少目录授权时读取任意 SAF 相邻文件。

| 能力 | 相对图片 |
|------|----------|
| `AppOwnedDirectory` | 同目录 / 子目录 / 上级（仍在资源根内） |
| `FileDirectory` | 同上（现有 file 权限范围内） |
| `SafTreeDirectory` | 仅当已有可验证 root 时；不伪造 sibling content URI |
| `SingleContentDocument` | 相对 → `Inaccessible`（受控失败） |
| `Unknown` | 相对 → `Inaccessible` |

绝对 URI 原样透传：`http` / `https` / `data` / `file` / `content` / `android.resource`（及内部 `asset`）。

本轮 **不做**：文件夹选择 / `ACTION_OPEN_DOCUMENT_TREE`、改 Markdown 原文、改 External Open session 生命周期、PDF / ThreePageStrip / Tile / H1.5A、搜索、大文件提示、Room schema、全量 release 打包、commit/push、清理无关工作区脏文件。

---

## 修改文件

### 本任务有意修改 / 新增

| 路径 | 说明 |
|------|------|
| `.memory/active_object.yaml` | 范围决定与 active object |
| `app/src/main/java/com/markleaf/app/markdown/MarkdownAssetResolver.kt` | **新增** 单一解析入口 + base/result 模型 |
| `app/src/main/java/com/markleaf/app/image/LocalImageLoader.kt` | 经 resolver 加载；保留 file 桥接 API |
| `app/src/main/java/com/markleaf/app/ui/viewer/components/ImageBlockView.kt` | 主/次失败文案分层展示 |
| `app/src/test/java/com/markleaf/app/markdown/MarkdownAssetResolverTest.kt` | **新增** 解析矩阵单测 |
| `app/src/test/java/com/markleaf/app/image/RelativeImageResolveTest.kt` | 桥接回归 + 包内 `../` |
| `docs/R8_MARKDOWN_RELATIVE_ASSET_V1_IMPLEMENTATION_REPORT.md` | 本报告 |
| `.memory/CURRENT_STATE.md` | 状态同步（若同步） |

### 工作区中存在但本任务未触碰的脏内容（不清理）

含 `docs/PDF_VIEWER_HANDOFF.md`、`docs/R7_H1_*`、`gradle.properties`、`dist/*`、若干未跟踪目录等。审查时请 **仅关注上表有意改动**。

---

## Resolver 数据模型

### `MarkdownDocumentBase`（显式能力，禁止一律退化为裸 path）

- `AppOwnedDirectory(documentUri, entryFile, rootDir)`
- `FileDirectory(documentUri, entryFile, rootDir)`
- `SafTreeDirectory(documentUri, treeUri, rootDir)`
- `SingleContentDocument(documentUri)`
- `Unknown`

URI 字段使用 **String**（非 `android.net.Uri`），保证 JVM 单测可跑（平台 `Uri` 未 mock）。

### `MarkdownAssetResult`

- `Absolute(uri: String)` — 不改写
- `Resolved(uri: String)` — 通常 `file://…`
- `Inaccessible(reason)`
- `Invalid(reason)`

### `MarkdownAssetFailure`

`EMPTY` / `UNSUPPORTED_SCHEME` / `NOT_A_FILE` / `NOT_AN_IMAGE` / `OUTSIDE_ROOT` / `NOT_FOUND` / `SINGLE_FILE_GRANT` / `UNKNOWN_BASE` / `INVALID_PATH`

### 路径规则

1. 先识别绝对 scheme → `Absolute`
2. 相对路径 **单次** URL decode
3. 剥离 query/fragment 再作为路径段
4. 规范化 `.` / `..`
5. 拼接后 `canonical` + `isUnder(root)` 沙箱
6. 拒绝空路径、目录目标、非图片扩展、越界
7. 失败返回 sealed result，不向 Compose 主线程抛解析异常

### 调用链

```
ImageBlockView
  → LocalImageLoader.load(context, source, documentUri)
      → MarkdownAssetResolver.fromDocumentUri(...)
      → MarkdownAssetResolver.resolve(base, source)
      → Absolute / Resolved 解码 | Inaccessible/Invalid → Error(userMessage)
```

UI 文案：

- 主：`无法访问相对图片`
- 单文件授权次级：`当前仅授权了 Markdown 文件，未授权图片所在目录`
- 网络：`网络图片已禁用`（行为不回归）

---

## 各来源支持情况

| 来源 | 生产 `fromDocumentUri` 分类 | 相对资源 |
|------|----------------------------|----------|
| 应用私有 `file://`（packages/library/inbox） | `AppOwnedDirectory` | 根内成功 |
| 其他可读 `file://` | `FileDirectory` | 根内成功（权限允许） |
| 已验证 tree + 可访问 root（测试/未来） | `SafTreeDirectory` | 根内成功；**不**猜 sibling URI |
| 任意 `content://` 文档 | `SingleContentDocument` | 相对 → `Inaccessible(SINGLE_FILE_GRANT)` |
| 空 / 未知 scheme | `Unknown` | 相对 → `Inaccessible(UNKNOWN_BASE)` |

说明：V1 **不会**根据 displayName 或路径猜测拼 content sibling URI。生产路径暂不探测 persisted tree grant（避免假成功）；`SafTreeDirectory` 保留类型供测试与后续 workspace 模式接入。

---

## SAF 单文件授权降级行为

1. `content://` 文档 → `SingleContentDocument`
2. 相对引用（如 `images/a.png`）→ `Inaccessible(SINGLE_FILE_GRANT)`
3. Markdown 正文继续渲染；图片位稳定失败占位 + 主/次文案
4. 绝对 `content://` / `http(s)` 等 **不被改写**；网络仍禁用
5. **不**弹 Toast 风暴、**不**请求目录权限、**不**改 External Open session 生命周期

External Open 入库后通常为私有 `file://` 单文件副本：相对邻文件若未一并导入 → `NOT_FOUND` 受控失败（同样不崩溃）。

---

## 测试结果

命令：

```bash
./gradlew :app:testDebugUnitTest \
  --tests 'com.markleaf.app.markdown.MarkdownAssetResolverTest' \
  --tests 'com.markleaf.app.image.RelativeImageResolveTest' \
  --tests 'com.markleaf.app.packageimport.PackageImageIntegrationTest'

./gradlew :app:testDebugUnitTest \
  --tests 'com.markleaf.app.markdown.*' \
  --tests 'com.markleaf.app.image.*' \
  --tests 'com.markleaf.app.packageimport.*' \
  --tests 'com.markleaf.app.share.*'
```

| 套件 | 结果 |
|------|------|
| `MarkdownAssetResolverTest` | **PASS** |
| `RelativeImageResolveTest` | **PASS** |
| `PackageImageIntegrationTest` | **PASS** |
| markdown + image + packageimport + share | **PASS** |

覆盖（摘要）：

- 纯解析：`image.png` / `./` / 子目录 / `../` / 空格 / `%20` / 中文 / `.` `..` 规范化 / 越界 / 绝对 scheme 透传 / 非法 / 不存在 / 非图 / 目录目标
- 能力：AppOwned / FileDirectory / SafTree 成功；SingleContent / Unknown 受控失败；绝对 content 不改写
- 桥接：`LocalImageLoader.resolveRelativeImage` 成功与拒绝

未跑：全量 `testDebugUnitTest` 全库、release 打包、设备 UI（按 V1「最小相关门禁」）。

---

## 回归结果

| 项 | 结论 |
|----|------|
| 无图片 Markdown | 未改 parser/block 文本路径；图片分支独立 |
| 网络图片 | 仍 `网络图片已禁用` |
| 图片失败不影响正文 | Error 仅占位，无 throw 到列表 |
| External Open session | **未修改** coordinator / intake / session 代码 |
| 旋转/重组重复提示 | 无 Toast；`remember`/`LaunchedEffect` 仅按 source+documentUri 重载，无权限风暴 |
| 缺图不崩溃 | sealed + catch → Error |

---

## 未支持事项

**必须明确：**

1. **External Open / SAF 仅单文件授权时，不能可靠访问相邻相对资源**
   → 受控失败占位，不伪造 URI。
2. **文件夹授权 / 工作区模式未包含在 V1**
   → 无 `ACTION_OPEN_DOCUMENT_TREE`、无 persisted tree 生产探测、无「选择图片目录」按钮。

其它未做：data URI 解码展示、android.resource 专项美化、改原始 Markdown、schema、远程/push。

---

## 工作区边界核验

| 边界 | 状态 |
|------|------|
| H1.5A 手势代码 | **未改**（工作区若有 H1 文档脏文件，非本任务） |
| PDF 手势与 Tile | **未改** |
| remote | **未配置、未 push** |
| git commit | **未执行** |
| 既有无关未提交内容 | **未清理、未纳入本实现范围** |

有意 diff 仅限 Markdown 相对资源解析链路与 active object / 报告。

---

## 最终结论

**IMPLEMENTATION_READY_FOR_REVIEW**
