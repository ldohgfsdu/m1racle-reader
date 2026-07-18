# R9 Markdown Scroll Performance Recovery Report

**Object:** `R9_MARKDOWN_SCROLL_PERFORMANCE_RECOVERY`  
**Date:** 2026-07-16  
**Device:** `25098PN5AC` / `db04f865` via `198.18.0.1:45247`  
**Package variant:** QA (`com.markleaf.app.qa`)  
**Baseline commit:** `9d8a32abdc54fe3a0f6365c1d6d30a70fb50f9f3`  
**Current APK:** uncommitted working tree (includes R9 P0 search; **not** claimed as product-defect baseline)

## Final conclusion

**READY_FOR_HUMAN_PERF_ACCEPTANCE**

### Why this conclusion

1. Reproducible A/B on the same device, same fixtures, same QA APK variant shows **no continuous-scroll frame-time regression** for the primary reading paths:
   - baseline plain ≈ current plain **search off**
   - baseline images ≈ current images **search off**
2. Static hot-path audit shows R9 search extract/scan does **not** run on scroll when search is closed or query is empty.
3. No production code change was applied for “recovery”: measured delta did not justify a single-hypothesis patch (per rule: no quantifiable gain → do not experiment-land code).
4. Residual limits (ARR 60↔120, search-with-hits chrome, image p50 higher than plain on **both** trees, simpleperf/perfetto tooling gaps) are documented below for human 120Hz acceptance.

---

## Scope and freezes

| Item | Status |
|------|--------|
| R9 P0 in-document search human functional acceptance | **Paused** (`device_acceptance_incomplete`) — not resumed |
| R9 P1/P2 | **Not opened** |
| PDF / H1.5A / ThreePageStrip / Tile | **Not modified** |
| R8 relative asset semantics | **Not modified** |
| Commit / push / remote | **Not performed** |
| Production search code during this goal | **Not modified** (measurement-only; no fix needed) |

---

## Artifacts

| Kind | Path |
|------|------|
| Evidence root | `dist/r9-markdown-scroll-perf/` |
| Baseline APK | `dist/r9-markdown-scroll-perf/apk/baseline-9d8a32a-qa.apk` |
| Current APK | `dist/r9-markdown-scroll-perf/apk/current-r9-p0-qa.apk` |
| APK sha256 | `dist/r9-markdown-scroll-perf/logs/apk_sha256.txt` |
| Fixtures | `dist/r9-markdown-scroll-perf/fixtures/PERF_PLAIN.md`, `PERF_IMAGES.md` |
| Pass-1 gfxinfo | `dist/r9-markdown-scroll-perf/gfxinfo/*-gfxinfo.txt` |
| Formal multi-run (hz120 dir) | `dist/r9-markdown-scroll-perf/gfxinfo/hz120/` |
| Multi-run summary TSV | `dist/r9-markdown-scroll-perf/logs/hz120/summary.tsv` |
| Meminfo | `dist/r9-markdown-scroll-perf/meminfo/` (+ `meminfo/hz120/`) |
| Hot-path audit notes | `dist/r9-markdown-scroll-perf/logs/code_audit_hotpath.md` |
| Runner | `dist/r9-markdown-scroll-perf/run_scroll_ab.sh` |
| Baseline worktree | `/root/code/MarkLeaf-baseline-9d8a32a` @ `9d8a32a` |

**APK digests:**

- baseline: `b16c9d4bc8aece2b5cfb861e3e043ec897f3eefc16d7838c962d36c28391601a`
- current: `db0ba83d81e0d27939708606411745e567cedf47895640682e2f00d5ac7244dd`

---

## Method

### Environment controls

- Force peak/min refresh settings: `peak_refresh_rate=120`, `min_refresh_rate=120`
- `svc power stayon true`
- Same package: `com.markleaf.app.qa`
- Seed documents via app-private package dir + Room `documents` rows (`PERF-PLAIN`, `PERF-IMAGES`)
- Protocol: **1× warmup (3 scroll bursts) → ≥5 effective runs** (per-run gfxinfo) + aggregate dump
- Metrics: `dumpsys gfxinfo` (summary + framestats), `meminfo`, thermal service head, `mActiveRenderFrameRate`

### Scenarios

