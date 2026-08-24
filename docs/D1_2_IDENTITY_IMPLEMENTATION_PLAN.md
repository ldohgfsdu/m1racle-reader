# MarkLeaf D-1.2 ZIP / Document Identity Implementation Plan

## 0. Plan status

| Field | Value |
|---|---|
| Date | 2026-08-25（UTC+8） |
| Release baseline | `v0.1.0-c-local` → `604d404978b5f09fdb4a9c825259ef6793f59949` |
| Architecture input | D-1.1 **DESIGN_ONLY COMPLETE** |
| Planned target | Post-release v0.1.1 candidate; implementation requires a separate explicit objective |
| Production code / Room schema / tests | Unchanged by D-1.2 |
| Release tag / APK | Unchanged by D-1.2 |
| Final status | **DESIGN_ONLY** |

This document converts the D-1.1 architecture decision into an ordered implementation and verification plan. It does not authorize or contain implementation changes.

## 1. Intended outcome and non-negotiable invariants

The future implementation is complete only when:

1. FAB/SAF, VIEW, and SEND use the same source-byte hashing and Room resolution path.
2. Exact original source bytes have one Room content identity regardless of URI or entry action.
3. `DocumentEntity.id` remains a stable opaque entity key and is never rewritten during migration.
4. `documents.contentSha256` is the only content-hash authority and is unique when non-null.
5. URI-derived values exist only as source aliases, not document or content identity.
6. Every new successful import has a non-null content SHA; null is reserved for unprovable legacy rows.
7. A source alias hit does not bypass reading and hashing the newly delivered bytes.
8. Exact-content merge preserves reading state and owned metadata before duplicate deletion.
9. `DocumentIdentityIndex` is absent from steady-state import, lookup, and delete paths after its one-time migration.
10. No intermediate implementation commit is releaseable until all import entry points have moved to the shared path and the legacy identity branches are removed.

## 2. Planned component boundary

The minimum shared architecture has two responsibilities:

```text
FAB / VIEW / SEND adapter
          |
          v
ContentIdentityResolver
  metadata -> staging copy + source-size gate -> SHA-256 -> Room lookup -> decision
          |
          v
DocumentRepository.commitResolvedImport
  revalidate -> create/enrich/merge -> alias bind -> payload ownership -> result
          |
          v
Room: documents + document_source_aliases
```

`ContentIdentityResolver` exists because there are three real callers and one identity rule. It must not absorb navigation, confirmation UI, reader-session shutdown, ZIP parsing, or document rendering.

`DocumentRepository` remains the owner of document rows, child data, app-private payload lifecycle, and deletion. A new general framework or dependency is not needed.

## 3. Work package A — Room migration

### 3.1 Schema version

Raise `AppDatabase` from version **6 to 7** and register one explicit `MIGRATION_6_7`. Continue exporting Room schemas and add the generated v7 JSON under the existing `app/schemas` location.

Do not use `fallbackToDestructiveMigration`, destructive table replacement, primary-key rewriting, or a compatibility database opened along a second path.

### 3.2 DocumentEntity.contentSha256

Add:

```text
contentSha256: String?
```

Contract:

- exact lowercase 64-character SHA-256 of the original imported bytes;
- immutable after assignment;
- unique among non-null values;
- nullable in SQL/Kotlin solely for legacy rows whose original bytes are unavailable;
- non-null at every new-import commit boundary;
- never inferred from a plain 64-hex document id, filename, provider size, extracted Markdown entry, or unverified sidecar field.

Add a unique Room index named consistently with the exported schema, expected as `index_documents_contentSha256`. SQLite permits multiple nulls in a unique index, so legacy unknown rows can coexist without weakening uniqueness for known hashes.

The database cannot express “nullable only for legacy rows.” Enforce that second invariant at the shared repository commit API and test that no new create result can persist null.

### 3.3 Source-alias table

D-1.1 also requires a Room-owned alias mapping. Plan a `DocumentSourceAliasEntity` and DAO with:

| Field | Planned contract |
|---|---|
| `aliasKey: String` | Primary key; versioned canonical or legacy URI-derived key |
| `documentId: String` | Foreign key to `documents.id`, `ON DELETE CASCADE` |
| `kind: String` | Alias normalization algorithm/version |
| `createdAt: Long` | First successful binding time |
| `lastSeenAt: Long` | Last successful byte verification time |

Add an index on `documentId`. One document may own many aliases; one alias has one current target. Rebinding an alias is an explicit update performed only after changed source bytes have been committed successfully.

