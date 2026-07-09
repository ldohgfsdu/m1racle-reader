# 📄 墨阅 MarkLeaf｜Android Markdown 阅读器 PRD

## 0. 产品定位

### 一句话定义

一款面向 Android 的轻量、本地优先、高颜值 Markdown 阅读器，用来快速导入、管理和阅读 `.md / .txt` 文档。

### 产品关键词

**本地优先、启动快、阅读舒服、排版漂亮、无账号、无广告、无联网依赖。**

### 核心目标

用户打开 App 后，只需要完成三件事：

1. 导入一个 Markdown 文件；
2. 在主页看到最近阅读记录；
3. 进入阅读页，获得稳定、清爽、可跳转目录的阅读体验。

### 不做什么

MVP 阶段明确不做：

- 不做 Markdown 编辑器；
- 不做云同步；
- 不做账号系统；
- 不做社区、分享、评论；
- 不做复杂文件夹管理；
- 不做插件系统；
- 不做 AI 总结；
- 不做富文本笔记软件。

本项目第一阶段只解决一个问题：**让用户在 Android 手机上优雅地阅读本地 Markdown 文档。**

---

# 1. 项目概述与目标

## 1.1 背景痛点

当前手机端 Markdown 阅读体验常见问题：

- 大文件打开慢，长文滑动卡顿；
- 排版粗糙，标题、代码块、引用块层级不清晰；
- 很多工具偏“编辑器”，阅读体验不够沉浸；
- 部分工具强依赖账号、云同步或联网，用户缺乏安全感；
- Android 端缺少一个像 Notion 一样干净、但像 Obsidian 一样本地可信的 Markdown 阅读器。

## 1.2 目标用户

### 核心用户

- 程序员：阅读技术文档、README、笔记、API 说明；
- 学习型用户：阅读课程笔记、复习资料、长文档；
- Obsidian / Typora / Logseq 用户：希望在手机端快速查看本地 Markdown；
- 独立开发者 / 写作者：随时查看项目文档、草稿、PRD、知识库内容。

### 用户特征

- 对纯净体验敏感；
- 讨厌广告、账号和强制云同步；
- 能接受基础文件选择器导入；
- 更在意“读得舒服”和“稳定可靠”，而不是功能堆叠。

---

# 2. 产品原则

## 2.1 MVP 原则

MVP 不追求“大而全”，只追求：

- 打得开；
- 读得顺；
- 排版美；
- 能回到上次阅读位置；
- 能通过目录快速跳转；
- 完全本地、安全可信。

## 2.2 体验原则

### 阅读优先

阅读页是核心，不要让按钮、卡片、菜单抢内容的注意力。

### 本地优先

MVP 不申请网络权限，不上传文件，不读取用户无关目录。

### 少即是多

主页只做最近阅读，不做复杂文件管理器。

### 长文友好

对 10 万字以上 Markdown 文档，至少保证首屏快速出现，后续内容渐进解析或分块渲染。

---

# 3. 功能范围 Feature Scope

## 3.1 阶段一：MVP 核心功能

| 模块 | 功能点 | 优先级 | 描述 |
|---|---|---:|---|
| 文件导入 | 系统文件选择器导入 | P0 | 支持选择 `.md`、`.markdown`、`.txt` 文件 |
| 文件记录 | 最近阅读列表 | P0 | 主页展示最近打开的文档 |
| 文件记录 | 删除单条历史 | P0 | 删除最近阅读记录，不删除源文件 |
| 阅读页 | Markdown 基础渲染 | P0 | 支持标题、段落、加粗、斜体、列表、引用、分割线、链接、代码块 |
| 阅读页 | 代码块展示 | P0 | 代码块使用等宽字体、独立背景、可横向滚动 |
| 阅读页 | 基础代码高亮 | P1 | 识别常见语言：Kotlin、Java、Python、JavaScript、Markdown、JSON、XML、Shell |
| 阅读页 | 目录大纲 TOC | P0 | 自动提取 H1-H3 标题，支持点击跳转 |
| 阅读页 | 阅读进度保存 | P0 | 保存每个文件的最后阅读位置 |
| 主题 | 深色 / 浅色模式 | P0 | 默认跟随系统 |
| 主题 | Android 12+ Dynamic Color | P1 | 支持跟随系统壁纸取色 |
| 隐私 | 无网络权限 | P0 | MVP 不申请 INTERNET 权限 |
| 性能 | 长文加载优化 | P0 | 大文件首屏尽快显示，避免一次性阻塞主线程 |

---

## 3.2 阶段二：后续迭代功能

