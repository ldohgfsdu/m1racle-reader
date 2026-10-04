# EPUB 排版与覆盖翻页修复（2026-10-03）

当前阶段：implemented / automated_verified / device_verified（本次 EPUB 场景）。1.0.0 / 27 正式签名包已覆盖安装并完成下述真机验证；人工手感反馈独立记录。

## 根因与范围

- 原 ListBlock 把全部条目合成一行测量任务，换行无悬挂缩进，条目间没有独立段距。现在每条独立测量，长条目继续有界分片；sourceRow、blockIndex 和原始字符坐标保持同一来源。
- 原翻页阴影位于新页内部，覆盖文字；旧页另有整页黑色遮罩。现保留覆盖式几何关系，取消整页变灰，并把 10dp 的轻阴影绘制到纸页边缘外侧。
- 原页脚使用 currentPage，拖过半页就显示新页；持久位置使用 settledPage，两者不一致。现页脚、章节进度与已提交阅读位置共用 settledPage。
- 字号重排原来同时启动恢复和位置观察，并把恢复后新页的首字符重新当成阅读锚点，反复切换字号会向前漂移。现先完成恢复，再观察页码；只有用户翻到另一页才更新字符锚点，重排期间保留原字符。原生回归先复现“大 → 默认 → 大”从第 6 页退到第 5 页。
- 真机原系统字号 1.1、阅读字号“大”，OEM 字重 key_miui_font_weight_scale=87；原 EPUB 相关章没有 strong / b 正文标签。未改系统设置或应用阅读偏好。

## 验证记录

回归先证明原实现整页变灰及拖过一半页码提前变化失败。新增原生字体测量测试验证悬挂缩进的实际字形位置、条目间距，以及分页位置和目录锚点。Legacy 字体桩不能验证真实换行，字形验证使用 Robolectric Native；业务语义测试保持原来的 Legacy 环境。

执行为 unmanaged；runId / projectId / machineId 为 unknown，未伪装成 DevHub 管理的运行。外部证据目录：`~/.devhub/runs/unmanaged-reader-epub-turn-20261003/`。HEAD、dirty fingerprint、完整源码摘要、命令、退出码、时间及最终交接存于该目录；动态状态不写入仓库。

### 自动化与构建

- EPUB 针对性回归：37 项，0 失败 / 0 错误 / 0 跳过，`gradlew.bat :app:testQaUnitTest --tests com.m1racle.reader.ui.epub.* --console=plain` 退出 0。
- 最终完整单测：1643 项，0 失败 / 0 错误 / 2 既有环境跳过（外部《老子》fixture、Windows 符号链接权限）；Release Lint 0 errors / 158 warnings / 4 hints。命令 `gradlew.bat :app:testQaUnitTest :app:lintRelease --console=plain` 退出 0。
- `gradlew.bat :app:assembleRelease --console=plain` 退出 0。生产证书连续性、APK 签名、16 KiB 对齐和 Manifest 检查通过；包名 `com.m1racle.reader`、versionCode 27、minSdk 31 / targetSdk 35，无 INTERNET 或 QA Activity。
- HEAD：`a314247d9cc27f7b3adc1edb3074b65eee3afbfe`；构建 dirty fingerprint：`6db8ab20ea0abf6aeddb16709c503140e11a76cd350b3991ec3aa5cf459ec797`；已验证源码摘要：`2fa0888ce863327a44790d0179e4201eb586e7e4585d78e7449554cc3259d29a`。本轮代码及版本在完整验证和构建间未改变，EPUB 源码仍与已验证快照一致。构建后另一个任务修改了分段控件 / 材质对比源码，未包含在 27；因此全量验证对该后续工作区状态为 stale，对本次 APK 快照仍有效。

### 产物

- `.external_outbox/m1racle-reader-release-latest.apk`：6,993,653 bytes。
- SHA-256：`a8311b291f8265d639dd99757b70ee1ce862a94c10f2ce9ebdf6c1cc86a16770`。
- 真机 `/storage/emulated/0/Download/m1racle-reader-release-latest.apk`，2026-10-03T08:36:40.592658+00:00；设备文件摘要匹配，`adb install -r` 成功，保留生产数据。
- 本轮保留同工作区底栏 / 手势区修复，详见 [底栏手势区报告](DOCK_GESTURE_AREA_20261003.md)。

### 设备验收与边界

API 36 真机打开用户《高性价比人生指南》。列表段距和悬挂缩进已呈现；正文及页脚不重叠。字体与原书正文无误设粗体，继续遵循“大”字号、默认行高、标准边距及 OEM 字重设置。重排后总页数从 1300 增至 1386；升级后的页号不与旧页号强行对应。

26 的初次真机检查发现字号往返漂移，因此该包已被包含锚点修复的 27 替代；26 的签名与测试记录保留在外部 `v26/`，未把当时的自动化通过当成字号实机通过。最终包真机记录：

- 覆盖安装后的列表换行、段距与页脚分离正常。
- 两轮“大 → 默认 → 大”：65/1386 → 52/1104 → 65/1386，页码与可见文字完全恢复。
- 返回首页、结束应用进程并重开：仍为 65/1386，可见文字完全一致。
- 拖过半页时页脚保持 65，回拖取消恢复同一文字；前翻至 66、后翻回 65，落稳后不残留拼页或遮罩。
- 原灰色覆盖区域背景主像素为 (226,226,226)，最终包为 (255,255,255)；阴影落在页边外侧，未遮蔽新页文字。
- 最终安装包、Outbox 与 Download 的 SHA-256 一致；versionCode 27，未发现本轮进程的 FATAL EXCEPTION / ANR。阅读偏好最终为“大 / 默认行高 / 标准边距”，手机留在原书 65/1386 页。

原生 UI 初次自动导航未进入阅读器，该步骤未计为通过；重新定位“继续阅读”入口后完成全部检查。

DRM、固定版式、复杂 CSS、脚本、音视频、搜索和批注仍不在 EPUB 有限支持范围。当前证据不代表所有书籍和设备的性能或人工手感验收。