Do not persist the raw user URI merely for deduplication. `DocumentEntity.uri` may continue to locate readable content, but it is not an identity field.

### 3.4 MIGRATION_6_7 execution order

The migration should perform only deterministic database work:

1. `ALTER TABLE documents ADD COLUMN contentSha256 TEXT`.
2. Create `document_source_aliases` with its foreign key and indexes.
3. Scan existing ids in migration code with a strict, anchored lowercase format validator.
4. For exactly `c:<64 lowercase hex>`, populate `contentSha256` from the suffix.
5. For exactly `<64 lowercase hex>` without `c:`, insert `legacy-raw-uri-v1:<id>` as a source alias and leave the hash null.
6. Leave all nonconforming ids and their data untouched with a null hash.
7. Create the unique `contentSha256` index after validated backfill so unexpected conflicts fail the migration instead of silently replacing rows.

The migration must not move package directories, change `documents.id`, rewrite child foreign keys, hash `DocumentEntity.uri`, or access a `ContentResolver`.

### 3.5 DAO write safety

The existing `DocumentDao.upsert` uses `OnConflictStrategy.REPLACE`. It must not be used for identity creation once `contentSha256` is uniquely indexed: SQLite `REPLACE` can delete the existing conflicting row and cascade-delete its anchors/outlines before inserting the replacement.

Plan explicit operations instead:

- `insertDocument` with `ABORT` for a new entity;
- targeted `UPDATE`/Room `@Update` for an existing entity;
- `getByContentSha256` for authoritative lookup;
- alias lookup, insert, rebind, and delete methods;
- one `@Transaction` repository/DAO boundary for revalidation and state merge.

On a unique-hash race, abort the losing insert, requery the winner, delete only operation-owned staged output, and return/reuse the winner. Never resolve a race with `REPLACE`.

### 3.6 One-time sidecar migration

Because the sidecar is filesystem data, migrate it with an idempotent post-open v7 upgrader before import actions are enabled:

1. if no live sidecar exists, finish immediately;
2. parse without changing the file;
3. accept only aliases whose target Room document exists;
4. trust the sidecar SHA only when the target id is valid `c:<sha>` and the values agree;
5. never populate `contentSha256` from a sidecar entry for a plain URI-hash id;
6. commit accepted aliases to Room in one transaction;
7. only after commit, remove or move the old file out of its live name;
8. remove all normal importer/index read-write call sites in the same implementation series.

A repeated run must yield the same aliases. Corrupt/stale entries are reported and skipped, never used to merge documents.

### 3.7 Rollback considerations

Room v7 is a forward-only user-data upgrade:

- A failure inside `MIGRATION_6_7` must leave the v6 database usable and must never fall back to destructive recreation.
- A failure in post-open sidecar migration leaves the live sidecar intact and Room inserts either fully committed or absent; the next start can retry.
- App-private payload cleanup occurs only after the corresponding Room commit, so rollback cannot leave a retained row pointing to a deleted payload.
- Before a public release, rollback means reverting the implementation commits while no user database has been upgraded.
- After users run v7, installing the old v6 APK is not a supported rollback: the old schema cannot safely open a newer database. A production defect requires a forward fix that still opens v7, or an explicit v7 → v8 migration.
- Do not add an in-app database clone, destructive downgrade path, old runtime identity branch, or dual-written sidecar as a rollback mechanism.

## 4. Work package B — ContentIdentityResolver design

### 4.1 Responsibility

`ContentIdentityResolver` owns this exact flow:

```text
Uri
  -> existing ProviderMetadataSource / ExternalUriMetadata resolution
  -> supported-type and source-size hint gates
  -> app-private temporary staging
  -> one streaming pass: byte count + 200 MiB bound + SHA-256 + disk write
  -> staged-file validation
  -> Room lookup by contentSha256 and alias
  -> provisional Existing / EnrichLegacy / Merge / Create decision
```

It does not write a document, mutate progress, close a reader, navigate, extract a ZIP, or bind aliases. Those are commit/coordinator responsibilities.

### 4.2 Planned input and output contracts

The exact Kotlin names may be adjusted during implementation, but the semantic contract should resemble:

```text
ResolveRequest
  sourceUri
  intentMime
  grantFlags
  activeDocumentId

PreparedContent
  operationToken
  sourceUri / aliasKey / aliasKind
  stagedFile
  copiedBytes
  contentSha256
  resolved displayName / mimeType / documentType
  isZipPackage
  decisionSnapshot

IdentityDecision
  CurrentActive(documentId)
  Existing(documentId)
  EnrichLegacy(documentId)
  Merge(contentOwnerId, aliasOwnerId)
  Create(candidateOpaqueId)
```

`PrepareOutcome` continues to distinguish unsupported input, too-large input, read failure, and success. A successful prepared object owns its staging file until commit or explicit discard.

The decision is provisional because another coroutine/process may commit the same hash before this operation. `DocumentRepository.commitResolvedImport` must requery hash and alias inside its transaction and may change `Create` into `Existing` or `Merge`.

### 4.3 Staging and hashing rules

- Use one app-private staging directory and operation-unique `.importing` names.
- Hash and write in the same bounded streaming pass; do not read the provider stream twice.
- Preserve the existing inclusive 200 MiB source limit and 64 KiB-class bounded buffer behavior.
- Validate actual staged length against copied byte count and fsync before returning success.
- Treat provider size only as an early rejection hint; actual copied bytes remain authoritative.
- The hash always covers the original input bytes. ZIP extraction occurs later from the staged ZIP.
- Staging paths are operation identities, not document identities, and must not use raw URI or content hash as ownership authority.

### 4.4 Failure and cancellation contract

Every exit owns explicit cleanup:

- unsupported/known-too-large input creates no staged file or Room mutation;
- read/hash/write/fsync validation failure removes the operation's temp file;
- stream overflow deletes the partial file and returns TooLarge;
- coroutine cancellation runs cleanup in `finally` and rethrows `CancellationException` rather than converting it to a generic read failure;
- discard after external-open cancellation removes prepared staging and creates no row/alias/package;
- startup cleanup may delete only stale `.importing` paths that cannot be referenced by committed Room rows.

No cleanup path may delete a pre-existing library file or a winner from a concurrent import.

### 4.5 Commit contract

`DocumentRepository.commitResolvedImport` should:

1. ensure the staged object is complete and its hash is valid;
2. flush the active reader before a merge/delete when requested by the coordinator;
3. prepare any required final payload/package without changing Room;
4. transactionally requery content and alias;
5. create, enrich, reuse, or merge using D-1.1 survivor rules;
6. bind/rebind the alias and update `lastSeenAt`;
7. return the one surviving document id;
8. clean losing staging/payload files only after commit.

Existing imports keep their reading snapshot. Reimport must not reset scroll position, viewport, title, anchors, or outline data.

## 5. Work package C — Import pipeline migration order

### 5.1 Development sequence

| Order | Change | Exit gate |
|---|---|---|
| 1 | Add red identity/migration tests and v6 fixtures | Tests demonstrate both current order-dependent ZIP duplicates |
| 2 | Add Room v7 schema, DAO APIs, and one-time data upgrader | Migration tests preserve all seeded state and schema validates |
| 3 | Implement resolver, staged-content lifecycle, and transactional commit/merge engine | Resolver and repository tests pass without entry-point branching |
| 4 | Route VIEW and SEND through the shared resolver/commit path | Both actions retain existing confirmation/session semantics |
| 5 | Route FAB/SAF through the same path | Cross-entry/order matrix yields one content entity |
| 6 | Converge ZIP extraction on one staged-package commit | Existing content never extracts twice; failures leave no row/orphan |
| 7 | Delete old identity and duplicate import paths | No primary-key parsing, file-rescan hash fallback, or live sidecar usage remains |
| 8 | Run full automated and focused device acceptance | All gates in §8 pass before release planning |

Steps 4–6 are one release unit. Do not publish, tag, or hand off an APK from an intermediate state where only some entry points use the resolver.

### 5.2 FAB / SAF

Keep `ActivityResultContracts.OpenDocument`, persistable-read permission handling, existing metadata authority, format policy, source limit, and current `ImportResult` UI mapping.

Replace the repository's URI-derived-id branch with:

1. pass the selected URI to `ContentIdentityResolver`;
2. stage/hash even when the URI alias is already known, because provider bytes may have changed;
3. commit immediately after successful resolution (FAB has no external replacement-confirm dialog);
4. return the survivor entity in `ImportResult.Success`;
5. delete the old `stableUriId -> getById -> upsert` identity decision and the FAB-only ZIP wrapper after all tests are green.

`stableUriId` may remain only in the one-time legacy alias migration/normalizer. It must not create a new `DocumentEntity.id`.

### 5.3 VIEW

