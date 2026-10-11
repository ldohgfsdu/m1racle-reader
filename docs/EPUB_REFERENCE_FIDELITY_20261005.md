# EPUB 滑动细节与阅读界面修复

## 用户范围（逐条，不合并漏项）

1. 下层页跟随滑动。
2. 深色原纸面随离开原页的距离逐渐变亮。
3. 按设备四角圆角适配。
4. PDF 全屏方式保留原生导航条。
5. 控件平滑出现，顶部单份书名。
6. 显示设置按按钮锚点展开，修改 / 重置 / 关闭符合标准菜单交互。

## 根因与实现

旧版抵消下层纸页的全部 Pager 位移；旧提亮规则在 6% 距离已到上限。视频抽帧追踪插图右边缘：帧 95 / 105 / 120 从 717 → 702 → 681px，停稳后 653px；空白纸面采样 16 → 22 → 26 → 31，沿纸面横向近乎均匀，变化发生在手势进度，不是随按下立即到上限。`output/epub-reference-fidelity/reference-measurements.json` 保留原帧结果。四分之一跟随符合该量级，不声明与 Apple Books 的曲线 / 像素完全一致。

新下层保留四分之一位移。亮度按连续的 1−(1−distance)² 进度变化；只影响原页背景，前后翻及松手均保留已停稳 readingPage 作为身份。颜色使用 Compose 色空间插值，不能用简单 RGB 算术比较。

圆角读取 API31+ WindowInsets 的四个物理像素半径；无圆角 / 未上报返回零，AbsoluteRoundedCornerShape 保持物理角顺序，RTL 不交换屏幕左右，密度不再次放大。布局或设备配置变化重新读取，不替换 Compose inset 监听器。

系统栏采用 DEFAULT，阅读与菜单期间 show(navigationBars) / hide(statusBars)，失焦返回与两秒检查恢复隐藏；目录显示双栏，退出恢复。顶部使用一份纸页书名，工具栏只提供按钮；按钮滑入淡入、章进度淡入，固定正文区域与页脚测量不变。设置采用 Material3 DropdownMenu 的锚点、尺寸约束、展开动画与关闭处理，复用原显示设置内容，原字号 / 行高 / 边距 / 重置存储路径保留。

## 验证状态

实施、自动化与正式包 Android 模拟器验证已完成；真实手机未连接，实机验收待完成。本轮交付版本为 37。

测试环境与修正记录：首次编译发现页脚两个旧 chromeVisible 引用，修正为同一 chromeProgress；旧 EPUB 回归随后通过。新增 Native 测试首次发现色空间预期、目录屏幕外节点、运行时形状更新帧，以及 host 主题覆盖玻璃能力导致 SkSL 不支持。修正测试取样语义和 host 能力注入，保持精确像素 / 几何断言，不扩大容差；正式 APK 的光学玻璃将用 Android 实际渲染核验。

真实手机没有连接。本轮实际 Android 运行证据只能标记模拟器，不宣称手机手感、厂商圆角上报或全性能通过。此前严格整页像素零差异诊断失败不冒充已解决；本轮按六条新要求验收。

新增五项 Native 回归已在第七轮全通过：动态半径的早期失败来自冻结 host 帧时钟后尚未完成绘制的取样；允许状态变更完成绘制后，24→44px 的相同角点由纸色变为下层色，正文坐标不变。保留精确断言，没有放宽色差。菜单采用实际 WindowManager.LayoutParams.y 核验锚点，因为 host 的 Popup 语义坐标原点与阅读窗口不同；字号、行高、边距、重置与菜单保持 / 关闭均通过。分段控件将原来实际测得的文字 / 触控高度移到 BoxWithConstraints 外，修正 DropdownMenu intrinsic 查询的真实崩溃，字体缩放仍参与测量。


## 最终逐条验收（37 正式包）

| 用户要求 | 实际 Android 证据 | 自动化证据 / 结果 |
| --- | --- | --- |
| 下层页跟随 | `after-dark-progress-12/30/50/65.png`：前页右缘 971→777→561→399，目标书名右缘 983→934→880→840，差值约四分之一 | 实际 held-drag 回归与精确几何断言通过 |
| 深色渐进提亮 / 松手衔接 | 同组 PNG 纸色 18→23→27→30；`after-dark.mp4`、`after-light.mp4` 实际前后松手及双向取消，最终 8/464 | 新进度像素断言、原松手 / 再次按住取消回归通过；双向取消正文 PNG 精确还原 |
| 设备圆角 | `native-corner-measurements.json`；420 / 480 密度配置均上报 [47,47,48,48]px，角点保留物理半径，不被 dp 放大；原密度已恢复 | 四个不同半径、null=0、RTL、密度及 24→44px 运行时绘制通过 |
| 原生小白条 / 沉浸 | `*-bars.json` 与原 dumpsys：阅读 / 控件 / 菜单状态栏 false，导航栏 true，DEFAULT；通知栏返回恢复；目录及退出均 true | 原生 WindowInsetsController 回归通过 |
| 平滑控件 / 单标题 | `chrome.mp4` 可见中间帧滑入淡入；hidden / reveal / finish XML 各一份 UI 书名，正文不移动 | 80ms 中间帧、标题数量与正文固定边界断言通过 |
| 设置锚点及功能 | `dark-chrome-menu.png` 与 `settings-native.log`：右上角按钮下方展开，真实小 / 默认 / 大字号、行高、边距、重置、Back 关闭均操作验证；原小字号恢复 | PopupLayout 实际窗口 y 与按钮锚点、全部设置回调 / reset 通过；修复 intrinsic 查询崩溃 |

完整 QA 1713（1711 通过 / 2 既有跳过 / 0 失败 / 错误），Release Lint 0 errors / 149 warnings / 4 hints，构建 8m34s / exit0。逐项证据汇总 `verification.json` 共 49/49，不代替性能 / 全页像素一致 / 实机手感验收。

实测 APK 7,041,356 bytes，SHA-256 `9916adb5fa1909eef092c069e0fef776859450f065c316054a7b29b42e0ade6a`；正式签名证书 SHA-256 `2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1`。16 KiB 对齐通过，无 INTERNET；installed / build / Outbox / Download 四份摘要一致，在线模拟器 install -r 成功。版本 1.0.0 / 37，Room15，无清数据。最终冷开还原 8/464、原正文与安全区，崩溃缓冲为空。

录屏实际为深色 21.70 秒 / 90 帧、浅色 23.74 秒 / 84 帧、控件 10.25 秒 / 48 帧，screenrecord 的静止持有为 VFR，不据此声称 FPS / 流畅度性能通过。完整原录屏保留，未编辑补帧。

源码 HEAD a57c324 加工作区未提交修改，生产差异摘要 `c0a7739c83e7ced41945c395419d6454674c03b6fa1ea53cc010bf924ea39f90`，完整字节保存在 `production.diff` / `source-provenance.json`。全部输出位于根目录 `output/epub-reference-fidelity/`，正式产物 `.external_outbox/m1racle-reader-release-latest.apk`；无新依赖，未推送 / 发布 / tag。
