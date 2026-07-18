# CURRENT_STATE

**Updated:** 2026-07-19  
**Active object:** `R9_COMPOSE_LIST_VS_PDF_SCROLL_ISOLATION`  
**Status:** **DEVICE_ACCEPTED** (human feel passed; R9 closed)

## 2026-07-19 R9 device acceptance

- Human result: **PASS / DEVICE_ACCEPTED** on 25098PN5AC / Android 16.
- Frozen QA candidate and pulled installed APK match SHA-256 7e3df156cba089de515e0b3b1a45ae8b2ca4e1674aee68413a3eca7ff61f19cf.
- Session endpoint: 198.18.0.1:37173; 1220x2656 panel with 120 Hz support.
- Real MainActivity cold entry into Large Search Document; normal reading, slow drag, micro-jitter, clear reversal, immediate reversal after fling, and continued-use feel passed.
- No stickiness, unintended jump, twitch, delayed direction switch, visible snap artifact, or feel drift was reported.
- Evidence: dist/r9-markdown-scroll-chrome/human-feel-pass/20260719-device-accepted/.
- Post-pass gfxinfo was invalid because the process was no longer dumpable and is not used as positive evidence; crash buffer was empty and logcat had no MarkLeaf FATAL/ANR.
- Frozen chrome Perfetto SHA-256: 38451ae9a93d331713b6850f633f9c1e1e52739dd5dee94220cae8aa660de467.

## 2026-07-18 human rejection: first-entry scrolling

- Human result: FAIL — immediately after entering the QA reader, scrolling visibly drops frames or appears to stall.
- Reproduced after cold process start on 25098PN5AC / Android 16 / 120 Hz; no production code changed.
- Perfetto artifact: `dist/r9-markdown-scroll-chrome/first-entry-repro/20260718-human-reject-first-scroll-r2/first-scroll-r2.pftrace`.
- FrameTimeline: 172 frames over 1.552s; 70 frames over 8.33ms; 8 jank rows (4 App Deadline Missed + 4 Buffer Stuffing); p95 11.998ms.
- Cold gfxinfo samples reproduced 2.06–2.60% modern janky with p95 28–53ms and p99 105–121ms.
- Baseline-profile-installed rerun still reproduced 14/555 janky (2.52%), p95 40ms, p99 113ms, and 14 deadline misses; not a one-time JIT warm-up issue.
- Main-thread evidence points to LazyColumn item animation/recomposition/measure, lazy prefetch, and semantics updates. The top-bar candidate remains snap-only and is not identified as the dominant work in these jank frames.
- Simpleperf symbol sampling is unavailable because the device rejects `security.perf_harden=0`; no CPU-symbol claim is made.
- **2026-07-18 QA A/B complete** (Intent switches, real MainActivity, cold first scroll):
  - Report: `docs/R9_FIRST_ENTRY_PERF_AB_DIAGNOSTIC.md`
  - Artifacts: `dist/r9-markdown-scroll-chrome/first-entry-ab/20260718-084025-tight-r3` (3× tight) + `.../20260718-083808` (single)
  - Flags verified via `ReaderPerfExperiment` logcat on every run.
  - **No dominant isolation win:** mean modern janky stays ~0.2–0.5%; mean p99 ~31–37 ms.
  - **disable_prefetch hurts:** legacy janky 31% → 47% (do not ship).
  - **clear_semantics:** no p99 win (slightly worse mean).
  - **disable_top_bar:** only small p95/p99 edge after chrome C; not enough alone.
  - Automation still under-reproduces human-reject severity (human p99 ~113 ms).
- **2026-07-18 item-cost closed loop** (done):
  - Attribution: Large Search Document is ~50% heading / ~50% paragraph; device first-scroll compose wall time **dominated by paragraph** (`MarkdownBlockPerf`: paragraph 11.2 ms vs heading 8.1 ms; `fastPath=29`).
  - Production fix: `InlineMarkdownParser` plain-text fast path (mid-word `_` not markup) + `InlineMarkdownText` memoization. **Not** shipping prefetch-off / semantics-off / fixed top bar.
  - Device cold first-scroll (same protocol, no extras): mean **p99 33→30**, **legacy jank 31%→27.7%**.
  - Report: `docs/R9_FIRST_ENTRY_ITEM_COST_CLOSED_LOOP_REPORT.md`
  - APK: `dist/r9-markdown-scroll-chrome/markleaf-qa-first-entry-item-fix.apk`  
    SHA-256 `7e3df156cba089de515e0b3b1a45ae8b2ca4e1674aee68413a3eca7ff61f19cf`
  - Artifacts: `dist/r9-markdown-scroll-chrome/first-entry-item-fix/20260718-085401-post/`
  - Unit tests: `InlineMarkdownParserPerformanceTest` PASS
  - Human gate: **PASS / DEVICE_ACCEPTED on 2026-07-19**

