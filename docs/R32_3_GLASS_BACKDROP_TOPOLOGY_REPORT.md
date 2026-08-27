# R32.3 Glass Backdrop Topology Report

**Date:** 2026-08-27
**Status:** `implemented / automated_verified / release_build_verified / device_pending`
**Release decision:** **NO-GO remains until the Xiaomi cold-start regression is verified on device**

## 1. Outcome

R32.2 identified a self-referential Home render graph:

```text
ancestor layerBackdrop(rootBackdrop)
  └─ descendant GlassDock
       └─ drawBackdrop(rootBackdrop)
```

R32.3 removes that topology while retaining the Glass Engine and all frozen material recipes. Backdrop capture now has one explicit ownership boundary, `GlassBackdropSource`. The source subtree cannot access the host it is feeding, and all real sampling surfaces are sibling overlays outside the capture subtree.

No `GlassMaterial` role, alpha, blur, lens, highlight, shadow, press animation, or engine parameter changed. `GlassMaterial.kt` remains SHA-256:

```text
591b03e07a44bfcfc7b03146928b60d8a58c6c8e27c15077b83e26c5f797b8ea
```

## 2. Ownership and scope rule

The enforced rule is:

1. A `LayerBackdrop` has exactly one capture owner.
2. Captured content is composed through `GlassBackdropSource`.
3. `GlassBackdropSource` masks `LocalGlassBackdropHost` inside its subtree before attaching `layerBackdrop`.
4. A surface that samples that backdrop must be composed as a sibling after the source, under the outer host provider.
5. A glass surface intentionally located inside captured content follows the existing engine soft-plate path because the captured subtree cannot resolve its own host.
6. Nested glass-on-glass remains valid only through the existing exported backdrop relationship; a child may not sample an ancestor capture that already contains the child.

This makes the cycle prevention structural rather than dependent on every future call site remembering a flag.

## 3. Implementation

### 3.1 Guarded capture boundary

Added `ui/material/GlassBackdropTopology.kt` with the sole production `layerBackdrop` call. It:

- owns the capture node;
- applies `LocalGlassBackdropHost provides null` to captured descendants;
- leaves sibling overlays under the outer host;
- does not modify `GlassMaterial.kt`.

A source scan now finds exactly one direct production `.layerBackdrop(...)` owner: `GlassBackdropTopology.kt`.

### 3.2 Home

Home now uses an outer overlay `Box`:

```text
LocalGlassBackdropHost(root)
  └─ outer Box
       ├─ GlassBackdropSource(root) → Home content
       └─ GlassDock → samples root as a sibling
```

The Dock keeps `GlassMaterialRole.Control` and real backdrop sampling, but the sampled source no longer contains the Dock. The Home FAB remains in the Scaffold FAB slot outside captured content. The data-dependent Continue Reading card remains in the shared Glass visual system and cannot sample its containing source, so it cannot create a second ancestor cycle.

### 3.3 Shared Hub screens

The previous `HubGlassShell` also captured its own Dock on Library, Bookmarks, and Settings. Its API now owns `selected` and `onSelect`, captures page content, and composes one Dock as a sibling overlay. Individual screens no longer place Dock inside the content lambda.

### 3.4 Reader, Dialog, and Menu preservation

- Reader PDF content uses the same guarded source boundary.
- Reader Chrome remains a sibling overlay under its existing `GlassBackdropHost` and continues using the same Control/Capsule/Menu/exported-backdrop relationships.
- `GlassAlertDialog` retains `sampleBackdrop = false`.
- Home Popup menu retains `sampleBackdrop = false`.
- No Reader state machine, PDF renderer, Dialog/Menu flow, Glass recipe, dependency, or OEM API changed.

## 4. Automated regression coverage

Added `GlassBackdropTopologyContractTest` with six root-cause contracts:

1. capture scope masks its host before attaching the layer;
2. every production capture node routes through the guarded owner;
3. Home Dock is outside captured Home content;
4. shared Hub Dock is outside captured page content;
5. Reader captured content remains content-only and Chrome remains outside it;
6. Dialog and Menu retain isolated soft-plate behavior.

