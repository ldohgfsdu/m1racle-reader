# R9 Markdown P1 Frame Pacing Recovery Report (human-reject round 2)

**Object:** `R9_MARKDOWN_P1_FRAME_PACING_RECOVERY`  
**Date:** 2026-07-16  
**Baseline A:** `a7da58c` (P0 search, no char highlight)  
**Candidate B:** current uncommitted P1 character highlight  
**Human status:** **HUMAN_PERF_REJECTED** (user CSV + video)  
**Technical status:** **PERFORMANCE_LIMIT_DOCUMENTED**  
**Commit/push:** not performed

---

## 0. User evidence (accepted as reject)

Sources (described by user; filenames):

- `MarkLeaf QA 2026-07-16 10-56-33.csv`
- `15827.mp4`

### CSV protocol (applied)

1. **Exclude first 3 rows** (open/startup): 0 FPS / 12.1 FPS / Max FrameTime 4081ms.
2. **Steady scroll ~10 samples:** avg FPS ~115.1 (111.4–117.9); Max FrameTime **16–26ms** per window; app CPU ~75.4%; JIT pool ~29.9%; RenderThread ~11.1%; GPU ~7.9%; CPU temp avg ~62.4°C max 66.8°C.
3. 120Hz budget **8.33ms** → 16–26ms spans **2–3** refresh periods → explains felt hitch clusters.
4. CSV **JANK=0 is not a pass** (tool definition ≠ FrameTimeline 8.33ms deadline).
5. Video ~61.9fps = **subjective only**.

### Working hypothesis (from CSV)

CPU / main / JIT primary; GPU not first bottleneck. QA JIT may matter — needs variant A/B. Empty path still re-audited.

---

## 1. Empty-path audit (code)

| Check | Result |
|---|---|
| `withSearchHighlights` empty | **Identity** `return this` — no Builder |
| Search closed theme | **No** tertiaryContainer read when `searchRanges` empty |
| `blockHighlightsFromGrouped` when closed | null via empty map; `remember` only |
| active key all items | active key only if `activeMatch.blockIndex == index` |
| next/prev all normal spans | normalKey ignores isActive; active second overlay |
| table/code empty | `hasSearch` gates; identity base AnnotatedString |
| parse on scroll | `remember(text, styles)` — not per-frame reparse |
| Shared with baseline | `MaterialTheme` code bg, LazyColumn bind, markdown layout |

**Conclusion:** Search-closed P1 highlight infrastructure is **zero-cost by construction**. No further empty-path patch kept in this round (nothing left that is P1-only and still hot on closed search).

---

## 2. Build matrix

| Tag | Tree | Variant | applicationId | debuggable | minify | SHA-256 |
|---|---|---|---|---|---|---|
| A_qa | a7da58c | qa | com.markleaf.app.qa | yes | no | `e812907c…` |
| B_qa | P1 current | qa | com.markleaf.app.qa | yes | no | `868227da…` |
| A_bench | a7da58c | benchmark | com.markleaf.app.benchmark | **no** | no | `6e02aba4…` |
| B_bench | P1 current | benchmark | com.markleaf.app.benchmark | **no** | no | `a676ca2c…` |

`profileable android:shell="true"` is in main manifest for all variants.

### Benchmark / release install on device

```
INSTALL_FAILED_USER_RESTRICTED: Install canceled by user
```

Non-debuggable APKs **cannot** be installed via adb on this device without interactive user approval.  
**Proxy for JIT/variant:** `cmd package compile -m speed -f com.markleaf.app.qa` AOT on QA A and B after natural run.

---

## 3. Formal conditions

- Cool-down ≥5 min: thermal **Status 3 → 0** before matrix (`logs/cooldown.txt`)
- peak/min refresh 120; FrameInterval ~8.31ms → **~120.3Hz**
- No charging (`USB powered: false`, level ~57–58)
- Overlay/recording **not** used during formal capture
- Fixture T: `R5_D_large.md` (~200KB) as `PERF_T_PLAIN.md` / Large Search Document
- Flow: open → wait → warmup scroll → 5 formal runs × (10s down + 10s up) for natural QA
- Primary: Perfetto FrameTimeline (`surface_frame_token != 0`)
- Secondary: PROFILEDATA ring + gfxinfo histogram

---

## 4. Results — Fixture T, search **closed**, 120Hz

### 4.1 FrameTimeline (primary)

| Build | surface n | **App Deadline Missed** | deadline_met% | on_time% | Dropped | Late Present | max app-deadline cluster |
|---|---:|---:|---:|---:|---:|---:|---:|
| **A_qa natural** | 2615 | **29** | 98.89 | 98.55 | 90 | 138 | 1 |
| **B_qa natural** | 2672 | **6** | **99.78** | 99.48 | 68 | 23 | 1 |
| A_qa after `compile -m speed` | 2734 | 7 | 99.74 | 99.38 | 87 | 27 | 1 |
| B_qa after `compile -m speed` | 2643 | 10 | 99.62 | 98.86 | 47 | 55 | 2 |

