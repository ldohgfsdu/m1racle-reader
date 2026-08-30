# R41 Release Optimization Integration Check

Branch: `R41_release_candidate_optimization` (`82b757f`, from `master` `3e44c3f`)  
This round is integration verification only. No new features. No UI change. No PDF behavior change. No further APK size work. **Not merged.**

```text
R38 packaging excludes: present
R40 PDFBox keep narrowing: present
HOST testQaUnitTest: PASS (1355 / 0 / 0 / 0)
Release source diff vs master: packaging + proguard only
MERGE REVIEW REQUIRED
```

## Before

**11.7MB**

| | |
|---|---|
| Documented pre-R38 Release | 11,706,279 bytes |
| SHA-256 | `ec8eef732125bc2126147c8976823a3b0bdfcdda19c7ee83936728afadb0fa1e` |
| Provenance | `docs/R38_SIZE_OPT_BASELINE.md` |

## R38

**7.56MB**

| | |
|---|---|
| After Picnic/SIKE resource pruning | 7,567,342 bytes |
| SHA-256 | `465dd8ee9a6fe387085a9c17277fd68638e6b78c6f5ab35ba906d925d32550e8` |
| Change | five exact `packaging.resources.excludes` |
| Provenance | `docs/R38_SIZE_OPTIMIZATION_REPORT.md` |

## R40

**6.68MB**

| | |
|---|---|
| After PDFBox keep-rule narrowing | 6,682,606 bytes |
| SHA-256 | `7ace751026fc0a774f455694a6dc2c040db7645ff4a3286e962e663dcfe8c7ff` |
| Change | remove app-side `-keep class com.tom_roush.** { *; }` |
| Provenance | `docs/R40_PDFBOX_KEEP_EXPERIMENT_REPORT.md` |

Those two size measurements were taken on the experiment working tree. R41 rebuilds the **same two optimizations on `master`**.

## Final RC

**6,652,842 bytes (6.65MB)**

| Field | Value |
|---|---|
| Branch | `R41_release_candidate_optimization` |
| Base | `master` `3e44c3f9ffec83349cd780cf0eef764d5fbcf9d9` |
| Gradle | `:app:assembleRelease` JDK 17, minify + shrinkResources |
| versionName / versionCode | `1.0.0` / `23` |
| APK bytes | **6,652,842** |
| Decimal MB | **6.652842** |
| SHA-256 | `ad5b021f4234d848b132f7389c2e3b8e5eaed0e8c4597e6177cd9476788ec7d8` |
| APK entries | 233 |
| `classes.dex` stored | 4,369,636 |
| Analyzer file-size | 6,652,842 |
| Analyzer download-size | 4,020,915 |
| Picnic/SIKE resources | **absent** |
| APK Signature Scheme v2 | **Verifies** |
| Signer | ephemeral audit `CN=R41 Release Optimization RC Audit` (keystore destroyed after build) |
| Outbox | `.external_outbox/m1racle-reader-release-r41-rc.apk` |
| Device Download | `/storage/emulated/0/Download/m1racle-reader-release-latest.apk` |

vs documented R40 experiment APK (6,682,606): **−29,764 bytes**. Same 233 entries, same two optimizations. The gap is the product snapshot: R41 is `master`, not the uncommitted R34–R40 working tree.

### APK Analyzer / ZIP breakdown

`apkanalyzer apk summary`: `com.m1racle.reader` `23` `1.0.0`  
`apkanalyzer apk file-size`: 6,652,842  
`apkanalyzer apk download-size`: 4,020,915

ZIP stored / compressed (python `ZipFile`, equivalent bucket view):

| Bucket | Stored | Compressed |
|---|---:|---:|
| `classes.dex` | 4,369,636 | 4,369,636 |
| `assets/` | 4,641,391 | 1,625,540 |
| `res/` | 179,487 | 179,035 |
| `lib/` | 60,292 | 60,292 |
| `META-INF/` | 51,642 | 18,706 |
| other | 379,740 | 271,488 |
| ZIP file | | **6,652,842** |
| ZIP overhead | | 128,145 |

## Verification

### Combined optimizations

Both are present on this branch at once:

1. **R38** — `app/build.gradle.kts` packaging excludes:
   - `org/bouncycastle/pqc/crypto/picnic/lowmc.properties`
   - `org/bouncycastle/pqc/crypto/sike/p434.properties`
   - `org/bouncycastle/pqc/crypto/sike/p503.properties`
   - `org/bouncycastle/pqc/crypto/sike/p610.properties`
   - `org/bouncycastle/pqc/crypto/sike/p751.properties`
2. **R40** — `app/proguard-rules.pro`: no `-keep class com.tom_roush.** { *; }`; retain AAR `SecurityHandler` constructors, `BouncyCastleProvider`, `PDPageLabelRange`, `JPXFilter`.

### Host regression

| Gate | Result |
|---|---|
| `./gradlew :app:testQaUnitTest` | **PASS** 1355 tests, **0 failures**, 0 errors, 0 skipped |
| PDF render / geometry / zoom host tests | **PASS** (included in subset below) |
| search | **PASS** host |
| outline | **PASS** host |
| bookmark | **PASS** host |
| highlight | **PASS** host |
| note | **PASS** host |
| export | **PASS** host |

PDF-related host subset: **886 tests / 97 classes / 0 failures**.

This is unminified QA JVM. It does not re-run the R40 device matrix. Device core matrix for these two optimizations remains the R40 evidence (`docs/R40_DEVICE_ACCEPTANCE_REPORT.md`). Password/AES remains `NOT_SUPPORTED_BY_CURRENT_PIPELINE` (`docs/R40_SECURITY_PDF_ACCEPTANCE.md`).

### Release diff vs `master`

```text
app/build.gradle.kts    | 11 +++++++++++
app/proguard-rules.pro  | 39 +++++++++++++++++++++++++++++++--------
2 files changed, 42 insertions(+), 8 deletions(-)
```

| Kind | Present vs master? |
|---|---|
| packaging excludes | **yes** |
| proguard rules | **yes** |
| Kotlin source diff | **no** |
| UI diff | **no** |
| resource (`res/`) diff | **no** |

## Merge readiness

R41 is eligible for merge review as the combined R38+R40 Release Candidate on `master`.

- No code changes beyond those two files.
- Do **not** merge in this step.
- Wait for explicit confirmation.
