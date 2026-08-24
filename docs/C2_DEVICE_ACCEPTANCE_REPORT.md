# C-2 Device Acceptance Evidence

| 字段 | 值 |
|---|---|
| 日期 | 2026-08-24（UTC+8） |
| Release | `v0.1.0` |
| Release commit | `5febda3bc0ea2582cdff276457ffec466e072650` |
| versionCode / versionName | `22` / `0.1.0` |
| 设备 | Xiaomi `25098PN5AC`，Android 16，1220×2656 @ 520 dpi |
| ADB endpoint | `198.18.0.1:34195`，state=`device` |
| 最终状态 | **HUMAN_REJECTED** |

## 1. 结论

RC 安装、身份、三类导入、真实来源应用 VIEW/SEND、Markdown 搜索、PDF 连续/横向阅读、capsule、More 菜单和正常导出均通过。

发布验收被 **Export cancellation FAIL** 拒绝：1–384 页 ZIP 导出在界面明确显示“已取消导出”后，SAF 目标 `/sdcard/Download/C2-cancel-mid.zip` 仍残留为 **0 bytes**；关闭完成提示并等待后仍存在。这违反设备验收要求“中途取消后无部分文件残留”。本批仅记录证据，未修改代码或配置。

Pinch zoom 需要真实双指输入；本次 ADB 单指注入不能替代，记录为 PENDING。由于已存在确定性 FAIL，最终状态仍为 HUMAN_REJECTED，而不是设备阻塞。

## 2. 安装与身份

| 检查项 | 状态 | 证据 |
|---|---|---|
| RC 安装 | **PASS** | `adb install -r` → `Success`；冷启动 `MainActivity` → `Status: ok`，`LaunchState: COLD` |
| Package identity | **PASS** | `com.markleaf.app` |
| versionCode | **PASS** | 设备 `dumpsys package` → `versionCode=22` |
| versionName | **PASS** | 设备 `dumpsys package` → `versionName=0.1.0` |
| Tag / commit | **PASS** | annotated tag `v0.1.0` → `5febda3bc0ea2582cdff276457ffec466e072650` |
| APK SHA-256 | **PASS** | outbox、Download、设备已安装 `base.apk` 均为 `e05386e2b9c32002044f7afefcb558efcd2798539c8b90c1f3693ae941b3e534` |
| 启动与崩溃缓冲 | **PASS** | Home 语义树正常；验收时段 crash buffer 无 MarkLeaf `FATAL EXCEPTION`；进程存活 |

APK：`.external_outbox/markleaf-release-latest.apk`，11,493,231 bytes。设备安装路径由 Package Manager 分配；已对其 `base.apk` 本体执行 SHA-256，而非只核对安装前文件。

## 3. 测试输入

为避免中文路径影响可重复的系统选择器操作，向设备 Download 写入以下验收副本；未修改仓库源文件：

| 文件 | SHA-256 | 用途 |
|---|---|---|
| `/sdcard/Download/C2-markdown.md` | `0034892a93c742f291157a4ed77f487474b7bcdc0505a6b40ea9f6515344ccbd` | Markdown / search |
| `/sdcard/Download/C2-heart.pdf` | `71dcde2a648d9540f3d70d8edbf9bf8e364dc7d377aa4e2a7f463ad6d6b35a12` | PDF / VIEW / SEND |
| `/sdcard/Download/C2-package.zip` | `6aaca9a87a8b03a36a71c5497fcc09e17f2d9cf877e2fc4c8827256c8644ff4d` | ZIP package |
| 最近文件中的 `27生理学笔记【合集】（带目录）.pdf` | 设备既有文件，384 页 | 正常导出 / 中途取消 |

## 4. Device smoke matrix

### Import

