# R33 Product Identity Full Migration Report

**Date:** 2026-08-27
**Status:** `implemented / automated_verified / release_not_built / device_not_run`
**Base commit:** `b1179bc3cf4a1d053daf8b8bd8d3f2f74d31fb02`

## 1. Result

The pre-publication product and Android/Kotlin identity has been fully migrated from MarkLeaf to M1racle Reader without adding features or changing Reader, Glass, Anchor, Annotation, Search, or persistence behavior.

| Identity | Before R33 | After R33 |
|---|---|---|
| Product | MarkLeaf / 墨阅 MarkLeaf | M1racle Reader |
| Brand | MarkLeaf | M1racle |
| Android applicationId | `com.markleaf.app` | `com.m1racle.reader` |
| Kotlin namespace | `com.markleaf.app` | `com.m1racle.reader` |
| QA applicationId | `com.markleaf.app.qa` | `com.m1racle.reader.qa` |
| Benchmark applicationId | `com.markleaf.app.benchmark` | `com.m1racle.reader.benchmark` |
| Repository/project identity | MarkLeaf | `m1racle-reader` |
| User-visible app name | 墨阅 MarkLeaf | M1racle Reader |

The source repository remains at its existing local checkout path for this session. `rootProject.name`, documentation, source paths, package declarations, artifact names, and canonical repository slug now use `m1racle-reader`. Renaming or creating a network-hosted Git repository is an external owner operation and was not attempted.

## 2. Android identity

`app/build.gradle.kts` now defines:

```text
namespace = com.m1racle.reader
applicationId = com.m1racle.reader
QA = com.m1racle.reader.qa
benchmark app = com.m1racle.reader.benchmark
```

The Macrobenchmark module namespace is `com.m1racle.reader.benchmark`, and its manifest queries the new production and benchmark packages.

Manifest and resource identity was migrated together:

- application class: `.M1racleReaderApplication`;
- launcher/activity theme: `Theme.M1racleReader`;
- app label: `M1racle Reader`;
- QA label: `M1racle Reader QA`;
- QA provider authorities:
  - `com.m1racle.reader.qa.h1fixture`;
  - `com.m1racle.reader.qa.externalopen`.

The main Manifest has no custom product URI scheme. Existing VIEW/SEND integration uses `content://` URIs and MIME filters, so R33 did not invent a new deep-link protocol. All package-targeted components, benchmark queries, intent tests, QA commands, and activity class paths now use the canonical new package.

## 3. Kotlin package migration

All Kotlin source-set directories, package declarations, imports, fully qualified names, reflective class names, and source-contract test paths moved from `com/markleaf/app` to `com/m1racle/reader`.

| Source set | Migrated Kotlin files |
|---|---:|
| `app/src/main` | 181 |
| `app/src/test` | 166 (including the new R33 contract) |
| `app/src/androidTest` | 15 |
| `app/src/debug` | 1 |
| `app/src/qa` | 7 |
| `benchmark/src/main` | 1 |

Package/path auditing found no mismatch. Empty legacy `com/markleaf` source directories were removed.

Identity-bearing Kotlin symbols were also migrated, including:

- `MarkLeafApplication` → `M1racleReaderApplication`;
- `MarkLeafTheme` → `M1racleReaderTheme`;
- `MarkLeafNavGraph` → `M1racleReaderNavGraph`;
- theme extra-color and typography symbols;
- diagnostic log tags and QA intent extras.

These are name-only changes; control flow and product behavior remain unchanged.

## 4. Build, signing, artifact, and tooling identity

Active build and tooling identities now use the M1racle brand:

- signer environment variables are `M1RACLE_RELEASE_STORE_FILE`, `M1RACLE_RELEASE_STORE_PASSWORD`, `M1RACLE_RELEASE_KEY_ALIAS`, and `M1RACLE_RELEASE_KEY_PASSWORD`;
- release signing remains fail-closed and has no debug fallback;
- R8 keep rules target `com.m1racle.reader`;
- generated artifact names use `m1racle-reader-<variant>-...apk`;
- the latest release path is `.external_outbox/m1racle-reader-release-latest.apk`;
- install, backup, restore, Perfetto, connected-test, and QA scripts target the new package;
- the private Room database filename is `m1racle-reader.db`.

