# V1_0_0_FINAL_CANDIDATE_BUILD_REPORT

任务：V1_0_0_FINAL_CANDIDATE_BUILD。日期：2026-09-05（Asia/Hong_Kong）。

**结果：Release 构建成功，最终候选 APK 的源码、版本、签名与 SHA-256 已建立唯一对应关系。**
本次只构建既有源码并生成证据；未修改产品代码、版本或构建配置，未创建/移动 tag，
未发布 GitHub Release，未修改 Draft asset，未安装设备。

## 1. 前置与构建后源码检查

| 项目 | 结果 |
|---|---|
| Worktree | `/root/code/MarkLeaf-101-canonical` |
| Branch | `release-1.0.0-canonical` |
| Commit SHA（构建前后相同） | `691cb65dd14459e2ee35c5aba01a9076832099bd` |
| 构建前 working tree | clean；`git status --porcelain=v1 --untracked-files=all` 为空 |
| 构建结束 working tree | clean；生成本报告前再次确认为空 |
| 版本事实源 | 已提交的 `app/version.properties`，1.0.0 / 23，未修改 |
| Branch/tag refs | 构建前后逐字一致 |
| 现有 v1.0.0 tag | 仍指向 `a43633adcd4cd8a877cdccd96ec3933209722097`，未移动 |

本报告生成后，本 worktree 只新增本报告这一项未跟踪文档；所有 tracked 文件仍无修改。
忽略的构建产物、日志及环境文件不属于源码修改。用户明确的本任务覆盖旧 `.memory`
中的历史会话目标；没有按旧路线图启动其他开发。

## 2. 构建执行

| 字段 | 实测值 |
|---|---|
| 命令 | `./gradlew :app:assembleRelease --no-daemon` |
| 工作目录 | `/root/code/MarkLeaf-101-canonical` |
| JDK | `/usr/lib/jvm/java-17-openjdk-arm64`（JDK 17） |
| Gradle wrapper | 8.11.1，未修改 |
| Build started | `2026-09-05T09:05:44.455627+08:00` |
| Build finished | `2026-09-05T09:08:29.132709+08:00` |
| Build exit code | `0` |
| 构建次数 | 1 次 assembleRelease；未重签、未重建第二种候选字节 |
| 变体 | release；沿用 minifyEnabled + shrinkResources |

构建摘要：

```text
PUBLIC_RELEASE_IDENTITY versionCode=23 versionName=1.0.0
OUTBOX: m1racle-reader-release-v1.0.0-20260905-090827.apk (6513 KB) → /root/code/MarkLeaf-101-canonical/.external_outbox
OUTBOX: m1racle-reader-release-latest.apk (latest)
BUILD SUCCESSFUL in 2m 43s
53 actionable tasks: 24 executed, 29 up-to-date
```

使用现有 R46 production 签名材料，通过既有四变量环境接口注入：
`M1RACLE_RELEASE_STORE_FILE`、`M1RACLE_RELEASE_STORE_PASSWORD`、
`M1RACLE_RELEASE_KEY_ALIAS`、`M1RACLE_RELEASE_KEY_PASSWORD`。
构建前通过 keytool 读取证书并核对 production fingerprint；不生成新密钥，
不复制密钥/密码到仓库，不在命令参数或报告中记录秘密。仅构建子进程持有注入环境，
调用结束后父脚本移除其环境映射，使用 `--no-daemon` 限定本次 Gradle 进程。

## 3. 唯一最终候选身份

| 字段 | 实测值 |
|---|---|
| Source commit | `691cb65dd14459e2ee35c5aba01a9076832099bd` |
| APK bytes | **6,669,642** |
| APK SHA-256 | `19adf629e8f380c8ca49f946dfa740b6489e425d2204ea89e192d6f7c7e5fc89` |
| package name | `com.m1racle.reader` |
| versionName | `1.0.0` |
| versionCode | `23` |
| signer certificate SHA-256 | `2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1` |
| signer identity | R46 production，非 Android Debug |
| signature verification | `apksigner verify --verbose --print-certs` exit 0，Verifies |
| Signature Scheme v2 | true |
| INTERNET permission | 二进制 Manifest 未声明 |
| 核验时间 | `2026-09-05T09:08:49.028117+08:00` |

版本与包名来自 `aapt dump badging` 的二进制 Manifest，不凭文件名或版本文件推断。
完整签名输出：

