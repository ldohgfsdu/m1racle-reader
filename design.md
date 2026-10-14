# M1racle Reader 设计规范

2026-10-09用户最新修正：Markdown编辑等待 M1racle Note，本轮移除其导航入口；“长截图”仅指适配系统截图工具的滚动捕获，不增加应用内长截图、拼图或导出功能。

本文件统一项目的界面、排版、阅读交互与材质要求。产品范围遵循 [PRD](docs/PRD.md)，工程与交付遵循 [AGENTS.md](AGENTS.md)，已验证现状查 [CURRENT_STATUS](docs/CURRENT_STATUS.md)；用户当前指令优先。设计要求、代码现状、历史验收是三个不同概念，不能互相替代。

2026-10-09用户要求实现全部积压清单，新增编辑、文件处理和阅读方式的范围见[全清单验收](docs/FULL_BACKLOG_20261009.md)。滑动、连续和卷页共用EPUB文字测量与定位；PDF反色统一作用于预览及高清图块，批注覆盖层独立。文件操作先生成可核验结果，覆盖原文件沿用已授权目标校验及持久备份流程。

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
| 设置 | 阅读偏好与应用设置 | 阅读字体、文档工具与隐私说明；2026-10-13 用户取消液态玻璃工作台，材质直接用于实际界面 |
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
| Markdown 正文 | 16.5sp，Normal | 保留内联强调 |
| EPUB 正文 | 默认原书 CSS / 字体 | 自定义模式提供字号、行距、边距与字体覆盖，不额外给整段加粗 |
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

按用户 2026-10-07 最新指令，EPUB 翻页与阅读界面回退到43版，只增加字体支持。恢复43版原生文字分页、HorizontalPager、纸页覆盖、无振荡弹簧、固定物理圆角、四分之一下层跟随、整页书名与页脚同步移动，以及原位288dp / 窗口82%上限的目录 / 设置菜单。43版手势、阈值、快滑与按住 / 拉回取消规则保持原源码，不再用 Readium 或 PixelCopy 翻页替代。

正文采用43版有限块模型与同一 TextMeasurer 的测量 / 绘制结果。字体提供默认、内置宋体、系统字体，以及本地 TTF / OTF 导入、选择和移除；选择只改变正文 TextStyle，不改变翻页或菜单结构。外部字体复制到私有库，EPUB / Markdown 可用，PDF 保留原文档字体。重排和重开恢复原文字锚点，不能以旧页号定位；45–50版的 Locator 仅在开书时作一次必要数据转换，书库和 Room15不清空。

默认沉浸正文；中央点击显示控件，左右区域点击或横向拖动翻页。慢拖三分之一页阈值、快滑速度判断、长按拖动和动画中重新按住拉回均沿用43版。中央短慢拖、纵滑、拉回或取消不提交目标页；停稳后才保存。将来新增翻页形式只能独立增加，不能覆盖这套已迭代的形式。字体外的阅读界面细节按43版处理，不附带新排版模式。实现及验收见 [43版恢复与字体](docs/EPUB_RESTORE43_FONT_20261007.md)。

原生块模型的原书 CSS / 表格 / 内嵌字体能力恢复43版边界，不继承45–50版的原书浏览器排版结论。作者脚本、网络资源、DRM、固定版式、音视频不进入本轮支持范围。

纸页延伸至窗口四边。全屏沉浸参照 PDF：使用 DEFAULT 系统栏行为，隐藏原生状态栏、保留原生导航小白条；点出顶栏或显示设置不恢复状态栏，目录作为同窗口面板继续保持沉浸，退出阅读恢复普通系统栏。下拉状态栏后按 PDF 相同的两秒周期重新隐藏。正文、书名、页脚和控件避让不随系统栏显隐变化的安全区，切换不得重新分页。

顶部只保留一个稳定居中的书名，底部显示章节、章内页数与 SDK 全书进度。目录和设置由原右上胶囊在同窗口展开，右上锚点固定，始终只有原位置的一组分段按钮；最大宽 288dp 且不超过窗口82%，高度受窗口约束。面板以最终尺寸排版并揭露，不缩放文字；切换内容不改面板尺寸或位置。开关使用同一个展开进度，关闭时保留最后的内容和选中分段，遮罩拦截持续到收起完成；不因关闭设置而临时显示目录。正文 WebView 不进入 Compose 玻璃采样层。设置提供字号预设、行距滑杆、页边距与重置，修改时保持菜单；外部点击 / 返回关闭，不翻页。其他阅读器的设置行为保持。

