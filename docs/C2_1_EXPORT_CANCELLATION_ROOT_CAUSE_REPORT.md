# MarkLeaf C-2.1 Export Cancellation Root Cause Report

## 0. Executive result

| Field | Result |
|---|---|
| Investigation date | 2026-08-24 (UTC+8) |
| Release under test | `v0.1.0` |
| Release commit | `5febda3bc0ea2582cdff276457ffec466e072650` |
| versionCode / versionName | `22` / `0.1.0` |
| Investigation base HEAD | `219a2e804e8594766440107e8d8f62a09811da87` |
| Device | Xiaomi `25098PN5AC`, Android 16 |
| ADB endpoint | `198.18.0.1:34195`, state=`device` |
| C-2 status carried forward | `HUMAN_REJECTED` |
| C-2.1 result | **ROOT_CAUSE_CONFIRMED / FIX_PENDING_APPROVAL** |

The primary cause is **A — `deleteSafDocumentBestEffort` is not called** on cancellation paths reached before the final SAF stream copy.

`ACTION_CREATE_DOCUMENT` has already created the user-selected destination when the exporter starts. Both exporters only call SAF delete from a `CancellationException` catch nested inside their final destination-copy block. Any earlier cancellation returns `Cancelled` or reaches the outer cancellation catch, cleans only the private cache work directory, and leaves the pre-created SAF document behind. The C-2 ZIP cancellation happened in this uncovered interval, so the 0-byte target persisted.

No product fix was applied. This investigation only strengthens tests and records the proposed fix for approval.

## 1. Real-device failure baseline

### 1.1 Reproduction

The accepted C-2 failure sample is retained as the C-2.1 baseline:

1. Open the existing 384-page PDF `27生理学笔记【合集】（带目录）.pdf`.
2. Choose chapter export, ZIP package, pages 1–384.
3. In DocumentsUI create `C2-cancel-mid.zip` in Download.
4. Cancel immediately after returning to MarkLeaf.
5. MarkLeaf changes from the running export dialog to `已取消导出`.
6. Dismiss the result, wait, and inspect the destination.

| Evidence | Value |
|---|---|
| SAF URI | `content://com.android.providers.downloads.documents/document/1482` |
| Device path | `/sdcard/Download/C2-cancel-mid.zip` |
| Provider file creation/mtime | `2026-08-24 16:10:33.584762890 +0800` |
| Cancel input point | approximately T+0.2–0.6 s after Save, or about `16:10:33.8–16:10:34.2 +0800` |
| UI state immediately before input | running dialog title `正在导出`; Cancel action available |
| UI state after input | `已取消导出` |
| Exporter substate | exact substate was not persisted in runtime logs; `Preparing` is the most likely code state, while **pre-final-SAF-copy** is the proven bound |
| Size after cancellation | 0 bytes |
| Persistence recheck | still 0 bytes at `2026-08-24 16:31:58 +0800` |
| Crash | none observed |

The URI mapping is based on the ordered grants held by the MarkLeaf activity and the ordered export artifacts:

| Document id | Operation | Device artifact |
|---:|---|---|
| `1480` | normal completed export | `C2-normal-export.zip` |
| `1481` | late-cancel completion-race control | `C2-cancel-export.zip` |
| `1482` | true immediate/mid-export cancellation | `C2-cancel-mid.zip` |

`dumpsys activity activities` shows both read and write grants for all three document URIs. Direct provider query from UID 2000 is denied because the grant belongs to MarkLeaf, not the ADB shell; therefore the URI-to-filename association is an ordered-grant/timestamp mapping, not a shell-side provider query.

### 1.2 State bound

The UI observation alone records only the generic `正在导出` title. The stronger state conclusion comes from the code and artifact:

- `runExportToUri` publishes `ChapterExportState.Preparing` before invoking an exporter.
- `ChapterPackageExporter` publishes `Preparing`, then stages and hashes the complete source before its first `CopyingPages` callback.
- The cancellation input arrived almost immediately after DocumentsUI returned.
- The destination remained exactly 0 bytes, so the final SAF copy did not write its first chunk.

Accordingly, this report does not claim an instrumented exact substate. It records the defensible bound as **`Preparing` probable; before final SAF copy confirmed**.

## 2. Cleanup call path

