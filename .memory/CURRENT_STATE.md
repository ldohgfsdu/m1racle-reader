# CURRENT_STATE

**Updated:** 2026-07-16  
**Active object:** `R9_MARKDOWN_SEARCH_MATCH_HIGHLIGHT_V1` (`design_audit` / **DESIGN_READY**)  
**P0 search:** `P0_DEVICE_ACCEPTED`（本地 checkpoint 见 git log）  
**Frozen:** R7 H1.5 / H1.5A **rejected / device_failed / frozen**.

## Closed: R9 Markdown in-document search V1 P0

```yaml
r9_markdown_in_document_search_v1:
  overall: P0_DEVICE_ACCEPTED
  implementation: checkpointed
  known_limits:
    - block_level_only
    - same_block_no_visual_distinction
    - full_block_yellow_not_char_highlight
    - no_char_precision_scroll
    - no_cross_block
  reports:
    - docs/R9_MARKDOWN_IN_DOCUMENT_SEARCH_V1_DESIGN_AUDIT.md
    - docs/R9_MARKDOWN_IN_DOCUMENT_SEARCH_V1_P0_IMPLEMENTATION_REPORT.md
    - docs/R9_MARKDOWN_IN_DOCUMENT_SEARCH_V1_P0_REVIEW_REPORT.md
    - docs/R9_MARKDOWN_IN_DOCUMENT_SEARCH_V1_P0_DEVICE_ACCEPTANCE_REPORT.md
```

## Active: R9 Match highlight V1 (P1 design only)

```yaml
r9_markdown_search_match_highlight_v1:
  phase: design_audit_only
  conclusion: DESIGN_READY
  design_report: docs/R9_MARKDOWN_SEARCH_MATCH_HIGHLIGHT_V1_DESIGN_AUDIT.md
  implementation: not_started
  excludes: bring_into_view, text_layout_scroll, cross_block, pdf_search
```

## Perf object

```yaml
r9_markdown_scroll_performance_recovery:
  status: ready_for_human_perf_acceptance
  production_code_changed: false
  evidence_local_only: dist/r9-markdown-scroll-perf/
```

## Closed: R8

```yaml
r8_markdown_relative_asset_v1:
  overall: closed_device_accepted
  checkpoint_commit: 9d8a32abdc54fe3a0f6365c1d6d30a70fb50f9f3
```
