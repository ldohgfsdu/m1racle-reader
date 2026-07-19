# R10 Home List Card Performance Report

**Date:** 2026-07-19
**Object:** R10_HOME_LIST_CARD_PERFORMANCE
**Scope:** Home recent-file cards only
**Conclusion:** DEVICE_ACCEPTED_V3 (feel shell + restored visible card lines)

## 1. Prior evidence

- R9 ladder A-D remained smooth; ladder E (Home-like Surface plus three Text nodes) was the first complexity jump.
- Prior deep slice: ladder A measure>5ms = 2; ladder E = 85. RenderThread was not the bottleneck.
- Current production already fixes supporting/progress footprints and memoizes title resolution.

## 2. Current-HEAD real Home baseline

- Device: 25098PN5AC / Android 16 / 1220x2656 / active 120 Hz.
- Package: com.markleaf.app.qa, real MainActivity, retained recent-document data.
- APK SHA-256: 7e3df156cba089de515e0b3b1a45ae8b2ca4e1674aee68413a3eca7ff61f19cf.
- Protocol: discard initial process warm-up; each scored run is 3 down plus 3 up swipes at 350ms.

| Run | Frames | Modern jank | Legacy jank | p95 | p99 | Deadline miss |
|---|---:|---:|---:|---:|---:|---:|
| r5 | 527 | 0.19% | 7.02% | 11ms | 12ms | 1 |
| r6 | 560 | 0.18% | 10.71% | 11ms | 13ms | 1 |
| r7 | 466 | 0.21% | 3.00% | 10ms | 13ms | 1 |

Weighted baseline: 1553 frames, modern jank 0.19%, legacy jank 7.15%; mean p95 10.67ms, mean p99 12.67ms.

## 3. Valid Perfetto baseline

- Valid trace: dist/r10-home-list-card-performance/baseline/20260719-current-head/perfetto/r10-home-baseline-valid-20260719.pftrace
- SHA-256: d18d243ea9fa95b229b43b5adf41f2224a37283a14b3ee6bdd0b71482f09a3d9.
- The earlier r10-home-baseline-20260719.pftrace did not overlap the gesture flow and has zero app frames; it is invalid and excluded.

| Metric | Value |
|---|---:|
| FrameTimeline span | 33.294s |
| Frames | 3356 |
| p50 / p95 / p99 | 5.018 / 8.943 / 11.114ms |
| >8.33ms / >16.67ms | 237 / 2 |
| jank rows | 137 |
| App Deadline Missed rows | 4 |
| Buffer Stuffing rows | 72 |
| Unknown Jank rows | 59 |
| doFrame >8.33ms | 10 / 3457 |
| measureAndLayout >5ms | 1 |
| RenderThread DrawFrames >8.33ms | 0 |

Main-thread aggregate evidence: measureAndLayout 1868.885ms, lazy prefetch compose 913.928ms, Compose:recompose 621.152ms over the full focused flow. The remaining app tail is small; most FrameTimeline jank rows are Buffer Stuffing or Unknown rather than app deadline misses.

## 4. Rejected production-safe micro-candidate

Candidate: separate LazyColumn header/card contentType, remember formatMeta(document), and reuse the already-read meta color.

| Dataset | Modern jank | Legacy jank | mean p95 | mean p99 | Deadline/run |
|---|---:|---:|---:|---:|---:|
| Before r5-r7 | 0.19% | 7.15% | 10.67ms | 12.67ms | 1.0 |
| Candidate x3 | 0.00% | 11.48% | 11.67ms | 15.00ms | 0.0 |

Result is mixed: modern deadline counters improved, but p95/p99 and legacy tail regressed. The production diff was reverted and is not shipped.

## 5. QA ladder sub-isolation

Three runs per scenario, Latin-square order, same swipe protocol:

| Scenario | Structure | Weighted legacy jank | mean p95 | mean p99 |
|---|---|---:|---:|---:|
| E | Surface plus three Text nodes | 10.80% | 12.00ms | 13.67ms |
| E_NO_SURFACE | Three Text nodes, no item Surface | 9.90% | 11.33ms | 14.33ms |
| E_ONE_TEXT | Surface plus one three-line Text | 5.08% | 9.67ms | 12.33ms |

