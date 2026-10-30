# 逐字选择、标注与默认沉浸

2026-10-31。正式签名、压缩 Release 1.0.0 / 90，Room17 已交付并在物理手机保留数据覆盖安装。最终隔离 QA 原生回归 47/47 通过，正式包真实中文 PDF / Markdown 选区与阅读显隐已复验。implemented / automated_verified / physical_phone device_verified 完成；人工验收仍 pending。

## 用户可见行为

- PDF 与 Markdown 长按命中实际字符，松手保留选区，通过两端句柄调整范围；浮动操作提供复制、高亮和批注。高亮需明确确认样式，批注进入既有笔记编辑，取消选择不写入标注。
- PDF 文档共用一个选区，连续阅读同时显示多页时，点另一页或留白先取消选区；下一次短点才切换阅读控件。旧页移出视口不会清掉新页的选区。
- Markdown 正文、留白、段落间隙短点整组切换阅读控件；链接、选区操作和拖动不误触发顶栏。保存的高亮按字符范围绘制，已有无字符范围的标注保留整块语义。
- 正文短点整组收起阅读控件，再次短点整组显示；滚动显隐的方向、36/56/32/28 距离阈值及原分阶段行为保持不变。
- 每次新打开 PDF 默认全屏沉浸，系统状态栏和阅读控件隐藏。菜单切换普通模式只影响当前阅读会话，离开 PDF 恢复系统栏。

扫描 PDF 没有文字层时不能逐字选择；OCR 不在本轮范围。高亮与批注保存在 Reader 文档记录中，本轮未增加 PDF 原文件标注写回能力。

## 真机与测试方法

设备为 Xiaomi 17 Pro，ADB serial db04f865，型号 25098PN5AC，Android SDK36。所有 instrumentation 仅针对 com.m1racle.reader.qa，生产包 com.m1racle.reader 只允许 install -r。原 QA 数据、生产 APK85、安装时间和设备设置已保存至证据目录。

该手机批量 instrumentation 曾在第一项完成后回到生产 Reader，使后续 ActivityScenario 失去前台并停住。已保留焦点、日志和中断记录；不将未结束的运行计为通过。最终回归每个方法独立启动一次 instrumentation，先显式将 QA 主界面切到前台，每项限时 120 秒，逐项检查请求的 class#method、OK (1 test) 和退出状态。中断或超时只停止 QA。

## 自动验证

| 检查 | 当前证据 |
|---|---|
| QA JVM | 1880 项，0 failures / 0 errors，2 项既有环境跳过 |
| Release JVM | 1717 项，0 failures / 0 errors，2 项既有环境跳过 |
| Release lint | 0 errors，179 warnings，6 hints |
| 真机最终回归 | 47/47 通过，0 failures / 0 skips / 0 timeouts；phone-native-summary.json |
| 正式签名压缩 APK | 已保留数据覆盖安装 90，release-phone-smoke.json |

full-checks.log 保存完整 QA / Release 单测和 lint 的成功记录，源代码中的 7 项选区会话回归包括有效取消、另一页选择、已清除选择和同页替换后的取消恢复。环境跳过没有被改写为通过。

source-tested-v90.zip 保存这轮完整检查的源码。随后仅修正 Android 测试夹具：按原文下标核对单字、使用 ActivityScenario 管理横向手势测试页面生命周期、等待 WebView 撤销/重做返回精确原文；source-final-v90.zip 保存这些修正。两份冻结间的生产代码、JVM 测试和 lint 输入相同。最终 instrumentation APK 已重建并重跑全部 47 项，正式 Release 使用相同生产源码；verification.json 逐项核对 660 个编译输入和 35 个 assets，明确列出三项 Android 测试文件差异。

最终真机运行目录为 phone-native-run-20261031-043327-289。覆盖 PDF 真实字形与沉浸交互 4、Markdown 阅读积压 10、PDF 手势生命周期 19、配置重建 1、三页翻页条 8、渲染到保存重开 1、Markdown 编辑器旋转 1、编辑桥接 1、QA 数据隔离 1、瓦片像素 1。每项单独完成一次 instrumentation，未用诊断重跑拼接整轮通过结果。

## 正式产物与升级

