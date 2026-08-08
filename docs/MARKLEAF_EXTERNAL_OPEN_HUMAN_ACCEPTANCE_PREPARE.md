# MARKLEAF_EXTERNAL_OPEN_HUMAN_ACCEPTANCE_PREPARE

**Date:** 2026-07-15  
**Scope:** QA-only human acceptance scene preparation  
**Code changes this round:** none  
**Git:** no commit / push / reset / clean / stash  
**Main package `com.markleaf.app`:** not touched  

---

## Status (before human feedback)

```yaml
external_open_single_active_session:
  implementation: complete
  host_validation: pass
  qa_device_validation: pass
  human_acceptance: pending
  overall: human_acceptance_pending
```

H1.5A remains:

```yaml
r7_h1_5a:
  human_video_acceptance: rejected
  overall: device_failed
  frozen: true
```

---

## Environment blockers

| Item | Result |
|------|--------|
| `adb` | **NOT FOUND** in PATH |
| QA package install by agent | **BLOCKED** (no adb) |
| Main package operations | **NOT PERFORMED** (forbidden) |
| Force-stop / UI dump by agent | **BLOCKED** (no adb) |

Agent prepared fixtures on shared storage only.  
**You must install the QA APK and run the open-with flows manually.**

---

## QA APK to install

| Field | Value |
|-------|-------|
| Path | `dist/markleaf-qa-external-open-single-active-session.apk` |
| Size | 73,062,197 bytes |
| SHA-256 | `225EA6C42809DBE803F19443F88CC2C5C4F6BA7A727726E514BD54117184F72D` |
| applicationId | `com.markleaf.app.qa` |
| versionName | `0.1.0-qa` |
| Label | MarkLeaf QA |
| INTERNET | none |

Install suggestion (on a machine with adb):

```bash
adb install -r dist/markleaf-qa-external-open-single-active-session.apk
adb shell am force-stop com.markleaf.app.qa
```

Do **not** install over or instrument `com.markleaf.app`.

---

## Fixture directory

```text
/sdcard/Download/MarkLeaf-ExternalOpen-HumanAcceptance/
```

Also mirrored for A/B/C under:

```text
/sdcard/inbox/MarkLeaf/
```

### Fixture table

| ID | 文件名 | 路径 | bytes | SHA-256 | MIME (expected) | 用途 |
|----|--------|------|------:|---------|-----------------|------|
| **A** | 内科学教材测试.pdf | `.../内科学教材测试.pdf` | 5255 | `ADF9427F8E0635863E47CF61005946D7BD1464E7B67CCEECCE6BCAA40DC786CA` | application/pdf | 冷启动 / 当前文档 |
| **B** | 心电图测试.pdf | `.../心电图测试.pdf` | 3168 | `71DCDE2A648D9540F3D70D8EDBF9BF8E364DC7D377AA4E2A7F463AD6D6B35A12` | application/pdf | 不同内容替换 |
| **C** | 内科学教材测试-副本C.pdf | `.../内科学教材测试-副本C.pdf` | 5255 | `ADF9427F8E0635863E47CF61005946D7BD1464E7B67CCEECCE6BCAA40DC786CA` | application/pdf | 与 A **内容相同**、文件名/Uri 不同 |
| **D1** | 同名测试.pdf | `.../D1/同名测试.pdf` | 2162 | `931DB596E6E9B2C9C27D903139E7C1A8D1D1F50466F7FA7010D1D943084F2109` | application/pdf | 同名不同内容 |
| **D2** | 同名测试.pdf | `.../D2/同名测试.pdf` | 4210 | `912294F2643C4E1CFE50D34D60456DD5147663B44A78D06685794D4CE2C9E065` | application/pdf | 同名不同内容 |
| **U** | 不支持格式测试.xyz | `.../不支持格式测试.xyz` | 64 | `3A64624D6C758BF1C231D96A68404A8F12B3AD569535155D6A0D56116D70A310` | (unsupported) | 不支持格式 |
| **M1** | 多文件分享1.txt | `.../多文件分享1.txt` | 8 | `CF77AC6A2F93E403C8DACEDAFAFFA71410CD9266ACE2FF2480811BCB50E7C4D2` | text/plain | SEND_MULTIPLE |
| **M2** | 多文件分享2.txt | `.../多文件分享2.txt` | 8 | `AC711613F3F3F722BA12AA8C96E7716AD0FA0A4CCB9C1A6519FFE40FBF38789A` | text/plain | SEND_MULTIPLE |

### Content relations

| Check | Result |
|-------|--------|
| A content == C content | **True** (same SHA-256, same 5255 bytes) |
| A content != B content | **True** |
| D1 name == D2 name | **True** (`同名测试.pdf`) |
| D1 content != D2 content | **True** (different SHA-256 and sizes) |

All fixtures are **controlled acceptance files**, not user private documents.

---

## Human acceptance script

### 0. Install & cold prep

1. 安装 QA APK（仅 `com.markleaf.app.qa`）。
2. 强制停止 MarkLeaf QA，确保无活动阅读页。
3. 用系统「文件」或第三方应用，从  
   `/sdcard/Download/MarkLeaf-ExternalOpen-HumanAcceptance/`  
   选择文件 → **打开方式 → MarkLeaf QA**。

