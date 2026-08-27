# M1racle Reader 滚动压力样例

用于真机 fling / LazyColumn 回收与代码块、表格、图片进入视口时的卡顿验收。

> 本地优先、无网络依赖、解析只做一次。

## 长段落区

### 段落组 1

本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。

这里是第 1 组长段落的收尾句，用于检查中文换行、行高与搜索命中。

### 段落组 2

本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。

这里是第 2 组长段落的收尾句，用于检查中文换行、行高与搜索命中。

### 段落组 3

本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。

这里是第 3 组长段落的收尾句，用于检查中文换行、行高与搜索命中。

### 段落组 4

本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。

这里是第 4 组长段落的收尾句，用于检查中文换行、行高与搜索命中。

### 段落组 5

本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。

这里是第 5 组长段落的收尾句，用于检查中文换行、行高与搜索命中。

### 段落组 6

本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。

这里是第 6 组长段落的收尾句，用于检查中文换行、行高与搜索命中。

### 段落组 7

本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。

这里是第 7 组长段落的收尾句，用于检查中文换行、行高与搜索命中。

### 段落组 8

本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。

这里是第 8 组长段落的收尾句，用于检查中文换行、行高与搜索命中。

### 段落组 9

本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。

这里是第 9 组长段落的收尾句，用于检查中文换行、行高与搜索命中。

### 段落组 10

本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。

这里是第 10 组长段落的收尾句，用于检查中文换行、行高与搜索命中。

### 段落组 11

本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。

这里是第 11 组长段落的收尾句，用于检查中文换行、行高与搜索命中。

### 段落组 12

本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。

这里是第 12 组长段落的收尾句，用于检查中文换行、行高与搜索命中。

### 段落组 13

本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。

这里是第 13 组长段落的收尾句，用于检查中文换行、行高与搜索命中。

### 段落组 14

本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。

这里是第 14 组长段落的收尾句，用于检查中文换行、行高与搜索命中。

### 段落组 15

本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。

这里是第 15 组长段落的收尾句，用于检查中文换行、行高与搜索命中。

## 代码块压力（20 个）

### 代码块 1 — kotlin

```kotlin
// block 1
fun sample1(x: Int): Int {
    val long = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
    return x * 1 + long.length
}
```

### 代码块 2 — java

```java
// block 2
public class Sample2 {
  public static int run(int x) {
    String longLine = "yyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyy";
    return x + 2 + longLine.length();
  }
}
```

### 代码块 3 — javascript

```javascript
// block 3
export function sample3(x) {
  const long = "zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz";
  return x * 3 + long.length;
}
```

### 代码块 4 — python

```python
# block 4
def sample_4(x):
    long = "pppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppp"
    return x * 4 + len(long)
```

### 代码块 5 — shell

```shell
# block 5
echo "sample 5"
export LONG="ssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssss"
printf '%s\n' "$LONG"
```

### 代码块 6 — json

```json
{
  "id": 6,
  "name": "block-6",
  "tags": ["scroll", "stress", "code"],
  "ok": true
}
```

### 代码块 7 — xml

```xml
<sample id="7">
  <title>block 7</title>
  <body>stress xml payload</body>
</sample>
```

### 代码块 8 — markdown

```markdown
# Inner heading 8

**bold** and *italic* sample for fence 8.

- a
- b
```

### 代码块 9 — plain

```plain
plain code block 9
line two
line three with long wwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwww
```

### 代码块 10 — kotlin

```kotlin
// block 10
fun sample10(x: Int): Int {
    val long = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
    return x * 10 + long.length
}
```

### 代码块 11 — kotlin

```kotlin
// block 11
fun sample11(x: Int): Int {
    val long = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
    return x * 11 + long.length
}
```

### 代码块 12 — java

```java
// block 12
public class Sample12 {
  public static int run(int x) {
    String longLine = "yyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyy";
    return x + 12 + longLine.length();
  }
}
```

### 代码块 13 — javascript

```javascript
// block 13
export function sample13(x) {
  const long = "zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz";
  return x * 13 + long.length;
}
```

### 代码块 14 — python

```python
# block 14
def sample_14(x):
    long = "pppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppp"
    return x * 14 + len(long)
```

### 代码块 15 — shell

```shell
# block 15
echo "sample 15"
export LONG="ssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssss"
printf '%s\n' "$LONG"
```

### 代码块 16 — json

```json
{
  "id": 16,
  "name": "block-16",
  "tags": ["scroll", "stress", "code"],
  "ok": true
}
```

### 代码块 17 — xml

```xml
<sample id="17">
  <title>block 17</title>
  <body>stress xml payload</body>
</sample>
```

### 代码块 18 — markdown

```markdown
# Inner heading 18

**bold** and *italic* sample for fence 18.

- a
- b
```

### 代码块 19 — plain

```plain
plain code block 19
line two
line three with long wwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwww
```

### 代码块 20 — kotlin

```kotlin
// block 20
fun sample20(x: Int): Int {
    val long = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
    return x * 20 + long.length
}
```

## 表格压力

### 表格 1