本节为当前设计，替代旧原生分页和早期基本 CSS 子集方案。当前实现与验收边界见 [翻页、字体与菜单报告](docs/EPUB_FONT_TURN_20261006.md)，原书核心基线见 [原书排版报告](docs/EPUB_PUBLISHER_LAYOUT_20261006.md)。文内搜索、批注及其他规划不因引擎能力而自动进入产品范围。

## PDF 阅读与阅读控件

### 页面、手势与 Chrome

PDF 标题 / 页码胶囊保留原 Capsule 玻璃配方（4dp 模糊、8dp / 24dp 透镜、边缘高光及阴影）。底栏和分段滑块的静止轻灰 / 按压透镜使用独立 SelectionLens，不得覆盖 PDF 胶囊配方。

PDF 保留原始页面颜色；暗色主题改变外围画布与控件，不自动反转纸张。白纸上方浮层的可读性根据真实背景处理，不能只看应用深色开关。横向 FitPage 的页面按后续修正居中，不恢复早期无条件顶对齐。

连续与横向分页复用阅读锚点及缩放 / 平移的权威模型。合法平移跟手，只有越界剩余位移产生弹性；松手回弹可被新手势中断并从当前状态接续。双指到单指再到双指切换不跳锚点、不重置缩放；渲染 tile 更新不能推动内容位置。页间移动连贯，取消回到原页，不制造中间灰屏或未提交的假页码。各历史阶段自动测试通过不代表当前真人手感已通过。

阅读界面保留“正常 / 沉浸”两种有效模式，不恢复早期四模式。沉浸按真实系统能力处理状态栏，保留透明系统手势区；不能画假的系统栏或承诺控制 OEM 瞬时提示层。

Chrome 显隐跟随实际内容滚动方向，程序跳转、恢复位置、布局变化不伪装成用户滚动。缩放、选字、跳页、菜单和搜索期间避免自动收起正在使用的控件；隐藏后清除触摸与可访问节点。页码胶囊与返回 / 更多按钮各自负责自己的点击范围，不能整片顶部空白都触发跳页。

Markdown 顶栏使用一个固定的渐进模糊效果：实时采样正文，状态栏后最强，向正文连续减弱，下缘 32dp 柔和淡出。按钮显示或收起不改变模糊的高度、强度或下缘位置，仅前景控件独立淡出；正文布局不跳动，搜索沿用同一层。覆层与阅读背景同色并随深度减淡，保留内容色彩，禁止灰 / 黑色装饰遮罩残留正文。Hub 悬浮底栏是独立场景，禁止恢复整片底部模糊铺层。

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
2. 保留四个入口及导航状态的单一所有者；点击、拖动预览、释放和取消的视觉状态必须与导航结果一致。采用项目自己的 OpticalDockTabs，直接使用 Kyant Backdrop API 与 PDF 共用的材质渲染器；不再使用 RiseDiary 来源实现。基准高度56dp、图标24dp、统一实心，选中使用应用蓝色，标签12sp；大字号按实际文字测量高度增加留白，页面尾部避让同步增加。加号位于同一胶囊中央，左右各两个入口；中央蓝色按钮触发导入，不属于导航选项，拖动松手落在中央时归回当前入口。
3. 静止选中态低调清晰；按压反馈及时，透镜与图标可以适度放大、折射、形变。释放与取消必须复原，不能残留暗罩、高光或放大。
4. 形变应有界，不裁切标签，不产生重影、透镜外的残余图标或底部碎片；系统大字号下仍保证触摸与阅读空间。
5. 四页在相同主题和相同背景条件下，材质必须一致。页面切换本身不得让底栏意外变灰；真实背景内容变化可以合理影响材质。
6. 采样必须包含实际的不透明页面背景与内容，不能把透明空像素当成页面颜色。去掉覆盖玻璃的装饰背景，不等于去掉采样中的内容底色。
7. 采样来源与玻璃呈现是不同职责。捕获层不得采样自身；底栏采样实际页面；交互透镜读取处理后的静态底栏表面，叠加隔离的蓝色图标 / 标签采样条，该采样条不包含透镜自身，不接收点击且不重复暴露语义。

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

