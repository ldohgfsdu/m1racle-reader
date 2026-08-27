# R31 Architecture Audit & Release Preparation Report

**Date:** 2026-08-26
**Scope:** Architecture audit and release preparation only; no user feature was added.
**Frozen boundaries honored:** `GlassMaterial`, canonical Anchor protocol, and Reader core behavior were not modified.
**Acceptance boundary:** Host automation and release artifact inspection only; no device installation or device acceptance was performed.

## 1. Executive conclusion

R31 closes the stage as an **internal, host-verified release candidate**, not as a public/store-ready release.

- The app remains a package-layered Android monolith with a one-way dependency from the `benchmark` module to `app`. Core/data packages do not import application UI, and UI does not import Room DAOs.
- Room is at schema version 12. The registered migration chain is contiguous from 1 to 12 and does not use destructive fallback. Committed JSON snapshots cover versions 6 through 12.
- Repository boundaries remain the runtime source of truth. Search index rows are derived data; Document, Highlight, and Note entities remain authoritative.
- Two confirmed zero-reference compatibility remnants were removed. Release R8 now strips debug/verbose logging, and generated release history is bounded to five timestamped APKs per variant.
- `:app:testQaUnitTest`, QA AndroidTest source compilation, release lint, and minified release assembly pass on JDK 17.
- Public release is blocked by the unresolved production signing/version decision and by the Backdrop-driven Compose/toolchain compatibility debt described below.

## 2. Delivery state

| Area | State | Evidence |
|---|---|---|
| Architecture and data audit | `implemented` | Source/import scan, Room registration/schema inspection, repository call-chain inspection |
| R31 contract tests | `automated_verified` | 5 new architecture/release contracts |
| Full QA unit suite | `automated_verified` | 1339 tests, 0 failures, 0 errors, 0 skipped |
| QA instrumentation source | `compile_verified` | `:app:compileQaAndroidTestKotlin` passed |
| Release lint | `automated_verified_with_declared_gap` | 0 errors, 167 warnings; incompatible Compose runtime lint registry excluded |
| Minified release APK | `release_build_verified` | R8/resource shrink and `assembleRelease` passed |
| Device acceptance | `not_run` | Explicitly excluded by R31 |
| Public/store release | `pending` | Production signer and release identity not selected; dependency/toolchain debt remains |

## 3. Module boundaries and dependency direction

### 3.1 Current topology

- Gradle modules: `app` and `benchmark`; `benchmark` consumes `app`, with no reverse dependency.
- The application remains package-layered inside `app` rather than split into speculative Gradle modules.
- `data`, `markdown`, `packageimport`, `pdf`, and `share` contain no imports from `com.markleaf.app.ui.*`.
- Application UI contains no imports of `*Dao`; production repositories are constructed and connected in `MarkLeafApplication`.

This is an acceptable boundary for the current product size. A multi-module refactor would add release risk without creating a second real deployment/reuse boundary, so R31 does not introduce one.

### 3.2 Non-blocking coupling findings

- Annotation UI reuses `BookmarkRowFormatter` from the hub package.
- Hub UI reuses Home design tokens.
- Reader controllers reference annotation UI state/types.
- `DocumentRepository` owns several related but broad responsibilities: import/document lifecycle, outline, and anchor-associated operations.

These are horizontal package couplings, not data-layer inversions. They are recorded for future work but deliberately not refactored in R31 because doing so would touch frozen Reader/Anchor behavior without a current functional defect.

## 4. Deprecated code and dead paths

Repository-wide reference inspection confirmed two unused compatibility-only members:

- `PdfRenderQuality.MAX_RENDER_WIDTH_PX`
- `MarkdownTableLayoutTokens.maximumWideCompressionFraction`

Both were deleted and are protected by an R31 contract test. No `Legacy*` runtime bridge, dual DAO/repository path, or destructive migration fallback was found in the audited annotation/search flow.

This finding does not claim that every large UI file is minimal. The largest PDF screen/ViewModel files remain maintainability and recompilation-cost hotspots, but splitting them during a release audit would be an unrelated Reader refactor.

