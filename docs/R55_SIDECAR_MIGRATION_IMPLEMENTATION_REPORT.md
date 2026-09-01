# R55 Sidecar Identity Migration Implementation Report

**Date:** 2026-08-31  
**Baseline:** `60f430b5642dd7857bad4646c336aba68fe33f6b`  
**Integration worktree:** `/root/code/MarkLeaf-r55-integration`  
**Git state:** detached HEAD; no merge, cherry-pick, commit, tag, or canonical candidate  
**Release state:** NOT READY / NO-GO  
**R55 scoped result:** IMPLEMENTED / AUTOMATED_VERIFIED

## Scope result

R54's one-time v1 sidecar migration component is implemented on an isolated clean
`60f430b` worktree. The change contains the Room v13 alias prerequisite, a pure
read-only v1 sidecar reader, a single Room alias transaction, deterministic
retirement/quarantine handling, and recovery tests.

No UI, Hub, Dock, signing, manifest, versionCode/versionName, release workflow, or
release APK was changed. A1/A2 and all UI integration were intentionally not started.

This R55 worktree is an implementation/review unit, not a canonical or releasable
candidate. Production entry-point activation remains part of the later atomic A4/A5
integration: the v12 baseline still contains the historical runtime identity path,
so wiring this executor there without simultaneously removing that path would create
a forbidden dual authority. R55 therefore does not claim production/device
activation.

## Implemented boundary

### Room v13 alias prerequisite

- `AppDatabase` advances from v12 to v13 and registers `MIGRATION_12_13`.
- `document_aliases` is additive and has an `ABORT` DAO insert.
- schema snapshot `13.json` is exported.
- the v12 document IDs and all child-table foreign keys remain unchanged.
- no destructive migration fallback is added.

The SQL migration's frozen Path B hash backfill happens before the sidecar executor.
The sidecar executor itself never writes `contentSha256`.

### Pure read-only reader

`SidecarIdentityV1Reader` reads one byte snapshot and returns a typed result:

| Input | Result | Database action | File action |
|---|---|---|---|
| absent | `Missing` | none | none |
| v1 with empty/valid entries | `Valid` | one alias transaction | retire after commit |
| malformed/empty/oversized | `Corrupt` | none | quarantine untouched source |
| version other than 1 | `UnknownVersion` | none | quarantine untouched source |

The reader has no reference or call to the historical mutable loader and performs no
rename, quarantine, rewrite, or delete. The maximum accepted snapshot is 16 MiB.

### SidecarIdentityMigrationV1

The implemented order is:

```text
Room v13 available
  -> inspect deterministic marker state
  -> read immutable sidecar snapshot
  -> validate entries in file order
  -> AppDatabase.withTransaction { read document/alias; insert alias with ABORT }
  -> atomic same-filesystem retire
```

Corrupt and unknown-version input bypass Room and is atomically quarantined. A
process-local mutex joins concurrent calls, and only terminal outcomes are cached.
An exception leaves the live sidecar as the retry trigger.

### Alias safety rules

- alias key: `uri-v1:` + lowercase SHA-256 of the in-memory canonical external key;
- raw canonical keys are not stored in Room, filenames, or reports;
- Path A accepts only an existing 64-lowercase-hex document ID and never treats the
  sidecar SHA as content authority;
- Path B accepts only `c:<64 lowercase hex>` with a matching sidecar SHA suffix;
- orphan entries are skipped;
- an existing alias for the same document is an idempotent no-op;
- an existing alias for a different document is recorded as a conflict and preserved;
- inserts use `OnConflictStrategy.ABORT`, never `IGNORE` or `REPLACE`.

The transaction exposes no document or child-table mutation operation. It performs no
document insert/update/delete, no `contentSha256` write, no document-ID rewrite, no
merge, and no alias rebind.

## Recovery behavior

| Failure point | Observed recovery contract |
|---|---|
| before/inside Room transaction | transaction rolls back; live sidecar remains; retry allowed |
| second alias insert after first insert | earlier insert rolls back with the transaction |
| after DB commit, before retirement | live sidecar remains; retry sees same-document alias and retires without duplicate write |
| corrupt/unknown input | source is preserved under deterministic quarantine name; no DB transaction |
| retired marker on a later executor | reports completed without reopening the old runtime path |
| live plus marker or multiple markers | fail closed; no overwrite/delete |

Retired and quarantine filenames include the SHA-256 of the exact source snapshot.
Destination artifacts are never overwritten.

## Automated verification

### R55 matrix

`SidecarIdentityMigrationV1Test` contains 14 tests:

| Required case | Evidence |
|---|---|
| missing sidecar | no transaction and no file mutation |
| empty sidecar | zero-write transaction then retirement |
| valid entry | Path A/Path B successful alias insertion |
| corrupt JSON | quarantine, zero DB transactions |
| unknown version | quarantine, entries not interpreted |
| Path A | SHA ignored as content evidence; document unchanged |
| Path B | suffix match required; content hash unchanged |
| orphan document | skipped; document not recreated |
| alias conflict | existing binding and both documents preserved |
| repeated run | cached and new-executor idempotence |
| failure recovery | pre-transaction, mid-transaction, and commit-before-rename recovery |
| U1/K/U2 | canonical bridge resolves independently of raw URI hash |

`MigrationV12ToV13Test` adds two host-SQLite tests proving:

- documents, IDs, bookmarks, highlights, and notes survive v12→v13;
- a detected Path B hash collision aborts and rolls back the entire schema migration.

### Commands and results

```text
./gradlew :app:testQaUnitTest
BUILD SUCCESSFUL
1371 tests, 0 failures, 0 errors, 0 skipped

./gradlew :app:compileQaAndroidTestKotlin
BUILD SUCCESSFUL
```

The configured Android SDK and JDK 17 were used. Android-test sources compile; no
instrumented/device run was claimed. The host is Linux aarch64, where Robolectric's
native Room SQLite runtime is unavailable. Accordingly, sidecar orchestration uses a
transactional fake seam in host tests, production code uses `RoomDatabase.withTransaction`,
and the exact v12→v13 SQL constants run separately against real host SQLite JDBC.

## Change inventory

Production/data files:

- `app/src/main/java/com/m1racle/reader/data/local/AppDatabase.kt`
- `app/src/main/java/com/m1racle/reader/data/local/DocumentAlias.kt`
- `app/src/main/java/com/m1racle/reader/identity/SidecarIdentityMigrationV1.kt`
- `app/schemas/com.m1racle.reader.data.local.AppDatabase/13.json`

Tests/contracts:

- `app/src/test/java/com/m1racle/reader/identity/SidecarIdentityMigrationV1Test.kt`
- `app/src/test/java/com/m1racle/reader/data/local/MigrationV12ToV13Test.kt`
- v13 pins in R31/R32/R33 and `ReadingModelAuthorityTest`

## Final status

R55's scoped sidecar migration implementation is complete and host-verified.

- canonical commit: **NOT CREATED**
- release APK: **NOT GENERATED**
- signing: **UNCHANGED**
- device verification: **PENDING future canonical A4/A5 candidate**
- production identity-entry wiring and old-runtime removal: **PENDING atomic A4/A5 integration**

Stop here. Do not proceed to A1/A2/UI integration or release execution.
