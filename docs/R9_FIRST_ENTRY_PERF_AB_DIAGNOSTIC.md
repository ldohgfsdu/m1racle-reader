# R9 First-Entry Markdown Scroll — QA A/B Diagnostic

**Date:** 2026-07-18  
**Device:** 25098PN5AC / `198.18.0.1:36611` / Android 16  
**Package:** `com.markleaf.app.qa`  
**Path:** real `MainActivity` → home card `Large Search Document` → cold first scroll  
**APK:** `dist/r9-markdown-scroll-chrome/first-entry-ab/20260718-083808/markleaf-qa-reader-perf-experiments.apk`  
**SHA-256:** `e4b8f8dc945a6e173dcccf68bead89b9f02c8161ec2384d0c6593e73d852350a`

## Goal

Isolate first-entry LazyColumn cost after human rejection of chrome candidate C:

1. top-bar hide/show flip  
2. lazy prefetch  
3. block semantics tree updates  

Production defaults remain unchanged. Switches exist only when
`BuildConfig.READER_PERFORMANCE_EXPERIMENTS_ENABLED` (qa build) and Intent extras are set.

## Switches

| Flag | Intent extra | Effect |
|------|--------------|--------|
| disable top-bar flip | `markleaf.qa.disable_top_bar` | null scroll-hide state + no nestedScroll; bar stays fixed visible |
| disable prefetch | `markleaf.qa.disable_prefetch` | no-op `LazyListPrefetchStrategy` |
| clear block semantics | `markleaf.qa.clear_block_semantics` | `Modifier.clearAndSetSemantics {}` on each Markdown block |

Logcat tag: `ReaderPerfExperiment` (verified on every run).

## Protocol A — single pass (mild warm UI dumps)

Artifacts: `dist/r9-markdown-scroll-chrome/first-entry-ab/20260718-083808/`

| variant | frames | modern janky | p95 | p99 | deadline |
|---------|-------:|-------------:|----:|----:|---------:|
| baseline | 534 | 0.56% | 22 | **53** | 3 |
| disable_top_bar | 538 | 0.19% | 21 | 29 | 1 |
| disable_prefetch | 512 | 0.20% | 22 | 36 | 1 |
| clear_semantics | 572 | 0.17% | 20 | 30 | 1 |
| all_three | 502 | 0.40% | 21 | 31 | 2 |

## Protocol B — tight 3× (minimal open settle; fixed swipe after one dump)

Artifacts: `dist/r9-markdown-scroll-chrome/first-entry-ab/20260718-084025-tight-r3/`

| variant | n | mean p95 | mean p99 | mean deadline | mean modern janky% | mean legacy janky% | min/max p99 |
|---------|--:|---------:|---------:|--------------:|-------------------:|-------------------:|------------|
| baseline | 3 | 22.0 | 33.0 | 1.3 | 0.27 | **31.0** | 29–36 |
| disable_top_bar | 3 | **20.3** | **31.0** | 1.3 | 0.28 | 31.3 | 25–34 |
| disable_prefetch | 3 | 24.0 | 31.7 | 1.7 | 0.40 | **47.2** | 27–36 |
| clear_semantics | 3 | 23.0 | 36.7 | 1.0 | 0.22 | 32.0 | 32–44 |
| all_three | 3 | 23.0 | 31.7 | 1.7 | 0.40 | **48.3** | 31–32 |

## Conclusions

1. **Flags work.** Every variant logged the expected `ReaderPerfExperiment` line.
2. **None of the three isolations removes first-entry jank as a dominant root cause.**
   Mean modern janky stays ~0.2–0.5%; mean p99 stays ~31–37 ms under protocol B.
3. **Disabling prefetch is net-negative.** Legacy janky rises baseline 31% → prefetch-off 47%
   / all_three 48%. Prefetch moves work off the gesture path; killing it dumps more work onto scroll frames.
4. **Clearing block semantics does not improve the tail.** Mean p99 is worse than baseline (36.7 vs 33.0).
5. **Freezing top-bar flip is only a small win** after candidate C (snap overlay). Mean p95 22→20.3,
   p99 33→31 — inside noise for n=3; not enough for a production change by itself.
6. **Human-reject severity is not fully reproduced** by this automation (human: modern janky ~2.5%,
   p99 ~113 ms). Remaining first-entry cost is likely **Markdown item compose/measure/layout**
   (and possibly document-specific content), not chrome flip / prefetch / a11y alone.

## Production guidance (do not ship from this AB)

| Idea | Ship? | Why |
|------|-------|-----|
| Disable lazy prefetch | **No** | Degrades legacy jank |
| Strip block semantics | **No** | No tail improvement; breaks a11y |
| Always-fixed top bar | **No as sole fix** | Small automatic gain only; product wants hide-on-scroll |
| Keep chrome C (snap overlay) | **Yes (already)** | Separate proven isolation vs animated bar |

## Next diagnostic / fix direction

1. Profile **per-item compose/measure** of Markdown blocks on first scroll (block type breakdown:
   table / code / paragraph / image).
2. Consider production mitigations that reduce item work, not kill system features:
   - cheaper first-layout for heavy blocks (table cache already exists; extend pattern)
   - defer non-critical decoration until settled
   - contentType / reuse audit for expensive branches
3. Optional: human feel retest with protocol B APK (no experiment extras) still required;
   automation does not close the human gate.

## Constraints respected

- No production behavior change when extras absent
- No Home / PDF changes
- No commit / push