## 5. Room schema and migration chain

### 5.1 Verified structure

- Current database version: **12**.
- Registered migrations: `1→2→3→4→5→6→7→8→9→10→11→12`.
- No destructive fallback is registered.
- Committed Room schema snapshots: **6.json through 12.json**.
- R31 adds a host contract that checks both migration contiguity and the committed snapshot set.

Existing host tests exercise the real SQL contracts from 6 through 12. Earlier 1 through 5 transitions retain source/version contract coverage. The v5→v6 full Room migration test and transaction/cascade instrumentation tests compile, but R31 did not execute them on a device.

### 5.2 Residual migration risk

Snapshots 1 through 5 were never exported and cannot be reconstructed reliably from the current KSP build. Therefore a complete `MigrationTestHelper` proof from every historical version is unavailable. Before a public release that promises preservation of those installations, run the existing migration/cascade instrumentation suite on a device and retain a real pre-v6 database fixture if such an install base exists.

## 6. Repository single source of truth

- UI calls repositories/state holders and never accesses Room DAOs directly.
- Highlight and Note remain associated through canonical `documentId` and Anchor data; R31 does not alter that protocol.
- Document deletion is the lifecycle authority. Foreign-key cascade and repository transaction contracts cover associated reading/annotation/search data.
- `SearchIndexEntity` is explicitly a derived projection. Import, Highlight, and Note repository writes maintain it; consumers query only through `SearchRepository`.
- No second in-memory or UI-owned authoritative copy of annotation/search state was found.

The broad `DocumentRepository` surface is a maintainability concern, but splitting it now would create a second authority or a compatibility bridge unless all callers moved atomically. R31 leaves the working authority intact.

## 7. Tests and documentation consistency

### 7.1 New R31 contracts

`R31ArchitectureReleaseContractTest` enforces:

1. core/data packages cannot import internal application UI;
2. UI cannot import DAOs and production repository wiring remains present;
3. Room migrations remain contiguous through v12 and schemas 6–12 remain committed;
4. release minify/resource shrink, debug-log stripping, and bounded outbox history stay enabled;
5. deleted compatibility-only members do not return.

### 7.2 Documentation corrections

- README and PRD now distinguish delivered local search of Document title/Highlight/Note from document-body full-text indexing, which remains out of scope.
- CHANGELOG records R23–R31 as unreleased work without inventing a release version.
- APK delivery documentation records the five-build per-variant retention policy.

The historical `docs/RELEASE_READINESS.md` remains a dated July snapshot and must not be treated as the current release decision.

## 8. Release build, R8, and resources

### 8.1 Build configuration

- Release `minifyEnabled=true` and `shrinkResources=true` remain enabled.
- R8 now removes `android.util.Log.d` and `Log.v` calls while preserving warning/error diagnostics.
- Known diagnostic markers `ZOOM_QUALITY_TARGET`, `TILE_FRAME_METRIC`, and `PDF_SYSBARS` are absent from the final DEX.
- Broad keep rules for Backdrop and PDFBox remain. They reduce optimization opportunity but avoid changing frozen rendering/material behavior during this audit.
- Manifest inspection shows no `INTERNET` permission. The only listed permission is Android's package-scoped dynamic-receiver permission.

### 8.2 Artifact evidence

- APK: `.external_outbox/markleaf-release-latest.apk`
- Size: **11,660,127 bytes**
- SHA-256: `8dd83553464bc876e4f6c53d66db32de6280893275fd2aac783a157ba38b8e29`
- Package: `com.markleaf.app`
- Version currently encoded: `versionCode=22`, `versionName=0.1.0`
- Signature: APK Signature Scheme v2, one signer, **Android Debug** certificate
- R8 outputs: mapping 50,580,673 bytes; usage 5,514,760 bytes; seeds 1,542,569 bytes; configuration 50,456 bytes

Compressed payload approximation from the APK central directory:

| Category | Compressed bytes |
|---|---:|
| DEX | 5,245,592 |
| Other/manifest/signing metadata | 4,183,531 |
| Assets | 1,624,464 |
| Android resources | 423,835 |
| Native libraries | 60,292 |

