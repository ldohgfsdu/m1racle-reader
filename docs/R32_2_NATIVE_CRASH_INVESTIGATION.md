# R32.2 Cold Start Native Crash Investigation

**Date:** 2026-08-27
**Result:** **TRIGGER LOCATED — PUBLIC RELEASE REMAINS BLOCKED**
**Scope:** Frozen R32 RC diagnosis only. No production source, UI, database, GlassMaterial, APK, or device app data was modified.

## 1. Executive conclusion

The Xiaomi Android 16 crash is triggered by a circular Backdrop render graph on the Home first-frame path, not by Application initialization, Room, Search, Annotation, or Reader startup.

The smallest unconditional component that closes this cycle in the frozen RC is `GlassDock`:

```text
Home Box.layerBackdrop(rootBackdrop)
  └─ GlassDock (always composed inside that Box)
       └─ Modifier.glassMaterial(sampleBackdrop = true by default)
            └─ drawBackdrop(backdrop = rootBackdrop)
                 └─ samples an ancestor capture that already contains the Dock
```

On Xiaomi's HWUI implementation this self-referential render-node graph enters `MiBackgroundBlurBlend::preUpdateInfo`, recursively prepares the same child graph, exhausts the RenderThread stack, and terminates with native `SIGSEGV / SEGV_ACCERR`.

This is a trigger-location conclusion, not a fix. No render topology or material behavior was changed.

## 2. Artifact and device integrity

| Field | Evidence |
|---|---|
| ADB target | `198.18.0.1:45891` (`device`) |
| Device | Xiaomi/POCO `25098PN5AC` (`pandora`) |
| OS | Android 16 / SDK 36 |
| Build fingerprint | `Xiaomi/pandora/pandora:16/BP2A.250605.031.A3/OS3.0.315.2.WBLCNXM:user/release-keys` |
| Package | `com.markleaf.app` 1.0.0 (23) |
| Outbox RC SHA-256 | `2cb388d84d64a7f477cff0bcd42b14b8201504a5bd788e4bef9b519ca4b92b31` |
| Pulled installed `base.apk` SHA-256 | `2cb388d84d64a7f477cff0bcd42b14b8201504a5bd788e4bef9b519ca4b92b31` |
| Existing first install time | `2026-07-13 12:09:22` (preserved) |

The installed package remains byte-identical to `.external_outbox/markleaf-release-latest.apk`. No install, uninstall, downgrade, data clear, database write, or replacement build occurred in R32.2.

## 3. Fresh native evidence

R32.2 captured a new independent cold launch at device time `2026-08-27 07:13:53`:

| Event | Time | Delta |
|---|---:|---:|
| Process start | 07:13:53.265 | baseline |
| `MainActivity.onCreate` completed | 07:13:53.343 | +78 ms |
| `MainActivity.onResume` completed | 07:13:53.345 | +80 ms |
| RenderThread fatal signal | 07:13:53.391 | +126 ms from process start / +48 ms from create |
| Process reported dead | 07:13:53.603 | +338 ms |

The process was absent at the four-second check. The launch generated a new full tombstone (`tombstone_06`, 955,151 bytes, 7,507 lines) and protobuf tombstone (622,120 bytes).

### 3.1 Full stack identity

```text
pid 27372, tid 25018, name RenderThread, cmdline com.markleaf.app
signal 11 (SIGSEGV), code 2 (SEGV_ACCERR)
Cause: stack pointer is close to top of stack; likely stack overflow.
512 total frames

#00 libhwui.so RenderProperties::promotedToLayer()
#01 libhwui.so MiBackgroundBlurBlend::preUpdateInfo(...)
#02 libhwui.so RenderNode::prepareTreeImpl(...)
#03 libhwui.so prepareTreeImpl child callback
#04 libhwui.so SkiaDisplayList::prepareListAndChildren(...)
```

Across the 512-frame capped backtrace, symbol counting finds 340 `prepareTreeImpl` occurrences and 170 `prepareListAndChildren` occurrences. Frames 2–511 are the repeating prepare-tree cycle; there is no application Java/Kotlin exception and no Reader/PDF native frame at the crash head.

The HWUI Build ID is `a2d2154e225bac1cf5d9a691e16364c4`. This identifies the observed OEM crash site. It does not by itself assign sole responsibility to the OEM: the application supplies the circular Backdrop topology that reaches this path.

### 3.2 Captured evidence

Host-side evidence is retained under `/tmp/markleaf-r32-2-native-crash/` for this investigation session:

| Evidence | SHA-256 / detail |
|---|---|
| `tombstone_06.txt` | `d63aa5b538a53b46613b1efdccc13341d7343211c39103184dfe30a9d1e8204b` |
| `tombstone_06.pb` | `02e4ded4395c2786e1b8867e0a6d8059f38df7929fd156d0d27d7e04a58045cd` |
| `startup.perfetto-trace` | `e0b091d2ac637ee6f9271578e54beb7915e5ce755efbe4a7ef612c2c9b770e39` (33,488,912 bytes) |
| `logcat-all.txt` | Process/activity lifecycle and death timing |
| `logcat-crash.txt` | Full 512-frame crash-buffer backtrace |
| `dropbox-data_app_native_crash.txt` | Android DropBox native-crash records |

The raw tombstone and Perfetto trace are intentionally not added to the source repository.

## 4. Startup-path comparison

