# Changelog

## Unreleased — v1.0.0 public candidate (versionCode 23)

- EPUB 有限阅读：本地 reflowable 章节、分页、目录、图片与阅读位置；不支持 DRM、固定版式、音视频、搜索与批注。
- EPUB 导入加固：解析深度/块数上限、XML 有界读取、ZIP 忽略条目同限计量、正文行内空白与 `pre` 原文保留、
  目录锚点与相对路径解析、包内图片沙箱解析。
- EPUB 排版成本：主线程按 8ms 分片让帧、常驻布局有界（锚点窗口 + 滚动 LRU，淘汰行按需重测）、
  图片尺寸探针移到 IO、目录页码每次布局只解析一次。
- 产品身份：产品/品牌迁移为 `M1racle Reader / M1racle`；Android `applicationId` 与 Kotlin
  namespace 迁移为 `com.m1racle.reader`，仓库标识迁移为 `m1racle-reader`。R32 及更早报告中的
  MarkLeaf 名称与 `com.markleaf.app` 仅作为迁移前历史证据保留。
- 阅读资产：收藏、书签、PDF 高亮、轻量笔记及统一管理闭环。
- 本地搜索：文档标题、高亮摘录与笔记索引、Home/Library 入口和 canonical anchor 原文跳转。
- 架构与发布收口：Repository/Room 权威边界审计、R8 release 日志剔除、outbox 有界保留。
- Release hardening：正式 release 禁止 debug 签名 fallback，签名材料仅通过环境安全注入；
  版本 identity 统一为 `app/version.properties`；Room v14 为当前数据库版本（公开基线自 v12 起）。
- 运动语言：底栏按压与选中两个弹簧提升为 `M1racleMotion` 令牌，特征代码不再自带运动常量。
- 本节仍为未发布候选；真实生产 signer、最终 tag 与公开发布批准尚未完成。

## v0.1.0 (versionCode 22) — 2026-08-24

首个 C-local Release（侧载分发）。最终 source baseline：`604d404978b5f09fdb4a9c825259ef6793f59949`；tag：`v0.1.0-c-local`。

### C-local closure
- 最终 APK SHA-256：`4dd9c7ebacec6276fbca0615107ce2885f35c8a70bf81de0be818b39035a3ba4`（11,493,231 bytes）
- 自动化：`assembleRelease` PASS；`testQaUnitTest` 1156 tests / 0 failures / 0 errors / 0 skipped
- 设备验收：C-2.3 `HUMAN_ACCEPTED`；ZIP/PDF cancel 均无 0-byte 或 partial 残留，正常 ZIP 回归通过
- 非阻塞 pending 固化于 `docs/B4_PENDING_ISSUES.md`；C-4 未修改生产代码、UI、功能或架构

### 产品能力
- 本地 Markdown 阅读：块解析（标题/列表/引用/代码高亮/表格/图片）、TOC 锚点、文档内搜索+高亮、全文读取（32MiB 上限）、阅读位置恢复、字号/行高/边距设置
- 本地 PDF 阅读：PdfRenderer 分块瓦片渲染 + 预测式解码，连续/单页模式，双指缩放平移，页面缩略图，教材目录大纲与印刷页码绑定，章节导出（ZIP 处理包 / 标注 PDF）
- 文档获取：SAF 导入（md/markdown/txt/pdf/zip）、系统「打开方式」VIEW 与「分享」SEND 直入阅读（含旋转/进程恢复导航恢复）、zip 教材包两入口统一提取管线
- 最近阅读管理、删除记录永不触碰用户源文件

### 工程与质量基线
- 隐私：无 INTERNET 等用户权限（仅 AndroidX 自动生成的非导出 receiver 内部权限）、无账号/广告/统计/云同步；ReleaseHygieneHostTest 强制门禁
- 测试：testQaUnitTest 1156 例全绿（Robolectric 契约 + 纯逻辑回归）
- 性能：连续视口热路径单收集器驱动；display mirror retention policy（长会话内存有界）；真机 gfxinfo P50=6ms / P90=16ms / P99=17ms
- 发布卫生：release 构建启用 R8 full-mode + resource shrink；debug telemetry/log 经编译期门控在 release 中剥离
- 导出取消：ZIP 与标注 PDF 在 SAF 目标提交前取消时清理 0-byte/partial 产物；C-2.3 真机回归 `HUMAN_ACCEPTED`

### 已知限制（详见 docs/B4_PENDING_ISSUES.md）
- 同一 zip 经不同入口导入可能产生两条库记录（跨入口身份模型待统一）
- >200MB 源文件拒绝时的提示复用通用失败文案
