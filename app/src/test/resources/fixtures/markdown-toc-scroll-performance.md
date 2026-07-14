# Markdown TOC 与滚动性能基准

[第一节](#第一节) · [中间章节](#中间章节) · [重复标题二](#重复标题-2) · [最后章节](#最后章节)

## 第一节

这是用于锚点跳转和长文档滚动性能验证的固定内容。正文保持足够长度，以便观察普通段落进入和离开 viewport 时的连续性。

```kotlin
data class ReadingAnchor(val id: String, val blockIndex: Int)

fun resolveAnchor(id: String, anchors: Map<String, ReadingAnchor>): Int? {
    return anchors[id]?.blockIndex
}
```

| 项目 | 结果 |
|---|---|
| 心率 | 正常 |
| PR 间期 | 0.20 秒 |
| QRS 波群 | 未见明显增宽 |

## 临床记录

连续观察多个导联，记录节律、间期和波形变化，并与既往心电图进行比较。长段落用于模拟医学笔记中的密集正文。

> 阅读器应只组合当前 viewport 附近的 block，并且不应在纵向滚动过程中重新解析整篇 Markdown。

```text
frame 01: paragraph
frame 02: code block enters viewport
frame 03: table enters viewport
frame 04: table leaves viewport
frame 05: table re-enters viewport
```

## 重复标题

第一个同名标题用于验证基础 anchor。

| 项目 | 右束支阻滞 | 左束支阻滞 |
|---|---|---|
| V1 | rsR′、M 形、兔耳 | QS 或 rS |
| I、V6 | 宽钝 S 波 | 宽大、切迹 R 波 |
| 复核 | 结合 QRS 时限和胸前导联形态综合判断 | 滚动到最右侧时本列必须完整可见 |

### 深层标题

H3 也进入目录注册表，并保持稳定 blockIndex。

```kotlin
repeat(40) { index ->
    trace("MarkdownBlockCompose", index)
}
```

## 中间章节

中间跳转目标用于区分短距离动画与跨多屏直接定位。跨越多个屏幕时不应执行贯穿整篇文档的长动画。

1. parser 只在文件打开时执行。
2. block key 包含 documentId 与 stable blockId。
3. table solve 结果跨 viewport 回收继续复用。
4. raw scroll offset 不进入表格缓存键。

```json
{
  "frameDurationMs": 12.4,
  "isJank": false,
  "currentBlockType": "code",
  "visibleTableIds": ["table-8"],
  "tableSolveCount": 1
}
```

## 重复标题

第二个同名标题必须得到 `重复标题-2`，不能跳到第一个同名标题。

| 核心心电图表现 | 速记与判断 |
|---|---|
| 每个 P 波后都有 QRS；PR 间期固定延长，成人通常大于 0.20 秒，需要结合心率和临床症状综合判断。 | 只延长，不漏搏；长文本应自然换行，不出现省略号。 |
| 连续观察多个导联，记录节律、间期和波形变化，并与既往心电图进行比较。 | 第二列应获得主要阅读空间。 |

## 性能观察一

复杂 block 第一次进入 viewport 可以执行必要布局；相同配置再次进入时必须命中文档级缓存。

```kotlin
val cacheKey = listOf(documentId, tableId, viewportWidth, density, fontScale)
check("scrollOffset" !in cacheKey)
```

## 性能观察二

阅读进度只在离散百分比变化、滚动停止、页面离开或应用进入后台时保存，不按每个像素写入数据库。

> TopBar 可见性和当前目录高亮应通过离散状态观察，不得让全部 LazyColumn item 订阅 raw scroll offset。

## 性能观察三

表格横向滚动只改变自己的 ScrollState，不触发列宽重新求解，也不阻断外层阅读页面的纵向滚动。

| 缓存字段 | 是否需要 |
|---|---|
| documentId | 是 |
| stableTableId | 是 |
| viewportWidthPx | 是 |
| rawVerticalScrollOffset | 否 |

## 最后章节

这是最后一个目录跳转目标。跳转完成后标题应位于 TopBar 下方，不能被工具栏遮挡。

不存在的锚点应返回结构化 miss，不崩溃，也不改变当前阅读位置。