| 模块 | 功能点 | 描述 |
|---|---|---|
| 搜索 | 当前文档内搜索 | 支持关键词搜索、上一个 / 下一个匹配 |
| 搜索 | 全文搜索 | 对导入过的文档建立本地索引 |
| 文件管理 | 文件夹导入 | 支持导入整个目录并保持层级 |
| 编辑 | 简单编辑模式 | 支持 Markdown 原文编辑 |
| 编辑 | 快捷工具栏 | 加粗、标题、列表、引用、代码块快捷插入 |
| 同步 | WebDAV | 支持坚果云等 WebDAV 服务 |
| 同步 | GitHub 仓库读取 | 支持读取指定仓库文档 |
| 阅读增强 | 字体大小调节 | 用户可调整字号、行高、段落间距 |
| 阅读增强 | 多主题 | 提供纸张、墨绿、夜间、极客等主题 |
| 导出 | 分享为图片 / PDF | 将当前文档导出为长图或 PDF |

---

# 4. 页面结构与交互流程

## 4.1 页面总览

MVP 只需要两个核心页面：

1. 主页：最近阅读列表；
2. 阅读页：Markdown 内容阅读 + 目录大纲。

可选增加一个轻量设置页，但不作为第一优先级。

---

## 4.2 Page 1：主页 Home

### 页面目标

让用户快速打开最近读过的文档，或者导入新的 Markdown 文件。

### 页面结构

#### 顶部区域

- App 名称：墨阅 MarkLeaf；
- 副标题：本地 Markdown 阅读器；
- 右上角可选设置入口；
- 右下角 FAB：导入文件。

#### 中部区域：最近阅读

有历史记录时展示列表：

每个文档卡片包含：

- 文件名；
- 文件路径简化显示；
- 上次阅读时间；
- 文件大小；
- 阅读进度；
- 更多按钮：删除记录。

无历史记录时展示空状态：

- 空状态文案：  
  “还没有导入文档”
- 辅助文案：  
  “选择一个 Markdown 文件，开始本地阅读。”
- 主按钮：  
  “导入文件”

### 主页交互

| 用户操作 | 系统反馈 |
|---|---|
| 点击 FAB | 打开系统文件选择器 |
| 选择文件 | 导入记录并进入阅读页 |
| 点击最近阅读卡片 | 打开对应文件并恢复阅读位置 |
| 长按卡片 | 进入选择态或弹出操作菜单 |
| 点击删除记录 | 只删除历史记录，不删除本地文件 |
| 文件不存在 | 提示“文件可能已被移动或删除”，允许移除记录 |

---

## 4.3 Page 2：阅读页 Viewer

### 页面目标

提供沉浸、稳定、排版舒服的 Markdown 阅读体验。

### 页面结构

#### 顶部栏

- 返回按钮；
- 当前文件名；
- 目录按钮；
- 更多按钮。

顶部栏默认显示，向下阅读时可轻微隐藏或压缩，向上滑动时恢复。

#### 主体区域

Markdown 渲染内容：

- 标题有明确层级；
- 段落行距舒适；
- 列表缩进清晰；
- 引用块有左侧竖线；
- 代码块使用等宽字体和独立背景；
- 链接可点击，但不主动联网预览；
- 长文滑动不卡顿。

#### 目录抽屉 TOC

从左侧或右侧弹出均可，建议右侧抽屉，避免和 Android 返回手势冲突。

目录内容：

- 自动提取 H1、H2、H3；
- H2 / H3 使用缩进表达层级；
- 当前阅读位置对应标题高亮；
- 点击目录项后跳转到对应标题位置；
- 点击抽屉外区域关闭。

### 阅读页交互

| 用户操作 | 系统反馈 |
|---|---|
| 点击返回 | 回到主页，并保存阅读位置 |
| 点击目录按钮 | 打开目录抽屉 |
| 点击目录项 | 跳转到对应标题 |
| 滑动正文 | 正常滚动，自动更新阅读进度 |
| 退出页面 | 保存最后阅读位置 |
| 点击链接 | 弹出确认后用系统浏览器打开 |
| 点击代码块 | 可选：展开横向滚动或复制代码 |

---

# 5. 视觉风格定义

## 5.1 视觉方向

整体风格采用：

**Notion 式极简排版 + Obsidian 式本地文档气质 + Android Material 3 组件体系。**

不要做成重型知识库，也不要做成花哨阅读 App。

目标感觉：

- 安静；
- 干净；
- 专注；
- 有纸张感；
- 有高级文档感；
- 适合长时间阅读。

## 5.2 色彩

### 浅色模式

