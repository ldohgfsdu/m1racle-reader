# R32.6 Production Commit Preparation Report

**Date:** 2026-08-27
**Status:** **PASS — v1.0.0 production source commit prepared**
**Scope:** Source-tree consolidation only. No production APK, release signer injection, or Git tag creation.

## 1. Outcome

The complete R23–R32 production chain was reviewed as one release source set and consolidated into the first clean v1.0.0 production commit. The set includes product code, resources, Room v8–v12 schema additions, migrations, host/instrumentation tests, public documentation, release policy, and the R32.1–R32.5 evidence reports.

No APK, keystore, password, device tombstone, screenshot, raw log, Gradle cache, or other generated binary was staged. The disposable R32.3 verification APK and its hashes remain outside Git as release evidence; they were not renamed or represented as the future production artifact.

The commit hash cannot be embedded in this report without requiring a second self-referential commit. The canonical hash is the commit containing this file and is reported by `git rev-parse HEAD` in the R32.6 handoff.

## 2. Complete change-set inventory

The pre-report inventory used both authorities:

```text
git diff --name-status
git ls-files --others --exclude-standard
```

It contained 154 unique paths: 52 tracked changes and 102 individual untracked files. This report is the 155th path in the final source set.

| Classification | Final path count | Disposition |
|---|---:|---|
| Production source/resources | 64 | Commit |
| Unit/instrumentation tests | 50 | Commit |
| Room schema JSON | 5 | Commit |
| Build/release configuration | 4 | Commit |
| Product/release/evidence documentation | 30 | Commit |
| Tracked handoff state | 2 | Commit |
| Untracked binary/secret/large files | 0 | Reject if present; none found |
| Generated caches and local delivery evidence | ignored, outside source set | Preserve locally; do not commit |

Every untracked source-set file was plain text and below 1 MiB. No unexpected extension or hidden binary was found.

## 3. Files required for v1.0.0

### 3.1 Product and architecture

The commit includes the complete user-facing and architectural chain:

- H2 Reading Library/Home Hub and Bookmark/Favorite authorities;
- R23 reading assets and Reader controllers;
- R24 canonical Annotation Anchor codec/resolver;
- R25 selection-to-annotation flow;
- R26 PDF Highlight overlay and management;
- R27 Note creation/view/edit/delete loop;
- R28 annotation integrity/performance/UX contracts;
- R29 local Search index/repository;
- R30 Search UI, canonical Reader jump, and incremental index maintenance;
- R31 architecture/release contracts and obsolete authority removal;
- R32 release signing/version policy;
- R32.3 `GlassBackdropSource` topology ownership and ancestor-cycle guard.

Application wiring was checked from `MarkLeafApplication` through Repository authorities into `NavGraph`, Hub, Search, and Reader controllers. UI code does not gain a second DAO authority.

### 3.2 Room and persistence

- `AppDatabase` is version 12.
- Schema snapshots 6, 7, 8, 9, 10, 11, and 12 are present; v8–v12 are part of this commit.
- Registered migrations are continuous from 1→2 through 11→12.
- v7→8 adds reading assets, v8→9 consolidates the model, v9→10 adds Highlight/Note, v10→11 adds canonical anchor storage, and v11→12 adds the derived Search index.
- Migration, cascade, cross-document isolation, Repository authority, and schema contracts remain in the test set.
- Room v12 remains the first public database baseline; no fictional v1–v5 public schema was generated.

### 3.3 Intentional source deletions

Three tracked legacy material authorities are deleted:

```text
app/src/main/java/com/markleaf/app/ui/common/MarkLeafLiquidSurface.kt
app/src/main/java/com/markleaf/app/ui/pdf/ReaderChromeGlass.kt
app/src/main/java/com/markleaf/app/ui/pdf/ReaderChromeMaterial.kt
```

They are replaced by the single `ui/material` Glass authority. `ReaderChromeTokensContractTest` explicitly requires these old paths to remain absent, preventing a compatibility/double-authority regression.

### 3.4 Documentation and release evidence

The production source set includes README, PRD, CHANGELOG, `RELEASE_CHECKLIST.md`, and the H2/R22/R23–R32.6 reports. In particular, native-crash investigation, topology fix, R32.4 device re-acceptance, and the R32.5 production NO-GO evidence are preserved rather than discarded during cleanup.

## 4. Excluded development/local artifacts

The following are deliberately not committed:

| Category | Examples | Treatment |
|---|---|---|
| Build caches | `.gradle/`, `.kotlin/`, `build/`, `app/build/` | Ignored, reproducible |
| APK/delivery evidence | `.external_outbox/`, `dist/` | Ignored and preserved; do not confuse with source |
| Local input/backup material | `.external_inbox/`, `.local-backups/` | Ignored and preserved |
| Machine configuration | `local.properties` | Ignored |
| Tool state | `.qoder/`, `.reasonix/`, `.claude/`, `mcps/`, `ovisocr2-deploy/` | Ignored |
| Visual/manual evidence | `markleaf-platform-home.png` | Ignored and preserved |
| R32.4/R32.5 raw device logs | `/tmp/markleaf-r32-4/`, `/tmp/markleaf-r32-5/` | Outside repository; reports retain conclusions |

`.external_outbox/` was explicitly retained because it contains historical APK/hash evidence. Large ignored directories were not deleted merely to make Git appear clean; ignored build outputs do not affect source-tree cleanliness.

## 5. Temporary cleanup

One ignored file was proven to be valueless tooling noise and removed:

```text
verify-external-compile.log
```

It contained only a July Windows debug compilation transcript already superseded by current Linux/JDK17 verification. No release evidence was deleted.

## 6. Security and hygiene review

- No untracked binary or file larger than 1 MiB exists in the commit set.
- No keystore, private-key block, local properties, APK/AAB, password value, or production signer identity is staged.
- Signing references are environment-variable names and documented placeholders only.
- `.gitignore` covers `*.jks`, `*.keystore`, `*.p12`, `*.pfx`, `keystore.properties`, APKs, caches, outbox, and local backups.
- The application manifest still declares no `INTERNET` permission.
- Release debug/verbose logs are removed by the reviewed R8 `-assumenosideeffects` policy; no logging cleanup or unrelated production-code rewrite was performed here.

## 7. Pre-commit verification

The final source set passed:

```text
git diff --check
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64 \
  ./gradlew :app:testQaUnitTest --rerun-tasks :app:compileQaAndroidTestKotlin \
  --console=plain
```

Result:

```text
QA unit tests: 1350
failures: 0
errors: 0
skipped: 0
compileQaAndroidTestKotlin: PASS
```

The staged tree was then rechecked for path coverage, large files, secret material, and diff whitespace before commit.

## 8. Commit and cleanliness rule

All 155 reviewed source-set paths, including this report, belong to one production commit. After commit:

- `git status --porcelain` must be empty;
- `v1.0.0` must remain absent;
- no production signing environment is required or consumed;
- no `assembleRelease` task is run;
- the next task may build the canonical production APK only after the external signer is securely injected.

## 9. Remaining publication gates

R32.6 closes only the source-immutability prerequisite. Publication remains blocked until a later authorized gate:

1. injects the real production/upload signer outside the repository;
2. builds and verifies the canonical production APK from this exact commit;
3. records its signer certificate, manifest identity, size, and SHA-256;
4. archives the artifact and test/device evidence;
5. creates the annotated immutable `v1.0.0` tag on this commit after artifact verification and owner approval.

No tag or production APK is created by R32.6.
