# Phase D — Next Development Plan

## 0. Planning status

| Field | Value |
|---|---|
| Date | 2026-08-24 |
| Input baseline | MarkLeaf v0.1.0 C-local — C-4 **CLOSED** / D-0 **BASELINE_PROTECTED** |
| Released product source | `604d404978b5f09fdb4a9c825259ef6793f59949` |
| Released tag | annotated `v0.1.0-c-local` |
| Candidate next version | `v0.1.1` |
| Nature | Post-release planning and boundary definition; no implementation authorization |

This plan classifies possible post-release work. A “candidate” or “next step” below is not a Session Objective and must not be implemented without an explicit user task.

## 1. v0.1.1 target

v0.1.1 is a bounded post-release quality release for the existing local Markdown + PDF reader. Its target is to land only explicitly approved reliability, consistency, release-hygiene, or user-comprehension corrections discovered around v0.1.0.

v0.1.1 is not a feature expansion. It must preserve the local-first, no-account, no-network product boundary and must not rewrite v0.1.0 history.

### Target constraints

- Start product work from the released behavior at source commit `604d404`; later C-3/C-4/D-0 commits are release documentation only.
- Select one bounded, evidence-backed issue per task. Do not bundle unrelated backlog entries.
- Diagnose the real call chain and add root-cause regression coverage before changing behavior.
- Do not bump version metadata during ordinary development. A dedicated v0.1.1 release execution task must assign a versionCode greater than `22`, set versionName `0.1.1`, build, verify, document, and create a new annotated tag.
- Never move or reuse `v0.1.0-c-local`.

## 2. Backlog classification

### A. Candidate v0.1.1 product corrections — explicit approval required

| Item | Classification | Why it may fit v0.1.1 | Required acceptance |
|---|---|---|---|
| B4 P-1 cross-entry ZIP identity | Data-consistency quality correction | Prevent duplicate equivalent library rows across FAB and VIEW/SEND without changing supported formats | Policy tests for both entry paths; existing imports/read paths green; targeted device import verification |
| B4 P-3 SourceTooLarge wording | Bounded user-comprehension correction | Rejection behavior is already correct; only the >200MiB reason is unclear | State/event test plus focused human UI acceptance; no limit or import-policy change |
| B4 P-4 LocalImageLoader `Log.w` gating | Release-hygiene correction | Align four decode warnings with existing compile-time telemetry policy | Unit/host gate plus release dex/log-string inspection; no loader behavior change |

These are independent candidates, not a promised bundle. P-1 changes data identity behavior; P-3 is a UI-visible change; P-4 is build/release hygiene. They require separate objectives and proportionate verification.

### B. Evidence-only follow-up — no production change by default

| Item | Classification | Default action |
|---|---|---|
| B4 P-5 force-stop during active export | Device evidence completeness | Reproduce with a large active export and inspect SAF residue; report facts before proposing code |
| ThumbnailLoader `closeAsync` observation | Long-session runtime observation | Capture a real overlap if it occurs; no speculative synchronization or loader rewrite |
| B-1 uninterrupted long-session evidence | Optional supplementary device evidence | Run only if still needed and a device-exclusive window is available; existing retention result remains valid |
| Original C-2 pinch-zoom boundary | Manual evidence boundary | May be checked with real two-finger input; do not treat missing automation as a product defect |

Evidence-only work does not justify a version bump by itself unless it discovers and then separately authorizes a product correction.

### C. Characterized future polish — not a release defect

| Item | Classification | Boundary |
|---|---|---|
| B4 P-2 small-export cancellation UX | Future UI polish | When cancel arrives after `Completed`, the retained output is complete and valid. Any feedback refinement needs a separate UI task and human acceptance; it does not reopen C-2.3 |

## 3. Not in v0.1.1 scope

The following are excluded unless the PRD and session objective are explicitly changed in a future planning task:

- Markdown editing, rich-text notes, annotations as a new product system, or document mutation.
- Accounts, cloud sync, network-drive integration, INTERNET permission, ads, analytics, community, comments, or sharing services.
- Full-text indexing or cross-document search.
- EPUB implementation or other new document formats/reading modes.
- Complex file management, plugin systems, or AI summarization.
- TOC redesign, broad UI restyling, or unrelated interaction polish.
- PDF renderer rewrite, PDFium migration, gesture-model replacement, or generation-gate redesign.
- Architecture adjustment, compatibility layers, speculative abstraction, dependency upgrades, or dependency additions unrelated to an approved issue.
- Performance optimization without measured evidence and a specific accepted bottleneck.
- C-store publication work, release keystore setup, or store/privacy collateral; those remain a separate release form and plan.
- Any opportunistic fix or refactor outside the active task.

## 4. Baseline protection during Phase D

1. `v0.1.0-c-local` and its target `604d404` are immutable.
2. The v0.1.0 canonical APK is `.external_outbox/markleaf-release-v0.1.0-20260824-223117.apk` with SHA-256 `4dd9c7...`.
3. A future `assemble*` must still follow the mandatory Download delivery rule, but the overwritten `release-latest.apk` must not be mistaken for the v0.1.0 artifact.
4. New work lands in new commits. No amend/rebase may rewrite released history.
5. New product behavior must have one authoritative path; no legacy fallback or dual read/write bridge.
6. No pending item becomes implementation work merely because it appears in this plan.

## 5. v0.1.1 release gate

If and only if an explicit v0.1.1 release task is opened, the release candidate must satisfy:

- [ ] Approved v0.1.1 scope is fully enumerated; no unrelated changes are present.
- [ ] Every behavior fix has root-cause regression coverage and relevant targeted tests.
- [ ] `:app:testQaUnitTest` is green with zero failures/errors/skips unless a separately documented test policy changes.
- [ ] `:app:assembleRelease` succeeds from a clean exact commit and the mandatory Download copy is performed.
- [ ] APK package/version/signature and SHA-256 are recorded; versionCode is greater than `22`; versionName is `0.1.1`.
- [ ] Release hygiene and no-INTERNET gates pass.
- [ ] Device verification covers every changed real-device behavior; evidence-only limitations are reported honestly.
- [ ] Known issues and CHANGELOG are synchronized.
- [ ] A new annotated tag is created; `v0.1.0-c-local` remains unchanged.
- [ ] Repository status is clean after release documentation is committed.

## 6. D-0 outcome

D-0 creates planning boundaries only. No production code, UI, issue fix, build configuration, version metadata, tag, or release artifact is changed.

**NEXT_PHASE_PLANNED — NO_ACTIVE_IMPLEMENTATION_OBJECTIVE**
