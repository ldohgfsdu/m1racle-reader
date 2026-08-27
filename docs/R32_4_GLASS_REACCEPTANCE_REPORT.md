# R32.4 Glass Backdrop RC Re-Acceptance Report

**Date:** 2026-08-27
**Status:** **PASS — R32.2 native crash blocker cleared**
**Coverage note:** `Highlight overlay = NOT_VERIFIED_NO_FIXTURE`; all other requested device paths passed
**Scope:** Device-only acceptance. No production code, GlassMaterial, Backdrop topology, UI, database, or APK content was modified.

## 1. Executive result

The exact R32.3 verification APK was installed in place on the Xiaomi Android 16 target and survived five independent force-stop/cold-start cycles. Every cycle reached a fully populated Home UI tree with all four Glass Dock destinations, retained a live process, and produced no MarkLeaf fatal signal, RenderThread `SIGSEGV`, `MiBackgroundBlurBlend` recursion, or new tombstone.

Home, Library, Bookmarks, and Settings all switched successfully through the shared Dock. Reader PDF Chrome, Capsule, More Menu, SettingsCard paths, and a Glass dialog were reachable without crash. Annotation Sheet and Search opened successfully. A ten-second idle `gfxinfo` check recorded only two frames and no jank/deadline/slow-draw counters, providing no evidence of sustained redraw.

The R32.2 release blocker is therefore cleared on the original affected device. This report does not claim Highlight overlay restoration because both inspected Annotation Sheet evidence and the selected document state contained no highlight fixture; acceptance did not create or alter user data merely to manufacture that evidence.

## 2. Device and artifact identity

| Field | Evidence |
|---|---|
| ADB endpoint | `198.18.0.1:45043` (`device`) |
| Device | Xiaomi/POCO `25098PN5AC`, device `pandora` |
| OS | Android 16 / SDK 36 |
| Build fingerprint | `Xiaomi/pandora/pandora:16/BP2A.250605.031.A3/OS3.0.315.2.WBLCNXM:user/release-keys` |
| APK | `.external_outbox/markleaf-release-latest.apk` |
| Package/version | `com.markleaf.app` 1.0.0 (23) |
| APK size | 11,660,123 bytes |
| Expected SHA-256 | `387c0021be6a68a0156fd23481f41614e0c7583d20390f3b12eae0a1fbbc215f` |
| Pulled installed `base.apk` SHA-256 | `387c0021be6a68a0156fd23481f41614e0c7583d20390f3b12eae0a1fbbc215f` |
| Signing | APK Signature Scheme v2 |
| Signer SHA-256 | `b1edf25780f76f464b60a78e86d30c37db938f1400e7596644604b7a54fc322b` |
| Signer DN | `CN=MarkLeaf R32.3 Build Verification, O=MarkLeaf Engineering, C=CN` |

`adb install -r` returned `Success`. Pulling the installed package back from `/data/app/.../base.apk` produced the exact outbox hash and signer. `firstInstallTime=2026-07-13 12:09:22` remained intact; no uninstall or data clear occurred.

The package already showed `lastUpdateTime=2026-08-27 09:13:28` after the user's manual R32.3 installation. The controlled re-install completed at `09:55:11`.

## 3. Xiaomi cold-start matrix

Each run used:

```text
am force-stop com.markleaf.app
am start -W -S -n com.markleaf.app/.MainActivity
wait 4 seconds
capture PID, focused window, UI tree, crash buffer, full logcat, tombstone list
```

| Attempt | PID after 4 s | `am start` wait | MarkLeaf focus | Home marker | Dock entries | MarkLeaf fatal/native crash |
|---:|---:|---:|---:|---:|---:|---:|
| 1 | 610 | 154 ms | PASS | PASS | 4/4 | 0 |
| 2 | 14771 | 131 ms | PASS | PASS | 4/4 | 0 |
| 3 | 17631 | 150 ms | PASS | PASS | 4/4 | 0 |
| 4 | 18175 | 132 ms | PASS | PASS | 4/4 | 0 |
| 5 | 18776 | 150 ms | PASS | PASS | 4/4 | 0 |

The Xiaomi window dump exposes an initial sub-display `mCurrentFocus=null`, followed by the authoritative main-display records:

```text
mCurrentFocus=... com.markleaf.app/com.markleaf.app.MainActivity
mFocusedApp=... com.markleaf.app/.MainActivity
topResumedActivity=... com.markleaf.app/.MainActivity
```

Every UI tree contained `墨阅 MarkLeaf`, populated Home content, and clickable `首页 / 文库 / 书签 / 设置` Dock destinations.

**Cold-start result: 5/5 PASS.**

## 4. Native crash, crash buffer, and tombstone audit

### 4.1 Tombstone delta

Before install/test and after the complete matrix, the newest files were identical:

```text
tombstone_08     947753 bytes  2026-08-27 07:55
tombstone_08.pb  617321 bytes  2026-08-27 07:55
```

No `tombstone_09` or other new tombstone appeared.

The pre-existing `tombstone_08` was pulled and inspected. It belongs to `com.markleaf.app` and contains the old 512-frame `MiBackgroundBlurBlend` RenderThread stack overflow. Its timestamp is `07:55:57`, which is earlier than the recorded R32.3 manual-install time `09:13:28`; it is therefore evidence from the old RC, not an R32.3 regression.

### 4.2 Crash buffer and logcat

