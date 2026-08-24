# MarkLeaf C-2.3 Device Retest Report

## 0. Result

| Field | Value |
|---|---|
| Date | 2026-08-24 (UTC+8) |
| Prior C-2 status | `HUMAN_REJECTED` |
| Fix under test | `604d404978b5f09fdb4a9c825259ef6793f59949` — C-2.2 Export Cancellation Fix |
| Release lineage | `v0.1.0`, versionCode `22`, versionName `0.1.0` |
| Device | Xiaomi `25098PN5AC`, Android 16, 1220×2656 @ 520 dpi |
| ADB endpoint | `198.18.0.1:32831`, state=`device` |
| Fix-build APK SHA-256 | `7285123ca78bf531580fe7bfda64c4e0e0d6737be342b2a9fe5c5711fd722e95` |
| Final status | **HUMAN_ACCEPTED** |

C-2.3 retested only the C-2 failure and its required export regressions. The 384-page ZIP export was cancelled immediately after SAF returned to MarkLeaf. The application settled at `已取消导出`, and the selected SAF destination remained absent across repeated checks. No zero-byte file and no partial artifact remained. Normal ZIP export and annotated-PDF cancellation also passed.

This is a local fix-build device acceptance, not a new release publication. The existing `v0.1.0` tag was not moved and still points to the original release commit.

## 1. Build, install, and identity

### 1.1 Local fix build

| Check | Result | Evidence |
|---|---|---|
| Clean build input | **PASS** | Repository was clean; HEAD was exactly `604d404978b5f09fdb4a9c825259ef6793f59949` before `assembleRelease` |
| `:app:assembleRelease` | **PASS** | `BUILD SUCCESSFUL` on 2026-08-24; no source or configuration change was made |
| Timestamped APK | **PASS** | `.external_outbox/markleaf-release-v0.1.0-20260824-171232.apk` |
| APK size | **PASS** | 11,493,231 bytes |
| Build time | **PASS** | `2026-08-24 17:12:32 +0800` |
| Required Download copy | **PASS** | `/storage/emulated/0/Download/markleaf-release-latest.apk` |
| Outbox / Download SHA | **PASS** | Both copies: `7285123ca78bf531580fe7bfda64c4e0e0d6737be342b2a9fe5c5711fd722e95` |

The Download copy is the repository-mandated post-build delivery copy. It does not represent a republished RC.

### 1.2 Device install and package identity

| Check | Result | Evidence |
|---|---|---|
| ADB connection | **PASS** | Endpoint state was `device` |
| Install | **PASS** | `adb install -r` returned `Success` |
| Package identity | **PASS** | APK and installed package are `com.markleaf.app` |
| versionCode | **PASS** | APK and device Package Manager both report `22` |
| versionName | **PASS** | APK and device Package Manager both report `0.1.0` |
| Installed APK SHA-256 | **PASS** | Device `base.apk`: `7285123ca78bf531580fe7bfda64c4e0e0d6737be342b2a9fe5c5711fd722e95` |
| Commit identity | **PASS** | Clean HEAD `604d404...` → fix-build APK SHA above → installed `base.apk` with the same SHA |
| MarkLeaf crash check | **PASS** | No `com.markleaf.app` fatal entry in the device crash buffer during the retest |

The release tag remained unchanged:

```text
v0.1.0 -> 5febda3bc0ea2582cdff276457ffec466e072650
```

The tag therefore identifies the original C-1 release, while this report explicitly identifies the untagged C-2.2 fix build by commit and APK digest.

## 2. Targeted failure retest — ZIP mid-cancel

Input: the existing 384-page PDF `27生理学笔记【合集】（带目录）.pdf`.

Procedure:

1. Open More → Export chapter.
2. Select `ZIP 处理包` and set the page range to `1–384`.
3. In the system SAF picker, create `C23-zip-mid-cancel.zip` in Download.
4. Immediately after SAF returned to MarkLeaf, invoke the visible `取消` action while the export dialog was active.
5. Wait for the task to settle, then inspect the SAF output directory repeatedly.

| Acceptance item | Result | Device evidence |
|---|---|---|
| Cancellation was during an active export | **PASS** | 1–384 range; cancel action was found and invoked immediately after Save returned; the UI transitioned to `已取消导出` |
| No zero-byte residue | **PASS** | `/sdcard/Download/C23-zip-mid-cancel.zip` was absent on all five one-second checks after settlement |
| No partial artifact | **PASS** | Prefix scan for `C23-zip-mid-cancel*` in Download returned no entry |
| Task reached terminal state | **PASS** | Result dialog displayed `已取消导出` and the `完成` action |

**ZIP mid-cancel result: PASS.** The C-2 rejection condition is no longer reproducible on the C-2.2 fix build.

## 3. Required regressions

### 3.1 ZIP normal export

| Check | Result | Evidence |
|---|---|---|
| Normal completion | **PASS** | MarkLeaf displayed the completed export result |
| Output exists and is non-empty | **PASS** | `/sdcard/Download/C23-zip-normal.zip`, 1,590,272 bytes |
| Output SHA-256 | **PASS** | `f3cc1a9324704cbd7494bd227be6eca064fada0bbbda0d42044154d3fe2789c3` |
| ZIP integrity | **PASS** | `unzip -t` reported no errors |
| ZIP contents | **PASS** | 5 entries: `source.pdf`, `manifest.json`, `outline.json`, `page_map.json`, `checksums.json` |

Normal completion retained its SAF output; cancellation cleanup did not delete a committed export.

### 3.2 Annotated PDF cancellation

Procedure: select `纯 PDF`, set range `1–384`, create `C23-pdf-cancel.pdf` through SAF, then immediately invoke the active export cancellation action.

| Check | Result | Evidence |
|---|---|---|
| Cancellation terminal state | **PASS** | MarkLeaf displayed `已取消导出` |
| No zero-byte residue | **PASS** | `/sdcard/Download/C23-pdf-cancel.pdf` was absent on all five one-second checks after settlement |
| No partial artifact | **PASS** | Prefix scan for `C23-pdf-cancel*` in Download returned no entry |

**Annotated PDF cancellation result: PASS.**

## 4. Acceptance matrix

| Requested C-2.3 item | Status |
|---|---|
| Build and install C-2.2 fix APK | **PASS** |
| versionCode / versionName | **PASS** — `22` / `0.1.0` |
| APK SHA | **PASS** — build, Download copy, and installed `base.apk` match |
| Commit identity | **PASS** — clean build chain from `604d404...` |
| ZIP export mid-cancel | **PASS** |
| No 0-byte residue | **PASS** |
| No partial artifact | **PASS** |
| ZIP normal export | **PASS** |
| PDF cancel | **PASS** |

## 5. Scope and pending

- No product code, UI, import logic, configuration, version, or release tag was changed.
- No unrelated pending item was investigated or fixed.
- The APK was built and installed solely for C-2.3 device verification; it was not tagged or published as a replacement release.
- Only this evidence report is added to the repository.
- The earlier C-2 pinch-zoom `PENDING` item was not rerun and is not converted to PASS by this targeted retest.

## 6. Final status

**HUMAN_ACCEPTED**

The sole C-2 rejection item now passes on the real device, and both required regressions pass. The prior `HUMAN_REJECTED` result is superseded for the C-2 export-cancellation gate by this C-2.3 evidence.
