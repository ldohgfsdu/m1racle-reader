# APP_WIDE_SCROLL_AND_MAIN_THREAD_STALL_ISOLATION

**Date:** 2026-07-16  
**Scope correction:** User CSV `MarkLeaf QA 2026-07-16 11-50-02.csv` is **home file-list scroll**, not Markdown open / R9 P0 / P1 highlight.  
**Status:** Isolation complete (measurement + call-chain ranking). **No P1 highlight changes. No commit of P1.**

---

## 0. User CSV reclassification

| Sample (user) | Interpretation |
|---|---|
| 1.0 FPS / JANK 4 / BigJANK 3 / **Max FrameTime 3758ms** | Cold / first interaction window (monitor bin), **not** steady Markdown |
| 74.4 FPS / Max 62ms | Home list mid-session |
| 57.1 FPS / BigJANK 1 / **Max 523ms** | Home list residual long bin |

**Conclusion:** Stall is **app-wide / home-list / cold-start class**, not exclusive to Markdown or character highlight.

---

## 1. Code map (home list) — static audit

| Concern | Finding |
|---|---|
| List UI | `HomeScreen` → `RecentDocumentList` → `LazyColumn` + `DocumentCard` |
| Data | `HomeViewModel.uiState` ← `DocumentRepository.observeRecentDocuments()` ← Room `SELECT * ORDER BY lastOpenedAt DESC` |
| Full list re-emit | **Yes** — Room Flow re-emits entire list on any `documents` table change |
| List-time file I/O | **No** — titles from Room fields only (`resolveDocumentCardTitles` + `remember`) |
| H1 at list time | **No re-parse** — uses cached `DocumentEntity.title` |
| Item key | `document.id` |
| contentType | **not set** |
| Top-bar nested scroll | **None** on home |
| Scroll-path residual | `formatMeta` / relative time every recompose (light); not multi-second |

Heavy work (import open, MD parse, H1 extract) is **viewer/import**, not steady home scroll.

---

## 2. Capture matrix

Device: 25098PN5AC, forced 120Hz, no overlay/recording, thermal cooled before home A.  
APK: QA `com.markleaf.app.qa` (uncommitted tree; **P1 code present but scenarios are home/minimal/settings**).  
Primary: Perfetto FrameTimeline + atrace (gfx/view/wm/am/dalvik/binder).  
Evidence: `/tmp/grok-goal-dd7a4fdaddd1/implementer/dist/app-wide-scroll-isolation/`

| ID | Scenario | Trace |
|---|---|---|
| **A** | Home cold start + first continuous scroll (~20s) | `traces/A_home_cold_first.pftrace` |
| **B** | Home warm continuous scroll | `traces/B_home_warm.pftrace` |
| **C** | Markdown warm continuous (Large Search Document) | `traces/C_markdown_warm.pftrace` |
| **D** | Minimal Compose LazyColumn (QA harness, 200 strings) | `traces/D_minimal_list.pftrace` |
| **E** | System Settings list | `traces/E_settings_list.pftrace` |

**Note on formal 5-runs:** Each scenario used one long 20s capture (≥ thousands of frames) as the quantitative window after 5s settle + warmup where specified. Cold A is force-stop → start → 5s → scroll (no warm pass).

**Layer filter:** Metrics below use `layer_name LIKE '%markleaf.app.qa%MainActivity%'` unless noted (Settings has no MarkLeaf layer).

---

## 3. FrameTimeline results (MarkLeaf MainActivity layer)

| Scenario | surface frames (ML) | **App Deadline Missed** | Late Present | max ADM cluster | Notes |
|---|---:|---:|---:|---:|---|
| **A home cold** | 941 | **~92** | high (mixed layers) | **14** | Worst; launch transition |
| **B home warm** | 2387 | **29** | (Buffer Stuffing 329) | **1** | Steady home still imperfect |
| **C Markdown warm** | 2444 | **6** | — | 1 | **Better than home warm** |
| **D minimal LazyColumn** | 2419 | **5** | 7 | ~1 | Best app-owned scroll |
| **E Settings** | n/a | 1 (all layers) | — | — | Dominated by **Buffer Stuffing**, not App Deadline |

### gfxinfo (supplemental)

| Scenario | total frames | p50 | p95 | p99 | deadline_miss | Slow UI |
|---|---:|---:|---:|---:|---:|---:|
| A home cold | 3375 | 8 | 18 | 32 | 11 | 10 |
| B home warm | 6284 | 8 | 13 | 15 | 0 | 0 |
| C Markdown warm | 6524 | 5 | 9 | 11 | 1 | 1 |

**No 500ms / 3758ms bins in gfxinfo histograms** for formal A/B/C captures (max tail ~77–133ms rare on other windows; cold p99 32ms).

---

## 4. Answers to mandatory questions

### 1) What main-thread slice matches the **3758ms** long frame?

**Not observed as a single MarkLeaf business function** in formal cold-home Perfetto.

Closest **system-wide** long slices in the same capture:

| Slice / entity | ~duration |
|---|---:|
| `SchedBoost: com.android.systemui` | ~6682ms |
| `SchedBoost: com.termux` | ~6185ms |
| `com.miui.home/.launcher.Launcher` | ~5106ms |
| Numeric system tokens | ~3831–4612ms |