- 背景：暖白 / 米白；
- 正文：接近黑色但不死黑；
- 次级文字：灰褐色；
- 强调色：墨绿、陶土色或跟随 Dynamic Color；
- 卡片：轻微色差，不要重描边。

### 深色模式

- 背景：深灰黑，不使用纯黑；
- 正文：柔和灰白；
- 代码块：比背景略亮；
- 分割线：低对比；
- 强调色：降低饱和度，避免刺眼。

## 5.3 字体与排版

### 正文

- 中文正文使用系统默认字体；
- 英文和代码使用等宽字体；
- 正文字号默认 16sp；
- 行高建议 1.55～1.7；
- 段落间距明显大于普通换行；
- 页面左右 padding 建议 20dp～24dp。

### 标题

- H1：大标题，强层级；
- H2：章节标题；
- H3：小节标题；
- H4-H6：弱化处理，不要造成过多视觉噪音。

### 代码块

- 使用独立背景；
- 圆角 10dp～14dp；
- 内边距 12dp～16dp；
- 支持横向滚动；
- 字号 13sp～14sp；
- 行距略紧凑。

---

# 6. 数据结构设计

## 6.1 最近阅读记录 DocumentEntity

用于 Room 本地数据库。

```kotlin
@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val uri: String,
    val mimeType: String?,
    val fileSize: Long?,
    val lastOpenedAt: Long,
    val createdAt: Long,
    val lastScrollIndex: Int,
    val lastScrollOffset: Int,
    val readingProgress: Float,
    val title: String?,
    val headingCount: Int
)
```

### 字段说明

| 字段 | 说明 |
|---|---|
| id | 文件 URI 或 URI hash 生成的稳定 ID |
| displayName | 展示文件名 |
| uri | Android SAF 文件 URI |
| mimeType | 文件类型 |
| fileSize | 文件大小 |
| lastOpenedAt | 最近打开时间 |
| createdAt | 首次导入时间 |
| lastScrollIndex | LazyColumn 最后阅读位置 index |
| lastScrollOffset | 最后阅读位置 offset |
| readingProgress | 阅读进度，0～1 |
| title | 从 Markdown H1 提取的标题 |
| headingCount | 标题数量，用于判断是否显示目录入口 |

---

## 6.2 文档解析模型 MarkdownBlock

Markdown 文件解析后转换为块级结构，方便长文分块渲染。

```kotlin
sealed class MarkdownBlock {
    data class Heading(
        val level: Int,
        val text: String,
        val anchor: String
    ) : MarkdownBlock()

    data class Paragraph(
        val text: String
    ) : MarkdownBlock()

    data class Quote(
        val text: String
    ) : MarkdownBlock()

    data class CodeBlock(
        val language: String?,
        val code: String
    ) : MarkdownBlock()

    data class ListBlock(
        val ordered: Boolean,
        val items: List<String>
    ) : MarkdownBlock()

    object Divider : MarkdownBlock()
}
```

---

## 6.3 目录模型 TocItem

```kotlin
data class TocItem(
    val level: Int,
    val title: String,
    val anchor: String,
    val blockIndex: Int
)
```

---

# 7. 技术方案建议

## 7.1 技术栈

| 类型 | 技术 |
|---|---|
| 语言 | Kotlin |
| UI | Jetpack Compose |
| 架构 | MVVM |
| 本地数据库 | Room |
| 轻量配置 | DataStore |
| 文件选择 | Android Storage Access Framework |
| 异步任务 | Kotlin Coroutines / Flow |
| 主题 | Material 3 |
| 导航 | Navigation Compose |

## 7.2 渲染策略

MVP 建议不要直接把整篇 Markdown 一次性塞进一个 WebView 或 TextView。

推荐策略：

1. 读取文件内容；
2. 后台线程解析 Markdown；
3. 转换成块级 `MarkdownBlock`；
4. 使用 Compose `LazyColumn` 分块渲染；
5. TOC 根据 Heading block 自动生成；
6. 保存 `LazyListState` 作为阅读位置。

这样更容易实现：

- 长文性能优化；
- 目录跳转；
- 阅读进度保存；
- 块级样式定制；
- 后续搜索和局部刷新。

---

# 8. 非功能性需求

## 8.1 性能要求

| 场景 | 要求 |
|---|---|
| App 冷启动 | 尽量控制在 1 秒左右进入主页 |
| 普通文档打开 | 1 秒内展示内容 |
| 超长文档打开 | 优先展示首屏，解析过程不可阻塞 UI |
| 滑动体验 | 无明显掉帧 |
| 数据库查询 | 最近阅读列表秒开 |
| 文件读取 | 不在主线程读取大文件 |

