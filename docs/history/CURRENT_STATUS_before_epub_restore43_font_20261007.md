# 当前状态

当前侧载验收包 **1.0.0 / 50，Room v15**。EPUB继续使用Readium原书排版，恢复此前纸页滑动的慢拖三分之一页阈值、速度判断与弹簧回弹；中央轻拖回原页，纵滑不进入连续滚动，正常手势只走一页。准备目标页不提前保存，取消恢复原正文和Locator。宽表格限制在一页内，独立行按原生tbody组分页，相连rowspan保留在同组；精确文字锚点使用真实CSS页面宽度，避免多页后的取整误差。

用户已明确要求保留原滑动翻页；将来新形式只能独立增加，已写入design.md。本轮没有新增翻页模式。保留RiseDiary底栏、独立导入、正式版工作台及紧凑目录 / 设置菜单，无INTERNET，无数据库升级。

## 当前产物

[正式签名 APK](../.external_outbox/m1racle-reader-release-latest.apk)，**29,576,845 bytes**，SHA-256 `c76bba9d35d76ea5d32602653199b0beff379c07dbb891550af61be39477da4e`。快照 `m1racle-reader-release-v1.0.0-20261007-114715.apk`。生产证书延续 `2d9daba0…bf9d1`；签名、16KiB对齐、Manifest50 / min31 / target35 / 非debuggable / 无INTERNET通过。原书测试资产只进入隔离测试APK。

正式构建exit0，Release Lint **0 errors / 275 warnings / 4 hints**。构建输出、Outbox latest、快照、实际base.apk及模拟器Download五份摘要一致；已install -r覆盖安装，未清空应用数据。

## 验证

- **implemented**：滑动规则、连续拖动拦截、取消恢复、表格页边界和精确锚点修正已落地。
- **automated_verified**：最终生产源码完整QA JVM1675项（1673通过 / 2既有跳过 / 0失败或错误），220套件。纸页安全契约、速度 / 距离行为、表格所有单元格 / 行顺序 / colspan / rowspan关系保持通过。
- **emulator_device_verified**：12个设备场景首轮10通过 / 2失败；修正菜单测试的导航前置等待后，其原断言复验通过。最终11个独立场景通过，1个外部字体加载场景仍失败。原书两项均通过：三次中央轻滑、纵滑、单页步进、按住取消、多页段落及85行表格完整可达；另验证36行宽目录、捕获延迟、字号重排 / Fragment重建 / 冷启动及长段落行内恢复。
- **实际签名Release**：默认1080×2400 / density420下，SAF导入用户原书、中央点按 / 目录跳转、轻滑8→8、纵滑8→8、前翻 / 回翻8→9→8→9、关闭进程重开恢复9/11通过。轻滑 / 纵滑 / 回翻后的正文与原8页逐像素一致，重开后正文与原9页逐像素一致。原8份文档仍在，新增1份用户原书。

证据位于 `output/epub-slide-boundaries/`，详见[本轮报告](EPUB_SLIDE_BOUNDARIES_20261007.md)。QA只用于隔离测试，交付为实际签名并压缩的Release。

## 未完成项与边界

外部私有字体加载设备测试仍失败，不能声称所有字体路径通过。恢复默认分辨率后的首次启动出现一次FocusEvent超时ANR，等待后恢复，后续原书阅读和冷启动重开通过；完整现场已保留，不作无ANR或手机性能结论。物理手机未连接，实际手机手感与启动性能待验。

使用用户提供的《高性价比人生指南》原EPUB（HowToLiveBetter.epub），未修改原文件；最初截图的《她先来》未取得源文件，不作同书验证结论。未声称所有出版物完全兼容；DRM、fixed-layout、作者脚本、在线资源、音视频、文内搜索 / 批注不在本轮范围。

## 工作区

`feature/epub-anchor`，HEAD `a57c3244d10d5e741bd25ae790aeb90fb9eed49a` + 未提交修改；生产源码258份文件fingerprint `d29ec1594ba2776ae4711e5bafce374ae5744c18628ebe20f7c2dc276fbe3c68`。保留既有其他修改，未提交 / 推送 / 公开发布 / tag。此前状态见[历史快照](history/CURRENT_STATUS_before_epub_slide_boundaries_20261007.md)。
