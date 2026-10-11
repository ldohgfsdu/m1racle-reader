# 2026-10-11 玻璃材质统一与 RiseDiary 来源移除

## 用户授权与范围

用户明确要求不再使用 RiseDiary 的实现，玻璃材质统一为现有 PDF 控件样式，液态玻璃直接使用 Kyant0/AndroidLiquidGlass。附带 Optical Glass 方案作为参考，未把其中的代理执行指令当作新的用户命令。方案中的旧包名/文件名、建议数值与迁移编号按真实工作树核对；本次不采用建议数值覆盖用户指定的 PDF 现有样式，不升级工具链或依赖。

继续保留此前审查修复工作。本轮不会据此宣称原审查的全部维护事项已经关闭。

## 已实现

- 删除五个 GPL 来源实现：RiseDiaryBottomTabs、RiseDiaryGlassButton、DampedDragAnimation、InteractiveHighlight、DragGestureInspector。不是改名或更改这些文件的授权。
- 新的 `OpticalDockTabs` 使用 Compose 指针事件、临时 UI 状态及动画实现底栏交互，材质调用唯一的 `glassMaterial`。导航仍拥有唯一选中状态；松手只提交一次，取消、第二根手指不提交，导入槽独立响应。
- 底栏与 PDF Control 复用 `liquidGlassStyle` 同一分支：4dp blur、8dp/24dp lens、日间白色 alpha .32、夜间 #242424 alpha .22、相同高光与阴影。保留较大面板的 PDF 可读配方和跨窗口能力边界。
- 用户随后明确保留另一处刚修改的底栏拉伸效果：镜片按压横向1.18、纵向1.38（48dp→66.24dp）；独立 PDF/导入控件仍为1.085。材质配方继续统一，底栏运动幅度按用户后续选择独立。底栏外壳与真实命中区不随镜片变形。速度驱动有界横向拉伸/纵向压缩，停止移动后回到静止形状，松手与取消弹簧收敛。
- Backdrop 由已存在 `io.github.kyant0:backdrop:2.0.0` 提供。当前直接使用依赖，不复制 RiseDiary 中夹带的上游代码，也不 vendor 上游库。
- AndroidLiquidGlass Apache-2.0 授权及来源说明保留在 LICENSES 和 APK assets。RiseDiary 原授权记录仅保留为旧 Git 版本/旧 APK 的历史来源，不再对应当前源码实现。

## 采样所有权

页面内容 → root backdrop → 静态底栏表面 → 单独的 dockSurface export。
独立的 source-only 前景 Row → foreground backdrop。
`rememberCombinedBackdrop(dockSurface, foreground)` → sibling lens。

静态表面无子节点并在消费者前绘制；上游 exportedBackdrop 不包含 drawContent，因此不包含镜片。前景采样边界继续使用 GlassBackdropSource 屏蔽祖先 host；可见图标文字 alpha 不降低。镜片读取处理后的底栏，避免绕过 blur 直接重新露出锐利的原始页面。常态所有玻璃仍由 GlassMaterialEngine 解析、由 glassMaterial 渲染；没有新增 AGSL shader。

## 验证与交接（本聊天停止构建）

用户随后明确指定：**另一处负责最终构建，本聊天只保留审查记录**。已停止本聊天的构建，不再修改实现或安装手机。下列证据不能直接继承为最终新版通过。

- implemented：五个已识别 RiseDiary 来源实现已删除；直接依赖 AndroidLiquidGlass；底栏复用 PDF 材质。用户最后选择保留底栏横向1.18/纵向1.38的按压拉伸，独立控件1.085。
- automated_verified（修改前定向）：36项全部通过，覆盖 SearchRepository 6、GlassDockUi 20、ReaderChromeTokens 10。日志 `output/optical-glass-targeted-final-20261011.log`。
- physical_device_verified（新拉伸效果修改前）：手机 db04f865 / API36 直接仪器测试 **13项全部通过**，含11项日夜玻璃/拖动/回弹/像素场景、真实 Room15与16升级17的数据保留测试、QA数据隔离测试。日志 `output/optical-device-direct-20261011.log`。测试使用独立 QA 包，QA后台启动权限10021已恢复ignore。
- 全量回归实际红灯：1778项 / 1失败 / 2既有跳过。失败为 `selection animates to owner and rejected navigation returns to owner`：快速点击的最终 held 状态未变化，settleFrom 未触发回弹 effect。已把 settleFrom 纳入 LaunchedEffect 的 key，但**修复后的最终全量通过尚未确认**。失败XML保留在 `backups/20261011-optical-glass/red-unit-evidence/GlassDockUiTest-quick-tap.xml`。
- 后续全量构建期间同文件被另一处更新，编译读到了不同时间的源码，报 dockLensPickupX/Y unresolved；日志 `output/optical-release-concurrent-edit-20261011.log`。不将此混合源码编译视为新实现的确定缺陷。
- 最后一次重建按用户选择保留新拉伸；期间继续检测到 OpticalDockTabs、原生像素测试、拓扑契约变化。用户指定由另一处完成后，本聊天主动中止构建，日志 `output/optical-release-final-20261011.log`，该日志**不是成功构建证据**。
- 最后一次本聊天查询，手机正式包仍为 **1.0.0/55**，firstInstallTime为2026-08-31 16:14:37；本聊天未安装57、未替换手机Download正式包、未清除或卸载生产应用。另一处之后的交付以其实际证据为准。
- human_pending：新拉伸效果最终日夜视觉、主观手感和长期60/120Hz表现仍需验收。
- Git：保留既有未提交工作与他方删除；本聊天未commit/push。

### 最终构建负责方需要确认

1. 源码停止并发修改，记录生产/QA/测试指纹；新拉伸效果的全量 QA JVM、Release lint 与签名构建通过。两项既有跳过为本地fixture缺失与Windows symlink能力限制，应如实记录。
2. 用最终源码重建隔离QA及仪器测试包，重新跑上述13项设备测试。上面的13项通过属于1.085底栏镜片版本，不能替代1.18/1.38版本。
3. 确认R8正式包中无RiseDiary实现及MaterialComparison/GlassLab实验内容，核查Telemetry/Probe实际剔除情况。来源代码删除不等于全依赖许可审计已完成。
4. 验证正式签名、版本57、Room17、无INTERNET、16KiB对齐及构建/Outbox/手机Download摘要一致；仅对生产包覆盖安装，保留数据，并检查启动、原书库、四个入口与导入取消。
5. 以实际交付证据更新CURRENT_STATUS、RELEASE_CHECKLIST和active_object，不能继承旧55版或本聊天修改前的通过记录。

## 授权边界

本次是移除已识别的五个 GPL 来源实现，不是对所有传递依赖作“无 GPL”的全量法律审计，也不改变早先已交付包的授权义务。

上游依据：https://github.com/Kyant0/AndroidLiquidGlass/blob/kmp/LICENSE