Keep `MainActivity -> ExternalOpenCoordinator` and the current two-phase session safety:

- the lightweight incoming card may use metadata and a Room alias as an advisory label only;
- accepting the card invokes the shared resolver and performs the actual byte identity check;
- a successful prepare occurs before the old reader is changed;
- when replacement is required, flush old progress before transactional commit and navigation;
- cancel discards the prepared operation without touching Room or the active reader;
- `ExternalDocumentImporter` becomes a thin adapter/coordinator over the shared resolver and repository commit rather than owning a second identity algorithm.

Remove direct sidecar prune/find/bind, `c:<sha>` id creation, and `findByContentSha256` file-rescan fallback from this path.

### 5.4 SEND

SEND follows exactly the VIEW implementation. Its Android intent parsing/grant metadata may differ, but it must call the same `ExternalOpenCoordinator`, resolver, and commit APIs with no SEND-specific identity, hash, merge, or ZIP branch.

Test VIEW and SEND separately at the intent boundary, then assert they produce identical resolver requests and final Room state for the same bytes.

### 5.5 ZIP package

ZIP is a payload commit strategy after identity resolution, not an identity strategy.

Planned behavior:

1. Resolver stages and hashes the complete compressed archive.
2. If the hash already exists with a readable committed package, discard staging and do not extract.
3. For Create, or for an enriched legacy entity whose payload must be reconstructed, extract only the staged archive through the existing `ZipPackageImporter` limits into an operation-owned temporary package directory.
4. Validate the selected Markdown entry and complete extracted tree before Room mutation.
5. Promote to `files/packages/<opaqueDocumentId>` with failure cleanup; existing legacy directories retain their names and are not renamed just for normalization.
6. Commit `MARKDOWN_PACKAGE`, original ZIP `contentSha256`, aliases, and the selected entry locator.
7. On a concurrent-hash winner, delete only this operation's staged archive/temp package and use the winner.
8. Delete the duplicated FAB/external ZIP orchestration once both callers use this one strategy; retain `ZipPackageImporter` as the single extraction/security implementation.

`DocumentEntity.fileSize` may continue its reader/display semantics for the committed entry/package. The resolver's copied-byte count enforces source size but is not a substitute for `contentSha256`.

### 5.6 Final legacy-path removal

After all callers are switched, remove—not deprecate or retain behind fallback—the following behaviors:

- new ids derived from raw URI;
- new ids formatted as `c:<sha>`;
- content hash inferred with `removePrefix("c:")`;
- content search by hashing `DocumentEntity.uri`;
- runtime `DocumentIdentityIndex` reads, writes, pruning, and delete hooks;
- two entry-specific ZIP commit implementations;
- tests that merely assert handcrafted identity strings instead of executing the real shared pipeline.

The v6 → v7 migrator remains the only historical conversion code.

## 6. Work package D — Existing data handling

### 6.1 Null-hash rows

Null means **unknown**, never “empty content” or “use URI identity.”

- Show and open the legacy document exactly as before.
- Do not run an automatic background scan of every source/payload.
- When an incoming verified alias points to a null-hash row and no hashed owner exists, assign the incoming hash to that same row and keep its id/state.
- When the incoming hash already belongs to another row, merge only if that incoming alias proves the null-hash row is the same source lineage.
- If original ZIP bytes and a connecting alias are unavailable, leave the legacy row null and unmerged.

New code must never create a null-hash document. Add a repository assertion/error result at the only shared create boundary.

### 6.2 Existing duplicate documents

Do not perform heuristic or eager migration-time deduplication. A duplicate pair becomes mergeable only when exact incoming source bytes connect its alias to a known `contentSha256` owner, or when both rows already carry the same proven hash in abnormal legacy data.

The deterministic survivor is selected according to D-1.1:

1. existing content-hash owner;
2. otherwise alias-matched legacy row enriched in place;
3. for an abnormal tie, older `createdAt`, then lexical id.

The merge transaction must move aliases, retain the freshest complete reading/viewport snapshot, merge print-page anchors by `updatedAt`, preserve manual outline nodes, deduplicate embedded nodes by `importKey`, and preserve metadata before deleting the loser.

`PdfReadingPrefs` is outside Room. Copy chosen values synchronously to survivor keys before the destructive Room step; remove losing keys only after commit. Prepare/verify the survivor payload before Room commit and remove the losing app-private payload afterward.

### 6.3 Migration safety rules