### 2.1 Actual failing path

```text
DocumentsUI ACTION_CREATE_DOCUMENT
  -> provider creates document/1482 (0 bytes)
  -> PdfViewerViewModel.runExportToUri(outputUri)
  -> ChapterPackageExporter: Preparing / private workDir staging
  -> user taps Cancel
  -> exportStateGate.cancel()
  -> cancellation token cancelled + export Job cancelled
  -> UI immediately becomes Cancelled
  -> exporter reaches an early cancellation checkpoint
  -> cancel(workDir, onState)
       -> cleanupDir(workDir) only
       -> Cancelled callback (now stale behind generation gate)
  -> no DocumentsContract.deleteDocument call
  -> provider document/1482 remains at 0 bytes
```

### 2.2 Covered versus uncovered cleanup

| Component/path | Cancellation behavior | SAF delete? | Finding |
|---|---|---:|---|
| `ChapterPackageExporter`: source staging | early `return cancel(...)` | No | Failing baseline can land here |
| `ChapterPackageExporter`: after staging/extraction/metadata/archive | early return or outer `CancellationException` catch | No | Same ownership gap |
| `ChapterPackageExporter`: final SAF copy | nested catch around `copyWithExportCancellation` | Yes | Only covered interval |
| `PdfAnnotatedExporter`: initial source copy/check | early `return cancel(...)` | No | Same destination ownership gap; initial `copyTo` is also not chunk-cancellable |
| `PdfAnnotatedExporter`: page import/save and pre-copy checks | early return or outer catch | No | Same gap |
| `PdfAnnotatedExporter`: final SAF copy | nested catch around `copyWithExportCancellation` | Yes | Only covered interval |
| `ExportCancellationIo.copyWithExportCancellation` | checks token/job before every 64 KiB chunk and throws `CancellationException` | Caller-owned | Correct for the final copy interval |
| `deleteSafDocumentBestEffort` | invokes `DocumentsContract.deleteDocument` inside `runCatching` | Best effort | Return value and exception are intentionally unobserved |

The ZIP device failure directly exercises `ChapterPackageExporter`. `PdfAnnotatedExporter` was not re-run on the device for C-2.1, but static inspection proves the same pre-final-copy cleanup gap.

## 3. A/B/C/D classification

| Candidate | Classification | Evidence |
|---|---|---|
| **A. Delete not called** | **CONFIRMED PRIMARY ROOT CAUSE** | All cancellation exits before the final SAF copy bypass the only delete call. The 0-byte baseline is consistent with this exact path. The new pre-copy characterization test observes zero provider delete calls and a surviving 0-byte document. |
| **B. Delete called but failed** | **NOT THE BASELINE CAUSE; SECONDARY OBSERVABILITY RISK** | The baseline path cannot reach the helper. A provider-failure test proves failure is swallowed and would also leave a file, but that is a distinct path. |
| **C. Provider behavior** | **NOT SUPPORTED AS ROOT CAUSE** | A protocol-correct `DocumentsProvider` test proves that, when called, `DocumentsContract.deleteDocument` reaches `deleteDocument` and deletes the backing document. Provider-specific refusal remains possible in general, hence best-effort semantics. |
| **D. Lifecycle race** | **CONTRIBUTING UI/OBSERVATION FACTOR, NOT ROOT CAUSE** | `cancelExport()` publishes `Cancelled` immediately and generation-gates later callbacks. This explains why UI completion precedes exporter drain, but the target still existed more than 21 minutes later. A transient race cannot explain persistent residue; the missing delete call can. |

The late-cancel control from C-2 is a separate completion race: the destination stream had already closed successfully, so the complete valid ZIP is retained. That is expected and is covered by the cancel-after-close test.

## 4. Test audit and new evidence

### 4.1 Previous coverage defect

The previous `ExportSafCancellationCleanupTest` used a generic `ContentProvider` and overrode `delete(uri, ...)`. Production calls `DocumentsContract.deleteDocument`, which uses the Documents provider `call` protocol and dispatches to `DocumentsProvider.deleteDocument`; it does not use the generic resolver `delete` route. The old mid-write test also failed to assert that the production helper reached the provider.

