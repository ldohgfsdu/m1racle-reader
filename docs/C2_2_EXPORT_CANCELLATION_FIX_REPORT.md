# MarkLeaf C-2.2 Export Cancellation Fix Report

## 0. Result

| Field | Value |
|---|---|
| Date | 2026-08-24 (UTC+8) |
| Implementation base | `3ea5f56583c2c76cd99c1c9af1ac87681a3b8548` |
| Release line | `v0.1.0`, versionCode `22`, versionName `0.1.0` |
| C-2.1 root cause | SAF output was pre-created, but pre-write cancellation bypassed delete |
| C-2.2 status | **IMPLEMENTED / AUTOMATED_VERIFIED / DEVICE_RETEST_PENDING** |

C-2.2 fixes cancellation ownership for both ZIP and annotated-PDF export. A SAF destination is now considered committed only after its final output stream closes successfully. Any `CancellationException` before that boundary performs best-effort destination deletion, preserves private temporary-directory cleanup, and rethrows the original cancellation exception.

No UI, import, ZIP identity, version, release tag, or release metadata was changed.

## 1. Test-first baseline

The lifecycle tests were changed before product code.

Initial red run:

```text
:app:testQaUnitTest
--tests com.markleaf.app.pdf.export.ExportSafCancellationCleanupTest

9 tests completed, 3 failed
```

The three expected failures were:

- ZIP cancellation after SAF document creation but before destination write.
- Annotated-PDF cancellation after SAF document creation but before destination write.
- ZIP pre-write cancellation when provider delete fails; the cancellation contract was not reached because the exporter returned `Cancelled` instead of throwing.

The existing mid-write, provider protocol, and post-close cases remained green. This isolated the red baseline to the C-2.1 ownership gap.

## 2. Implementation

### 2.1 Shared lifecycle rule

Both `ChapterPackageExporter` and `PdfAnnotatedExporter` now follow the same state rule:

```text
SAF document already exists
  -> destinationCommitted = false
  -> export preparation / transformation / final copy
  -> final SAF output stream closes successfully
  -> destinationCommitted = true
```

Cancellation handling is:

```text
catch original CancellationException
  -> if destinationCommitted == false:
       deleteSafDocumentBestEffort(outputUri)
  -> cleanup private workDir and publish Cancelled
  -> throw the same CancellationException
```

Consequences:

- Cancel before the first destination byte: the pre-created 0-byte SAF document is deleted best-effort.
- Cancel during destination copy: partial output is deleted best-effort.
- Provider delete failure: `deleteSafDocumentBestEffort` swallows the provider failure, so it cannot replace the original `CancellationException`.
- Cancel after successful stream close: the completed destination is retained.
- Normal completion: delete is never called.

### 2.2 ZIP exporter

`ChapterPackageExporter` changes:

- All token/job cancellation checkpoints now throw through `throwIfExportCancelled` instead of returning early from the exporter.
- The source-copy error boundary explicitly rethrows `CancellationException` before handling ordinary read failures.
- Extractor cancellation is converted to `CancellationException`.
- The nested final-copy delete catch is removed; all cancellation cleanup is owned by the outer handler.
- `destinationCommitted` is set only after the SAF stream closes.
- The outer cancellation handler performs best-effort SAF delete when uncommitted, then retains existing `workDir` cleanup and rethrows.

ZIP content, manifest, checksums, filename rules, and identity were not changed.

### 2.3 Annotated-PDF exporter

`PdfAnnotatedExporter` changes:

- All existing token/job cancellation checkpoints now throw through `throwIfExportCancelled`.
- The nested final-copy delete catch is removed in favor of the same outer lifecycle owner.
- `destinationCommitted` is set only after the SAF stream closes.
- The outer cancellation handler deletes only uncommitted output, cleans the existing `workDir`, and rethrows.

PDF rendering, cover/footer content, page range, filename rules, and UI state definitions were not changed.

## 3. Regression coverage

`ExportSafCancellationCleanupTest` now uses a real `DocumentsProvider` protocol stub, a valid one-page PDF source, and both real exporters.

| Coverage | Result |
|---|---|
| SAF document starts as existing 0-byte output | PASS |
| ZIP cancel after SAF creation, before write | PASS: delete=1, output absent, CE rethrown |
| Annotated PDF cancel after SAF creation, before write | PASS: delete=1, output absent, CE rethrown |
| ZIP pre-write cancel with provider delete failure | PASS: delete attempted, output remains, original CE preserved |
| Cancel after output stream opened but before first write | PASS |
| Cancel during write | PASS: partial output deleted |
| Cancel after successful stream close | PASS: completed output retained, delete=0 |
| Provider delete success | PASS |
| Provider delete failure | PASS: failure swallowed, document remains |
| ZIP normal completion | PASS: real exporter output retained, delete=0 |
| Annotated-PDF normal completion | PASS: real exporter output retained, delete=0 |
| Private export work directories | PASS: absent after cancellation and completion cases |

Final automated results:

| Gate | Result |
|---|---|
| C-2.2 SAF lifecycle class | **11/11 PASS** |
| `com.markleaf.app.pdf.export.*` | **39/39 PASS** |
| `:app:testQaUnitTest` | **1156 tests, 0 failures, 0 errors, 0 skipped** |
| `:app:assembleRelease` | **PASS** (`BUILD SUCCESSFUL`, final run 1m 11s) |

## 4. Local release-build verification

The required release build was produced only as a local verification artifact. It was not tagged, published, or declared a new release candidate.

| Field | Value |
|---|---|
| APK | `.external_outbox/markleaf-release-latest.apk` |
| Timestamped output | `.external_outbox/markleaf-release-v0.1.0-20260824-170238.apk` |
| Size | 11,493,231 bytes |
| SHA-256 | `68bb4990b5ee0592ad0b976f9efeeb2a19f39f11671c48e3e378478fc9f80141` |
| Build time | `2026-08-24 17:02:38 +0800` |
| APK package | `com.markleaf.app` |
| APK versionCode / versionName | `22` / `0.1.0` |
| Download verification copy | `/storage/emulated/0/Download/markleaf-release-latest.apk` |
| Download SHA-256 | `68bb4990b5ee0592ad0b976f9efeeb2a19f39f11671c48e3e378478fc9f80141` |
| Release tag target | `v0.1.0` -> `5febda3bc0ea2582cdff276457ffec466e072650` (unchanged) |

The Download copy was required by the repository build-delivery rule after a successful `assemble*`. The APK was not installed and C-2 device acceptance was not rerun in this task.

Build note: an earlier verification run printed a transient ASM `NegativeArraySizeException` diagnostic while Gradle continued. The final rerun completed `lintVitalAnalyzeRelease`, `lintVitalRelease`, R8, packaging, and `assembleRelease` without that diagnostic and exited 0. No lint/build configuration was changed.

## 5. Pending

- C-2 remains `HUMAN_REJECTED` until real-device export cancellation is repeated against this fix build.
- Required device retest: ZIP cancel before first write, ZIP cancel during write, and annotated-PDF cancellation.
- Verify on the device that no 0-byte or partial SAF target remains after the cancelled result settles.
- A provider that refuses deletion may still leave a document by design; the cancellation exception remains authoritative and the failure is best-effort.

## 6. Scope compliance

- No UI changes.
- No import changes.
- No ZIP identity changes.
- No versionCode/versionName changes.
- No release tag changes.
- No release publication or device installation.
- No unrelated refactor or architecture adjustment.

**Final C-2.2 status: IMPLEMENTED / AUTOMATED_VERIFIED / DEVICE_RETEST_PENDING**