Removing Surface does not provide a dominant win. Collapsing three Text/layout nodes into one is the only consistent gain, so the residual is multi-Text/layout-tree cost rather than the card shell.

## 6. Decision

- Production HomeScreen now uses a cached single draw node for the three independently styled card lines.
- The implementation keeps independent typography, one-line ellipsis, menu-reserved width, fixed vertical positions, and a combined accessibility description; it does not change scroll physics.
- Current real Home is already near the platform deadline gate: about 0.2% modern jank and only four App Deadline Missed rows across 33.294s.
- Automated UI/performance gates pass; final user feel confirmation remains pending.
- QA-only E_NO_SURFACE and E_ONE_TEXT remain as regression/diagnostic anchors.
- PDF and Markdown production paths were not modified.

## 7. Verification

- ./gradlew :app:testDebugUnitTest :app:assembleQa --quiet: PASS for the rejected production candidate.
- ./gradlew :app:assembleQa --quiet: PASS after adding QA-only sub-ladders.
- No MarkLeaf FATAL or ANR markers in the captured baseline/candidate/sub-isolation logs.
- Commit/push: not done.

## 8. Reopened short-fling optimization (2026-07-19)

- Protocol: real Home, 120Hz, one 500px/120ms flick per reset, UI-tree dump used as settle barrier.
- Warm pre-candidate samples: weighted legacy jank 6.53%; p95 9-11ms.
- Post-candidate five runs: 1016 frames, modern jank 0.10%, legacy jank 2.76%, mean p95 7.2ms (7/7/7/7/8), no FATAL or ANR.
- UI regression check: card height 294px versus prior 297px (under 1dp), menu opens/dismisses, card text remains available as one combined accessibility node.
- QA APK SHA-256: c3c3e1b670c09669e8173c821bffa355c85e54077181c87eb42c3bab76b3d659.
- Build: `./gradlew :app:testDebugUnitTest :app:assembleQa --no-daemon` PASS.

## 9. Rebuilt-candidate continuity recovery (2026-07-19)

- On resume, the report/state said the cached-text production candidate was applied, but the tracked `HomeScreen.kt` had drifted back to the three-`Text` baseline. No PDF or Markdown source was involved.
- The final accepted source was recovered exactly: cached drawing for the three styled/ellipsized lines, combined accessibility description, 40dp menu reservation, and the original Box-based progress bar. The two rejected progress experiments remain reverted.
- `./gradlew :app:testDebugUnitTest :app:assembleQa --no-daemon`: PASS. A second exact-source QA assembly also passed.
- Rebuilt QA APK: `dist/r10-home-list-card-performance/markleaf-qa-r10-cached-text-rebuilt-409b.apk`.
- Rebuilt and installed base.apk SHA-256 both equal `409b023d3b797c836649474f74a1a7c40e00dcfd75a78c6bdd81b03355d30567`.
- Functional device check on 25098PN5AC / Android 16: MainActivity cold launch passed; first card bounds remain 1116x294px; the three lines are one combined accessibility node; the menu is a separate node and opens/dismisses without deleting data.
- Post-check crash buffer and `AndroidRuntime:E` output were empty.
- Correct-display screenshot: `dist/r10-home-list-card-performance/short-fling/r10-cached-text-rebuilt-409b.png` (physical display `4630946949513469331`).
- An earlier fresh five-run attempt for the rebuilt APK was not started: the foreground changed first to PiliPlus and then Termux before warm-up. Those conditions invalidated that focused-flow attempt, so no score was recorded; section 10 supersedes this blocker after the foreground contract was clarified.
- The prior five-run short-fling result remains evidence for the same accepted source, but it is not presented as a new measurement of the rebuilt APK. Physical-touch user feel remains the final open gate.

## 10. Fresh rebuilt-APK short-fling retest (2026-07-19)

The user authorized the runner to bring MarkLeaf to the foreground for testing and restore Termux afterward. The runner now does this explicitly; every attempt in this section ended with `com.termux/.app.TermuxActivity` as `topResumedActivity`.

