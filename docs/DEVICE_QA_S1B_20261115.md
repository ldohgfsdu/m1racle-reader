# S1b 候选包真机回归记录

日期：2026-11-15
设备：Xiaomi 25098PN5AC（pandora），Android 16 / SDK 36，1220×2656 @520dpi
被测包：`M1racle-Reader-1.0.0-97-s1b-candidate-20261115-1110.apk`
包名 `com.m1racle.reader`，versionCode 97，SHA-256 `e94ddf8a…bbe09`

结论：**PDF 主循环未发现回归。发现 1 个体验缺陷（旧格式文档点击无任何反馈），已修复待复测。**
另有 4 项因设备侧条件限制未测完，逐项注明原因。

---

## 1. 覆盖安装与数据保留

| 项 | 结果 |
|---|---|
| 安装方式 | 同 versionCode 97 覆盖，未卸载 |
| `firstInstallTime` | `2026-08-31 16:14:37`（未变，说明是同一 App 的连续安装） |
| `lastUpdateTime` | `2026-11-15 11:28:55` |
| 设备上的 APK 大小 | 6,745,924 B，与候选包一致 |

安装后文库、最近阅读、书签、PDF 进度全部存在：

```
2026老贺执医辅导讲义-上           PDF 21/632
《2型糖尿病缓解专家共识》…         PDF 1/6
26贺银成实践技能应试指南          PDF 248/420
review-before-after            PDF 2/15
27生理学笔记【合集】（带目录）      PDF 63/384
github                        Markdown 未读   ← 旧格式记录
27内科学笔记【合集】（带目录）      PDF 262/403
男人脸书：男士护肤必修课           PDF 21/328
```

旧 Markdown 记录仍在文库，未被删除或改写。

---

## 2. 逐项结果

| # | 项目 | 结果 | 证据 |
|---|---|---|---|
| 1 | 覆盖安装、数据保留 | **通过** | `firstInstallTime` 未变；9 条文档与进度完整 |
| 2 | 点击旧 Markdown 文档 | **不崩溃、不跳转，但无提示** | 见第 3 节 |
| 3 | 书签页 | 通过 | 全部为 PDF 书签，页码正确 |
| 4 | 首页文档菜单 | **通过** | 只剩 重命名 / 导出文件 / 文件信息 / 从文库移除；「编辑正文」已消失 |
| 5 | 打开 571 MB PDF | **通过** | 点击后 1.2 s 已出页，无空白 |
| 6 | 快速连续滚动 | **通过** | 8 次快速上滑 + 4 次反向，无空白页、无 ANR、无崩溃 |
| 7 | 双指缩放 | **通过** | 注入真实多点触控（event8），页面放大、矢量清晰、页码不变 |
| 8 | 目录 | **通过** | 72 项，当前项高亮；点「4 血液循环」页码 69 → 43 |
| 9 | 进度保存 | **通过** | 退出后首页显示 44/384 |
| 10 | 杀进程恢复 | **通过** | `force-stop` 后重开仍是 44/384，「继续阅读」进入直接落在 44 页 |
| 11 | 导入 PDF | **通过** | 选择器只列 PDF；导入后自动打开，进入文库 |
| 12 | 拆分 PDF | **通过** | 「已生成 1 份 PDF，并完成文件回读」；产物 `%PDF-1.4` / `%%EOF` / 3 个页对象 / 78,422 B |

### 导入的 MIME 过滤证据

设备 `/sdcard/Download` 里有 **140 个 .md、75 个 .zip、98 个 .apk、144 个 .png**，系统选择器只列出 PDF。这是把 `arrayOf("application/pdf")` 收敛后的直接效果。

### 缩放是怎么测的

adb 的 `input` 不支持多点，所以用 root 直接写 `/dev/input/event8`（主触摸屏，坐标单位 1/100 px）注入两指序列：两指从 ±100 px 分开到 ±300 px，10 步插值，然后抬起。脚本在 `output/device-qa-20261115/pinch.sh`。

---

## 3. 发现的问题（已修复，待复测）

**旧格式文档点击后没有任何反馈。**

`openDocument` 对 Markdown / EPUB 直接 `return`，既不导航也不提示。用户看到的是「点了没反应」，比明确拒绝更像故障。

修复：`openDocument` 增加 `onUnsupported` 回调，5 个调用点（首页事件、首页列表、文库列表、搜索结果、PDF 工具）统一传入同一个 snackbar 提示「该格式已不在支持范围，无法打开」。书签页原本那条提示复用同一资源，资源名从 `bookmark_unsupported_format` 改为 `unsupported_document_format`。

---

## 4. 未测完的项与原因

| 项目 | 状态 | 原因 |
|---|---|---|
| 标注（长按选区 → 高亮/批注） | **未测** | adb 长按注入在 PDF 文本层上不稳定；测试中途设备被切到其他 App，停止自动操作以免干扰 |
| 导出章节 | **未测** | 与拆分共用 SAF 输出链路；拆分已验证该链路可用，未单独跑 |
| 导出文件到存储根 | **受阻** | MIUI 的 SAF 拒绝写入存储根（「无法使用此文件夹 / 为保护您的隐私，请选择其他文件夹」）。选子目录后链路正常。这是系统策略，不是 App 缺陷 |
| 旧格式书签点击 | **无样本** | 该设备书签表里只有 PDF 书签，没有 Markdown/EPUB 书签可点。代码路径已覆盖（`Routes.bookmark` 返回 null + 提示），但没有真机样本 |
| 设置页外观面板 | **未测** | 与 PDF 主循环无关，本轮未触及 |

---

## 5. 本轮未改动的代码（回归风险判断依据）

```
git diff github/master..HEAD -- app/src/main/java/com/m1racle/reader/ui/pdf \
                                core/pdf \
                                app/src/main/java/com/m1racle/reader/ui/annotation \
                                app/src/main/java/com/m1racle/reader/data/anchor
```

**空。** PDF 渲染、手势、标注、锚点四条路径在本轮一次都没被改过。真机上这些功能正常，属于「没被碰过所以没坏」，不是「改完验证过没坏」。

---

## 6. 证据文件

`output/device-qa-20261115/`：

| 文件 | 内容 |
|---|---|
| `01-home.png` | 覆盖安装后首页，旧进度完整 |
| `02-markdown-tap.png` / `03-markdown-toast.png` | 点击旧 Markdown 文档：无跳转、无提示 |
| `05-pdf-open.png` | 书签跳转打开 PDF |
| `07-chrome.png` | 滚动后页码 70/384 |
| `09-toc.png` | 目录 72 项，当前项高亮 |
| `10-toc-jump.png` | 目录跳转后页码 43 |
| `12-after-kill.png` | 杀进程后 44/384 |
| `14-resume-chrome.png` | 继续阅读落在 44 页 |
| `16-after-pinch.png` | 双指缩放后 |
| `22-fast-scroll.png` | 快速滚动后无空白 |
| `26-imported.png` | 导入后自动打开 |
| `31-split-screen.png` / `33-split-done.png` | 拆分界面与完成提示 |
| `split-output.pdf` | 拆分产物（78,422 B，3 页） |
| `pinch.sh` | 双指注入脚本 |
| `ui-*.xml` | 各步骤的界面控件树 |
