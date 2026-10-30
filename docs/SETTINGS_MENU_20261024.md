# 设置页品牌头与首页文件菜单交付报告

日期：2026-10-24  
候选：Release `1.0.0 / 81`  
工作树：`D:\03_M1racle Reader\reader-audit-fixes-20261024`  
证据根目录：`D:\03_M1racle Reader\output\settings-menu-20261024`

## 交付结果

本轮交付正式签名 Release APK：

| 项目 | 结果 |
|---|---|
| 包名 | `com.m1racle.reader` |
| 版本 | `versionCode=81`，`versionName=1.0.0` |
| 文件 | `.external_outbox/m1racle-reader-release-latest.apk` |
| 字节 | `27,604,500` |
| SHA-256 | `CFEAB6B3B3E73F9DDEAA60DEAA34546415EDFAE4620454C652514E85636507F6` |
| 签名 | APK Signature Scheme v2 通过 |
| 证书 SHA-256 | `2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1` |
| 16 KiB 对齐 | `zipalign -c -v -P 16 4` 通过 |
| INTERNET 权限 | 不存在 |

构建 APK、Outbox、设备实际安装包、设备 Download 回读包和证据副本五处摘要一致。`adb install -r` 覆盖安装；`firstInstallTime` 保持 `2026-10-03 06:05:17`，未卸载、未清除应用数据。

## 界面实现

### 设置页品牌头

- 顶部展开状态：108dp 图标居中，应用名在图标下方，版本号位于应用名下方；图标保持白色背景和母标轮廓。
- 向上滚动：图标连续缩放并向左上移动，应用名和版本号淡出，折叠为左侧小图标和「设置」标题。
- 反向滚动：完整恢复居中品牌头，不保留中间状态。
- 父容器保持折叠高度占位；内部使用约束展开高度布局，避免大字号时文字被压成 0 高。
- 图标尺寸、标题尺寸和位置由 `SettingsBrandHeader.kt` 的同一进度值驱动，避免时钟推进造成叠字或跳位。
- 字号、行高和边距继续使用原 `ReaderSettingsRepository`，没有新增第二份阅读偏好。
- 设置正文分组为「阅读 / 文件 / 关于」，行高至少 64dp，尾部避让动态 Dock 与系统导航安全区。

### 首页文件菜单

- 使用完全不透明的 `surfaceContainerHigh` 平台锚定菜单，不再用透明 Popup 模拟玻璃。
- Markdown 独有「编辑正文」；PDF 和 EPUB 按实际能力不显示编辑入口。
- 重命名、导出、文件信息使用统一轮廓图标；移除项单独分隔并使用危险色。
- 普通文档行和首页「继续阅读」卡片共用同一菜单行为。
- 移除只清除应用内副本、进度、书签、收藏和批注，不删除用户原始文件。

### 临时诊断与最终恢复

全量 Release 测试曾因旧菜单结构契约失败。定位期间临时把 `DocumentListRow.kt` 回退到旧玻璃菜单结构，确认失败来自测试契约而不是业务回归；之后已完整恢复为最终实色菜单。三个旧契约测试随后只替换菜单角色断言，保留其余断言。最终冻结源码和最终 APK 均来自恢复后的实色菜单实现。

最终 `DocumentListRow.kt` SHA-256：

`1B77245E2E38FFC348FC8FBA4244CD30D0DB9DF4E48C9680F6B73BEBFA9ACA4D`

## 验证

### 自动化

| 范围 | 结果 |
|---|---|
| 重点 QA 回归 | 4 suites，20 tests，0 failures，0 errors，0 skipped |
| 全量 Release 单元测试 | 216 suites，1683 tests，0 failures，0 errors，2 skipped |
| `lintRelease` | 成功；0 errors，178 warnings，6 hints |
| `assembleRelease` | 成功 |

新增品牌头测试 `SettingsBrandHeaderTest` 的四个方法覆盖：

1. 展开品牌居中，名称位于图标下方。
2. 手势滚动经过中间和折叠状态，再回顶完整恢复。
3. 2× 字体、窄屏下名称和图标不裁切、不叠字。
4. 折叠后主时钟推进，位置和文字布局保持稳定。

全量测试失败证据、契约修正和最终成功的日志均保留：

- `build-settings-menu-fail7-releaseContracts.log`
- `testRelease-rerun8.log`
- `testRelease-rerun8-summary.txt`

### 模拟器

设备：`emulator-5554`，Android 35，720×1600，density 280，夜间模式。