| 列A | 列B | 列C | 列D | 列E |
| --- | :---: | ---: | --- | --- |
| R1-1A 内容 | 居中 1 | 1 | **强调** | `cell` |
| R1-2A 内容 | 居中 2 | 2 | **强调** | `cell` |
| R1-3A 内容 | 居中 3 | 3 | **强调** | `cell` |
| R1-4A 内容 | 居中 4 | 4 | **强调** | `cell` |
| R1-5A 内容 | 居中 5 | 5 | **强调** | `cell` |
| R1-6A 内容 | 居中 6 | 6 | **强调** | `cell` |
| R1-7A 内容 | 居中 7 | 7 | **强调** | `cell` |

### 表格 2

| 列A | 列B | 列C | 列D | 列E |
| --- | :---: | ---: | --- | --- |
| R2-1A 内容 | 居中 1 | 2 | **强调** | `cell` |
| R2-2A 内容 | 居中 2 | 4 | **强调** | `cell` |
| R2-3A 内容 | 居中 3 | 6 | **强调** | `cell` |
| R2-4A 内容 | 居中 4 | 8 | **强调** | `cell` |
| R2-5A 内容 | 居中 5 | 10 | **强调** | `cell` |
| R2-6A 内容 | 居中 6 | 12 | **强调** | `cell` |
| R2-7A 内容 | 居中 7 | 14 | **强调** | `cell` |

### 表格 3

| 列A | 列B | 列C | 列D | 列E |
| --- | :---: | ---: | --- | --- |
| R3-1A 内容 | 居中 1 | 3 | **强调** | `cell` |
| R3-2A 内容 | 居中 2 | 6 | **强调** | `cell` |
| R3-3A 内容 | 居中 3 | 9 | **强调** | `cell` |
| R3-4A 内容 | 居中 4 | 12 | **强调** | `cell` |
| R3-5A 内容 | 居中 5 | 15 | **强调** | `cell` |
| R3-6A 内容 | 居中 6 | 18 | **强调** | `cell` |
| R3-7A 内容 | 居中 7 | 21 | **强调** | `cell` |

### 表格 4

| 列A | 列B | 列C | 列D | 列E |
| --- | :---: | ---: | --- | --- |
| R4-1A 内容 | 居中 1 | 4 | **强调** | `cell` |
| R4-2A 内容 | 居中 2 | 8 | **强调** | `cell` |
| R4-3A 内容 | 居中 3 | 12 | **强调** | `cell` |
| R4-4A 内容 | 居中 4 | 16 | **强调** | `cell` |
| R4-5A 内容 | 居中 5 | 20 | **强调** | `cell` |
| R4-6A 内容 | 居中 6 | 24 | **强调** | `cell` |
| R4-7A 内容 | 居中 7 | 28 | **强调** | `cell` |

### 表格 5

| 列A | 列B | 列C | 列D | 列E |
| --- | :---: | ---: | --- | --- |
| R5-1A 内容 | 居中 1 | 5 | **强调** | `cell` |
| R5-2A 内容 | 居中 2 | 10 | **强调** | `cell` |
| R5-3A 内容 | 居中 3 | 15 | **强调** | `cell` |
| R5-4A 内容 | 居中 4 | 20 | **强调** | `cell` |
| R5-5A 内容 | 居中 5 | 25 | **强调** | `cell` |
| R5-6A 内容 | 居中 6 | 30 | **强调** | `cell` |
| R5-7A 内容 | 居中 7 | 35 | **强调** | `cell` |

## 图片占位

### 图片 1

![压力样例图 1](asset://sample-image.png "stress-1")

本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。

### 图片 2

![压力样例图 2](asset://sample-image.png "stress-2")

本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。

### 图片 3

![压力样例图 3](asset://sample-image.png "stress-3")

本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。

### 图片 4

![压力样例图 4](asset://sample-image.png "stress-4")

本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。

### 图片 5

![压力样例图 5](asset://sample-image.png "stress-5")

本地 Markdown 阅读器在快速滚动时必须把文件读取、解析与高亮排除在滚动路径之外。每一个 block 都应是 LazyColumn 的独立 item，并提供稳定的 key 与 contentType，这样回收池才能按类型复用组合节点。TopBar 的显隐只应驱动 graphicsLayer.translationY，绝不能改 contentPadding 或导致正文整树重组。阅读进度应在滚动停止或整数百分比变化时再写入 Room。

## 列表与分割线

- 无序项 1: 用于混合 block 节奏
- 无序项 2: 用于混合 block 节奏
- 无序项 3: 用于混合 block 节奏
- 无序项 4: 用于混合 block 节奏
- 无序项 5: 用于混合 block 节奏
- 无序项 6: 用于混合 block 节奏
- 无序项 7: 用于混合 block 节奏

1. 有序项 1
2. 有序项 2
3. 有序项 3
4. 有序项 4
5. 有序项 5
6. 有序项 6
7. 有序项 7

---

## 结尾

若快速 fling 经过本节仍无明显卡顿，且 TopBar 显隐不带动正文跳动，则滚动性能验收通过。
