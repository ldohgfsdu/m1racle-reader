
## Status gate (verifier)

| Gate | State |
|---|---|
| Path matrix | **Done** |
| Home/MD warm FT + deep slices | **Done** (`deep_slice_metrics.json`) |
| Chrome isolation MD | **Done** (gain proven; production always-visible bar applied) |
| Ladder A–E | **Done** (jump at E) |
| **PDF continuous UI-verified FT** | **Done** — `ui_pdf_continuous_ok.xml` + `pdf_cont_warm_r1–3` |
| Ladder F | **Done** (real PDF continuous) |
| Production fix MD chrome | Applied uncommitted; device post-fix MD FT optional |
| Home card fix | Not applied (separate hypothesis) |

# R9_COMPOSE_LIST_VS_PDF_SCROLL_ISOLATION

**Date:** 2026-07-16  
**Goal gate:** PDF continuous control **VALID** (UI-verified 上下连续 + `pdf_cont_warm_r1–3`)  
**Dual status (evidence-backed):**

1. **`SHARED_SCROLL_CHROME_ROOT_CAUSE_FOUND`** — **Markdown only**  
2. **`SCREEN_SPECIFIC_ITEM_WORK_FOUND`** — **Home file-list cards** (ladder E)

**PDF continuous production code:** **unchanged**  
**MD chrome production fix:** always-visible bar applied (uncommitted); post-fix MD remeasure still optional  
**Commit / push:** none · P1 acceptance / P2: **paused**  
**Device:** `198.18.0.1:35627` 25098PN5AC online

**Scratch:** `/tmp/grok-goal-7f6d7b8ed971/implementer`

---

## 0. Updated judgment (from goal premise)

| Premise | Verdict |
|---|---|
| Device 120Hz capability normal | **Accepted** (user + prior) |
| System display path first suspect | **Rejected** as primary |
| QA/JIT alone explains jank | **Rejected** (simple ladder A–D smooth in same QA process) |
| P1 highlight is root | **Rejected** (home has no P1; prior MD empty-path A/B) |
| Focus shared Compose list / text / chrome | **Confirmed** with dual split below |

---

## 1. Three production paths

Full matrix: `docs/R9_COMPOSE_LIST_VS_PDF_PATH_MATRIX.md` and scratch `compose_vs_pdf_path_matrix.md`.

| Capability | Home | Markdown | PDF continuous |
|---|---|---|---|
| Compose LazyColumn | yes | yes | yes |
| Variable height item | yes | yes | **no** (aspectRatio) |
| Compose Text body | yes | yes | no |
| AnnotatedString | no | yes | no |
| Top-bar scroll hide | **no** | **yes** | **no** |
| Per-frame scroll→VM | no | settled only | viewport ensure |
| Bitmap pages | no | images only | **yes** |
| stable item size | no | no | **yes** |
| contentType | missing | yes | missing |
| item key | `id` | `docId:blockId` | `docId\|page` |

**Home∩Markdown − PDF:** variable-height Compose text bodies.  
**Markdown-only:** scroll-aware top bar (`ReaderTopBarScroll` + nestedScroll + Animatable).  
**Home-only residual driver:** multi-line card Surface UI (ladder E), not chrome.

---

## 2. Warm FrameTimeline A/B (formal)

Conditions: same QA APK family, 120Hz, cool-ish session, no screen record / perf overlay for formal traces, ~15s.

### 2.1 Home / Markdown / (invalid) PDF

| Path | ADM (r2/r3) | p95 (r2/r3) | >8.33 (r2/r3) | Notes |
|---|---:|---:|---:|---|
| Home | 20 / 18 | 11.7 / 11.7 | 610 / 595 | Fixed top bar; card list |
| Markdown chrome ON | 11 / 4 | 10.0 / 9.7 | 271 / 273 | Production chrome |
| Markdown fixed bar | **3 / 3** | **8.5 / 8.5** | **119 / 110** | Hardcode diagnostic |
| pdf_warm_* | 28 / 22 | 12.1 / 12.1 | 601 / 602 | **INVALID** — UI dump = 最近文件 |

Full table: scratch `frametimeline_ab_summary.md`, raw `logs/full_ft_metrics.json`.

### 2.2 Markdown chrome isolation (priority #1)

| Metric (r2+r3 mean) | Chrome ON | Fixed bar hardcode | Change |
|---|---:|---:|---:|
| ADM | 7.5 | **3.0** | −60% |
| >8.33ms | 272 | **114.5** | −58% |
| p95 | 9.83 | **8.51** | −1.3 ms |
| late_present | 215 | **53.5** | −75% |

UI proof of reader: `logs/ui_md_reader_verify.xml` (`返回` / 搜索 / 目录).  
Hardcode **reverted** after measure (product chrome restored).

**Conclusion:**  
`SHARED_SCROLL_CHROME_ROOT_CAUSE_FOUND` **for Markdown**.  
Home has no scroll-hide chrome → **not** a dual-screen shared chrome root.

