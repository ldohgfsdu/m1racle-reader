# Reader 87 首页优化与 Note 图标占比

2026-10-29 发起，2026-10-30 收尾。唯一当前交付为 Release 1.0.0 / 87，Room17。当前开发、构建及交付均来自 reader-ui-release-20261025。

## 实现

- 首页左上改为原有纯 M 母标配一次 Reader，保持 24dp 内容轨道、固定标题与搜索。
- 继续阅读单独显示，最近文档最多五条且不重复继续阅读。上限在状态层执行，乐观移除补位、失败恢复原顺序；文库及持久数据完整保留。溢出才提供“查看全部”，进入文库时恢复全部格式，避免沿用 PDF 筛选。
- 底栏导入采用不透明 Radix Blue 9，移除原透明渐变；深色选中前景用 Blue Dark 10。选中白色叠层从 12% 降为 8%，实际图文对比度从约 4.29 升至 4.71，浅色场景约 4.55。语义角色参照 [Radix 色阶用途](https://www.radix-ui.com/colors/docs/palette-composition/understanding-the-scale)。
- 原生常规字号 12 / 14 / 18sp，设置品牌保留 28sp，共四种；阅读正文及语义标题独立。设置页删除重复副说明，保留当前字体等有效状态。文库窄筛选位采用常见简称 MD，避免 Markdown 被拆成两行。
- 原生保留物理 0.5px 边界，WebView 按设备像素比换算；代码块移除装饰左线。内嵌编辑器从原生同一 Radix 色板生成 CSS，覆盖链接、选择、语法色和复选框，并将色板纳入构建输入与资源新鲜度校验。
- 底栏必要反馈为 250ms ease-out，导入按压缩为 4%；保持跟手、反向、取消和单次导航提交。取消回位不再因状态回写重复启动；搜索与返回移除装饰缩放。

## 图标

按用户追加要求对照本机 Note Android 正式资源：此前 Reader 将品牌画板再次缩到 64dp，现与 Note 一致，采用 108dp 图层中的 72dp 居中画板。外层缩放 0.25 → 0.28125，位移 22 → 18，整体放大 12.5%。

母标与 Reader 六个字形共七条原路径逐字节保留，没有重画标识或更换字体。母标宽约 51.7923dp，与 Note 相同；每段曲线 512 份采样的最大半径约 32.1598dp，小于 33dp。此为采样与渲染证据，不称解析极值证明。前景不烘焙白底或外形，由系统裁切，参照 [Android 自适应图标规范](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive)。

APK 编译资源另查到 0.28125 缩放及 18dp 位移；设置和首页母标向量与 86 字节一致。Note 源文件只读；当前模拟器未安装 Note，对照采用其本机 Android 向量及等尺寸预览，Reader 则检查真实系统启动器及点击启动。

## 验证与同源边界

| 检查 | 结果 |
|---|---|
| 最终 87 完整 Release 单测 | 1686 项，0 failures / 0 errors，2 项既有环境跳过 |
| 最终 lintRelease | 0 errors / 179 warnings / 6 hints |
| 最终包 | assembleRelease，R8 / shrinkResources，正式生产签名与 16KiB 对齐通过 |
| 包内容 | 35 个主 assets 与冻结源码逐字节一致，无 INTERNET，非 debuggable |
| 图标 | 原七条路径保留、几何与编译资源检查、真实启动器与启动检查通过 |
| 86 界面阶段完整 QA | 1843 项，0 failures / 0 errors，2 项既有环境跳过 |
| 86 界面阶段原生专项 | 10/10，覆盖深浅主题、320dp / 1.5 倍字体、尾项避让、真实蓝色及对比度、筛选重置、局部染色 / 放大、黑底轨道与 QA 隔离 |
| 编辑器 | 9 项源文件测试与浏览器流程通过，覆盖 BOM / 混合换行、单实例、选区和撤销重做、组合输入保护及离线边界 |

87 相对已验证的 86，仅图标生成物、版本信息和文库筛选简称等九个文件不同；主 Kotlin 及编辑器源 / assets 的摘要一致。最终 87 的完整 Release 测试、构建与交付来自同一份冻结源码，没有把 86 的 QA 或原生计数当成 87 新跑。各阶段分别保留源码 ZIP 和 SHA-256 表。86 追加色板构建输入声明后重执行完整命令，同输入编译 / 测试缓存复用单独记录。

跳过仍为 Laozi 缺失夹具及 Windows symlink 环境项，未新增跳过或放宽断言容差。首轮 JVM 曾因宿主内存不足退出，已降低并发、暂关模拟器重跑；旧日志与修正前对比度画面保留。未交付的 87 筛选文案候选保存在 before-filter-label，当前文件以本报告摘要为准。

## 交付及模拟器

- 唯一当前文件：D:/03_M1racle Reader/apk/M1racle-Reader-1.0.0-87.apk。
- 大小：27605878 字节，26.33 MiB。
- SHA-256：1F7D32B36DF7A8A8EF24F7F7628CB1B5F4EE717FA95A8B1ED382CC15A25DBD78。
- 生产证书 SHA-256：2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1。
- 构建、Outbox、交付、已安装 base.apk 与模拟器 Download 摘要相同。保留数据覆盖安装，首次安装时间未变，旧文库和继续阅读仍在。
- 当前正式包检查：首页五条预览及查看全部，先选 PDF 再查看全部恢复全部类型，MD 单行筛选，深浅主题，设置品牌，真实启动器图标与点击启动。恢复模拟器时出现过 System UI ANR，选择等待后恢复；不是 Reader 崩溃，失败画面不作验收，稳定窗口和 Reader crash buffer 另存。
- 物理手机未连接；手机 Download / 安装、物理输入法与用户视觉 / 手感验收 pending。85 扩展原生中的六项既有失败仍待独立核查，本轮不关闭该待办。

状态：implemented / automated_verified / emulator_verified，READY_WITH_LIMITATIONS。未提交、未推送、未公开发布。两个原 Reader 工作树保留。

证据目录 D:/03_M1racle Reader/output/home-polish-20261029，最终 87 在 v87。图标对照为 icon-comparison.png，正式启动器为 launcher-apps.png，正式界面为 release-home-dark.png 等。资源预览与真实模拟器窗口分别标注。
