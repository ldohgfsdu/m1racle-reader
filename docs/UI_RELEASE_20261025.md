# Reader 83 界面整理与 APK 交付

2026-10-25，Release 1.0.0 / 83。当前源码：`D:\03_M1racle Reader\reader-ui-release-20261025`。

## 变更与基线

以 `reader-audit-fixes-20261024` 的完整 82 版为基线，合入原 `reader` 工作树的界面修改，在隔离工作树构建。原两个工作树的现有改动保留；82 基线的 1233 文件摘要复核无漂移。

- 保留当前页 Markdown 编辑、完成后原地回阅读及冻结的 WebView/CodeMirror 资源。
- 保留 data、documents、pdf、epub、packageimport 核心包，与 82 版逐文件一致；不修改 Room17 或用户存储格式。
- 设置页纯母标 M、单次 Reader、主题反色及启动图标沿用 82。二进制 APK 内 `ic_reader_mark`、`ic_reader_brand_mark` 与已验签 82 包逐字节一致。
- 界面颜色使用 Radix 固定色阶和语义角色，维持黑白品牌。1–2 为背景，3–5 为控件，6–8 为边界，11–12 为文字；语义状态使用对应有色色阶。单一色板放在 `com.m1racle.reader.design`，核心解析包不反向依赖 UI。
- 界面常规字号 12 / 14 / 18sp，品牌 Reader 保留 28sp，合计四种；阅读正文字号设置独立保留。减少重复说明、装饰标签和中点分隔，整理按钮及占位内容对齐。
- 边界按物理 0.5px 绘制，避免 Foundation border 将亚像素向上取整；密度 1 / 2 / 3.5 的原生像素回归通过。
- 减少页面切换与面板内部转场，必要的显示隐藏使用约 250ms ease-out；保留手势所需物理回弹。
- 本机 `Reader构建.cmd`、`Reader模拟调试.cmd` 经共享的 `$readerRepository` 指向当前 83 工作树。三个 PowerShell 入口语法和构建入口 `--version` 检查通过；未运行模拟器脚本。

## 自动验证

| 检查 | 本轮结果 |
|---|---|
| 完整 QA | 1836 tests，0 failures，0 errors，2 skipped |
| 完整 Release | 1683 tests，0 failures，0 errors，2 skipped |
| lintRelease | 0 errors，179 warnings，6 hints |
| 正式构建 | assembleRelease 成功，R8 + shrinkResources，正式生产签名 |
| 签名与对齐 | apksigner、zipalign -c -P 16 -v 4 通过 |
| 包身份 | com.m1racle.reader，1.0.0 / 83，minSdk31 / targetSdk35，非 debuggable，无 INTERNET |
| 打包资源 | 35 个主 assets 与源码逐字节一致；两份品牌向量与 82 包一致 |

共同源码的 QA、Release 完整测试在第一次 lint 配置失败前已执行通过。随后仅修正仓库外依赖的本机 `local.properties` 路径转义，编译和测试由 Gradle 复用；冻结源码摘要保持一致，lint 分析强制重跑，未关闭检查、未建立忽略基线。初轮两项回归失败及 lint 缓存旧报告均单独归档，最终结论取 `verification.json`。

跳过项：

```json
{
  "Qa": [
    {
      "class": "com.m1racle.reader.markdown.LaoziAnchorFixtureTest",
      "test": "laozi_fixture_anchorsAndTocPlainTitles"
    },
    {
      "class": "com.m1racle.reader.markdown.MarkdownAssetResolverSecurityTest",
      "test": "symlink_escape_rejected_when_supported"
    }
  ],
  "Release": [
    {
      "class": "com.m1racle.reader.markdown.LaoziAnchorFixtureTest",
      "test": "laozi_fixture_anchorsAndTocPlainTitles"
    },
    {
      "class": "com.m1racle.reader.markdown.MarkdownAssetResolverSecurityTest",
      "test": "symlink_escape_rejected_when_supported"
    }
  ]
}
```

六张截图为 Robolectric 原生 Android Canvas、QA JVM、实色玻璃回退；包含首页、设置、阅读设置的深浅主题。设置截图中的外观预览为 QA 专有入口，正式 Release 隐藏。这些结果属于 automated_verified；本轮没有 device_verified 证据。

## 产物与边界

- 安装文件：`D:\03_M1racle Reader\apk\M1racle-Reader-1.0.0-83.apk`。
- 大小：27,603,994 字节，26.33 MiB。
- SHA-256：`7C8F6541A85C192C8BBE0593D51E6FF88ECD607DDE1CE1FB6C90C9BD8817BF27`。
- 生产证书 SHA-256：`2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1`，与现场重新验签的 82 包一致。
- 构建输出、Outbox 和交付 APK 摘要相同。Manifest 保留 `profileable android:shell=true`，用于 ADB 性能采样。
- ADB 无在线设备，设备 Download 复制、覆盖安装、真实玻璃、手势、阅读恢复及物理手机观感均 pending；不能继承 82 的设备结果。没有卸载、清库或启动 GUI。
- 未提交、未推送、未公开发布。

证据目录：`D:\03_M1racle Reader\output\apk-83-20261025`。`build-source-sha256.json` 绑定构建前源码，`source-tested-v83.zip` 是该源码检查点；交付后更新的状态、报告、索引、变更日志及历史状态单独记录在 `delivery-docs-sha256.json`。`machine-entry-before` / `machine-entry-after` 保留本机入口修正。所有配置与证据均不包含签名密码或私钥。