| 项 | 状态 | 设备证据 |
|---|---|---|
| Markdown | **PASS** | 系统文件选择器导入 `markdown-table-human-acceptance.md`；进入 Reader，表格正文与标题语义可见 |
| PDF | **PASS** | 系统文件选择器导入 `C2-heart.pdf`；Reader 识别 3 页并渲染 page semantics |
| ZIP package | **PASS** | 系统文件选择器导入 `C2-package.zip`；进入 `Fixture A root entry`，根文档与 child 语义可见 |
| External VIEW | **PASS** | 系统文件管理器产生真实 `ACTION_VIEW application/pdf`；Intent URI 为其 FileProvider URI并携带 read grant；MarkLeaf 打开 `C2-heart.pdf` |
| External SEND | **PASS** | 文件管理器“发送”→系统分享面板→“墨阅 MarkLeaf”；任务记录为真实 `ACTION_SEND application/pdf`，含 `ClipData`、STREAM extra 与 read URI grant；MarkLeaf 打开 `C2-heart.pdf` |

说明：直接由 UID 2000 的 ADB shell 伪造 DocumentsProvider URI 被 Android 16 在 ActivityManager 层拒绝，MarkLeaf 未收到该 Intent，因此不计应用 FAIL；最终 VIEW/SEND 证据均来自真实来源应用授予的 URI 权限。

### Reader

| 项 | 状态 | 设备证据 |
|---|---|---|
| Continuous scroll | **PASS** | `C2-heart.pdf` 连续窗口由 pages 1/2 滚动到 pages 2/3，页码到 `3 / 3` |
| Horizontal mode | **PASS** | More → 阅读方式 → 左右翻页；语义树由多页窗口变为单页，左滑 `1 / 3` → `2 / 3` |
| Pinch zoom | **PENDING** | ADB `input` 仅能可靠注入单指；无真实双指人工确认，未声称 PASS |
| Capsule | **PASS** | 折叠页码 capsule 显示 `3 / 3`；点击后打开“跳转到页面”对话框 |
| More menu | **PASS** | 成功打开；阅读方式、阅读界面、设置教材页码、教材目录、页面缩略图、导出章节均可见 |
| Search | **PASS** | Markdown 搜索 `QRS` 返回 `1 / 3`，点击下一项到 `2 / 3` |

### Export

| 项 | 状态 | 设备证据 |
|---|---|---|
| Normal export | **PASS** | 章节 374–384 导出 `/sdcard/Download/C2-normal-export.zip`；应用显示“已导出”；1,590,267 bytes；SHA-256 `5cf028499c59900bc68373436dee758dbe82a7892dba561ec4209b4df9ccdcf0`；`unzip -t` 无错误 |
| Cancellation, late control | **PASS（完成竞态对照）** | 第一次取消动作到达时任务已完成；`C2-cancel-export.zip` 为 79,556,289 bytes 的完整 ZIP，5 个 entry 全部 CRC OK；不计作中途取消证据 |
| Cancellation, true mid-export | **FAIL** | 1–384 页导出，保存后立即点击取消；应用显示“已取消导出”，但 `/sdcard/Download/C2-cancel-mid.zip` 以 0 bytes 残留，关闭结果提示并等待后仍存在 |

## 5. PASS / FAIL / PENDING 汇总

| 类别 | PASS | FAIL | PENDING |
|---|---:|---:|---:|
| 安装与身份 | 7 | 0 | 0 |
| Import / external intents | 5 | 0 | 0 |
| Reader | 5 | 0 | 1（pinch zoom） |
| Export | 1 + 1 个完成竞态对照 | 1（中途取消残留） | 0 |

## 6. 约束遵守

- 未修改代码、UI 或配置。
- 未运行 Gradle，未重新打包、签名或替换 RC。
- 未修复 B4 pending；仅记录新的真实设备证据。
- 设备数据未清除；安装使用 `-r`。
- 测试产物保留在设备 Download，供复核：`C2-normal-export.zip`、`C2-cancel-export.zip`、`C2-cancel-mid.zip`。

## 7. Final status

**HUMAN_REJECTED**

拒绝原因：Export cancellation 的真实中途取消留下 0-byte SAF 目标文件。Pinch zoom 仍为 PENDING，但不是本次拒绝的主因。
