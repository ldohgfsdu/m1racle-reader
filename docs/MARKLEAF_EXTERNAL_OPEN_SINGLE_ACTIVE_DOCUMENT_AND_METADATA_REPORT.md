# MARKLEAF_EXTERNAL_OPEN_SINGLE_ACTIVE_DOCUMENT_AND_METADATA

**Date:** 2026-07-15  
**Status:** implementation complete; host gates pass; human acceptance **PENDING**  
**Package under test:** `com.markleaf.app.qa` only  

## 0. H1.5A status correction (frozen)

| Field | Value |
|---|---|
| `r7_h1_5a.component_validation` | pass |
| `r7_h1_5a.real_ui_automated_validation` | pass |
| `r7_h1_5a.human_video_acceptance` | **rejected** |
| `r7_h1_5a.overall` | **device_failed** |

Latest human video reconfirmed H1.5A gesture issues remain.  
**Do not** record `human_video_acceptance: accepted` or `device_accepted_for_bug_scope`.  
This object **does not** modify PDF gestures / ThreePageStrip / H1 flip / tiles / Markdown / Room schema version / QA isolation redesign.

---

## 1. Why old code produced many “未命名文档”

Root causes (observed + code path):

1. **Metadata fallback too early**  
   `DocumentRepository.importDocument` and `ExternalFileIntake.queryMeta` only read `OpenableColumns`. Many third-party providers omit `DISPLAY_NAME` or return empty → hard-coded **“未命名文档”** / **“shared”**.

2. **Identity tied to post-copy `file://` path**  
   External intake first copied to `files/inbox/<hash(uri)>/<name>`, then called `importDocument(Uri.fromFile(...))`.  
   Document id became `stableUriId(file://…)` of a **new private path each time**.  
   Same third-party file opened repeatedly → **new Room rows** every time, often all named “未命名文档”.

3. **No single-session gate**  
   `MainActivity.handleIncomingIntent` always imported and `navigate`d with `launchSingleTop` only.  
   It did **not** confirm when a document was already open, did not pop the previous viewer, and could stack reading sessions.

4. **No content-hash dedup**  
   Different `content://` URIs for identical bytes still created separate copies and rows.

---

## 2. Unified onCreate / onNewIntent chain

```
Intent (VIEW | SEND | SEND_MULTIPLE)
  → ExternalOpenCoordinator.onIncomingIntent
      → eventId = SHA-256(action|uri|type)   // one-shot, survives config change
      → if handled(eventId): return
      → SEND_MULTIPLE: toast “一次只能打开一个文档”; return
      → probe metadata card (name/size) without writing DB
      → if activeDocumentId == null: import → navigate (single viewer)
      → if same bound document: navigate only
      → else: pending = AWAITING_CONFIRM (dialog)
Cancel: mark event handled; no DB; no copy; keep active session
Confirm: flush hook → clear active session bookkeeping → pop to HOME → atomic import → navigate once
```

`MainActivity` wires effects to navigation; `NavGraph` registers `onViewerOpened` / `onViewerClosed` and **pops to HOME** on back so a dead viewer is not restored.

`launchMode` remains `singleTop` so warm starts deliver `onNewIntent` to the existing activity (no multi-activity document stack).

---

## 3. File name and size resolution priority

**Name**

1. `OpenableColumns.DISPLAY_NAME`  
2. Decoded last path segment (skip opaque provider ids when possible) + MIME extension if needed  
3. MIME-generated name (`document.pdf`, …)  
4. `未命名文档` (last resort only)

**Size (hint)**

1. `OpenableColumns.SIZE`  
2. `AssetFileDescriptor.length`  
3. `ParcelFileDescriptor.statSize`  

**Size (final authority after copy)**

- Streamed **byte count** of the private copy (`COPY_BYTE_COUNT`)  
- Stored as raw `Long` in `DocumentEntity.fileSize`; UI formats via `FileSizeFormatter` (B/KB/MB/GB)

---

## 4. Atomic import

1. Optional `takePersistableUriPermission` when flag present (never sole durable source).  
2. Stream copy to `files/library/<nano>.importing` while updating SHA-256 and byte count.  
3. `fsync` / close.  
4. Content-hash / URI index lookup → **reuse** existing row when possible (delete temp).  
5. Else atomic rename to `files/library/<sha256>/<safeName>`.  
6. Room upsert `DocumentEntity` (`id = c:<sha256>`, `uri = file://…` private).  
7. Bind sidecar indexes (`library_index/uri_index.json`, `sha_index.json`) — **no Room schema version change**.  
8. Navigate only after success.