## 8.2 隐私要求

MVP 阶段：

- 不申请网络权限；
- 不上传任何文件；
- 不接入统计 SDK；
- 不接入广告 SDK；
- 不读取用户未主动选择的文件；
- 删除历史记录时不删除本地原文件。

## 8.3 兼容性要求

- 支持 Android 12 及以上；
- 适配深色模式；
- 适配横竖屏基础布局；
- 适配系统字体大小变化；
- 适配刘海屏、安全区域、手势导航；
- Material 3 Dynamic Color 作为增强项，不影响基础主题。

---

# 9. MVP 验收标准

## 9.1 主页验收

- 首次打开显示空状态；
- 点击导入按钮可以打开系统文件选择器；
- 选择 `.md` 文件后进入阅读页；
- 返回主页后能看到最近阅读记录；
- 最近阅读记录包含文件名、时间、大小、进度；
- 可以删除单条历史；
- 删除历史不会删除真实文件。

## 9.2 阅读页验收

- 可以展示 Markdown 标题、段落、列表、引用、代码块；
- H1-H3 可以生成目录；
- 点击目录项可以跳转；
- 退出阅读页后再次打开能恢复阅读位置；
- 深色 / 浅色主题可正常切换；
- 大文件打开时 App 不崩溃、不长时间白屏；
- 文件被移动或删除时有明确错误提示。

## 9.3 隐私验收

- AndroidManifest 不包含 `INTERNET` 权限；
- 不接入第三方统计；
- 不上传用户文件；
- 用户只授权自己选择的文件。

---

# 10. 推荐开发顺序

## Step 1：项目骨架

- 创建 Kotlin + Jetpack Compose 项目；
- 配置 Material 3；
- 配置 Navigation Compose；
- 建立基础主题系统。

## Step 2：主页

- 实现空状态；
- 实现最近阅读列表 UI；
- 实现 FAB 导入入口；
- 建立 Room 数据表。

## Step 3：文件导入

- 接入系统文件选择器；
- 获取文件 URI；
- 读取文件名、大小、类型；
- 保存到 Room；
- 跳转阅读页。

## Step 4：Markdown 解析

- 读取文件内容；
- 后台解析 Markdown；
- 转换为 `MarkdownBlock`；
- 提取 TOC。

## Step 5：阅读页渲染

- 使用 LazyColumn 渲染块级内容；
- 实现标题、段落、引用、列表、代码块样式；
- 实现阅读位置保存。

## Step 6：目录跳转

- 实现目录抽屉；
- 点击 TOC 跳转到对应 block；
- 当前阅读位置同步高亮。

## Step 7：主题与 polish

- 深色 / 浅色模式；
- Dynamic Color；
- 细节排版；
- 错误态；
- 空态；
- 性能优化。

---

# 11. AI Agent 开发约束

后续让 AI Agent 写代码时，必须遵守以下规则：

1. 每次只实现一个明确任务；
2. 不允许擅自扩展 MVP 范围；
3. 不允许加入账号、云同步、广告、统计 SDK；
4. 不允许申请网络权限；
5. 每次修改后说明改了哪些文件；
6. UI 优先使用 Jetpack Compose；
7. 数据持久化优先使用 Room；
8. 文件访问使用 Android SAF；
9. 大文件读取和解析不能阻塞主线程；
10. 如果实现方案和 PRD 冲突，必须先说明冲突点。

---

# 12. 可直接喂给 AI Agent 的启动提示词

你现在是我的 Android 技术合伙人。下面是我的项目 PRD：我正在做一款 Android Markdown 阅读器，项目名暂定为“墨阅 MarkLeaf”。

产品定位：本地优先、轻量、高颜值、无账号、无广告、无联网依赖。MVP 只做本地 Markdown 阅读，不做编辑、不做同步、不做 AI 总结、不做复杂文件管理。

技术栈默认使用 Kotlin、Jetpack Compose、Material 3、Room、DataStore、Navigation Compose、Android Storage Access Framework、Coroutines / Flow。

现在请严格按照 MVP 范围开始实现。第一步只做：

1. 创建主页 Home；
2. 主页包含 App 标题、空状态、最近阅读列表区域、导入文件 FAB；
3. 设计 Room 数据结构 `DocumentEntity`；
4. 设计 DAO 和 Repository；
5. 不需要真正实现 Markdown 渲染；
6. 不要申请网络权限；
7. 不要加入任何超出 MVP 的功能。

请先输出你的实现计划，然后给出具体代码。每次代码修改必须说明文件路径和作用。
