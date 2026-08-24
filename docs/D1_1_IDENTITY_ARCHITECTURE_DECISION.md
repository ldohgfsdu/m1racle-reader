# MarkLeaf D-1.1 ZIP / Document Identity Architecture Decision

## 0. Decision status

| Field | Value |
|---|---|
| Date | 2026-08-24（UTC+8） |
| Release baseline | `v0.1.0-c-local` → `604d404978b5f09fdb4a9c825259ef6793f59949` |
| Input finding | D-1.0 **IDENTITY_ISSUE_CONFIRMED** |
| Decision scope | ZIP / Document identity, location aliases, deduplication, and preservation of existing library state |
| Implementation | Not authorized and not performed |
| Production code / Room schema / UI | Unchanged |
| Release tag / APK | Unchanged |
| Final status | **DESIGN_ONLY** |

This document is an architecture decision, not an implementation plan approval. It defines the one authoritative identity model that a separately authorized task may implement.

## 1. Decision summary

MarkLeaf will separate three concepts that are currently mixed in `DocumentEntity.id` and `DocumentIdentityIndex`:

1. **Document entity identity** — a stable, opaque Room primary key representing one library record and owning its reading state.
2. **Content identity** — lowercase SHA-256 of the exact original imported source bytes, persisted explicitly in Room as `contentSha256`.
3. **Location alias** — a versioned, normalized key derived from a delivery location such as a SAF/content URI; multiple aliases may point to one document entity.

The decisions are:

- Keep `DocumentEntity.id` as the primary key. Do not re-key existing rows.
- Stop assigning business meaning to new ids; new ids should be opaque generated values rather than URI hashes or `c:<contentHash>` values.
- Add nullable `DocumentEntity.contentSha256` with a unique Room index. It is mandatory for every import created after migration and nullable only for legacy rows whose original bytes cannot be proven.
- Add a Room-owned source-alias table with a unique alias key and a foreign key to `documents.id`.
- Make all FAB/SAF and VIEW/SEND imports use one hash-before-resolution pipeline.
- Replace `ExistingLibrary`'s current id/sidecar/file-rescan heuristics with Room queries against `contentSha256` and source aliases.
- Import usable legacy sidecar aliases once, then retire `DocumentIdentityIndex` from runtime identity decisions. No dual read/write or permanent compatibility path remains.
- Merge rows only after exact content proof. Names, sizes, extracted entry hashes, and URI similarity are not sufficient proof.

D-1.0 correctly identified source-byte SHA-256 as the authority for byte-identical ZIPs. This decision changes where that identity is represented: it belongs in an explicit Room column, not in the primary-key format.

## 2. Current architecture reviewed

### 2.1 DocumentEntity schema

Room schema version 6 has one `documents` primary key, `id`, and no secondary document index. `DocumentEntity` stores:

- source/display fields: `displayName`, `uri`, `mimeType`, `fileSize`;
- lifecycle fields: `createdAt`, `lastOpenedAt`;
- reading state: scroll index/offset, aggregate progress, and PDF viewport fields;
- derived metadata: title, heading count, document type, and page count.

There is no `content_sha`, `contentHash`, `contentSha`, or `contentSha256` field. The exported v6 schema confirms this. `DocumentEntity.id` is also consumed by navigation, search state, package-directory ownership, deletion, PDF reading preferences, and child-table foreign keys.

Changing the primary key would therefore be a high-risk state migration with no identity benefit once content hash is stored separately.

### 2.2 Current Room migration policy

`AppDatabase` uses explicit additive migrations from v1 through v6 and does not enable destructive fallback. Existing user state is intentionally retained. A future identity migration must follow the same policy:

- explicit schema migration;
- no destructive rebuild;
- one-time data backfill where proof exists;
- no ongoing legacy runtime branch after migration finishes.

### 2.3 Import chains

Current FAB/SAF import computes `stableUriId(uri.toString())`, looks up only that primary key, and gives a ZIP package the URI-derived id. It does not hash the original ZIP or bind the sidecar.

Current VIEW/SEND import copies and hashes the original bytes, queries several sidecar/id fallbacks, and creates `id = "c:<sha256>"` when content is new. VIEW and SEND already share this external-open implementation; FAB does not.

The two entry families therefore create different primary keys for the same ZIP bytes and are order-dependent, as established in D-1.0.

### 2.4 ExistingLibrary behavior

