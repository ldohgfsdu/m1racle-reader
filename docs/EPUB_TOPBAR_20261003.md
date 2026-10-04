# EPUB 顶栏避让与紧凑控件 — 2026-10-03

状态：implemented / automated_verified / device_verified。正式签名侧载 Release **1.0.0 / 30** 已覆盖安装到用户手机并复制至 Download；未公开发布 / tag，人工观感与手感仍由用户确认。

## 根因与修改

旧实现把 Material TopAppBar 作为分页正文的顶部覆盖层，正文仅留 12dp；中央点击后，整条工具栏遮住首行。API 36 真机旧包 29 可复现：正文上缘 y=189，按钮下缘 y=293；截图中第一行被盖住。

现在工具区始终预留至少 56dp，并按超大系统字号的标题行高增高；正文另留 12dp 间距。中央点击只淡入 / 淡出控件，不改变正文视口，因此不会反复分页或跳页。返回使用独立 40dp 圆形玻璃，目录 / 设置共用轻玻璃组，书名为普通文字；每个按钮保留 48dp 点击范围和按压反馈。复用现有 GlassMaterial 和 Backdrop，不新增依赖；只捕获纸面 / 正文，工具控件放在捕获层之外，避免玻璃采样自身与重复图标。已有覆盖翻页、页码停稳后更新、列表排版和重排字符锚点修复保留。

本轮产品文件为 `EpubReaderContent.kt`、新增 `EpubReaderTopBar.kt`，配套 `EpubReaderUiTest.kt`、版本 29 → 30 和发布身份检查。当前包也包含其他聊天已稳定的分段控件与蓝色滑块源码；本轮未回退其改动。

## 分层验证

- red：新增普通字号 / 窄屏和 200% 系统字号的两项避让断言，在旧工具栏上均失败；保存退出 1、XML 和日志。失败原因是控件位于正文内。
- fast：`gradlew.bat :app:testQaUnitTest --tests com.m1racle.reader.ui.epub.* --tests *R32ReleaseHardeningContractTest* --console=plain`，43 tests / 0 failures / 0 errors / 0 skipped，退出 0；其中 EPUB 38 项。包含原字号 ABA、拖动取消、目录和位置恢复，以及新工具栏避让、显示隐藏时正文边界 / 文字 / 页码不变、按钮回调。
- full：`gradlew.bat :app:testQaUnitTest :app:lintRelease --console=plain`，1650 tests / 0 failures / 0 errors / 2 既有环境跳过（外部《老子》教材 fixture 与 Windows 符号链接权限），退出 0。`BackgroundPositionSliderTest` 3 项通过并保存独立 XML。Release Lint 0 errors / 158 warnings / 4 hints。
- release：`gradlew.bat :app:assembleRelease --console=plain`，JDK 17，退出 0；apksigner、证书连续性、16 KiB zipalign、版本 / Manifest 均通过，无 INTERNET 或 QA Activity。验证前后和构建 / 交付时全部产品源码摘要一致，源码指纹 `f05892bbb202de0d1d760d7e6ab43adcf5a9948141cb6f5b8a41a1595d98a501`。
- device：`db04f865` / 25098PN5AC / API 36 / 1220×2656 / 520dpi，保留原系统 fontScale 1.1 与应用“大”字号。`adb install -r` 成功，无卸载或清数据。当前正文范围 `[65,371][1155,2461]`；三个 48dp 点击区域分别 `[26,163][182,319]`、`[882,163][1038,319]`、`[1038,163][1194,319]`，全部在正文上方。三个显示 / 隐藏循环的正文文字、页码、边界和截图正文裁剪像素完全一致；设置、目录、返回可达，前后翻页返回及退出 / 结束进程 / 重开均恢复相同可见文字与第 72 / 1503 页。重开进程日志未发现 FATAL EXCEPTION / ANR。

避让降低了分页可用高度，本书总页数从 1386 变为 1503；升级前第 67 页的原文本锚点仍在升级后的第 72 页中可见，属于同一阅读位置的新分页。显示与隐藏顶栏自身不会更改页数或位置。真机自动化曾误把设置标题断言为“阅读设置”，实际为“显示设置”；确认当前 XML / 本地字符串后更正测试脚本并继续，未为此修改产品或削弱避让断言。

## 正式产物

来源：本轮 `app/build/outputs/apk/release/app-release.apk`，Outbox `C:\Users\21234\Desktop\M1racle Reader\reader\.external_outbox\m1racle-reader-release-latest.apk`。**6,994,057 bytes**；SHA-256 `d5b935f58cde18fbcc8b5d73090601013eb88ea8e40d50b5c71a6fc4905645e2`。设备 `/storage/emulated/0/Download/m1racle-reader-release-latest.apk`；交付时间 `2026-10-03T09:15:07.192463+00:00`。构建输出、Outbox、Download、实际安装 base.apk 摘要一致；校验文件与构建日志保留。

证书 SHA-256 `2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1`，与原生产包一致。沿用用户已有签名授权，签名材料仅在仓库外临时受限目录读取并经环境注入，构建后清理；没有记录密码或密钥。

## 身份、范围与交接

unmanaged；runId / projectId / machineId unknown；Codex / GPT-6 / version unknown，Windows。项目契约与中央工程公约本机不可用，不声称 DevHub 受监管租约。分支 `codex/fix-project-audit`，HEAD `a314247d9cc27f7b3adc1edb3074b65eee3afbfe`；构建 dirty fingerprint `be57114216df072c12678b12aff79d0de29ad0751741e718956fd1c654d18b76`。全部已有未提交修改保留，无 reset / stash / clean / commit / push。

外部证据 / handoff：`C:/Users/21234/.devhub/runs/unmanaged-reader-epub-topbar-20261003`，绑定 HEAD、源码指纹、dirty fingerprint、命令 / 退出码、时间与 unknown 身份。保留两项失败回归和全部通过记录。当前实机只覆盖该用户长书、浅色及本次交互；深色真机、其他书籍、图片和长书性能、Markdown / PDF 整体复查不在本轮。原 EPUB 排版 / 字号、分段控件和滑块模拟器的独立证据保持各自验收范围。人工观感 / 手感和公开发布未自动认定完成。