The test provider is now a real `DocumentsProvider` registered with the required exported/grant/`MANAGE_DOCUMENTS` metadata. This makes delete call counts and backing-file outcomes meaningful. The platform call routing is documented in AOSP [`DocumentsContract`](https://android.googlesource.com/platform/frameworks/base.git/+/master/core/java/android/provider/DocumentsContract.java) and [`DocumentsProvider`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/provider/DocumentsProvider.java).

### 4.2 Added/strengthened cases

File: `app/src/test/java/com/markleaf/app/pdf/export/ExportSafCancellationCleanupTest.kt`

| Test | Result | What it proves |
|---|---|---|
| `safDocumentCreated_isInitiallyZeroBytes` | PASS | SAF lifecycle begins with an existing 0-byte destination |
| `cancelBeforeFinalSafCopy_currentEarlyReturn_skipsDeleteAndLeavesZeroByteDocument` | PASS | Current early-return shape calls no delete and reproduces the residue contract |
| `cancelBeforeFirstWrite_afterSafStreamOpened_callsDeleteSuccessfully` | PASS | Once inside the final-copy catch, cancellation before the first chunk deletes the target |
| `cancelDuringWrite_callsDeleteAndRemovesPartialDocument` | PASS | Per-chunk cancellation invokes delete and removes partial output |
| `cancelAfterStreamClose_keepsCompletedDocumentAndDoesNotDelete` | PASS | Post-close cancellation retains the completed output |
| `deleteSuccess_routesThroughDocumentsProvider_andRemovesDocument` | PASS | Production helper reaches `DocumentsProvider.deleteDocument` |
| `deleteFailure_isSwallowed_andDocumentRemains` | PASS | Best-effort failure remains non-throwing and leaves the document |

These are characterization and protocol tests only. They intentionally preserve the current failing early-return behavior and do not implement a fix.

### 4.3 Verification runs

| Command/scope | Result |
|---|---|
| `:app:testQaUnitTest --tests com.markleaf.app.pdf.export.ExportSafCancellationCleanupTest --rerun-tasks` | PASS, 7/7 |
| `:app:testQaUnitTest --tests com.markleaf.app.pdf.export.*` | PASS, 35/35 |
| `:app:testQaUnitTest` | PASS, 1152 tests, 0 failures, 0 errors, 0 skipped |

No `assemble*` task was run, no APK was produced or replaced, and the v0.1.0 RC was not re-released.

## 5. Proposed fix — not implemented

Approval is required before product changes. The recommended minimal fix is:

1. Make the SAF destination lifecycle owned by the whole exporter invocation, not only the final stream-copy block.
2. Route every cancellation checkpoint through one cancellation exit. On any cancellation before a successful destination stream close, call `deleteSafDocumentBestEffort(outputUri)` exactly once.
3. Mark the destination committed only after the final SAF output stream closes successfully. A cancel received after that boundary must retain the completed file.
4. Preserve the current private `workDir` cleanup and structured coroutine cancellation.
5. Return or record the delete outcome so a provider refusal can be distinguished from a missing call during device acceptance; do not silently classify both as the same failure.
6. Apply the same ownership rule to both `ChapterPackageExporter` and `PdfAnnotatedExporter`, then add exporter-level regression coverage around the real early checkpoints.

A straightforward implementation would convert early cancellation returns into a single thrown cancellation path and perform destination cleanup in the outer cancellation handler while guarding the post-close committed state. This needs no UI, import, identity, version, or architecture change.

## 6. Pending after C-2.1

- Product fix is **not implemented** and awaits explicit approval.
- After an approved fix, repeat real-device ZIP cancellation before first write and during write; both must leave no destination.
- Repeat annotated-PDF cancellation on device because it shares the static gap but lacks direct C-2.1 device evidence.
- Capture/report provider delete failure distinctly if the selected provider returns failure or throws.
- C-2 pinch zoom remains an unrelated human-input pending item; it is not part of this root cause.

## 7. Scope compliance

- No UI, import, package identity, version, build configuration, or release artifact was changed.
- No new feature, TOC, EPUB, cloud-drive, B4 pending fix, or architecture adjustment was introduced.
- No product code fix was made.
- Only the cancellation test evidence and this report were changed.

**Final C-2.1 status: ROOT_CAUSE_CONFIRMED / FIX_PENDING_APPROVAL**
