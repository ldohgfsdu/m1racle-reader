# 当前状态

当前侧载验收包 **1.0.0 / 51，Room v15**。按用户要求，EPUB翻页和界面恢复43版，只加入字体选择与私有字体导入。恢复原生HorizontalPager、原纸页弹簧 / 跟随动画 / 距离速度规则、全屏纸面、圆角、标题页码与43版目录 / 设置面板。Readium / WebView翻页路径已移除，无第二渲染器。保留其他既有界面改动，无INTERNET，无数据库升级。

## 当前产物

[正式签名 APK](../.external_outbox/m1racle-reader-release-latest.apk)。APK **27,183,175 bytes**，SHA-256 `6e9039dd25f2985d722db71bb8a2624a27b2d74e4fc674fe092480ca9337989c`；快照 `m1racle-reader-release-v1.0.0-20261007-142532.apk`。 安装版本号递增便于覆盖升级，EPUB阅读行为基线为43。

生产证书延续 `2d9daba0…bf9d1`；签名、16KiB对齐、Manifest51 / min31 / target35 / 非debuggable / 无INTERNET通过。原书测试资产只在隔离测试APK内。正式构建exit0，构建输出 / Outbox latest / 快照 / 实际base.apk / 模拟器Download五份摘要一致，已install -r覆盖安装，未清空数据。

## 验证

- **implemented**：43版EPUB源码恢复、字体接入和45–50版Locator开书时一次性转换完成。转换处理EPUB根目录与OPF相对路径差异，仅唯一匹配才选章节；正常正文按原文恢复，无法匹配按旧原生降级规则处理。
- **automated_verified**：最终QA JVM1730项（1728通过 / 2既有跳过 / 0失败或错误），225套件；Release Lint0 errors / 151 warnings / 4 hints。 原43版分页、纸页、手势、菜单与位置测试恢复；字体与一次性数据转换新增回归通过。对应已撤销SDK实现的测试随实现退役，保存在恢复前源码备份中。
- **emulator_device_verified**：最终4项原生仪器场景全部通过：用户原书的轻拖 / 纵滑 / 按住拉回取消 / 单页往返；原书旧SDK根目录路径恢复精确原文锚点；私有字体实际字形变化、重排 / 重建 / 移除 / 宋体保留位置；私有库去重 / 持久化 / 源文件删除 / 无效与超限文件拒绝 / 删除回退。
- **实际签名Release**：原书中央轻滑与纵滑保持12页，正常滑动12→13→12，默认正文逐像素一致；宋体698页与默认679页，实际字形变化，冷启动重开恢复12/698且正文逐像素一致。字体恢复默认后12/679，最终包冷启动及滑动复验通过。Markdown正文及PDF2/3基础打开通过。升级前9份文档与9份原文件路径 / 字节摘要、书签 / 收藏 / 标注记录保留；Room15。

详细阶段证据与失败复验说明见[本轮报告](EPUB_RESTORE43_FONT_20261007.md)，目录 `output/epub-restore43-font/`。字体正式界面首次验收与最终包之间只有一次性Locator路径转换文件变化，纸页 / 字体 / 设置源码完全一致，见source-amendment.json；最终包重新执行4项设备回归、覆盖安装与阅读冒烟。

## 边界与待验

实际43版取消方式按旧实现：长拖时不提前保存，拉回后取消归位；不声称超过阈值后直接ACTION_CANCEL一定回原页。纵滑可显示43版工具栏，页码与正文位置不变，逐像素比较在工具栏收起后执行。

恢复43版有限块模型，表格按普通文本投影，原书CSS / 内嵌字体 / SVG不保留45–50版浏览器排版能力。旧SDK表格等与旧块原文无法对应时位置会降级，不能声称全部旧位置精确恢复。DRM、fixed-layout、脚本、在线资源、音视频及文内搜索 / 批注不在范围内。使用用户提供的HowToLiveBetter.epub，原文件未修改；最初截图《她先来》未取得源文件。

物理手机未连接，手机实际手感与性能待人工验收；模拟器结论不代替真机结论。

## 工作区

`feature/epub-anchor`，HEAD `a57c3244d10d5e741bd25ae790aeb90fb9eed49a` + 未提交修改。生产源码258份文件fingerprint `31e80cc7209bfecb40291094f3f48438e204d016b066d822bcef9eed5ea8b476`。其他既有工作保留，无commit / push / 公开发布 / tag。此前状态见[50版历史快照](history/CURRENT_STATUS_before_epub_restore43_font_20261007.md)。