- Per-cold-start MarkLeaf crash matches: `0 / 0 / 0 / 0 / 0`.
- Final MarkLeaf fatal/ANR/native-signal matches: `0`.
- No `MiBackgroundBlurBlend`, MarkLeaf `SIGSEGV`, stack overflow, or MarkLeaf `FATAL EXCEPTION` appeared after the final cold-start log reset.

The global crash buffer later contained one unrelated Java fatal from `com.omarea.vtools` (`ClosedChannelException`). It is explicitly excluded from MarkLeaf results and retained in raw evidence rather than hidden.

## 5. Glass Dock topology paths

All taps used coordinates computed from the current UI tree, never screenshot-derived coordinates.

| Page | UI evidence | Dock evidence | Result |
|---|---|---|---|
| Home | `墨阅 MarkLeaf`, Recent Files, document cards | `首页 / 文库 / 书签 / 设置` present and clickable | PASS |
| Library | Title `文库`, filters `全部 / PDF / Markdown` | Same four Dock entries | PASS |
| Bookmarks | Title `书签`, empty-state copy | Same four Dock entries | PASS |
| Settings | Title `设置`, app identity/version content | Same four Dock entries | PASS |

The Dock changed destination on every tap and the process remained foreground/alive with no crash-buffer match.

### Screenshot limitation

This Xiaomi exposes two physical displays. `screencap`—including explicit SurfaceFlinger display IDs—captured the terminal-host display while WindowManager and UIAutomator identified MarkLeaf on display 0. Those images are not used as false visual proof. Dock acceptance is based on the rendered accessibility hierarchy, clickable bounds, successful destination changes, window focus, and absence of crash. Pixel-level glass appearance remains corroborated by the user's manual Home observation, not by the unusable automated screenshots.

## 6. Reader regression

An existing 75.2 MB PDF was opened at the restored location.

| Surface | Device evidence | Result |
|---|---|---|
| PDF content | `PDF page 381 of 384` and page 382 semantics | PASS |
| Reader Chrome | Top `返回` and `更多` controls | PASS |
| Capsule | Chapter `女性雌激素的来源`, `381 / 384 · 99%` | PASS |
| More Menu | Reading mode, appearance, page mapping, outline, thumbnails, export, annotations, bookmark rows | PASS |
| SettingsCard paths | Expanded `阅读方式` and `阅读界面`; `普通 / 全屏沉浸` choices present | PASS |
| Dialog | `新建目录节点` Glass dialog with fields and Cancel/Save | PASS |

The dialog was cancelled. Export and textbook-page cards were also cancelled without saving. No Reader setting, outline node, annotation, or database row was changed.

Reader crash-buffer matches remained zero.

## 7. Annotation and Search regression

| Scenario | Evidence | Result |
|---|---|---|
| Annotation Sheet | Bottom sheet opened with `高亮` and `批注` sections | PASS |
| Highlight overlay | Current document explicitly reported `当前文档还没有高亮标注。` | **NOT_VERIFIED_NO_FIXTURE** |
| Search | `本地搜索` page opened; input prompt `搜索文档、高亮或笔记` | PASS |

No highlight was created because R32.4 is acceptance-only and prohibits database/UI mutation. The missing fixture is a coverage limitation, not a product failure and not a fabricated PASS.

## 8. Quick performance/stability check

After the interaction matrix, `gfxinfo` was reset and the Search page was left idle.

| Metric | Start | After 10 s |
|---|---:|---:|
| Total frames rendered | 0 | 2 |
| Janky frames | 0 | 0 |
| Missed Vsync | 0 | 0 |
| Slow UI thread | 0 | 0 |
| Slow draw commands | 0 | 0 |
| Frame deadline missed | 0 | 0 |
| Total PSS | 257,221 KB | 149,853 KB |

The two idle frames were 5 ms in the final snapshot. The cumulative `dumpsys cpuinfo` line remained at 1.7% across the two snapshots. Memory decreased rather than growing. This short check provides no evidence of a continuous redraw loop, runaway memory, ANR, or crash; it is not a long-session performance certification.

## 9. Evidence inventory

Raw evidence is retained outside the repository under `/tmp/markleaf-r32-4/`:

- artifact/install identity: `install-r.txt`, `installed-base.apk`, `installed-apk-sha256.txt`, `installed-apksigner.txt`;
- five cold starts: `cold-1/` through `cold-5/`, plus `cold5-summary.txt`;
- Hub routes: `hub/*.xml`, summaries, action-coordinate records, and screenshots with the multi-display caveat;
- Reader surfaces: `reader/*.xml` and summaries;
- Annotation/Search: `annotation-search/*.xml` and summaries;
- performance: `performance/gfxinfo-*`, `cpuinfo-*`, `meminfo-*`;
- native audit: `tombstones-before.txt`, `tombstones-final.txt`, `tombstone_08-preexisting.txt`, `final-crash-buffer.txt`, `final-logcat-all.txt`.

Large logs, tombstones, APKs, and screenshots were not added to source control.

## 10. Constraint compliance and disposition

- Production source changes: none.
- GlassMaterial/Backdrop/UI/database changes: none.
- APK rebuild, mutation, or re-signing: none.
- Device uninstall or data clear: none.
- Failure repair during acceptance: none.
- Unsupported device claims: none.

**R32.4 Glass Backdrop re-acceptance: PASS.**
**R32.2 Xiaomi RenderThread native-crash blocker: CLEARED on the affected device.**
**Highlight overlay coverage: PENDING a pre-existing non-mutating fixture.**

The tested artifact uses the disposable R32.3 verification signer. Public publishing still requires the production-owner signing/tag/archive gates from R32; this device result must not be mistaken for production signing approval.