### 2.3 PDF as in-app positive control — **VALID**

| Artifact | Value |
|---|---|
| UI | `logs/ui_pdf_continuous_ok.xml` — title `h1-human-acceptance-fixture.pdf`, `PDF page 1/2 of 3`, mode **上下连续** |
| After capture | `ui_pdf_continuous_after.xml` still PDF (not 最近文件) |
| Traces | `traces/pdf_cont_warm_r1.pftrace` … `r3` (+ warmup) |
| Metrics | `logs/pdf_cont_ft_metrics.json` |

| trace | n | p50 | p95 | p99 | >8.33 | >16.67 | max_ADM_cluster | measure>5ms | main doFrame>8.33 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| pdf_cont_warm_r1 | 1486 | 6.53 | **7.25** | 8.01 | **12** | 0 | 0 | **0** | 1 |
| pdf_cont_warm_r2 | 1561 | 6.56 | **7.17** | 7.94 | **11** | 0 | 0 | **0** | 1 |
| pdf_cont_warm_r3 | 1512 | 6.56 | **7.18** | 7.78 | **9** | 0 | 0 | **0** | 0 |

**vs home / Markdown (prior valid warm):**

| path | >8.33 (r2) | p95 | measure>5ms | main doFrame>8.33 |
|---|---:|---:|---:|---:|
| home | 610 | 11.71 | 116 | 115 |
| Markdown | 271 | 9.97 | 58 | 96 |
| **PDF continuous** | **11** | **7.17** | **0** | **1** |

**PDF-lighter call chains (measured, not just subjective):**

1. Body measure: aspect-box / layout max ~2.5ms, **zero** measure>5ms (home 116, MD 58)  
2. Main doFrame rarely exceeds 8.33ms (0–1 vs home 115)  
3. Recomposer animation sum ~60ms vs MD chrome ~3700ms  
4. Prefetch nearly empty during warm continuous scroll  
5. FT p95 **under 8.33ms** — aligns with user “full 120Hz” feel  

Prior invalid `pdf_warm_*` (home dumps) remain archived and must not be cited.

### 2.4 Deep call-chain notes (objective §二 — home/MD/ladder)

Full tables: `{SCRATCH}/frametimeline_ab_summary.md` §2, raw `{SCRATCH}/logs/deep_slice_metrics.json`.

| Path | Main doFrame>8.33 | measure>5ms | recompose c | anim sum_ms | prefetch max_ms | RT DrawFrames>8.33 | GC max_ms |
|---|---:|---:|---:|---:|---:|---:|---:|
| home r2 | 115 | 116 | 3117 | 4142 | 5.57 | **0** | 32 |
| md_warm r2 | 96 | 58 | **13546** | 3724 | 1.61 | **0** | 165* |
| md_fixedON r2 | **32** | **27** | 12891 | **3290** | 1.55 | **0** | 115 |
| ladder A r2 | **3** | **2** | 5448 | 3040 | 1.89 | **0** | 24 |
| ladder E r2 | 32 | **85** | 3809 | 3940 | 2.64 | **0** | 34 |

\* includes co-timed system contention in that run.  
**RenderThread is not the bottleneck** on any valid scenario.  
**Scroll delta:** no counter track; `Scroll:`/`Fling:` wall slices span whole gestures — per-frame cost is doFrame/measure/recompose.

---

## 3. List complexity ladder (QA-only)

Harness: `MinimalListHarnessActivity` (`ladder=A|B|C|D|E`). **Not for product commit.**

| Step | >8.33 (r2) | p95 (r2) | Verdict |
|---|---:|---:|---|
| A fixed String | 4 | 6.2 | Smooth |
| B variable Text | 10 | 6.0 | Smooth |
| C AnnotatedString | 18 | 5.8 | Smooth |
| D heavy multi-para Annotated | 10 | 5.7 | Smooth |
| **E home-like cards** | **182** | **8.8** | **First complexity jump** |

→ **Not** result B (variable text alone) or C (AnnotatedString alone).  
→ **Result D / screen-specific:** home residual = **card item work** (`SCREEN_SPECIFIC_ITEM_WORK_FOUND`).  
→ Simple Compose LazyColumn is **not** inherently capped on this device (rejects pure “all Compose lists are doomed” as sole explanation).

Ladder F = real PDF continuous (blocked on device offline + prior invalid dumps).

---

## 4. LazyColumn / text / shell audit (summary)

### Home
| Check | Finding |
|---|---|
| item key | Stable `id` |
| contentType | Missing (slot reuse weaker) |
| model | Room entities; not rebuilt every frame on scroll |
| scroll→VM | None |
| FS / H1 on scroll | No (titles from Room; format in composition of already-loaded fields) |
| LaunchedEffect per item | Not on scroll hot path for list body |

