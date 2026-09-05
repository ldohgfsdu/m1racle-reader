# P0-1 Final Integration Report

Goal: Markdown annotation on the v1.0.0 RC mainline. No UI polish started.

## Finding resolution

Review finding 1 (`itemsForBlock` without production caller): **deleted**.
The UI loader keeps the single `listForDocument` query (sheet + tint share it);
the restore contract is now tested directly against
`RepositoryAnnotationOverlaySource.itemsFor` (same assertions, real API).
Finding 2 (report wording “tap” vs long-press entry) stands corrected in the
review doc; behavior unchanged.

## Integration

- Feature commit `7a3678d` on `feat/p01-markdown-annotation`
  (5 files, +519/−4; explicit paths only).
- Fast-forward merged into `release-1.0.0-canonical`:
  `60f430b` → `a43633a` → `7a3678d`. No merge commit, no conflicts.
- Untouched as required: tag `v1.0.0` still → `a43633a` (ancestor of tip, history
  preserved); `version.properties` 23/1.0.0; identity/migration files absent
  from the diff; no APK built, nothing published.

## Verification (merged line, JDK 17)

- Markdown annotation: 6 new tests green.
- PDF annotation regression: all 10 annotation suites green.
- Full `:app:testQaUnitTest`: **1393 / 0 / 0 / 0** (170 files).
- Device validation: pending (no install performed; no extra 1.0.0 hash minted).

## Note for next step

The RC line tip (`7a3678d`) is now one commit ahead of tag `v1.0.0`. Shipping
this line requires a new version/tag decision (e.g. 1.0.1) — explicitly out of
this task; do not retag `v1.0.0`.
