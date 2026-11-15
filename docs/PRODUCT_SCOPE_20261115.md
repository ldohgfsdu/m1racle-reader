# M1racle Reader 产品范围与职责边界（2026-11-15 定稿）

定位：**一款专注本地 PDF 阅读的 Android 应用。轻量、稳定、流畅，以阅读体验为核心。**

本文件是范围决策的唯一事实源。功能取舍以它为准，代码重构的批次顺序也以它为准。

## 1. 保留范围

| 模块 | 功能 | 优先级 |
|---|---|---|
| 文件管理 | 导入 PDF、文库、最近阅读、重命名、移除 | P0 |
| PDF 阅读 | 连续滚动、左右翻页、双指缩放、平移 | P0 |
| 阅读进度 | 自动保存位置、退出恢复、重新打开不跳页 | P0 |
| 阅读导航 | 目录、缩略图、页码跳转 | P0 |
| 阅读交互 | 文字选择、复制、搜索 | P1 |
| 阅读资产 | 书签、高亮、批注 | P1 |
| 文件工具 | 拆分 PDF、导出章节、导出完整文件 | P2 |
| 个性化 | 深浅色、阅读显示设置 | P2 |

P0 表示必须可靠，不表示尚未实现。

扫描件无文本层时，选择与搜索必须明确提示能力限制，不能假装支持。

## 2. 退出范围

| 功能 | 处置 |
|---|---|
| Markdown 阅读与编辑 | 退出（UI 已下线，解析器与数据侧待清） |
| EPUB 阅读 | 退出 |
| PDF 正文编辑 | 冻结 |
| OCR / AI / 云同步 / 账号 | 不做 |
| 新增复杂玻璃动画 | 不新增 |

退出 ≠ 直接删源码。必须先确认依赖、历史数据与替代路径，再安全退出。

## 3. 现状测量（2026-11-15，批 A 之后）

代码量 187 文件 / 39,498 行。

| 边界 | 文件 | 行数 | 占比 |
|---|---|---|---|
| Reader UI | 24 | 13,199 | 33.4% |
| App Shell | 55 | 8,033 | 20.3% |
| Library | 31 | 5,717 | 14.5% |
| EPUB（待退出） | 20 | 3,481 | 8.8% |
| PDF Engine | 16 | 3,262 | 8.3% |
| Markdown（待退出） | 15 | 2,768 | 7.0% |
| Reading Assets | 20 | 2,417 | 6.1% |
| PDF Tools | 6 | 621 | 1.6% |

## 4. 依赖倒置问题（按严重度）

按 import 统计的跨边界依赖：

| 依赖 | 次数 | 判断 |
|---|---|---|
| Reader UI → PDF Engine | 175 | 正常：UI 驱动引擎 |
| Reader UI → App Shell | 95 | 偏高：阅读器反向依赖外壳组件 |
| App Shell → Library | 50 | 正常 |
| Reading Assets → App Shell | 13（原报 29，含误归类） | 见 `docs/S3_DEPENDENCY_DEBT.md`：全部在 `ui/annotation` 界面文件，`data/anchor` 对 UI 的 import 为 0 |
| EPUB → App Shell | 27 | 随退出消失 |
| EPUB → Library | 19 | 随退出消失 |
| Reader UI → Library | 15 | 偏高 |
| PDF Engine → Library | 9 | 见 `docs/S3_DEPENDENCY_DEBT.md`：8 处在 `pdf/export`（归 PDF Tools），引擎目录只剩 `pdf/PrintPageAnchors.kt:4` |
| Library → Markdown | 10 | 随 Markdown 退出消失 |

上面 29 和 9 的“违规”结论已被逐行复核推翻，详见 `docs/S3_DEPENDENCY_DEBT.md`。
修正后：Reading Assets 对 App Shell 的 13 处全部是标注界面在用主题和组件，数据层为零；
PDF Engine 对 Library 的 9 处里 8 处属于导出工具。S3 先重划归属，再决定要不要动代码，不为消掉 import 加抽象层。
高亮颜色不把 Compose `Color` 下沉到 Model，Model 只保存颜色标识，色值留在 UI。

## 5. 批次顺序

范围先定，再谈拆分。顺序固定如下：

**S1 退出范围**（对应批 B）
- EPUB 阅读下线：`ui/epub`、`epub`、`pagecurl` 依赖、`Routes.EPUB_VIEWER`
- Markdown 剩余解析器与数据侧分支下线：`markdown/`、`search/Markdown*`、`DocumentRepository` 的 Markdown 分支、`DocumentType.MARKDOWN*` 的导入入口（枚举与历史行保留）
- 完成后重新测量边界占比

**S2 P0 流程缺陷修复**（优先级高于任何拆分）
- 滑动跟手 / 异常跳页
- 缩放松手前界面状态不反馈
- 大文件打开与内存峰值
- 退出恢复不跳页
- 书签/标注可靠保存

**S3 边界解耦**（输入为 `docs/S3_DEPENDENCY_DEBT.md`，先重划归属再改代码）
- 标注界面从 Reading Assets 改归 Reader UI；仅 3 处高亮色值改为由 Model 提供颜色标识、UI 映射色值
- 导出代码从 PDF Engine 改归 PDF Tools；`ChapterPackageModels` 不再直接持有 DAO 实体
- PDF Tools 与阅读 UI 解耦：工具不得持有阅读状态

**S4 物理模块化（可选）**
只有 S3 完成后，才评估哪些边界值得拆成 Gradle 模块。不预先拆。

## 6. 第一阶段验收标准

不以"实现了多少功能"验收，以以下六条：

1. 大型扫描 PDF 可靠打开，内存受控（给出峰值数字）
2. 滑动真正跟手，无异常跳页
3. 缩放与平移自然，松手前界面状态正确
4. 阅读位置、书签、标注可靠保存
5. 升级/重构不丢原有 PDF 数据
6. 能自动构建、测试、发布 APK 供真机验收

当前状态：第 6 条部分成立（CI 可跑，本机 Release 构建受内存限制需跳过 lint）；第 1–5 条均待验证，其中 2、3 已有真机缺陷报告。
