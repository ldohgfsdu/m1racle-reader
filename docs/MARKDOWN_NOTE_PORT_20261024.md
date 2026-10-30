# Markdown 预览编辑与顶栏修复验收

2026-10-24。隔离工作树 `reader-audit-fixes-20261024`，基线 `615ded1`，包含先前 F01–F10 修复。原 reader 工作区未修改；本轮未提交、未推送、未公开发布。

## 用户打开与编辑流程

书库打开独立 Markdown → 阅读页右上角「更多」→「编辑正文」。默认直接点击排版正文输入；底栏「源码」切至高亮 Markdown 标记，「正文」切回，内容、光标和撤销记录共享。顶栏「保存」更新书库副本，返回阅读后刷新。编辑页「更多」提供「导出 Markdown」「保存到原文件…」，后者通过系统文件选择重新授权。未保存修改有退出确认，并在进程重启后恢复草稿。Markdown 资源包需先导出为独立 Markdown，未开放 EPUB/PDF 通过此编辑器编辑。

## 实现

- 移植 Note CodeMirror / Atomic 预览和源码保真层，移除原整页输入框与逐段弹框双路径。Markdown 原文为唯一内容来源，不走 AST 重序列化。
- 原生管理文档身份、不可变副本提交、版本核验、草稿与文件操作；编辑器只处理文本与历史。组合输入中暂缓切换和保存。
- 编辑页简化为文件名、返回、保存；底栏撤销/重做和「正文/源码」，随键盘上移；最终实测修正底栏四字标签折行问题。
- 阅读顶栏为单行标题、48dp 操作触区、实色背景与细分隔线；表面与控件同步收起，不留白带，正文不重排。保留 EPUB 共用正文排版参数。
- WebView 仅加载固定应用内 HTTPS origin，禁止文件/content/网络访问、子框架与页面跳转。未授权邻图显示带 alt 的占位；链接由原生校验协议后交系统处理。
- Reader 自带锁定依赖、源码、许可证与冻结资源。Gradle 校验资源与源码摘要；独立安装 Reader 依赖并重建，28 个运行文件 hash 全部一致。
- 修正草稿恢复完成前提前挂载编辑器的竞态，以及保存期间晚到输入丢失草稿的问题。新增慢 IO 草稿恢复与保存中继续输入回归。

## 构建产物

| 项目 | 最终值 |
|---|---|
| 包 | `.external_outbox/m1racle-reader-release-latest.apk` |
| 版本 | 1.0.0 / 78，Room17 |
| 字节 | 27,581,188（约 26.30 MiB） |
| SHA-256 | `fe9752e66bc3c3f0510e83f813c103ec9741061f4f23e85d7e3f4632b5335913` |
| 签名 | 原生产证书，SHA-256 `2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1` |
| 完整性 | APK/Outbox/实际安装 base.apk/模拟器 Download 均相同；28 个编辑器运行文件及 manifest 与源码一致 |
| 检查 | apksigner 验证通过；zipalign 16KiB 检查通过；正式混淆包 |
| 最终构建 | 14:42:20 产出，BUILD SUCCESSFUL，6m7s |

## 自动验证

| 检查 | 结果与证据 |
|---|---|
| 完整 QA / Release JVM | 1,824 / 1,681 项，0 失败、0 错误，各 2 项既有跳过；`full-passed-unit-results.zip` |
| 最终权限修正重点回归 | 5/5；`post-permission-focused-result.json` |
| lintRelease | 0 错误、170 警告、4 提示；较前轮新增 1 项 Uri.parse UseKtx 建议，未宣称警告全清 |
| Node 原文/历史 | 9/9，覆盖 BOM、混合换行、局部编辑与撤销 |
| 真实 Chromium | 渲染、模式切换、光标、共享历史、只读、组合状态、主题、图片无外部请求；真实键盘粗体与表格输入保真；`browser-live-result.json` |
| Reader 独立重建 | npm ci 成功；独立目录重建 28 个运行文件摘要一致；`frontend-rebuild-result.json` |
| 原生字体/绘制回归 | 顶栏 4 项通过，含窄屏双倍字号、省略、48dp 触区、点击和正文边界 |
| Android 35 隔离仪器测试 | 2/2，14.04s；日夜实色顶栏像素及收起边界、离线 WebView BOM/混合换行模式保真；`emulator-instrumentation.log` |

完整 JVM 在等价 URI 权限掩码改写前完成；改写后重点 5 项及 lint、R8、Release、QA 仪器包构建通过。最后仅将底栏标签改为单行「正文」，重建正式 Release 并完成下述实际流程验收，未声称完整 JVM 在最后文案上重新执行。

## 最终 Release 模拟器实测

Android 35 `emulator-5554`，同一原签名 `install -r`；firstInstallTime 仍为 `2026-10-03 06:05:17`。更新前已备份旧主包与 QA 包的私有文件、数据库、偏好及摘要，未卸载或清库。私有备份仅本地保留，不打进源码交付包。

- 系统 Files 导入合成 Markdown → 阅读 → 更多 → 编辑正文，真实 WebView 与混淆后原生桥正常。
- 实际键盘输入 QA-78，源码切换、撤销移除/重做恢复、保存成功；键盘上方工具栏无重叠。
- 输入 DRAFT-78 后不保存，强停应用、关闭模拟器、重建并更新 APK；重新打开编辑器恢复该草稿；保存返回原生阅读页显示新正文。
- 日夜阅读/编辑截图均人工查看；最终「正文/源码」单行。上滑收起顶栏时无残余白带，下滑恢复；目录跳转「关于保存」位于顶栏下，搜索 QA-78 显示 1/1。
- 「保存到原文件…」实际走系统授权并回读测试原文件，只有 QA-78 和 DRAFT-78 两处预期插入，其余字节不变。
- BOM + CRLF/LF/CR 测试文件经系统导入、正文/源码切换、导出新文件，73 字节与输入逐字节相同；`release-export-fidelity-result.json`。
- 实际安装包与模拟器 Download 的 SHA-256 与最终 Outbox 一致；`release-emulator-final-result.json`。本次启动后 Reader crash buffer 为空。

证据截图：`emulator/final-reader-light.png`、`final-reader-dark-stable.png`、`final-editor-ready.png`、`final-editor-dark-stable.png`、`reader-collapsed.png`、`toc-jump.png`、`search-result.png`。每张对应 XML；部分早期过渡截图不代表稳定页面，以这些命名为准。

## 验收边界与保留事项

物理手机、物理中文输入法、低内存压力和用户最终审美认可未验证。冷启动模拟器曾出现 System UI 无响应提示，选择 Wait 后继续，未将此当作 Reader 崩溃；Reader crash buffer 为空。手工拼接 adb 外部 VIEW URI 未进入文档，不计外部打开通过；正常系统 Files 导入已通过。仪器测试 QA 与真实正式 Release 用户流程分开记录。

先前 F01–F10 的修复说明见 `docs/AUDIT_FIXES_20261024.md`。未新增网络权限，未触碰物理手机或生产用户文档。源码检查点和变更包用于保留本次工作，不包含签名凭据、依赖目录、构建缓存或用户私有数据。
