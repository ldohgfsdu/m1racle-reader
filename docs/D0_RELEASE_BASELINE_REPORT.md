# MarkLeaf D-0 Release Baseline Report

## 0. Baseline status

| Field | Frozen value |
|---|---|
| Release | MarkLeaf `v0.1.0` C-local |
| Release status | C-4 Release Closure **CLOSED** |
| Product source commit | `604d404978b5f09fdb4a9c825259ef6793f59949` |
| Annotated release tag | `v0.1.0-c-local` |
| Tag object | `f5fabe267b1fbf380598ba927c2dfca8887856dd` |
| Tag target | `604d404978b5f09fdb4a9c825259ef6793f59949` |
| Package / version | `com.markleaf.app` / versionCode `22` / versionName `0.1.0` |
| Canonical APK SHA-256 | `4dd9c7ebacec6276fbca0615107ce2885f35c8a70bf81de0be818b39035a3ba4` |
| Canonical APK size | `11,493,231` bytes |
| Device acceptance | C-2.3 **HUMAN_ACCEPTED** |
| D-0 status | **BASELINE_PROTECTED** |

This document is the post-release boundary for MarkLeaf v0.1.0 C-local. Later documentation or development commits do not change the released product source, tag, artifact identity, or acceptance status recorded here.

## 1. Immutable release identity

### 1.1 Source and tag

The released product source is exactly:

```text
604d404978b5f09fdb4a9c825259ef6793f59949
fix: clean SAF output on export cancellation
```

`v0.1.0-c-local` is an annotated tag whose tag object is `f5fabe267b1fbf380598ba927c2dfca8887856dd` and whose peeled target is the source commit above.

The earlier annotated `v0.1.0` Release Candidate tag remains a separate historical object pointing to `5febda3bc0ea2582cdff276457ffec466e072650`. Neither tag may be force-moved, deleted/recreated, or silently repurposed.

### 1.2 Artifact

The immutable baseline artifact is identified by the timestamped path plus digest:

```text
.external_outbox/markleaf-release-v0.1.0-20260824-223117.apk
SHA-256  4dd9c7ebacec6276fbca0615107ce2885f35c8a70bf81de0be818b39035a3ba4
size     11,493,231 bytes
```

At D-0, these delivery copies are byte-identical to the canonical timestamped APK:

- `.external_outbox/markleaf-release-latest.apk`
- `/storage/emulated/0/Download/markleaf-release-latest.apk`

`release-latest.apk` is a delivery alias and, by repository build rules, a later successful `assemble*` may replace it. It must not be treated as the immutable v0.1.0 identity after that point. The timestamped path and SHA-256 above remain authoritative; no future artifact with a different digest may be called the v0.1.0 C-local baseline.

## 2. Automated verification baseline

The following evidence was executed from a detached, clean checkout of the exact release source commit:

| Gate | Frozen result |
|---|---|
| `:app:assembleRelease --rerun-tasks` | **PASS** — 2026-08-24 22:26:31–22:31:19 UTC+8; 289s; 52 tasks executed |
| `:app:testQaUnitTest --rerun-tasks` | **PASS** — 1156 tests; 0 failures; 0 errors; 0 skipped |
| APK identity | **PASS** — package `com.markleaf.app`; versionCode `22`; versionName `0.1.0` |
| APK signature | **PASS** — APK Signature Scheme v2; C-local debug certificate DN `C=US, O=Android, CN=Android Debug` |
| Release dex hygiene | **PASS** — `CHROME_STATE`, `PDF_SYSBARS`, `MARKDOWN_REFRESH` absent |
| Permissions | **PASS** — no INTERNET/user network permission; only AndroidX-generated non-exported receiver internal permission |
| Artifact copies | **PASS** — timestamped / latest / Download copies shared the canonical SHA at D-0 |

No build or test was rerun for D-0. D-0 protects the already-closed C-4 evidence and verifies its on-disk identity without modifying the artifact.

## 3. Device acceptance baseline

C-2.3 evidence commit `7594a30dbe52667e663d54e5840395309fcfe26a` records **HUMAN_ACCEPTED** for a clean build of the same exact source commit `604d404`:

- ZIP mid-cancel: no zero-byte or partial destination remained.
- Annotated-PDF cancel: no zero-byte or partial destination remained.
- Normal ZIP export: completed output remained and passed integrity verification.
- Installed identity: package `com.markleaf.app`, versionCode `22`, versionName `0.1.0`; install/cold launch passed.

The installed C-2.3 APK SHA was `7285123ca78bf531580fe7bfda64c4e0e0d6737be342b2a9fe5c5711fd722e95`. Separate builds are not byte-identical, so this is not the canonical closure APK digest. Device acceptance protects the exact source baseline; it does not falsely claim that the final `4dd9c7...` artifact was separately installed.

## 4. Post-release protection rules

1. Never move, overwrite, delete/recreate, or reuse `v0.1.0-c-local` for another commit.
2. Never amend or rewrite the released source commit. Any fix starts from a new commit and belongs to a later version line.
3. Never overwrite or delete the timestamped baseline APK. Verify it by full SHA-256, not filename alone.
4. Treat `release-latest.apk` as a mutable delivery alias; after any later build, use the timestamped v0.1.0 artifact when the historical release is required.
5. No product change may continue to use the v0.1.0 release identity. A later release must use a new versionCode, versionName, annotated tag, artifact digest, and acceptance record.
6. Backlog entries and roadmap stages are not active objectives. Each code/UI change requires an explicit task and its own root-cause/acceptance scope.
7. A hotfix must create a new commit and new tag. Moving the old tag is prohibited even if the versionName would otherwise remain similar.
8. C-store publication remains a separate release form with its own keystore and documentation gates; C-local closure does not satisfy them.

## 5. D-0 integrity check

At D-0 execution:

- [x] Worktree was clean before documentation work.
- [x] Annotated tag object and peeled target matched the frozen values above.
- [x] Canonical timestamped APK, latest alias, and Download delivery copy existed.
- [x] All three APK copies were 11,493,231 bytes with SHA-256 `4dd9c7...`.
- [x] APK identity and signature remained valid.
- [x] No `assemble*` command ran; APK size, digest, and mtime were unchanged by D-0.
- [x] D-0 changed documentation only.
- [x] Worktree was clean after the D-0 documentation commit.

## 6. Final status

**BASELINE_PROTECTED**

MarkLeaf v0.1.0 C-local remains closed and immutable at source commit `604d404978b5f09fdb4a9c825259ef6793f59949`, annotated tag `v0.1.0-c-local`, and canonical APK SHA-256 `4dd9c7ebacec6276fbca0615107ce2885f35c8a70bf81de0be818b39035a3ba4`.
