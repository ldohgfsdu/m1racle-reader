# R32.5 Production Release Gate Report

**Date:** 2026-08-27
**Status:** **NO-GO — production prerequisites are not present**
**Scope:** Release-gate audit only. No UI, GlassMaterial, Anchor, Annotation, Search, database, or production behavior was modified.

## 1. Executive result

MarkLeaf v1.0.0 cannot be published from the current checkout. The version configuration and freshly rerun QA unit tests pass, and R32.4 remains valid evidence that the R32.3 verification APK clears the Xiaomi native-crash blocker. However, the three prerequisites for an immutable production release are absent:

1. the source tree is not a reviewed, clean production commit;
2. the real production signer is not injected;
3. consequently, no production-signed APK exists to identify or archive.

The `v1.0.0` tag was deliberately **not created**. The existing R32.3 APK was deliberately **not relabeled or archived as production**.

## 2. Gate matrix

| Gate | Evidence | Result |
|---|---|---|
| Git worktree clean | Audit found 52 tracked changes and 70 untracked entries at HEAD `76ebea51a58fcd0fee2b3a2243b57cb192a696eb` | **FAIL** |
| Reviewed production commit | HEAD predates the uncommitted R23–R32.4 implementation/report set | **FAIL** |
| Immutable tag | `refs/tags/v1.0.0` is absent; no tag was created on an incomplete source state | **NOT_CREATED_BY_DESIGN** |
| Production signer injection | All four required `MARKLEAF_RELEASE_*` environment variables are unset | **FAIL** |
| Version identity | `app/version.properties`: `versionName=1.0.0`, `versionCode=23` | **PASS** |
| Fresh QA unit tests | Forced rerun: 1,350 tests, 0 failures, 0 errors, 0 skipped | **PASS** |
| Production release build | Prohibited by missing signer and non-immutable source state | **NOT_RUN** |
| Production APK signer/hash | No production APK exists | **PENDING** |
| Production archive | Cannot archive an artifact, identity, commit, and tag that do not exist | **PENDING** |
| Highlight fixture | Device disconnected before a valid long-press selection could be injected | **NOT_VERIFIED_DEVICE_DISCONNECTED** |

**Final publication decision: NO-GO.**

## 3. Git and tag gate

The audit was performed on branch `master` at:

```text
76ebea51a58fcd0fee2b3a2243b57cb192a696eb
```

At the start of R32.5, `git status --porcelain=v1` contained:

```text
tracked changes:   52
untracked entries: 70
tracked diff:      2,047 insertions, 1,410 deletions
```

The change surface includes application code, tests, Room schemas, build/release configuration, and R23–R32 reports. It cannot be converted into a production commit merely by an unreviewed bulk commit during the final gate.

`v1.0.0` did not exist before the audit and remains absent. This is the correct fail-safe outcome: an immutable release tag must point to the exact reviewed production commit, not to the old HEAD or a dirty working-tree build.

## 4. Signing and version gate

The release build correctly requires all four external inputs:

```text
MARKLEAF_RELEASE_STORE_FILE
MARKLEAF_RELEASE_STORE_PASSWORD
MARKLEAF_RELEASE_KEY_ALIAS
MARKLEAF_RELEASE_KEY_PASSWORD
```

All four were unset. No secret values were requested, printed, written to disk, or added to Git. Keystore patterns remain ignored by `.gitignore`.

The configured public version is correct:

```text
versionName=1.0.0
versionCode=23
```

Because the signer was unavailable, `assembleRelease` was not invoked. A predictable fail-closed configuration error is not a production build and must not be presented as one.

## 5. Automated verification

The current candidate source was freshly exercised with:

```text
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64 \
  ./gradlew :app:testQaUnitTest --rerun-tasks --console=plain
```

Result:

```text
BUILD SUCCESSFUL in 3m 14s
29 actionable tasks: 29 executed
tests=1350 failures=0 errors=0 skipped=0
```

The raw console log is retained outside the repository at `/tmp/markleaf-r32-5-testQaUnitTest-rerun.log`. Gradle XML results are under `app/build/test-results/testQaUnitTest/` and are generated build evidence, not a production archive.

## 6. Artifact identity and archive disposition

The current outbox file is the already accepted **R32.3 verification artifact**:

| Field | Value |
|---|---|
| Path | `.external_outbox/markleaf-release-latest.apk` |
| Size | 11,660,123 bytes |
| SHA-256 | `387c0021be6a68a0156fd23481f41614e0c7583d20390f3b12eae0a1fbbc215f` |
| Signer | Disposable R32.3 build-verification identity |

It is not production-signed. R32.4 verifies this exact hash on the affected Xiaomi Android 16 device, but that evidence does not transform its disposable certificate into a production identity.

No production archive directory or manifest was created. Doing so now would risk confusing the verification APK with the future canonical public binary. When the gate is unblocked, the archive must atomically bind:

- the one exact production APK;
- its SHA-256 and byte size;
- `apksigner verify --verbose --print-certs` output and certificate SHA-256;
- manifest package, `versionName`, and `versionCode`;
- the immutable source commit and annotated `v1.0.0` tag object;
- the fresh automated-test summary;
- `docs/R32_4_GLASS_REACCEPTANCE_REPORT.md` and this gate report.

## 7. Minimal Highlight fixture attempt

The Xiaomi target initially remained online at `198.18.0.1:45043`. The small `C2-heart.pdf` fixture opened successfully, and its Annotation Sheet established the baseline:

```text
当前文档还没有高亮标注。
还没有批注。可从上方高亮添加。
```

An initial generic swipe was interpreted as normal PDF scrolling and did not create a highlight. Before the explicit `DOWN → long-press → MOVE → UP` selection sequence could begin, ADB returned:

```text
adb: device '198.18.0.1:45043' not found
```

Per the device-test protocol, the session was not polled indefinitely and no PASS was claimed. No Highlight fixture, Overlay restoration, Annotation Sheet row, or deletion cleanup was verified. The only observed interaction was a reading-position scroll.

Evidence is retained outside the repository under `/tmp/markleaf-r32-5/highlight/`.

## 8. Required unblock sequence

The production owner must complete these steps in order:

1. Review the complete R23–R32.5 change set, including generated Room v12 schema evidence and release configuration.
2. Create the production source commit and confirm `git status --porcelain` is empty.
3. Inject the real, externally backed-up production/upload signer through the four environment variables without exposing secrets.
4. From the exact clean commit, rerun `:app:testQaUnitTest --rerun-tasks` and `:app:assembleRelease`.
5. Verify package `com.markleaf.app`, version `1.0.0 (23)`, APK Signature Scheme, production certificate digest, permissions, APK hash, and size.
6. Create the annotated immutable `v1.0.0` tag on that exact verified commit; never move or overwrite an existing tag.
7. Archive the canonical APK and all identity/evidence files together, then compare the distributed bytes to the archived SHA-256.
8. If a stable device connection and disposable fixture are available, create, restore, list, and delete one Highlight; otherwise retain the explicit coverage limitation.

Until these steps are complete, v1.0.0 remains **NO-GO**.

## 9. Constraint compliance

- UI changes: none.
- GlassMaterial or Backdrop topology changes: none.
- Anchor protocol changes: none.
- Annotation/Search architecture changes: none.
- Keystore or secret material committed: none.
- Production tag created: no.
- Verification APK misrepresented as production: no.