The current external path checks canonical URI identity, a legacy URI-derived primary key, sidecar SHA + size, and finally `findByContentSha256`. The last method first assumes a `c:<sha>` id and then may hash the file at `DocumentEntity.uri`.

That fallback is not authoritative for a FAB ZIP: its stored URI points to extracted Markdown and its stored size is the entry/package size, while VIEW/SEND hashes the original compressed ZIP bytes. The legacy match also removes a `c:` prefix from any id, which can mislabel a URI hash as a content SHA.

### 2.5 Sidecar identity index

`DocumentIdentityIndex` is documented as rebuildable and subordinate to Room/private files. Only VIEW/SEND binds it. Its rebuild function can recover content hashes only from ids already formatted as `c:<sha>`, and it cannot recover the discarded source ZIP bytes of a FAB import.

It cannot be repaired into an authority without retaining the same split-brain problem. The new model therefore moves both content and alias lookup into Room and removes the sidecar from normal identity decisions after a one-time import.

## 3. Definitions

### 3.1 What is content identity?

**Content identity is SHA-256 of the exact byte sequence delivered as the original import source, before parsing, extraction, normalization, or metadata enrichment.** It is represented as a lowercase 64-character hexadecimal value in `contentSha256`.

For each supported input:

- PDF: hash the original PDF bytes.
- Plain Markdown: hash the original Markdown file bytes.
- ZIP Markdown package: hash the original compressed ZIP bytes, not its selected entry, extracted tree, filenames, timestamps, or a recompressed archive.

Consequences:

- The same bytes delivered through FAB, VIEW, or SEND have the same content identity.
- A mutable URI whose bytes change represents new content even though its location alias is unchanged.
- Separately repacked ZIPs with equivalent extracted files are different byte content. Semantic-package equivalence is outside this decision.
- `fileSize`, MIME supplied by a provider, display name, and extracted-tree shape may assist validation but never decide identity.
- Once assigned, `contentSha256` is immutable for that entity. Changed bytes create or resolve to a different entity; the location alias may then be repointed.

### 3.2 What is a location alias?

**A location alias is a non-owning pointer from a delivery location to the document entity last proven to contain the bytes read from that location.** It answers “where did these bytes arrive from?”, not “what content is this?”

Examples include a canonicalized content-provider identity or the existing SHA-256 of a raw URI string. The persisted key should be versioned, for example:

```text
uri-v1:<sha256(canonicalUriIdentity)>
legacy-raw-uri-v1:<stableUriId(rawUri)>
```

The alias table should store only what resolution needs:

| Proposed field | Rule |
|---|---|
| `aliasKey` | Primary key; versioned normalized/hardened location key |
| `documentId` | Non-null foreign key to `documents.id`, `ON DELETE CASCADE` |
| `kind` | Identifies the alias normalization algorithm/version |
| `createdAt` | First binding time |
| `lastSeenAt` | Last successful byte verification time |

An index on `documentId` supports cleanup and provenance queries. Persisting the original user-visible URI is not required merely for identity; if a reader still needs a URI to open the payload, that locator remains a separate storage/access concern.

Aliases have these invariants:

- one alias key points to at most one current document;
- one document may have zero, one, or many aliases;
- an alias hit may select a candidate but may not skip reading and hashing incoming bytes;
- an alias is rebound only after the new bytes are fully staged, hashed, and committed successfully.

## 4. Persistence decisions

### 4.1 Does the DocumentEntity primary key change?

**No.** `DocumentEntity.id` remains the stable entity primary key.

Reasons:

- it is already referenced by reading state, child Room tables, navigation, preferences, app-private paths, search/session state, and deletion;
- changing it would require a broad, failure-prone rewrite of foreign keys, preferences, paths, and active sessions;
- a primary key identifies the library entity, while a content hash describes a property of its source bytes;
- legacy records may not have enough retained evidence to compute the original source hash;
- keeping id opaque prevents two competing sources of truth (`id` encoding hash plus a hash column).

Existing URI-hash and `c:<sha>` ids remain unchanged and are treated as opaque after migration. New documents receive generated opaque ids. Code must not branch on an id prefix or infer a SHA from an id after the one-time migration.

### 4.2 Is a contentHash field added?

**Yes, with the precise name and contract `contentSha256: String?`.** A generic `contentHash` name is rejected because it hides the algorithm and invites incompatible values.

The proposed Room contract is:

- nullable during/after migration only because some legacy source archives no longer exist;
- required and validated for all newly committed imports;
- lowercase 64-character SHA-256 hex;
- unique indexed value; SQLite's multiple-NULL behavior permits unknown legacy rows;
- never populated from display name, URI hash, extracted entry hash, or an unverified sidecar value.

