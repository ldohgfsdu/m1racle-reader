# R33.1 Identity Migration Commit Finalization Report

**Date:** 2026-08-27
**Status:** `PASS / automated_verified / committed_in_this_change / release_not_entered`
**Base commit:** `b1179bc3cf4a1d053daf8b8bd8d3f2f74d31fb02`
**Commit message:** `release: migrate identity to m1racle reader`

## 1. Gate result

The R33 M1racle Reader identity migration was reviewed as one production-source
change set. It contains identity migration and the tests/documentation required
to prove that migration. It contains no feature or runtime logic change.

The containing commit is the unique R33/R33.1 identity migration commit. Its
hash is intentionally not embedded in this file because a commit cannot contain
its own final object ID.

## 2. Reviewed change scope

The rename-aware diff from the R32.6 production source commit contains:

- Kotlin package paths, declarations, imports, FQCNs, and identity-bearing symbols;
- Android `namespace`, production/QA/benchmark `applicationId`, Manifest component
  identity, QA provider authorities, and theme/application names;
- build identity: Gradle root project name, fail-closed signer variable names,
  R8 class names, artifact prefix, diagnostic property names, and scripts;
- user-visible product and brand text: MarkLeaf to M1racle Reader / M1racle;
- Room schema directory relocation required by the new database FQCN, with schema
  JSON content unchanged;
- tests migrated to the new package plus `R33ProductIdentityMigrationTest`;
- current product/operations documentation and R33/R33.1 audit reports.

Pre-R33 release and device reports retain old names, package IDs, signer DNs,
artifact paths, and hashes as historical evidence. Those references are explicitly
classified and are not active product identity.

## 3. No-logic-change evidence

All 180 production Kotlin files under `app/src/main` were audited against the
base commit. For each file, the base version was transformed in memory using only
the declared package, brand, symbol, property, and artifact-prefix mappings, then
compared byte-for-byte with the migrated file.

```text
production Kotlin identity-only comparison: 180 / 180 PASS
unexpected production Kotlin differences:   0
dependency/version catalog changes:           0
versionName/versionCode changes:               0
Room schema v6-v12 byte differences:           0
```

This comparison covers the frozen Anchor, GlassMaterial, Reader, Annotation, and
Search implementation paths as well as the remaining production code. Migration
SQL is part of the compared production Kotlin content and is unchanged apart from
the enclosing Kotlin package identity. The seven Room schema JSON files are exact
byte copies at their new FQCN-derived path.

The only non-mechanical test adjustments are repository/module-relative source
lookup candidates for source-contract tests. They replace assumptions about the
old checkout directory and do not alter production behavior or weaken assertions.

## 4. Identity checks

```text
namespace / applicationId:       com.m1racle.reader
QA applicationId:                com.m1racle.reader.qa
benchmark applicationId:         com.m1racle.reader.benchmark
root project identity:            m1racle-reader
active source/script old package: 0 references
tracked old package paths:        0
app label:                        M1racle Reader
provider authorities:             com.m1racle.reader.qa.*
release version:                  1.0.0 (23), unchanged
```

The app has no custom product URI scheme. Existing `content://` VIEW/SEND routing
remains intact, so no new deep-link protocol or navigation path was introduced.

## 5. Automated verification

The final pre-commit gate ran with JDK 17:

```text
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64 ./gradlew :app:testQaUnitTest
1355 tests / 0 failures / 0 errors / 0 skipped
```

`git diff --check` and the staged equivalent passed. The commit was made only
after the complete change set and this report were staged and reviewed.

## 6. Release boundary

- Release tag: not created.
- Production signer: not injected or used.
- Release APK/AAB: not built or generated.
- Device acceptance: not entered.
- Feature development: none.

Post-commit worktree status is clean; publication and artifact generation remain
separate, explicitly authorized future gates.
