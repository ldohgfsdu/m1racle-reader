# R7 H1 Horizontal Zoom-Pan Physics Handoff

## Baseline ownership failure

`PdfHorizontalPagedArea` locally owned scale/offset. Pager was disabled above 1.01x, but the
page transform detector used per-frame `scale * calculateZoom`, all-pointer pan, hard clamps,
no pointer-set rebase, no resistance and no release physics. A second pointerInput observed the
same stream for taps. New Pager pages could retain a detector that exited while VM page state
was stale. Per-page transforms were reset on every page change.

## New state machine

- At <=1.01x Pager owns horizontal drag; above it inner pan owns and consumes it.
- Stable two-pointer pair uses baseline span/focal math. Every pointer-set change is a
  rebase-only frame and clears edge-handoff candidacy without changing transform.
- In-range pan is 1:1. Only overflow uses `limit*(1-exp(-abs(x)/limit))`, limit 56dp.
- Clear horizontal outward intent must accumulate 32dp at an edge before one explicit page
  navigation. No `dispatchRawDelta` or residual velocity injection is used.
- Release performs an official no-bounce medium-stiffness spring to the legal boundary.
  Internal fling is intentionally omitted for this first device-tuning build to prevent the
  reported small-release long flight; velocity policy remains clamped in pure logic.
- Per-page scale/offset is retained in a document-scoped map during the horizontal session.
- Active-page pointer handlers are keyed by the latest state page, preventing stale composed
  pages from permanently losing gesture handling.

Device validation is required for resistance feel, threshold, spring feel, exact page-image
bounds and 2->1->2 continuity. This handoff does not claim subjective acceptance.

Directed device instrumentation passed 2/2 shared corridor/edge-threshold logic tests. True
nested-scroll Pager takeover, fitted bitmap bounds and optional short decay remain open tuning
items; therefore `R7_H1_HORIZONTAL_ZOOM_PAN_PHYSICS` remains `implementation_in_progress` and
is not device accepted.
