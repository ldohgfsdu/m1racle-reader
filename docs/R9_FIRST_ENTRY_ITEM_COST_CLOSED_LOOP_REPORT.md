# R9 First-Entry Markdown Item Cost — Closed Loop

**Date:** 2026-07-18  
**Object:** `R9_COMPOSE_LIST_VS_PDF_SCROLL_ISOLATION`  
**Device:** 25098PN5AC / `198.18.0.1:36611`  
**Package:** `com.markleaf.app.qa` (real `MainActivity`, no experiment Intent extras)  
**Document:** Large Search Document (`PERF_PLAIN` style: heading + paragraph only)

## 1. Block-type attribution

### Fixture population

| type | count | share |
|------|------:|------:|
| heading | 121 | ~50% |
| paragraph | 120 | ~50% |
| table / code / list / image | 0 | 0% |

Host evidence: `dist/r9-markdown-scroll-perf/fixtures/PERF_PLAIN.md` +  
`app/src/test/java/com/markleaf/app/markdown/InlineMarkdownParserPerformanceTest.kt`  
(`plainFixture_paragraphsDominateInlineParseWork`).

### Device first-scroll compose (shipped path)

Logcat tag `MarkdownBlockPerf` after cold open + first scroll (production defaults, QA build):

```
dominant=paragraph inlineParse=29 fastPath=29 tableSolve=0 cacheHit=0
blocks=[heading:15:8.057,paragraph:14:11.236]
```

Artifact:  
`dist/r9-markdown-scroll-chrome/first-entry-item-fix/20260718-085401-post/r1/block-perf.txt`

**Dominant cost:** **paragraph** compose wall time (11.2 ms total vs heading 8.1 ms for a similar compose count).  
No table solves on this fixture — tables are not the first-entry driver here.

All 29 inline parses used the plain-text **fast path** (`fastPath=29`).

## 2. Production-safe fix

### Shipped change

1. **`InlineMarkdownParser.mayContainInlineMarkup` + parse fast path**  
   Plain body text without real markup skips `buildAnnotatedString` and returns `AnnotatedString(text)`.  
   Mid-word `_` (e.g. `SCROLL_BODY`) is not treated as an emphasis opener (CommonMark-aligned).
2. **Mid-word `_` in the full parser** also stays literal when both neighbors are alphanumerics.
3. **`InlineMarkdownText`:** remember resolved style + link-annotation presence (avoid per-compose rescans).
4. **QA attribution only:** `MarkdownPerformance` per-type compose counters + `MarkdownBlockPerf` dump after first user scroll settles when `BuildConfig.READER_PERFORMANCE_EXPERIMENTS_ENABLED` (qa).

### Explicitly **not** shipped

| Kill-switch | Status |
|-------------|--------|
| disable lazy prefetch | not production default |
| strip block semantics | not production default |
| always-fixed top bar as sole fix | not used |

Default production / debug builds keep experiments **off**.

## 3. Automated cold first-scroll retest

Protocol (same as prior tight AB): cold `am start` → open Large Search Document →  
gfxinfo reset → 3× down + 2× up swipes @ 380 ms → gfxinfo.

| | mean p95 | mean p99 | mean deadline | mean modern janky | mean legacy janky |
|--|---------:|---------:|--------------:|------------------:|------------------:|
| **Baseline** (pre-fix, `20260718-084025-tight-r3` baseline×3) | 22.0 | **33.0** | 1.3 | 0.27% | **31.0%** |
| **Post-fix** (`20260718-085401-post` ×3, no extras) | 23.3 | **30.0** | 1.3 | 0.27% | **27.7%** |
| **Δ** | +1.3 | **−3.0** | 0 | 0 | **−3.3 pp** |

Artifacts:

- Baseline: `dist/r9-markdown-scroll-chrome/first-entry-ab/20260718-084025-tight-r3/`
- Post: `dist/r9-markdown-scroll-chrome/first-entry-item-fix/20260718-085401-post/`
- APK: `dist/r9-markdown-scroll-chrome/markleaf-qa-first-entry-item-fix.apk`  
  SHA-256: `7e3df156cba089de515e0b3b1a45ae8b2ca4e1674aee68413a3eca7ff61f19cf`

**Gate:** automated improvement on **p99** and **legacy janky** holds.  
Modern janky % was already low and stayed flat.

## 4. Tests

```
./gradlew :app:testDebugUnitTest --tests com.markleaf.app.markdown.InlineMarkdownParserPerformanceTest
```

PASS — covers shipped `parse` / `mayContainInlineMarkup` / block-compose counters / fixture attribution.

Also green: `TableLayoutCacheTest` (existing performance counters).

## 5. Residual risk / open gate

| Item | Status |
|------|--------|
| Human feel / DEVICE_ACCEPTED | **PASS / DEVICE_ACCEPTED** — 2026-07-19 on frozen QA candidate |
| Commit / push | closure commit allowed; push not done |
| Home list cards (`R10`) | untouched |
| PDF | untouched |
| Remaining item cost | text measure/layout of long paragraphs; further wins need deeper layout work, not kill-switches |

## 6. Conclusion

Closed loop for **first-entry item-cost diagnostic + production-safe fix**:

1. Attributed dominant path → **paragraph** inline parse/compose on Large Search Document.  
2. Shipped plain-text inline parse fast path + minor compose memoization.  
3. Device cold first-scroll shows **lower mean p99 (33→30)** and **lower legacy jank (31%→27.7%)**.  
4. Human feel passed on 2026-07-19; installed APK matched the frozen candidate SHA-256 7e3df156cba089de515e0b3b1a45ae8b2ca4e1674aee68413a3eca7ff61f19cf.
