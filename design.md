# M1racle Reader 设计规范

本文件统一项目的界面、排版、阅读交互与材质要求。产品范围遵循 [PRD](docs/PRD.md)，工程与交付遵循 [AGENTS.md](AGENTS.md)，已验证现状查 [CURRENT_STATUS](docs/CURRENT_STATUS.md)；用户当前指令优先。设计要求、代码现状、历史验收是三个不同概念，不能互相替代。

整理日期：2026-10-03。检索仓库 Markdown 文档，审阅设计方案、交互约束、视觉修正、用户反馈与相关报告中的设计章节，并对主题、首页和阅读排版令牌做源码核对。测试夹具作为验收场景，不作为产品规范；构建日志、旧 APK 摘要和迁移细节不搬入设计正文。原始文档保留用于追溯，本文末尾按主题列出来源。

历史报告中的“最终”“冻结”“通过”仅对当时批次有效。后续用户反馈与有效修正优先；未实现的规划、未批准的实验和被拒绝的截图方案不得写成当前要求。本文不是本轮运行时验收报告。

2026-10-03 界面落实：四入口共用标题与水平对齐，文档列表明确类型和进度，分类无结果区别于空文库，设置按实际阅读入口与隐私信息分组；阅读工具栏避免文档名和同名章节重复。具体实现、截图证据与未验证项见 [界面优化报告](docs/UI_DESIGN_POLISH_20261003.md)，不因此视为本文全部要求已完成。

## 阅读导航

