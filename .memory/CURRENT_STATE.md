# CURRENT_STATE

**Updated:** 2026-07-15  
**Active object:** `MARKLEAF_EXTERNAL_OPEN_DEVICE_ACCEPTED_CHECKPOINT` (`device_accepted`)  
**Frozen direction:** R7 H1.5 / H1.5A gesture work remains **rejected / device_failed / frozen** and is **not** accepted by this checkpoint.

## external_open_single_active_session

```yaml
external_open_single_active_session:
  implementation: complete
  host_validation: pass
  self_adb_control: pass
  qa_device_validation: pass
  human_acceptance: accepted
  overall: device_accepted
```

### Host gates (acceptance evidence)

- JVM unit tests: **723/723 PASS**
- lintDebug: **PASS**
- assembleDebug / assembleQa: **PASS**
- compileQaAndroidTestKotlin / assembleQaAndroidTest: **PASS**

### QA device + human

- Self-ADB wireless control: **pass** (serial configured outside repo)
- Isolated QA package automation: **pass**
- Guided human acceptance (4 scenes): **ACCEPTED**
- Production package `com.markleaf.app`: **zero write** (pm path read-only only)

### Scope of this checkpoint

- Unified VIEW/SEND external open via `ExternalOpenCoordinator`
- Confirm-before-replace dialog; cancel keeps session; prepare→flush→commit→nav replace
- ContentProvider metadata (displayName / sizeBytes / MIME)
- URI + SHA-256 content dedup; same-name different content kept separate
- Single active viewer session registration (NavGraph)
- Related JVM/QA tests, fixture provider, and external-open reports

### Explicitly NOT accepted / frozen

```yaml
r7_h1_5a:
  human_video_acceptance: rejected
  overall: device_failed
  status: frozen
```

Do **not** treat H1 / ThreePageStrip / horizontal gesture physics / tile pipeline as device_accepted from this checkpoint.

### Reports

- `docs/MARKLEAF_EXTERNAL_OPEN_SINGLE_ACTIVE_SESSION_IMPLEMENTATION_REPORT.md`
- `docs/MARKLEAF_EXTERNAL_OPEN_GUIDED_HUMAN_ACCEPTANCE_REPORT.md`
- `docs/MARKLEAF_EXTERNAL_OPEN_ATOMIC_SESSION_SWITCH_HARDENING_REPORT.md`
- `docs/MARKLEAF_EXTERNAL_OPEN_SINGLE_ACTIVE_DOCUMENT_AND_METADATA_REPORT.md`

### Repo safety

- No APK / screenshots / logcat / ADB secrets in this commit
- local.properties and markleaf-adb env stay outside git
