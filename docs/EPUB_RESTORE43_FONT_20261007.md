# EPUB恢复43版与字体

用户明确要求：EPUB翻页和界面回退到43版，加上字体就行。安装版本递增为51以支持覆盖升级；阅读行为基线是43。implemented / automated_verified / emulator_device_verified；物理手机手感与性能pending。

## 源码恢复

43版依据 `../output/risediary-toolbar/production.diff`，SHA-256 `4d55e6d24f621959adf65cfeebc66f4d3b3ff7f1a5a30322d0d7de822a241ed6`，由HEAD a57c324及该版本生产差异恢复；附加未跟踪源码也已从同一记录提取。未改变的原生分页 / 锚点代码按对应HEAD及退役源码归档恢复。

菜单、分页算法、文字布局、纸页边缘、物理圆角和系统栏与43版源码一致。EpubReaderContent只增加正文fontFamily；Screen / ViewModel / ReadingSettings仅接字体库、字体选择和回调。HorizontalPager手势和动画没有改写。源码核对与各文件差异在 `output/epub-restore43-font/v43-source-comparison.json` / `*-from43.diff`。

删除Readium依赖、Fragment / WebView导航、PixelCopy手势控制器和专属运行资源，不保留第二渲染器。对应45–50版Publisher / paper controller / 新菜单测试随被撤销的实现退役，已保存在 `before-restore43-source.zip`；43版原生UI、纸页、圆角、分页及锚点测试随源码恢复。Publisher布局模式设置测试改为字体选择与43版行距可用性测试，不继承旧SDK的验证结论。

## 字体与数据

43版设置结构中加入默认 / 宋体 / 系统及原有私有TTF / OTF导入管理。原生正文使用同一TextStyle测量和绘制，标题 / 正文保持原字号规则，代码保留等宽。私有库校验 / 去重 / 删除回退沿用已存在的字体模块；不添加新翻页形式。

45–50版Locator在开书时一次回转：href先区分EPUB根目录与OPF相对路径（完整后缀唯一才对应），再按可见原文定位旧块锚点，不把SDK位置当段落索引。文字不匹配保留章节降级和保存保护，章节丢失或数据损坏按百分比恢复。43版原生锚点与未打开记录不变，不改Room15、书库或源文件。升级前完整模拟器应用备份 `reader-before-restore43.tar`，60,097,536 bytes。

## 验收

最终QA JVM1730项（1728通过 / 2既有跳过 / 0失败或错误），225套件；Release Lint0 errors / 151 warnings / 4 hints。

最终4项原生仪器场景全部通过，`native-all-final.log`：原书慢拖24% / 纵滑不翻、长拖按住不保存、拉回取消、前后单页；私有字体实际像素变化与精确锚点重排 / 重建 / 移除 / 内置宋体；字体库去重 / 持久化 / 删除原文件后使用 / 无效与50MB超限拒绝 / 删除选中字库回退；原书OEBPS根目录Locator转回原生章节和字符位置。

实际生产签名包默认1080×2400 / density420下，原书轻滑 / 纵滑12→12、正常翻页12→13→12；字体默认679页，宋体698页，实际字形发生变化，关闭进程重开仍12/698且正文逐像素一致，最后恢复默认12/679。Markdown正文、PDF2/3基础打开通过。最终包覆盖安装后再次冷启动、轻滑、纵滑与单页往返，正文与原默认页逐像素一致，见main-ui-verification.json / final-smoke.json。

升级前后9份文档、9份原文件路径与字节摘要、书签 / 收藏 / 标注记录一致；正常打开时间、阅读位置更新详列data-preservation.json。未打开书的旧Locator不变。未清库、卸载或回灌正式应用数据。

APK **27,183,175 bytes**，SHA-256 `6e9039dd25f2985d722db71bb8a2624a27b2d74e4fc674fe092480ca9337989c`；快照 `m1racle-reader-release-v1.0.0-20261007-142532.apk`。 生产签名 / 16KiB对齐 / 非debuggable / 无INTERNET / 测试原书未进入正式APK通过，构建 / Outbox / 快照 / 实际base.apk / Download五份SHA一致，已自动复制并覆盖安装。最终源码fingerprint `31e80cc7209bfecb40291094f3f48438e204d016b066d822bcef9eed5ea8b476`，仅候选一中的Locator转换文件补充根目录路径处理，阅读 / 字体文件未再变化，见source-amendment.json。

## 初轮失败与复验边界

初轮完整1728项JVM有1项失败：ReaderReturnHostTest等待已移除SDK的epub-publication标签；改为恢复后原生epub-pager标签，返回坐标、底栏与时间断言不变，完整1728项复验通过。最后路径转换增加2项回归，最终1730项完整通过。数据库更新Anchor参数改为可空以匹配Room列，仅同步7个测试DAO签名，无模式变更。

首轮仪器测试2项失败：直接在超过阈值的长拖终点ACTION_CANCEL会按43版Foundation归位目标页，这是原43行为；测试改为原版的拉回再取消，未改手势代码。字体测试等待设置观测超时：根组合仅在SideEffect中读状态而未订阅，修正组合组中的状态读取后，原字形 / 原文 / 精确锚点 / 重建 / 移除断言通过。日志native-tests.log / native-recheck.log / native-final.log保留，最终4项原断言通过，不继承旧SDK字体失败结论。

正式包垂直手势可唤出原43工具栏；覆盖层改变文字像素，原文与页码不变。像素对比在工具栏关闭、相同界面状态后进行，原始截图保留。冷启动异步位置恢复完成后12/698，不声称恢复第一帧没有暂态。

检查初候选的旧SDK位置时发现根目录与OPF相对路径差异，已修正一次性转换并完整重建；歧义路径不猜测。旧SDK表格文本投影与原生块原文仍可能不同，无法匹配按章节 / 进度降级，不承诺所有旧SDK位置精确恢复。

未连接物理手机，手机手感与性能待验。

恢复43版有限块模型后，表格转普通文本、原书CSS / 内嵌字体仍为该版本的能力边界；不声称保留45–50的浏览器原书排版能力。原书样本是用户提供的HowToLiveBetter.epub，原文件不改。

## 工作区

feature/epub-anchor，HEAD a57c324 + 未提交工作。仅处理EPUB及其字体 / 数据接点，其余既有改动保留。无commit / push / 公开发布 / tag。证据目录 `output/epub-restore43-font/`。
