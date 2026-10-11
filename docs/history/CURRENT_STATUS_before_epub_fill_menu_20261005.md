# 当前状态

当前基线 **1.0.0 / 39，Room v15**。本轮修复 EPUB 左右侧边贴合、起止补偿跳变、回翻预加载层序和圆角交叉轮廓，加入深色单像素高光；右上角目录 / 设置统一为参考图第 10 张的圆角面板。用户纠正的“缝隙”指侧边。报告见 [本轮验收](EPUB_CARD_POLISH_20261005.md)。固定设备四角、四分之一跟随、原纸渐进提亮、停稳保存、单书名、原生导航条透明沉浸均保留。

本地 Markdown、PDF、有限 EPUB，无 INTERNET、账号、广告、云同步或新增依赖，数据库不变。

## 当前交付

`.external_outbox/m1racle-reader-release-latest.apk`，**7,057,744 bytes**，SHA-256 `dfcd30be4efc06361b04167b3e5ea79cdbad77ae840bf3c7ec0bca4023779528`；快照 `m1racle-reader-release-v1.0.0-20261005-213255.apk`。正式证书不变，签名、16 KiB 对齐、Manifest39 / 无 INTERNET 通过。build / Outbox / installed / Download 四份摘要一致。已在线覆盖安装 `emulator-5554`，保留书库和进度，无清数据。手机未连接，厂商屏幕与实际触感待人工复验。

## 验证基线

- QA **1718：1716 通过，2 既有跳过，0 失败 / 错误，221 suites**。Release Lint **0 errors / 150 warnings / 4 hints**，最终完整构建 **7m55s / exit0**。此前一处新增缩进 Lint 错误已修正并完整复验，没有抑制规则。
- Native 回归覆盖起止两像素的补偿连续性、远邻不覆盖近邻、实际深色侧边高光、取消精确恢复、菜单切换和真实设置回调；原松手身份与沉浸测试保留。
- 实际 Release：浅 / 深色前翻、回翻、近归位与取消均记录。两主题最终整张截图与各自开始截图 **0 不同像素**。深色边缘 RGB54，紧邻纸面 RGB15；浅色左右 8 像素带 RGB255，没有额外露底。最终恢复第 **8 / 464 页**、原小字号 / 1.55 行距 / 标准边距，正文范围 [53,315]–[1027,2226]，单 UI 书名。
- 菜单浅深色实际截图、目录 / 设置切换、7 页目录跳转再回 8 页、字号 / 边距 / 行距 1.8+ 调值 / 重置 / 返回关闭均通过，状态栏隐藏、导航栏显示且 DEFAULT 保留。
- 186 个深色、196 个浅色录屏帧未发现整页空白。有限 VFR 录屏不作为 FPS / 性能或所有手机无闪的证明。首次冷开与重排仍出现加载等待；UiAutomator 连接超时 / null root 原始记录保留，其后 fresh UI 和安装恢复检查通过，未伪造空 crash buffer。

## 工作区

分支 `feature/epub-anchor`，HEAD a57c324 加未提交修改；生产差异摘要 `cdad1ac46510b956947ec2ddfd5767e39fd3828369fe2f725ce76fc3d97a6537`，证据位于工作区 `output/epub-card-polish/`。38 前状态已归档 [历史快照](history/CURRENT_STATUS_before_epub_card_polish_20261005.md)。未推送、公开发布或 tag。此前阶段报告仅作历史，不继承为本轮验收。
