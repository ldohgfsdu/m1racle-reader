# 当前状态

当前交付 **1.0.0 / 48，Room v15**。48 在 47 的触控看门狗之上再去掉翻页等待：纸页动画按自己的 320 ms 时间线揭示，不再等 Readium 迟到 1.6 s 的翻页通知，DOM 锚点改为事后精修。翻页、字体与菜单改动一并保留。EPUB 已切换到 Readium 独立核心，保留原书字体、缩进、表格、SVG 和封面布局；提供原书 / 自定义排版及原书 / 宋体 / 系统字体。重复重排、旋转和冷启动使用同一文字 Locator 恢复，旧原生文字分页器已退役。

保留 RiseDiary 四入口底栏、独立导入、正式版液态玻璃工作台和最大288dp / 窗口82%的目录 / 设置分段菜单。首页从第一帧使用显隐无关的安全区，阅读退出不采用交叉淡化。本地 Markdown / PDF / 可重排 EPUB，无 INTERNET，无数据库升级。

## 当前产物

[正式签名 APK](../.external_outbox/m1racle-reader-release-latest.apk)，**29,576,845 bytes**，SHA-256 `cb71c2e32ba5ce4d27fab10ec1c57bdb6e0d5caca0897b3ae20f38869fd8c374`。快照 `m1racle-reader-release-v1.0.0-20261007-094310.apk`。包体包含完整中文宋体字体及许可。

正式构建 exit0；Release Lint 0 errors / 285 warnings / 4 hints；证书延续（SHA-256 `2d9daba0…bf9d1`，与 45/46 相同）、4 字节与 16 KiB 页对齐通过、Manifest48 / min31 / target35 / 无 INTERNET 通过。构建输出 / Outbox latest / Outbox 快照三份摘要一致；已 `install -r` 到 emulator-5554 实际装机验证。

## 验证

- 完整 QA JVM 1672项（含新增 5 项纸页安全契约）：0失败、2既有跳过、0错误。
- **本轮实际装机验证（emulator-5554，正式包 47）**：单次横滑 1→2 页且提交；连续 6 次横滑逐页前进；8 次 0.4 s 快滑连打到末页且无叠加卡死；受控回翻 13→12→11→10、前翻 10→11→12 均单页步进；末页越界停住且点按仍能切出顶栏；打开设置菜单并关闭后横滑继续可用。logcat 无 reader 进程 ANR / FATAL（仅有镜像自带信息应用 ANR 与 `uiautomator dump` 的 UiAutomation 异常）。证据在 `output/font-turn-watchdog/`。
- Release Lint0 errors / 285 warnings / 4 hints。仪器测试属于隔离QA；实际签名并压缩的Release另行安装验收，不把QA包作为交付。
- 45 版既有结论：中文封面 / 宋体 / 缩进 / 表格、目录跳转、字体切换、字号重排、横竖屏恢复、冷启动和SAF重复导入通过；三段退出录屏109帧无空白过渡。VFR / 软件渲染模拟器证据不作为FPS或物理手机性能结论。
- Room仍v15，原有文档与字节均保留；未清空应用数据。证据位于 `output/epub-publisher/`（45）与 `output/font-turn-watchdog/`（47）。

## 验收边界

未取得用户《高性价比人生指南》原EPUB，物理手机未连接。同书排版、OEM系统行为、手机视觉与手感仍待复验；46 报告的翻页动画手感、外部字体导入与菜单闪帧同样待手机确认。验收使用生成的出版布局样本和原模拟器文档；不声称所有出版物完整兼容。DRM、fixed-layout、作者脚本、在线资源、音视频、文内搜索 / 批注不在本轮范围。

Readest、Lumi、Morealm作为产品参考；采用Readium Kotlin3.3.0及内置Noto Serif，未复制上述阅读器应用代码。46 的翻页动画保留但已加看门狗与降级路径，详见[本轮报告](EPUB_TOUCH_WATCHDOG_20261007.md)；46 的其余改动见[46 报告](EPUB_FONT_TURN_20261006.md)与[45 报告](EPUB_PUBLISHER_LAYOUT_20261006.md)。

## 工作区

`feature/epub-anchor`，HEAD `a57c3244d10d5e741bd25ae790aeb90fb9eed49a` + 未提交修改（含 47 的触控看门狗修复与新增契约测试）。证据位于 `output/epub-publisher/`（45）与 `output/font-turn-watchdog/`（47）。未提交 / 推送 / 公开发布 / tag。45 版见[历史快照](history/CURRENT_STATUS_before_epub_publisher_20261006.md)。
