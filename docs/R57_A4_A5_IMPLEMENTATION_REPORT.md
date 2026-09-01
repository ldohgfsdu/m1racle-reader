# R57 A4/A5 Atomic Integration Implementation Report

**Date:** 2026-08-31  
**Baseline:** `60f430b5642dd7857bad4646c336aba68fe33f6b`  
**Worktree:** `/root/code/MarkLeaf-r55-integration`  
**Git state:** detached HEAD; no merge, commit, tag, signing, or publication  
**Result:** IMPLEMENTED / AUTOMATED_VERIFIED  
**Canonical candidate:** NOT CREATED  
**Release state:** NOT READY / NO-GO

# Scope

R56's A4/A5 authority switch is implemented as one non-installable working-tree
unit on the clean `60f430b` integration worktree. The implementation combines:

- R55 Room v13, `MIGRATION_12_13`, alias schema, and sidecar executor;
- one application-owned sidecar migration gate;
- `ContentIdentityResolver` for all inbound source bytes;
- `DocumentRepository.commitResolvedImport` as the only ID/identity write boundary;
- shared Home SAF, ACTION_VIEW, and ACTION_SEND behavior;
- complete removal of the mutable sidecar runtime.

No A1/A2/Home visual/Dock work was imported. No UI source, resource, Manifest,
version, signing configuration, or R8 configuration changed.

# Runtime authority

The final working-tree authority is:

```text
SAF / VIEW / SEND URI
  -> SidecarIdentityMigrationV1.ensureCompleted
  -> metadata/type/size adapter
  -> ContentIdentityResolver: stage original bytes + SHA-256
  -> DocumentRepository.commitResolvedImport
       -> transactional content-hash requery
       -> reuse existing content owner, or
       -> enrich an eligible legacy null-hash alias candidate, or
       -> INSERT ABORT a new opaque UUID document
       -> bind/rebind byte-verified Room aliases
  -> navigate using committed Room documentId
```

Existing Home, Library, Search, Bookmark, and restored-viewer opens continue to use
the existing Room `documentId` directly. They do not invoke the resolver, alias
lookup, or sidecar migration.

## Shared gate

`M1racleReaderApplication` owns one process-lifetime R55 executor using the same v13
database and DAOs as the repositories. Application startup starts an opportunistic
IO warm-up. Correctness does not depend on it: the following boundaries await the
same executor again:

- Home `DocumentRepository.importDocument` before staging;
- external prepare before staging;
- external probe through `findBySourceAlias` before alias lookup;
- `commitResolvedImport` before the Room identity transaction.

Terminal `NoInput`, `Migrated`, and `Quarantined` outcomes open the gate. Retryable
or inconsistent marker failures stop the operation without reading source bytes or
falling back to an old identity path. External failure keeps the current reader
session intact and uses the existing effect channel.

If the final commit defense gate fails after a caller hands over staged content, the
prepared staging file is closed before the error propagates.

# Room and alias behavior

- Room is v13 with a registered contiguous `MIGRATION_12_13`.
- `document_aliases` uses a document FK and `ABORT` insert.
- R55 migration is insert-only and never rebinds a conflict.
- Runtime alias rebind is separate and is allowed only after current source bytes are
  staged, hashed, and the final content owner is selected inside the import commit.
- `DocumentAliasKeys` is the single formatter for `uri-v1` and
  `legacy-raw-uri-v1` keys.
- raw canonical URI keys are not stored.
- sidecar SHA is never written to `contentSha256`.
- document IDs and child rows are never merged, rewritten, or deleted by identity
  migration/commit.
- new documents receive UUID IDs; neither raw URI nor content SHA mints an ID.

The old `DocumentDao.upsert(REPLACE)` API remains for existing non-import/test setup
compatibility, but production import has zero calls to it. All identity commits use
`insertDocument(ABORT)` and targeted field updates.

# Old path removal

Removed from production runtime:

- `share/DocumentIdentityIndex.kt` in full;
- sidecar `find`, `bind`, `prune`, `rebuild`, and delete cleanup;
- `DocumentRepository.deleteById` sidecar mutation;
- importer-owned SHA hashing and sidecar lookup/write;
- raw URI `stableUriId` document creation;
- `c:<sha256>` document creation;
- `PreparedOpen.CurrentActive`, `ExistingLibrary`, and `NewContent` identity decisions;
- Home/external import calls to Room `REPLACE`;
- `DocumentIdentityIndexFileTest` and old-runtime test contracts.