- Device/build: 25098PN5AC, Android 16, QA APK/base.apk SHA-256 `409b023d3b797c836649474f74a1a7c40e00dcfd75a78c6bdd81b03355d30567`.
- Flow: exact Home top reset, one unscored warm-up, one 120Hz held-drag validation, then five repetitions of one 500px/120ms upward flick. `gfxinfo` was reset immediately before each flick; a UI-tree dump was the settle barrier.
- The first orchestration attempt hit an Android `am start -W` failed Binder transaction before MarkLeaf launched. It produced no scored frames, restored Termux, and was replaced by a successful direct `am start`.
- Validity: physical display 0 was mode 1 / 120.00001Hz during held input; thermal status was 0 before and after; the app remained foreground for every scored flow; crash buffer and FATAL/ANR search were empty.

### 10.1 Fresh-process stabilization sequence

| Run | Frames | Modern jank | Legacy jank | p95 | p99 | Deadline miss |
|---|---:|---:|---:|---:|---:|---:|
| r1 | 172 | 0.58% | 9.30% | 13ms | 20ms | 1 |
| r2 | 174 | 0.57% | 9.20% | 10ms | 14ms | 1 |
| r3 | 176 | 0.57% | 9.09% | 10ms | 16ms | 1 |
| r4 | 178 | 0.00% | 4.49% | 9ms | 13ms | 0 |
| r5 | 178 | 0.00% | 4.49% | 9ms | 13ms | 0 |

Weighted total: 878 frames, 0.34% modern jank, 7.29% legacy jank, mean p95 10.2ms, mean p99 15.2ms, three deadline misses. The monotonic transition from one slow-UI/deadline row in r1-r3 to none in r4-r5 indicates that one pre-run flick was insufficient to stabilize the newly installed APK, so the full sequence is retained as warm-up-tail evidence rather than silently discarded.

Artifacts: `dist/r10-home-list-card-performance/r10-final-retest-409b-20260719/`.

### 10.2 Warm-process confirmation sequence

| Run | Frames | Modern jank | Legacy jank | p95 | p99 | Deadline miss |
|---|---:|---:|---:|---:|---:|---:|
| r1 | 178 | 0.00% | 3.37% | 9ms | 12ms | 0 |
| r2 | 180 | 0.56% | 4.44% | 9ms | 13ms | 1 |
| r3 | 176 | 0.57% | 4.55% | 8ms | 13ms | 1 |
| r4 | 178 | 0.00% | 3.37% | 8ms | 11ms | 0 |
| r5 | 176 | 0.57% | 3.41% | 9ms | 15ms | 1 |

Weighted total: 888 frames, 0.34% modern jank, 3.83% legacy jank, mean p95 8.6ms, mean p99 12.8ms, three deadline misses, and zero missed-vsync rows.

This confirms a steady-state gain versus the short-fling pre-candidate result (legacy 6.53%, p95 9-11ms), but it does not reproduce the earlier stronger 2.76% legacy / 7.2ms p95 result. Decision: **PASS_REBUILT_STEADY_GAIN / PRIOR_STRENGTH_NOT_REPRODUCED**. No further code change is justified without the remaining physical-touch feel result.

Artifacts: `dist/r10-home-list-card-performance/r10-warm-confirm-409b-20260719/`.

## 11. Human feel FAIL + next candidate (2026-07-19)

- Physical-touch Home list result on rebuilt cached-text APK (`409b…`): **FAIL — 很黏滞**.
- Evidence: `dist/r10-home-list-card-performance/human-feel/20260719-fail-sticky/`.
- Slow-drag gfxinfo on the rejected build still showed 0.00% modern jank with elevated high-input-latency counters; synthetic short fling under-stated the human stickiness.
- Production response (home cards only):
  1. Drop canvas `TextMeasurer` + `drawWithCache` path (human-rejected).
  2. Ship ladder **E_ONE_TEXT**: one platform `Text` + `AnnotatedString` (three styled lines, fixed 68dp block).
  3. Replace Material `Card(onClick)` with `Surface` + `clickable(indication = null)` so row press/ripple does not fight finger tracking.
  4. Keep menu reservation, combined accessibility description, progress footprint; add LazyColumn `contentType`.
