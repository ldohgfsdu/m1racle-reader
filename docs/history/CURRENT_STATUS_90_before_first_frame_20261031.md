# 当前状态

2026-10-31：正式签名、压缩 Release **1.0.0 / 90，Room17**。开发仓库 D:/03_M1racle Reader/reader-ui-release-20261025，分支 codex/reader-stability-20261030；本机构建使用 reader-env-local.ps1 的 $readerRepository。原 reader 与 reader-audit-fixes-20261024 工作树保留。

90 修复 PDF 与 Markdown 长按逐字选择、两端句柄、高亮和批注；正文短点整组隐藏/显示阅读控件，有选区时先取消选择。PDF 新会话默认全屏沉浸并隐藏控件。滚动显隐沿用原方向、距离阈值和分阶段行为。继承 89 稳定性修复及 87 界面品牌基线。详见 [本轮实现与真机证据](TEXT_SELECTION_AND_IMMERSION_20261031.md)。

唯一交付：D:/03_M1racle Reader/apk/M1racle-Reader-1.0.0-90.apk，27638646 字节（26.36 MiB）。SHA-256：450E5AD033F2DE19DC6E2CB5F7A301C0724D9C889E8C8D1298194F9649B22F2F。生产签名、16KiB 对齐、R8 / 资源压缩、35 个 assets 同源校验通过，无 INTERNET、非 debuggable，保留 profileable shell=true。构建、Outbox、交付、手机已安装包与 Download 摘要一致。

同源 QA 单元测试 1880 项、Release 单元测试 1717 项，均 0 failures / 0 errors，各 2 项既有环境跳过；lint 0 errors / 179 warnings / 6 hints。最终物理手机隔离 QA 原生回归 **47/47 通过**，0 failures / 0 skips / 0 timeouts，覆盖选字、标注保存重开、句柄、显隐、旋转、翻页缩放、视口锚点、瓦片像素和数据隔离。未放宽字符、位置、历史分支或像素断言。

Xiaomi 17 Pro（25098PN5AC，Android SDK36，db04f865）已从正式 85 保留数据覆盖安装 90，firstInstallTime 仍为 2026-08-31 16:14:37。既有 PDF 保持 44/97 页，默认沉浸、整组点击显隐和重开通过；真实中文文字层长按及句柄拖动通过。Markdown 真实中文单字选择、菜单与句柄、点击显隐通过，继续阅读仍为 84%；既有 EPUB 正文打开，进度仍为 9%。生产文档未新增标注，持久化高亮/批注回归在隔离 QA 合成资料中完成。

手机系统方向 0/0、字体比例 1.1 与测试前一致；正式进程崩溃缓冲无 fatal。模拟器已正常关闭，保留其数据。本轮没有做性能对照采集；真实拼音输入法、持续滚动手感、性能、TalkBack 和厂商长截图仍待专门验收。89 的模拟器帧统计保留为历史诊断，不作为本轮真机性能结论。

本轮 implemented / automated_verified / physical_phone device_verified 已完成，human_acceptance 仍 pending。扫描 PDF 没有文字层时不支持逐字选择，OCR 不在范围。源码与文档本地保存，未推送、未公开发布。证据：D:/03_M1racle Reader/output/reading-selection-20261030；[89 状态归档](history/CURRENT_STATUS_89_before_text_selection_20261031.md)。
