# MarkLeaf Public Release Checklist

This checklist is the authority for the next public MarkLeaf release. It does not alter or replace the immutable `v0.1.0-c-local` history.

## Signing

- [x] `release` has no debug-keystore fallback and is always bound to the dedicated release signing config.
- [x] Signing is injected only through `MARKLEAF_RELEASE_STORE_FILE`, `MARKLEAF_RELEASE_STORE_PASSWORD`, `MARKLEAF_RELEASE_KEY_ALIAS`, and `MARKLEAF_RELEASE_KEY_PASSWORD`.
- [x] Missing/partial injection, `debug.keystore`, and alias `androiddebugkey` are rejected before public output is accepted.
- [x] Keystore/container patterns (`*.jks`, `*.keystore`, `*.p12`, `*.pfx`) and `keystore.properties` are gitignored.
- [ ] The real production/upload keystore has been created outside the repository, backed up securely, and injected by the release owner or CI secret store.
- [ ] The production certificate SHA-256 and Play App Signing/upload-key ownership record have been archived outside the repository.

Expected invocation (values must come from a local secret manager or CI environment, never from committed files or `-P` command-line arguments):

```bash
export MARKLEAF_RELEASE_STORE_FILE=/secure/path/markleaf-upload.jks
export MARKLEAF_RELEASE_STORE_PASSWORD='<secret>'
export MARKLEAF_RELEASE_KEY_ALIAS='<alias>'
export MARKLEAF_RELEASE_KEY_PASSWORD='<secret>'
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64 ./gradlew :app:testQaUnitTest :app:assembleRelease
```

## Version

- [x] Candidate identity is `versionName=1.0.0`, `versionCode=23`.
- [x] Both values come from committed `app/version.properties`; no Git-count or hardcoded fallback exists.
- [x] `versionName` follows SemVer. `versionCode` is a positive monotonic integer and must increase for every artifact uploaded to a distribution channel, including rejected uploads.
- [ ] Before publication, create an annotated immutable `v1.0.0` tag on the exact verified source commit; do not move an existing tag.

## APK identity and hash

- [x] R32 verification APK was built through the injected-signing path. It is a host-verification artifact, not the publishable production binary.
- [x] Verification identity: package `com.markleaf.app`; version `1.0.0` (23); 11,660,123 bytes; APK Signature Scheme v2.
- [x] Verification APK SHA-256: `2cb388d84d64a7f477cff0bcd42b14b8201504a5bd788e4bef9b519ca4b92b31`.
- [x] Disposable signer certificate SHA-256: `4b7bb1848ae56f18530f1a7e660d215a693ac276d9cd171d55f3058f712d36a5`; DN `CN=MarkLeaf R32 Final Verification, O=MarkLeaf Engineering, C=CN`; RSA 3072. The private key was deleted after the build.
- [x] `.external_outbox/markleaf-release-latest.apk` and `/storage/emulated/0/Download/markleaf-release-latest.apk` are byte-identical at the final verification hash above.
- [x] Two forced builds from unchanged source/version and the same signer succeeded. All 238 extracted ZIP entries and central-directory metadata matched.
- [x] Signed APK byte reproducibility result is recorded: build A `24d53f61e1eac07ba1d9b9ad7e64d8d481407369b549b78ea5da87256858596a`; build B `7aba6b5d2dca5c31a8ce81bfa873ad18457299bc7e1de8ddbc1560536e02d1d2`. Only the APK v2 signing block differed, so final signed APKs were **not byte-identical**.
- [ ] Replace the disposable verification identity with the production/upload signer and archive that final artifact's size, digest, signer certificate, immutable source commit, and tag before publication.

## Automated verification

- [x] `JAVA_HOME=java17 ./gradlew :app:testQaUnitTest assembleRelease` passes with securely injected disposable signing.
- [x] QA unit tests: 1344 tests, 0 failures, 0 errors, 0 skipped.
- [x] Two forced release builds from unchanged inputs completed successfully.
- [x] `apksigner verify --verbose --print-certs` passes and the verification signer is not Android Debug.
- [x] APK manifest has no `INTERNET` permission; only Android's package-scoped dynamic-receiver permission is present.
- [x] Missing signing injection and partial signing injection both fail closed without printing secret values.
- [x] No device acceptance is claimed; R32 performed host verification only.

## Room migration policy