2026-10-13 用户提供 iOS App Store 原生录屏并指定“新标准”，随后补充夜间录屏。Dock 材质、按压透镜、端帽、局部图文变色及拖动 / 释放优先按 [日间 / 夜间参考标准](docs/IOS_MATERIAL_STANDARD_20261013.md)分别验收；旧阶段参数只作已有实现基线，不能代替新视频对照。参考覆盖深浅色不构成 Reader 材质匹配或物理手机验收。

2026-10-13 用户明确取消工作台：“不要这个工作台，我们直接取iOS的材质现在”。此决定取代 2026-10-04 新增独立参数预览页的要求：删除工作台页面、路由、设置入口、参数及预览实现，QA 也不保留此功能。iOS 材质参考直接用于 Dock 和阅读控件，继续由现有统一材质引擎提供 Android 实现，不引入第二套预览材质或用户调参入口。QA 现有材质对比及变体隔离能力不属于被取消的工作台。

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

2026-10-14 按用户提供的 iOS「显示与亮度」日夜视频适配：状态栏后保持最强模糊，向正文连续减弱，顶栏下方衔接范围为 32dp；按钮显示或收起不改变模糊的高度、强度或下缘位置，只有前景按钮独立淡出。减淡页面色覆盖并随深度退去，保留背后内容色彩；标题与按钮保持独立清晰前景。屏幕边界用实际页面底色补足，禁止拉伸第一行笔画或透入清晰底图。实现与验证见 [Markdown 顶栏渐进模糊](docs/MARKDOWN_PROGRESSIVE_CHROME_20261014.md)。

2026-10-14 用户否定76过渡自然度后，77完整光学路径改由平台原生高斯生成同一份正文的不同尺度，以连续权重衔接；移除自算变半径双向核。下缘回到原始正文，不以二次透明遮罩叠回清晰底图；原生滤波保留足够的尾部区域和整数像素对齐。固定背景与独立前景语义保持。API31–32既有平台降级不扩大验收，本轮设备证据为API35，77手机及用户最终观感待验。见 [77连续过渡报告](docs/MARKDOWN_TOPBAR_NATIVE_TRANSITION_20261014.md)。


## 底栏与阅读工具栏裁决（2026-10-06）

用户明确要求直接采用本地 RiseDiary 底栏。生产组件由该参考的 LiquidBottomTabs、LiquidBottomTab、LiquidGlassButton 和拖动 / 高光辅助类移植，保留原版权头、GPL-3.0 与 Apache-2.0 许可证。持续曲线胶囊使用维护中的 shapes 库；四入口保留，导入按钮按2026-10-08用户要求并入胶囊中央。随后用户要求按酷安真机采样复刻：56dp作为基准，系统大字号按实际文字测量增加高度与滚动尾部避让。导航状态是选中项的唯一事实源，拖动位置直接跟手，只在松手提交一次，取消或拒绝导航后恢复原入口。

阅读器退出不使用 NavHost 默认交叉淡化；底栏等可见阅读页真正退出后才恢复，防止主页玻璃采样仍在退出的阅读正文。Hub 内短淡入 / 位移保留。工作台已按 2026-10-13 用户决定取消，不再作为设置子页或发布验收要求。

