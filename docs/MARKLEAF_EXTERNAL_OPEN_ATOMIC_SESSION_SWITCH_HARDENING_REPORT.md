# MARKLEAF_EXTERNAL_OPEN_ATOMIC_SESSION_SWITCH_HARDENING

**Date:** 2026-07-15  
**Status:** implementation complete; host gates pass; human acceptance **PENDING**  
**Must not mark:** `device_accepted`  

## 1. Why the old confirm sequence was unsafe

Previous confirm path:

```
flush → clear activeDocumentId/session → popBackStack(HOME) → import → navigate
```

If **import failed after pop**, the user had already lost the old viewer (and possibly renderer), violating:

> 导入失败时保持旧文档继续打开

Additionally, intermediate `pop HOME` could flash an empty home list before the new document was ready.

## 2. Two-phase prepare / commit state machine

```
onIncomingIntent
  ├─ eventId already handled → return
  ├─ SEND_MULTIPLE → toast; return
  ├─ probe card (name/size metadata only; no full copy for dialog size)
  ├─ active == same document → Navigate bringToFrontOnly (no confirm, no copy)
  ├─ no active → PHASE1 prepare → PHASE2 commit → Navigate replace
  └─ active different → AWAITING_CONFIRM
        cancel → mark handled; discard any prepared; keep active
        confirm → PHASE1 prepare (old viewer still active)
                    fail → toast; mark handled; keep active; cleanup temp
                    ok  → flush old (fail aborts; discard prepared; keep active)
                          → PHASE2 commit Room/index
                          → set activeDocumentId/session
                          → Navigate replace (single transaction)
```

Phases on `PendingExternalOpen`:  
`AWAITING_CONFIRM → PREPARING → READY_TO_COMMIT → COMMITTING → (clear pending)`  
Failed events end as handled (`failed`) so rotation does not re-run prepare/commit.

## 3. Failure points keep the old viewer

| Failure | Old viewer | Temp | Room | Active ids |
|---|---|---|---|---|
| Stream / open fail | kept | deleted | unchanged | unchanged |
| SHA / copy fail | kept | deleted | unchanged | unchanged |
| Stage file fail | kept | deleted | unchanged | unchanged |
| Flush fail | kept | discarded prepared | unchanged | unchanged |
| Room commit fail | kept | cleaned | no half-active switch | unchanged |

Evidence: `ExternalOpenCoordinator.runPrepareAndCommitLocked` never clears `_activeDocumentId` until `CommitOutcome.Success`.  
Instrumented: `prepare_fail_keeps_active_session` (QA provider `fail=1`).  
JVM: `ExternalOpenAtomicSessionTest` cases 1–5.

## 4. Identity index: atomic write & recovery

- File: `files/library_index/identity_index_v1.json`
- `formatVersion = 1`
- Entries: `documentId`, `canonicalUriIdentity`, `sha256`, `sizeBytes`, `mimeType`
- Write: temp + `fsync` + rename
- Corrupt parse/version → quarantine under `library_index/corrupt/`, load empty
- `pruneMissingDocuments` when prepare runs
- `deleteById` removes index row after Room transaction
- SHA lookup requires **sha + sizeBytes** agreement
- **Authority:** Room + private library files; index is rebuildable acceleration only

## 5. Three open paths

| Path | Behavior |
|---|---|
| **A Current active** | No confirm; no re-copy; `bringToFrontOnly` navigate |
| **B Existing library, not active** | Confirm; flush; switch to existing id; no second content copy |
| **C New content** | Confirm (if active); prepare stages `.importing`; commit renames + Room + index; nav replace |

## 6. No-SIZE provider

- Confirm dialog: `大小：打开后自动计算` (`FileSizeFormatter.formatForConfirmDialog`)
- Does **not** full-copy just to fill the dialog (cancel leaves no library copy)
- After confirm prepare: final `sizeBytes` = streamed byte count

QA fixture: `nosize=1` also returns `AssetFileDescriptor.UNKNOWN_LENGTH`.

## 7. Navigation without HOME flash

```kotlin
navController.navigate(route) {
  popUpTo(Routes.HOME) { inclusive = false; saveState = false }
  launchSingleTop = true
}
```

Confirm **no longer** calls `popBackStack(HOME)` before prepare.  
Back from reader still lands on HOME only (no old viewer under stack).

## 8. Concurrent active renderers

`ExternalOpenCoordinator` tracks `onViewerOpened` / `onViewerClosed` with `peakRendererCount`.  
Steady state: **1**. Brief dispose/open overlap during nav may peak at 2; never intentionally dual-active sessions.

## 9. QA device flow (script)

Only `com.markleaf.app.qa`. Fixtures A/B/C/D/E via `ExternalOpenFixtureProvider`.

1. Open A  
2. External B → Cancel → A unchanged; no B row  
3. External B → Confirm → B replaces only after prepare; no HOME flash; back ≠ A viewer  
4. On A, external E (`fail=1`) confirm → fail toast; A still active  
5. Reading B, external B again → no confirm; no reset  
6. Open C (same bytes as A) → reuse content id  
7. Open D (`nosize=1`) → dialog “打开后自动计算”; success shows real size  
8. Delete + re-open → index does not reference deleted row  

**Human status: PENDING** (this report does not claim device_accepted).

## 10. Host gates

| Task | Result |
|---|---|
| `:app:testDebugUnitTest` | **715 / 715 PASS** (0 fail; ≥ 693) |
| `:app:lintDebug` | PASS |
| `:app:assembleDebug` | PASS |
| `:app:assembleQa` | PASS |
| `:app:compileQaAndroidTestKotlin` | PASS |
| `:app:assembleQaAndroidTest` | PASS |

Markdown / table / PDF / isolation suites not reduced.

## 11. APK

| Field | Value |
|---|---|
| Path | `dist/markleaf-qa-external-open-atomic-session-switch.apk` |
| Bytes | `74026647` |
| SHA-256 | `4511860927CEA689E963D539FF5C8D8B91FC083B4D2221959D37089952D60748` |
| applicationId | `com.markleaf.app.qa` |

## 12. Human acceptance

**PENDING** — not `device_accepted`.

## Stopped

- No H1 / ThreePageStrip / tile / Markdown changes  
- No Room schema version upgrade  
- No commit / push  
- No main-package ops  
