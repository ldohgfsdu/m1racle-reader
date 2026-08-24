# MarkLeaf D-1.0 ZIP Identity Audit

## 0. Result

| Field | Value |
|---|---|
| Date | 2026-08-24（UTC+8） |
| Release baseline | `v0.1.0-c-local` → `604d404978b5f09fdb4a9c825259ef6793f59949` |
| Audit scope | ZIP identity across FAB/SAF and VIEW/SEND external open |
| Method | Read-only call-chain/schema/test inspection plus deterministic fixture branch simulation |
| Production code / schema / UI | Unchanged |
| Release tag / artifact | Unchanged |
| Final status | **IDENTITY_ISSUE_CONFIRMED** |

The issue is confirmed, with an important condition: it is not true that every FAB-first reopen immediately duplicates. FAB-first followed by external open with the exact same raw URI can reuse the FAB row through a legacy URI-id fallback. Cross-entry delivery commonly supplies a different URI for the same bytes, and that case creates two rows. The reverse order—VIEW/SEND first, then FAB—creates two rows even when both deliveries use the same raw URI.

## 1. Baseline and audit boundary

- Current production/build inputs are byte-identical to the release tag; commits after `604d404` are documentation-only.
- No source file, Room schema, UI, test, version, tag, or APK was modified.
- No `assemble*`, migration, database write, or device action was performed.
- Existing tests were inspected but not changed or rerun. The simulation below evaluates the verified production branch conditions using an existing repository ZIP fixture.

## 2. ZIP import entry chains

### 2.1 FAB / SAF path（Path A）

```text
HomeScreen ActivityResultContracts.OpenDocument
  -> HomeViewModel.onImportDocument(uri)
  -> DocumentRepository.importDocument(uri)
  -> stableUriId(uri.toString())
  -> documentDao.getById(uriHash)
  -> importZipPackage(sourceUri, id=uriHash, existing=that exact id only)
  -> ZipPackageImporter.extract(..., files/packages/<uriHash>)
  -> DocumentEntity(id=uriHash, uri=file://.../<entry.md>)
  -> documentDao.upsert
```

Evidence:

- `HomeScreen.kt:139-142` launches SAF `OpenDocument` and passes the URI to the ViewModel.
- `HomeViewModel.kt:88-91` calls only `repository.importDocument(uri)`.
- `DocumentRepository.kt:533-550` computes `id = stableUriId(uriString)` before the ZIP branch and looks up only `getById(id)`.
- `DocumentRepository.kt:627-705` extracts into `files/packages/<id>`, changes the stored URI to the extracted Markdown entry, and upserts the URI-derived id.
- Path A never computes the original ZIP SHA-256 and never binds `DocumentIdentityIndex`.

### 2.2 VIEW / SEND external-open path（Path B）

```text
MainActivity launch/onNewIntent
  -> ExternalOpenCoordinator.onIncomingIntent
  -> ExternalDocumentImporter.prepare
     -> canonicalExternalKey
     -> URI/index/legacy-id lookup
     -> copy original ZIP bytes + SHA-256
     -> content lookup
     -> NewContent(documentId="c:<zipSha256>") if no match
  -> ExternalOpenCoordinator.confirmPending
  -> ExternalDocumentImporter.commitPrepared
  -> commitZipPackage
  -> ZipPackageImporter.extract(..., files/packages/c:<zipSha256>)
  -> DocumentEntity(id="c:<zipSha256>", uri=file://.../<entry.md>)
  -> Room upsert, then sidecar identity-index bind
```

Evidence:

- `MainActivity.kt:167-175` routes new VIEW/SEND deliveries to `ExternalOpenCoordinator`.
- `ExternalOpenCoordinator.kt:121-180` extracts the incoming URI; `:260-346` runs prepare then commit.
- `ExternalDocumentImporter.kt:71-96` derives the external key and performs URI-level lookup.
- `ExternalDocumentImporter.kt:99-197` copies and hashes the original bytes, performs content lookup, and assigns `documentId = "c:$shaHex"` for new content.
- `ExternalDocumentImporter.kt:403-514` extracts a ZIP package, writes the content-derived primary key to Room, and binds the sidecar index with original ZIP SHA and copied-byte size.

VIEW and SEND share this same coordinator/importer path. Their action differs, but their document identity algorithm does not.

## 3. Current identity model

### 3.1 DocumentEntity primary key

`DocumentEntity.id` is the only Room document identity column and the primary key (`DocumentEntity.kt:18-21`). It is also the navigation, reading progress, search, outline/anchor foreign-key, package-directory, and deletion identity.

The exported Room v6 schema confirms:

- `documents.id` is the sole primary key.
- `documents` has no secondary index.
- There is no `content_sha`, `contentSha`, or `contentSha256` column.

This directly contradicts the earlier B4 proposal note that a v6 `contentSha256` column already existed. A future implementation/design must not assume that column is present. This audit does not add it or select a migration.

### 3.2 stableUriId

`DocumentRepository.stableUriId(uriString)` is SHA-256 of the URI string (`DocumentRepository.kt:746-752`). It is stable only for the same string representation of the same provider location. It is not a content hash:

