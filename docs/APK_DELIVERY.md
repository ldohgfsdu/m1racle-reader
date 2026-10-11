# APK 交付规则

**完整真机安装 / ADB 判断协议见：`docs/DEVICE_INSTALL_AND_ADB_TEST.md`（Agent 必读）。**

**构建类型权威规则见 [AGENTS.md](../AGENTS.md#构建类型用户明确要求2026-09-15)：用户只要 Release，禁止主动构建或交付 Debug；缺少正式签名时报告阻塞，不切换变体或签名。**

## 构建能力（保留，勿删）

| 变体 | 用途 | Gradle |
|------|------|--------|
| debug | 仅保留构建能力，用户未明确要求时禁止使用 | `:app:assembleDebug` |
| **release** | **公开候选/人工实机验收交付（minify，要求注入 signer）** | `:app:assembleRelease` |
| qa | 隔离仪器测试 | `:app:assembleQa` |
| benchmark | Macrobenchmark / FrameTiming | `:app:assembleBenchmark` + `:benchmark:assembleBenchmark` |

`:benchmark` 模块与 app `benchmark` buildType **不得删除**。

## 产物落盘（2026-07-20）

| 变体 | 构建输出 | 自动导出（每次 assemble 成功） |
|------|----------|--------------------------------|
| release（唯一交付包） | `app/build/outputs/apk/release/` | **`.external_outbox/m1racle-reader-release-…apk`** + `latest` + `.sha256` |
| qa / debug / benchmark | `app/build/outputs/apk/<variant>/` | `.external_outbox/<variant>/m1racle-reader-<variant>-…apk` + `latest` + `.sha256` |

release 是唯一交付包，位于 outbox 根目录；测试变体（qa / debug / benchmark）分别落在各自子目录，不与交付包混放。
每个变体目录只保留最近 **3** 份带时间戳 APK（及对应 SHA-256）；`latest` 始终覆盖更新，防止长期构建
无限占用工作区。该策略只清理由构建生成、且已被 `.gitignore` 排除的 outbox 历史产物。

仅导出不重编：

```bash
./gradlew :app:exportApksToOutbox
```

### 交付与测试用途

| 场景 | 用哪个 |
|------|--------|
| 给人安装 / 日常验收 | **`.external_outbox/m1racle-reader-release-latest.apk`**；体积以CURRENT_STATUS实测为准 |
| 仪器测试 | QA 包 `com.m1racle.reader.qa` |
| Debug | 禁止主动构建或交付，不替代 Release 验证 |

### 手机 Download

- ADB **离线**时：复制 release 到 `/storage/emulated/0/Download/m1racle-reader-release-latest.apk` 供手动安装
- Debug / QA / benchmark：不作为用户交付包，不复制到 Download
- 仅在本次 Release 构建成功并确认 Outbox 产物已更新后复制；旧包不得作为本次改动交付。
- 详见 `DEVICE_INSTALL_AND_ADB_TEST.md`

## 命名

- 时间戳：`m1racle-reader-release-v0.1.0-yyyyMMdd-HHmmss.apk`
- 最新指针：`m1racle-reader-release-latest.apk`

## 真机 Macrobenchmark

```bash
./gradlew :app:installBenchmark :benchmark:connectedBenchmarkAndroidTest
```

## Release 签名与安装（重要）

- `:app:assembleRelease` 不再使用 debug fallback。构建前必须通过环境注入
  `M1RACLE_RELEASE_STORE_FILE`、`M1RACLE_RELEASE_STORE_PASSWORD`、
  `M1RACLE_RELEASE_KEY_ALIAS`、`M1RACLE_RELEASE_KEY_PASSWORD`；缺失或部分注入会拒绝构建。
- 密钥、密码、`keystore.properties` 不得提交，也不要通过 Gradle `-P` 参数传递。完整发布步骤见
  根目录 `RELEASE_CHECKLIST.md`。
- 成功输出为 `app/build/outputs/apk/release/app-release.apk`（并自动进入 outbox）。
- **禁止** `jarsigner` 二次签名（破坏 16KB page alignment → `INSTALL_FAILED_INVALID_APK`）
- Release 已 **minify + shrinkResources**；QA/debug 保持未压缩便于调试

## ADB

```bash
adb devices -l                    # 先判断
bash scripts/install-release-outbox.sh   # 有 device 时一键装 release
```

无 device → 只交付 Download / outbox 路径，**不写真机 PASS**。