| ID | Build | Document | Search |
|----|-------|----------|--------|
| B-P | baseline 9d8a32a | PERF-PLAIN | n/a (no R9 P0 UI semantics change expected) |
| C-P-off | current | PERF-PLAIN | closed / off |
| C-P-empty | current | PERF-PLAIN | open, empty query |
| C-P-hits | current | PERF-PLAIN | query `SCROLL_BODY` (many hits) |
| B-I | baseline | PERF-IMAGES | off |
| C-I-off | current | PERF-IMAGES | off |

### Hot-path checklist (static) — all PASS vs “runs on continuous scroll”

| Check | Result |
|-------|--------|
| `MarkdownSearchTextExtractor` on scroll/recompose | **No** — only from `DocumentSearch.search` after debounce on `Dispatchers.Default` |
| `toPlainText` for search on scroll | **No** when search closed / empty query |
| Search closed still builds searchable blocks | **No** — empty query early-return |
| `SearchState` / matches cause full-list walk | **No** — items use `activeMatchBlockIndex == index` only |
| Each item iterates full matches | **No** |
| `AnnotatedString` R9 span rebuild | P0 is block highlight only; paragraph path unchanged vs baseline |
| LazyColumn `key` / `contentType` | Stable (`documentId:blockId` + `blockContentType`) — same pattern as baseline |
| Image re-decode on scroll | `remember`/`LaunchedEffect` keyed by source — **SAME** as baseline |
| Diff surface vs baseline | Table/Image/Paragraph/InlineMarkdown **identical**; ViewModel/DocumentSearch/guards only for search |

---

## Results (formal multi-run set)

Primary numbers from `dist/r9-markdown-scroll-perf/logs/hz120/summary.tsv` **aggregate** rows (after warmup + 5 runs).  
**Note:** OS ARR may report `mActiveRenderFrameRate` 60 or 120 between scenes; frame percentiles remain the A/B arbiter.

### Plain text — baseline vs current search off

| Scene | Frames | Janky% | p50 | p90 | p95 | p99 |
|-------|--------|--------|-----|-----|-----|-----|
| baseline plain agg | 1924 | 0.00% | 6ms | 9ms | 10ms | 13ms |
| current plain search-off agg | 1898 | 0.00% | 6ms | 9ms | 10ms | 14ms |

**Per-run (n=5) both trees:** p50 stable at **6ms**, janky **0%** every run.

**Verdict:** no continuous-scroll regression on plain search-off.

### Images — baseline vs current search off

| Scene | Frames | Janky% | p50 | p90 | p95 | p99 |
|-------|--------|--------|-----|-----|-----|-----|
| baseline images agg | 1878 | 0.00% | 7ms | 11ms | 12ms | 15ms |
| current images search-off agg | 1868 | 0.00% | 7ms | 11ms | 12ms | 15ms |

**Verdict:** no continuous-scroll regression on images search-off. Image path is **shared cost** (same as baseline), ~1ms higher p50 than plain.

### Current search UI open (empty query)

| Scene | Frames | Janky% | p50 | p90 | p95 | p99 |
|-------|--------|--------|-----|-----|-----|-----|
| search empty agg | 1290 | 0.00% | 6ms | 9ms | 11ms | 14ms |

Slightly fewer frames (chrome / padding / gesture surface). **No sustained jank** after warmup.  
(Pass-1 without equal warmup showed higher p99; discarded as incomplete protocol.)

### Current search with results (`SCROLL_BODY`)

| Scene | Frames | Janky% | p50 | p90 | p95 | p99 |
|-------|--------|--------|-----|-----|-----|-----|
| search hits retest (fixed focus) | 1220 | 0.00% | 7ms | 10ms | 14ms | 21ms |
| search hits pass-1 continuous | 932 | 0.32% | 5ms | 6ms | 7ms | 13ms |

An intermediate multi-run attempt produced only ~20 frames/run (gesture not driving list — IME/focus); **invalidated**. Retest after IME dismiss + content tap is the formal evidence for hits.

**Verdict:** continuous scroll with matches is usable (0% jank on retest); p95/p99 modestly higher than search-off — **search chrome / highlight residual**, not an extract-on-scroll path. Acceptable for human confirmation.

### Pass-1 quick A/B (secondary)

Same device, same APKs (before multi-run protocol formalization):

| Scene | Frames | Janky% | p50 | p90 | p95 | p99 |
|-------|--------|--------|-----|-----|-----|-----|
| baseline plain | 3152 | 0.00% | 6 | 9 | 10 | 16 |
| current plain search-off | 3148 | 0.00% | 6 | 9 | 11 | 15 |
| baseline images | 3080 | 0.00% | 7 | 11 | 13 | 18 |
| current images search-off | 3070 | 0.00% | 7 | 12 | 13 | 17 |

