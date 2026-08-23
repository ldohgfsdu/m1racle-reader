# MarkLeaf Release Candidate — INTERIM / DEVICE BLOCKED

> **历史快照（2026-07-21）**：本文记录当时的发布就绪评估，不代表当前状态；当前工程与交付状态见 `.memory/CURRENT_STATE.md` 与 `docs/APK_DELIVERY.md`。

**Date:** 2026-07-21 (continued host validation)  
**Status line (required):** **host gates passed / device and full release validation incomplete**  
**Not a final verdict:** do **not** treat this as `accepted` / `conditionally accepted` / `rejected`.

Goal source: `/storage/emulated/0/inbox/MarkLeaf/MarkLeaf_Release_Candidate_Long_Goal.md`

---

## Candidate package (current rebuild)

| Field | Value |
|-------|--------|
| Variant | **release** (`isMinifyEnabled=true`, `isShrinkResources=true`) |
| applicationId | `com.markleaf.app` |
| versionCode / versionName | `1` / `0.1.0` |
| Path | `.external_outbox/markleaf-release-latest.apk` |
| Timestamped | `.external_outbox/markleaf-release-v0.1.0-20260721-225720.apk` |
| Download copy | `/storage/emulated/0/Download/markleaf-release-latest.apk` |
| Size | **10 925 075** bytes (~10.4 MB) |
| **SHA-256** | `afcb16573b4632b864164ed6ceec3714bea8369b5f5858d0cb8660197297a7a5` |
| R8 mapping | `app/build/outputs/mapping/release/mapping.txt` **present** |
| R8 usage | `app/build/outputs/mapping/release/usage.txt` **present** → shrink active |
| Unit suite | **937 tests, 0 failures** |

**Note:** Earlier interim SHAs (`206bdff…`, `d253dba…`) are **superseded** by this rebuild (PFD open-order fix, package-dir cleanup on delete, zip limits, etc.).

---

## Evidence locations

| Evidence | Path |
|----------|------|
| Unit summary | implementer scratch `unit-test-summary.txt` → **933 tests, 0 fail** |
| Full gate log | `full-gate-rerun.log` |
| Host extra tests | `host-extra-tests.log` |
| Lint class map | `lint/classified.txt` + `lint-summary.txt` |
| APK size | `apk/size-breakdown-refined.txt` |
| Signing notes | `apk/signing.txt` |
| Git classification | `git/classification.txt` |
| Device matrix | `device-matrix.md` / `adb-now.txt` (empty devices) |

Scratch root: `/tmp/grok-goal-3c8250cb148c/implementer/` (session-local).

---

## Twelve-stage matrix (maps to original /goal)

Legend: **PASS** / **FAIL** / **PARTIAL** / **NOT RUN** / **BLOCKED**

### Stage 0 — Baseline inventory & host gates

| Item | Status | Evidence |
|------|--------|----------|
| git status / dirty-tree inventory | **PASS** | `git/classification.txt`: 35 tracked mods (+2535/−1039); 29 untracked; no cleanup |
| Variants / version / minify | **PASS** | `app/build.gradle.kts` release minify+shrink; versionName 0.1.0 |
| Unit suite | **PASS** | `./gradlew :app:testDebugUnitTest` → **937 / 0** |
| assembleRelease | **PASS** | `full-gate-rerun.log` OUTBOX copy |
| lintRelease | **PASS** (warnings remain) | 0 errors; 53 warnings classified |

### Stage 1 — Markdown integrity

| Item | Status | Evidence |
|------|--------|----------|
| HTML empty anchors id/name | **PASS** (host) | `HtmlEmptyAnchorTest`, `MarkdownEdgeCaseHostTest` |
| EOF orphan anchors not last-top | **PASS** (host) | `ignoredTrailingHtmlAnchors` + tests |
| TOC plain titles for `## [text](#id)` | **PASS** (host) | `TocTitleAndInPageLinkTest` |
| `#` vs http(s) routing | **PASS** (host) | `MarkdownLinkRouter` + edge tests |
| Table header/body shared align/width/padding | **PASS** (host) | `TableHeaderBodyAlignmentTest` |
| Duplicate anchors stable last-wins | **PASS** (host) | `MarkdownEdgeCaseHostTest.duplicateHtmlAnchors_lastWinsStable` |
| Chinese + URL-encoded fragments | **PASS** (host) | `MarkdownEdgeCaseHostTest` |
| Code fence anchors not registered | **PASS** (host) | edge + HtmlEmptyAnchor tests |
| Cross-doc AnchorRegistry isolation | **PASS** (host) | `AnchorRegistryIsolationTest` |
| 老子 fixture parse | **PASS** when mounted | `LaoziAnchorFixtureTest` |
| Device UI (TOC click, table link, jump offset) | **BLOCKED** | ADB offline |

