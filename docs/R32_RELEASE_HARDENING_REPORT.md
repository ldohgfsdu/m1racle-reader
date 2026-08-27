# R32 Release Hardening Report

**Date:** 2026-08-27
**Scope:** Public-release engineering closure only; no user feature was added.
**Frozen boundaries honored:** Reader UI, GlassMaterial, Anchor, Annotation, and Search architecture were not modified.
**Acceptance boundary:** Host build, signing, schema-policy, and artifact inspection only; no device acceptance was performed.

## 1. Result

R32 establishes the engineering path for a public MarkLeaf release:

- `release` no longer uses or falls back to Android's debug keystore;
- signing material is accepted only through four environment variables and is never committed;
- the next public identity is explicitly `versionName=1.0.0`, `versionCode=23`;
- Room v12 is declared the first public/store database baseline;
- full QA tests and two forced signed release builds pass;
- compiled/R8/resource payload reproducibility is verified, while signed-APK byte non-determinism is explicitly recorded.

The repository is **engineering-ready for a production owner to inject the real upload/release signer**, but the R32 verification APK is not publishable: its disposable private key was destroyed, the worktree is not an immutable release commit, and no final production tag or device/store acceptance exists.

## 2. Secure release signing

### 2.1 Configuration

Public APK/AAB output requires all of:

- `MARKLEAF_RELEASE_STORE_FILE`
- `MARKLEAF_RELEASE_STORE_PASSWORD`
- `MARKLEAF_RELEASE_KEY_ALIAS`
- `MARKLEAF_RELEASE_KEY_PASSWORD`

The values are read through Gradle environment providers. Passwords are not accepted through committed Gradle/local property files or documented `-P` arguments. Keystore formats and `keystore.properties` are ignored by Git.

The release build type is always bound to its dedicated signing config. `assembleRelease`, `packageRelease`, and `bundleRelease` depend on `validatePublicReleaseConfiguration`, which verifies:

1. all four values are present;
2. the injected keystore is a real file;
3. the filename is not `debug.keystore`;
4. the alias is not `androiddebugkey`;
5. the release build type is bound to the injected config.

Benchmark remains an internal, non-debuggable artifact and explicitly uses the normal debug signing config; it cannot silently consume production credentials.

### 2.2 Negative and positive evidence

- No signing variables: validation fails with a list of required variable names.
- Partial injection: configuration fails and lists only missing variable names.
- No `*.jks`, `*.keystore`, `*.p12`, `*.pfx`, or `keystore.properties` file is tracked or present in the repository.
- Positive verification used a disposable RSA-3072 PKCS12 identity under `/tmp`. The password existed only in the build process environment, and the keystore was deleted automatically when verification ended.
- `apksigner` verified APK Signature Scheme v2 with a non-debug certificate.

The disposable signer proves the injection path; it is deliberately not a substitute for the production/upload key.

## 3. Version policy

`app/version.properties` is now the single committed authority:

```properties
versionCode=23
versionName=1.0.0
```

- `versionName` follows SemVer and represents the user-facing release line.
- `versionCode` is a positive monotonic Android distribution identity. It must increase for every uploaded artifact, even if a store submission is rejected or withdrawn.
- Git commit count is no longer a fallback because it is not monotonic across rebases, branches, shallow clones, or squashes.
- Missing/invalid version properties fail configuration instead of silently changing the release identity.
- The immutable `v0.1.0-c-local` / code 22 history remains unchanged. The future public tag is `v1.0.0` and must be created only on the final reviewed source commit.

## 4. Room v12 public-release strategy

### 4.1 Historical facts

- The closed `v0.1.0-c-local` build used Room schema v6 and an Android Debug signer.
- Current source uses Room v12, exports `12.json`, and retains the real contiguous v1→v12 development migration chain.
- There has been no public/store production-signed MarkLeaf database release.
- Android will not install a production-signed build as an update over the debug-signed C-local package, regardless of database migrations.

### 4.2 Public policy

Room **v12 is the first public database baseline** for MarkLeaf 1.0.0. R32 does not invent v1-v5 schema snapshots, add synthetic migrations, or claim those development schemas as public compatibility contracts.

Existing migrations remain because they are real development/C-local history and have current test value; no migration code is added or removed in R32. The first future public schema change must migrate from v12 to v13, export `13.json`, preserve cascade/foreign-key behavior, and pass migration tests before release.

