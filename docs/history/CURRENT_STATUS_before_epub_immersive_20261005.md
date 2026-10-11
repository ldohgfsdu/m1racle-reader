# 当前状态

当前基线 **1.0.0 / 34，Room v15**。按用户 Apple Books 视频修正 EPUB 滑动翻页，并保留上轮原书排版修复。完整自动化验证、正式签名打包、API 35 模拟器正式包验收与交付完成。**真实手机手感 / 视觉待验收**。报告见 [Apple Books 滑动翻页](EPUB_APPLEBOOK_SLIDE_20261005.md)。

## 当前实现

- 本地 Markdown、PDF、有限 EPUB 阅读；无 INTERNET、账号、广告或云同步。
- EPUB 前翻是当前整张纸页向左移开，下一页静止；后翻是上一页从左侧盖回。纸面 / 书名 / 页码一起移动，右边缘轻圆角与外侧阴影，拖动中的原纸面短暂改变明暗。原生 Pager 按距离 / 速度落页，最多一页，可反向拉回取消；停稳后保存位置。
- EPUB 保留显式换行，TOC 导航与分页解耦，连续标题在可容纳时跟随后文首行；文件边界 / 一级标题另起页。正文 Normal，取消强制两字缩进。完整出版方 CSS 尚未支持。
- EPUB 文本锚点恢复沿用 R04：同章 quote / 前 12 字定位、章节 / 百分比兜底及非精确恢复保护。原块编号不变，重排按文本位置恢复。
- 底栏玻璃配方与按压行为保留；Release 液态玻璃工作台仍隐藏。本轮修改 EPUB 翻页及对应验证 / 版本文档。

## 唯一交付产物

`.external_outbox/m1racle-reader-release-latest.apk`，**7,024,976 bytes**。

SHA-256：`54c905a4c6dca8a2150c6081ae12939e21bc65fcdbd16860451152f165d16c3a`。

快照：`m1racle-reader-release-v1.0.0-20261005-121840.apk`。正式证书、16 KiB 对齐、Manifest 版本和无联网权限检查通过。构建输出、Outbox、模拟器安装包与模拟器 Download 摘要一致；已覆盖安装 `emulator-5554`，保留书库 / 进度。尚未安装或复制到真实手机。

## 验证基线

- 完整 QA JVM：**1702 项，1700 通过，2 项既有跳过，0 失败 / 错误**。真实 Compose 手势回归证明拖动前后层次、反向拉回取消、完成前不保存锚点，以及重排 / 顶栏 / 页脚避让。
- Release Lint：**0 errors / 149 warnings / 4 hints**。完整构建退出码 0。
- 正式包模拟器 **18 / 18 检查通过**：前后慢拖、快速滑动只翻一页、越过半页后拉回取消、首尾页边界、升级 / 重启可见正文恢复、工具栏显隐不重排、产物一致性与无崩溃。
- 原书保持 **464 页**，升级前后正文一致；下层图片在拖动中的像素对照证明静止，整纸移动和圆角 / 阴影经截图确认。模拟器原主题、原书第 8 页与测试书第 2 页均恢复。
- 视频演示位于工作区根目录 `output/applebook-turn/applebook-slide-demo.mp4`。本轮未运行 connected 仪器套件，未验证真实手机的刷新率、手感、字形或长书性能。

## 工作区与证据

主工作区 `reader`，分支 `feature/epub-anchor`，基于 `a57c324` 加未提交修改；`reader-r04` 保留此前基线。生产差异、SHA、截图 / XML、像素对照与检查结果见工作区根目录 `output/applebook-turn/`，完整构建见 `output/applebook-turn-release.log`。此前状态见 [历史快照](history/CURRENT_STATUS_before_applebook_slide_20261005.md)。未推送、公开发布或 tag。