### Stage 2 — Reader chrome & scroll state

| Item | Status | Evidence |
|------|--------|----------|
| Top bar hide/show/jitter pure state | **PASS** (host) | `ReaderTopBarScrollStateTest` |
| Top bar offset read inside graphicsLayer | **PASS** (code) | `ViewerScreen.ReaderTopBarOverlay` — avoids per-pixel overlay recompose |
| Progress save only when scroll settles | **PASS** (structural) | `ViewerScreen` snapshotFlow `isScrollInProgress` |
| 100-gesture stress / process kill restore | **BLOCKED** | needs device |
| Scroll jank absolute frame metrics | **NOT RUN / device pending** | Host analysis complete (see §Scroll jank) |

### Stage 3 — Home & files

| Item | Status | Evidence |
|------|--------|----------|
| Home header collapse state | **PASS** (host) | `HomeHeaderScrollStateTest` |
| Scaffold FAB padding lint | **PASS** | HomeScreen uses `scaffoldPadding.calculateBottomPadding()` |
| External open / cold share paths | **PARTIAL** | Existing share unit tests; full UI **BLOCKED** |

### Stage 4 — PDF (implemented path)

| Item | Status | Evidence |
|------|--------|----------|
| Existing PDF policy/gesture/export unit suite | **PASS** | `:app:testDebugUnitTest --tests com.markleaf.app.pdf.*` |
| Close-order contract (renderer→PFD) | **PASS** (host pure) | `PdfResourceCloseOrder` + `PdfResourceCloseOrderTest` |
| ViewModel clear / applicationScope close | **PASS** (host) | `ViewModelClearOrderEvidenceTest` |
| Document-switch cache clear plan | **PASS** (host pure) | `documentSwitchPlan()` asserts caches before reopen |
| Real PdfRenderer open/close on device | **BLOCKED** | no instrumented device |
| PFD leak if PdfRenderer ctor fails | **PASS** (host code) | `PdfPageRenderer.open` assigns `pfd` **before** `PdfRenderer()`; failed-open still `closeLocked()`; contract tests cover PFD-only close |

### Stage 5 — EPUB / ZIP packages

| Item | Status | Evidence |
|------|--------|----------|
| EPUB full reader | **NOT IN PRODUCT** | No `DocumentType.EPUB`; design-only `docs/EPUB_SUPPORT_ARCHITECTURE_DESIGN.md` |
| Markdown-in-ZIP package import | **PASS** (host) | `ZipPackageImporterTest`, package image tests |
| Zip Slip | **PASS** (host) | `..` / nested traversal tests |
| Zip bomb / entry limits | **PASS** (host, newly added) | `MAX_ENTRY_COUNT=5000`, `MAX_SINGLE_ENTRY_BYTES`, `MAX_TOTAL_UNCOMPRESSED_BYTES=200MiB`; tests for count + size |
| EPUB fixture regression | **NOT RUN** | no EPUB production path to exercise |
| Package dir cleanup on document delete | **PASS** (host) | `DocumentRepository.deleteById` wipes `packages/{id}`; `PackageDirCleanupPolicyTest` |

### Stage 6 — Search / settings / isolation

| Item | Status | Evidence |
|------|--------|----------|
| Search grouping isolation unit | **PASS** | `DocumentSearchIsolationTest` |
| Settings pure units (if present) | **PARTIAL** | reader settings covered indirectly |
| Cross-doc search/registry isolation | **PASS** (host) | registry isolation + search isolation tests |
| Device multi-doc switch UI | **BLOCKED** | |

### Stage 7 — Performance & jank (document scroll)

| Item | Status | Evidence |
|------|--------|----------|
| Host root-cause analysis | **PASS** | See §Scroll jank below; Lazy keys/types present; parse not on scroll |
| Host mitigations this round | **PASS** | Top bar offset inside `graphicsLayer`; zip/PDF pure contracts |
| gfxinfo / Perfetto / 120Hz human feel | **BLOCKED** | device pending |
| Historical flag | **OPEN / device pending** | `document_scroll_jank: open_outside_r10_viewer_path` — R10 closed home only |

