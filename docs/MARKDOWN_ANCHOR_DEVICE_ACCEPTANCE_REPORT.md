# Markdown 页内锚点修复 — 真机验收报告

**Date:** 2026-07-21  
**Scope:** HTML empty anchors / 页内 `#` 跳转 / 侧边 TOC 标题归一化 / 文末孤立锚点语义  
**Candidate variant:** **release**（minify，非 QA）

---

## 1. 安装包

| 项 | 值 |
|----|-----|
| Build variant | `release` (`:app:assembleRelease`) |
| Package name | `com.markleaf.app` |
| Outbox latest | `.external_outbox/markleaf-release-latest.apk` |
| Timestamped | `.external_outbox/markleaf-release-v0.1.0-20260721-065447.apk` |
| Frozen copy | `dist/markdown-anchor-device-acceptance/markleaf-release-v0.1.0-20260721-065447.apk` |
| Phone Download | `/storage/emulated/0/Download/markleaf-release-latest.apk` |
| SHA-256 | `5fe04fe4c7f9d4476df7084655ea15dc71f221b3aa2ee5653e8855590c9cab0f` |
| Size | 10 924 039 bytes (~10.4 MB) |

---

## 2. 文末孤立锚点语义审查与修复

### 问题

先前实现将 **文末孤立** `<a id|name>` 绑定到 `blocks.lastIndex`。  
Registry 只能表达 **block 顶部**，因此点击该 fragment 会滚到 **最后一个块顶部**，而不是文档末尾 / 最后一块底部。

### 决议（按产品语义）

| 场景 | 行为 |
|------|------|
| 锚点后有下一个可渲染块 | 绑定到该块（优先标题） |
| 文末孤立锚点（无下一块） | **不注册**；记入 `ParseResult.ignoredTrailingHtmlAnchors`；`ViewerViewModel` 打 `MARKDOWN_TOC html_anchor_trailing_ignored` |
| 架构能否表达末尾/块底 | **不能**（仅 `blockIndex` → item top）→ **安全忽略**，禁止误绑 last-top |

### 代码

- `MarkdownParser.kt`：删除 last-block flush；暴露 `ignoredTrailingHtmlAnchors`
- `ViewerViewModel.kt`：加载时 `Log.w` 记录被忽略的 id

### 单元测试

- `HtmlEmptyAnchorTest.trailingOrphanAnchors_areNotBoundToLastBlockTop`
- `TocTitleAndInPageLinkTest.trailingHtmlAnchor_isIgnoredNotBoundToLastBlockTop`
- `TocTitleAndInPageLinkTest.trailingHtmlAnchor_onlyDocument_isIgnored`
- `TocTitleAndInPageLinkTest.anchorBeforeNextBlock_stillBindsToThatBlock`

---

## 3. 同批修复（验收路径必需）

验收文档《老子（帛书精堪版）-整理排版版》中：

- 侧栏标题：`### [德篇](#de-pian)` → 显示「德篇」
- 正文/表格：`[第一章](#chapter-01)` 在 **表格单元格** 内

因此本轮一并完成：

1. TOC 纯文本标题 + heading 内 `#fragment` 注册（前序）  
2. **表格单元格链接可点击**（`TableBlockView` → `onLinkClick`）  
3. 标题内链接可点击（`HeadingBlockView`）  
4. `MarkdownLinkRouter` 分流 `#` vs http(s)

---

## 4. 离线 / 主机验证（fixture + unit）

**Fixture:** `/storage/emulated/0/Download/老子（帛书精堪版）-整理排版版.md`

| 检查 | 结果 |
|------|------|
| 整文件 parse（`LaoziAnchorFixtureTest`） | **PASS**（1/1，0 skip） |
| HTML 空锚点数量 | 83，**无重复 id** |
| `de-pian` / `dao-pian` / `chapter-01` 绑定下一标题 | PASS（结构 + registry） |
| TOC 标题「德篇」「道篇」无 `[]()` 源码 | PASS |
| 表内 `#chapter-01` destination 保留 | PASS |
| 缺失 fragment resolve null | PASS |
| 文末无孤立锚点（fixture 以正文结束） | PASS |
| `com.markleaf.app.markdown.*` unit suite | **BUILD SUCCESSFUL** |

---

## 5. 真机环境与安装状态

| 项 | 值 |
|----|-----|
| 型号 | **25098PN5AC**（Xiaomi / pandora） |
| 系统 | **Android 16** |
| ADB | **offline**（`adb devices` 无 `device`；历史无线端口拒绝连接） |
| Shell `pm install` | **失败**：MIUI `XSpaceManagerService` → `SecurityException: MANAGE_USERS / CREATE_USERS` |
| 已安装包 SHA-256 | `bf8dc0ca786aa553c8ca76795aac956661752fbc4943a39667ac79520a2ad7fa`（**≠ 候选**，旧包 2026-07-20） |
| `am start` | **失败**：`INTERACT_ACROSS_USERS`（Termux 无跨用户启动权限） |

