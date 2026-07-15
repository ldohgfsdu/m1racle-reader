# R8 Markdown Relative Asset V1 真机验收报告

**Active object:** `R8_MARKDOWN_RELATIVE_ASSET_RESOLUTION_V1`
**验收执行:** 2026-07-15（设备真实 UI + 真实解码，非 mock / 非仅单元测试）
**Commit:** 未提交

---

## 1. 环境

| 项 | 值 |
|---|---|
| 分支 | `master` |
| HEAD（git） | `f34b57d0dc929a5bd7f1ee0d4fd8f5e62450c726` |
| 工作区 | 含未提交 R8 改动（resolver / loader / ImageBlockView / 测试 / fixture） |
| APK | `app/build/outputs/apk/qa/app-qa.apk` |
| applicationId | `com.markleaf.app.qa` |
| versionName / versionCode | `0.1.0-qa` / `1` |
| 设备 | `25098PN5AC`（product: pandora） |
| Android | 16（API 36） |
| 序列号 | （省略；不入库） |
| 安装命令 | `adb install -r app/build/outputs/apk/qa/app-qa.apk` |
| 启动命令 | `adb am start -n com.markleaf.app.qa/com.markleaf.app.MainActivity -a android.intent.action.MAIN -c android.intent.category.LAUNCHER` |
| External Open 启动 | `am start -a VIEW -t text/markdown -d content://media/external/file/<id> -n …MainActivity --grant-read-uri-permission` |
| Fixture | `testdata/r8_relative_asset_v1/` |
| 证据目录 | 本地 `dist/r8-device-acceptance/`（screens / ui / logs；**不提交**） |
| 验收时间 | 2026-07-15T15:09Z–15:26Z UTC |

**验收方式说明：**
- A/B/D：将 fixture 注入 QA 包 `files/packages/*` 与 `files/library/*`，写入 Room `documents` 行，经真实列表 → 阅读器打开。
- B 场景明确使用 **应用 filesDir 生产路径**（非公开任意 file:// 外部树），报告不伪造「已覆盖普通外部文件」。
- C：共享下载目录中的 `only_markdown.md` 经 MediaStore 单文件 `content://media/external/file/<id>` VIEW（仅 md，不授予 tree）。

---

## 2. 核心结果

| 用例 | 预期 | 实际 | 结果 | 证据 |
|---|---|---|---|---|
| 同目录图片 | 显示 | A 根 `same` / B 同目录色块正常解码显示 | **PASS** | `screens/A1_root_top.png`、`B1_wide_top.png`；UI 描述含 `same`/`child` |
| 子目录图片 | 显示 | `images/child.png` 可打开（content-desc `child`） | **PASS** | `A1_root` DESCS；`B2` 见 `child` 成功区 |
| 合法上级路径 | 按资源根规则 | A chapter `../same` 等在 package root 内显示；A 根 `../outside_should_fail` 拒绝 | **PASS** | `A2_chapter_top.png`、`A1_root_scroll0.png`（escape 占位） |
| 空格 / %20 | 显示且不双解码 | 空格文件名图显示（紫色色块）；fixture 含 `space image.png` 与 `%20` 引用 | **PASS** | fixture A + A 滚动截图色块 |
| 中文文件名 | 显示 | `中文图片.png` 显示（品红色块，alt `zh`） | **PASS** | `A1_root_top.png`、`A2_chapter_top.png` |
| 缺失图片 | 占位，正文正常 | 「无法访问相对图片」+ 正文仍在 | **PASS** | `A1_root_top.png`、`A2_chapter_top.png` |
| 越界路径 | 拒绝 | A `parent_from_root_escape` 拒绝；B-DOCROOT 的 `../sibling`/`../outside` 拒绝 | **PASS** | `A1_root_scroll0.png`、`B2_docroot_scroll0.png` |
| 非图片/目录 | 受控失败 | 伪 png →「图片无法读取」；txt →「无法访问相对图片」；不崩溃 | **PASS** | `B2_docroot_scroll0.png` |
| External Open 单文件 SAF | 明确占位 | 正文正常；`neighbor` →「无法访问相对图片」；未加载邻图；无 tree picker | **PASS** | `C1_external_open_degrade.png`；`logs/c1_result.txt` |
| 绝对 URI | 不被改写 | http/https「网络图片已禁用」；data/file 受控「图片无法读取」；正文仍在 | **PASS** | `D1_abs_top.png` |
| A/B 文档切换 | 不串图 | A→D 后为绝对资源失败文案，无 A 包图标题串页 | **PASS** | `R_switch_A.png` / `R_switch_D.png`；UI 文本 |
| External Open A→B session | 第二份文档 base 独立 | C1→确认「关闭并打开」→ C2 正文；相对图仍占位 | **PASS** | `C2_viewer.png`；`logs/c2_result.txt` |
| 旋转/重开 | 稳定 | 旋转截图有文档；杀进程后重开 A 仍见占位与正文 | **PASS** | `R_rotate_*.png`、`R_kill_reopen_A.png` |
| 崩溃/ANR | 无 | 无 `FATAL EXCEPTION` / ANR（markleaf）；无 FileUriExposed | **PASS** | `logs/fatal_final.txt`（空/无应用崩溃） |