### Markdown
| Check | Finding |
|---|---|
| item key | `docId:blockId` |
| contentType | Present |
| chrome | nestedScroll + threshold hide + Animatable — **proven cost** |
| scroll→VM | Settled only |
| AnnotatedString | remember/parse path; ladder C/D show spans alone not fatal |
| P1 search off | Prior: empty-path zero-cost; not home root |

### Shared shell
Scaffold / insets differ; **shared jank driver is not one identical chrome component**. Split: MD chrome + home cards.

---

## 5. Home 3758ms / 523ms

| Class | Formal finding |
|---|---|
| 3758ms | **Not** a warm MarkLeaf business slice; prior cold/monitor + system SchedBoost/Launcher co-timing |
| 523ms | **Not** reproduced in warm formal |
| Warm home long work | GC 20–60ms; doFrame 15–22ms |

See scratch `home_long_frame_analysis.md` and `docs/APP_WIDE_SCROLL_AND_MAIN_THREAD_STALL_ISOLATION_REPORT.md`.

---

## 6. Root-cause decision tree (goal §八)

| Result | Outcome |
|---|---|
| A fixed top bar improves MD | **HIT** — MD chrome |
| B variable Text starts jank | **MISS** — ladder B smooth |
| C AnnotatedString starts jank | **MISS** — ladder C smooth |
| D MD + home real items | **HIT home** — ladder E; MD residual after chrome-off is text blocks / remaining Compose cost |
| E all Compose bad, PDF good | **Partial** — simple lists good; PDF FT control invalid this session |
| F only cold bad | **Partial** — multi-second cold only; warm residual remains |

**Assigned dual status (non-contradictory):**

1. **`SHARED_SCROLL_CHROME_ROOT_CAUSE_FOUND`** — Markdown scroll-hide top bar (quantified A/B).  
2. **`SCREEN_SPECIFIC_ITEM_WORK_FOUND`** — Home multi-line card item composition (ladder E).  

**Rejected as sole labels:**  
- `VARIABLE_TEXT_LAYOUT_ROOT_CAUSE_FOUND` alone (A–D smooth)  
- `COMPOSE_LIST_FRAME_PACING_LIMIT_FOUND` alone (simple lists near 120Hz budget)  
- Prior report claim “chrome no gain” — **superseded** by md_fixedON evidence  

---

## 7. Production fix (Markdown chrome — applied, uncommitted)

**Single hypothesis:** scroll-hide nestedScroll top bar is the Markdown chrome cost proven by `md_fixedON` A/B.

| Change | File |
|---|---|
| No nestedScroll chrome connection (`nestedScrollConnection = null`) | `ViewerScreen.kt` |
| at-top `forceShow` only when scroll **settled** (no per-frame offset observation) | `ViewerScreen.kt` |
| Overlay **snap** instead of 190ms `animateTo` | `ViewerScreen.kt` |
| Injected `uptimeMillis` for JVM tests | `ReaderTopBarScroll.kt` |
| Unit tests | `ReaderTopBarScrollStateTest.kt` |

**UX:** reading top bar stays always visible (no auto-hide on scroll). Search / TOC / forceShow paths unchanged.  
**PDF continuous:** unmodified.  
**Home cards:** not fixed this change (ladder E residual).  
**Post-fix device A/B:** **pending** (adb offline). On reconnect: build QA, warm MD FT ≥3 vs pre-fix baseline in this report.  
**Commit/push:** not done.

### Home (next, separate hypothesis)

1. Reduce card composition cost / preformat meta in model  
2. Add `contentType`  
3. Measure ≥3 FT before keep

---

## 8. Constraints honored

| Constraint | Status |
|---|---|
| No commit | Yes |
| No push | Yes |
| No P1 device acceptance | Yes |
| No P2 | Yes |
| No PDF continuous production edit | Yes |
| QA ladder not product | Yes (qa source set only) |

---

## 9. Evidence index

| Artifact | Path |
|---|---|
| This report | `docs/R9_COMPOSE_LIST_VS_PDF_SCROLL_ISOLATION_REPORT.md` |
| Path matrix | `docs/R9_COMPOSE_LIST_VS_PDF_PATH_MATRIX.md` |
| FT summary | `{SCRATCH}/frametimeline_ab_summary.md` |
| Isolation switches | `{SCRATCH}/isolation_switches.md` |
| Ladder | `{SCRATCH}/list_complexity_ladder.md` |
| Home long frames | `{SCRATCH}/home_long_frame_analysis.md` |
| Metrics JSON | `{SCRATCH}/logs/full_ft_metrics.json`, `ladder_ft.json` |
| Traces | `{SCRATCH}/traces/*.pftrace` |
| QA harness | `app/src/qa/java/.../MinimalListHarnessActivity.kt` |

`{SCRATCH}` = `/tmp/grok-goal-7f6d7b8ed971/implementer`

---

## 10. Open items (blocked on device)

1. Verified PDF continuous open + UI dump + ≥3 warm FT (ladder F / positive control).  
2. Optional production chrome optimization A/B (post-fix, ≥3 runs).  
3. Optional home card item simplification A/B.