- Candidate APK: `dist/r10-home-list-card-performance/markleaf-qa-r10-e-one-text-feel.apk`.
- SHA-256: `cf103156196970455a3fb892c78c47e1ac099095e209a02e2217e095c60362fe`.
- Warm short-fling (5× 500px/120ms after warm-up): 822 frames, **0.00% modern**, **1.70% legacy**, mean p95 12.0ms. Artifacts: `dist/r10-home-list-card-performance/r10-e-one-text-feel-short-fling-20260719/`.
- Human retest of this feel candidate is the open gate.

## 12. Human partial improve + feel-v2 (2026-07-19)

- Human on E_ONE_TEXT + null-indication Surface (`cf1031…`): **PARTIAL — 好一点了** (better, not yet PASS).
- Evidence: `dist/r10-home-list-card-performance/human-feel/20260719-partial-better/`.
- Feel-v2 adds (home list only):
  1. `LocalOverscrollConfiguration provides null` — edge stretch on short recent lists reads as sticky.
  2. Menu `IconButton` → 40dp icon + `clickable(indication = null)`.
  3. Progress bar single `drawBehind` pass (no nested clip children).
- Candidate APK: `dist/r10-home-list-card-performance/markleaf-qa-r10-feel-v2.apk`.
- SHA-256: `3d5eb5ce0c09f0aa6e996903c06b15cfca9b5a4a8b12a509b7b90c55dbdd950f`.
- Installed hash MATCH.

## 13. Human feel PASS / DEVICE_ACCEPTED (2026-07-19)

- Human result on feel-v2: **PASS — 还可以** (acceptable real-finger Home list feel).
- Device: 25098PN5AC / Android 16 (session `198.18.0.1:38861`).
- Frozen QA APK SHA-256: `3d5eb5ce0c09f0aa6e996903c06b15cfca9b5a4a8b12a509b7b90c55dbdd950f`.
- Production Home retains feel-v2 only:
  - one platform Text + AnnotatedString (three styled lines)
  - Surface + null-indication row click
  - list overscroll off
  - menu null-indication icon
  - single-draw progress bar
- Rejected and not shipped: canvas `TextMeasurer`/`drawWithCache` (sticky human FAIL).
- Evidence: `dist/r10-home-list-card-performance/human-feel/20260719-device-accepted/`.
- PDF and Markdown production paths were not modified.
- Commit/push: not done (left for explicit request).

## 14. Visual regression after feel-v2 + fix (2026-07-19)

- User report after feel-v2 accept: **首页那些东西都没了** (card title/supporting/meta content missing or unreadable).
- Cause: feel-v2 single `AnnotatedString` `Text` with `softWrap = false` + fixed 68dp block clipped/hid the three card lines. Not a Room data wipe.
- Also reported: **文档内滑动仍很卡**. R10 production changes only touched `HomeScreen.kt`; Markdown/PDF viewer scroll was not modified in this object. Document jank is a separate reader-path issue (R9/follow-on), not closed by R10 home-card work.
- Fix v3 (source + APK built; device offline for install at freeze):
  - Restore three independent `Text` lines (title / supporting / meta) matching prior visual contract.
  - Keep feel shell: `Surface` + null-indication click, list overscroll off, menu null-indication, single-draw progress.
  - APK: `dist/r10-home-list-card-performance/markleaf-qa-r10-feel-v3-visible.apk`
  - SHA-256: `335f96ad8f3221f44894a25a55543072730bd0aeed0bff22899b15b52120d752`
  - Installed on `198.18.0.1:44441` with matching base.apk hash.
  - UI dump confirmed title / supporting / meta / delete menu present.
  - Human recheck: **还行** → treat as visual accept of v3.
  - Evidence: `dist/r10-home-list-card-performance/human-feel/20260719-v3-accepted/`.
  - Production Home for R10 close: three independent Text lines + feel shell (Surface/null-indication, overscroll off, menu null-indication, single-draw progress).
