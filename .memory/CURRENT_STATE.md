# CURRENT_STATE

**Updated:** 2026-07-15
**Active object:** `R8_MARKDOWN_RELATIVE_ASSET_RESOLUTION_V1` (`DEVICE_ACCEPTED`)
**Baseline commit:** `f34b57d0dc929a5bd7f1ee0d4fd8f5e62450c726`
**Frozen direction:** R7 H1.5 / H1.5A remains **rejected / device_failed / frozen**.

## R8 Markdown relative asset V1

```yaml
r8_markdown_relative_asset_v1:
  scope: capability_tiered_safe_degradation
  implementation: complete
  independent_review: APPROVED_FOR_DEVICE_ACCEPTANCE
  host_tests: pass_related_suites_133
  lint_debug: pass_0_errors
  assemble_debug_qa: pass
  device_acceptance: DEVICE_ACCEPTED
  device: 25098PN5AC Android16
  evidence: dist/r8-device-acceptance/
  commit: not_done
  overall: DEVICE_ACCEPTED
```

### Semantics (V1)

- Single entry: `MarkdownAssetResolver` + explicit `MarkdownDocumentBase`
- App-owned / file directory: relative images within sandbox root (`./`, `../`, spaces, `%20`)
- `content://` single-file grant: relative → controlled `Inaccessible` placeholder
- No tree picker, no sibling URI guessing, no schema, no External Open lifecycle change
- Absolute http/https/data/file/content/android.resource not rewritten

### Host gates (this object)

- `MarkdownAssetResolverTest` + `RelativeImageResolveTest` + `PackageImageIntegrationTest`: **PASS**
- markdown + image + packageimport + share unit tests: **PASS**
- Full release package / device UI: **not** run for this object

### Reports / fixtures / evidence

- `docs/R8_MARKDOWN_RELATIVE_ASSET_V1_IMPLEMENTATION_REPORT.md`
- `docs/R8_MARKDOWN_RELATIVE_ASSET_V1_REVIEW_REPORT.md`
- `docs/R8_MARKDOWN_RELATIVE_ASSET_V1_DEVICE_ACCEPTANCE_REPORT.md`
- `testdata/r8_relative_asset_v1/` (+ `HUMAN_ACCEPTANCE_CHECKLIST.md`)
- `dist/r8-device-acceptance/` (screens, ui dumps, logs)

### Explicitly NOT in V1

- External Open single-file grant cannot reliably open sibling assets
- Folder grant / workspace mode
- H1.5A / ThreePageStrip / tile pipeline changes
- remote / push / auto-commit

## Prior: external_open_single_active_session

```yaml
external_open_single_active_session:
  overall: device_accepted
  checkpoint_commit: f34b57d0dc929a5bd7f1ee0d4fd8f5e62450c726
```

Still valid; R8 must not regress session prepare→flush→commit semantics.

### Frozen

```yaml
r7_h1_5a:
  overall: device_failed
  status: frozen
```
