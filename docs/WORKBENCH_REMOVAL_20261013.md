# 工作台取消、正式版复验与新参考（2026-10-13）

用户决定：“不要这个工作台，我们直接取iOS的材质现在”，随后提供 iOS App Store 录屏作为“新标准”。本轮已完成工作台删除及验证，并把录屏转为[逐状态视觉标准](IOS_MATERIAL_STANDARD_20261013.md)。当前状态见 [CURRENT_STATUS](CURRENT_STATUS.md)。

## 实施结果

- 删除工作台设置入口、回调参数、导航路由及 QA / production 桥接函数。
- 删除独立预览、参数模型、调参页面及 shader 四个 QA 文件，移除 30 个专属字符串；QA 也不保留被取消功能。
- 正式版契约验证工作台类、路由和资源不存在，实验开关关闭；QA 导航回归要求旧路由被拒绝，并验证文档工具往返。
- 同步 design.md、文档索引及 Code Wiki；旧工作台报告标为历史。
- 视频原件已备份并核对摘要，提取静止、按住、各位置拖动及背景变化；新标准优先于旧参考。

本轮没有修改 Dock 光学、统一材质、数据库或生产依赖。五个材质 / Dock 源文件与 version.properties 的摘要均与上午基线一致，见 material-and-version-unchanged.json。新增视频标准是后续视觉调整依据，不能写成“当前材质已匹配”。

## 测试调整及依据

取消功能后，原有 5 项工作台参数测试、2 项预览交互及 1 项原生输入测试随功能删除。原设置测试的两项替换为 QA 无工作台入口、文档工具仍可点击；保留功能的测试与像素阈值没有放宽。

24 个依赖 Compose 测试 Activity 的 UI 文件归入 testQa。移动前后逐文件 SHA-256 一致，映射见 qa-ui-test-moves.json。测试宿主来自 QA 的 ui-test-manifest 依赖，正式资源包不含该 Activity；没有为测试向正式包加入页面。公共逻辑仍在 test，正式版专属契约在 testRelease。QA 全量仍执行全部保留测试，没有增加忽略、排除或跳过条件。

首次完整 Release 执行还暴露 5 项统计预期错误：正式版按既有政策关闭 MarkdownPerformance，旧测试却要求 QA 计数。两类公共测试现分别验证 QA 的精确计数、Release 的零计数及空归因；文本解析 / 样式与缓存复用断言保留，夹具解析计数由非零检查加强为精确数量。

导航补验使用真实文档工具页。Robolectric 夹具补齐共享目录、主存储卷及未授权的 all-files 状态，避免框架在空卷数组上失败；未修改生产权限或扫描逻辑。相关构建系统约定见 [Robolectric 官方说明](https://robolectric.org/build-system-integration/)，本机具体诊断保存在证据目录。

## 最终验证

| 检查 | 结果 | 证据 |
|---|---|---|
| Full + PrepareDeviceTests | 成功，11 分 47 秒，退出码 0 | 仓库 output/verification/full-20261013-134849.log；full-final-entry.log |
| 全量 QA JVM | 1,780 项，0 失败 / 错误，2 环境跳过 | full-jvm-results.json、full-qa-results.zip；234 个 XML |
| 全量 Release JVM | 1,637 项，0 失败 / 错误，2 环境跳过 | full-jvm-results.json、full-release-results.zip；209 个 XML |
| Release lint | 0 errors / 170 warnings / 4 hints | lint.json、app/build/reports/lint-results-release.xml |
| QA 应用与 instrumentation | 当前源码构建成功，包名核验后保留数据安装 | qa-ui-result.json 中记录两份 APK 大小和 SHA-256 |
| API 35 模拟器流程 | 无工作台入口、文档工具打开、返回设置恢复 Dock、返回首页，通过 | qa-ui-result.json、qa-settings.png、qa-document-tools.png、qa-settings-returned.png；界面树及截图已检查 |
| 生产包保护 | 安装前后版本及安装时间一致，无生产包安装 / 清库 / instrumentation | qa-ui-result.json；操作只针对 QA 包 |
| Code Wiki | 20 篇、3,126 相对链接、3,032 源码链接、113 关键 API，通过 | docs/code-wiki/reference/validation.json |

两种 JVM 构建各自的两项跳过仍为外部老子夹具未挂载、环境不支持符号链接。不是全部 case 均执行通过。

本轮未重新执行原生 instrumentation。上午核心 24/24、Dock 27/29 属于[首轮核查证据](STABILIZATION_20261013.md)，不冒充本轮或新视频匹配结果。

## 回溯与执行环境

证据在工作区 output/workbench-removal-20261013/；修改前相关 17 个文件保存在 before-removal.zip，SHA-256 为 518522120918a39124f799c0144e6c7eafc3b96e2a9790c0780b77fe91394689。完整原始源码检查点仍见[首轮报告](STABILIZATION_20261013.md)。未 reset / stash / commit / push 用户修改。

中间一次 JVM 因主机提交内存不足退出，日志及 hs_err 已归档，不能计为有效测试。最终暂停本任务的模拟器，使用 Gradle 1,024 MiB、测试 JVM 512 MiB、单 worker、每 40 类 fork、有效处理器 2；没有修改全局内存配置。Full 完成后恢复原模拟器并完成 QA 界面核对，最后恢复既有生产应用到前台。

## 当前边界与下一步

工作台需求冲突已消除。当前交付仍为既有 1.0.0 / 71，未构建或分发新正式 APK。

Dock 浅色左端帽两项原生失败仍未解决，新录屏也要求该位置有清楚轮廓。下一阶段按新视频调整实际采样 / 折射，保存同场景前后对照，完整复验 29 项及动态拖动 / 取消；再做正式签名构建与保留数据升级。物理手机未连接，手机动态视觉与手感仍 pending。
