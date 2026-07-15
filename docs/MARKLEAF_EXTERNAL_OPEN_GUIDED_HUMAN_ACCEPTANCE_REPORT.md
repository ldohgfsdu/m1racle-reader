# MARKLEAF_EXTERNAL_OPEN_GUIDED_HUMAN_ACCEPTANCE

**Date:** 2026-07-15  
**Result:** `human_acceptance: accepted` / `overall: device_accepted`  
**Package:** `com.markleaf.app.qa` only  

## User feedback (verbatim)

| 场景 | 用户反馈 |
|------|----------|
| 1 确认框信息 | **准确** |
| 2 取消后旧文档 | **完全保持** |
| 3a 确认替换 | **正常** |
| 3b 返回栈和旧进度 | **正常** |
| 4 最终文档列表 | **正常** |

## Evidence paths

| 场景 | 截图 | UI XML |
|------|------|--------|
| 1 确认框 | `/root/.cache/markleaf-qa-acceptance/human-guided-20260715-203940/S1-confirm-dialog.png` | `/root/.cache/markleaf-qa-acceptance/human-guided-20260715-203940/S1-confirm-dialog-ui.xml` |
| 2 取消后 | `/root/.cache/markleaf-qa-acceptance/human-guided-20260715-203940/S2-after-cancel.png` | `/root/.cache/markleaf-qa-acceptance/human-guided-20260715-203940/S2-after-cancel-ui.xml` |
| 3 确认前对话框 | `/root/.cache/markleaf-qa-acceptance/human-guided-20260715-203940/S3-dialog.png` | `/root/.cache/markleaf-qa-acceptance/human-guided-20260715-203940/S3-dialog-ui.xml` |
| 3 替换后 B | `/root/.cache/markleaf-qa-acceptance/human-guided-20260715-203940/S3-after-confirm.png` | `/root/.cache/markleaf-qa-acceptance/human-guided-20260715-203940/S3-after-confirm-ui.xml` |
| 3 返回后 | `/root/.cache/markleaf-qa-acceptance/human-guided-20260715-203940/S3-after-back.png` | `/root/.cache/markleaf-qa-acceptance/human-guided-20260715-203940/S3-after-back-ui.xml` |
| 4 文档列表 | `/root/.cache/markleaf-qa-acceptance/human-guided-20260715-203940/S4-doc-list.png` | `/root/.cache/markleaf-qa-acceptance/human-guided-20260715-203940/S4-doc-list-ui.xml` |

Also copied under `dist/performance/human-s1-confirm-dialog.png` … `human-s4-doc-list.png`.

## Confirm dialog metadata (scene 1)

- 标题：打开新文档？
- 当前文档：甲号-内科学教材测试.pdf / 2.3 KB
- 新文档：乙号-心电图测试.pdf / 821 B
- 提示：将关闭当前阅读页并打开新文档；当前阅读进度会保存，旧文档不会被删除。
- 按钮：取消 / 关闭并打开

## Active document observations (agent-prepared; user-confirmed)

| 时机 | active（UI 标题） |
|------|-------------------|
| 取消后 | 甲号-内科学教材测试.pdf（A） |
| 确认替换后 | 乙号-心电图测试.pdf（B） |
| 返回后 | 最近文件列表（非 A viewer） |

## Final library

Private files (4):

| 文件 | bytes | SHA-256 prefix |
|------|------:|----------------|
| 甲号-内科学教材测试.pdf | 2357 | f38256c9… |
| 乙号-心电图测试.pdf | 821 | 5b7a29f4… |
| 同名D1测试.pdf | 609 | a1a05c90… |
| 同名D2测试.pdf | 1209 | f9f55f79… |

C (同内容不同 Uri) 未形成额外私有文件/重复卡片。

## Production package

- Before: `package:/data/app/~~4jkavlkAMK-ErCujCjTPBA==/com.markleaf.app-0I8Y4V1-LZz37ixR-gFPyQ==/base.apk`
- After: same
- Writes to `com.markleaf.app`: **none** (pm path only)

## Process guarantees

- No code/parameter changes
- No full automation re-run
- No H1 / ThreePageStrip / Tile / Markdown / Room changes
- No commit / push / reset / clean / stash