Historical `.external_outbox/markleaf-*` files were not renamed or deleted: they are byte-addressed pre-R33 evidence and are not M1racle Reader production artifacts.

No signing environment was consumed. No release APK, AAB, production artifact, or release tag was generated.

## 5. Room, Anchor, Glass, Reader, Annotation, and Search safeguards

### Room

The Room schema directory moved to match the new database FQCN:

```text
app/schemas/com.m1racle.reader.data.local.AppDatabase/
```

All seven JSON files (v6–v12) retained their exact pre-R33 SHA-256 values. `MIGRATION_1_2` through `MIGRATION_11_12` SQL content has the same aggregate hash as the base commit. No schema column, table, index, foreign key, identity hash, or migration statement changed.

### Frozen runtime boundaries

For Anchor, `GlassMaterial.kt`, PDF/Reader, Annotation, Search, and their Repository/controller surfaces, the audit transformed the base-commit file in memory using only the declared package/brand mapping and compared it byte-for-byte with the current file.

```text
Anchor protocol: PASS
GlassMaterial implementation/parameters: PASS
Reader/PDF logic: PASS
Annotation architecture: PASS
Search architecture: PASS
Reading/Search Repository logic: PASS
```

There is no non-identity diff in these frozen boundaries.

## 6. Product documentation and historical evidence

Current product and operational authorities were updated:

- `AGENTS.md`;
- `README.md`;
- `CHANGELOG.md`;
- `RELEASE_CHECKLIST.md`;
- `docs/PRD.md`;
- `docs/ACCEPTANCE.md`;
- APK delivery, device install/data safety, PDF handoff, performance, and sample documents.

The initial audit found 115 documentation files containing the old brand. Stage reports, historical release readiness, old device evidence, and R22–R32 reports intentionally retain their original names, package IDs, APK paths, signer DNs, and hashes. Editing those facts would invalidate evidence.

This report is the classification authority for those remaining references: unless a file is listed above as a current authority, an old MarkLeaf reference in a pre-R33 report or historical plan means **pre-R33 historical identity**, not an active runtime or product identity. `RELEASE_CHECKLIST.md` applies the same label to R32 APK evidence.

## 7. Compatibility consequence

Android treats `com.m1racle.reader` as a different application from `com.markleaf.app`. The project had not yet shipped a production-signed public v1.0.0, so R33 does not fabricate a cross-package migration or compatibility bridge.

Existing development/RC installations under the old package are not upgraded in place and do not share app-private data with M1racle Reader. Room v12 remains the first public database baseline for the new package.

## 8. Automated verification

Added `R33ProductIdentityMigrationTest` to lock:

- applicationId/namespace/repository identity;
- Manifest application, theme, provider authorities, and app label;
- absence of legacy identity in active code/scripts;
- exact v6–v12 schema hashes;
- new fail-closed signer environment names.

Verification:

```text
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64 ./gradlew :app:testQaUnitTest
1355 tests / 0 failures / 0 errors / 0 skipped

:app:compileQaAndroidTestKotlin
PASS

:benchmark:compileBenchmarkKotlin
PASS
```

The requested JDK17 host test was executed. No device acceptance was performed because R33 changes identity rather than runtime behavior and the task did not request an APK.

## 9. Constraint compliance

- New feature: none.
- Room schema/migration content: unchanged.
- Anchor protocol: identity-only package migration.
- GlassMaterial: identity-only package/comment migration; recipe and parameters unchanged.
- Reader core behavior: unchanged.
- Search/Annotation architecture: unchanged.
- Production signer: not used.
- Production APK/AAB: not generated.
- Release tag: not created.
