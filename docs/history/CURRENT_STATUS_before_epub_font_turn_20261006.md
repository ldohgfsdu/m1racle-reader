# 当前状态

当前交付 **1.0.0 / 45，Room v15**。EPUB 已切换到 Readium 独立核心，保留原书字体、缩进、表格、SVG 和封面布局；提供原书 / 自定义排版及原书 / 宋体 / 系统字体。重复重排、旋转和冷启动使用同一文字 Locator 恢复，旧原生文字分页器已退役。

保留 RiseDiary 四入口底栏、独立导入、正式版液态玻璃工作台和最大288dp / 窗口82%的目录 / 设置分段菜单。首页从第一帧使用显隐无关的安全区，阅读退出不采用交叉淡化。本地 Markdown / PDF / 可重排 EPUB，无 INTERNET，无数据库升级。

## 当前产物

[正式签名 APK](../.external_outbox/m1racle-reader-release-latest.apk)，**29,544,077 bytes**，SHA-256 `0edbf8b7dcb22f928097a23b4f0834238a07f546fb5554f3e61133eae1104c3e`。快照 `m1racle-reader-release-v1.0.0-20261006-205758.apk`。包体包含完整中文宋体字体及许可。

正式构建7m20s / exit0；证书延续、16 KiB对齐、Manifest45 / min31 / target35 / 无 INTERNET通过。已自动复制 emulator-5554 Download 并 install -r；构建输出 / Outbox / Download / 安装的 base.apk 四份摘要一致。

## 验证

- 完整 QA JVM1664项：1662通过、2既有跳过、0失败或错误。API35真实 WebView的4项仪器测试通过，覆盖出版结构、七次字号重排保留同一文字、目录 fragment、旋转与新 Publication 重开、4000词长段落行内恢复，以及拖动提交 / 取消语义。
- Release Lint0 errors / 283 warnings / 4 hints。仪器测试属于隔离QA；实际签名并压缩的Release另行安装验收，不把QA包作为交付。
- 实际Release模拟器：中文封面 / 宋体 / 缩进 / 合并单元格表格、目录跳转、字体切换、自定义行距与边距、重置、横竖屏恢复、进程冷启动和SAF重复导入通过。工作台打开 / Clear / 拖动 / 重置，以及Markdown / PDF打开与返回通过。
- 本轮Release三段退出录屏共109帧：深色工具栏32帧、深色系统返回38帧、浅色工具栏39帧；26个首页帧标题均y=176，位移0。没有检测到空白过渡帧，已查看全帧联系图。VFR / 软件渲染模拟器证据不作为FPS或物理手机性能结论。
- Room仍v15，原7份文档ID / URI / 内容摘要及7个原文件字节均保留；新增1份验收样本，重复导入未新增文档。未清空应用数据；未打开的旧EPUB记录保留，首次打开时才转换。crash buffer保留模拟器SystemUI与UiAutomation异常，本轮无reader进程FATAL。

## 验收边界

未取得用户《高性价比人生指南》原EPUB，物理手机未连接。同书排版、OEM系统行为、手机视觉与手感仍待复验。验收使用生成的出版布局样本和原模拟器文档；不声称所有出版物完整兼容。DRM、fixed-layout、作者脚本、在线资源、音视频、文内搜索 / 批注不在本轮范围。

Readest、Lumi、Morealm作为产品参考；采用Readium Kotlin3.3.0及内置Noto Serif，未复制上述阅读器应用代码。翻页现在采用Readium水平分页，旧自定义纸面覆盖 / 圆角 / 亮度动画已随旧核心退役。详见[本轮报告](EPUB_PUBLISHER_LAYOUT_20261006.md)。

## 工作区

`feature/epub-anchor`，HEAD `a57c3244d10d5e741bd25ae790aeb90fb9eed49a` + 未提交修改。最终255个生产 / 配置文件指纹 `fa82b839f8d01c5cc781de810dfd814dfc644a87d378309e75faadcc49dc5822`；源码归档、测试、装机、数据核对和录屏证据位于 `output/epub-publisher/`。未提交 / 推送 / 公开发布 / tag。44版见[历史快照](history/CURRENT_STATUS_before_epub_publisher_20261006.md)。