Content identity becomes a Room fact. `DocumentEntity.id` and `DocumentIdentityIndex` are no longer accepted as content-hash APIs.

### 4.3 Room as the single authority

After migration, Room owns both mappings:

```text
documents.id                    -> stable entity identity
documents.contentSha256         -> exact source-byte identity (unique when known)
document_source_aliases.aliasKey -> delivery location -> documents.id
```

The app-private `DocumentEntity.uri` remains a payload locator, not identity. The sidecar is retired rather than kept synchronized with Room.

## 5. Unified import and ExistingLibrary resolution

All FAB/SAF, VIEW, and SEND imports must call the same domain operation. Entry-point-specific code may collect an Android URI or show confirmation, but it may not select identity.

The single flow is:

1. Canonicalize the incoming location into a versioned alias key.
2. Stream the complete original source into a staging file while computing byte count and SHA-256 exactly once. Apply existing size/type/security gates during this bounded stage.
3. Begin a Room transaction and query `documents.contentSha256`.
4. If that content exists, bind/rebind the alias to it, discard the staging file, and return `ExistingLibrary` for that entity.
5. If the alias points to a legacy row whose hash is null and there is no other row with the incoming hash, assign the proven hash to that row. Its id and reading state remain intact.
6. If the content hash and alias resolve to two different rows, execute the exact-content merge in §7 and bind the alias to the survivor.
7. Otherwise create one new opaque document id, persist `contentSha256`, bind the alias, and commit the payload/extracted package.
8. Navigate only to the surviving committed entity.

The unique index is the final concurrency guard. Concurrent imports of the same bytes must converge by catching the uniqueness conflict, requerying the winner in a new transaction, and applying the same alias/merge rules; they must not create a second id convention.

`ExistingLibrary` after this change means “Room already has the exact source-byte hash.” A location-only match is a candidate optimization, not a completed identity decision. The following current heuristics are removed from normal operation:

- `stableUriId` as `DocumentEntity.id`;
- `c:<sha>` primary-key lookup;
- `removePrefix("c:")` as content evidence;
- sidecar as an early identity authority;
- hashing `DocumentEntity.uri` as a substitute for a discarded ZIP archive;
- display-name/file-size matching as deduplication proof.

## 6. Migration of existing user data

### 6.1 Schema migration

A future explicit v6 → v7 migration should:

1. add nullable `documents.contentSha256`;
2. add its unique index;
3. create `document_source_aliases` with its foreign key and indexes;
4. backfill only facts provable from existing durable data.

No destructive migration is permitted. No primary key, child foreign key, reading-state column, or package directory is rewritten merely to normalize old id formats.

### 6.2 Deterministic Room backfill

The database-only migration may safely perform:

- For `id = "c:<64 lowercase hex>"`, validate the complete format and set `contentSha256` to the suffix. This format was generated from copied source bytes by the current external importer.
- For a legacy 64-hex URI-derived id, create `legacy-raw-uri-v1:<id>` as a location alias and leave `contentSha256 = NULL`.
- For unknown/nonconforming ids, keep the row and its state unchanged with a null hash.

A plain 64-hex id must never be assumed to be a content hash. This is the exact ambiguity that caused the current sidecar mislabeling.

### 6.3 One-time sidecar import and retirement

Sidecar filesystem data cannot be read safely inside the SQL-only Room migration. A dedicated, idempotent post-open v7 upgrader should run before import entry points become available:

1. read the old sidecar if present;
2. retain an alias only when its target Room document exists and its alias key is valid;
3. trust a sidecar SHA only when the target id is a valid `c:<sha>` id and both SHA values agree; never promote the SHA attached to a plain URI-hash row;
4. insert/update accepted aliases transactionally in Room;
5. after that transaction succeeds, remove or quarantine the old live sidecar so runtime code cannot consult it.

The operation is idempotent: a crash before Room commit changes nothing; a crash after commit may repeat identical alias inserts; cleanup happens only after commit. Once the upgrader finishes, no importer reads or writes `DocumentIdentityIndex`. This is a one-time necessary data migration, not a compatibility layer.

### 6.4 Legacy ZIPs whose source bytes are unavailable

A FAB ZIP row generally retains only its extracted tree and selected Markdown entry. Neither proves the SHA-256 of the discarded original compressed archive. Therefore:

- its content hash remains null after automatic migration;
- no tree hash, entry hash, name, or size heuristic is substituted;
- when the same legacy URI is explicitly imported again, the migrated alias selects the legacy candidate, the newly read source bytes provide the hash, and that existing row is enriched without changing id;
- if a different URI supplies those bytes and a hashed row already exists, MarkLeaf can reuse the hashed row, but cannot merge an unrelated null-hash legacy row until an exact proof connects them.

Some historical duplicates may consequently remain until the original location is seen again. Preserving potentially distinct user state is safer than an unproven automatic merge.

## 7. Same content through different URIs

### 7.1 Normal post-migration case

Two imports with the same source-byte SHA resolve to one `documents` row regardless of whether FAB, VIEW, or SEND arrives first. Each successfully verified URI becomes an alias of that row.

If one URI later serves different bytes:

- do not mutate the old entity's content hash;
- resolve/create the entity for the new hash;
- rebind that URI alias only after the new payload is safely committed;
- retain the old library entity and its state until the user explicitly removes it.

### 7.2 When two existing entities are proven identical

Rows are merged only when both are tied to the same exact source SHA-256. The merge must be a deterministic repository operation, not DAO call-site policy.

Survivor selection:

1. prefer the row that already owns the non-null `contentSha256`;
2. if only an alias-matched legacy row exists, keep that row and assign the proven hash;
3. if both are hashed duplicates from abnormal/legacy state, prefer the earlier `createdAt`, then lexicographically smaller id as a stable tie-breaker.

The survivor id never changes. All aliases and owned state move to it before the duplicate row and duplicate app-private payload are removed.

## 8. Deletion and source lifecycle

Deleting or losing one source does **not** delete the document entity.

- Removing/revoking one source removes or invalidates only that alias.
- A ZIP entity remains readable from its committed app-private extracted package even if the original provider item disappears.
- For an entity whose only readable payload is still user-owned external content, loss of that source may make the entity temporarily unreadable, but it must not silently erase progress or metadata. Storage ownership normalization is outside this identity decision.
- Other aliases remain bound and usable.
- Alias count is not a reference count and reaching zero does not auto-delete the library record.

Only an explicit “remove from library” operation deletes `DocumentEntity`. That operation:

- cascades Room aliases and child tables;
- removes the entity's app-owned copy/package tree;
- removes per-document app preferences;
- never deletes a user-owned SAF/content-provider source.

## 9. Reading progress, annotations, and metadata migration

Identity merge must preserve user state before deleting any duplicate. The rules below are atomic where data is in Room and crash-safe where filesystem/preferences are involved.

### 9.1 Reading progress and viewport

Treat these fields as one indivisible reading-state snapshot:

```text
lastScrollIndex
lastScrollOffset
readingProgress
pdfViewportSessionVersion
pdfViewportReadingMode
pdfViewportPageIndex
pdfPageAnchorXFraction / pdfPageAnchorYFraction
pdfViewportAnchorXFraction / pdfViewportAnchorYFraction
pdfViewportScale
pdfViewportGeneration
```

Choose the snapshot from the row with the later `lastOpenedAt`; never combine coordinates from different rows and never average progress. If timestamps tie, keep the survivor snapshot. The current schema has no separate progress-updated timestamp, so `lastOpenedAt` is the only durable freshness proxy available during this migration.

An actively open losing document must flush its state and leave the reader session before merge. Navigation resumes with the survivor id only after commit.

### 9.2 PDF print-page anchors

Move losing `pdf_print_page_anchors` rows to the survivor inside the same Room transaction. For the same `pdfPageIndex`, retain the value with the greater `updatedAt`; use the survivor value on a tie. This avoids the losing row's `ON DELETE CASCADE` removing the only anchor.

### 9.3 Outline metadata

For `textbook_outline_nodes`:

- preserve all manual nodes and reparent their `documentId` to the survivor;
- for embedded nodes with the same non-null `importKey`, retain the newer row;
- build a loser-node-id → survivor-node-id map and rewrite child `parentId` values before deleting duplicate embedded nodes, preserving hierarchy;
- keep globally unique node ids and deterministically normalize sibling order only where the two trees collide.

### 9.4 General annotations

Room v6 has no general annotation/highlight entity, so there are currently no such rows to migrate. PDF print-page anchors and outline nodes are handled explicitly above. Exported files are output artifacts, not annotations owned by a document row.

If a future annotation table is introduced, it must reference stable `DocumentEntity.id` and participate in the same survivor transaction; this decision does not authorize such a feature.