### Stage 8 — Security & package hygiene

| Item | Status | Evidence |
|------|--------|----------|
| No INTERNET permission | **PASS** | Manifest comment + audit |
| QA experiments disabled in release | **PASS** | `READER_PERFORMANCE_EXPERIMENTS_ENABLED=false` on non-qa; qa buildType only true |
| QA Intent flags no-op in release | **PASS** | `ReaderPerformanceExperiments.configure` |
| PDF `Log.d` gated by `isDebuggable` | **PASS** (mostly) | ViewModel patterns; release not debuggable |
| profileable shell | **PASS** (intentional) | Manifest for macrobenchmark |
| allowBackup + dataExtractionRules | **PASS** | Added `@xml/backup_rules` + `data_extraction_rules` |
| Verbose Log in release PDF | **PARTIAL** | Some paths still `Log.d` without isDebuggable — deferred non-blocking |

### Stage 9 — Lint classification (53 warnings)

| ID | Count | Severity class | Action |
|----|-------|----------------|--------|
| UnusedResources | 22 | Cosmetic | **Defer** — strings for PDF outline/export UI not always referenced |
| ModifierParameter | 13 | Style | **Defer** — Compose API order; no functional risk |
| GradleDependency | 11 | Upgrade | **Defer** — non-goal: no blind upgrades |
| TrustAllX509TrustManager | 3 | Dependency noise | **Defer** — from BouncyCastle jar inside PDFBox stack; app has no INTERNET |
| OldTargetApi | 1 | Policy | **Defer** — targetSdk 35 intentional until full retest |
| UnclosedTrace | 1 | Tooling | **Addressed** — restructure Direct/Animated Trace sections in `scrollToBlockIndex` |
| DataExtractionRules | 1 | Policy | **Fixed** — added backup + extraction XML |
| ObsoleteSdkInt | 1 | Cleanup | **Defer** — LocalImageLoader SDK check |
| AutoboxingStateCreation | 3 | Info | **Defer** — PDF continuous viewport |

**Blocking lint errors:** 0.

### Stage 10 — APK size & R8

| Component (uncompressed entry sizes) | Bytes | % of entry total |
|--------------------------------------|------:|-----------------:|
| **bouncycastle_props** (`org/bouncycastle/pqc/...properties`) | ~8.26 MB | **46.0%** |
| **dex** (`classes.dex`) | ~4.73 MB | **26.3%** |
| **pdfbox_assets** (cmap/ttf/afm under tom_roush) | ~4.63 MB | **25.8%** |
| resources.arsc + res | ~0.23 MB | 1.3% |
| native (4 ABIs, tiny) | ~60 KB | 0.3% |
| other/kotlin/meta | small | |

| Check | Result |
|-------|--------|
| On-disk APK | ~10.4 MB (zip compression of above) |
| R8 mapping/usage | **Present** → minify/shrink ran |
| Main bulk drivers | PDFBox Android + transitive BouncyCastle PQC property files + single dex |
| ABI | arm64-v8a, armeabi-v7a, x86, x86_64 (graphics-path + datastore_shared_counter only) |

**Host note:** ProGuard `-keep class com.tom_roush.**` and `-dontwarn org.bouncycastle.**` prevent stripping PDFBox/BC resources — expected size floor unless keep rules narrowed (risky; not done this round).

### Stage 11 — Signing

| Scheme | Status | Evidence |
|--------|--------|----------|
| v1 (JAR) | **false** | `apksigner verify -v` |
| v2 | **true** | `apksigner verify -v` |
| v3 / v3.1 / v4 | **false** | `apksigner verify -v` |
| Verifies | **YES** | build-tools 35.0.0 `apksigner` |
| Signer DN | `C=US, O=Android, CN=Android Debug` | **debug keystore** (intentional local RC per `build.gradle.kts`) |
| Cert SHA-256 | `43ff89a0b194b49f4b4d82e34ba4a42875d5649e677a132a026ada4fc74dc30a` | `apk/apksigner-verify2.txt` |
| Play-store production key | **NOT USED** | Non-blocking for local RC; **blocking for store ship** |

Command:

```bash
$HOME/Android/Sdk/build-tools/35.0.0/apksigner verify -v --print-certs \
  .external_outbox/markleaf-release-latest.apk
```

### Stage 12 — Device matrix