On any failure: temp deleted; no half row; old viewer remains; toast error.

---

## 5. Dedup rules

| Case | Behavior |
|---|---|
| Same external key / URI | Reuse document id; touch `lastOpenedAt`; no second copy |
| Different URI, same SHA-256 | Reuse content row; rebind URI index; no second file |
| Same display name, different SHA | Two rows (name alone never dedups) |
| Intent redelivery / recreate | Same `eventId` processed once (no double dialog / double import) |

---

## 6. Close old session → replace viewer (ordering)

1. User confirms “关闭并打开”.  
2. `progressFlushHook` (if installed) + ViewModel dispose path for PDF progress.  
3. Clear `activeDocumentId` / `activeViewerSessionId`.  
4. `navController.popBackStack(HOME)` — removes prior `pdf/{id}` / `viewer/{id}`.  
5. Atomic import of the new file.  
6. `navigate(pdf|viewer)` with `launchSingleTop`.  
7. Back from reader returns to **home list**, not a stale previous reader.

Closing the session **does not** delete the old file or Room row.

---

## 7. Cancel / confirm paths (expected UI evidence)

**Cancel**

- Dialog shows current vs new name + size.  
- Cancel → dialog dismissed; page/zoom/mode of A unchanged; **no** B row; intent marked handled (no re-popup on rotate).

**Confirm**

- A progress flushed; A route disposed; B opens; back stack has no active A viewer; A remains in recent list.

*(Human video still required for final UI sign-off; host automation covers importer identity/metadata.)*

---

## 8. Same file opened three times → DB count

Importer rule + instrumented case `REOPEN_SAME_EXTERNAL_FILE_three_times_single_row`:  
**exactly one** Room row for that display name / content identity.

---

## 9. Sample identity fields (design)

| Field | Example |
|---|---|
| displayName | `甲号教材.pdf` (from DISPLAY_NAME) |
| sizeBytes | actual copied length (e.g. after AFD-missing provider) |
| contentSha256 | hex digest of private file bytes |
| documentId | `c:<contentSha256>` |

---

## 10. Tests and builds

### JVM (`:app:testDebugUnitTest`)

- **≥ 668** required; this change adds external-open policy/metadata suites.  
- Result: **PASS** (see `app/build/reports/tests/testDebugUnitTest/`).  
- Count: **693 / 693** (failures 0; ≥ 668 required).  

New / extended pure tests include:

- DISPLAY_NAME + SIZE present  
- SIZE from AssetFileDescriptor  
- final size from copy byte count  
- Chinese / spaces / parentheses names  
- extension filled from MIME  
- URI identity stability  
- cancel / confirm / failure / single-shot event policy  

### Other gates

| Task | Result |
|---|---|
| `:app:lintDebug` | PASS |
| `:app:assembleDebug` | PASS |
| `:app:assembleQa` | PASS |
| `:app:compileQaAndroidTestKotlin` | PASS |
| `:app:assembleQaAndroidTest` | PASS |

### QA instrumentation (added)

`ExternalOpenSingleSessionInstrumentedTest` + `ExternalOpenFixtureProvider`  
(`APP_COLD_START…`, reopen×3, different content, provider without SIZE, cancel bookkeeping).  
Full interactive dialog B/C flows: **human PENDING**.

Existing Markdown / table / QA isolation / PDF unit suites were not reduced.

---

## 11. QA APK artifact

| Field | Value |
|---|---|
| Path | `dist/markleaf-qa-external-open-single-session.apk` |
| Absolute | `C:\Users\21234\Desktop\MarkLeaf\dist\markleaf-qa-external-open-single-session.apk` |
| Bytes | `73012597` |
| SHA-256 | `E4A37378CCF2804D361C4CE38DD7E081EF72A5676AAF5593FDE9D69200AADEDF` |
| applicationId | `com.markleaf.app.qa` |
| label | MarkLeaf QA |

Sidecar: `dist/markleaf-qa-external-open-single-session.apk.sha256`

---

## 12. Human acceptance

**PENDING**

Suggested manual sequence (QA package only):

1. External open A (Chinese name PDF).  
2. While reading A, external open B → **Cancel** → A unchanged; no B card.  
3. External open B → **关闭并打开** → B replaces viewer; back goes home.  
4. Open B again → still one B row.  
5. Open C (same bytes as A, different URI) → reuses A content identity (no third unnamed card).  
6. Recent list shows accurate names and sizes; no spurious “未命名文档”.

---

## 13. Stop conditions honored

- No further H1 gesture work  
- No commit / push  
- No main-package operations  
- No Markdown feature work  
- No Room schema version upgrade  