Known transition limitation: a C-local user must uninstall the debug-signed package before installing a production-signed public build. App-private data is not preserved by that uninstall. R32 does not expand scope into an export/import migration feature.

## 5. Repeatable release-build verification

### 5.1 Executed builds

Using one disposable injected signer and unchanged source/version inputs:

1. `:app:testQaUnitTest assembleRelease`
2. `:app:assembleRelease --rerun-tasks`
3. an additional retained two-build diagnostic pair with `--rerun-tasks`

All release builds completed successfully. Every successful assemble copied the latest APK to `/storage/emulated/0/Download/markleaf-release-latest.apk` as required.

### 5.2 Reproducibility result

Diagnostic build hashes:

- build A: `24d53f61e1eac07ba1d9b9ad7e64d8d481407369b549b78ea5da87256858596a`
- diagnostic build B: `7aba6b5d2dca5c31a8ce81bfa873ad18457299bc7e1de8ddbc1560536e02d1d2`

The two signed APKs were not byte-identical. Extraction and hashing showed all **238 ZIP entries** were byte-identical, and `zipinfo` showed identical central-directory metadata. The only remaining difference was the APK v2 signing block, consistent with randomized signing behavior.

Therefore:

- repeatable compilation, R8, resource shrink, packaging payload: **PASS**;
- repeatable generation of valid signed APKs: **PASS**;
- byte-for-byte reproducibility of the final signed APK: **NOT ACHIEVED**.

Release operations must archive and publish one exact signed artifact/hash. They must not assume that rebuilding the same source produces the same final digest. Play App Signing may also replace the upload signature, so store-delivered certificate/artifact identity must be recorded separately where applicable.

## 6. Final verification artifact

| Field | Value |
|---|---|
| Path | `.external_outbox/markleaf-release-latest.apk` |
| Download copy | `/storage/emulated/0/Download/markleaf-release-latest.apk` |
| Package | `com.markleaf.app` |
| Version | `1.0.0` / code `23` |
| Size | `11,660,123` bytes |
| SHA-256 | `2cb388d84d64a7f477cff0bcd42b14b8201504a5bd788e4bef9b519ca4b92b31` |
| Signature | APK Signature Scheme v2; RSA 3072 |
| Disposable cert SHA-256 | `4b7bb1848ae56f18530f1a7e660d215a693ac276d9cd171d55f3058f712d36a5` |
| INTERNET permission | Absent |
| Outbox/Download identity | Byte-identical |

The disposable key no longer exists, so this artifact is for host verification/internal inspection only and cannot form a maintainable public update line.

## 7. Automated verification

| Gate | Result |
|---|---|
| R32 hardening contracts | 5/5 PASS |
| `:app:testQaUnitTest` | 1344 tests; 0 failures; 0 errors; 0 skipped |
| Required signed `assembleRelease` | PASS |
| Forced second release build | PASS |
| Release payload comparison | 238/238 entries identical |
| Signed APK byte comparison | Different signing blocks; hashes differ |
| `apksigner verify --verbose --print-certs` | PASS; non-debug disposable signer |
| Manifest permission inspection | PASS; no INTERNET |
| Device install/upgrade/UI acceptance | NOT RUN |

## 8. Known limitations and remaining publication gates

1. A real production/upload key must be created and backed up outside the repository, injected by its owner/CI, and its certificate digest archived.
2. The existing R23-R32 worktree is not yet one reviewed, clean, immutable release commit. The production artifact and `v1.0.0` tag must be created from that final commit.
3. Final signed APK hashes are not deterministic because the exercised signing block is randomized; archive one canonical artifact rather than rebuilding by hash expectation.
4. The Backdrop-driven Compose/Kotlin/AGP metadata, AAR, and lint-registry compatibility exclusions documented by R31 remain.
5. C-local debug-signed installations cannot upgrade in place to the production-signed public package.
6. R32 did not perform device install, migration, Reader, UI, interaction, performance, Play Console, or store-policy acceptance.

## 9. Release decision

**R32 engineering hardening: implemented and host-verified.**

**Public publication: pending production-owner actions.** The secure path, identity, schema baseline, tests, and artifact ledger are ready, but publication must wait for a real protected signer, immutable source/tag, final canonical artifact hash, and the unchecked approval items in `RELEASE_CHECKLIST.md`.
