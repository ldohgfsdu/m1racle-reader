# Markdown 固定渐变顶栏与厚雾材质

2026-09-08；当前状态以 [CURRENT_STATUS.md](CURRENT_STATUS.md) 为准。

## 用户确认的范围

Markdown 收起保留纯渐变，展开使用通栏控件；展开与收起的背景范围必须一致。首页底栏和 PDF 悬浮控件统一为设置预览中选定的厚雾。

## 实现

- Markdown 文档单独由 `GlassBackdropSource` 捕获，顶栏为其兄弟节点，避免采样自身造成递归渲染。
- 渐变范围固定为状态栏 + 56dp 控件行 + 24dp 尾部。16dp 模糊、62% 阅读背景色与渐隐遮罩不读取折叠状态；使用离屏合成和 DstIn 遮罩，不依赖 Android 13 专属 shader。
- 仅标题和操作行随滚动淡出，最多上移 6dp。完全隐藏后移除控件的触摸和语义节点；背景无点击行为。正文顶部留白为固定值，不随展开改变。
- 顶栏为返回、标题/当前章节与进度、搜索、目录、更多；章节和进度复用现有阅读规则。阅读设置、书签、标注归入更多。搜索仍占相同的 56dp 行。
- 保留观察式滚动与反向抖动阈值。弹出菜单锁定控件，阻止未完成的收起动画继续隐藏菜单入口；搜索、目录、阅读设置和标注管理期间暂停滚动收起。
- Dock、PDF 控件、胶囊与小浮层共用厚雾：18dp 模糊，浅色白底 62%、深色黑底 58%，无镜片折射、无色散、无亮边；按压也不绘制零宽描边，避免 Android 将其当作细线。大展开面板仍为浅色 72% / 深色 78% 底色，避免大面积过透。
- 设置预览“厚雾 · 当前”直接使用全局配方，删除旧 B 对比项。参考研究见 [HyperChanger](HYPERCHANGER_BLUR_REFERENCE.md)。

## 验证

- implemented：上述代码已接入。
- automated_verified：QA 单测 1459 项，0 失败/错误/跳过；包含固定范围、隐藏触摸穿透、更多菜单锁定及搜索范围回归。Debug/Release 构建成功，生产签名与无 INTERNET 权限检查通过。
- Release：6,883,046 bytes；SHA-256 `347df9251334e8ff6da598470b4f2ef05f8d7b4869a538bcb1652f477e96c7bd`。已覆盖 `/storage/emulated/0/Download/m1racle-reader-release-latest.apk`，与构建产物摘要一致，附带 `.sha256`。Debug 基线 79,490,851 bytes，仅作构建验证。
- lintQa：本轮已执行，既有 Compose lint 与 Kotlin 分析 API 的 `KaFunctionCall` 类/接口不兼容导致分析失败，未跳过或降低检查；Release 的 lintVital 成功。日志 `lint.log`。
- device_verified：NOT_RUN。本轮一次 ADB 探测无在线设备，未声称实机触摸、模糊渲染或性能通过。
- pending：手机安装后检查深浅色正文经过渐变、慢拖/快速滚动、搜索/目录/更多切换、字体缩放、横屏，以及首页底栏与 PDF 大小浮层的厚雾观感。

证据目录：`.external_outbox/markdown-fixed-gradient/`。
