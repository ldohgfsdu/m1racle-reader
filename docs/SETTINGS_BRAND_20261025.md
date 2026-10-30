# 设置页纯母标品牌区交付报告

日期：2026-10-25  
候选：Release `1.0.0 / 82`  
工作树：`D:\03_M1racle Reader\reader-audit-fixes-20261024`  
证据目录：`D:\03_M1racle Reader\output\settings-brand-20261024-new`

## 交付结果

| 项目 | 结果 |
|---|---|
| APK | `.external_outbox/m1racle-reader-release-latest.apk` |
| 版本 | `versionCode=82`，`versionName=1.0.0` |
| 字节 | `27,608,096` |
| SHA-256 | `87CEE1D254C6D2CF23356296D6C1C0DFE9F5156F519B3CAB63CB4EC5ECDF6FB1` |
| 签名 | APK Signature Scheme v2 |
| 证书 SHA-256 | `2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1` |
| 16 KiB 对齐 | 通过 |
| INTERNET 权限 | 不存在 |

构建、Outbox、设备安装包、Download 回读和证据副本五处摘要一致。覆盖安装使用 `adb install -r`；`firstInstallTime` 保持 `2026-10-03 06:05:17`，未卸载、未清除数据。

## 设置页品牌区

- 展开状态：纯母标 M 居中，约 144×74dp；下方只显示一次 “Reader”，约 28sp。
- 母标来自 `branding/m1racle-reader/v1/source/master-trace.svg` 的同一路径，新增 `ic_reader_brand_mark.xml` 裁到墨迹边界并保留少量安全留白。
- 不包含旧 launcher 图标的内嵌 “Reader” 字形，不添加白色或圆角底板。
- 母标使用主题 `onSurface`：深色白标、浅色黑标。
- 向上滚动连续折叠为左上小母标和「设置」；反向回顶完整恢复。
- 母标保持同一宽高比例，从约 144×74dp 缩到约 32×16dp。
- 版本号已从品牌头移除；“关于 Reader”仍提供版本信息。
- 原有单页滚动、Dock 避让和阅读偏好仓库保持。

本轮只改设置页品牌头；launcher 图标、首页名称、文件菜单和阅读业务未改。

## 自动化验证

| 范围 | 结果 |
|---|---|
| 品牌头测试 | 5 tests，包含 Reader-only 语义检查 |
| 设置页测试 | 2 tests |
| 首页菜单与材质契约 | 14 tests |
| 重点集合计 | 21 tests，0 failures，0 errors，0 skipped |
| `lintRelease` | 成功；0 errors，179 warnings，6 hints |
| `assembleRelease` | 成功，`versionCode=82` |

新增 warning 是母标矢量长路径的性能提示，与旧 launcher 图标同类；静态检查无 error。

前轮全量 Release 的 1683 tests / 2 skipped 继续属于 81 基线，本轮未重复全量测试。

测试过程中发现新 header 变短后，旧第一次真实滑动手势可能直接达到折叠下限。测试代理仅把第一次滑动从 `.62→.48` 缩短为 `.62→.57`；全部几何、连续、回顶、大字和严格断言保留。产品未为旧测试制造额外空白。

## 原生设备验证

设备：`emulator-5554`，Android 35，720×1600，density 280。

- 深色展开：白母标、单次 “Reader”，无底板。
- 深色收起：左上纯母标 +「设置」。
- 深色回顶：恢复居中母标与 “Reader”。
- 浅色展开：黑母标、单次 “Reader”，无底板。
- 浅色收起与回顶通过。
- 2× 系统字号展开与收起通过，不裁切、不叠字。
- 短录屏覆盖展开、折叠与回顶。
- 书库保持可读，crash buffer 为空。

### 代表截图

- `emulator/brand82-dark-expanded.png`
- `emulator/brand82-dark-collapsed.png`
- `emulator/brand82-dark-restored.png`
- `emulator/brand82-light-expanded.png`
- `emulator/brand82-light-collapsed.png`
- `emulator/brand82-light-restored.png`
- `emulator/brand82-font2-expanded.png`
- `emulator/brand82-font2-collapsed.png`

### 手势录屏

`emulator/M1racle-brand82-scroll.mp4`

## 设置还原

应用阅读偏好测试前后 DataStore 文件逐字节一致：

- 字号：小 / `0.9`
- 行距：默认 / `1.55`
- 页边距：标准 / `Standard`
- 文件 SHA-256：`78CFBDCC11C3CF6001DD441E52A9E3CD1D169CE1C070AD1D2E3BB8CCBF0583E1`
- 未使用“重置默认”

系统最终值：

- `font_scale=1.0`
- `animator_duration_scale=null`
- `window_animation_scale=1.0`
- `transition_animation_scale=1.0`
- 夜间模式：`yes`
- 720×1600，density 280

## 状态分类

| 项目 | implemented | automated_verified | device_verified | pending |
|---|---:|---:|---:|---:|
| 纯母标 / 单次 Reader | 是 | 是 | 是 | 否 |
| 无白底 / 主题反色 | 是 | 是 | 是 | 否 |
| 展开 / 收起 / 回顶 | 是 | 是 | 是 | 否 |
| 2× 字号快速回归 | 是 | 是 | 是 | 否 |
| 82 正式签名 Release | 是 | 是 | 是 | 否 |
| 物理 Android 手机验收 | — | — | — | 是 |

## 证据与冻结

- 构建日志：`assembleRelease-final.log`
- Lint 日志：`lintRelease-final.log`
- 重点测试日志：`testQa-final-21.log`
- 失败样本仅作历史：`TEST-SettingsBrandHeaderTest-rerun2-failed.xml`
- 原始编译环境失败：`build-82-brand-qa.log`；单进程确认成功：`compileQa-rerun1.log`

最终源码和证据清单由 `source-frozen.json`、`evidence-manifest.json` 共同记录，排除签名秘密、缓存、应用数据和用户文档。

## 边界

- 未提交、未推送、未公开发布。
- 未修改原 `D:\03_M1racle Reader\reader` 工作树。
- 未更换生产签名。
- 本轮只交付 Release。
