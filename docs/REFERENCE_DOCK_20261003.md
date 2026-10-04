# 四入口统一截图底栏 — 2026-10-03

状态：implemented / automated_verified / device_verified。正式签名 Release **1.0.0 / 26** 已覆盖安装到用户手机，并复制至 Download。人工视觉 / 手感确认 pending；未公开发布、创建 tag 或推送。

## 用户要求与实现

用户要求所有底栏使用所附设置页截图样式，并提供签名配置。本次授权用于本地正式签名构建。

四个 Hub 入口共用白色悬浮胶囊、浅灰选中块、蓝色选中图标和文字，右侧独立圆形加号。底座与加号不再采样原页面，使用不透明主题底色；选中块使用 10% 主题对应覆盖色，不再接入背景采样。因此滚动书名不会形成清晰文字 / 模糊条带透进底栏。深色使用深色主题底色。点击、拖动吸附、选中块按压弹性与加号按压反馈保留。

本轮替代前轮底栏的 processed backdrop 导出方案，删除底栏不再使用的采样和导出节点。保留前轮四入口滚动绘制到手势区域、末尾增加安全间距的修复。阅读器、筛选与材质引擎的其他消费者不改。

产品源码本轮仅 `ui/hub/GlassDock.kt` 和 `app/version.properties`；版本 25 → 26。更新拓扑 / 版本回归与硬件像素测试、design.md、CHANGELOG、版本说明和当前状态。既有未提交修改全部保留。

## 验证

- JDK 17，Hub / Home / Backdrop 拓扑 / Release 身份定向 QA 单测 **80 tests / 0 failures / 0 errors / 0 skipped**，退出 0。
- QA 与 QA AndroidTest 构建退出 0，仅隔离验证用途。
- Android 15 模拟器 `emulator-5554` 的 `GlassDockPixelsInstrumentedTest` **3 tests PASS**：浅深色图标清晰；黑白密条背景下，选中项保持浅灰、其余保持白色，静止 / 按住 / 取消均无原页面纹理透出。新增绝对亮度要求并将纹理波动上限从 0.20 收紧至 0.02，没有放宽断言。
- `:app:assembleRelease` 退出 0（包含 lintVitalRelease，R8 / resource shrink）。未重跑完整单测与 full lint。
- 原证书一致、APK 签名验证与 16 KiB zipalign 均退出 0。
- 真机 `db04f865` / 25098PN5AC / 1220×2656：正式 25 → 26 覆盖安装成功，冷启动首页 → 文库 → 书签 → 设置 → 首页。五份 XML / 截图保存并核对，底栏均为用户截图样式。首页继续阅读 4%、PDF 5 / 384 与 85 / 403 保留。未清数据，未对生产包跑 instrumentation。
- crash buffer 有 9 月历史记录；本次 16:11 后窗口未发现 Reader 崩溃，未清系统日志。未验证长书性能、人工手感和其他功能。

## 正式产物

来源：本轮 `gradlew.bat :app:assembleRelease` 的 `app/build/outputs/apk/release/app-release.apk`。

大小：**6993653 bytes**。SHA-256：`3dbf0929cc94bdc41bc13b0ae4846fd0ac203abcd6be28b22ff69fb1c47c846a`。

主机：`.external_outbox/m1racle-reader-release-latest.apk`，同目录 `.sha256`。设备：`/storage/emulated/0/Download/m1racle-reader-release-latest.apk`，交付时间 `2026-10-03T08:12:16.343716+00:00`。构建输出、Outbox、手机 Download、实际安装 base.apk SHA-256 完全一致。

签名证书 SHA-256：`2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1`。从本次用户附件读取签名配置，仅写入仓库外临时受限目录，经环境注入；构建退出后删除临时 keystore，未记录密码、未提交密钥。

## 身份与交接

unmanaged；runId / projectId / machineId unknown；Codex，GPT-6，版本 unknown，Windows。分支 `codex/fix-project-audit`、HEAD `a314247d9cc27f7b3adc1edb3074b65eee3afbfe`。项目契约与中央公约在本机不可用。

外部证据：`C:/Users/21234/.devhub/runs/unmanaged-reader-reference-dock-20261003`，绑定 HEAD、dirty fingerprint、源码 SHA-256、命令 / 退出码、时间与 unknown 身份字段。无提交 / 推送 / tag。剩余为用户视觉和手感确认；长书与全量门禁不冒充本轮通过。
