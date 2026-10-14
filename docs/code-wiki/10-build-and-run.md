# 10 构建与运行

[返回目录](README.md)

本篇命令从当前脚本/构建配置核对得出；本次编写 Wiki 没有重新构建 APK、安装应用或运行设备测试。签名值、密码和证书私钥不应写入文档或日志。

## 工具链

需要 JDK 17、Gradle Wrapper 9.5.0、Android SDK Platform 对应 compileSdk 37、Build Tools 36.0.0 与 platform-tools。系统最低 Android 12/API 31。先按本机 SDK Manager 确认可用的 Platform 37 包名称；工作区旧 README 使用 `platforms;android-37.0` 的安装包写法。

本工作区 [reader-env-local.ps1](../../../reader-env-local.ps1) 将 JAVA_HOME、GRADLE_USER_HOME、ANDROID_HOME/ANDROID_SDK_ROOT 与 AVD 目录配置到 `.build-tools/`，并检查 Java、ADB 和 Wrapper 缓存。脚本在仓库外，因此只克隆 `reader/` 时需要自行提供等价工具链。

## 本工作区 Windows 入口

在 `D:\03_M1racle Reader` 执行：

```powershell
# 默认：全部 QA JVM 单测 + Release lint
.\reader-build-local.ps1

# 正式签名 Release：默认单测、lint、assembleRelease
.\reader-build-local.ps1 -Release

# 只执行指定任务；签名和工具链仍由 helper 管理
.\reader-build-local.ps1 -Release :app:assembleRelease
```

`Reader构建.cmd` 是参数透传包装；脚本没有自动 bump versionCode，版本须通过 `app/version.properties` 显式修改。

统一检查脚本在 `reader/` 执行：

```powershell
.\scripts\verify-reader.ps1 -Mode Reading
.\scripts\verify-reader.ps1 -Mode Full
# 确需原生设备测试时才构建隔离 QA 包
.\scripts\verify-reader.ps1 -Mode Full -PrepareDeviceTests
.\scripts\verify-reader.ps1 -Mode Release
```

调用前配置 JDK/SDK，Release 模式还需注入四项签名环境变量。脚本直接使用仓库 Wrapper，不依赖外层 helper。Reading 运行选择的回归测试；Full 运行全部 QA / Release JVM 与 Release lint，不生成 APK；确需设备测试时加 `-PrepareDeviceTests`；Release 在 Full 基础上构建正式 Release。文件锁避免相同入口并发，日志写到仓库 `output/verification`。

## 独立克隆的 Windows 构建

在工程根 `reader/` 配置 JAVA_HOME、ANDROID_HOME，并写入机器本地 `local.properties`。属性文件路径需要正确转义，例如 `sdk.dir=C\:/Android/Sdk`。不要提交本机路径。

```powershell
# 准备好本机 Java/SDK 环境之后
.\gradlew.bat --version
.\gradlew.bat :app:testQaUnitTest :app:lintRelease

# 先由可信本机机制完整注入四个签名环境变量，再执行
.\gradlew.bat :app:assembleRelease
```

## Linux/macOS 源码构建

在工程根使用已配置的 JDK 17 和 Android SDK：

```bash
./gradlew --version
./gradlew :app:testQaUnitTest :app:lintRelease
# 安全注入完整 Release 签名环境后
./gradlew :app:assembleRelease
```

ARM 主机若需 aapt2 包装，应仅在本机 `~/.gradle/gradle.properties` 配置 `android.aapt2FromMavenOverride`；不要给所有 Windows/x86 主机提交统一的 Unix override。当前 Windows 本机工具链 helper 不适用于其他主机。

## Release 签名门禁

| 环境变量 | 说明 |
|---|---|
| `M1RACLE_RELEASE_STORE_FILE` | 正式 keystore 路径 |
| `M1RACLE_RELEASE_STORE_PASSWORD` | keystore 密码 |
| `M1RACLE_RELEASE_KEY_ALIAS` | 正式 key alias |
| `M1RACLE_RELEASE_KEY_PASSWORD` | key 密码 |