EPUB 返回按钮独立靠前，目录 / 设置共用靠后的胶囊。面板上限 288dp 和窗口 82%，高上限 520dp；原位置的一组图标兼作等宽分段控制，共用轨道与单个移动选中块，选中段不再次关闭菜单。切换只改变面板内容，文字不缩放，目录 / 设置图标坐标保持；“关闭”、外侧或系统返回收起。原纸面分页与阅读位置不因展开改变。参考 [Apple 分段控制](https://developer.apple.com/cn/design/human-interface-guidelines/segmented-controls) 与 [工具栏](https://developer.apple.com/cn/design/human-interface-guidelines/toolbars)。

### EPUB 顶栏避让（2026-10-03）

分页正文上方始终预留同一条工具区，最少 56dp，超大系统字号随标题行高增高；正文再留 12dp 间距。中央点击只切换控件淡入 / 淡出，不改变分页视口。返回采用48dp圆形玻璃与点击区域，按压放大、提亮并回弹；目录与设置共用一个玻璃组及按压反馈，书名保持普通前景文本。只捕获纸面与内容，玻璃控件是捕获层之外的兄弟节点，不采样自身。正文和页脚均不被顶栏遮挡；翻页后的自动隐藏、目录、设置、阅读锚点恢复沿用原行为。


### EPUB 章节与目录分页（2026-10-05 修正）

文件边界和一级章标题从新页开始。目录导航目标不再强制分页：二级及以下标题随正文流排，连续标题在页面可容纳时与后文首行一起排，避免只有“01”或章节名的空页。目录仍按真实块锚点定位到包含目标的页面；失效 fragment 不伪装成文件首。章节页脚按文件边界 / 一级标题的范围计数。保留 XHTML 显式换行，不统一强加两字首行缩进；正文维持 Normal 字重。分页变化保留原块 / 字符阅读锚点，顶栏固定避让沿用现有规则；翻页以本文件 EPUB 阅读章节中的 2026-10-05 视频修正为准。此项依据用户原书和 Apple Books 对照反馈，取代 2026-10-03 将所有目录小节另起页的规则；不代表支持完整出版方 CSS。


## Dock 视频样式裁决（2026-10-08）

按用户视频与截图采用一体玻璃胶囊：首页、文库、中央导入、书签、设置。保留 M1racle 蓝色（浅色 #007AFF、深色 #64A8FF）。按随后授权的酷安真机采样调整：底栏56dp、左右20dp，静止透镜48dp、按住67dp；速度控制对称横向拉伸与纵向压缩，释放弹簧归位。整条材料按压略扩张，命中区域与拖动坐标稳定。中央按钮保持白色加号与蓝色填充，单独放大与提亮。屏幕下缘采用轻量随主题渐淡，依据日间和深色滚动采样，不引入不透明底板。底栏真实采样页面，选中透镜只采样不含自身的隔离底栏层。阅读控件按用户补充范围采用动作组与独立按钮各自的扩张反馈，不引入酷安业务功能。依据见[采样报告](docs/COOLAPK_DOCK_CAPTURE_20261008.md)，本轮实现与验证见[复刻报告](docs/COOLAPK_GLASS_REPLICA_20261008.md)。


## 2026-10-11 用户修正：移除 RiseDiary，统一 PDF 玻璃材质

本节优先于前述历史底栏参数。用户明确要求不使用 RiseDiary 的东西，玻璃材质统一为现有 PDF 控件样式，底层使用 Kyant0/AndroidLiquidGlass。五个 GPL 来源文件删除，不能通过改名或改授权冒充来源移除。

底栏和 PDF 小控件共享同一套材质参数：日间白色 .32、夜间 #242424 .22、4dp模糊、8dp/24dp折射、同一高光与阴影。较大的菜单使用 PDF 已有可读性角色。正文与状态栏渐淡仍按内容层职责处理，不给正文铺玻璃。

底栏保留56dp基础布局和24dp图标，大字号自适应；外壳与真实命中区域固定。镜片按压幅度读取 PDF Control 已解析的比例，当前生产基线1.085，取代历史48→67dp及外壳5%扩张。保留速度形变、直接跟手、松手单次提交、取消回位与独立导入动作。

采样规则由下方64版裁决取代：页面root → 真实可见Dock及其唯一前景的捕获层 → sibling镜片。禁止另造静态底栏或放大前景充当来源。历史实现见[材质统一记录](docs/OPTICAL_GLASS_20261011.md)。

### 2026-10-11 后续用户选择：底栏拉伸

用户在检测到并发修改后明确选择“保留刚修改的底栏拉伸效果”：OpticalDockTabs 镜片按压横向1.18、纵向1.38，48dp静止高度到66.24dp；外壳和真实命中区固定。此选择覆盖上文底栏镜片1.085的运动幅度描述，玻璃材质仍与PDF Control同配方，独立PDF/导入控件1.085不变。

## 底栏留白与加号形态裁决（2026-10-11，60版）

用户追加要求参考原视频底栏留白、酷安加号形态：常规底栏60dp，选中透镜48dp，四周内容内缩6dp；按压外扩及内部底栏折射保留。中央导入采用52×38dp横向圆角胶囊和圆头加号，保留Reader蓝色。图标行、透镜和拖动分区共同使用内缩后的宽度，不能只移动绘制而保留旧分区计算。此条覆盖此前56dp栏高、首尾透镜无横向内缩和49×48dp近圆加号的尺寸裁决，字体放大时继续按文字测量扩高。


## 底栏通透感裁决（2026-10-11，61版）

用户要求继续对照酷安改善通透感。Dock使用6dp背景模糊；浅色中性覆盖由40%降至24%，深色保持40%覆盖但由#121212改为纯黑，避免抬高暗部；静止选中透镜中性覆盖由10%降至6%。按压透镜仍无填色，清晰前景、60dp栏高及6dp留白保持。覆盖此前58版8dp/40%和静止10%的底栏参数裁决，PDF Control/Capsule不变。另造内部采样栏的旧路径已由64版删除。

## 按压透镜裁决（2026-10-11，62版）

按用户新提供的酷安日夜视频，按压不允许整栏白色叠加或整栏底色加厚。仅手指附近使用局部透明高光，保持远端底栏颜色。真实可见Dock负责材质处理，外透镜仅折射、色散、边缘与阴影，不重复增艳；保留61版模糊/透明度与60版尺寸。62版使用虚构来源的实现未满足参考要求，不能视为视觉验收通过。

## 真实Dock捕获裁决（2026-10-11，64版）

用户指出酷安透镜渲染的是实际Dock，不能在透镜中造一条较矮底栏并另行放大图标。删除隐藏来源、DockLensContent角色及1.2倍前景。实际Dock材质、图标、文字和导入仅绘制一次，一起捕获供兄弟透镜取样，透镜绝不进入自己的来源。捕获范围包含真实栏外缘的小幅伸展与阴影，命中布局不变；底栏只小幅伸展，不能靠伪造来源制造整体大幅放大。实际前景强调色随透镜按住及回程位置变化，导航仍只在松手提交。按压镜片边缘折射4dp/6dp、阴影3dp；视觉一致性必须以日夜真机录屏核对，测试通过不代替视觉验收。

## 酷安日夜参考底栏（2026-10-12，65版）

根据本轮两段视频及三张截图，移除胶囊外部的整页底部渐变遮罩，让页面颜色直接延伸到系统手势区。整栏静止时只做6dp模糊和浅24%/深黑40%覆盖，折射为0；按住后以同一交互进度渐变到24dp/24dp边缘折射，高光由28%增强至70%，松手回到静止态。覆盖64版整栏持续24dp/24dp折射及60%静止高光参数。保留真实Dock单份捕获、镜片78/56拾起比例、16dp整栏增宽、4dp跟随、速度形变和松手提交。图标和四入口沿用阅读器语义及蓝色强调色，选中文字加重；中央加号使用横向蓝色渐变，取消常驻顶部白色亮带与硬边。详见[本轮实现与验证](docs/DOCK_REFERENCE_20261012.md)。

## 镜片内部轮廓修正（2026-10-12，66版候选）

用户红圈指出65版缺少大镜片内部的真实底栏轮廓。镜片垂直居中，常规高度由48dp改为56dp，按住至78dp、横向1.6倍；底栏60dp和横向6dp内缩保持。DockLens使用10dp/4dp折射和8dp内阴影，让折射边的采样包含真实底栏外部，内部才能看见与外镜片分开的弯曲轮廓。紧凑分段控件SelectionLens保持4dp/6dp。真实Dock及其唯一前景仍只捕获一次，不新增隐藏较矮栏或假描边；验收必须观察有纹理背景下的日夜按住画面，自动检查不代表完整外观被接受。覆盖64版底栏镜片48→66.86dp、4dp/6dp及镜片横向78/56的参数裁决；[本轮验证](docs/DOCK_INNER_CONTOUR_20261012.md)。

## 酷安尺寸与动态透镜裁决（2026-10-12，67版候选）

用户指出66版偏大，强调镜片随速度形变、可外扩覆盖真实栏边或压扁缩进栏内，且图标只有被镜片覆盖的部分立即变色，边缘保留折射色散。本条覆盖66版几何和此前按入口整块切色的实现要求。参考RiseDiary的独立拾起、速度形变、整栏随动与回弹结构，尺寸以酷安实测为准：常规底栏56dp，镜片48→67dp、横向1.5倍，中央导入50×36dp；20dp屏幕边距、6dp内容内缩、24dp图标、12sp标签及大字号扩高保持。

四份可见图标/标签各自记录真实像素，镜片输入对同一像素局部染色并与实际Dock合成；外部前景保持中性色，不再按预览入口取整切换整枚图标颜色。可见Row仍一份，不复制隐藏较矮底栏、不同尺寸前景或假内边缘。中央导入不参加图标染色。速度输入为每秒槽宽/20，形变弹簧阻尼.5/刚度600；快速移动可将镜片压扁到真实栏内，停止后外扩，松手回弹并提交导航，取消恢复owner。命中布局保持稳定。

DockLens在既有共享引擎中对真实采样执行10dp/2dp宽边折射及1.5dp/8dp窄边光谱折射，内阴影4dp、按压白色高光.7；两次都使用Kyant原生效果，不补画彩虹。紧凑SelectionLens仍4dp/6dp。整数居中的半像素差通过采样感知的layerBlock补偿。完整参考外观和手感仍需用户及物理手机验收，模拟器测试不替代该判断。来源、失败记录和当前产物见[本轮报告](docs/DOCK_RISEDIARY_MOTION_20261012.md)。

## 透镜内底栏与端帽包覆（2026-10-12，68版候选）

用户指出67版透镜内底栏过满，左右端帽未被完整包住。本条覆盖67版将扩张后的整条Dock直接原比例采入透镜的方式；保持67版镜片、外部底栏和命中几何。在透镜对实际材料的采样中抵消整栏拾起放大，纵向随按压渐变至静止镜片高度/栏高，常规按住时材料光学高度48dp。支点为真实底栏中心在镜片逆变换坐标中的位置，让内部底栏恢复上下留白，左右端帽都处于镜片内部。外部底栏仍伸展16dp并随动，透镜形变和色散保持。

真实材料与唯一Row中的四组图标/标签、中央导入各捕获一次，仅材料的光学采样做此比例修正，前景仍保持原尺寸并按覆盖区域染色；中央导入按原色采样。禁止隐藏替代栏、重复前景或补画内轮廓。验收分别核对浅色/深色左右端帽距镜片边缘的独立留白，不能只以“内部存在亮边”判断包覆正确。来源和验证见[本轮报告](docs/DOCK_CAP_FIT_20261012.md)。

## 放大视频重新对照（2026-10-12，69版候选）

用户补充放大录屏要求仔细核对。本条覆盖67版双折射层、24dp/12sp前景及68版48dp材料光学高度和不放大前景的描述。正常实际图标22dp、标签11sp，图文viewbox间距-1dp以抵消CJK内部留白，实际墨迹不重叠；大字号仍按实际测量扩高。按压镜片输入将四组真实图标/标签分别围绕实际中心从1倍放至1.2倍，并染为Reader蓝色，只有镜片覆盖部分显示该变换；外部实际前景尺寸与原色保持。中央导入保留实际原色及自身尺寸，不参与此变换。真实可见Row和每份捕获仍各只有一份，不能恢复隐藏替代前景。

DockLens改为Kyant单次10dp/14dp宽折射与色散，删除第二窄光谱层；SelectionLens仍4dp/6dp。删除会把黑背景抬灰的局部白色叠加及其辅助类，按压透镜清晰中心不填色；共享边缘高光仍保持。材料光学采样抵消整栏拾起，并在满按压时收至47dp，让真实边线进入宽折射采样范围而不与最远采样点相切。真实内边缘随形变折回，不补画等高内胶囊或假光谱。

56dp外栏、48→67dp镜片、横向1.5倍拾起、16dp整栏伸展、4dp随动、速度压扁/停止外扩和回弹、固定命中区、松手单次提交及取消保持。几何使用原真机测量，放大录屏只用于相对比例与动态关系，不能把视频像素直接当dp。正常按住文字可读性、左右端帽独立留白、局部切色及真实色散分别验证；快速形变的边缘折回不能由静态截图代替。完整观感及物理手机手感仍待验收，见[69版报告](docs/DOCK_VIDEO_RECHECK_20261012.md)。

## 底栏面板使用PDF控件材质（2026-10-12，70版候选）

用户认可69版透镜效果，指出按压后面板泛白，要求改用PDF控件材质试效果。本条覆盖65版以来Dock的独立6dp/24%或40%材质及按压0→24dp折射、.28→.70高光。Dock直接与PDF Control/Capsule共享4dp模糊、日间白32%/夜间#242424 22%、8dp/24dp折射、.60高光和同样阴影；按住与静止保持这组材质端点，不把PDF独立按钮的整体提亮复制到整条底栏。整栏伸展和随动仍由既有几何负责。

69版透镜的10dp/14dp色散、局部图文放大、材料采样留白、尺寸、形变、回弹、固定命中区与导航保持；中央导入和PDF自身按压反馈保持。只做此材质候选，不重新调透镜参数。验收既核对日夜整块面板按压前后稳定，也与同场景实际PDF Control透色量对照，不能只看远端中心单点。70版材质观感仍待用户评价，见[本轮报告](docs/DOCK_PDF_MATERIAL_20261012.md)。
# 2026-10-12 用户修正：透镜细色散与比例

在PDF控件面板材质之上，按用户新提供的五组酷安/Reader截图收敛静止/按住时的强彩带。色散与折射位移相联系，不制造速度开关或静止颜色动画；透镜内真实底栏应等比缩小，不能只压纵轴。上下折射带适度加宽，外透镜按截图微调，保留动态形变、局部切色、真实前景放大和输入回弹。本次授权覆盖此前只保留69版透镜不动的局部约束。

## iOS 日间 / 夜间 Dock 适配（2026-10-13）

用户在提供两段 iOS App Store 新参考后要求“完成适配”。本条覆盖 70 版 Dock 与 PDF 控件底色 / 高光必须完全相等的局部要求：保留统一渲染引擎、背景透过比例和光学处理，由 Dock 角色定义日夜底色、细轮廓及紧凑选中层级。夜间在纯黑上仍有炭灰轨道和稍亮的灰色选中胶囊，选中图文为明亮蓝色；按住后选中叠色收为透明，保持实际轨道透过，整栏不发白。浅色真实端帽必须保持可辨的细轮廓，色散保持窄而连续。

四入口、中央导入动作、真实前景局部变色 / 放大、等比采样、两端空间、双向拖动及释放 / 取消继续复验。PDF Control / Capsule 和其他 SelectionLens 的配方独立保留。参考定义见 [日夜标准](docs/IOS_MATERIAL_STANDARD_20261013.md)，实现与验证见 [适配报告](docs/DOCK_IOS_ADAPTATION_20261013.md)；物理手机手感须单独验收。

## 折射、色散与点击 Tab 修正（2026-10-13）

用户明确指出 72 版“折射和色散没有做到位”，并补充点击其他 Tab 的页面切换动画缺失。折射必须实际折回真实轨道及背景边线，色散在上下中点与端部也连续可见，同时保持细窄范围及中央图文清晰。实际采样仍等比缩小，保留两端空间，不补画内轨道或彩虹。DockLens 的光学采样可针对该目标修正，PDF 与紧凑 SelectionLens 不受本次调节影响。

点击未选中入口时，透镜从当前实际位置连续移动到目标，短点击释放后从当前帧续接，不能在按下或抬起时瞬移。按住拖动在拾起结束后继续跟手；松手仅提交一次、取消回到原项。页面按 Dock 顺序短距离滑动并淡入淡出，返回首页也检查；Dock 本体保持在页面转场之外。具体实现、失败记录与验证边界见 [光学与点击动画报告](docs/DOCK_OPTICS_20261013.md)。
