# Reader 同系列图标替换

2026-10-24，工作树 `reader-audit-fixes-20261024`。按用户的 Note 图标与品牌母标实现白底、深色完整 M 和 Reader 字标。Note 正式品牌资产未修改；本轮仅改变应用图标资源和版本字段，阅读/编辑代码与79版一致。

## 资产与来源

两张用户输入与 Note 正式资产的 SHA256 分别相同。本轮复用既有 master-trace.svg，不重画母标；原始1254px位图仍为权威源，已在 Reader 资产包内保留。Reader 使用同款 Inter Medium500 / opsz32 的真实字形轮廓，SVG 和 Android 均不依赖设备安装字体。

资产目录 `branding/m1racle-reader/v1/`：母标原图、既有矢量、字体原文件/OFL、来源摘要、独立 SVG/PNG、SHA256SUMS。生成器 `scripts/branding/build_reader_icon.py` 读取仓库内资产，可重复生成，不依赖 Note 工程。512预览沿用 Note 的母标/字标结构；Android前景透明，背景白色，圆角/圆形由系统蒙版决定，另保留系统着色图标支持。

静态检查：母标1条、Reader字形6条轮廓；前景最远包围盒角距30.912dp，小于33dp安全半径。字体使用nonZero填充，母标使用evenOdd；实际 APK 解码确认 `[1,0,0,0,0,0,0]`，不是只验证源文件。首次打包的字形填充设置与源字体不一致，已修正并重新构建；前次候选未交付或安装。

## 最终正式包

| 项目 | 值 |
|---|---|
| 版本 | 1.0.0 / 80，Room17 |
| 包 | `.external_outbox/m1racle-reader-release-latest.apk` |
| 字节 | 27,588,652 |
| SHA256 | `0e993182343d664ffaab3b6d0121229cec98419ebfc2e290a40f322c43b735b6` |
| 原证书SHA256 | `2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1` |
| 构建 | `build-icon-final.log`，BUILD SUCCESSFUL，3m24s |
| 一致性 | 构建/Outbox/已安装base.apk/模拟器Download摘要一致；签名、16KiB对齐通过；28个编辑器资源一致 |

## 实际验收

Android35 `emulator-5554`，同签名 install-r 更新，未卸载、未清库。firstInstallTime仍为2026-10-03 06:05:17，原书库继续阅读和最近列表正常加载。

- 系统应用列表 `emulator/launcher-apps.png` 与 XML：Note 和 Reader 并排，Reader 的新白底母标图标、Reader 字样与圆形裁切可见，已人工查看。
- 根据新图标的实际 UI 节点点击 M1racle Reader，启动到正式应用首页；topResumedActivity确认com.m1racle.reader/.MainActivity。
- installed-package.txt确认80；已安装APK回读摘要一致；Reader crash buffer为空。
- 旁边保留的蓝色旧图标属于隔离包 com.m1racle.reader.qa，不是正式应用。本轮没有重建或交付QA包。
- 首次 Home 捕获为冷启动黑帧，不作为验收；QA脚本改用唯一远端XML文件并校验dump成功，避免把旧XML与新截图混用。最终使用稳定的 launcher-apps 与 release80-loaded 证据。

本轮是资源替换，没有重跑完整JVM或编辑仪器测试，不将79及78的历史验证冒充80重跑。前轮同页编辑与顶栏撤销重做实现保留；业务源文件与79冻结输入比对，仅ic_reader_mark.xml、colors.xml、version.properties三处不同。物理手机和用户最终视觉认可未计为通过。

图标预览：`branding/m1racle-reader/v1/png/icon-512-day.png`。对照板：`output/reader-icon-20261024/icon-comparison.png`。

没有提交、推送、公开发布，也未修改原reader工作区的既有改动。源码差量与品牌资产包不含签名密钥、缓存或用户私有备份。
