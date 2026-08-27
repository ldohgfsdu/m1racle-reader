# APK 交付规则

**完整真机安装 / ADB 判断协议见：`docs/DEVICE_INSTALL_AND_ADB_TEST.md`（Agent 必读）。**

## 构建能力（保留，勿删）

| 变体 | 用途 | Gradle |
|------|------|--------|
| debug | 功能验证 | `:app:assembleDebug` |
| **release** | **公开候选/人工实机验收交付（minify，要求注入 signer）** | `:app:assembleRelease` |
| qa | 隔离仪器测试 | `:app:assembleQa` |
| benchmark | Macrobenchmark / FrameTiming | `:app:assembleBenchmark` + `:benchmark:assembleBenchmark` |

`:benchmark` 模块与 app `benchmark` buildType **不得删除**。

## 产物落盘（2026-07-20）

| 变体 | 构建输出 | 自动导出（每次 assemble 成功） |
|------|----------|--------------------------------|
| release / qa / debug / benchmark | `app/build/outputs/apk/<variant>/` | **`.external_outbox/markleaf-<variant>-…apk`** + `latest` + `.sha256` |

每个变体只保留最近 **5** 份带时间戳 APK（及对应 SHA-256）；`latest` 始终覆盖更新，防止长期构建
无限占用工作区。该策略只清理由构建生成、且已被 `.gitignore` 排除的 outbox 历史产物。

仅导出不重编：

```bash
./gradlew :app:exportApksToOutbox
```

### 交付优先级

| 场景 | 用哪个 |
|------|--------|
| 给人安装 / 日常验收 | **`.external_outbox/markleaf-release-latest.apk`（~10–11MB）** |
| 仪器测试 | QA 包 `com.markleaf.app.qa` |
| 调试 | debug（一般不交付） |

### 手机 Download

- ADB **离线**时：复制 release 到 `/storage/emulated/0/Download/markleaf-release-latest.apk` 供手动安装
- Debug / benchmark：**不要**默认丢进 Download
- 详见 `DEVICE_INSTALL_AND_ADB_TEST.md`

## 命名

- 时间戳：`markleaf-release-v0.1.0-yyyyMMdd-HHmmss.apk`
- 最新指针：`markleaf-release-latest.apk`

## 真机 Macrobenchmark

```bash
./gradlew :app:installBenchmark :benchmark:connectedBenchmarkAndroidTest
```

## Release 签名与安装（重要）

- `:app:assembleRelease` 不再使用 debug fallback。构建前必须通过环境注入
  `MARKLEAF_RELEASE_STORE_FILE`、`MARKLEAF_RELEASE_STORE_PASSWORD`、
  `MARKLEAF_RELEASE_KEY_ALIAS`、`MARKLEAF_RELEASE_KEY_PASSWORD`；缺失或部分注入会拒绝构建。
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