### 补充判定说明

1. **B-WIDE 包根**（`package-manifest` 在 `fixture_B` 顶）：`../outside/secret.png` **仍在包根内**，故**允许显示**（红块）。这不是越界成功，而是 root 定义包含该目录。越界门禁由 **B-DOCROOT** 与 **A escape** 覆盖。
2. **C 次级文案**「当前仅授权了 Markdown 文件…」：**未出现**。原因：External Open 将单文件**复制**到 app `library/` 后 base 为 `AppOwnedDirectory`，相对邻图为 `NOT_FOUND`，仅主文案「无法访问相对图片」。主门禁（不加载未授权邻图、正文正常、不崩溃）已满足；次级为「可显示」项。
3. **SafTreeDirectory**：本轮未测成功路径——**数据模型已预留，但 V1 当前产品流程不可达。**

---

## 3. 日志摘要

| 源 | 结论 |
|----|------|
| `dist/r8-device-acceptance/logs/r8_markleaf_image.log` | 有向图像加载调试；无应用 FATAL |
| `logs/fatal_final.txt` / `fatal_scan.txt` | 无 `FATAL EXCEPTION` / `ANR in com.markleaf` / `FileUriExposedException` |
| UI 自动化 | 多图失败仅图片位文案；未见 Toast/Dialog 风暴（External Open 确认框为预期 session UI） |

受控失败（缺图/网络禁用/解码失败）允许出现；未观察到隐私完整路径刷屏或未捕获异常导致崩溃。

---

## 4. 已知限制

- **External Open 单文件授权不能访问未授权相邻资源**（本轮 C1/C2 已验证受控失败）。
- **SafTreeDirectory 在 V1 当前产品流程不可达**（模型预留，非本轮成功门禁）。
- **文件夹授权 / 工作区模式不在 V1**。
- B 场景以 **app filesDir 包路径** 验收，不宣称已覆盖任意外部 `file://` 公开目录。
- External Open 导入后次级「仅授权 Markdown 文件」文案通常不出现（见上）。

---

## 5. 工作区边界

| 项 | 状态 |
|----|------|
| H1.5A | **未修改** |
| PDF 手势 / Tile | **未修改** |
| External Open session 语义 | **未修改**（仅真实打开验证） |
| remote / push / commit | **未执行** |
| 无关工作区文件 | **未清理** |

本轮**未改业务代码**（仅写入 `dist/r8-device-acceptance/` 证据与本报告；可更新 `.memory` 状态）。

---

## 6. 最终结论

**DEVICE_ACCEPTED**

全部核心门禁通过。停止：不 commit、不 push、不开启下一 object。