**MarkLeaf-associated UI slices (cold A), max order:**

| Slice | ~ms |
|---|---:|
| Background young concurrent copying **GC** | **115** |
| `launchingActivity#2` | 72 |
| NativeAlloc / concurrent **GC** | 46–62 |
| system_server **monitor contention** (WMS / MiuiFreeForm / ATM) | 45–59 |
| `Choreographer#doFrame` (longest) | **~44** |
| **JIT** Compose `ChangeList.pushUpdateValue` (Baseline) | **42** |
| `AndroidOwner:measureAndLayout` | 15–31 |
| `Record View#draw` / DrawFrames | 15–29 |

**Interpretation of user 3758ms:**

1. Monitoring tool **windowed sample** during cold start / process attach (1.0 FPS sample) can attribute a multi-second wall-time bin that is **not** one Compose frame.  
2. Or co-timing with **launcher / SchedBoost / system_server** transitions visible in the same Perfetto (layers include `miui.home` Launcher + Termux during cold A).  
3. **Not** home-list file I/O, Room query-on-scroll, or H1 re-parse (code + trace both deny multi-second list IO).

### 2) Does it only happen on the first scroll?

**Severe multi-second / multi-cluster jank is cold-first only.**

| | Cold home A | Warm home B |
|---|---|---|
| App Deadline Missed | ~92 | 29 |
| max ADM cluster | **14** | **1** |
| gfxinfo Slow UI | 10 | 0 |
| gfxinfo deadline_miss | 11 | 0 |

Warm home is still not perfect (29 ADM + Buffer Stuffing), but **orders of magnitude softer** than user 3758ms / cold cluster.

### 3) After warmup, are there still **500ms-class** long frames?

**Not in formal warm captures.**

- Warm home longest doFrame ~**18ms** class in slice ranking; gfxinfo p99 **15ms**.  
- Warm markdown: ADM **6**, p99 **11ms**.  
- User **523ms** sample was not reproduced; likely cold residual, tool binning, or concurrent system work (not steady warm list).

### 4) Do home and Markdown share the same blocking source?

**Partially shared platform costs; not the same severity; not Markdown/P1-unique.**

| Shared | Distinct |
|---|---|
| Concurrent **GC** (tens of ms) | Home warm **worse** ADM than Markdown warm (29 vs 6) |
| **Buffer Stuffing** | Cold home includes **launch + launcher + system_server locks + JIT** |
| Compose `measureAndLayout` / draw | Markdown path has heavier content but **better** warm FT here |
| QA debuggable / JIT baseline | P1 highlight **not** on home path at all |

Home list does **not** call `withSearchHighlights` / Markdown parse. Blaming R9 P1 for home CSV is **incorrect**.

### 5) Do minimal list and system Settings show the same rhythm?

| Surface | App Deadline Missed | Character |
|---|---:|---|
| **D minimal LazyColumn** | **5** | Cleanest MarkLeaf-owned scroll |
| **E Settings** | ~1 (all layers) | **Buffer Stuffing** storm (device/SF), different profile |

So:

- **Pure Compose list** can scroll near-clean → home cost is **above** minimal list (card UI / theme / list density / GC), not “any LazyColumn broken.”  
- **System Settings** also shows pacing issues → **device/SF Buffer Stuffing** is real and confounds human feel under 120Hz.

---

## 5. Ranked stall sources (for next engineering work)

| Priority | Source | Evidence | Scope |
|---|---|---|---|
| P0 | **Cold start / first composition** (JIT + GC + launch + system_server locks) | A ADM 92, cluster 14; launchingActivity; JIT Compose baseline; GC 40–115ms | App-wide QA |
| P1 | **Buffer Stuffing** (SF) | B/C/E jank_type tables | Device + present path |
| P2 | **Home warm residual** ADM 29 vs minimal 5 | Card composition / recompose / GC under scroll | Home UI polish |
| P3 | Markdown warm | Already relatively good (ADM 6) | Do **not** treat as primary for this CSV |
| — | P1 character highlight | Not on home path | **Do not modify** for this bug |

---

## 6. Explicit non-actions (this object)

- No P1 highlight edits  
- No P1 commit  
- No P2 search work  
- No “Markdown rendering is the root cause” claim for the home CSV  

---

## 7. Optional next steps (out of this isolation report)

1. **Home cold-start budget:** delay non-critical work; pre-warm Compose; `package compile -m speed` for QA human tests.  
2. **Home warm:** profile `DocumentCard` recompose (`formatMeta` remember, contentType, reduce theme reads).  
3. **Human retest protocol:** discard first 3–5s after process start; separate cold home vs warm home vs Markdown; no float overlay.  
4. Keep QA-only `MinimalListHarnessActivity` as regression anchor (scenario D).

---

## 8. Status

```yaml
object: APP_WIDE_SCROLL_AND_MAIN_THREAD_STALL_ISOLATION
user_csv: home_list_not_markdown
long_3758ms: not_reproduced_as_markleaf_slice; system_launch_or_tool_window
first_scroll_only: severe_yes_cold
warm_500ms: not_seen_in_formal
home_vs_markdown: home_warm_worse_than_markdown_warm; not_shared_p1_path
minimal_vs_settings: minimal_adm_5; settings_buffer_stuffing
p1_highlight: out_of_scope_no_change
commit: none
```
