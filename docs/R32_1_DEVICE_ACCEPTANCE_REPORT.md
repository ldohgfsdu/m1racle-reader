# R32.1 Release Candidate Device Acceptance Report

**Date:** 2026-08-27
**Result:** **FAIL — PUBLIC RELEASE BLOCKED**
**Scope:** Final RC install and real-device acceptance only. No UI, architecture, database, GlassMaterial, or production code was modified.

## 1. Executive result

The R32 RC APK installed successfully and its on-device package, version, binary hash, and signing certificate exactly matched the supplied artifact. The application then crashed in the RenderThread before Home became visible on every valid launch attempt.

The crash reproduced **5/5 times** and prevents entry into Home, Reader, Annotation, Search, and performance scenarios. Those scenarios are classified `BLOCKED`, not `PASS` and not `NOT_APPLICABLE`.

R32.1 therefore fails final device acceptance. No fix was attempted during this acceptance task.

## 2. Device and artifact identity

| Field | Evidence |
|---|---|
| ADB endpoint | `198.18.0.1:45891` (`device`) |
| Device | Xiaomi/POCO `25098PN5AC`, product/device `pandora` |
| OS | Android 16, SDK 36 |
| Build fingerprint | `Xiaomi/pandora/pandora:16/BP2A.250605.031.A3/OS3.0.315.2.WBLCNXM:user/release-keys` |
| Display | 1220×2656 physical, 520 dpi |
| APK | `.external_outbox/markleaf-release-latest.apk` |
| APK SHA-256 | `2cb388d84d64a7f477cff0bcd42b14b8201504a5bd788e4bef9b519ca4b92b31` |
| Package/version | `com.markleaf.app`, `1.0.0` (23) |
| APK signing | v2; RSA 3072 |
| Signer certificate SHA-256 | `4b7bb1848ae56f18530f1a7e660d215a693ac276d9cd171d55f3058f712d36a5` |
| Signer DN | `CN=MarkLeaf R32 Final Verification, O=MarkLeaf Engineering, C=CN` |

The APK was installed with `adb install -r`. Pulling the installed `base.apk` back from the device produced the same SHA-256 as the outbox APK, and `dumpsys package` reported version 23 / 1.0.0.

### 2.1 Signature-transition caveat

Before installation, the device held `0.1.0` (22), signed by Android Debug certificate SHA-256 `43ff89a0…`. The RC uses a different certificate and nevertheless replaced it while preserving the original `firstInstallTime`.

The device reported both `verifier_verify_adb_installs=0` and `package_verifier_enable=0`. This acceptance environment therefore permits a signature replacement that standard production Android normally rejects. The successful replacement is valid evidence that the exact RC is installed, but it is **not** evidence that C-local debug-signed users can upgrade to a future production-signed build.

## 3. Release-blocking crash

### 3.1 Reproduction

The first valid cold launch and three explicit force-stop/cold-launch retries all terminated before MarkLeaf acquired a stable foreground window. A fifth diagnostic launch with Android's global `disable_window_blurs=1` also crashed. That setting was restored to its original `null` value immediately afterward.

| Attempt | Environment | Result |
|---|---|---|
| Initial valid cold launch | Device default | Native crash; no Home |
| Retry 1 | Device default | Native crash; process absent after 3 s |
| Retry 2 | Device default | Native crash; process absent after 3 s |
| Retry 3 | Device default | Native crash; process absent after 3 s |
| Diagnostic 5 | `disable_window_blurs=1` | Same native crash; setting restored |

The invalid first shell command caused by Xiaomi's multi-line `resolve-activity` output is excluded from this count; it never launched MarkLeaf.

### 3.2 Native evidence

Crash buffer evidence:

```text
Fatal signal 11 (SIGSEGV), code 2 (SEGV_ACCERR), tid RenderThread
Cause: stack pointer is close to top of stack; likely stack overflow.
512 total frames
#00 libhwui.so android::uirenderer::RenderProperties::promotedToLayer()
#01 libhwui.so android::uirenderer::skiapipeline::MiBackgroundBlurBlend::preUpdateInfo(...)
#02 libhwui.so android::uirenderer::RenderNode::prepareTreeImpl(...)
#04 libhwui.so android::uirenderer::skiapipeline::SkiaDisplayList::prepareListAndChildren(...)
```

