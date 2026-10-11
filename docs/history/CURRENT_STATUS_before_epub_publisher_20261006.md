# 当前状态

当前交付 **1.0.0 / 44，Room v15**。根据用户新视频复现并修复 EPUB 返回首页跳动：首页在状态栏尚未恢复时已出现，原顶部边距暂为0；改用显隐无关的状态栏安全区，从第一帧就保留正确位置。

沿用 RiseDiary 四入口底栏与独立导入、按标签实际高度计算的大字号留白、正式版液态玻璃工作台，以及 288dp / 窗口82%上限的 EPUB 目录 / 设置分段菜单。Markdown / PDF / 有限 EPUB，本地优先，无 INTERNET，数据库不变。

## 当前产物

`.external_outbox/m1racle-reader-release-latest.apk`，7,141,724 bytes，SHA-256 `31f135055d04ad3584ada63251eb6affa0aa7ca0c83cbb4762bbc3e628d6a7fc`；快照 `m1racle-reader-release-v1.0.0-20261006-152436.apk`。已自动复制 emulator-5554 Download 并 `install -r` 覆盖安装。构建 / Outbox / installed / Download 四份摘要一致，正式证书延续，16 KiB对齐与 Manifest44 / 无 INTERNET通过。

## 验证

- 完整 QA1722：1720 通过 / 2 既有跳过 / 0 失败或错误，223 suites；Release Lint0 errors / 161 warnings / 4 hints。降低构建并发后正式构建9m36s / exit0。
- 原生 WindowInsets 回归修复前失败、修复后通过；实际 Repository / ViewModel / NavGraph 加载 EPUB2 后返回首页，每帧坐标保持，Dock 出现时不包含退出的 EPUB Pager。既有底栏、工作台、分段菜单回归通过。
- 实际 Release43 录屏复现标题 y=40→176，位移136px等于状态栏高度。实际 Release44 三段录屏覆盖阅读页、退出首页、状态栏逐渐恢复：深色工具栏返回43帧 / 16个首页帧、深色系统返回45帧 / 20个首页帧、浅色工具栏返回47帧 / 19个首页帧；所有首页标题 y=176，位移0。录屏是 VFR，属于退出过程证据，不作为 FPS 或全面性能结论。
- 覆盖升级后冷启动书库保留；长篇 EPUB 锚点还原完成后、重复打开与浅深色切换均为8/464。系统栏阅读时隐藏状态栏、保留原生导航栏；首页两栏恢复。crash buffer保留既有模拟器 MultiDisplayService 异常，本轮没有 reader 进程崩溃。
- 物理手机未连接，用户手机复验 pending。其他设备与 OEM 的系统行为尚未验证。

## 排版研究与未决项

已检查网上 / GitHub 的 Morealm、Lumi Books、Readium、Readest、KOReader。Lumi 的 WebView 原书布局是更贴近截图问题的成品参考，已核对其样式保留、本地资源与玻璃限制；尚未构建 / 安装 Lumi。Morealm 核心另依赖未包含的 epub-lib，Readium 仍为优先评估的独立引擎候选。详见 [本轮报告](EPUB_RETURN_RESEARCH_20261006.md)。当前 EPUB 内核未迁移，仍使用应用统一字体 / 段落样式，表格转普通段落、单图片页居中；用户截图中的原书字体、封面、表格完整排版尚未修复。未取得《高性价比人生指南》原 EPUB，待同书样本核对与引擎接入验证。本次 Lumi 研究仅更新文档，交付包仍为44。

## 工作区

`feature/epub-anchor`，HEAD a57c324 + 未提交修改。证据 `output/epub-return-v44/`。上轮43见 [历史快照](history/CURRENT_STATUS_before_epub_return_research_20261006.md) 与 [上轮报告](RISEDIARY_TOOLBAR_20261006.md)。未提交 / 推送 / 公开发布 / tag。