### 一、冷启动打开 A

1. 确认 QA 无活动阅读页（必要时 force-stop）。
2. 打开方式打开 **内科学教材测试.pdf**。

人工确认：

- [ ] 直接进入 A，**无**替换弹窗  
- [ ] 标题为「内科学教材测试.pdf」  
- [ ] 文件大小正确（导入后应接近 **5255 B** / 格式化后的等价显示）  
- [ ] 最近文档仅新增 **一条** A  
- [ ] **不**显示「未命名文档」  

记录：displayName / sizeBytes / MIME / source Uri / documentId / SHA-256 / 库记录数  

### 二、重复打开当前文件 A ×3

保持 A 正在阅读，再从第三方连续打开 A 三次。

- [ ] 不弹「打开新文档」  
- [ ] 不反复复制  
- [ ] 不产生重复卡片  
- [ ] 页码 / 缩放 / 阅读模式不异常复位  
- [ ] 最近文档中 A **仅 1 条**  

### 三、打开 B 并取消

保持 A 阅读，打开 **心电图测试.pdf**。

对话框应显示：

```text
当前文档
内科学教材测试.pdf
<大小>

新文档
心电图测试.pdf
<大小>

将关闭当前阅读页并打开新文档。
当前阅读进度会保存，旧文档不会被删除。
```

点 **取消**：

- [ ] 仍停留在 A  
- [ ] 页码 / 缩放 / 模式保持  
- [ ] B **未**出现在最近文档  
- [ ] 对话框不因旋转/返回/重组重复弹出  

### 四、打开 B 并确认

再次打开 B，点 **关闭并打开**：

- [ ] B 成功替换当前阅读页  
- [ ] 标题与大小正确  
- [ ] 无短暂空白 viewer  
- [ ] 无残留 A 的标题或页面  
- [ ] 返回键 **不**回到仍活动的 A 阅读页  
- [ ] A 仍在最近文档  
- [ ] 重新打开 A 时原阅读进度仍在  
- [ ] A 的私有文件未删除  

### 五、内容去重 C

打开 **内科学教材测试-副本C.pdf**（与 A 内容相同，Uri/文件名不同）：

- [ ] 不创建第二份相同内容私有文件  
- [ ] 最近文档不新增重复 A 卡片  
- [ ] 不出现两个仅名称不同、内容完全相同的卡片  

### 六、同名不同内容 D1 / D2

依次打开：

- `D1/同名测试.pdf`（2162 bytes）  
- `D2/同名测试.pdf`（4210 bytes）  

- [ ] 同名但两条独立记录  
- [ ] 不互相覆盖  
- [ ] 大小/内容可区分  
- [ ] 阅读进度各自独立  

### 七、不支持格式与多文件

- 打开 `不支持格式测试.xyz`：当前文档不关；不新增记录；提示不支持格式  
- 一次分享 `多文件分享1.txt` + `多文件分享2.txt`：提示「一次只能打开一个文档」；不静默开第一个；不多 session  

### 八、最近文档最终检查

- [ ] 无重复导入的同一文件  
- [ ] 无无故「未命名文档」  
- [ ] 文件名 / 大小准确  
- [ ] A、B 都保留  
- [ ] C 未生成重复内容记录  
- [ ] D1、D2 均独立存在  
- [ ] 当前活动阅读会话只有一个  

---

## Human feedback form（请回复）

```text
冷启动打开：正常 / 异常
文件名：准确 / 错误
文件大小：准确 / 错误
同一文件重复打开：不重复 / 仍重复
打开新文档弹窗：正常 / 异常
取消后旧文档：保持 / 被改变
确认后替换：正常 / 异常
返回栈：正常 / 返回旧阅读页
旧进度保存：正常 / 丢失
不同 Uri 相同内容：正确去重 / 仍重复
同名不同内容：正确独立 / 错误合并
不支持格式：正常拒绝 / 异常
多文件分享：正常拒绝 / 异常
最近文档列表：正常 / 存在具体问题：________
```

---

## Fixture absolute paths

```text
A:  /sdcard/Download/MarkLeaf-ExternalOpen-HumanAcceptance/内科学教材测试.pdf
B:  /sdcard/Download/MarkLeaf-ExternalOpen-HumanAcceptance/心电图测试.pdf
C:  /sdcard/Download/MarkLeaf-ExternalOpen-HumanAcceptance/内科学教材测试-副本C.pdf
D1: /sdcard/Download/MarkLeaf-ExternalOpen-HumanAcceptance/D1/同名测试.pdf
D2: /sdcard/Download/MarkLeaf-ExternalOpen-HumanAcceptance/D2/同名测试.pdf
U:  /sdcard/Download/MarkLeaf-ExternalOpen-HumanAcceptance/不支持格式测试.xyz
M1: /sdcard/Download/MarkLeaf-ExternalOpen-HumanAcceptance/多文件分享1.txt
M2: /sdcard/Download/MarkLeaf-ExternalOpen-HumanAcceptance/多文件分享2.txt
```

## Agent limits this round

- No code / parameter / Room / navigation changes  
- No main package operation  
- No adb → **could not install QA APK or force-stop QA app**  
- Fixtures prepared on shared storage for manual「打开方式」  

**Stopped after scene preparation. Waiting for human feedback.**
