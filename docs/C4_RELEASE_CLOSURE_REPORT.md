# MarkLeaf C-4 Release Closure Report

## 0. Closure result

| Field | Value |
|---|---|
| Date | 2026-08-24（UTC+8） |
| Release form | C-local（可信用户侧载） |
| Source commit | `604d404978b5f09fdb4a9c825259ef6793f59949` |
| Annotated tag | `v0.1.0-c-local` → `604d404978b5f09fdb4a9c825259ef6793f59949` |
| Version identity | package `com.markleaf.app`; versionCode `22`; versionName `0.1.0` |
| Final APK SHA-256 | `4dd9c7ebacec6276fbca0615107ce2885f35c8a70bf81de0be818b39035a3ba4` |
| Device acceptance | C-2.3 **HUMAN_ACCEPTED** |
| Final status | **CLOSED** |

C-4 closes the C-local release line without changing production code, UI, product scope, architecture, or version identity. The original annotated `v0.1.0` tag remains at `5febda3bc0ea2582cdff276457ffec466e072650`; it was not moved or overwritten.

## 1. Release source and artifact

The release source is the exact C-2.2 export-cancellation fix commit:

```text
604d404978b5f09fdb4a9c825259ef6793f59949
fix: clean SAF output on export cancellation
```

The final release build was produced from a detached, clean checkout of that exact commit.

| Artifact check | Evidence |
|---|---|
| Timestamped APK | `.external_outbox/markleaf-release-v0.1.0-20260824-223117.apk` |
| Latest APK | `.external_outbox/markleaf-release-latest.apk` |
| Device Download delivery | `/storage/emulated/0/Download/markleaf-release-latest.apk` |
| Size | `11,493,231` bytes |
| SHA-256 | `4dd9c7ebacec6276fbca0615107ce2885f35c8a70bf81de0be818b39035a3ba4` |
| Copy identity | Timestamped / latest / Download copies are byte-identical |
| APK badging | `com.markleaf.app`, versionCode `22`, versionName `0.1.0` |
| Signature | `apksigner verify` PASS; APK Signature Scheme v2; C-local debug certificate DN `C=US, O=Android, CN=Android Debug` |

## 2. Automated verification

| Gate | Result |
|---|---|
| `:app:assembleRelease --rerun-tasks` | **PASS** — 2026-08-24 22:26:31–22:31:19 UTC+8; 289s; 52 tasks executed |
| `:app:testQaUnitTest --rerun-tasks` | **PASS** — 1156 tests; 0 failures; 0 errors; 0 skipped |
| Release dex hygiene | **PASS** — `CHROME_STATE`, `PDF_SYSBARS`, `MARKDOWN_REFRESH` absent |
| Release permissions | **PASS** — no INTERNET/user network permission; only the AndroidX-generated non-exported receiver internal permission is present |
| Artifact ledger match | **PASS** — on-disk SHA matches Phase C and C-2.3 promotion records |

## 3. Device acceptance

C-2.3 evidence is committed at `7594a30dbe52667e663d54e5840395309fcfe26a` and records **HUMAN_ACCEPTED** against a clean build of the same exact source commit `604d404`:

- ZIP export mid-cancel reached `已取消导出` with no zero-byte or partial SAF destination.
- Annotated-PDF cancellation left no zero-byte or partial destination.
- Normal ZIP export completed, remained present, and passed integrity verification.
- APK/device identity was versionCode `22`, versionName `0.1.0`; install and cold launch passed.

The C-2.3 installed APK SHA was `7285123ca78bf531580fe7bfda64c4e0e0d6737be342b2a9fe5c5711fd722e95`. The final closure APK SHA is different because separate builds are not byte-identical. Both builds use the same clean, exact source commit and version/signature identity. Device acceptance applies to the source baseline; this report does not claim that the final `4dd9c7...` digest was separately reinstalled.

## 4. Frozen non-blocking pending

These items remain recorded and do not block C-local closure. C-4 does not authorize their implementation.

| Item | Closure classification |
|---|---|
| Cross-entry ZIP identity | Non-blocking quality issue; same ZIP may produce duplicate library rows across import paths; tracked as B4 P-1 |
| SourceTooLarge UI copy | Functional rejection is correct; dedicated >200MiB wording remains deferred; tracked as B4 P-3 |
| LocalImageLoader `Log.w` gating | Release-hardening cleanup only; tracked as B4 P-4 |
| Force-stop during active export reproduction | Evidence-completeness item, not a confirmed defect; tracked as B4 P-5 |
| ThumbnailLoader `closeAsync` observation | Long-session race observation; no confirmed defect |
| B-1 uninterrupted long-session evidence | Existing retention behavior is verified; uninterrupted supplementary device run remains optional if still needed |

The earlier small-export cancellation UX ambiguity is retained in B4 as an already-characterized future polish idea, not as a C-local release pending defect: when cancellation arrives after `Completed`, the retained output is a complete valid artifact. The original C-2 pinch-zoom limitation remains an evidence boundary and was not rewritten as PASS by the targeted C-2.3 retest.

## 5. Closure checklist

- [x] Release source commit is immutable and identified.
- [x] Annotated final tag exists and resolves to the release source.
- [x] Historical `v0.1.0` tag remains unchanged.
- [x] Final APK is locatable in timestamped, latest, and Download delivery paths.
- [x] APK SHA-256, size, package, versionCode, versionName, and signature are recorded and verified.
- [x] Required automated build/test/hygiene evidence is recorded.
- [x] C-2.3 HUMAN_ACCEPTED device evidence is linked without overstating binary identity.
- [x] CHANGELOG, Phase C release checklist, and B4 known-issues ledger are synchronized.
- [x] Production code, UI, features, and architecture are unchanged by C-4.
- [x] Repository worktree is clean after the closure documentation commit.

## 6. Final status

**CLOSED**

MarkLeaf v0.1.0 C-local is closed on source commit `604d404978b5f09fdb4a9c825259ef6793f59949`, annotated tag `v0.1.0-c-local`, and final artifact SHA-256 `4dd9c7ebacec6276fbca0615107ce2885f35c8a70bf81de0be818b39035a3ba4`.