候选 APK 已放入 Download，并曾 `termux-open` 拉起系统安装器（需人手点确认）。**截至报告时安装哈希仍未变为候选包。**

按 `docs/DEVICE_INSTALL_AND_ADB_TEST.md`：**禁止在未装上候选包时写真机 PASS。**

---

## 6. 真机验收清单（对《老子》整理排版版）

| # | 项 | 结果 | 说明 |
|---|----|------|------|
| 1 | HTML 空锚点不可见且无额外间距 | **NOT_RUN** | 需候选包 UI |
| 2 | 侧边目录只显示「德篇」「道篇」 | **OFFLINE_PASS** / **NOT_RUN UI** | parse/TOC unit + fixture PASS；UI 未跑 |
| 3 | 点击正文「德篇」→ 德篇标题 | **NOT_RUN** | 需 UI；heading 已接线 |
| 4 | 点击表格「第一章」→「第一章 论德」 | **NOT_RUN** | 需 UI；表格链接已接线 |
| 5 | 跳转避开状态栏 + 顶栏 | **NOT_RUN** | 既有 `forceShow` + `headingTopPadding` offset |
| 6 | 连续跨章节往返 ≥ 20 次 | **NOT_RUN** | |
| 7 | 缺失 fragment 安全失败、不打开浏览器 | **OFFLINE_PASS** / **NOT_RUN UI** | router + registry unit PASS |
| 8 | http/https 外部链接原行为 | **OFFLINE_PASS** / **NOT_RUN UI** | `MarkdownLinkRouter` unit PASS |
| 9 | 横竖屏后跳转仍正确 | **NOT_RUN** | |
| 10 | 切换文档后 registry 不串档 | **NOT_RUN** | ViewModel 按 document 重 parse；需 UI |
| 11 | logcat 无 FATAL/ANR/锚点冲突异常 | **NOT_RUN** | 未装候选包，未做跳转压测 |

### 专项观察（离线）

| 观察 | 结果 |
|------|------|
| 重复 anchor id（fixture） | **无**（83 unique） |
| 文档切换 registry 残留 | 代码路径每次 `load()` 新 `ParseResult`；**未真机验证** |
| offset 偏差 | 未真机测量；逻辑仍为 heading 内顶 padding 补偿（历史 R 报告曾 device_accepted） |
| 文末孤立锚点误绑 last-top | **已修**（忽略 + 日志） |

---

## 7. 最终结论

### **rejected**（真机验收未关闭）

**原因：** 候选 release 已构建并交付 Download/outbox，但 **未能安装到 25098PN5AC 上的 `com.markleaf.app`**（ADB 离线 + 非 root shell 的 `pm install` 被 MIUI 权限拒绝），已装包 SHA **不等于** 候选 SHA。全部依赖 UI 的验收项 **NOT_RUN**。

### 已完成（可复核）

- 文末孤立锚点语义修正 + 单测  
- 表格/标题页内链接点击接线  
- release 候选包构建与哈希固定  
- 《老子》fixture 离线 parse 验收 **PASS**

### 人类下一步（关闭验收）

1. 打开 `/storage/emulated/0/Download/markleaf-release-latest.apk` 手动安装（或开启无线调试后 `bash scripts/install-release-outbox.sh`）  
2. 确认安装后 `pm path` / 安装目录 base.apk SHA-256 = `5fe04fe4c7f9d4476df7084655ea15dc71f221b3aa2ee5653e8855590c9cab0f`  
3. 用《老子（帛书精堪版）-整理排版版》按 §6 跑 UI 清单  
4. 通过后将本报告结论改为 **accepted** 并补 UI 证据

---

## 8. 主要代码路径

| 文件 | 变更 |
|------|------|
| `markdown/MarkdownParser.kt` | 空锚点；TOC 纯标题；文末忽略 |
| `markdown/MarkdownLinkRouter.kt` | `#` / 外部分流 |
| `markdown/InlineMarkdownParser.kt` | `extractLinkDestinations` |
| `ui/viewer/ViewerScreen.kt` | router 驱动跳转 |
| `ui/viewer/ViewerViewModel.kt` | trailing ignored 日志 |
| `ui/viewer/components/TableBlockView.kt` | 单元格链接点击 |
| `ui/viewer/components/HeadingBlockView.kt` | 标题链接点击 |
| `test/.../HtmlEmptyAnchorTest.kt` 等 | 单测 |
| `test/.../LaoziAnchorFixtureTest.kt` | 真书 fixture 离线 parse |
