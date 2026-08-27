# R24 Annotation Anchor Engine 报告

**日期：** 2026-08-26
**性质：** 跨格式标注定位基础。GlassMaterial/Home/Reader UI 零改动；无 AI；
无标注 UI（仅引擎与数据层）。
**验证：** `:app:testQaUnitTest`（JDK17）**1289 tests / 0 failures / 0 errors**
（1277 − 3 个旧签名测试被 anchor 版取代 + 新增 15，净 +12）。schema `11.json`。

---

## 1. DocumentAnchor 抽象模型（`data/anchor/DocumentAnchor.kt`）

```
sealed class DocumentAnchor
 ├─ PdfPage(page: Int, rect: NormalizedRect?)      // rect=null = 整页锚
 └─ MarkdownBlock(blockIndex: Int, charStart?, charEnd?)
NormalizedRect(x, y, width, height)                // 全分量 ∈ [0,1]，构造钳制
```

- **分辨率无关红线**：模型只携带归一化/索引信息，禁止像素或渲染坐标进入存储。
- `NormalizedRect.fromPixel(px,py,pw,ph,pageW,pageH)`：缩放不变（分子分母同倍数），
  构造后钳制到单位立方。
- Markdown 块索引与解析器块序一致；charStart/charEnd 为可选块内字符精化区间。

## 2. 实体调整（v11）

`highlights`/`notes` 新增 `anchor TEXT` 列（AnchorCodec 规范化原文）：

| 列 | 角色 |
|---|---|
| `anchor` | **恢复权威来源**（存在时精确还原锚点） |
| `locationKind` / `location` | 写时单点派生的**索引投影列**（查询/排序键），非第二事实源 |
| `anchorStart`/`anchorEnd` | v11 前历史行遗留字段，仅在合成回退中读取 |

MIGRATION_10_11 = 两条 `ADD COLUMN anchor TEXT`；历史行保持 NULL，
由解析器合成等价锚点（纯函数回退，非兼容层运行路径）。

## 3. AnchorResolver 接口（`data/anchor/AnnotationAnchorResolver.kt`）

```kotlin
interface AnnotationAnchorResolver {
    fun project(anchor): AnchorProjection(locationKind, location, encoded)  // 保存
    fun resolve(locationKind, location, anchorRaw, legacyStart?, legacyEnd?): DocumentAnchor?  // 恢复
}
PdfPageAnchorResolver        // PAGE_INDEX ↔ PdfPage
MarkdownBlockAnchorResolver  // SCROLL_POSITION ↔ MarkdownBlock
AnnotationAnchorResolvers    // 注册表：kind→resolver / anchor→resolver
```

- **保存**：仓库 `addHighlight/addNote(documentId, anchor, …)` 单点投影后落库；
- **恢复**：`resolveHighlightAnchor(id)/resolveNoteAnchor(id)` → 抽象锚点；
  anchor 列优先解码，NULL 时按 kind/location(+旧区间) 合成；
- 解码永不抛异常：任何非法输入 → null（调用方按无锚处理）；
- 未来格式（EPUB 等）：新增锚点子类型 + resolver 实现 + kind 注册，纯增量。

编解码（`AnchorCodec`）：零依赖确定性文本格式 `pdf:<page>|<rect|- >` /
`md:<block>|<range|->`，Float 用最短往返表示——同锚点编码逐字节稳定。

## 4. 稳定性测试（要求④，新增 15）

| 文件 | 数量 | 覆盖 |
|---|---|---|
| `data/anchor/DocumentAnchorCodecTest.kt` | 5 | PDF/MD 往返一致 ×3 重复、12 类畸形输入安全拒绝、矩形钳制、**fromPixel 缩放不变性** |
| `data/anchor/AnchorResolverTest.kt` | 6 | **同一锚点 ×10 次投影/恢复完全一致**（双格式）、**PDF scale×3 归一化逐分量相等且经编解码仍相等**、**MD 块索引保存-恢复循环**、v10 历史行合成等价锚点、注册表映射 |
| `data/repository/AnnotationAnchorRepositoryTest.kt` | 3 | PDF 高亮/MD 批注的投影列落库 + 锚点恢复往返、legacy NULL 行合成 |
| `data/local/MigrationV10ToV11Test.kt` | 1 | ADD COLUMN、user_version=11、历史行 NULL 且数据保留 |

## 变更面

```
A data/anchor/{DocumentAnchor,AnchorCodec,AnnotationAnchorResolver}.kt
M data/local/{AnnotationEntities,AnnotationDaos}.kt        (+anchor 列 / listById·getById)
M data/local/AppDatabase.kt                                (v11 + MIGRATION_10_11)
M data/repository/ReadingRepository.kt                     (addHighlight/addNote 改 anchor 入参; resolve*Anchor 恢复 API)
A schemas/.../11.json
± 测试：+4 新增文件、−1 旧签名测试（被 anchor 版取代）、版本钉 10→11 有意识修订
```

## 未做项（明确声明）

- **device_pending**：无（本轮无可视变更）。
- 标注 UI、选区捕获、渲染层高亮绘制均不在本轮；引擎已为「PDF 页内归一化矩形
  高亮」与「MD 块内字符区间高亮」提供全部定位原语。
- Bookmark 路径未迁移到 anchor（书签定位粒度=整页/整块，现有列即完备；
  若未来书签需要精定位，可复用同一 anchor 列模式做增量演进）。