- [x] Room v12 is the first public database baseline for `1.0.0`.
- [x] The canonical `12.json` schema is committed and Room schema export remains enabled.
- [x] Do not fabricate v1-v5 schema snapshots or migrations for users who never received a public/store build.
- [x] Existing real v1→v12 development/C-local migrations remain registered and tested where evidence exists; they are not represented as a public compatibility promise.
- [x] The debug-signed `v0.1.0-c-local` package cannot upgrade in place to a production-signed public package because Android requires signer continuity.
- [ ] Every public schema change after `1.0.0` must add a reviewed migration from v12 (or the then-current public schema), export the new JSON snapshot, and pass migration/cascade tests before release.

## Known limitations

- The real production signing key is intentionally unavailable to the repository; R32 host verification uses a disposable, non-debug injected key and is not the publishable artifact.
- APK build payload is reproducible under unchanged inputs, but the final v2 signing block is non-deterministic with the exercised RSA signer; archive and distribute one exact hash rather than assuming a rebuild has the same digest.
- Backdrop 2.0.0 currently forces a Compose/Kotlin/AGP compatibility workaround: metadata/AAR gates and the incompatible Compose runtime lint registry remain excluded as documented in R31.
- Room v1-v5 schema snapshots do not exist. Public compatibility starts at v12; no historical snapshots were reconstructed.
- Transition from the debug-signed C-local package to the production-signed public package requires uninstall/reinstall and does not preserve app-private data.
- Device install, upgrade, UI, interaction, and migration acceptance are outside R32 unless separately recorded.
- The R32 verification ran from HEAD `76ebea5` plus the existing uncommitted R23-R32 worktree. A public artifact still requires one reviewed, clean, immutable source commit and tag.

## R32.1 device acceptance

- [x] RC 1.0.0 (23) installed on Xiaomi/POCO `25098PN5AC` / Android 16 and the pulled installed APK matched the outbox SHA-256 and signer.
- [ ] Stable cold launch: **FAIL** — RenderThread native `SIGSEGV`, likely stack overflow, reproduced 5/5 times in Xiaomi `MiBackgroundBlurBlend`/render-tree preparation.
- [ ] Home, Reader, Annotation, Search, long-reading, and annotation-density acceptance: **BLOCKED** by the startup crash.
- [ ] Public release device gate: **NO-GO** until RC-DA-001 is diagnosed, fixed in a separate task, and the full matrix is rerun.
- Evidence: `docs/R32_1_DEVICE_ACCEPTANCE_REPORT.md`.

## R32.3 / R32.4 native-crash closure

- [x] The ancestor Backdrop cycle was removed without changing GlassMaterial parameters or deleting the Glass engine.
- [x] R32.3 host gate passed: 1,350 QA unit tests and `assembleRelease` with the disposable verification signer.
- [x] R32.3 verification APK SHA-256 is `387c0021be6a68a0156fd23481f41614e0c7583d20390f3b12eae0a1fbbc215f`.
- [x] On the original affected Xiaomi Android 16 device, R32.4 cold-started 5/5 times with no MarkLeaf RenderThread native crash or new tombstone.
- [x] Home/Library/Bookmarks/Settings Dock, Reader, Annotation Sheet, and Search smoke paths passed.
- [ ] Highlight Overlay restoration remains unverified because the accepted document had no pre-existing fixture and the R32.5 retry lost ADB before selection injection.
- [x] The R32.2 native-crash blocker is closed for the R32.3 verification APK.
- Evidence: `docs/R32_3_GLASS_BACKDROP_TOPOLOGY_REPORT.md` and `docs/R32_4_GLASS_REACCEPTANCE_REPORT.md`.

## R32.5 production gate

- [x] Candidate version remains `1.0.0 (23)`.
- [x] QA unit tests were freshly forced on 2026-08-27: 1,350 tests, 0 failures, 0 errors, 0 skipped.
- [x] R32.6 reviews and commits the complete R23–R32.6 source with a clean worktree.
- [ ] The real production/upload signer is securely injected.
- [ ] The canonical production APK signer, manifest identity, size, and SHA-256 are verified and archived.
- [ ] Annotated immutable tag `v1.0.0` points to the exact verified production commit.
- [ ] The production APK, digest, test evidence, and device-acceptance reports are archived as one release set.
- Current decision: **NO-GO**. Evidence: `docs/R32_5_PRODUCTION_RELEASE_GATE_REPORT.md`.

## Publication approval

- [ ] All unchecked production-owner items above are complete.
- [ ] Privacy/store listing, licenses, screenshots, support contact, and distribution policy have separate owner approval.
- [ ] Final source is clean, immutable, tagged, and its published APK/AAB digest is archived.
- [ ] Release owner explicitly approves publication.