```text
Verifies
Verified using v1 scheme (JAR signing): false
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): false
Verified using v3.1 scheme (APK Signature Scheme v3.1): false
Verified using v4 scheme (APK Signature Scheme v4): false
Verified for SourceStamp: false
Number of signers: 1
Signer #1 certificate DN: CN=M1racle Reader, OU=Android, O=M1racle, L=China, C=CN
Signer #1 certificate SHA-256 digest: 2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1
Signer #1 certificate SHA-1 digest: fd5bf03bd6bc5e4072a647750666818a041fb661
Signer #1 certificate MD5 digest: fa148567cfdae4f9aa548030af5edcd5
Signer #1 key algorithm: RSA
Signer #1 key size (bits): 4096
Signer #1 public key SHA-256 digest: 7125eaacbbb6f0c92665229730897cfa88215ee018ae6e67c5decf9b6ad0fc0e
Signer #1 public key SHA-1 digest: 788eb75ab58db08d574f3c6282230e066ab5f109
Signer #1 public key MD5 digest: 1df84dfa859b66c002aae50acf45db69
```

## 4. APK 路径与交付一致性

下列文件均已逐个计算 SHA-256，全部等于上表唯一 hash：

- Gradle 原始输出：`/root/code/MarkLeaf-101-canonical/app/build/outputs/apk/release/app-release.apk`
- 自动导出的时间戳包：`/root/code/MarkLeaf-101-canonical/.external_outbox/m1racle-reader-release-v1.0.0-20260905-090827.apk`
- canonical outbox latest：`/root/code/MarkLeaf-101-canonical/.external_outbox/m1racle-reader-release-latest.apk`
- 内部存储 Download latest：`/storage/emulated/0/Download/m1racle-reader-release-latest.apk`
- 按 commit/hash 命名的证据归档副本：`/root/code/MarkLeaf-101-canonical/.external_outbox/final-candidate-691cb65-20260905-090544/m1racle-reader-final-candidate-691cb65-19adf629e8f3.apk`

assembleRelease 成功后，已按 AGENTS.md 执行固定 Download 拷贝，并同步 `.sha256`。
自动生成的时间戳包、outbox latest、Download latest 的 checksum sidecar 已与 APK 实算 hash 对比。
证据归档是同一 APK 的字节副本，不是第二次构建，也没有二次签名。

历史 `a43633a / e8c0431a…` 是旧 Draft/tag 基线；现有远端 Draft 保持原样。
本次仅将 canonical outbox latest 与 Download latest 更新为新候选。
历史工作区 `/root/code/MarkLeaf` 的旧 release-latest 不属于本次候选入口，也没有拿它参与构建。
未来验收应以本报告的完整 commit + hash + signer 为准，不能仅凭同名 latest 或 1.0.0/23 认包。

## 5. 验证边界

| 项目 | 状态 |
|---|---|
| 产品代码实现 | 使用既有 691cb65；本轮无代码修改 |
| assembleRelease | automated_verified：exit 0，BUILD SUCCESSFUL |
| APK Manifest / SHA-256 / production signer / v2 signature | automated_verified |
| 构建原件、outbox、Download、证据归档一致性 | automated_verified |
| QA unit tests | NOT_RUN：用户限定只构建；没有把历史 1393 项记录称为本轮运行 |
| 独立 lintRelease | NOT_RUN |
| 设备安装 / 启动 / UI / 升级验收 | NOT_RUN：用户明确禁止安装；本轮未探测或操作设备 |
| Git tag / GitHub Release / Draft asset | 未创建、未移动、未发布、未修改 |

构建中的 `:app:lintVitalAnalyzeRelease` 日志出现 `java.lang.NegativeArraySizeException: -1`
（ASM `Frame.merge` / `MethodWriter.computeAllFrames`）。Gradle 最终仍以 exit 0 完成构建。
本次未修改/禁用 detector 或绕过构建任务，也未重跑独立 lint；因此只声明 APK 构建与身份核验通过，
不据此声明完整静态分析无异常。详见原始构建日志。

## 6. 证据与停止点

证据目录：`/root/code/MarkLeaf-101-canonical/.external_outbox/final-candidate-691cb65-20260905-090544`（gitignored）。

- `assembleRelease.log`：原始构建日志。
- `build-record.json`：构建开始/结束时间、退出码、来源、APK 身份与路径。
- `refs-before.txt`：构建前 branch/tag refs，与构建后再次比较一致。
- `apk-badging.txt`：APK 二进制 Manifest badging。
- `signature-verification.txt`：签名方案与证书核验输出。
- 按 commit/hash 命名的 APK 及 `.sha256`：候选字节归档。

报告另复制到内部存储 `Download/V1_0_0_FINAL_CANDIDATE_BUILD_REPORT.md` 方便读取。
身份闭环前序见 `/root/code/MarkLeaf/docs/V1_0_0_CANDIDATE_IDENTITY_CLOSURE.md`；
该报告关于“最终 APK 尚未建立”的结论是构建前快照，由本报告的新构建证据推进，旧记录不重写。

**本任务完成后停止。设备验收与任何发布动作均未执行。**