### 9.5 Document metadata

- `createdAt`: minimum of both rows.
- `lastOpenedAt`: maximum of both rows.
- reading-state tuple: from the row selected in §9.1.
- `displayName` and `title`: keep the survivor's nonblank value; otherwise fill from the loser. There is no current user-editable metadata authority that would justify overwriting a nonblank survivor value.
- `documentType`: must agree for exact source bytes; disagreement is treated as corruption and blocks automatic merge.
- `headingCount` / `pageCount`: prefer a valid positive value associated with the retained readable payload; otherwise use the other row's value.
- `mimeType`: prefer validated/sniffed type over provider-declared type.
- `uri`: prefer an existing app-private managed payload, then a currently readable persisted source. It remains a locator, not identity.
- `fileSize`: retain the size semantics required by the reader; it is not used to prove content identity.

### 9.6 PDF reading preferences

`PdfReadingPrefs` currently stores keys containing `documentId` outside Room. Before the destructive Room merge step:

1. synchronously copy the selected preference values to the survivor keys, preferring the more recently opened row when both have explicit values;
2. commit the Room merge;
3. remove losing keys after commit.

This order is idempotent and fail-safe: interruption may leave harmless duplicate preference keys, but it does not delete the only surviving values.

### 9.7 App-private payloads

Verify the survivor payload/tree before changing the Room locator. Perform Room state reparenting and duplicate-row deletion in one transaction. Delete the losing app-private payload only after commit and only when it is not the selected survivor payload. A crash may leave an orphan for later cleanup, but must not leave a Room row pointing at a deleted payload.

## 10. Invariants for a future implementation

The implementation is acceptable only if all of these remain true:

1. One exact source-byte SHA maps to at most one non-null `documents.contentSha256` row.
2. New committed imports always have a content SHA.
3. No code infers content SHA from `DocumentEntity.id` after migration.
4. No import entry point invents its own identity policy.
5. A URI/alias hit never bypasses verification of newly delivered bytes.
6. An entity id never changes because a new source alias is discovered.
7. Deleting a source alias never cascades to the document.
8. Deleting a document never deletes user-owned source content.
9. Exact-content merge preserves reading state and owned metadata before deleting the loser.
10. The sidecar is absent from steady-state identity reads/writes.

## 11. Rejected alternatives

| Alternative | Rejection reason |
|---|---|
| Make `DocumentEntity.id = c:<sha>` universal | Rewrites a widely referenced primary key, duplicates hash truth, and cannot represent legacy unknown content without another convention |
| Keep URI hash as primary identity | Same bytes across providers/URIs remain duplicated; mutable URI bytes are conflated |
| Promote `DocumentIdentityIndex` to authority | It is incomplete, rebuild-limited, filesystem-backed, and already diverges between entry paths |
| Keep Room and sidecar synchronized forever | Creates two authorities and a permanent repair/fallback path |
| Hash extracted ZIP tree/entry | Does not equal the defined original-source identity and introduces a separate semantic-deduplication policy |
| Merge by name + size/type | Can destroy distinct documents and their user state; not cryptographic proof |
| Destructive Room migration | Violates release data preservation and discards progress/metadata unnecessarily |

## 12. Required verification for a separately authorized implementation

No tests are added or run by D-1.1. A future implementation must provide at least:

- Room v6 → v7 migration tests for `c:<sha>` rows, plain URI-hash rows, child tables, null-hash rows, valid/stale/corrupt sidecar inputs, and idempotent rerun;
- FAB → VIEW/SEND and VIEW/SEND → FAB integration tests using identical ZIP bytes at different URIs, asserting one document, one content hash, one survivor state, and no duplicate package ownership;
- same URI with changed bytes, asserting a different content entity and safe alias rebind;
- exact-hash duplicate merge tests covering progress/viewport, print anchors, outline hierarchy/import keys, metadata, and PDF reading preferences;
- alias deletion versus explicit library deletion tests;
- concurrent same-content import test proving the unique-index winner path;
- package/file failure-injection tests proving no committed row points at a deleted or incomplete payload;
- focused device acceptance for FAB, VIEW, and SEND with retained reading progress.

## 13. Scope boundary and final state

D-1.1 changes only this decision record. It does not modify production code, Room entities/schema/version/migrations, tests, UI, release tag, APK, or release artifact. It does not authorize ZIP identity implementation, cleanup of existing duplicate rows, new features, or unrelated architecture work.

**DESIGN_ONLY**