| Row | Status | Notes |
|-----|--------|-------|
| ADB `device` | **BLOCKED** | empty `adb devices` |
| Install SHA `d253dba…` | **BLOCKED** | cannot pm install (MIUI MANAGE_USERS) |
| Markdown 老子 UI | **BLOCKED** | |
| Wide table H-scroll UI | **BLOCKED** | host layout PASS |
| TOC / anchors / 20× jump | **BLOCKED** | |
| Home collapse UI | **BLOCKED** | host state PASS |
| PDF open/scroll UI | **BLOCKED** | |
| EPUB | **N/A product** | |
| Rotation / kill restore | **BLOCKED** | |
| 100-gesture stress | **BLOCKED** | |
| gfxinfo / Perfetto / CPU / mem | **BLOCKED** | |
| logcat FATAL/ANR | **BLOCKED** | |
| Screenshots | **BLOCKED** | |

---

## Scroll jank (why still open)

**Flag:** `.memory/active_object.yaml` → `document_scroll_jank: open_outside_r10_viewer_path`

| Finding | Detail |
|---------|--------|
| R10 scope | Home list cards only — **did not** close viewer document scroll |
| Host architecture | LazyColumn has stable `key` + `contentType`; `MarkdownParser.parse` only on open (`Dispatchers.Default`); table `TableLayoutCache` document-scoped; progress writes on settle only |
| Residual risk | First composition of paragraph-heavy items; table cold solve; inline parse on Lazy recycle; (mitigated) top-bar recompose — now offset read **inside** `graphicsLayer` |
| Absolute frame claims | **device pending only** — no gfxinfo/Perfetto this session |

---

## Git classification (no cleanup)

| Bucket | Content |
|--------|---------|
| Pre-goal WIP (dirty tree) | PDF R7, R9 chrome, R10 home, anchor/table WIP, many docs/reports |
| This continued host pass | Zip size limits; `PdfResourceCloseOrder*`; `MarkdownEdgeCaseHostTest`; backup XML; top-bar graphicsLayer; Trace restructure; expanded readiness |
| Untracked docs/scripts | Delivery/device scripts, R7 reports, RELEASE_READINESS — not temp APKs in source |
| Forbidden | No `git reset`, no delete of user data, no push |

---

## This-round code changes (host-executable)

1. **ZipPackageImporter** — entry count + single-entry + total uncompressed limits; `copyWithLimit`  
2. **PdfResourceCloseOrder** + unit tests — close order + document-switch plan  
3. **MarkdownEdgeCaseHostTest** — empty/garbage/dup anchors/Chinese/URL-encoded/missing/relative  
4. **ViewerScreen** — Trace around Direct/Animated safely; top bar offset inside `graphicsLayer`  
5. **Manifest** — `fullBackupContent` + `dataExtractionRules`  
6. **ZipPackageImporterTest** — size/count limit tests  

---

## Open host items (not blocking package build)

| Item | Severity |
|------|----------|
| BouncyCastle PQC properties ~46% of entry bulk | Size — needs careful keep-rule surgery (risk to PDFBox) |
| Signed with **Android Debug** keystore | Policy — OK local RC; **not** store production |
| Full 12-stage device matrix | **External device block** |

### Closed this continuation

| Item | Fix |
|------|-----|
| PFD leak on failed PdfRenderer ctor | Assign `pfd` before construction; `closeLocked` on failure |
| Orphan `packages/{id}` after delete | Best-effort `deleteRecursively` after Room commit |

---

## How to resume when ADB returns (do not restart goal)

1. Install **exactly**  
   `sha256=afcb16573b4632b864164ed6ceec3714bea8369b5f5858d0cb8660197297a7a5`  
   via `bash scripts/install-release-outbox.sh`  
2. Verify installed base.apk SHA matches  
3. Run full UI matrix: 老子 anchors/TOC/table, home, PDF smoke, rotation, kill restore, 100-gesture, logcat, gfxinfo/Perfetto  
4. Replace this INTERIM report with final **accepted / rejected** only after device rows filled  

---

## Conclusion (interim only)

**host gates passed / device and full release validation incomplete**

- Host unit **937 PASS**, Release **builds**, lint **0 errors**, size/signing/R8 **documented**, Markdown/PDF/ZIP security host gaps **narrowed**.  
- PFD failed-open + package-dir cleanup **closed on host**.  
- Device matrix **BLOCKED** (ADB offline).  
- **No final release verdict** until device evidence exists for SHA `afcb165…`.