### 8.3 Generated artifact retention

Before R31, `.external_outbox` held about **7.0 GiB**, including 168 timestamped release APKs. The successful release export removed 163 old release APKs and their matching checksums, retaining the newest five; total outbox size fell to about **5.3 GiB**. These were generated, gitignored artifacts and are recoverable only by rebuilding the corresponding source revision. The latest APK and five recent release histories remain.

QA/debug historical artifacts still account for most of the remaining outbox footprint. The new per-variant policy will bound each variant when that variant is next assembled; R31 does not bulk-delete unrelated variant history.

## 9. Toolchain compatibility and lint coverage gap

Backdrop 2.0.0 forces Compose UI/runtime 1.11.1 and Lifecycle 2.9.4 above the project's declared Compose BOM/toolchain generation. Current build workarounds also skip Kotlin metadata checks and disable the AAR metadata gate because Backdrop declares `minCompileSdk=37` while the available build uses compileSdk 36.

The Compose runtime 1.11.1 lint registry is compiled against a newer Kotlin Analysis API ABI than AGP 8.7.3 supplies. It crashes lint with `IncompatibleClassChangeError` in both unit-test and production source analysis. R31 therefore excludes all 16 rules from that incompatible registry, plus the pre-existing incompatible Lifecycle rule, while leaving every other release lint registry active. The excluded Compose rules include naming, remember/state, coroutine/flow, autoboxing, and mutable-state checks.

This is a declared static-analysis gap, not a clean dependency state. The durable fix is one coordinated Kotlin/AGP/KSP/compileSdk/Compose/Backdrop alignment, followed by removal of the metadata/AAR/lint exclusions. Such an upgrade changes the Glass dependency environment and is outside R31's frozen scope.

## 10. Risk register and release decision

| Priority | Finding | Release effect | Required action |
|---|---|---|---|
| P0 | Release APK uses Android Debug signer | Blocks public/store release and stable upgrade identity | Select and protect a production signing key/configuration |
| P0 | R23–R31 work still reports v0.1.0 / code 22 | Release identity is ambiguous | Choose versionCode/versionName as an explicit release decision |
| P1 | Backdrop/Compose/toolchain ABI mismatch; metadata/AAR gates and Compose lint registry excluded | Build succeeds, but compatibility and static-analysis coverage are incomplete | Perform one coordinated dependency/toolchain alignment, then remove all workarounds |
| P1 | Room snapshots begin at v6; full migration/cascade instrumentation not device-run in R31 | Old-install preservation is not fully evidenced | Run real migration/cascade tests before promising public upgrade support |
| P2 | Horizontal UI coupling and broad `DocumentRepository` | Maintainability risk, no current runtime inversion | Revisit only with an atomic boundary task and real second use case |
| P2 | Remaining QA/debug history keeps outbox at ~5.3 GiB | Host disk usage | Let per-variant pruning run on future assemblies or authorize a separate cleanup |
| P2 | Broad Backdrop/PDFBox R8 keeps and large PDF UI files | Larger mapping/APK and slower R8/recompilation | Measure before narrowing; do not change frozen renderer/material paths during release prep |

**Decision:** suitable for internal distribution and host-side release candidate review. **Not ready for public/store release** until P0 items are resolved; P1 items require explicit acceptance or remediation.

## 11. Verification commands

All commands used JDK 17 and wrote detailed logs under `/tmp`.

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64 ./gradlew \
  :app:testQaUnitTest :app:compileQaAndroidTestKotlin

JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64 ./gradlew :app:lintRelease

JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64 ./gradlew :app:assembleRelease
```

Results:

- Unit tests: **1339 / 0 failures / 0 errors / 0 skipped**
- QA AndroidTest Kotlin compilation: **PASS**
- Release lint: **PASS, 0 errors / 167 warnings**, subject to the declared Compose registry exclusion
- Release assembly/R8/resource shrink: **PASS**
- Required delivery copy: `/storage/emulated/0/Download/markleaf-release-latest.apk`
- Device install, launch, interaction, performance, and migration acceptance: **NOT RUN**
