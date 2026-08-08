# R7 H1 Horizontal Zoom-Pan Physics Handoff

## Current authority

H1.1A supersedes the earlier centered-page/live-pinch display-offset implementation while keeping
the accepted H1.1 X rubber-band constants and H1.2 deferral intact.

- Active object: `R7_H1_1A_TOP_ALIGN_AND_PINCH_X_BOUND_REBASE`.
- Status: `implementation_complete_device_acceptance_pending`.
- Current implementation, audit, device evidence and artifact details:
  `docs/R7_H1_1A_TOP_ALIGN_AND_PINCH_X_BOUND_REBASE_REPORT.md`.
- P0C1.4 and Android test-data isolation remain `device_accepted`; neither object was modified.

## H1.1 contract

- All horizontal-page pan physics use measured viewport pixels.
- Fitted page width/height come from the real viewport and stable per-page aspect.
- Legal X/Y offset, raw X edge pull and visual X overscroll are separate state.
- Only horizontal residual edge pull uses a scale-independent 28dp saturating rubber band.
- Y is hard-clamped at top/bottom and has no visual/raw overscroll or release spring.
- Release springs visual X overscroll only; legal reading position stays fixed.
- Pointer-set changes are rebase-only frames.
- Horizontal pages are top-aligned with an 8dp content gap and top-center transform origin.
- Live-pinch bounds are recomputed from legal X/Y plus a stable page-content anchor; visual X
  overscroll never enters the new legal offset.
- At or below 1.01x Pager owns horizontal drag. Above 1.01x inner pan owns the entire gesture.
- Zoomed edge Pager handoff is deliberately absent.

## Pending acceptance

Current objective device evidence shows the intended 8dp top gap, exact in-range 2x legal-delta
consumption, a maximum sampled 27.94859dp X display pull, a 179ms X spring and no zoomed-edge page
change. The user must still verify real two-finger 2x/3x live-pinch left-blank continuity,
subjective drag feel and a fresh P0C1.4 hold-drag visual check. Do not mark H1.1A device accepted
without that confirmation.

## Next object (not started)

`R7_H1_2_ZOOMED_EDGE_PAGER_HANDOFF` is deferred. Do not combine it with H1.1 tuning or change
P0C1.4, persistence/Room, Pointer Epoch, tile geometry, cache parameters or R6 legacy code.