- Preserve all existing ids and foreign-key references during schema migration.
- Preserve all null-hash rows rather than guessing.
- Disable import entry points until the one-time sidecar upgrader has finished or safely reported no usable input.
- Serialize repository identity commits where practical; still rely on the database unique index as the final race guard.
- Never use `REPLACE` for a content-hash conflict.
- Never delete a user-owned SAF/provider source.
- Never delete a duplicate row before its progress, child rows, preferences, and selected payload have a survivor.
- Every filesystem operation is operation-scoped and followed by a Room consistency check/failure cleanup.
- A process death may leave a removable orphan `.importing` path, but never a committed row pointing to a partial payload.

## 7. Planned file-level change map

This is a responsibility map, not authorization to edit:

| Area | Expected files/responsibility |
|---|---|
| Room entity/schema | `DocumentEntity.kt`, new `DocumentSourceAliasEntity.kt`, `AppDatabase.kt`, exported schema `7.json` |
| DAO | `DocumentDao.kt`, new alias DAO; explicit insert/update/hash/alias/transaction queries |
| Identity staging/resolution | new focused `ContentIdentityResolver.kt` near data/share intake code |
| Commit/merge/payload ownership | `DocumentRepository.kt` plus narrowly scoped state-merger helpers only if two real consumers require them |
| External adapters | `ExternalDocumentImporter.kt`, `ExternalOpenCoordinator.kt`; VIEW/SEND stay unified |
| FAB adapter | `HomeViewModel.kt` result wiring only if its public result contract must change; no UI redesign |
| ZIP commit | one shared package commit path reusing `ZipPackageImporter.kt` |
| Sidecar retirement | one-time upgrader plus deletion of steady-state `DocumentIdentityIndex` consumers/file tests |
| Migration tests | new v6 → v7 Android migration fixture/test and host SQL/version contracts |
| Resolver/pipeline tests | Robolectric/JVM integration tests using real Room where identity persistence is asserted |

Avoid changing screens, text, navigation presentation, version metadata, or unrelated repository APIs.

## 8. Test strategy and acceptance matrix

### 8.1 Room migration tests

Create a real v6 fixture containing:

- a valid `c:<sha>` row with progress, PDF viewport, print anchor, and outline nodes;
- a plain URI-hash row;
- a nonconforming/opaque row;
- valid and stale sidecar entries, including the known false “plain id used as SHA” shape.

Run `MIGRATION_6_7` and assert:

- Room validates against exported schema v7;
- all ids, child rows, reading state, timestamps, and payload locators are unchanged;
- only valid `c:<sha>` rows receive a hash;
- plain ids receive legacy aliases and remain null-hash;
- the unique index and alias foreign-key cascade exist;
- sidecar import is idempotent and false/stale hashes do not enter Room;
- failure injection yields no partial conversion and no destructive fallback.

Retain the existing v5 → v6 test and add an end-to-end v5 → v7 migration chain test so the registered migration sequence remains complete.

### 8.2 Required identity scenarios

| Scenario | Entry matrix | Required assertions |
|---|---|---|
| Same content, different URI | FAB→VIEW, VIEW→FAB, FAB→SEND, SEND→FAB, VIEW↔SEND | One document row, one non-null hash, two aliases, stable survivor id/progress, one owned ZIP package |
| Different content, same filename | Each entry and cross-entry | Two rows with distinct hashes; no alias/content merge; both readable |
| Interrupted hashing | IOException, coroutine cancellation, process-like abort hook | No Room/alias write, no final payload/package, staging removed or safely startup-cleanable, cancellation rethrown |
| Large file | 199 MiB, exactly 200 MiB, 200 MiB + 1; with and without provider hint | Same verdict across FAB/VIEW/SEND, bounded memory, single streaming pass, overflow cleanup |
| Duplicate import | Same URI repeated, different URI repeated, sequential and concurrent | Idempotent result, no progress reset, one hash owner, no `REPLACE` cascade, no duplicate extraction |

Use logical/fake streams for boundary tests to avoid allocating 200 MiB in JVM memory. Add one real moderate-size staged-file integration test to verify byte count, digest, fsync/length validation, and cleanup.

### 8.3 Merge-state tests

Seed two proven-identical rows and assert:

- survivor selection is deterministic;
- the newest whole progress/viewport snapshot wins without field mixing;
- print anchors merge by page and `updatedAt`;
- manual outline nodes survive and embedded `importKey` duplicates retain a valid hierarchy;
- aliases all point to the survivor;
- PDF reading preferences are copied before loser cleanup;
- the losing row and only its app-private payload disappear after commit;
- a forced failure before commit preserves both original rows and payloads;
- a forced failure after Room commit leaves at worst an orphan eligible for safe cleanup.