四项全无时可配置非发布检查；只提供部分变量会在 Gradle 配置阶段失败。`assembleRelease`、`bundleRelease`、`packageRelease` 依赖 `validatePublicReleaseConfiguration`，检查全部注入、文件存在、非 debug.keystore/非 androiddebugkey、release 绑定正确 signer。

工作区 helper `-Release` 从本机 signing 目录读取材料，只在进程环境注入，结束后还原原环境。构建失败或签名缺失时不能改成交付 Debug、临时签名或未签名包。

## 变体与产物

| 变体 | applicationId | 用途与限制 |
|---|---|---|
| release | `com.m1racle.reader` | 唯一用户交付包；正式签名、压缩/混淆 |
| debug | `com.m1racle.reader` | 和用户包共享包名；工程规则不主动构建/安装/交付 |
| qa | `com.m1racle.reader.qa` | 隔离测试沙箱、测试页面和实验开关 |
| benchmark | `com.m1racle.reader.benchmark` | 不可调试的测量目标，内部 debug signer，禁止当发布包 |

Gradle 成功 `assemble*` 后自动校验 APK 基本 ZIP 条目，导出 latest、时间戳 APK 与 SHA-256；每变体保留最近 3 份时间戳历史。

```text
app/build/outputs/apk/release/app-release.apk
.external_outbox/m1racle-reader-release-latest.apk
.external_outbox/m1racle-reader-release-latest.apk.sha256
.external_outbox/m1racle-reader-release-v1.0.0-<时间戳>.apk
.external_outbox/qa/ ...（非交付变体分目录）
```

Gradle 任务只导出仓库产物。仓库 `verify-reader.ps1 -Mode Release` 在检查、构建及 build/Outbox 摘要通过后，向唯一在线 ADB 设备自动 push 到 `/storage/emulated/0/Download/m1racle-reader-release-latest.apk`；无 ADB 或无唯一设备会报告交付 pending。外层 Windows helper 的 `-Release :app:assembleRelease` 也承担此交付步骤；裸 Gradle 调用须由调用方执行交付。

`exportApksToOutbox` 只重新导出现有 APK，不能用其证明源码已重新构建。不要将旧 latest 文件冒充新构建结果。

## 启动应用与设备验证

ADB 操作前读 [DEVICE_INSTALL_AND_ADB_TEST](../DEVICE_INSTALL_AND_ADB_TEST.md)、[APK_DELIVERY](../APK_DELIVERY.md) 和 [测试数据安全](../ANDROID_TEST_DATA_SAFETY.md)。确认 `adb devices` 的目标为 `device`，再明确选择 serial：

```powershell
adb devices
adb -s <目标设备序列号> install -r .external_outbox/m1racle-reader-release-latest.apk
adb -s <目标设备序列号> shell am start -n com.m1racle.reader/.MainActivity
```

若需要保留手机可安装文件，另执行对应 serial 的 `adb push` 到上述 Download 路径。只使用保留数据的覆盖安装；不把 uninstall、clear data 当作常规准备步骤。

Unix 辅助脚本 `bash scripts/install-release-outbox.sh` 安装并启动 Release，但它选择第一个在线设备；存在多个设备时宜使用明确 serial 的命令。

启动后通过中央导入动作选择 `.md/.markdown/.txt/.pdf/.epub/.zip`，或在文件管理器选择“打开方式/分享”。样例不默认导入，可选择 `docs/sample-large.md`。设备 smoke 和性能验证需要记录实际产物身份与对应场景。

QA 连接测试使用唯一 Windows 准备入口：

```powershell
.\scripts\run-qa-connected-tests.ps1
```

它先调用工作区模拟器 helper 完成 QA 构建/包名检查/安装，再执行 `:app:connectedQaAndroidTest`。`connectedDebugAndroidTest` 和针对主包的 connected 任务会被工程主动拒绝。
