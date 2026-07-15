# MARKLEAF_EXTERNAL_OPEN_SINGLE_ACTIVE_SESSION_IMPLEMENTATION

**Date:** 2026-07-15  
**Status:** implementation complete; host gates pass; human acceptance **PENDING**  
**Must not mark:** `device_accepted`  

## Summary

Third-party `ACTION_VIEW` / `ACTION_SEND` opens are unified through `ExternalOpenCoordinator` with a one-shot event id, confirm-before-replace when a viewer is active, ContentProvider metadata for real names/sizes, SHA-256 + URI identity dedup, and two-phase prepare → flush → commit → nav replace so import failure never blanks the old session.

## Behavior

| Scenario | Result |
|---|---|
| Cold start external open | Prepare + commit; open viewer; no dialog |
| Active doc + different external file | Confirm dialog; cancel keeps old; confirm switches |
| Same active document again | Bring to front; no copy; no dialog |
| Same content different URI | Reuse private content + Room row |
| SEND_MULTIPLE | Toast「一次只能打开一个文档」; no silent first-file |
| Unsupported type | Toast; old session unchanged |

### Confirm dialog

- Title: 打开新文档？
- Current / new name + size (or「大小将在打开时确认」)
- Hint: close reading page; progress saved; library row not deleted
- Buttons: 取消 / 关闭并打开

### Switch order

1. PHASE1 prepare (stream copy + SHA; old viewer still live)  
2. Flush old progress (`progressFlushHook` → PDF `flushProgress`)  
3. Commit Room + identity index  
4. Set `activeDocumentId`  
5. Navigate replace (`popUpTo HOME`) → old ViewModel `onCleared` releases renderer/tiles  

## Files touched

- `share/ExternalOpenCoordinator.kt` — classify via `ExternalOpenSessionRules`
- `share/ExternalOpenSessionRules.kt` — pure path rules
- `share/ExternalDocumentImporter.kt` — Room-validated probe
- `share/FileSizeFormatter.kt` — deferred size copy
- `ui/navigation/NavGraph.kt` — flush hook wiring
- `res/values/strings.xml` — dialog copy
- JVM + QA androidTest coverage for replace / cancel / metadata

## Frozen (untouched)

H1 / ThreePageStrip / tile pipeline / Markdown reader / QA isolation redesign / Room schema / no commit-push.

## Host gates

| Gate | Result |
|---|---|
| `:app:testDebugUnitTest` | **723/723 PASS** |
| `:app:lintDebug` | **PASS** |
| `:app:assembleDebug` | **PASS** |
| `:app:assembleQa` | **PASS** |
| `:app:compileQaAndroidTestKotlin` | **PASS** |
| `:app:assembleQaAndroidTest` | **PASS** |

## QA device automation (self-ADB)

| Field | Value |
|---|---|
| serial | `198.18.0.1:39555` |
| model | 25098PN5AC / Android 16 |
| package | `com.markleaf.app.qa` only |
| prod path | **unchanged** |
| evidence | `/root/.cache/markleaf-qa-acceptance/20260715-200436-r3` |

| 场景 | 弹窗 | 私有文件 | 结果 |
|---|---|---|---|
| 冷启动 A | 否 | 1（甲号教材.pdf） | **PASS** |
| 同一 A ×3 | 否 | 仍 1 | **PASS** |
| A→B 取消 | 是（文案完整） | 仍 1 | **PASS** |
| A→B 确认 | 是 → 乙号讲义 | 2 | **PASS** |
| 返回栈 | 回最近文件，A+B 均在 | — | **PASS** |
| 同内容 C | 打开为甲号教材 | 仍 2 | **PASS** |
| 同名 D1/D2 | 独立两条 | 4（含 D1/D2） | **PASS** |
| 不支持 .xyz | 无替换弹窗，仍看当前 PDF | 仍 4 | **PASS** |
| SEND_MULTIPLE | 无新 session | 仍 4 | **PASS** |

QA APK: `dist/markleaf-qa-external-open-single-active-session.apk`  
SHA-256 `E8EC24A01017373E214F23F9E56C3823314DE39008E9FB2AB3FA62C1251573BD` (74,235,639 bytes)

Human spot-check optional. **Not** marking full human_acceptance without explicit user confirmation.
