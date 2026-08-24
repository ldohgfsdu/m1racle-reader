# MarkLeaf D-1.3 Identity Schema Migration Report

## 0. Result

| Field | Value |
|---|---|
| Date | 2026-08-25（UTC+8） |
| Release baseline | `v0.1.0-c-local` → `604d404978b5f09fdb4a9c825259ef6793f59949` |
| Input decisions | D-1.1 architecture decision; D-1.2 implementation plan |
| Implementation commit | `dd3497b223903cbff497e91eae076d82d700e3c7` |
| Room migration | v6 → v7 |
| Import/UI behavior | Unchanged |
| Release tag / canonical APK | Unchanged |
| Status | **IMPLEMENTATION_BATCH** |

D-1.3 implements only the durable nullable content-hash slot and its uniqueness constraint. It does not populate hashes or unify any import entry point.

## 1. Implemented schema

### 1.1 DocumentEntity

`DocumentEntity` now includes:

```text
contentSha256: String? = null
```

The field is nullable to preserve all existing v6 rows without inventing content identity. The default also keeps every existing constructor and current import path source-compatible; until a later authorized identity pipeline writes a proven hash, newly imported rows also remain null.

No primary key, existing column, child foreign key, document row, reading state, or app-private path is changed.

### 1.2 Room version and migration

`AppDatabase` advances from version 6 to 7 and registers `MIGRATION_6_7` in the production database builder.

The migration executes only:

```sql
ALTER TABLE documents ADD COLUMN contentSha256 TEXT;
CREATE UNIQUE INDEX IF NOT EXISTS index_documents_contentSha256
    ON documents(contentSha256);
```

It performs no `UPDATE`, backfill, table rebuild, primary-key rewrite, or `DELETE`. Existing rows therefore receive SQL `NULL` automatically.

Room schema v7 was exported to:

```text
app/schemas/com.markleaf.app.data.local.AppDatabase/7.json
identityHash=b23d7a73d5c716880279355e53f470b2
```

The exported schema records `contentSha256` as nullable `TEXT` and `index_documents_contentSha256` as unique.

## 2. Index decision

Decision: use a normal Room unique index, **not** a partial unique index.

Reasons:

- SQLite treats NULL values as distinct for a UNIQUE index, so multiple legacy NULL rows are permitted.
- Non-null duplicate hashes are rejected by the database.
- Room's `@Index(value = ["contentSha256"], unique = true)` expresses and exports the constraint directly.
- A partial predicate `WHERE contentSha256 IS NOT NULL` would not change the required NULL behavior and would add manual schema complexity without benefit.

The real-SQLite migration test verifies both sides: three NULL rows coexist, while a second equal non-null hash fails and leaves the original owner unchanged.

## 3. Migration test coverage

### 3.1 Executable host migration suite

Added `MigrationV6ToV7Test`, which creates a real v6 SQLite database file, executes the exact SQL constants used by `MIGRATION_6_7` in a transaction, and verifies:

| Case | Result |
|---|---|
| Empty v6 database upgrade | PASS — nullable column and unique non-partial index exist |
| Existing DocumentEntity upgrade | PASS — all seeded fields retained and hash is NULL |
| Multiple NULL hashes | PASS — three NULL document rows coexist |
| Existing data integrity | PASS — document/PDF viewport, print-page anchor, and outline node retained |
| Duplicate known hash | PASS — second equal non-null value rejected by UNIQUE index |

The migration SQL is shared between production `MIGRATION_6_7` and the host tests to prevent a copied test script from drifting away from production.

The host suite uses `org.xerial:sqlite-jdbc:3.45.3.0` as a test-only dependency. It is not present on production/runtime configurations and does not enter the APK.

### 3.2 Existing migration chain

The historical v5 → v6 Android migration test was adjusted to open the current v7 `AppDatabase` through `MIGRATION_6_7` and assert the new field remains null. Android test source compilation is green.

No ADB target was attached, so instrumented migration execution was not claimed. This does not affect the host migration PASS result; the device-only evidence boundary is recorded honestly.

## 4. Verification evidence

Environment: JDK 17 at `/usr/lib/jvm/java-17-openjdk-arm64`.

### Targeted migration/contracts

```text
./gradlew :app:testQaUnitTest \
  --tests com.markleaf.app.data.local.MigrationV6ToV7Test \
  --tests com.markleaf.app.data.local.DocumentMigrationSqlTest

13 tests / 0 failures / 0 errors / 0 skipped
BUILD SUCCESSFUL
```

### Full QA unit suite

```text
./gradlew :app:testQaUnitTest

1162 tests / 0 failures / 0 errors / 0 skipped
BUILD SUCCESSFUL
```

### Android test source compilation

```text
./gradlew :app:compileQaAndroidTestKotlin

BUILD SUCCESSFUL
```

Only pre-existing Compose test API deprecation warnings were emitted.

No `assemble*` task was run, in accordance with the explicit APK boundary.

## 5. Scope audit

Changed production files are limited to:

- `DocumentEntity.kt` — nullable field and unique Room index declaration;
- `AppDatabase.kt` — version 7, migration SQL, and migration registration.

Other changes are the exported schema, migration tests, compatibility adjustment to the older migration test, and a test-only SQLite dependency.

Confirmed unchanged:

- `DocumentRepository` and all import pipeline code;
- FAB/SAF, VIEW, and SEND entry points;
- `ExternalDocumentImporter`, identity resolver behavior, and sidecar behavior;
- ZIP extraction/commit behavior;
- DAO lookup/upsert behavior;
- UI, strings, navigation, versionCode/versionName;
- release tag and release APK.

## 6. Expected pending work

These items remain intentionally pending and do not block D-1.3:

- No row is backfilled with a content hash.
- Current import paths do not compute or persist `contentSha256`.
- Existing duplicates are not detected or merged.
- Location-alias persistence is not implemented.
- `DocumentIdentityIndex` is not migrated or retired.
- `DocumentDao.upsert(REPLACE)` remains unchanged; a later identity-write batch must replace it before non-null hash conflicts can occur through production imports.
- Cross-entry ZIP identity remains behaviorally unchanged until the separately authorized pipeline/resolver batch.

## 7. Final status

D-1.3 provides the v7 database foundation without changing import behavior or release history.

**IMPLEMENTATION_BATCH**