### 4.1 Application

`MarkLeafApplication` has no `onCreate` rendering work. Database and repositories are lazy properties. The process reaches `MainActivity.onCreate` and `onResume`, ruling out an Application-construction native crash.

### 4.2 MainActivity and Compose root

`MainActivity.onCreate` applies edge-to-edge/display settings, configures release-disabled Reader experiments, dispatches the launch intent, and calls `setContent`. The Compose root is `MarkLeafTheme → Material3 Surface → MarkLeafNavGraph`.

The release manifest contains one launchable activity, `MainActivity`. The RC has no release diagnostic Activity and no Glass feature extra. Its only build-time diagnostic fields concern PDF detail tiles and QA-only Reader performance experiments.

### 4.3 Home

`MarkLeafNavGraph` has a fixed `Routes.HOME` start destination. `HomeScreen` then:

1. creates `rootBackdrop` and `parentExport`;
2. provides them through `LocalGlassBackdropHost`;
3. applies `.layerBackdrop(rootBackdrop)` to the Home content `Box`;
4. composes `GlassDock` unconditionally as a child of that same `Box`;
5. `GlassDock` applies `glassMaterial(Control)` with the default `sampleBackdrop = true`;
6. `glassMaterial` resolves the host's `rootBackdrop` and calls `drawBackdrop`.

The material contract in `GlassMaterial.kt` already states that `sampleBackdrop` must be false for a surface inside its own captured layer. The older Home visual report independently records that sampling from inside Home's `layerBackdrop` causes a circular render crash. The Dock call site violates that topology constraint without changing the frozen `GlassMaterial` implementation itself.

### 4.4 Other Home glass consumers

- The Home FAB samples `rootBackdrop`, but it lives in the Scaffold FAB slot outside the captured content `Box`; it does not by itself close the same ancestor/descendant cycle.
- The delete Popup explicitly uses `sampleBackdrop = false` and therefore uses the soft-plate path.
- `ContinueReadingCard` uses default backdrop sampling and is placed inside the captured list. When present, it is a second circular consumer, but it is data-dependent.
- `GlassDock` is present in both empty and populated Home states, so it is the smallest unconditional trigger in the RC startup tree.

## 5. Minimal reproduction matrix

The requested a–d switches do not exist in the frozen release binary. Adding them, patching DEX, or building source-modified diagnostic APKs would violate the explicit R32.2 constraints and would no longer test the preserved RC. The matrix therefore separates executed evidence from unavailable binary ablations.

| Case | Frozen-RC execution | Result | Interpretation |
|---|---|---|---|
| Baseline: all current Glass paths enabled | Executed | **CRASH** | R32.2 fresh default launch 1/1; same 512-frame recursion |
| a) `GlassMaterial` disabled | No existing switch | **BLOCKED_BY_FROZEN_BINARY** | Requires production/binary modification; not claimed as tested |
| b) Glass Dock disabled | No existing switch | **BLOCKED_BY_FROZEN_BINARY** | Requires production/binary modification; not claimed as tested |
| c) Home glass cards disabled | No existing switch | **BLOCKED_BY_FROZEN_BINARY** | Cards are conditional, but Dock remains unconditional; no removal run was fabricated |
| d) All Glass disabled | No existing switch | **BLOCKED_BY_FROZEN_BINARY** | Requires production/binary modification; not claimed as tested |
| Android global `disable_window_blurs=1` | Executed, then restored | **CRASH** | R32.2 1/1, same stack; this system switch does not disable Compose Backdrop capture/draw nodes |

Combined with R32.1, the evidence set is:

- device-default cold launch: 5/5 crashes (4 R32.1 + 1 R32.2);
- `disable_window_blurs=1`: 2/2 crashes (1 R32.1 + 1 R32.2);
- total observed launches: 7/7 crashes;
- the global setting was restored to its original `null` value after each diagnostic run.

The inability to run a–d is an explicit limitation, not a negative result. It does not prevent locating the smallest unconditional cycle from the exact frozen startup graph plus the matching native recursion, but it means no report should claim a successful crash-free ablation build.

## 6. Trigger statement and confidence boundary

### Confirmed

- Cold startup reaches Home Compose and crashes during the first HWUI render-tree preparation.
- The native failure is a RenderThread stack overflow with a 510-frame repeating prepare-tree cycle through Xiaomi `MiBackgroundBlurBlend`.
- Home constructs a self-referential Backdrop graph.
- `GlassDock` is the smallest unconditional Home component that closes that graph by sampling an ancestor layer that contains the Dock itself.
- Disabling Android window blur does not remove the failure because it does not remove the Compose Backdrop graph.

### Not claimed

- No claim that Xiaomi HWUI is the sole root cause.
- No claim that all devices or OS versions reproduce it; only the specified Xiaomi Android 16 target was tested.
- No claim that a Dock-disabled or all-Glass-disabled APK launches successfully; those variants do not exist and were not created.
- No fix, workaround, dependency change, or render-policy change was attempted.

## 7. Final disposition

**R32.2 diagnosis:** trigger located at the unconditional Home `layerBackdrop(rootBackdrop) → descendant GlassDock → drawBackdrop(rootBackdrop)` cycle.
**Release status:** **NO-GO remains in force.**
**Production changes:** none.
**Device state:** RC remains installed; app data and database untouched; `disable_window_blurs=null` restored.