### 8.4 ZIP-specific tests

- Hash is calculated over the original ZIP bytes and differs for a repacked archive even when extracted content is equivalent.
- Existing-content resolution skips extraction.
- Create extracts once through `ZipPackageImporter` and commits the selected Markdown entry.
- ZIP-slip, entry-count, single-entry, and total-uncompressed limits remain unchanged.
- Extraction failure leaves no document, alias, or final package directory.
- Concurrent same-ZIP imports cannot delete the winner's package.

### 8.5 Automated and device gates

Before implementation handoff:

1. Run targeted migration and identity tests during each work package.
2. Run `:app:testQaUnitTest` with JDK 17; zero failures/errors/skips unless separately documented policy changes.
3. Run `:app:compileQaAndroidTestKotlin`.
4. Run the v6 → v7 migration instrumented test on an available Android target; without a `device` ADB state, report it as device-pending rather than PASS.
5. Device-test FAB, VIEW, and SEND using one byte-identical ZIP from distinct provider URIs in both order directions.
6. Verify one recent-library row, retained reading position after cross-entry reopen, and no duplicate package tree.

An `assemble*` is not required merely to land the design or early implementation. If a later task performs one, the repository's mandatory APK delivery rule applies. Release version/tag/signature work remains a separate objective.

## 9. Implementation checkpoints

### Checkpoint 1 — schema safe

- v7 schema exported and validated;
- v6 user state retained;
- null strategy and unique index proven;
- no destructive fallback;
- no production entry point switched yet.

### Checkpoint 2 — identity core safe

- resolver has one bounded stage/hash pass;
- repository transaction handles Existing/Enrich/Merge/Create and races;
- interruption and cleanup tests green;
- `REPLACE` cannot participate in identity conflict resolution.

### Checkpoint 3 — all entries converged

- FAB, VIEW, and SEND call the same resolver and commit contract;
- external confirmation and active-session ordering remain unchanged;
- ZIP has one commit strategy;
- cross-entry order matrix is green.

### Checkpoint 4 — legacy removed

- no live sidecar identity dependency;
- no id-prefix or URI-primary-key logic in normal imports;
- no file-rescan substitute for original ZIP hash;
- obsolete tests/paths removed rather than retained as fallback;
- full automated verification green.

### Checkpoint 5 — implementation report only

Record implemented, automated_verified, device_verified, and pending separately. Do not create/move a release tag or overwrite the canonical v0.1.0 artifact as part of implementation closure.

## 10. Risks and mitigations

| Risk | Planned mitigation |
|---|---|
| Unique hash conflict triggers `REPLACE` cascade | Explicit `ABORT` insert + transaction requery; regression-test anchors/outlines/progress |
| Legacy URI hash is mislabeled as content SHA | Strict `c:<64 lowercase hex>` backfill only; false sidecar-shape test |
| Alias points at changed provider bytes | Always stage/hash before reuse; rebind only after successful new commit |
| Hash interrupted or disk full | Operation-owned temp path, `finally` cleanup, no Room write before complete validation |
| ZIP extraction duplicates disk usage | Resolve content before extraction; one shared extraction path; cleanup losing concurrent operation |
| Merge loses state | Deterministic survivor and field/table merge tests; preferences copied before delete |
| Room migration succeeds but sidecar import fails | Independent idempotent post-open step; leave live sidecar untouched until Room commit |
| Old APK installed after v7 upgrade | Explicitly unsupported; forward-fix policy, no destructive downgrade |
| Intermediate branch retains two identity paths | No release/build handoff until all entries switched and old path deleted |

## 11. Scope exclusions

D-1.2 does not authorize:

- production code, Room schema/version, test, UI, or APK changes;
- automatic semantic deduplication of repacked ZIP archives;
- background hashing of the complete existing library;
- a document-id rewrite or package-directory normalization migration;
- UI wording, duplicate-management UI, new formats, TOC, cloud/netdisk, EPUB, annotations as a new feature, or performance work;
- dependency upgrades, architecture refactoring outside identity, release version changes, or release tagging.

## 12. Final state

The implementation sequence, failure boundaries, migration contract, existing-data policy, and acceptance matrix are defined. No implementation action has been taken.

**DESIGN_ONLY**
