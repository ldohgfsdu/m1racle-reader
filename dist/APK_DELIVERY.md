# APK 交付规则

## 构建能力（保留，勿删）

| 变体 | 用途 | Gradle |
|------|------|--------|
| debug | 功能验证 | `:app:assembleDebug` |
| release | 人工实机验收交付 | `:app:assembleRelease` |
| benchmark | Macrobenchmark / FrameTiming | `:app:assembleBenchmark` + `:benchmark:assembleBenchmark` |

`:benchmark` 模块与 app `benchmark` buildType **不得删除**；Macrobenchmark 仍打 benchmark variant。

## 产物落盘

| APK | 路径 | 是否复制到 `/storage/emulated/0/Download/` |
|-----|------|------------------------------------------|
| **Release（交付）** | `dist/markleaf-release.apk` | **是** → `markleaf-release.apk` |
| Debug | `app/build/outputs/apk/debug/` 与 `dist/performance/markleaf-debug.apk` | **否** |
| Benchmark app | `app/build/outputs/apk/benchmark/` 与 `dist/performance/markleaf-benchmark.apk` | **否** |
| Benchmark test | `benchmark/build/outputs/apk/` 与 `dist/performance/benchmark-test.apk` | **否** |

## 命名

- 实机验收唯一文件名：`markleaf-release.apk`
- Debug / benchmark 仅工程内或 `dist/performance/`，不进手机 Download。

## 真机 Macrobenchmark

```bash
./gradlew :app:installBenchmark :benchmark:connectedBenchmarkAndroidTest
```

不影响上述 Release 交付路径。

## Release 签名与安装（重要）

- 必须使用 `:app:assembleRelease` 正式输出：`app/build/outputs/apk/release/app-release.apk`
- **禁止** 对 APK 使用 `jarsigner` 二次签名（会破坏未压缩 `.so` 的 16KB page alignment，导致 `INSTALL_FAILED_INVALID_APK` / extract native libraries）
- AGP 已配置 release signing（本地用 debug keystore）+ APK Signature Scheme v2
- 交付到手机：仅复制上述正式产物为 `/storage/emulated/0/Download/markleaf-release.apk`