- [产品原则与页面职责](#产品原则与页面职责)
- [主题、排版与布局](#主题排版与布局)
- [首页、文库、书签与设置](#首页文库书签与设置)
- [Markdown 阅读](#markdown-阅读)
- [EPUB 阅读](#epub-阅读)
- [PDF 阅读与阅读控件](#pdf-阅读与阅读控件)
- [批注、正文编辑与保存](#批注正文编辑与保存)
- [搜索、导入与外部打开](#搜索导入与外部打开)
- [Liquid Glass 官方资料](#liquid-glass审阅范围)
- [历史冲突裁决](#历史冲突裁决)
- [设计验收与待完成项](#设计验收与待完成项)
- [设计资料来源索引](#设计资料来源索引)

## 产品原则与页面职责

阅读优先、内容优先、离线优先。界面安静，主要空间留给文档；材质与运动帮助识别导航和操作，不抢正文注意力。导入、继续阅读、回到最近位置和查找应直接可达；没有数据时给出真实的空态和下一步操作，不制造示例进度、占位功能或未来导航。

当前范围为本地 Markdown / 文本、PDF、有限 EPUB 阅读，以及有限 PDF 正文编辑。账号、广告、云同步、在线资源抓取、通用 Markdown 编辑器、DRM 解锁、OCR 和全库正文全文检索不因本文而进入范围。规划文档中的扩展能力须另立任务。

| 页面 / 流程 | 核心职责 | 主要约束 |
|---|---|---|
| 首页 | 导入、继续阅读、最近文件 | 固定顶部工具栏；继续阅读与列表属于内容层 |
| 文库 | 已导入文档、筛选与管理 | 与首页复用列表行、菜单行为和文档身份 |
| 书签 | 从真实保存位置恢复阅读 | Markdown / EPUB 锚点与 PDF 页码按实际类型呈现 |
| 设置 | 阅读偏好与应用设置 | 普通设置内容；用户 2026-10-04 新增液态玻璃工作台二级页，实验参数仅影响独立预览 |
| Markdown 阅读器 | 连续阅读、目录、文内搜索与链接 | 文字排版与稳定锚点优先 |
| EPUB 阅读器 | 有限可重排内容、分页、章节导航 | 字体 / 视口变化重分页并保留阅读锚点 |
| PDF 阅读器 | 原始页面、缩放、翻页、目录、阅读工具 | 固定版式，保留原始页面颜色 |
| 全局搜索 | 文档标题、划线摘录与笔记 | 从首页 / 文库进入，不新增第五个底栏入口 |
| PDF 正文编辑 | 有限修改、预览、撤销重做与保存 | 独立编辑状态，退出和覆盖操作有明确保护 |

首页、文库、书签、设置共用四入口悬浮底栏；阅读器不继承 Hub 底栏。导航栈是选中状态的权威，显示预览、拖动目标和已提交入口不能成为互相竞争的持久状态。文档标题、阅读进度、书签和批注同样复用真实文档模型，不在 UI 内另造一份事实源。

## 主题、排版与布局

### 主题与表面

采用固定中性色体系。浅色页面为纸白，深色为中性石墨黑；不从旧方案恢复暖米色、绿 / 陶土品牌配色、蓝灰黑或任意动态主题。Dock 蓝色仅表示导航选中态，错误、搜索命中、批注等使用各自语义色。

| 令牌角色 | 当前源码值 / 入口 | 设计含义 |
|---|---|---|
| 浅色背景 / 表面 | `#FFFFFF` | 页面空白与内容底色保持真实，不因玻璃变灰 |
| 浅色主文字 / 次文字 | `#0D0D0D` / `#5D5D5D` | 次信息以语义颜色区分，不把整行任意降低透明度 |
| 深色背景 / 表面 | `#0F0F0F` / `#171717` | 按角色区分表面，不能把所有深色区域统一为一个值 |
| 深色主文字 / 次文字 | `#F2F2F2` / `#B3B3B3` | 前景可读性独立于玻璃表面 alpha |
| Dock 选中色 | 浅色 `#007AFF`；深色 `#64A8FF` | 图标和标签的局部状态色 |
| PDF 页面外画布 | 浅色 `#E8E8E8`；深色 `#252525` | 页面周围的画布允许灰色，PDF 纸张不反色 |

这些是 [Color.kt](app/src/main/java/com/m1racle/reader/ui/theme/Color.kt) 的现状记录，不是要求所有页面同色。首页卡片、阅读器画布和玻璃表面按各自角色解析。应核对实际消费的令牌，不仅查看未使用的默认值或旧注释。

### 字体层级

正文使用正常比例字体，中英文均保持自然字宽；行内代码和代码块使用等宽字体。标题、正文、辅助信息有稳定层级，不以大量加粗代替结构。用户字体和行距设置应影响对应阅读内容并持久化，PDF 原始页面不随正文字号重排。

| 使用位置 | 当前基准 | 约束 |
|---|---|---|
| 首页品牌 | 22sp / 28sp，SemiBold | 固定工具栏中的紧凑品牌，不恢复旧 28sp 大标题布局 |
| 继续阅读标题 | 20sp / 28sp，SemiBold | 优先标题，位置与进度作为较小辅助信息 |
| 首页分节标题 | 15sp | 与内容轨道对齐 |
| 文档列表标题 | 16sp / 24sp，Medium | 最多两行；完整名称在信息面板可查看 / 复制 |
| Markdown / EPUB 正文 | 16.5sp，Normal | EPUB 不额外给整段叠加粗体 |
| Markdown H1 / H2 / H3 / H4 / H5–H6 | 24 / 21 / 19 / 17 / 16sp | 使用共同字号设置与行距比例 |
| Markdown 目录 H1 / H2–H3 | 15 / 14sp | 层级由缩进、字重和当前项共同表达 |
| 行内代码 / 代码块 | 14.5 / 13.5sp | 代码块独立行距，当前字号上限 15.5sp |
| Dock 标签 | 11sp / 14sp | 系统大字号不能裁切；不靠隐藏标签解决空间不足 |

正文行高由字号乘用户选择的比例计算，当前紧凑 / 默认 / 宽松为 1.35 / 1.55 / 1.75。不能使用固定行高导致字号放大后拥挤。代码块维持独立比例；它的字号上限属于当前策略，大字号可读性仍需单独检查。平板正文栏当前最大宽度 760dp，避免无限拉长一行。

具体入口：[ReaderTypography](app/src/main/java/com/m1racle/reader/ui/viewer/components/ReaderTypography.kt)、[HomeDesignTokens](app/src/main/java/com/m1racle/reader/ui/home/HomeDesignTokens.kt)、首页 [ContinueReadingCard](app/src/main/java/com/m1racle/reader/ui/home/ContinueReadingCard.kt) 和 [DocumentListRow](app/src/main/java/com/m1racle/reader/ui/home/DocumentListRow.kt)。通用 `M1racleTypography` 的默认值不必然等于页面覆盖后的实际尺寸。

### 布局与触摸

首页统一 24dp 内容轨道，卡片、品牌和分节对齐；卡片使用普通表面，当前圆角 20dp。可见图标尺寸与可点击尺寸分开：小图标仍提供至少 48dp 的触摸区域，不因此把每一行视觉高度无限撑大。文库格式筛选、阅读字号 / 行高 / 边距等横排单选项，按用户 2026-10-03 参考统一为连体胶囊分段控件：共用底座、内部圆角选中滑块与弹簧切换，不再使用分散 Chip。按用户追加的 RiseDiary 紧凑参考，默认视觉高度 38dp、滑块内缩 3dp、文字 13sp，宽度按内容收紧；按住时预览滑块膨胀与高光、文字轻微放大，滑块使用独立 SelectionLens 玻璃材质，独立采样底座及文字，按压增强边缘折射 / 色散 / 高光与文字放大；采样源与滑块分为兄弟层，避免自采样，阅读设置弹窗也使用自身的采样宿主。按住横向拖动时滑块连续跟随手指，松手吸附并提交目标，取消恢复原项。超宽轨道普通横滑滚动，长按后拖动滑块；正常宽度直接按住拖动。每项至少 48dp 透明点击区域；普通字号下按可用宽度显示整组等宽选项，窄屏允许标签换行；大字号下保持文字完整并可横向滚动。轨道视口始终保留两端圆角，文库四项不得在普通字号下被截断。材质对比的背景单选也复用同一分段组件（Apple Segmented controls，2026-10-03 用户追加要求）。设置与长浮层可纵向滚动。

安全区、顶部控件、正文、底栏和系统导航区分别负责自己的空间。内容可以从悬浮控件后方滚过，但最后一项、最终正文和操作按钮必须能滚到无遮挡的位置。IME 出现、横屏、字体变化后按可用视口约束浮层，不能保留巨大的空白容器或把操作推出屏幕。

## 首页、文库、书签与设置

首页顶部品牌与搜索固定在状态栏下方；滚动只发生在被约束和裁切的内容区域，不越过顶部工具栏或系统区域。按用户 2026-10-03 Tab Bar 参考及加号更正，导入入口位于底栏末尾独立圆钮，与四个导航标签保持 8dp 间隔；不是第五个标签。空首页额外提供明确导入按钮，与底部加号走同一导入流程，由导航层统一拥有 SAF launcher、导入 ViewModel 与结果处理；导入忙时禁用，取消保持当前分区。

最近阅读列表的视口延伸至系统导航安全区上方，允许真实内容经过悬浮玻璃底栏后方；底栏避让由列表尾部内容间距承担，不把整个列表截在底栏上方。空首页保留独立底部避让。加号有独立按压弹簧：玻璃圆钮与前景图标同步最多放大 12%，触摸区域保持固定，松开或移出取消后回弹；按住期间不打开文件选择器，释放点击才触发导入。

继续阅读只展示真实最近打开的文档和位置；尚无阅读记录时不制造 0% 的大卡片。首页与文库共用文档行，标题和进度间距清晰，格式、大小与时间居于辅助层。PDF 展示标题可去掉尾部 `.pdf`，完整文件名仍保留在信息面板；大小未知时如实显示未知，不能编造数值。

继续阅读和普通文档行的溢出按钮、长按提供一致操作。操作菜单不重复塞入完整文件名，详细信息单独显示。当前首页菜单采用由平台定位的不透明表面和轻阴影，绑定到按钮锚点；不能用透明 Popup 伪装成正确背景采样的玻璃。

导入过程中顶部与空态入口同步禁用，显示忙碌状态和可访问提示。失败有具体解释，过大文件显示真实限制与对应错误，不统称“读取失败”；限制来自领域常量，旧预览阈值不能成为新的 UI 限制。

删除文档前明确确认：移除应用内文档、进度、书签、收藏、批注和私有副本，不删除用户原始文件；不可撤销性须说明。禁止重复删除；乐观删除失败时恢复行并提示，不留下空洞或假成功。

文库筛选使用真实格式与数据；Markdown 包仍按 Markdown 类别表达。书签页面按实际锚点类型显示位置，不把所有书签伪装成 PDF 页码。设置只展示有效功能，不留下 H0 / H1 / H2 材质预览、已删除的 Frosted / Liquid 切换或未来能力开关。按用户 2026-10-03 新增要求，设置增加独立“材质对比”入口：相同背景与几何尺寸展示 PDF 原版玻璃、底栏玻璃、选中滑块，支持图案 / 文字背景切换、移动背景和按住预览，文字作为真实采样内容，使用真实生产材质与独立采样源；预览状态不改变应用实际材质。

## Markdown 阅读

### 内容与表格

标题、段落、引用、列表、代码、链接和表格保持原有语义。链接文字可点击，正文不显示源码标记来冒充渲染结果。普通正文与代码的字体、背景和间距分开；不将整个文档卡片化或铺玻璃。

表格采用内容驱动列宽：短标签列保持紧凑，长正文列获得可读宽度。两列不固定等分，也不按简单类别硬套比例；三列以上不为“露出最后一列”过度压缩。实际可读宽度超过视口时水平滚动，末列可完整到达，不添加假预览空间、渐隐、箭头或尾部空白。

表头与正文共用同一列宽向量、单元格 padding 和 GFM 对齐映射（左 / 中 / 右）。同一表头行使用统一字体与首基线；当前表头 SemiBold、正文 Normal，内联强调保留。自然换行，不省略正文；只保留轻水平分隔，不增加外部圆角卡、整圈边框或竖分隔。

表格使用自己的排版尺度，但仍尊重阅读字号和行距。列宽测量依赖内容、视口、密度、字体和方向，不能随着滚动偏移重复计算。表内横向手势与正文纵向滚动共存，不另外抢占整页手势。

### 目录、锚点与图片

目录按真实标题层级和当前标题表达结构。点击后直接到达目标附近，标题不被工具栏挡住；远距离跳转不沿整篇文章做长动画。中文标题、重复标题、列表 / 表格中的链接和显式锚点使用稳定映射。

空 HTML 锚点不可见且不占额外行高，定位到实际关联内容；末尾无法对应内容的空锚点不能错误跳到最后一块顶部。找不到片段时明确处理，不误走外部浏览器。目录“固定当前章 + 独立节列表嵌套滚动”是待决重设计，尚不是本节已生效的交互。

相对图片访问受实际授权限制。单文件 SAF 授权不意味着可以读相邻文件；目录授权成功路径不属于 R8 V1 的验收范围，不能据此宣称当前单文件入口已能读取邻图。未授权、网络图片禁用、解码失败在图片位置给出稳定占位和原因，不连续弹 Toast / 对话框打断阅读。失败不影响其他正文；替代文字与可访问描述按真实内容呈现。

## EPUB 阅读

有限支持可重排 EPUB 文本内容和章节 / 片段链接，不扩展到 DRM、固定版式、脚本、音视频或完整 CSS 浏览器。损坏、受保护、空 spine 或不支持的格式给出明确失败，不停留在空白阅读页。

当前交互是分页阅读，正文测量、页码、目录和阅读位置共用分页结果。应用字体、系统字体、边距或视口变化时重新分页，按内容锚点恢复位置，不能把旧页号直接当成新位置。沿用共同正文排版，页脚所需高度按两行实际测量与字体 padding 计算；页脚显隐不能改变正文可分页高度。

默认优先沉浸正文；中央点击显示控件，左右区域点击或横向拖动翻页，翻页后按既定策略收起控件。覆盖式翻页保持当前页固定，由下一页及其阴影覆盖，不能变成两页一起滑走的横向轮播。控件显隐不意外截获正文手势，目录避让状态栏，页脚安静且位置稳定。

以上分页与覆盖式翻页来自后续实施 / 用户反馈，替代早期 EPUB 架构中的连续滚动建议。封面、复杂富文本、文内搜索和批注等规划不因架构文档提及而自动认定支持。

## PDF 阅读与阅读控件

### 页面、手势与 Chrome

PDF 标题 / 页码胶囊保留原 Capsule 玻璃配方（4dp 模糊、8dp / 24dp 透镜、边缘高光及阴影）。底栏和分段滑块的静止轻灰 / 按压透镜使用独立 SelectionLens，不得覆盖 PDF 胶囊配方。

PDF 保留原始页面颜色；暗色主题改变外围画布与控件，不自动反转纸张。白纸上方浮层的可读性根据真实背景处理，不能只看应用深色开关。横向 FitPage 的页面按后续修正居中，不恢复早期无条件顶对齐。

连续与横向分页复用阅读锚点及缩放 / 平移的权威模型。合法平移跟手，只有越界剩余位移产生弹性；松手回弹可被新手势中断并从当前状态接续。双指到单指再到双指切换不跳锚点、不重置缩放；渲染 tile 更新不能推动内容位置。页间移动连贯，取消回到原页，不制造中间灰屏或未提交的假页码。各历史阶段自动测试通过不代表当前真人手感已通过。

阅读界面保留“正常 / 沉浸”两种有效模式，不恢复早期四模式。沉浸按真实系统能力处理状态栏，保留透明系统手势区；不能画假的系统栏或承诺控制 OEM 瞬时提示层。

Chrome 显隐跟随实际内容滚动方向，程序跳转、恢复位置、布局变化不伪装成用户滚动。缩放、选字、跳页、菜单和搜索期间避免自动收起正在使用的控件；隐藏后清除触摸与可访问节点。页码胶囊与返回 / 更多按钮各自负责自己的点击范围，不能整片顶部空白都触发跳页。

Markdown 顶栏使用一个滚动边缘效果：实时采样正文并模糊，覆层与阅读背景同色，工具栏区域保持可读，下缘 48dp 柔和淡出；随控件收起缩短过渡范围，仅保留状态栏下方 16dp 的可读性保护，正文布局不跳动，搜索沿用同一层。禁止灰 / 黑色装饰遮罩残留正文。Hub 悬浮底栏是独立场景，禁止恢复整片底部模糊铺层。

### 更多菜单、子浮层与输入

更多入口展开为具有连续几何关系的主菜单；宽度、形状和自然高度同步变化，内部文字保持稳定最终布局，避免容器缩放同时又独立缩放文字造成漂移和双重淡化。视觉 morph 容器与点击节点分开，防止小按钮波纹膨胀成整张菜单。

阅读方式与阅读界面在主菜单内互斥展开。“上下连续 / 左右翻页”和“普通 / 全屏沉浸”均保留纵向点击单选，不使用横向拖动的玻璃分段控件（2026-10-03 用户明确要求）。高级功能以当前 `childState` 身份绑定实际来源行，父菜单冻结为上下文，子卡在同一 overlay 坐标系展开；不能使用已失效的第 2–5 行索引，也不能重复扣减根坐标偏移。正文编辑入口加入后仍须保证锚点正确。

四项高级功能沿当前父子浮层路线进入，不恢复早期“关闭主菜单后再弹独立卡”或统一 BottomSheet 方案。Back 从子层返回主菜单、从内联展开返回主菜单、再关闭主菜单；切换来源时先处理原展开状态。批注管理等独立业务面板不因这个规则被强行改成高级子卡；导出进度也按实际任务状态处理。

浮层按自然内容定高，受安全区 / 横屏 / 大字号 / IME 可用空间约束；长内容可滚动，关键操作可达。进入高级功能不自动抢键盘，用户点字段才聚焦。跳页是明确输入任务，可在稳定展开后预选当前数字并提供 IME Go；非法或越界数字给内联错误，不静默改到别页。

长按拖动使用父级真实坐标命中行，临时高亮跟随当前行，离开 / 取消清除。位移与形变有界，释放从当前状态连续回弹；轻点保持正常点击，长按拖动不能误触业务操作。旧“绝对固定中心”和后续有界位移是不同迭代，不同时要求。

## 批注、正文编辑与保存

批注绑定真实文档身份与规范化位置。PDF 文字高亮按实际字符和逐行范围绘制，不用跨行大矩形替代文字选择；静止态不保留橙色调试边框，覆盖色不能把原文字漂白。按页面旋转、裁切和当前变换换算坐标；滚动、移动或第二根手指取消选择，不抢阅读手势。

扫描页无可选择文本时说明原因，不伪造 OCR。有限选字能力与有限正文编辑能力分别声明，前者支持某种旋转不代表后者能修改该类文字。

批注管理中的高亮 / 笔记属于当前文档，在一个虚拟滚动列表内组织，列表身份含类型。笔记保持简单文本；关联同一位置时优先编辑既有笔记而非制造重复项。删除高亮与删除笔记的影响分别说明，失败保留输入和实际数据，不显示假保存成功。

PDF 正文编辑是独立模式，包含原页面预览、可编辑来源区域 / 列表、有效修改后的预览、撤销和重做。先验证候选修改，再替换当前预览；失败保留最后有效内容并给出具体限制。当前只承诺支持路径内的原生水平文本与原字体编码、宽度等约束；不自动 OCR、替换字体、缩字塞入、推动整页排版或解除保护 / 签名限制。

未保存退出需要确认。旋转尽量保留输入和选中位置，但不把进程死亡后的撤销栈恢复当成已保证能力。外部文档打开请求须与编辑状态协调，不能静默丢弃修改。

| 保存动作 | 身份与用户说明 |
|---|---|
| 保存到当前应用文档 | 保留文档身份、历史与关联数据；重新打开显示新内容，不读旧缓存 |
| 另存为 | 形成新文件 / 文档身份，不承诺自动复制原批注与锚点 |
| 覆盖原始文件 | 明确选择，校验原文件与授权 / 内容冲突；有备份、写入核验与恢复路径 |
| 写文件成功但导入失败 | 说明文件已保存、应用导入失败及可用后续入口 |
| 取消或保存失败 | 保留未保存编辑，不伪造成功或关闭现场 |

重新授权应针对原文件，不能借覆盖功能要求整个目录。不同文档提供者的写入能力不同，不声称所有外部文件都具有原子覆盖保证。

## 搜索、导入与外部打开

### 两种搜索范围

| 场景 | 搜索对象 | 结果行为 |
|---|---|---|
| Markdown 文内搜索 | 与渲染对应的可见文本 | 显示命中数、当前命中、上一 / 下一和清空；定位到对应内容块 |
| 应用搜索 | 文档标题、划线摘录、笔记 | 结果标明文档、来源种类、摘要和真实位置，跳转到原文 |

文内搜索不匹配 Markdown 语法、链接 URL 或图片资源地址；图片 alt / caption 只有与实际显示文本对齐时才参与。初始未输入与无命中分开，0 命中禁用上 / 下一；更换查询重置当前命中，切换文档清理旧状态。清空查询保留搜索模式，Back 优先退出搜索。

逐字符高亮全部命中，活动命中使用更强区分，保留粗体、斜体、代码和链接语义；不把整块正文染黄。同块多个命中不可只显示一个假位置。当前定位承诺到内容块，字符级精确滚入视口是待完成项，不宣称跨块、正则或全库正文索引。

应用搜索区分初始、加载、无结果、失败和有结果。摘要简洁，页 / 段位置以用户可读的从 1 开始方式显示；结果身份按文档、来源类型和真实条目组成，不能按摘要猜锚点。新输入取消旧查询，不能让旧结果覆盖新结果或把取消显示成错误。索引历史回填是否完成以实际记录为准。

### 导入与外部打开

统一处理应用内导入、VIEW / SEND 等入口。元数据来自真实提供者与复制结果；缺失名称 / 大小时采用诚实回退，重复打开不堆积“未命名文档”。相同文件复用身份，同名不同内容不自动合并；旧去重报告不能替代最新身份规则。

一次只保留一个活动阅读会话。同一文档可恢复当前会话；不同文档需要明确替换确认，取消保留原文档、位置与编辑状态，不能留下新卡片或半成品导入。确认后按受控会话切换，失败不把原会话静默删除；关闭阅读会话不等于从文库删除文件。外部兼容性待办按提供者分别验证，不凭一条成功路径宣布全部支持。


## Liquid Glass：审阅范围

审阅日期：2026-10-03。依据本会话中文摘要、用户提供的“应用和配置液体玻璃效果”截图及下列官方资料整理；截图已与官方对应章节核对，当前未收到可读取的完整版附件，不声称已逐段审阅附件。以下按五个来源分节，中文内容为归纳而非官方译文。WWDC 使用网页文字稿，未观看视频；第五节明确属于补充来源。Android 落地要求是本项目的设计选择，不是 Apple API 对 Android 的承诺。

### 1. Meet Liquid Glass — WWDC25 文字稿

来源：[Meet Liquid Glass](https://developer.apple.com/videos/play/wwdc2025/219/)，重点参考 Dynamics、Adaptivity、Principles。

玻璃是浮在内容上方的导航与控件层，光学与运动共同表达交互。静止应安静，触摸时可以形变、发光、抬起，释放后复原；透明加模糊本身不足以代表完整材质。

避免玻璃叠玻璃：放在玻璃上的元素优先采用填充、透明度和 vibrancy，形成同一材质的薄覆盖，而不是再加一层独立玻璃。着色用于重点功能和主要操作，避免所有控件都着色；整体品牌表达主要放在内容层。

### 2. HIG Materials — 材质与使用边界

来源：[Materials](https://developer.apple.com/design/human-interface-guidelines/materials)。

Liquid Glass 用于导航和控件；正文、文档列表、卡片背景属于内容层，使用普通表面或标准材质。内容中的滑块、开关等短暂交互控件是例外，可以在激活时显现玻璃。

默认采用 Regular 的设计目标：模糊并调节背景亮度，保证标签清晰。Clear 主要用于照片、视频等视觉丰富的背景。明亮背景可考虑约 35% 黑色调暗层；背景足够暗或 AVKit 已提供调暗时，不必另加。Clear 不是“透明度更低的 Regular”，不能只改 alpha 就宣称实现某种原生变体。

### 3. Adopting Liquid Glass — 采用与验证

来源：[Adopting Liquid Glass](https://developer.apple.com/documentation/TechnologyOverviews/adopting-liquid-glass)。

Apple 原生应用使用最新 Xcode / SDK 构建，并运行在对应新系统上，可让符合条件的 SwiftUI、UIKit、AppKit 标准组件采用新外观。这不适用于 Android 自动获得原生 Liquid Glass。

减少导航栏、标签栏、工具栏及浮层中会遮挡、干扰系统材质的自定义背景；不要误删真实内容背景。测试浅深色和不同显示 / 辅助功能配置，包括降低透明度、增强对比度、减弱动态效果。标准组件的自动适应不能当作自定义 Android 控件已具备这些能力的证据。

### 4. Applying Liquid Glass to custom views — 自定义组件

来源：[Applying Liquid Glass to custom views](https://developer.apple.com/documentation/swiftui/applying-liquid-glass-to-custom-views)。

SwiftUI 自定义组件使用 `glassEffect(_:in:)`；多个相邻效果用 `GlassEffectContainer` 协调渲染、形状融合与过渡。容器并不是允许任意叠加独立玻璃面板的理由。突出主要按钮时可采用 `.glassProminent` 样式；图标和文字也可着色，不能把“主要操作着色”理解成其他语义色一律禁止。

这些属于 Apple 原生 API。Android 应复用既有 Compose / Backdrop 材质入口和受保护的采样边界，避免每个控件各自创建一套采样、滤镜和动画逻辑；这只是实现原则的对应，不是 API 等价。

#### 形状、着色与交互分别配置

用户截图对应官方“Apply and configure Liquid Glass effects”章节。三个示例展示的是不同配置维度，不是三个固定的材质等级：

| 配置 | 官方语义 | 本项目的对应要求 |
|---|---|---|
| `.glassEffect()` | 默认 Regular，玻璃位于内容后方，形状为 Capsule | 底栏可采用胶囊，但不能将所有组件都做成胶囊 |
| `.glassEffect(in: .rect(cornerRadius: 16.0))` | 更换为指定圆角的矩形 | 较大浮层根据尺寸选择圆角矩形；16 是示例值，不是全项目圆角标准 |
| `.glassEffect(.regular.tint(.orange).interactive())` | Regular 材质、强调着色、触摸与指针交互响应分别组合 | 材质、语义强调色、交互状态分开管理；橙色是示例，不是项目配色要求 |

`.interactive()` 为自定义玻璃加入类似标准玻璃按钮的响应与流动反馈；它不自动赋予视图业务点击行为、导航回调或可访问的按钮语义。不能把仅绘制了玻璃的外观当成已经实现交互。

SwiftUI 先设置字体、布局和 padding 等影响视图外观的修饰，再应用玻璃效果。Android 也应让玻璃边界匹配最终控件尺寸，触摸范围与导航语义由控件负责。示例用 `Text` 演示 API 能力，不改变前文“正文不铺玻璃”的产品规则。

#### 官方网页动态示例的视觉核对

2026-10-03 使用浏览器播放本页的配置示例与形变示例，并逐时查看画面。此处是网页内嵌示例的观察，不是 WWDC 视频的观看记录，也不是对本应用效果的验收。

- 配置示例中，未着色的玻璃内部保留经过柔化的背景颜色与纹理，边缘有明暗层次；橙色交互示例的轮廓短暂扩张后恢复。不能用固定灰色填充加缩放，作为完整材质已实现的依据。
- 形变示例中，两个圆形控件之间出现连续连接的玻璃轮廓，再回到单个圆形；过渡过程同时改变轮廓和内部呈现，并非两个固定圆形仅做淡入淡出。下方 Toggle 在激活画面中也有更明亮的反馈。
- 上述融合是相邻控件在容器内的特定过渡，不要求本项目四个导航入口融合成液滴。底栏的具体手感仍须独立核对，不能从示例推导统一的放大倍数、动画时长或折射参数。

本项目据此补充验收要求：检查背景纹理在玻璃内部的呈现、边缘光学层次，以及交互轮廓与材质反馈的同步变化。纯白页面不能单独证明这些能力，应增加文字和图片背景；按压放大只是反馈的一部分。

### 5. HIG / Design 更新内容 — 补充

来源：[What’s new — Design](https://developer.apple.com/design/whats-new/)。

HIG 首页作为索引，不作为 Liquid Glass 细则的出处。更新页用于追踪指南变更，例如 Materials、Motion、Color、Buttons、Toolbars、Tab bars 的 Liquid Glass 更新。具体结论仍需回到对应指南或技术文档，不能仅凭更新目录推导规则。

WWDC25 文字稿反映发布时的说明；当后来更新的 HIG 给出更细条件时，应同时标明差异。例如文字稿强调 Clear 的调暗需求，而 Materials 明确允许在足够暗的背景或已有系统调暗处理时不额外添加调暗层。

## Liquid Glass 的项目落地要求

### 使用范围与层级

| 场景 | 项目规则 |
|---|---|
| 底部导航、阅读工具栏、必要的主操作控件 | 可以使用玻璃，优先满足导航识别与可读性 |
| 菜单、popover、sheet 等操作浮层 | 按功能层级选择表面；内部选项和卡片不再各铺独立玻璃 |
| Markdown / PDF / EPUB 正文、文档列表、继续阅读卡片 | 内容层，不使用 Liquid Glass 背景；保留真实内容底色 |
| 导航选中态 | 同一导航材质上的轻覆盖或交互透镜；不要把选中态另做成独立玻璃面板 |
| 蓝色选中图标 / 标签 | 表示当前入口；不要求整条底栏染蓝，也不等于将所有操作提升为主要操作 |

阅读器的文字与图标密度不适合以照片 / 视频场景的 Clear 为默认目标。不能为了“更透明”牺牲文字对比度，也不能用厚灰色覆层掩盖采样错误。

材质由角色、环境、硬件能力和交互状态统一解析。完整折射、仅模糊和实色降级必须明确区分；没有可靠采样来源或跨窗口浮层时采用可读的实色表面，不能让文字直接浮在未经处理的复杂背景上。前景文字 / 图标保持独立可读，不随表面透明度一起变淡。采样与滤镜集中复用，不把历史预览配方带入每个页面。

### 悬浮底栏与交互

1. 底栏浮在页面之上；下方不添加整片灰色底板或模糊铺层。允许轻阴影表达高度，Android 系统手势指示条由系统管理。
2. 保留四个入口及导航状态的单一所有者；点击、拖动预览、释放和取消的视觉状态必须与导航结果一致。默认高度 60dp、内缩 4dp；图标统一实心 24dp，短标签保留，大字号按实际文字高度适配。末尾独立圆形加号触发导入，不纳入拖动选项。
3. 静止选中态低调清晰；按压反馈及时，透镜与图标可以适度放大、折射、形变。释放与取消必须复原，不能残留暗罩、高光或放大。
4. 形变应有界，不裁切标签，不产生重影、透镜外的残余图标或底部碎片；系统大字号下仍保证触摸与阅读空间。
5. 四页在相同主题和相同背景条件下，材质必须一致。页面切换本身不得让底栏意外变灰；真实背景内容变化可以合理影响材质。
6. 采样必须包含实际的不透明页面背景与内容，不能把透明空像素当成页面颜色。去掉覆盖玻璃的装饰背景，不等于去掉采样中的内容底色。
7. 采样来源与玻璃呈现是不同职责。捕获层不得采样自身；底栏及选中透镜只采样实际页面，图标和标签作为独立前景绘制，不进入透镜来源，避免折射边缘重复字形。

### 无障碍与验收门槛

| 条件 | 目标行为与验证 |
|---|---|
| 降低透明度或相应应用设置 | 增强表面遮蔽，优先清晰；不改变入口与功能 |
| 增强对比度或相应应用设置 | 检查文字、图标、选中态和边界；避免只靠透明度或颜色区分 |
| 减弱动态效果、系统禁用动画或相应应用设置 | 减弱或关闭弹性、速度形变及装饰运动；保留状态反馈 |
| 系统大字号 | 检查标签、触摸范围、浮层滚动及阅读正文避让 |
| 浅 / 深色、纯色 / 文字 / 图片背景 | 分别检查静止、按住、松手、拖动、取消和快速切换 |

Android 没有与 Apple 三项设置完全一一对应的统一 API。实现时应明确采用哪些系统信号、哪些需要应用设置，以及实际效果；硬件能力降级不等于无障碍适配。没有实现与验收证据的项必须标为待完成。

GPU 玻璃效果需要最终屏幕截图或设备像素证据；语义树和 JVM 测试不能代替光学与性能验收。四入口应逐页核对，不能用首页通过代表全部页面。

## Liquid Glass 当前证据与未完成项

已确认的范围见 [四入口采样一致性报告](docs/DOCK_CAPTURE_CONSISTENCY_20261003.md)：共享采样补齐主题背景、四页浅深色一致性、底部无整片铺层，以及既有按压 / 拖动反馈。此前报告属于历史过程，不应重复声明为新的验收结果。

以下仍需单独审查或实现，本文不宣布完成：

- 导航外层与选中 Capsule 的组合，是否形成重复玻璃处理，能否按同一材质交互透镜的目标收口。
- 菜单、设置卡片等当前表面的全项目使用范围审查，避免内容层玻璃和玻璃叠玻璃。
- 背景亮度自适应与可读性，不能将当前固定配方直接称作 Apple 原生 Regular。
- 降低透明度、增强对比度、减弱动态效果的实际接入和逐项验收。
- 真机光学、运动手感、帧率与资源占用验收。

本轮只整理设计文档，未修改产品代码或构建 APK；上述历史证据不因此刷新。

## HIG 中文总览附件的审阅与采用边界

2026-10-03 审阅用户提供的 `hig-summary.md`。它适合作为跨平台资料索引；本文只采用经过核对且适合本项目的设计原则。附件声称抓取了 173 个页面，但其 `/workspace/hig/raw/` 原始文本不在本次可读附件中，不能宣布已逐页核验全部抓取结果。本次通过 Apple 官方文档 JSON 核对 Accessibility、Buttons、Dark Mode、Tab bars、Motion、Popovers、Pointing devices 和 Scroll views，并对更新记录与 WCAG 文字对比度条件交叉核对。

[Apple Design 更新页](https://developer.apple.com/design/whats-new/)确实记录了 2026 年的设计原则更新及 Designing for iPhone Duo；不能因旧知识中没有这些名称就判定附件虚构。它们属于 Apple 平台的资料背景，不自动扩展本项目设备或功能范围。

| 摘要中容易被直接套用的条目 | 核对与本项目采用方式 |
|---|---|
| iOS 控件默认 44pt、最小 28pt | [Accessibility](https://developer.apple.com/design/human-interface-guidelines/accessibility) 的表格确有这两个值，但 [Buttons](https://developer.apple.com/design/human-interface-guidelines/buttons) 的一般点击区域建议仍至少 44×44pt。不能据此把普通按钮缩为 28pt；Android 继续采用本文至少 48dp 触摸区域的项目规则，pt / dp / sp 不直接换算。 |
| “18pt 或粗体”都可以用 3:1 | 摘要压缩了尺寸条件。按 [WCAG Contrast Minimum](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html)，大字条件为至少 18pt 普通字或 14pt 粗体；不能理解为任意小号粗体都只需 3:1。项目小标签和普通正文以至少 4.5:1 为目标，玻璃上另查实际背景；不能只计算固定 tint 色。 |
| 不提供应用内外观设置、不要硬编码颜色 | [Dark Mode](https://developer.apple.com/design/human-interface-guidelines/dark-mode) 的原文是避免独立外观设置，并优先尊重系统外观；不能升级为跨平台的绝对禁令。保留本项目既有阅读偏好与语义令牌，不据此删除设置或重置中性色。系统颜色的动态语义与自定义品牌 / 内容色分开。 |
| 标签栏“始终可见” | [Tab bars](https://developer.apple.com/design/human-interface-guidelines/tab-bars) 强调分区导航持续可发现，也说明模态遮盖和特定 accessory 场景的收缩行为。本项目四入口在 Hub 分区保留，阅读器不因这句话被强制加入底栏；本文也不新增滚动收缩功能。 |
| 频繁交互“不加动画” | [Motion](https://developer.apple.com/design/human-interface-guidelines/motion) 是节制额外、无必要的运动，不是取消系统细微反馈或自定义按钮按下状态。Dock 保留必要的按压 / 拖动反馈，避免冗长装饰，并适配减弱动态效果。 |
| scroll edge effect 用于浮层后的滚动内容 | [Scroll views](https://developer.apple.com/design/human-interface-guidelines/scroll-views) 限定了功能用途，且建议优先 automatic、充分测试 soft。它不要求本项目恢复已被用户删除的整片底部铺层；Markdown 顶部可读性处理与 Hub 底栏分别判断。 |
| 优先系统组件、SF Symbols、Dynamic Type | 这些是 Apple 平台实现建议。Android 对应采用 Compose 组件、自己的图标 / 语义、系统字号和辅助功能支持，不能宣布自动获得 Apple 原生玻璃或 VoiceOver 能力。 |

继续采用摘要中适用的共同原则：内容优先、渐进披露、保留上下文、及时而真实的反馈、手势替代入口、克制模态、具体错误文案、就地请求必要权限和尊重辅助功能。账户、支付、AI、通知及各 Apple 专属技术是资料背景，不纳入 Reader 当前功能清单。

摘要末尾的“18 条”应作为带平台 / 场景前提的检查提示，不能称作统一硬指标。后续引用应直接链接对应原文，注明建议还是必要条件；其原始提取文本和逐项核验仍是不同证据。

## 历史冲突裁决

下表用于防止从旧报告恢复已经替换的方案；裁决不删除历史证据，也不把现有代码自动宣布为符合全部设计要求。

| 旧资料中的要求 / 结论 | 当前收口 | 依据 |
|---|---|---|
| PRD 早期米色 / 绿陶土色、英文等宽正文、16sp 基准 | 固定中性色、正常比例正文、16.5sp 共同基准 | 当前主题 / ReaderTypography 与 10 月排版报告 |
| 首页大标题随列表滚动、导入 FAB、Hero 玻璃 | 固定工具栏导入，实色继续阅读 / 文档内容 | HOME_LAYOUT_POLISH、HOME_CONTENT_FIRST 与当前用户要求 |
| 首页操作菜单透明 Popup / 玻璃 | 当前平台锚定、不透明轻阴影菜单 | HOME_MENU_CORRECTION、HOME_SOLID_MENU |
| 旧“最近文件首页” / 三核心页 / EPUB 尚未实现 | 四入口 Hub 与有限 EPUB 分页阅读 | H2.2、后续 EPUB 状态记录、实际页面 |
| 旧 Markdown 预览大小阈值和截断验收 | 遵循当前完整加载 / 限制常量，错误说明具体 | 后续审查与 HOME_FUNCTIONAL_FIXES；不复制旧 200KB 数字 |
| 两列表格按类别分配、表头强制左对齐 | 内容驱动列宽；表头正文共用 GFM 对齐 | Content-driven V2、R3 宽度修正、TABLE_HEADER_BODY_ALIGNMENT_FIX |
| PDF 横向模式无条件顶部对齐、四种阅读界面 | 后续 FitPage 居中、正常 / 沉浸两种界面 | R13 模式收敛与后续控件报告 |
| 更多菜单独立卡、所有高级项 BottomSheet、教材页码自动聚焦 | 当前同根父子浮层、身份绑定来源行、用户点字段聚焦 | R26 父子浮层、NESTED_ROW_ORIGIN、后续输入修正 |
| 高级项固定第 2–5 行来源 | 使用 childState 和真实入口身份 | 新增正文编辑后固定索引已失效 |
| 父子玻璃导出被称为“官方 glass-on-glass” | 工程采样能力不等于官方设计许可；独立叠玻璃仍待审查 | Apple Materials / WWDC 原则优先 |
| 菜单固定中心不平移与后续拖动位移 | 采用后续有界位移，连续回弹、临时命中高亮 | PDF_MENU_ELASTIC_HIGHLIGHT 内后续修正、VIDEO_MENU_RISE_DOCK |
| EPUB 架构建议连续滚动 | 当前分页及覆盖式翻页，锚点保留 | 后续状态流水中的实施与用户反馈 |
| Settings 的 H0 / H1 / H2、光学配方比较面板 | 已退出产品设置；实验不作为正式入口或批准 | PDF_MENU_DRAG_SOFT_SCROLL_EDGE 与后续设置现状 |
| 某阶段 0.86 / 18dp “冻结”配方、其他低 alpha 候选 | 各自是阶段配方，非全项目永恒常量 | 后续 FLOATING_LIQUID_GLASS / Dock 参考与当前材质入口 |
| Dock 下方 SoftScrollEdge、四页同色已通过 | 底部铺层已移除；以采样一致性修复后的逐页证据为准 | FLOATING_DOCK、DOCK_CAPTURE_CONSISTENCY |
| 采样滞后被假定为 GPU 一帧延迟 | 不沿用已撤回假设，按实际布局 / 采样根因排查 | SOFT_GLASS_SAMPLING / CASCADE 与四页采样报告 |
| 外部打开先关闭旧阅读器、再导入新文件 | 先准备并核验新文档，再原子提交会话替换；失败保留原会话 | EXTERNAL_OPEN_ATOMIC_SESSION_SWITCH_HARDENING |
| 纯白截图、预览卡、结构测试通过即批准材质 | 同背景设备像素、动态帧、复杂内容和人工观感分别验收 | 光学审计、官方动态示例与用户反馈 |

阶段材料中的毫秒、放大倍数、折射量、透明度和阻尼属于具体实现记录。除本文明确标注的当前布局 / 排版基准外，不能把它们混合成一份新的配方。后续调参应绑定场景、背景、主题和来源，明确候选与批准状态。

## 设计验收与待完成项

### 共同验收要求

每个用户流程都检查初始、空态、加载、成功、失败与取消；状态文字与真实数据一致。危险操作有准确的影响说明，失败保留可恢复现场。选择 / 展开 / 按钮语义与实际状态一致，不只靠颜色、动画或小图标表达结果。

系统 200% 字号、浅深色、横竖屏和窄屏至少覆盖核心阅读与操作链路。长标题、两行标签、Wrap / FlowRow、浮层滚动、页脚、安全区和 IME 分别检查；TalkBack 的焦点顺序、角色、选中 / 展开状态与隐藏节点不能用截图代替。

运动从当前状态接续，允许中断、快速切换和取消，不在新手势开始时跳回零点。减弱动画时保留状态反馈，关闭装饰弹性。正常空闲不持续驱动重绘；滤镜与采样资源按生命周期复用和释放。表格测量、搜索、图片和 PDF 渲染不能让主线程随滚动重复做重型工作，这些目标需要真实性能证据。

逻辑测试、语义树、最终像素、动态录屏、性能数据与人工验收分开报告。历史截图只对其代码、设备与配置有效；资料中“实现完成但实机待验”“自动通过但人工拒绝”必须保留区别。设计文档修改不刷新任何运行时结果，也不等于 APK 交付。

### 待决与待验证分开

| 项目 | 状态 / 下一步 | 不应作出的声明 |
|---|---|---|
| 目录固定当前章与节列表嵌套滚动 | [ISSUE_TOC_OUTLINE_UX_REDESIGN](docs/ISSUE_TOC_OUTLINE_UX_REDESIGN.md) 仍 OPEN；外层章列表范围、点章行为、影响范围需决策 | 不说已经实现，不以本次总结自动立开发任务 |
| 文内搜索字符级精确滚入视口 | 当前块级定位之外的后续能力 | 不把字符高亮当成字符精确定位 |
| 玻璃使用范围及父子 / Dock 选中层 | 逐个消费点审查是否内容层玻璃或重复处理 | 不说全项目已符合 Apple 规则 |
| 玻璃亮度自适应与辅助功能 | 明确 Android 信号 / 应用设置，补实际接入和验收 | 不把固定配方或 API 降级当作原生 Regular / 无障碍完成 |
| TalkBack、大字号和 IME 的完整流程 | 按实际页面补设备证据 | 不把单页截图当全应用通过 |
| PDF 手势与玻璃动态观感 | 当前代码的真人操作 / 录屏，真机光学与性能 | 不继承曾被拒绝视频的通过结论 |
| 外部提供者兼容、覆盖与恢复 | 按真实提供者和授权路径验证 | 不说任意网盘 / 文件提供者都受支持或原子写入 |
| 规划的 EPUB / PDF 扩展能力 | 维持 PRD 有限范围，另行评估 | 不把研究方案写成现有功能 |

具体已验证版本、设备、发布状态仍由 CURRENT_STATUS 与对应阶段报告负责。本轮只更新设计规范和文档路由，不修改产品行为，不构建或交付 APK。

## 设计资料来源索引

以下按设计主题收录方案与阶段证据。同一组中从早到晚可能相互替换，应结合上面的冲突裁决阅读，不逐项恢复。文件名中的 MARKLEAF / 旧包名保留其历史身份，不代表当前产品重新命名。PRD、README、CHANGELOG、CURRENT_STATUS、验收清单和历史流水中的相关设计内容作为交叉核对；纯技术日志不作为额外设计规范。

### 产品、Hub、首页与排版

- [DESIGN_SYSTEM_DEBT_CLOSURE_INTEGRATION_REPORT](docs/DESIGN_SYSTEM_DEBT_CLOSURE_INTEGRATION_REPORT.md)
- [H2_1_READING_LIBRARY_FOUNDATION_REPORT](docs/H2_1_READING_LIBRARY_FOUNDATION_REPORT.md)
- [H2_1_READING_MODEL_AUTHORITY_REPORT](docs/H2_1_READING_MODEL_AUTHORITY_REPORT.md)
- [H2_2_HOME_READING_HUB_REPORT](docs/H2_2_HOME_READING_HUB_REPORT.md)
- [HOME_ALIGN_LIQUID_CONTROLS_REPORT](docs/HOME_ALIGN_LIQUID_CONTROLS_REPORT.md)
- [HOME_APPLE_DESIGN_GUIDANCE](docs/HOME_APPLE_DESIGN_GUIDANCE.md)
- [HOME_CONTENT_FIRST_LAYOUT_REPORT](docs/HOME_CONTENT_FIRST_LAYOUT_REPORT.md)
- [HOME_DENSITY_POPUP_CLOSURE_REPORT](docs/HOME_DENSITY_POPUP_CLOSURE_REPORT.md)
- [HOME_FINAL_POLISH_REPORT](docs/HOME_FINAL_POLISH_REPORT.md)
- [HOME_FUNCTIONAL_FIXES](docs/HOME_FUNCTIONAL_FIXES.md)
- [HOME_LAYOUT_POLISH](docs/HOME_LAYOUT_POLISH.md)
- [HOME_LIQUID_CONTROLS_REPORT](docs/HOME_LIQUID_CONTROLS_REPORT.md)
- [HOME_MENU_CORRECTION](docs/HOME_MENU_CORRECTION.md)
- [HOME_PLAIN_CONTROLS_RESTORE_REPORT](docs/HOME_PLAIN_CONTROLS_RESTORE_REPORT.md)
- [HOME_SOLID_MENU](docs/HOME_SOLID_MENU.md)
- [HOME_VISUAL_CLOSURE_REPORT](docs/HOME_VISUAL_CLOSURE_REPORT.md)
- [PRD](docs/PRD.md)
- [R10_HOME_LIST_CARD_PERFORMANCE_REPORT](docs/R10_HOME_LIST_CARD_PERFORMANCE_REPORT.md)
- [R33_PRODUCT_IDENTITY_MIGRATION_REPORT](docs/R33_PRODUCT_IDENTITY_MIGRATION_REPORT.md)
- [UI_TYPOGRAPHY_20261002](docs/UI_TYPOGRAPHY_20261002.md)

### 材质、悬浮底栏与参考实验

2026-10-04 用户提供 Apple Materials、Aave 参数工作台截图与 AndroidLiquidGlass 链接，并明确要求直接加入 M1racle Reader 设置的二级页面。新增“液态玻璃工作台”：复用 Backdrop，独立普通背景采样，实时控制几何、曲率、折射、色散、模糊、高光和阴影；支持网格 / 光谱 / 文字背景、拖动、法线分屏、Regular / Clear 实验预设与参数复制。进入此子路由隐藏 Hub 底栏，返回恢复设置。实验不修改实际阅读器和 Dock 的生产材质。此明确新增要求取代此前“设置不允许任何玻璃实验入口”的历史限制；不恢复旧 H0/H1/H2 面板。

- [DOCK_CAPTURE_CONSISTENCY_20261003](docs/DOCK_CAPTURE_CONSISTENCY_20261003.md)
- [DOCK_REFERENCE_OPTIMIZATION](docs/DOCK_REFERENCE_OPTIMIZATION.md)
- [FLOATING_DOCK_20261003](docs/FLOATING_DOCK_20261003.md)
- [FLOATING_LIQUID_GLASS_20260925](docs/FLOATING_LIQUID_GLASS_20260925.md)
- [FROST_EXPLICIT_DROP_SHADOW](docs/FROST_EXPLICIT_DROP_SHADOW.md)
- [FROST_VISIBLE_OUTLINE_PREVIEW](docs/FROST_VISIBLE_OUTLINE_PREVIEW.md)
- [GENTLE_FROST_AND_UI_POLISH](docs/GENTLE_FROST_AND_UI_POLISH.md)
- [GLASS_BACKPLATE_ROLLOUT](docs/GLASS_BACKPLATE_ROLLOUT.md)
- [GLASS_MATERIAL_ACCEPTANCE_REPORT](docs/GLASS_MATERIAL_ACCEPTANCE_REPORT.md)
- [GLASS_MATERIAL_COMPARISON](docs/GLASS_MATERIAL_COMPARISON.md)
- [GLASS_MATERIAL_ENGINE_REPORT](docs/GLASS_MATERIAL_ENGINE_REPORT.md)
- [HYPERCHANGER_BLUR_REFERENCE](docs/HYPERCHANGER_BLUR_REFERENCE.md)
- [LIQUID_GLASS_DOCK_20261002](docs/LIQUID_GLASS_DOCK_20261002.md)
- [LIQUID_GLASS_REFERENCE_20261003](docs/LIQUID_GLASS_REFERENCE_20261003.md)
- [R36_1_GLASS_AUTHORITY_CLOSURE](docs/R36_1_GLASS_AUTHORITY_CLOSURE.md)
- [R36_2_DARK_RECIPE_EVALUATION](docs/R36_2_DARK_RECIPE_EVALUATION.md)
- [R36_2_LIGHT_TINT_EVALUATION](docs/R36_2_LIGHT_TINT_EVALUATION.md)
- [R36_2_OPTICAL_MATERIAL_REPORT](docs/R36_2_OPTICAL_MATERIAL_REPORT.md)
- [R36_3_HYPERLIGHT_SOFT_GLASS_COMPARISON](docs/R36_3_HYPERLIGHT_SOFT_GLASS_COMPARISON.md)
- [R36_GLASS_GROUND_TRUTH_AUDIT](docs/R36_GLASS_GROUND_TRUTH_AUDIT.md)
- [SETTINGS_LIQUID_GLASS_20260924](docs/SETTINGS_LIQUID_GLASS_20260924.md)
- [SETTINGS_SOFT_LIGHT_MENU_20260926](docs/SETTINGS_SOFT_LIGHT_MENU_20260926.md)
- [SOFT_GLASS_CASCADE_20260924](docs/SOFT_GLASS_CASCADE_20260924.md)
- [SOFT_GLASS_PANEL_20260924](docs/SOFT_GLASS_PANEL_20260924.md)
- [SOFT_GLASS_SAMPLING_20260924](docs/SOFT_GLASS_SAMPLING_20260924.md)
- [VIDEO_MENU_RISE_DOCK_20260927](docs/VIDEO_MENU_RISE_DOCK_20260927.md)

### 阅读控件、菜单、几何与输入

- [EXPANDED_READER_MENU_FROST](docs/EXPANDED_READER_MENU_FROST.md)
- [MARKDOWN_FIXED_GRADIENT_AND_THICK_FROST](docs/MARKDOWN_FIXED_GRADIENT_AND_THICK_FROST.md)
- [MORE_LIQUID_STABLE_20260925](docs/MORE_LIQUID_STABLE_20260925.md)
- [NESTED_LIQUID_MATERIAL_20260925](docs/NESTED_LIQUID_MATERIAL_20260925.md)
- [NESTED_ROW_ORIGIN_20260925](docs/NESTED_ROW_ORIGIN_20260925.md)
- [PDF_CHROME_BACKGROUND_FLICKER_FIX](docs/PDF_CHROME_BACKGROUND_FLICKER_FIX.md)
- [PDF_CONTROLS_POLISH_20260915](docs/PDF_CONTROLS_POLISH_20260915.md)
- [PDF_FROST_COLOR_AND_SHADOW](docs/PDF_FROST_COLOR_AND_SHADOW.md)
- [PDF_MENU_DRAG_SOFT_SCROLL_EDGE_20260927](docs/PDF_MENU_DRAG_SOFT_SCROLL_EDGE_20260927.md)
- [PDF_MENU_ELASTIC_HIGHLIGHT_20260927](docs/PDF_MENU_ELASTIC_HIGHLIGHT_20260927.md)
- [R11_X_STYLE_TOPBAR_IMMERSIVE_COLLAPSE_REPORT](docs/R11_X_STYLE_TOPBAR_IMMERSIVE_COLLAPSE_REPORT.md)
- [R12_PDF_READER_MENU_IMMERSIVE_POLISH_REPORT](docs/R12_PDF_READER_MENU_IMMERSIVE_POLISH_REPORT.md)
- [R13_PDF_READER_MODE_CONVERGENCE_MORPH_MENU_PAGE_CENTERING_REPORT](docs/R13_PDF_READER_MODE_CONVERGENCE_MORPH_MENU_PAGE_CENTERING_REPORT.md)
- [R14_PDF_READER_MORE_SURFACE_SINGLE_ANCHOR_GLASS_REPORT](docs/R14_PDF_READER_MORE_SURFACE_SINGLE_ANCHOR_GLASS_REPORT.md)
- [R15_PDF_READER_MENU_INTERACTION_NODE_SPLIT_REPORT](docs/R15_PDF_READER_MENU_INTERACTION_NODE_SPLIT_REPORT.md)
- [R16_PDF_READER_CHROME_SCROLL_STATE_MACHINE_REPORT](docs/R16_PDF_READER_CHROME_SCROLL_STATE_MACHINE_REPORT.md)
- [R17_PDF_READER_CHROME_DIRECTION_GROUND_TRUTH_REPORT](docs/R17_PDF_READER_CHROME_DIRECTION_GROUND_TRUTH_REPORT.md)
- [R18_PDF_READER_CHROME_INTERACTION_RECONSTRUCTION_REPORT](docs/R18_PDF_READER_CHROME_INTERACTION_RECONSTRUCTION_REPORT.md)
- [R19_PDF_READER_CHROME_GEOMETRY_DENSITY_REPORT](docs/R19_PDF_READER_CHROME_GEOMETRY_DENSITY_REPORT.md)
- [R20_PDF_READER_CHROME_VISUAL_DENSITY_REPORT](docs/R20_PDF_READER_CHROME_VISUAL_DENSITY_REPORT.md)
- [R21_PDF_READER_CHROME_INTERACTION_POLISH_REPORT](docs/R21_PDF_READER_CHROME_INTERACTION_POLISH_REPORT.md)
- [R22_5_TEST_REPORT](docs/R22_5_TEST_REPORT.md)
- [R22_6_GLASS_INTERACTION_REPORT](docs/R22_6_GLASS_INTERACTION_REPORT.md)
- [R22_READER_CHROME_CONTAINER_MORPH_REPORT](docs/R22_READER_CHROME_CONTAINER_MORPH_REPORT.md)
- [R22_READER_INTERACTION_POLISH_REPORT](docs/R22_READER_INTERACTION_POLISH_REPORT.md)
- [R23_READER_MORE_SURFACE_FAMILY_REPORT](docs/R23_READER_MORE_SURFACE_FAMILY_REPORT.md)
- [R24_1_MORE_NESTED_MOTION_REPLICA_REPORT](docs/R24_1_MORE_NESTED_MOTION_REPLICA_REPORT.md)
- [R24_2_NESTED_CARD_VISUAL_REPLICA_REPORT](docs/R24_2_NESTED_CARD_VISUAL_REPLICA_REPORT.md)
- [R24_MORE_VISUAL_NESTED_OVERLAY_REPORT](docs/R24_MORE_VISUAL_NESTED_OVERLAY_REPORT.md)
- [R25_1_ADVANCED_GEOMETRY_CONTEXT_IME_REPORT](docs/R25_1_ADVANCED_GEOMETRY_CONTEXT_IME_REPORT.md)
- [R25_1_READER_MORE_SINGLE_SURFACE_ARCHITECTURE_REPORT](docs/R25_1_READER_MORE_SINGLE_SURFACE_ARCHITECTURE_REPORT.md)
- [R25_2_1_ADVANCED_COORDINATE_SPACE_RESTORE_REPORT](docs/R25_2_1_ADVANCED_COORDINATE_SPACE_RESTORE_REPORT.md)
- [R25_2_NESTED_CARD_INTERACTION_REPORT](docs/R25_2_NESTED_CARD_INTERACTION_REPORT.md)
- [R25_3_ANCHORED_NESTED_POPOVER_PHASE1_REPORT](docs/R25_3_ANCHORED_NESTED_POPOVER_PHASE1_REPORT.md)
- [R25_ADVANCED_FOREGROUND_CARD_UNIFICATION_REPORT](docs/R25_ADVANCED_FOREGROUND_CARD_UNIFICATION_REPORT.md)
- [R25_READER_CLOSURE_REPORT](docs/R25_READER_CLOSURE_REPORT.md)
- [R25_READER_MORE_ARCHITECTURE_RESET_REPORT](docs/R25_READER_MORE_ARCHITECTURE_RESET_REPORT.md)
- [R26_1_LIQUID_ONLY_ANCHOR_FIX_REPORT](docs/R26_1_LIQUID_ONLY_ANCHOR_FIX_REPORT.md)
- [R26_PDF_READER_PARENT_CHILD_POPOVER_REPORT](docs/R26_PDF_READER_PARENT_CHILD_POPOVER_REPORT.md)
- [READER_CHROME_LIQUID_UNIFIED_NAV_REPORT](docs/READER_CHROME_LIQUID_UNIFIED_NAV_REPORT.md)
- [READER_CHROME_MATERIAL_REPORT](docs/READER_CHROME_MATERIAL_REPORT.md)
- [READER_FLOW_POLISH_20260906](docs/READER_FLOW_POLISH_20260906.md)

### Markdown 表格、目录、资源与搜索

- [ISSUE_TOC_OUTLINE_UX_REDESIGN](docs/ISSUE_TOC_OUTLINE_UX_REDESIGN.md)
- [MARKDOWN_ANCHOR_DEVICE_ACCEPTANCE_REPORT](docs/MARKDOWN_ANCHOR_DEVICE_ACCEPTANCE_REPORT.md)
- [MARKLEAF_MARKDOWN_RESPONSIVE_TABLE_RENDERER_REPORT](docs/MARKLEAF_MARKDOWN_RESPONSIVE_TABLE_RENDERER_REPORT.md)
- [MARKLEAF_MARKDOWN_TABLE_CHATGPT_MOBILE_PARITY_REPORT](docs/MARKLEAF_MARKDOWN_TABLE_CHATGPT_MOBILE_PARITY_REPORT.md)
- [MARKLEAF_MARKDOWN_TABLE_CONTENT_DRIVEN_LAYOUT_V2_REPORT](docs/MARKLEAF_MARKDOWN_TABLE_CONTENT_DRIVEN_LAYOUT_V2_REPORT.md)
- [MARKLEAF_MARKDOWN_TOC_ANCHOR_AND_SCROLL_PERFORMANCE_REPORT](docs/MARKLEAF_MARKDOWN_TOC_ANCHOR_AND_SCROLL_PERFORMANCE_REPORT.md)
- [MARKLEAF_TABLE_CHATGPT_PARITY_R3_THREE_COLUMN_WIDTH_PRIORITY_REPORT](docs/MARKLEAF_TABLE_CHATGPT_PARITY_R3_THREE_COLUMN_WIDTH_PRIORITY_REPORT.md)
- [MARKLEAF_TABLE_HEADER_TYPOGRAPHY_AND_ALIGNMENT_PARITY_FIX_REPORT](docs/MARKLEAF_TABLE_HEADER_TYPOGRAPHY_AND_ALIGNMENT_PARITY_FIX_REPORT.md)
- [R29_SEARCH_FOUNDATION_REPORT](docs/R29_SEARCH_FOUNDATION_REPORT.md)
- [R30_SEARCH_EXPERIENCE_REPORT](docs/R30_SEARCH_EXPERIENCE_REPORT.md)
- [R8_MARKDOWN_RELATIVE_ASSET_V1_DEVICE_ACCEPTANCE_REPORT](docs/R8_MARKDOWN_RELATIVE_ASSET_V1_DEVICE_ACCEPTANCE_REPORT.md)
- [R8_MARKDOWN_RELATIVE_ASSET_V1_IMPLEMENTATION_REPORT](docs/R8_MARKDOWN_RELATIVE_ASSET_V1_IMPLEMENTATION_REPORT.md)
- [R8_MARKDOWN_RELATIVE_ASSET_V1_REVIEW_REPORT](docs/R8_MARKDOWN_RELATIVE_ASSET_V1_REVIEW_REPORT.md)
- [R9_MARKDOWN_IN_DOCUMENT_SEARCH_V1_DESIGN_AUDIT](docs/R9_MARKDOWN_IN_DOCUMENT_SEARCH_V1_DESIGN_AUDIT.md)
- [R9_MARKDOWN_IN_DOCUMENT_SEARCH_V1_P0_DEVICE_ACCEPTANCE_REPORT](docs/R9_MARKDOWN_IN_DOCUMENT_SEARCH_V1_P0_DEVICE_ACCEPTANCE_REPORT.md)
- [R9_MARKDOWN_IN_DOCUMENT_SEARCH_V1_P0_IMPLEMENTATION_REPORT](docs/R9_MARKDOWN_IN_DOCUMENT_SEARCH_V1_P0_IMPLEMENTATION_REPORT.md)
- [R9_MARKDOWN_IN_DOCUMENT_SEARCH_V1_P0_REVIEW_REPORT](docs/R9_MARKDOWN_IN_DOCUMENT_SEARCH_V1_P0_REVIEW_REPORT.md)
- [R9_MARKDOWN_SEARCH_MATCH_HIGHLIGHT_V1_DESIGN_AUDIT](docs/R9_MARKDOWN_SEARCH_MATCH_HIGHLIGHT_V1_DESIGN_AUDIT.md)
- [R9_MARKDOWN_SEARCH_MATCH_HIGHLIGHT_V1_IMPLEMENTATION_REPORT](docs/R9_MARKDOWN_SEARCH_MATCH_HIGHLIGHT_V1_IMPLEMENTATION_REPORT.md)
- [R9_MARKDOWN_SEARCH_MATCH_HIGHLIGHT_V1_REVIEW_REPORT](docs/R9_MARKDOWN_SEARCH_MATCH_HIGHLIGHT_V1_REVIEW_REPORT.md)
- [TABLE_HEADER_BODY_ALIGNMENT_FIX](docs/TABLE_HEADER_BODY_ALIGNMENT_FIX.md)

### PDF 手势、批注、编辑与保存

- [PDF_BODY_EDITING_DESIGN](docs/PDF_BODY_EDITING_DESIGN.md)
- [PDF_BODY_EDITOR_FIRST_LOOP](docs/PDF_BODY_EDITOR_FIRST_LOOP.md)
- [PDF_EDITOR_SAVE_AND_OVERWRITE](docs/PDF_EDITOR_SAVE_AND_OVERWRITE.md)
- [PDF_P0_1](docs/PDF_P0_1.md)
- [PDF_P0_2A](docs/PDF_P0_2A.md)
- [PDF_P0_2B](docs/PDF_P0_2B.md)
- [PDF_P0_3A](docs/PDF_P0_3A.md)
- [PDF_P0_3B](docs/PDF_P0_3B.md)
- [PDF_P0_4A](docs/PDF_P0_4A.md)
- [PDF_TEXT_HIGHLIGHT](docs/PDF_TEXT_HIGHLIGHT.md)
- [PDF_VIEWER_HANDOFF](docs/PDF_VIEWER_HANDOFF.md)
- [R23_READING_ASSETS_FOUNDATION_REPORT](docs/R23_READING_ASSETS_FOUNDATION_REPORT.md)
- [R24_ANNOTATION_ANCHOR_REPORT](docs/R24_ANNOTATION_ANCHOR_REPORT.md)
- [R25_SELECTION_FLOW_REPORT](docs/R25_SELECTION_FLOW_REPORT.md)
- [R26_1_PDF_HIGHLIGHT_UI_REPORT](docs/R26_1_PDF_HIGHLIGHT_UI_REPORT.md)
- [R26_2_ANNOTATION_MANAGEMENT_REPORT](docs/R26_2_ANNOTATION_MANAGEMENT_REPORT.md)
- [R27_NOTE_FOUNDATION_REPORT](docs/R27_NOTE_FOUNDATION_REPORT.md)
- [R28_ANNOTATION_AUDIT_REPORT](docs/R28_ANNOTATION_AUDIT_REPORT.md)
- [R7_CONTINUOUS_ENTRY_RESTORE_HANDOFF](docs/R7_CONTINUOUS_ENTRY_RESTORE_HANDOFF.md)
- [R7_CONTINUOUS_VIEWPORT_HANDOFF](docs/R7_CONTINUOUS_VIEWPORT_HANDOFF.md)
- [R7_H1_1_HORIZONTAL_ZOOM_BOUNDS_AND_RUBBER_BAND_REPORT](docs/R7_H1_1_HORIZONTAL_ZOOM_BOUNDS_AND_RUBBER_BAND_REPORT.md)
- [R7_H1_1A_TOP_ALIGN_AND_PINCH_X_BOUND_REBASE_REPORT](docs/R7_H1_1A_TOP_ALIGN_AND_PINCH_X_BOUND_REBASE_REPORT.md)
- [R7_H1_1B_UNIFY_ZOOM_PAN_PHYSICS_WITH_CONTINUOUS_MODE_REPORT](docs/R7_H1_1B_UNIFY_ZOOM_PAN_PHYSICS_WITH_CONTINUOUS_MODE_REPORT.md)
- [R7_H1_2_HORIZONTAL_PAGED_INTERACTION_POLICY_AND_AUTOMATED_ACCEPTANCE_REPORT](docs/R7_H1_2_HORIZONTAL_PAGED_INTERACTION_POLICY_AND_AUTOMATED_ACCEPTANCE_REPORT.md)
- [R7_H1_3_REAL_GESTURE_PATH_AND_RENDER_REGRESSION_RECOVERY_REPORT](docs/R7_H1_3_REAL_GESTURE_PATH_AND_RENDER_REGRESSION_RECOVERY_REPORT.md)
- [R7_H1_4_UNIFIED_GESTURE_CONTROLLER_AND_SEAMLESS_MODE_CONTINUITY_REPORT](docs/R7_H1_4_UNIFIED_GESTURE_CONTROLLER_AND_SEAMLESS_MODE_CONTINUITY_REPORT.md)
- [R7_H1_5_PLATFORM_SCROLL_PHYSICS_AND_SINGLE_OWNER_PAGE_TRANSITION_REPORT](docs/R7_H1_5_PLATFORM_SCROLL_PHYSICS_AND_SINGLE_OWNER_PAGE_TRANSITION_REPORT.md)
- [R7_H1_5_THREE_PAGE_STRIP_AND_CONTINUOUS_PAGE_TRANSITION_REPORT](docs/R7_H1_5_THREE_PAGE_STRIP_AND_CONTINUOUS_PAGE_TRANSITION_REPORT.md)
- [R7_H1_HORIZONTAL_ZOOM_PAN_PHYSICS_HANDOFF](docs/R7_H1_HORIZONTAL_ZOOM_PAN_PHYSICS_HANDOFF.md)
- [R7_P0B1_1_POINTER_EPOCH_SESSION_REPORT](docs/R7_P0B1_1_POINTER_EPOCH_SESSION_REPORT.md)
- [R7_P0C1_1_PAGE_AHEAD_PREFETCH_HANDOFF](docs/R7_P0C1_1_PAGE_AHEAD_PREFETCH_HANDOFF.md)
- [R7_P0C1_2_TILE_GEOMETRY_CORRUPTION_FIX_REPORT](docs/R7_P0C1_2_TILE_GEOMETRY_CORRUPTION_FIX_REPORT.md)
- [R7_P0C1_4_DECOUPLE_GESTURE_AND_RENDER_PIPELINE_REPORT](docs/R7_P0C1_4_DECOUPLE_GESTURE_AND_RENDER_PIPELINE_REPORT.md)
- [R7_P0C1_PREDICTIVE_TILE_RENDERING_HANDOFF](docs/R7_P0C1_PREDICTIVE_TILE_RENDERING_HANDOFF.md)
- [R7_TEST_CLASSIFICATION](docs/R7_TEST_CLASSIFICATION.md)

### EPUB、外部打开与兼容性

- [EPUB_SUPPORT_ARCHITECTURE_DESIGN](docs/EPUB_SUPPORT_ARCHITECTURE_DESIGN.md)
- [ISSUE_NETDISK_INTENT_COMPATIBILITY](docs/ISSUE_NETDISK_INTENT_COMPATIBILITY.md)
- [MARKLEAF_EXTERNAL_OPEN_ATOMIC_SESSION_SWITCH_HARDENING_REPORT](docs/MARKLEAF_EXTERNAL_OPEN_ATOMIC_SESSION_SWITCH_HARDENING_REPORT.md)
- [MARKLEAF_EXTERNAL_OPEN_GUIDED_HUMAN_ACCEPTANCE_REPORT](docs/MARKLEAF_EXTERNAL_OPEN_GUIDED_HUMAN_ACCEPTANCE_REPORT.md)
- [MARKLEAF_EXTERNAL_OPEN_HUMAN_ACCEPTANCE_PREPARE](docs/MARKLEAF_EXTERNAL_OPEN_HUMAN_ACCEPTANCE_PREPARE.md)
- [MARKLEAF_EXTERNAL_OPEN_SINGLE_ACTIVE_DOCUMENT_AND_METADATA_REPORT](docs/MARKLEAF_EXTERNAL_OPEN_SINGLE_ACTIVE_DOCUMENT_AND_METADATA_REPORT.md)
- [MARKLEAF_EXTERNAL_OPEN_SINGLE_ACTIVE_SESSION_IMPLEMENTATION_REPORT](docs/MARKLEAF_EXTERNAL_OPEN_SINGLE_ACTIVE_SESSION_IMPLEMENTATION_REPORT.md)

### 跨页面体验、性能与审查交叉核对

- [APP_WIDE_SCROLL_AND_MAIN_THREAD_STALL_ISOLATION_REPORT](docs/APP_WIDE_SCROLL_AND_MAIN_THREAD_STALL_ISOLATION_REPORT.md)
- [PROJECT_AUDIT_FIXES_20260923](docs/PROJECT_AUDIT_FIXES_20260923.md)
- [PROJECT_AUDIT_FIXES_20260930](docs/PROJECT_AUDIT_FIXES_20260930.md)
- [PROJECT_AUDIT_FIXES_20261002_WINDOWS](docs/PROJECT_AUDIT_FIXES_20261002_WINDOWS.md)
- [R31_ARCHITECTURE_AUDIT_REPORT](docs/R31_ARCHITECTURE_AUDIT_REPORT.md)
- [R32_3_GLASS_BACKDROP_TOPOLOGY_REPORT](docs/R32_3_GLASS_BACKDROP_TOPOLOGY_REPORT.md)
- [R32_4_GLASS_REACCEPTANCE_REPORT](docs/R32_4_GLASS_REACCEPTANCE_REPORT.md)
- [R32_RELEASE_HARDENING_REPORT](docs/R32_RELEASE_HARDENING_REPORT.md)
- [R9_COMPOSE_LIST_VS_PDF_PATH_MATRIX](docs/R9_COMPOSE_LIST_VS_PDF_PATH_MATRIX.md)
- [R9_COMPOSE_LIST_VS_PDF_SCROLL_ISOLATION_REPORT](docs/R9_COMPOSE_LIST_VS_PDF_SCROLL_ISOLATION_REPORT.md)
- [R9_FIRST_ENTRY_ITEM_COST_CLOSED_LOOP_REPORT](docs/R9_FIRST_ENTRY_ITEM_COST_CLOSED_LOOP_REPORT.md)
- [R9_FIRST_ENTRY_PERF_AB_DIAGNOSTIC](docs/R9_FIRST_ENTRY_PERF_AB_DIAGNOSTIC.md)
- [R9_MARKDOWN_P1_FRAME_PACING_RECOVERY_REPORT](docs/R9_MARKDOWN_P1_FRAME_PACING_RECOVERY_REPORT.md)
- [R9_MARKDOWN_SCROLL_CHROME_ADB_RETEST](docs/R9_MARKDOWN_SCROLL_CHROME_ADB_RETEST.md)
- [R9_MARKDOWN_SCROLL_CHROME_RECOVERY_REPORT](docs/R9_MARKDOWN_SCROLL_CHROME_RECOVERY_REPORT.md)
- [R9_MARKDOWN_SCROLL_PERFORMANCE_RECOVERY_REPORT](docs/R9_MARKDOWN_SCROLL_PERFORMANCE_RECOVERY_REPORT.md)
- [SCROLL_PERF](docs/SCROLL_PERF.md)

### 综合入口与历史反馈

- [README.md](README.md)
- [CHANGELOG.md](CHANGELOG.md)
- [docs/CURRENT_STATUS.md](docs/CURRENT_STATUS.md)
- [docs/ACCEPTANCE.md](docs/ACCEPTANCE.md)
- [docs/A2_VALIDATION_CLOSURE_DRAFT.md](docs/A2_VALIDATION_CLOSURE_DRAFT.md)
- [docs/B4_PENDING_ISSUES.md](docs/B4_PENDING_ISSUES.md)
- [docs/history/CURRENT_STATUS_202609_STAGE_LOG.md](docs/history/CURRENT_STATUS_202609_STAGE_LOG.md)
- [docs/history/CURRENT_STATUS_pre_windows_audit_20261002.md](docs/history/CURRENT_STATUS_pre_windows_audit_20261002.md)
- [docs/history/NEXT_AGENT_HANDOFF_R6.md](docs/history/NEXT_AGENT_HANDOFF_R6.md)
- [docs/history/r7.md](docs/history/r7.md)

### 交互审计与人工验收场景补充

- [R36_INTERACTION_ARCHITECTURE_AUDIT](docs/R36_INTERACTION_ARCHITECTURE_AUDIT.md) — 旧表面清单与未决运动问题；由后续修正覆盖，不作为当前实现清单。
- [Markdown 表格人工视觉验收夹具](app/src/test/resources/fixtures/markdown-table-human-acceptance.md) — 短 / 长两列、三列末端与纵向手势场景。
- [相对图片人工验收清单](testdata/r8_relative_asset_v1/HUMAN_ACCEPTANCE_CHECKLIST.md) — 授权边界、稳定失败占位与提示风暴场景；历史包型建议服从当前工程规则。

Markdown 顶栏：返回与更多是独立圆形玻璃，搜索 / 目录共用一个胶囊，组间 8dp；40dp 视觉尺寸 / 48dp 点击区域。标题和阅读进度保持普通前景文本。正文背景捕获与控件玻璃分层，图标不参与采样。顶栏高度、正文留白、收起距离和标题弹出位置共同随系统字号调整。参考 Apple Adopting Liquid Glass。

Markdown 滚动边缘参考 ScrollEdgeEffectStyle / UIScrollEdgeEffect：当前选用柔和模糊过渡，正文与固定顶栏发生重叠时 180ms 淡入，返回顶部时淡出；只应用到实际有浮动控件的顶部边缘，加载 / 错误页不显示。控件玻璃与滚动边缘是不同用途的层，保持单一正文采样来源。


## 底栏外观裁决（2026-10-04 iOS 通透度参考）

2026-10-04 用户要求恢复底栏折射和缩放，随后提供 Photos / Books 两段视频作为动效参考，并在 iOS / Google 对比图中明确选择左侧 iOS 的通透感。四个 Hub 入口保留悬浮胶囊、浅灰静止选中块、蓝色选中图标与文字及右侧独立圆形加号；底座与加号采样页面并应用共享 Dock 材质，不覆盖不透明底色。底座白 / 深色染色 alpha 为 0.18 / 0.26，高光为 0.24 / 0.28，阴影为 0.08 / 0.20；保留 8dp 模糊、12dp / 16dp 边缘折射及细密背景预过滤，让背景内容和颜色通过玻璃可见。

选中透镜按用户补充 RiseDiary 参考采样一条高度与透镜匹配的完整短底栏，包含玻璃边缘、圆弧和隔离的蓝色图标 / 标签，不能只采样底座内部材质；光学模式的蓝色只由移动透镜绘制，避免释放 / 取消过渡中重复高亮。用户补充 Books 底栏视频并指出上一轮按压放大偏小后，透镜按住时改为横向约 1.38 倍、纵向约 1.44 倍，采样图标 / 标签约 1.22 倍；这些是本项目适配尺度，不是对参考视频的精确测量值。触下即预览手指所在项，按压时灰色覆盖退去。拖动弹簧跟随手指，两方向都随速度横向拉伸 / 纵向收缩；松手提交最近入口并回收，取消回到原入口，按住时不提前改变页面。仅裁决 Hub 底栏，不改变阅读器与分段控件材质。手势区继续透明，页面内容绘制到底部，安全留白设在滚动尾部。


### EPUB 顶栏避让（2026-10-03）

分页正文上方始终预留同一条工具区，最少 56dp，超大系统字号随标题行高增高；正文再留 12dp 间距。中央点击只切换控件淡入 / 淡出，不改变分页视口。返回采用 40dp 圆形玻璃 / 48dp 点击区域；目录与设置共用一个玻璃组，书名保持普通前景文本。只捕获纸面与内容，玻璃控件是捕获层之外的兄弟节点，不采样自身。正文和页脚均不被顶栏遮挡；翻页后的自动隐藏、目录、设置、阅读锚点恢复沿用原行为。


### EPUB 章节与目录分页（2026-10-03）

文件边界、目录指向的有效章节 / 小节块以及未列入目录的一级章标题均从新页开始；普通未列入目录的二级及以下小标题继续随正文流排。目录与分页使用同一块锚点解析规则，目录标题页不混入前一节正文；失效 fragment 不伪装成文件首。章节页脚按当前逻辑节的页范围计数，而不是把整个 XHTML 文件视为同一章。分页变化保留原块 / 字符阅读锚点，顶栏固定避让和覆盖式翻页沿用现有规则。