- Different authorities, document IDs, encodings, grants, or provider URI shapes produce different ids for identical bytes.
- Same URI string reused after the underlying provider content changes retains the same id.

It is therefore a location alias, not cross-entry content authority.

### 3.3 content SHA

Path B computes SHA-256 over the copied original ZIP bytes and uses `c:<sha>` as the new primary key. That SHA exists in `PreparedOpen`/`CommitOutcome` and in `DocumentIdentityIndex`, but not in `DocumentEntity` as a separate field.

Path A computes no source ZIP SHA. After extraction, its Room row points at entry Markdown, so the original archive bytes are no longer reachable through `DocumentEntity.uri`.

### 3.4 identity index

`DocumentIdentityIndex` is explicitly a rebuildable acceleration sidecar; its own contract says Room rows plus private library files are authority (`DocumentIdentityIndex.kt:11-17`). It maps:

```text
documentId + canonicalUriIdentity + sha256 + sizeBytes + mimeType
```

Path B binds it after Room commit. Path A does not bind it. `rebuildFromDocuments` can reconstruct only rows whose ids already encode `c:<sha>`, and there is no production call site for that function.

The index cannot be the identity authority because it may be absent, corrupt, pruned, or rebuilt, and it cannot reconstruct the original ZIP SHA for a FAB row.

### 3.5 ExistingLibrary lookup

Path B checks, in order:

1. sidecar `canonicalExternalKey`;
2. legacy `stableUriId(sourceUri.toString())` Room row;
3. sidecar SHA + original ZIP byte size;
4. `DocumentRepository.findByContentSha256`.

The repository fallback first checks primary key `c:<sha>`. Otherwise it filters candidate Room rows by `fileSize` and hashes the file at `DocumentEntity.uri` (`DocumentRepository.kt:67-87`).

For a FAB ZIP row this fallback cannot normally identify the source archive:

- Room `uri` points to extracted entry Markdown, not the ZIP.
- Room `fileSize` prefers entry Markdown size, then extracted-package size, not compressed source ZIP size (`DocumentRepository.kt:653-666`).
- Hashing the entry Markdown cannot equal the original ZIP byte SHA except by cryptographic coincidence.

There is also a correctness smell in the legacy URI match: `contentSha256 = doc.id.removePrefix("c:")` (`ExternalDocumentImporter.kt:554-567`). For a FAB row, `doc.id` is a URI hash, so this value is not a content SHA even though it is subsequently stored in the sidecar SHA field.

## 4. Deterministic same-ZIP cases

### 4.1 Fixture

The simulation uses the existing repository fixture:

```text
testdata/r8_relative_asset_v1/fixture_A_app_owned.zip
ZIP SHA-256: 6aaca9a87a8b03a36a71c5497fcc09e17f2d9cf877e2fc4c8827256c8644ff4d
compressed ZIP bytes: 1639
resolved entry: README.md
entry bytes: 217
```

Two URI representations were used for the same fixture bytes:

```text
FAB URI:
content://com.android.providers.downloads.documents/document/msf%3A42
stableUriId = 45e1719877925e92c0e41cd409f5d606576a1f0c0be4939eab5150b92e8c7f88

External URI:
content://com.mi.android.globalFileexplorer.myprovider/root/fixture_A_app_owned.zip
legacy stableUriId = 44b3a7c67982284fbaf1a53b9d287a60f7c594e4ccf23f669c90d6f029d3df4b

Content identity:
c:6aaca9a87a8b03a36a71c5497fcc09e17f2d9cf877e2fc4c8827256c8644ff4d
```

The values use the exact production `stableUriId` formula and the real fixture SHA/size. The branch outcomes below follow the actual lookup order described in §3.5.

### 4.2 Case A — FAB first, then VIEW/SEND

#### A1. FAB import

Room after FAB:

| Field | Value |
|---|---|
| `id` | `45e171...c7f88`（URI hash） |
| `documentType` | `MARKDOWN_PACKAGE` |
| `uri` | `file://.../packages/45e171...c7f88/README.md` |
| `fileSize` | `217`（entry size） |
| durable source ZIP SHA | absent |
| sidecar index entry | absent |

#### A2. External open through a different URI

- canonical external-key index: miss;
- external legacy URI id `44b3...3df4b`: does not match FAB id `45e1...c7f88`;
- content index: miss because FAB never bound it;
- primary key `c:6aaca...ff4d`: miss;
- fallback candidate: FAB `fileSize=217` does not match ZIP copied bytes `1639`; even without that filter, stored `uri` hashes `README.md`, not the ZIP.

Path B therefore creates the content row:

| Field | Value |
|---|---|
| `id` | `c:6aaca...ff4d`（original ZIP content hash） |
| `documentType` | `MARKDOWN_PACKAGE` |
| `uri` | `file://.../packages/c:6aaca...ff4d/README.md` |
| `fileSize` | `217`（entry size） |
| sidecar SHA/size | ZIP SHA + `1639` copied bytes |