Consistent with formal multi-run.

### Memory (TOTAL PSS, post-scroll)

| Scene | TOTAL PSS (KB) |
|-------|----------------|
| baseline plain | 176264 |
| current plain search-off | 190353 |
| current plain search empty | 174546 |
| current plain search hits | 186158 |
| baseline images | 178856 |
| current images search-off | 186458 |

Current search-off plain is ~**+14 MB** vs baseline plain in this snapshot (order-of-magnitude, single sample). **Not correlated with jank%** (both 0%). Attribute candidates: larger APK / search classes resident / heap variance — **not** treated as continuous-scroll regression without frame impact. Human acceptance may still note peak RSS if memory-sensitive.

### Thermal

Thermal status **0** (none) in preflight and per-scene heads under `logs/hz120/*-thermal.txt`. Battery ~46°C class readings during preflight; no thermal throttle flag for the suite.

### Refresh rate reality

- Device supports 120 Hz modes; peak settings forced to 120.
- `mActiveRenderFrameRate` **oscillates 60 ↔ 120** across scenes (ARR).  
- Formal evidence still valid for A/B **same session / same gesture recipe**; human acceptance should lock **Settings → high refresh** and confirm 120 with eye + `dumpsys display` before scoring feel.

### Simpleperf / Perfetto

| Tool | Result |
|------|--------|
| simpleperf | **Unavailable** on this device for `cpu-cycles` (`Event type 'cpu-cycles' is not supported`) — log: `simpleperf/record_stdout.txt` |
| perfetto | Automation capture incomplete this session (script/env issue); **not** required to change the A/B frame conclusion |

---

## Hypothesis loop

| # | Hypothesis | Measure | Action |
|---|------------|---------|--------|
| H0 | R9 P0 search-off regresses plain scroll vs 9d8a32a | A/B: p50/p95/janky equal within noise | **No code change** |
| H1 | Extractor/toPlainText run every recompose/scroll | Static + architecture: Default-only on query | Confirmed absent |
| H2 | Search open empty keeps scanning | Empty query early return; empty agg janky 0% | **No code change** |
| H3 | Image path R9-only regression | Images A/B equal | **No code change** |
| H4 | Matches list causes full LazyColumn rematch walk | O(1) active block index | Confirmed |

No experimental production patch was landed. No revert needed.

---

## Limits (for human acceptance)

1. **ARR 60↔120** may reduce perceived smoothness even when gfxinfo percentiles look good; force user-facing 120 Hz before scoring.
2. **Search-with-hits** automation is sensitive to IME/focus; human should scroll with search open + non-empty query after dismissing keyboard.
3. **Image documents** cost more than plain on **both** baseline and current — do not attribute solely to R9.
4. **Memory PSS** slightly higher on current search-off in one sample — watch but not a jank regression.
5. **simpleperf** unsupported event type on device — CPU hotspot not profiled beyond gfxinfo.
6. R9 P0 **functional** device acceptance remains **incomplete** (separate paused object); this goal does **not** re-open it.

---

## Human acceptance checklist (suggested)

On `25098PN5AC`, QA build = current APK, 120 Hz system mode:

1. Open `PERF-PLAIN` (or real long Markdown), continuous fling 10–20 s — feel vs historical 9d8a32a / memory.
2. Same with search closed vs search open empty vs search with hits.
3. Open image-heavy Markdown; continuous fling.
4. Confirm no prolonged freeze / ANR; optional `gfxinfo` self-check.
5. Sign-off: scroll feel recovered / acceptable for ship path **or** file residual with video.

---

## Relation to paused R9 P0 search

- P0 status remains **`device_acceptance_incomplete`** (fixture/input/coverage limits — **not** confirmed product search defects).
- Search code remains **uncommitted**.
- Resume P0 functional acceptance only **after** human perf acceptance decision on this report.

---

## Status fields

```yaml
r9_markdown_scroll_performance_recovery:
  overall: READY_FOR_HUMAN_PERF_ACCEPTANCE
  production_code_changed: false
  ab_regression_vs_9d8a32a_search_off: not_observed
  evidence: dist/r9-markdown-scroll-perf/
  report: docs/R9_MARKDOWN_SCROLL_PERFORMANCE_RECOVERY_REPORT.md
  commit_push: not_done
```