The backtrace then repeats `RenderNode::prepareTreeImpl` and `SkiaDisplayList::prepareListAndChildren` through frame 511. The stack clearly enters Xiaomi's `MiBackgroundBlurBlend` path. This report does not claim that the OEM implementation is the sole root cause; application render-node composition and the OEM HWUI blur implementation require a separate diagnosis task.

The UI hierarchy and screenshot captured after each failure belonged to the terminal application because MarkLeaf had already died. No MarkLeaf Home screenshot could be produced.

## 4. Acceptance matrix

| Area | Requested verification | Result | Evidence boundary |
|---|---|---|---|
| Install/signing | Install final RC and confirm signer | **PASS with environment caveat** | Installed APK hash and signer match exactly; verifier disabled on device |
| Cold launch | MainActivity reaches stable Home | **FAIL** | RenderThread SIGSEGV, 5/5 |
| Home | Continue Reading | **BLOCKED** | Home never rendered |
| Home | Glass Dock | **BLOCKED** | Home never rendered |
| Home | Library | **BLOCKED** | Home never rendered |
| Home | Search entry | **BLOCKED** | Home never rendered |
| Reader | PDF continuous mode | **BLOCKED** | Cannot enter Reader |
| Reader | Horizontal paging | **BLOCKED** | Cannot enter Reader |
| Reader | Pinch zoom | **BLOCKED** | Cannot enter Reader |
| Reader | Chrome state | **BLOCKED** | Cannot enter Reader |
| Annotation | Highlight creation | **BLOCKED** | Cannot enter Reader |
| Annotation | Overlay restoration | **BLOCKED** | Cannot enter Reader |
| Annotation | Annotation Sheet | **BLOCKED** | Cannot enter Reader |
| Annotation | Note create/delete | **BLOCKED** | Cannot enter Reader |
| Search | Result location restoration | **BLOCKED** | Cannot enter Home/Search |
| Performance | Long reading session | **BLOCKED** | Process dies at launch |
| Performance | Annotation-dense page | **BLOCKED** | Process dies at launch |

## 5. Performance decision

No frame timing, long-session memory, thermal, scrolling, or annotation-density measurement can be validly collected from a process that dies before the first usable screen. The launch crash itself is the controlling performance/stability failure. R32.1 does not substitute short-lived process metrics for the requested scenarios.

## 6. Device state after acceptance

- `com.markleaf.app` 1.0.0 (23) remains installed with the R32 disposable verification signer.
- Installed APK is byte-identical to the outbox artifact.
- Existing package `firstInstallTime` remained `2026-07-13 12:09:22`; no uninstall or data-clear command was used.
- Global `disable_window_blurs` was restored to its original `null` state.
- No device database, user document, UI setting, or app source file was deliberately modified by the test.

## 7. Issue record

### RC-DA-001 — P0 startup native crash on Xiaomi Android 16

- **Severity:** P0 / public-release blocker
- **Frequency:** 5/5 valid launches
- **Impact:** Application is unusable; all functional and performance acceptance is blocked
- **Thread/signal:** RenderThread / SIGSEGV / likely native stack overflow
- **Observed path:** `libhwui.so` → `MiBackgroundBlurBlend::preUpdateInfo` → recursive render-tree preparation
- **Fix status:** Not attempted, per R32.1 acceptance-only scope

Recommended follow-up evidence, in a separate diagnosis task:

1. reproduce on a second Android 16 non-Xiaomi device and a Xiaomi Android 15/16 device;
2. isolate the smallest render-node/blur composition that triggers the recursive OEM path;
3. verify whether the crash is Home-only or shared by Reader glass chrome using a diagnosis-only build or controlled route;
4. after a fix, rerun the complete R32.1 matrix from a newly signed canonical RC.

## 8. Final decision

**R32.1 DEVICE ACCEPTANCE: FAIL**
**PUBLIC RELEASE: NO-GO**

Installation and signer identity passed, but stable launch is a mandatory prerequisite. Home, Reader, Annotation, Search, and performance remain unverified because the RC consistently crashes before they can be exercised.
