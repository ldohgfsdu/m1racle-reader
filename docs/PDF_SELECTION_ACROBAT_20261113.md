# PDF 选区与 Acrobat 截图对照

用户反馈 96 的 PDF 选字仍不够自然，提供 Reader 与 Acrobat 的三张截图。本轮范围为手柄、端点与拖动校准；当前分支 `chore/deps-prune-20261108`，在现有 WIP 上继续，旧工作树和正式 96 包保留，不暂存、提交或推送。

## 参考对照

1. Reader 选区（需改进）：原手柄为小蓝圆点、纸色外圈与细引线。主要的可抓取视觉偏小，指示端点与圆心分离。以下为用户本轮提供的图片，不是新的现场设备采样。

![Reader 参考](../output/pdf-selection-acrobat-20261113/01-reader-reference.jpg)

2. Acrobat 选区（参考明确）：饱满实色手柄伸到选区两侧，方角贴住文字边界；左右端点容易辨认。静态图不能证明拖动时的吸附、时延和放大镜行为。

![Acrobat 手柄参考](../output/pdf-selection-acrobat-20261113/02-acrobat-handles.jpg)

3. Acrobat 菜单（参考层级明确）：选区保持在原文上，操作菜单单独出现。Reader 的选字操作继续走现有复制、高亮、批注调用链。

![Acrobat 菜单参考](../output/pdf-selection-acrobat-20261113/03-acrobat-menu.jpg)

## 实现依据

Acrobat 官方说明的基本流程为长按文本、拖动两端手柄、从上下文菜单复制，见[Adobe 官方帮助](https://helpx.adobe.com/acrobat/mobile/edit-text-images/copy-text.html)。手柄几何参考 AndroidX 维护中的标准实现：圆形加一个方角，左端水平镜像，方角为文字端点锚，见 [AndroidX 源码](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/foundation/foundation/src/androidMain/kotlin/androidx/compose/foundation/text/selection/AndroidSelectionHandles.android.kt)（Apache-2.0，项目已依赖 Compose Foundation）。未依赖其 internal API，也未新增库。

Reader 的视觉直径设为 25dp，尖角直接连接字框边界；实色采用现有 Radix Blue9。44dp 触控和视觉大小均按页面缩放反算，绘制、命中与可访问节点消费同一份几何结果。临近页边调整手柄身体朝向，字框锚点不被夹移；底部空间不足时翻到文字上缘。

拖动水平位置从原字符的选区边界加手指位移计算，垂直方向保持行中心偏移。两端采用对应的半开区间规则，宽窄字符变化不再承受原字符半宽偏差；跨过另一端时，由候选位置在同一个指针事件中确定起止归属，避免松手再修正一字。点击未越过拖动阈值仍保持原范围。手势阈值也换算到页面局部坐标，避免 2.5 倍缩放时需要 2.5 倍物理位移才能开始拖动。可访问触控框用逆缩放与浮点位移保持 44dp，消除预先取整被页面缩放放大的中心误差。

## 验证与交付

冻结源码为 `output/pdf-selection-acrobat-20261113/selection-candidate-5-sha256.json`：763 项编译输入，其中 core 141 项；构建后复核零新增、零缺失、零变化。与 96 正式包快照相比只有 8 项输入改动和 1 项新增测试，无输入缺失；差异及补丁同目录保存。测试和打包使用同一份工作树字节，未用提交号替代源码摘要。

| 本轮实际运行 | 结果 |
|---|---|
| 全量 app QA | 1495 项，0 failure / 0 error，1 项既有 Windows 符号链接环境跳过 |
| 全量 app Release | 1310 项，0 failure / 0 error，同上 1 项跳过 |
| core/pdf Release | 465 项，全通过 |
| Reader / core PDF lint | 零错误；Reader 98 warnings、6 hints，core PDF 8 warnings，数量未增加 |
| 选择组件原生绘制 | 日间、夜间、2.5 倍缩放；真实生产 `PdfHighlightLayer`、打包内 Noto CJK 字体；像素及 44dp 节点断言通过，三图已逐张查看 |
| 选择组件手势 | 28 项，包括轻点不改选区、不提前保存、反向/跨行/宽窄字边界、交叉和放大阈值 |
| Code Wiki | 生成与静态链接 / API / 新鲜度检查通过 |

这是宿主原生组件夹具，不是用户原 PDF 的手机截图，也不替代正式 APK / 实机交互。未变化的 database、model、pdf-tool 沿用 [96 报告](AUDIT_FIXES_20261113.md) 的模块证据，不计作 97 新跑。

[日间绘制](../output/pdf-selection-acrobat-20261113/rendered/selection-day-1x.png) · [夜间绘制](../output/pdf-selection-acrobat-20261113/rendered/selection-night-1x.png)

![生产组件 2.5 倍缩放](../output/pdf-selection-acrobat-20261113/rendered/selection-day-2_5x.png)

正式交付：[1.0.0 / 97 APK](</D:/03_M1racle Reader/apk/M1racle-Reader-1.0.0-97.apk>)，26.45 MiB（27732541 bytes）。SHA-256：`4195636FC4033BAA327385A896F56B7CFD8AB96889984C61AE62CBC338E8F32D`。生产证书与 96 一致，代码和资源压缩开启，签名、APK ZIP 与 8 份原生库的 ELF 16 KiB 对齐均通过。构建输出、Outbox 和本机交付摘要一致；minSdk31 / targetSdk35，无 INTERNET、非 debuggable，保留 shell profileable。具体结果见 `apk-97-verification.json`、`verification-summary.json`、`release-attempt-1.log`。

正式 97 包在 API35 软件图形模拟器保留数据升级后：首页、三页 PDF 渲染、长按选择、右端跨行调整、松手保留和复制菜单退出均通过。输入坐标来自 UI 层级及夹具 PDF 的文字几何；`selected-97-ui.xml` / `dragged-97-ui.xml` / `copied-97-ui.xml` 和对应截图记录结果。系统剪贴板正文未回读，模拟器的复制检查仅限菜单路径及选区退出。测试 PDF 自身包含灰色矩形背景，截屏保留其原色。

[正式 APK 长按选择](../output/pdf-selection-acrobat-20261113/selected-97.png) · [正式 APK 跨行拖选](../output/pdf-selection-acrobat-20261113/dragged-97.png)

崩溃缓冲为空；模拟器 Download 和已安装 base.apk 摘要均与交付包一致。首次冷启动曾出现模拟器 System UI 的 Wait 对话框，选择 Wait 并重新启动 Reader 后恢复，原始截图保留；该对话框不计作 Reader 崩溃。本轮使用 540×1200 / density210，结束后已恢复显示参数并关闭本轮模拟器，未清除或卸载生产应用。完整记录见 `emulator-97-smoke.json`。

**尚未验收：**物理手机未连接，原 PDF 未提供；同页 Adobe 对照、中文实文拖动手感、复制全文准确性、实体触控/IME、滚动及性能均 pending。宿主测试和模拟器结果不能替代这些验收。

用户要求的“站起来蹬”子代理（实际后端 `group/group`）负责限定范围的测试补充；主代理检查实际文件、修正夹具计算 / 画布问题并亲自执行完整验证。没有以子代理完成消息代替验收。本次按用户后续授权将已验证源码与文档同步到 GitHub 当前分支。两个旧工作树及 96 正式包保留；`output/` 图像、原始测试与设备附件为本机证据，不入 Git。