**Result: two `documents` rows and two extracted package directories.** Their reading progress, title metadata, last-open time, search state, and deletion lifecycle are independent.

#### A-exception. External open with the exact same raw URI

The legacy `stableUriId` lookup finds the FAB row, so this single transition keeps one Room row. However, it labels the URI hash as `contentSha256` and binds that false value to the sidecar. A later different-URI delivery of the same ZIP still cannot content-match and can create the second row.

### 4.3 Case B — VIEW/SEND first, then FAB

#### B1. External open

Path B copies/hashes the ZIP and creates:

```text
id = c:6aaca9a87a8b03a36a71c5497fcc09e17f2d9cf877e2fc4c8827256c8644ff4d
```

Room and sidecar are bound to the content-derived id.

#### B2. FAB import

FAB computes only `stableUriId(fabUri)` and queries only that id before extraction. A 64-character URI hash can never equal the prefixed `c:<zipSha>` convention. FAB does not query the sidecar or content SHA path.

It therefore creates:

```text
id = 45e1719877925e92c0e41cd409f5d606576a1f0c0be4939eab5150b92e8c7f88
```

**Result: two `documents` rows and two extracted package directories.** This result holds even if Path B and FAB receive the exact same raw URI, because FAB never asks whether a `c:<sha>` row already represents those bytes.

## 5. Fields that diverge

| Field/source | FAB row | VIEW/SEND row | Identity role |
|---|---|---|---|
| `DocumentEntity.id` | SHA-256 of URI string | `c:` + SHA-256 of original ZIP bytes | Conflicting primary-key conventions; direct cause of two rows |
| `DocumentEntity.uri` | extracted entry under `packages/<uriHash>` | extracted entry under `packages/c:<zipSha>` | Different app-private trees for the same package |
| source ZIP SHA | not retained | transient model + sidecar; encoded in id | Only Path B has content identity |
| `DocumentEntity.fileSize` | entry/package size | entry/package size | Not source ZIP identity; sidecar separately stores copied ZIP size |
| canonical external key | not recorded | sidecar alias | Location alias only |
| reading/progress metadata | attached to URI-hash row | attached to content-hash row | Diverges independently after duplication |

Display name, `MARKDOWN_PACKAGE` type, title, and extracted entry content can be equal while the primary keys remain different, so UI shows two equivalent recent-library entries.

## 6. Authority determination

For the requested case—byte-identical source ZIP delivered through multiple entry points—the authoritative semantic identity should be **SHA-256 of the original ZIP bytes**.

The durable authority must remain Room, not the sidecar index. In the current model, the already-established content-derived convention `DocumentEntity.id = "c:<sourceZipSha256>"` is the only durable representation that survives loss of the original ZIP and sidecar index. Therefore:

- `DocumentEntity.id` should converge on one canonical content-derived id for new ZIP imports before the first Room upsert.
- `stableUriId` and `canonicalExternalKey` should be aliases that resolve to that canonical Room row, not competing document identities.
- `DocumentIdentityIndex` should remain a rebuildable accelerator, never the source of truth.
- `fileSize`, display name, and extracted-entry URI must not be used as ZIP identity.
- If a future design introduces an explicit Room content-SHA field, that decision requires its own schema/persistence design; the field does not exist today. This audit does not authorize a migration or compatibility path.

This authority rule covers byte-identical ZIP archives. Whether separately repacked archives with equivalent extracted content should deduplicate is a different product policy and is outside D-1.0.

## 7. Existing test coverage gap

Existing tests cover:

- Path B ZIP commits as `MARKDOWN_PACKAGE` and points to extracted Markdown.
- FAB ZIP independently commits as `MARKDOWN_PACKAGE`.
- sidecar file atomicity/corrupt quarantine and SHA+size lookup policy.
- a pure policy assertion that two manually constructed strings `c:<sameSha>` are equal.

No test runs FAB then VIEW/SEND or VIEW/SEND then FAB against one shared Room/DAO and the same ZIP bytes. No test asserts one final document row, one package directory, or one progress identity across entries. No test covers the FAB-row legacy fallback incorrectly treating a URI hash as `contentSha256`.

The absence of a cross-entry regression test explains why both individual entry contracts are green while the composed identity contract is broken.

## 8. Impact and scope conclusion

- Confirmed behavior: duplicate equivalent library records are possible and order B deterministically produces them.
- User impact: duplicate recent-list entries, split reading progress/metadata, and duplicate app-private extraction trees.
- Not observed/implied: source-file deletion, content corruption, crash, or C-local release artifact mutation.
- D-1.0 action: audit and record only. No implementation, schema change, UI change, test change, migration, cleanup, or tag operation occurred.

## 9. Final status

**IDENTITY_ISSUE_CONFIRMED**

The current system has two incompatible primary-key authorities for ZIP packages: URI-string SHA on FAB/SAF and original-content SHA on VIEW/SEND. Because FAB does not participate in content identity and Room has no durable content-SHA field beyond Path B's `c:<sha>` id convention, identical ZIP bytes can be registered as two documents.