## Dual root cause (unchanged)

1. **Markdown:** scroll-hide top bar chrome — **production candidate C applied**  
2. **Home:** multi-line card item work (ladder E) — **not touched**; next candidate `R10_HOME_LIST_CARD_PERFORMANCE`

## Markdown chrome production fix (C)

- Overlay + `graphicsLayer.translationY` snap (no `Animatable.animateTo` frame loop)
- Thresholded nestedScroll observe-only (does not consume body deltas)
- Slow same-direction deltas accumulate; jitter applies to accumulated reversal instead of dropping frames
- Fixed LazyColumn top inset (`ReaderTopBarHeight + 8.dp`)
- Settled-only forceShow at top; document-keyed state reset
- Search mode: nestedScroll off + forceShow
- Report: `docs/R9_MARKDOWN_SCROLL_CHROME_RECOVERY_REPORT.md`
- APK: `dist/r9-markdown-scroll-chrome/*-c-slow-drag.apk` (debug + qa)

## ADB retest (25098PN5AC / Android 16)

### 2026-07-18 reconnect rerun

- Device reconnected at `198.18.0.1:36611`; 1220×2656 internal display confirmed at 120 Hz.
- Formal QA runner result: PASS; artifacts: `dist/r9-markdown-scroll-chrome/adb-runs/20260718-070458`.
- Installed QA APK SHA-256 matches candidate: `89e5d81c590cdf609e3b7ff3f8f5ac09a3f572380a4d34d82f38f126d1406a2b`.
- 5/5 focused gfxinfo runs valid: four runs at 0.00% modern janky, one at 0.12%; one total deadline miss.
- No QA FATAL/ANR markers.
- Runner correction only: landscape reverse drag increased from 18% to 36%. At landscape height, 18% was ~59dp and could not cross the production 88dp show threshold; production code/APK unchanged.
- Current rerun skipped the second-document fixture because its card was absent; prior document-reset evidence remains the positive control.
- This rerun does not close the reopened 8.33ms Perfetto gate and does not replace the pending human-feel gate.

- QA install + real MainActivity slow/fast hide-show: PASS
- Reverse jitter / search / landscape / document reset / settled top: PASS
- 5 valid focused gfxinfo runs: 0.08–0.10% modern janky; 1 deadline miss each
- Raw Perfetto: `dist/r9-markdown-scroll-chrome/r9-md-chrome-c-slow-drag.pftrace`
- Prior FrameTimeline used an insufficient 16.67 ms interpretation. At 120 Hz the budget is 8.33 ms; p95 10.133 ms and 47/279 frames over 8.33 ms reopen the performance gate.
- Report: `docs/R9_MARKDOWN_SCROLL_CHROME_ADB_RETEST.md`
- Repeatable runner: `scripts/run-r9-markdown-chrome-adb-qa.sh`
- Human feel: **PASS / DEVICE_ACCEPTED on 2026-07-19**

### Human gate (closed 2026-07-19)

- Round 1: normal reading gestures; do not deliberately hunt for defects
- Round 2: slow drag, micro-jitter, clear reversal, immediate reversal after fling
- Pass requires no stickiness, unintended jump, twitch, delayed direction switch, visible snap artifact, or feel drift after several minutes
- Result: **PASS**; conditions, APK/trace hashes, and device evidence are frozen above.

## PDF continuous positive control (VALID)

- Production PDF code: **not modified**
- Prior UI/FT evidence still valid from isolation report

## Constraints

R9 closure commit is allowed after the human pass. No push / remote action was requested.  
Do not start Home code or alter PDF production code until the next task begins.

## Reports

- `docs/R9_MARKDOWN_SCROLL_CHROME_RECOVERY_REPORT.md` (this recovery)
- `docs/R9_COMPOSE_LIST_VS_PDF_SCROLL_ISOLATION_REPORT.md` (isolation A/B + PDF control)
