# MarkLeaf D-1.4 Content Identity Resolver Report

## 0. Result

| Field | Value |
|---|---|
| Date | 2026-08-25（UTC+8） |
| Release baseline | `v0.1.0-c-local` → `604d404978b5f09fdb4a9c825259ef6793f59949` |
| Database baseline | Room v7; nullable `contentSha256` available |
| Implementation commit | `d03d8ce799f07b3b936bd0db54ac2f86ed8c262c` |
| Entry-point migration | Not performed |
| Room schema / UI / release assets | Unchanged |
| Status | **IMPLEMENTATION_BATCH — RESOLVER_CORE_COMPLETE** |

D-1.4 adds the shared source-byte identity resolver only. It is intentionally not called by FAB/SAF, VIEW, SEND, or ZIP routing in this batch.

## 1. Implemented resolver contract

Added `ContentIdentityResolver` under `com.markleaf.app.identity`.

Input:

```text
ContentIdentityInput
  sourceUri: Uri
  metadata: ResolvedExternalMetadata
  maximumBytes: Long?   // caller-owned intake policy
```

Pipeline:

```text
Uri + resolved metadata
  -> metadata size-hint guard when a maximum is supplied
  -> unique app-private .importing staging file
  -> one streaming copy pass
       byte count + optional actual-byte limit + SHA-256 + disk write
  -> fsync and staged-length validation
  -> DocumentDao.getByContentSha256(hash)
  -> ExistingDocumentFound / NoExistingDocument
```

The resolver does not determine a document id, insert/update/delete a Room row, merge metadata, bind aliases, close a reader, navigate, or parse/extract ZIP content.

### 1.1 Decision model

```text
ContentIdentityDecision.ExistingDocumentFound(document)
  -> exact content already has a Room owner; later commit code may evaluate merge state

ContentIdentityDecision.NoExistingDocument
  -> no exact content owner exists; later commit code may create one
```

The result is a candidate decision, not a database mutation. A future commit layer must requery inside its transaction to handle concurrent imports.

### 1.2 Staged result ownership

A successful `Resolved` result contains:

- original `Uri` and resolved metadata;
- staged app-private source file;
- actual copied byte count;
- lowercase SHA-256;
- identity decision.

`PreparedContentIdentity` implements `Closeable`. Until an entry pipeline owns the commit lifecycle, callers/tests must close it to delete the staged source. Failed, too-large, and cancelled operations clean their partial stage internally.

## 2. Streaming SHA-256 behavior

- SHA-256 is updated from each copied chunk; the complete source is never loaded into a byte array.
- The copy buffer is fixed at 64 KiB.
- Source bytes are written and hashed in the same pass.
- Actual byte count, not provider metadata, is authoritative after staging.
- The optional `maximumBytes` comes from the caller rather than duplicating the current FAB/VIEW/SEND size-policy constant inside the resolver.
- A metadata hint above the supplied maximum is rejected before stream opening; an unknown or inaccurate hint is bounded by actual copied bytes.
- The staged file is fsynced and its filesystem length is compared with the copied count before success.
- File and content URIs are supported by the production source opener.

### Cancellation

Staging runs through coroutine `runInterruptible(Dispatchers.IO)`. The loop also checks the thread interrupt state between reads.

On coroutine cancellation:

1. the blocking staging thread is interrupted;
2. input/output streams close through `use`;
3. the incomplete `.importing` file is deleted in `finally`;
4. `CancellationException` is rethrown rather than converted to a generic failure.

An ordinary source/read/write error returns `Failed(cause)` after partial-file cleanup.

## 3. Room lookup

Added one indexed DAO query:

```sql
SELECT * FROM documents
WHERE contentSha256 = :contentSha256
LIMIT 1
```

It consumes the unique v7 index added by D-1.3. A legacy row with `contentSha256 IS NULL` cannot match an incoming non-null digest and therefore yields `NoExistingDocument` at resolver level.

The DAO addition changes no table, column, index, schema version, or exported schema identity hash. `app/schemas/.../7.json` has no diff.

## 4. Contract test coverage

Added `ContentIdentityResolverTest` with seven executable contracts:

| Case | Result |
|---|---|
| Same bytes, different Uri | PASS — identical lowercase SHA-256 and create candidate |
| Different bytes, same filename | PASS — different SHA-256 values; filename does not decide identity |
| Same hash already in Room lookup | PASS — `ExistingDocumentFound` returns the existing entity |
| Existing NULL-hash document | PASS — no false match; returns `NoExistingDocument` |
| Interrupted hashing | PASS — typed failure and no partial stage remains |
| Coroutine cancellation | PASS — cancellation propagates and no partial stage remains |
| Large generated stream | PASS — 16 MiB copied/hashed with read requests bounded to 64 KiB |

The large-stream fixture generates bytes on demand and does not allocate a 16 MiB source array. The expected digest is independently calculated in bounded chunks.

## 5. Verification evidence

Environment: JDK 17 at `/usr/lib/jvm/java-17-openjdk-arm64`.

### Resolver contracts

```text
./gradlew :app:testQaUnitTest \
  --tests com.markleaf.app.identity.ContentIdentityResolverTest

7 tests / 0 failures / 0 errors / 0 skipped
BUILD SUCCESSFUL
```

### Full QA unit suite and Android test-source compatibility

```text
./gradlew :app:testQaUnitTest :app:compileQaAndroidTestKotlin

1169 tests / 0 failures / 0 errors / 0 skipped
compileQaAndroidTestKotlin PASS
BUILD SUCCESSFUL
```

No `assemble*` task or device action was performed.

## 6. Scope audit

Changed production code is limited to:

- new `ContentIdentityResolver.kt` and its models;
- one `DocumentDao.getByContentSha256` query.

Existing fake/delegating DAOs were updated mechanically for the new interface method. No production caller was migrated.

Confirmed unchanged:

- FAB/SAF import;
- VIEW/SEND external-open import;
- `ExternalDocumentImporter` and `ExternalOpenCoordinator`;
- `DocumentRepository`;
- ZIP detection, extraction, and routing;
- `DocumentEntity`, `AppDatabase`, Room version, migration, and schema JSON;
- sidecar identity index;
- UI, strings, navigation, version metadata;
- release tag and canonical release APK.

## 7. Intentional pending work

The following remain outside D-1.4:

- Resolver construction/wiring in any import entry point.
- Persisting `contentSha256` to new or legacy rows.
- Transactional revalidation, create, or merge.
- Location aliases and sidecar retirement.
- Shared 200 MiB policy wiring; future callers must pass the existing intake limit to `maximumBytes`.
- ZIP extraction from the staged source.
- Concurrent insert conflict handling and replacement of identity writes that currently use `REPLACE`.

Therefore D-1.4 completes the reusable identity-resolution core but does not claim that cross-entry ZIP identity is fixed.

## 8. Final status

**IMPLEMENTATION_BATCH — RESOLVER_CORE_COMPLETE**