- 冷启动成功，书库保持可读。
- 展开、中间、折叠、回顶四个状态原生验证通过。
- 日间与夜间品牌头原生验证通过。
- 系统字体缩放 2× 下展开和折叠不裁切、不叠字。
- 三项系统动画关闭后状态仍可用，随后全部还原。
- 手势录屏覆盖展开、折叠、中间和回顶过程。
- 普通 Markdown 菜单、首页继续阅读菜单、PDF 菜单和 EPUB 菜单均已打开验证。
- 重命名取消后文件名仍在；文件信息弹窗可正常关闭。
- 从文库移除进入确认弹窗后点「取消」，文件仍在，未删真实文件。
- 导出文件打开系统保存器后按返回退出，未写入文件。
- 崩溃缓冲区为空。

代表截图：

- `emulator/hero-expanded-final.png`
- `emulator/hero-middle-final.png`
- `emulator/hero-collapsed-final.png`
- `emulator/hero-day-expanded-final.png`
- `emulator/hero-font2-expanded-final.png`
- `emulator/hero-anim0-collapsed-final.png`
- `emulator/menu-real-open.png`
- `emulator/menu-normal-open-final.png`
- `emulator/hero-menu-open.png`
- `emulator/pdf-menu-open.png`
- `emulator/epub-menu-open.png`
- `emulator/menu-information-dialog.png`
- `emulator/menu-remove-confirm.png`
- `emulator/menu-export-picker.png`

手势录屏：

`emulator/M1racle-hero-scroll-final.mp4`

## 状态恢复

### 应用阅读偏好

测试前与测试后的 DataStore 文件逐字节一致：

- 字号：小，`fontScale=0.9`
- 行距：默认，`lineHeightScale=1.55`
- 页边距：标准，`contentPaddingLevel=Standard`
- EPUB 字体：默认

证据：

- 测试前快照：`emulator/old81-preferences-readonly/reader_settings.preferences_pb`
- 测试后快照：`reader_settings.preferences_pb`
- 两者 SHA-256 均为 `78CFBDCC11C3CF6001DD441E52A9E3CD1D169CE1C070AD1D2E3BB8CCBF0583E1`

测试过程中未使用「重置默认」。

### 系统设置

最终读回值：

- `font_scale=1.0`
- `animator_duration_scale=null`（测试前原值）
- `window_animation_scale=1.0`
- `transition_animation_scale=1.0`
- 夜间模式：`yes`
- 显示覆盖：720×1600，density 280

## 证据分类

| 项目 | implemented | automated_verified | device_verified | pending |
|---|---:|---:|---:|---:|
| 设置页品牌头展开 / 折叠 / 回顶 | 是 | 是 | 是 | 否 |
| 日间 / 夜间品牌头 | 是 | 是 | 是 | 否 |
| 2× 字体品牌头稳定性 | 是 | 是 | 是 | 否 |
| 系统动画关闭状态 | 是 | 是 | 是 | 否 |
| 首页实色文件菜单 | 是 | 是 | 是 | 否 |
| Markdown 编辑入口条件显示 | 是 | 是 | 是 | 否 |
| 重命名 / 信息 / 移除取消 | 是 | 是 | 是 | 否 |
| 导出选择器取消 | 是 | 是 | 是 | 否 |
| 正式签名 Release 构建 | 是 | 是 | 是 | 否 |
| 物理 Android 手机验收 | — | — | — | 是 |

物理手机未测；本轮结论限定为 Android 35 模拟器。

## 证据与源码冻结

- APK：`output/settings-menu-20261024/m1racle-reader-v81-hero-final.apk`
- 构建日志：`output/settings-menu-20261024/assembleRelease-final.log`
- Lint 日志：`output/settings-menu-20261024/lintRelease-final.log`
- 全量测试日志：`output/settings-menu-20261024/testRelease-rerun8.log`
- 源码冻结：`output/settings-menu-20261024/source-frozen-final.json`
- 证据清单：`output/settings-menu-20261024/evidence-manifest.json`

源码冻结包含：

- 新增 `SettingsBrandHeader.kt`
- 最终 `HubSettingsScreen.kt`
- 最终 `DocumentListRow.kt`
- 新增 `SettingsBrandHeaderTest.kt`
- 三个更新后的菜单 / 页面契约测试
- 全部产品源码、资源、Manifest、版本文件、编辑器源码与构建文件

冻结排除了构建缓存、`node_modules`、`dist`、Outbox、签名私钥、口令、应用数据备份和用户文档。冻结后立即逐文件复算，漂移为空。

## 边界

- 未提交、未推送、未公开发布。
- 未卸载、未清除应用数据，未删除真实文档。
- 未更换生产签名。
- 原 `D:\03_M1racle Reader\reader` 工作树未改动。
- 本轮只交付 Release，不生成 Debug 交付包。