The literal live sidecar path now occurs in production only inside
`SidecarIdentityMigrationV1.kt`. The R55 reader is read-only; retirement/quarantine
is owned by its lifecycle component after the appropriate durable boundary.

# Entry integration

## Home SAF

The existing launcher and HomeViewModel remain unchanged. Repository import now:

1. awaits R55;
2. preserves grant/metadata/type/size classification;
3. resolves original bytes once;
4. enters the unified commit;
5. returns the committed document.

## ACTION_VIEW and ACTION_SEND

Both actions continue through `ExternalIntentUri` and one
`ExternalDocumentImporter`. Probe aliases are advisory only. Even a same-active
candidate proceeds through prepare, actual byte hashing, reader-progress flush, and
unified commit before bring-to-front/navigation is decided.

SEND has no separate hash, ID, alias, or commit implementation.

## Existing document open

Navigation remains unchanged and direct by Room ID. The R57 source contract proves
that `NavGraph` contains no resolver, alias, or sidecar dependency.

# Automated verification

## Required gates

The required suites passed:

```text
SidecarIdentityMigrationV1Test                 14 tests PASS
MigrationV12ToV13Test                          2 tests PASS
ContentIdentityImportWiringTest                7 tests PASS
ImportAcceptanceParityGoldenTest              13 tests PASS
R37ContentIdentityAuthorityContractTest         PASS
R57AtomicIdentityAuthorityContractTest         5 tests PASS
ContentIdentityResolverTest                     PASS
```

The integration tests cover:

- missing/empty/valid/corrupt/unknown sidecar;
- Path A, Path B, orphan, alias conflict, idempotence, and recovery;
- v12→v13 document/child preservation and collision rollback;
- Home SAF, ACTION_VIEW, and ACTION_SEND unified resolver/commit behavior;
- identical bytes across different URIs;
- UUID-only new IDs and zero import REPLACE calls;
- bookmark/highlight/note/search/anchor preservation on duplicate import;
- U1/K/U2: migrated Path A alias + changed URI reuses the original document and
  preserves bookmark/highlight/note/anchor ownership;
- inconsistent sidecar marker state blocks Home and external import before source
  stream access;
- static absence of mutable sidecar runtime and derived-ID creation;
- existing-document navigation remains direct by Room ID.

## Full regression

```text
./gradlew :app:testQaUnitTest
BUILD SUCCESSFUL
1385 tests, 0 failures, 0 errors, 0 skipped

./gradlew :app:compileQaAndroidTestKotlin
BUILD SUCCESSFUL
```

Linux aarch64 cannot load Robolectric's native Room SQLite runtime for a real Room
transaction in the U1/K/U2 host test. That host integration case therefore uses
R55's transaction-store seam. Production uses `RoomDatabase.withTransaction`; R55
separately verifies rollback/idempotence, and the exact v12→v13 SQL constants execute
against host SQLite JDBC. A populated-v12 real-device upgrade remains required before
release and is not claimed here.

# Boundary audit

Compared with `60f430b`:

- `app/version.properties`: byte-identical;
- `app/build.gradle.kts`: byte-identical;
- `app/proguard-rules.pro`: byte-identical;
- UI source diff: none;
- resource diff: none;
- Manifest diff: none;
- signing changes: none;
- release APK: not generated;
- tag/canonical commit: not created.

No `assemble*` task was executed, so the mandatory APK delivery flow was not
triggered.

# Change inventory

Production/data implementation:

- `M1racleReaderApplication.kt`
- `data/local/AppDatabase.kt`
- `data/local/DocumentAlias.kt`
- `data/local/DocumentDao.kt`
- `data/repository/DocumentRepository.kt`
- `identity/ContentIdentityResolver.kt`
- `identity/SidecarIdentityMigrationV1.kt`
- `share/ExternalDocumentImporter.kt`
- `share/ExternalOpenCoordinator.kt`
- `share/ExternalOpenModels.kt`
- exported Room schema `13.json`
- deleted `share/DocumentIdentityIndex.kt`

Identity/migration verification includes R55 tests, v12→v13 host SQLite tests,
resolver/import parity tests, external session tests, and R37/R57 authority contracts.

# Final status

- R57 implementation worktree: **IMPLEMENTED / AUTOMATED_VERIFIED**
- R55 production wiring in this worktree: **COMPLETE**
- old sidecar runtime: **REMOVED**
- canonical commit: **NOT CREATED**
- device upgrade verification: **PENDING**
- release APK/signing/tag/publication: **NOT PERFORMED**
- release readiness: **NOT READY / NO-GO**

Stop here. Do not continue into A1/A2/UI integration or release execution.