- 唯一交付：D:/03_M1racle Reader/apk/M1racle-Reader-1.0.0-90.apk。
- 大小：27638646 字节，26.36 MiB。
- SHA-256：450E5AD033F2DE19DC6E2CB5F7A301C0724D9C889E8C8D1298194F9649B22F2F。
- 证书 SHA-256：2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1。
- R8 / 资源压缩、16KiB 对齐、35 个 assets 字节一致通过；包名 com.m1racle.reader，minSdk31 / targetSdk35，无 INTERNET、非 debuggable，保留 profileable android:shell=true。

手机原正式包为 85，仅以 install -r 升级；firstInstallTime 前后均为 2026-08-31 16:14:37。构建、Outbox、交付、手机已安装 base.apk 与 Download 的摘要一致。手机方向 0/0、字体比例 1.1 与测试前一致，正式进程崩溃缓冲没有 fatal。未清理或卸载生产包。

| 正式包场景 | 结果 | 证据 |
|---|---|---|
| 既有 PDF 打开、退出重开 | 保持 44/97，默认沉浸并隐藏阅读控件 | release-pdf-tap-smoke.json、release-pdf-default-immersive.png/xml |
| PDF 正文短点 | 隐藏→整组显示→整组隐藏→整组显示 | release-pdf-controls-shown/hidden/restored.png/xml |
| 既有中文 PDF 长按及句柄拖动 | 显示三项操作和双句柄，终点移动后保留选区；Back 先取消选择 | release-pdf-chinese-selection-smoke.json |
| 既有 Markdown 中文长按 | 可见单字选区、双句柄和复制/高亮/批注；Back 清选区 | release-markdown-smoke.json、release-markdown-plain-paragraph-long-press.png |
| Markdown 正文短点、继续阅读 | 顶栏收起/恢复，返回首页仍为 84% | release-markdown-controls-hidden/restored.png/xml、release-home-final.png/xml |
| 既有 EPUB 打开 | 中文正文正常，返回首页仍为 9% | release-epub-smoke.json、release-epub-open.png/xml |

生产文档没有新增高亮或批注；持久化高亮/批注保存重开的精确回归在 QA 合成资料中完成。测试后恢复原 Markdown 继续阅读文档。Markdown 浮动菜单为非聚焦窗口，常规 uiautomator 活动窗口 XML 不包含该弹层；正式包截图已目视核对三项动作，Compose 真机测试另在多根节点间验证实际动作及持久化，未将 XML 缺失误当成产品缺陷。

release-phone-smoke.json、install-verification.json、final-device-verification.json 保存正式安装与冒烟汇总。源码快照保留实际工作区字节；收尾文档和本地提交另存 delivery-source-v90.zip，final-consistency.json 确认编译输入未变。未推送、未公开发布。

## 诊断记录与验收边界

- 早期模拟器冷启动出现 Process crashed；预热后的诊断仍有失败，原始日志保留，不作为最终通过证据。用户改要求真机后，模拟器正常关闭，数据保留。
- 共享 PDF 选区初次编译因属性委托类型推导失败，已改为只读 Compose State 与显式更新方法；失败日志保留。
- 首轮单方法真机回归 39/47 通过。发现跨页取消后局部浮条显隐状态残留，导致多吞一次点击，已将浮条显隐直接从共享选区及当前手势派生。标注列表测试补齐等待真实弹层出现，保留原有精确断言。
- 两项视口恢复测试硬编码 380dp，超过本机窗口可用宽度而等待超时；测试改为使用窗口约束内的扩展宽度，仍精确核对视口尺寸、页码和原来的页内位置容差。
- 一次 MotionEvent 注入失败，单独诊断重跑通过，未放宽手势或像素断言；最终结果须以修正后的完整重跑为准。
- 第二轮 44/47 通过：一个 Markdown 长按测试的预期字符与其原文不符，已按原文下标修正并增加 charStart/charEnd 精确断言；触摸注入和编辑器等待各一次失败，单独抓日志重跑均通过。诊断与失败证据完整保留，不作为最终整轮通过结果。
- 第三轮 45/47 通过：横向手势夹具再次发生系统事件注入失败，已使用 AndroidX ActivityScenario 替代手动 shell 启动与固定等待；旋转测试在撤销任务提交完成前读取原文，已等待与期望原文完全相同后再断言。既有字符、换行、历史分支、像素及页内位置断言均保留。
- debuggerd/JDB 诊断没有取得有效 Java 线程栈，未 root 手机，未改安全策略。
- 自动注入中文文本通过不等于真实拼音输入法组合验收；持续滚动手感、厂商长截图和人工视觉验收分别保留待验状态。

证据根目录：D:/03_M1racle Reader/output/reading-selection-20261030。