### 4.2 PROFILEDATA ring (end of each formal run, ~119 frames)

All natural QA runs: **over_8.33% = 0**, p95 ≈ 5.6–5.9ms, inferred_hz ≈ 120.3.

### 4.3 gfxinfo histogram (supplemental)

Natural A_qa: ≥9ms share ~9–11% over multi-thousand-frame windows.  
Natural B_qa: often ~1% (better sample).  
Integer-ms bins — not FrameTimeline.

### 4.4 Comparisons required

| Comparison | Outcome |
|---|---|
| **1. QA baseline vs QA P1** | P1 **not worse** — App Deadline Missed **6 vs 29** |
| **2. profileable/benchmark baseline vs P1** | **Blocked** — install restricted |
| **3. QA P1 vs profileable P1** | **Blocked** — install restricted |
| **Proxy: QA natural vs QA speed AOT** | A improves 29→7; B stays ~6–10 (noise) |

### 4.5 Decision tree application

| Rule | Applied result |
|---|---|
| QA & profileable both P1 much worse → code regression | **Not met** (profileable N/A; QA P1 better) |
| QA bad, profileable smooth, baseline same → JIT/variant | **Partially:** A_qa natural elevated vs A_qa speed; B already low |
| Search closed still highlight calls → fix empty path | **Code audit clean** |
| CPU/JIT high but highlight not hot → don’t blind-change P1 | **Followed** — no P1 production change this round |

---

## 5. Interpreting HUMAN_PERF_REJECTED vs A/B

User felt hitch with Max FrameTime 16–26ms and ~115 FPS under **QA monitor CSV** at **elevated CPU temp**.

Automated cool-device FrameTimeline **does not show P1 search-off regression vs a7da58c**. Residual shared cost:

1. Markdown LazyColumn composition / text layout (both A and B)
2. QA debuggable runtime (JIT visible in user CSV ~30%; AOT helps cold A)
3. Thermal (user ~62–67°C vs formal thermal status 0)
4. On-device monitor overlay itself (formal runs closed it)

**Do not** “fix” by disabling animation, image quality, or scroll clamping.

---

## 6. Candidate APK for optional human retest

Still available (same P1 tree, search-off empty-path fixes included):

- `dist/r9-p1-frame-pacing/apk/p1-candidate-qa.apk`  
  SHA-256: `868227da96337135488fba97932b4e57b591a0ff1f75070f0dec4092b837962d`
- Mirror: `…/r9-p1-human-reject2/apk/B_p1-qa.apk` (same hash)

**Retest protocol if human continues:**

1. Cool ≥5 min; no charge; 120Hz; **no** perf float; **no** record  
2. Install A_qa then B_qa separately; same Large Search Document  
3. Search closed; fling 10s down / 10s up  
4. Compare **feel A vs B**, not absolute “perfect 120Hz”

---

## 7. Final status (allowed values only)

```text
PERFORMANCE_LIMIT_DOCUMENTED
```

Meaning:

- P1 search-closed empty path is zero-cost in code.
- FrameTimeline A/B on cool 120Hz device: **no P1 regression** vs `a7da58c` (P1 equal/better).
- Absolute 16–26ms hitch clusters remain possible under heat / QA / monitor and are **not proven P1-only**.
- Non-debuggable profileable install blocked on device → variant A/B incomplete; AOT speed proxy used.
- Human reject stands as product experience under their conditions; agent cannot honestly ship a “fixed 120Hz” claim without fabricating P1-only root cause.

Not used: `READY_FOR_HUMAN_RETEST` (optional retest possible but not required to close agent loop), `BLOCKED` (measurement not fully blocked — QA matrix completed).

---

## 8. Evidence paths

| Path | Content |
|---|---|
| `/tmp/grok-goal-dd7a4fdaddd1/implementer/dist/r9-p1-human-reject2/` | Full package |
| `…/apk/` | A/B qa + bench + A release |
| `…/traces/A_qa_*.pftrace` `B_qa_*.pftrace` `*_speed_*.pftrace` | FrameTimeline |
| `…/logs/jit_speed_compare.json` | AOT vs natural |
| `…/logs/A_qa_summary.json` `B_qa_summary.json` | Natural QA 5-run |
| `…/logs/empty_path_audit_r2.md` | Code audit |
| `…/logs/user_csv_summary.md` | User CSV digest |
| `…/logs/cooldown.txt` | Thermal cool-down |

---

## 9. Status YAML

```yaml
r9_markdown_p1_frame_pacing_recovery:
  human: HUMAN_PERF_REJECTED
  technical: PERFORMANCE_LIMIT_DOCUMENTED
  p1_vs_a7da58c_search_off_frametimeline: no_regression_p1_equal_or_better
  empty_path: code_zero_cost
  benchmark_install: USER_RESTRICTED
  jit_proxy: package_compile_speed
  A_qa_app_deadline: 29
  B_qa_app_deadline: 6
  commit: not_done
  p1_device_acceptance: paused
  p2: paused
```
