# Glass Material Engine Report

**Date:** 2026-08-25
**Status:** implemented / automated_verified / device_pending

## Objective

Unify MarkLeaf's floating glass surfaces without changing product flows,
gestures, layout geometry, or user-visible settings. The implementation stays
inside Compose and uses the existing Kyant Backdrop dependency only.

## Reference review

The implementation borrows architecture ideas, not code:

- [ColdP/HyperChanger](https://github.com/ColdP/HyperChanger): semantic appearance
  slots are normalized into one settings model and applied through a dedicated
  boundary. MarkLeaf adopts the same separation of semantic role, normalized
  recipe and renderer.
- [KiminonawaResa/HyperLight](https://github.com/KiminonawaResa/HyperLight): treats
  blur, blend color, stroke and shadow as one material recipe. MarkLeaf keeps
  those parameters together instead of letting components tune them separately.

MarkLeaf does **not** adopt Xposed/LSPosed, reflection, System UI hooks, HyperOS
private `MaterialToken` APIs, permissions, or vendor-specific fallbacks.

## Root cause

Three material authorities coexisted:

1. `ui/common/MarkLeafLiquidSurface.kt` owned the active Backdrop recipe.
2. `ui/pdf/ReaderChromeGlass.kt` owned a second alpha/stroke/shadow recipe. Its
   surface renderer was no longer used at runtime, but geometry and tests still
   depended on its tokens.
3. Dialogs used independent Material 3 container colors.

`ReaderChromeMaterial.kt` added another Reader-only mapping layer between the
first two systems. A parameter change therefore did not have one authoritative
path.

## Implemented architecture

The single authority now lives in `ui/material`:

- `GlassMaterialEngine` resolves theme and pressed state.
- `GlassMaterialRole` is the semantic input.
- `Modifier.glassMaterial` is the only glass renderer.
- `GlassBackdropHost` owns root capture and the exported Menu layer used for
  stacked SettingsCard glass.
- `GlassAlertDialog` preserves platform dialog behavior while applying the
  Dialog recipe.

| Role | Current consumers | Light alpha | Blur | Shadow |
|---|---|---:|---:|---:|
| Control | Reader back/more circles, Home FAB | 0.50 | 12dp | 8dp |
| Capsule | PDF position/jump capsule | 0.50 | 12dp | 8dp |
| Menu | Reader More parent, Home utility menu | 0.60 | 16dp | 16dp |
| SettingsCard | Anchored advanced Reader cards | 0.66 | 16dp | 18dp |
| Dialog | Import, outline and export dialogs | 0.72 | 18dp | 18dp |

The engine also owns dark/light tint, saturation, brightness, highlight/stroke,
shadow color, secondary-content alpha, divider alpha, lens dimensions and
pressed-state boosts. Components continue to own shape, size, position and
animation geometry.

Popup and Dialog content lives in a separate platform window. Cross-window
Backdrop sampling is unsafe, so those consumers explicitly use the engine's
soft-plate renderer. It applies the same resolved tint, alpha, highlight/stroke
and shadow; it does not pretend to provide a sampled blur when no capturable
backdrop exists.

## Removed authorities

- `ui/common/MarkLeafLiquidSurface.kt`
- `ui/pdf/ReaderChromeGlass.kt`
- `ui/pdf/ReaderChromeMaterial.kt`
- `ColorScheme.dialogContainer`
- Home's component-local popup edge-highlight tokens

Reader input/button corner geometry moved to `AdvancedChildLayoutSpec`; it is
layout geometry and intentionally does not belong to the material engine.

## Scope proof

- No dependency changes.
- No manifest or permission changes; INTERNET remains absent.
- No user-visible settings.
- No PDF renderer, state machine, gesture model or navigation changes.
- Existing More/capsule morph and press-target contracts remain covered.

## Verification

### automated_verified

- Targeted material/geometry contracts: **35 tests / 0 failures**.
- `:app:testQaUnitTest`: **1167 tests / 0 failures / 0 errors**.
- `:app:compileQaKotlin`: PASS.
- `:app:compileQaAndroidTestKotlin`: PASS.
- `git diff --check`: PASS.
- `:app:assembleRelease`: PASS (R8/minify + shrink resources).
- APK signature: v2 verified.

### Lint

`:app:lintQa` did not produce findings because the existing lint toolchain
crashed in `ComposableFlowOperatorDetector` with a Kotlin Analysis API binary
incompatibility (`KaFunctionCall` class/interface mismatch). The crash occurred
while analyzing existing `DisplayRefreshRatePolicy.kt` and QA test files. No
detector was disabled and no assertion was weakened to manufacture a pass.

### APK delivery

- Outbox: `.external_outbox/markleaf-release-latest.apk`
- Manual install: `/storage/emulated/0/Download/markleaf-release-latest.apk`
- Size: **11,493,231 bytes**
- SHA-256: `2997b6520ab5ca6f64a958f4cbfd30fb38dd4f32ddeafc00e087e3cb6a0e669a`

### device_pending

`adb devices -l` returned no devices. Install and visual smoke were not run and
are not marked PASS. Manual acceptance should cover Home FAB/menu, PDF chrome,
capsule expansion, all four advanced cards, import confirmation, outline dialogs
and export progress in both light and dark themes.