Existing Home and Reader material contracts were updated to require `GlassBackdropSource` instead of a direct, unguarded `layerBackdrop` call. Frozen recipe-number tests remain unchanged.

### 4.1 Directed gate

The first directed run compiled successfully but exposed one pre-existing whitespace-exact Home source assertion after the new ownership wrapper changed indentation. The assertion was changed to a semantic structural regex that still requires the empty-state `PageHorizontal` padding. No assertion was deleted or weakened to omit the behavior.

The directed rerun passed:

```text
GlassBackdropTopologyContractTest
HomeLayoutContractTest
ReaderChromeMaterialTest
25 tests, 0 failures
```

### 4.2 Full host gate

The requested literal `JAVA_HOME=java17` is not a valid directory in this environment. The installed Java 17 path `/usr/lib/jvm/java-17-openjdk-arm64` was used for the same Gradle tasks:

```text
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64 \
  ./gradlew :app:testQaUnitTest assembleRelease

BUILD SUCCESSFUL in 9m 22s
1350 tests / 0 failures / 0 errors / 0 skipped
```

`lintVitalAnalyzeRelease` printed the already documented Backdrop/Compose lint-registry ASM `NegativeArraySizeException`, while the configured lintVital gate, R8 minification, resource shrinking, packaging, and `assembleRelease` all completed successfully. R32.3 did not alter that frozen toolchain workaround.

## 5. Release artifact

Release signing variables were absent. A disposable PKCS12 verification signer was created under `/tmp`, injected only through the four existing signing environment variables, and securely destroyed after the build. It is not a production publishing identity.

| Field | Evidence |
|---|---|
| Package/version | `com.markleaf.app` 1.0.0 (23) |
| APK | `.external_outbox/markleaf-release-latest.apk` |
| Size | 11,660,123 bytes |
| SHA-256 | `387c0021be6a68a0156fd23481f41614e0c7583d20390f3b12eae0a1fbbc215f` |
| Signing | APK Signature Scheme v2 |
| Verification signer SHA-256 | `b1edf25780f76f464b60a78e86d30c37db938f1400e7596644604b7a54fc322b` |
| Signer DN | `CN=MarkLeaf R32.3 Build Verification, O=MarkLeaf Engineering, C=CN` |
| Download delivery | `/storage/emulated/0/Download/markleaf-release-latest.apk` |
| Download SHA-256 | `387c0021be6a68a0156fd23481f41614e0c7583d20390f3b12eae0a1fbbc215f` |

## 6. Device verification boundary

Host tests and release assembly completed before any device action. The first ADB probe returned an empty device list; the previous endpoint `198.18.0.1:45891` was no longer present. The subsequently supplied endpoint `198.18.0.1:39065` returned `Connection refused` and also did not appear in `adb devices`.

Therefore:

- install: `NOT_RUN — ADB offline`;
- Xiaomi cold-start loop: `PENDING`;
- absence of RenderThread SIGSEGV on device: **not claimed**;
- no uninstall, data clear, database mutation, or connected test was performed.

The latest verification APK is already in the device-shared Download path. Once wireless ADB is reconnected, the next gate is an in-place install followed by repeated force-stop/cold-start runs with crash-buffer and focus checks. Full R32.1 functional acceptance remains a separate follow-up after startup stability is proven.

## 7. Constraint compliance

- GlassMaterial parameters: unchanged.
- Glass Engine: retained.
- Reader Chrome behavior: retained by sibling-overlay topology and regression contracts.
- Dialog/Menu behavior: retained.
- OEM private APIs: none introduced.
- New dependencies: none.
- Test bypasses/skips/tolerance expansion: none.
- Device claim without evidence: none.

## 8. Final status

The ancestor-cycle root cause is fixed in source and protected by automated topology contracts. All host tests and the minified release build pass. The release remains blocked until the new APK completes the Xiaomi Android 16 cold-start device gate.
