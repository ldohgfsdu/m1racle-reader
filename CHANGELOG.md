# Changelog

## v0.1.0 (versionCode 22) — 2026-08-24

首个 C-local Release Candidate（侧载分发）。

### 产品能力
- 本地 Markdown 阅读：块解析（标题/列表/引用/代码高亮/表格/图片）、TOC 锚点、文档内搜索+高亮、全文读取（32MiB 上限）、阅读位置恢复、字号/行高/边距设置
- 本地 PDF 阅读：PdfRenderer 分块瓦片渲染 + 预测式解码，连续/单页模式，双指缩放平移，页面缩略图，教材目录大纲与印刷页码绑定，章节导出（ZIP 处理包 / 标注 PDF）
- 文档获取：SAF 导入（md/markdown/txt/pdf/zip）、系统「打开方式」VIEW 与「分享」SEND 直入阅读（含旋转/进程恢复导航恢复）、zip 教材包两入口统一提取管线
- 最近阅读管理、删除记录永不触碰用户源文件

### 工程与质量基线
- 隐私：manifest 零权限（无 INTERNET）、无账号/广告/统计/云同步；ReleaseHygieneHostTest 强制门禁
- 测试：testQaUnitTest 1149 例全绿（Robolectric 契约 + 纯逻辑回归）
- 性能：连续视口热路径单收集器驱动；display mirror retention policy（长会话内存有界）；真机 gfxinfo P50=6ms / P90=16ms / P99=17ms
- 发布卫生：release 构建启用 R8 full-mode + resource shrink；debug telemetry/log 经编译期门控在 release 中剥离

### 已知限制（详见 docs/B4_PENDING_ISSUES.md）
- 同一 zip 经不同入口导入可能产生两条库记录（跨入口身份模型待统一）
- >200MB 源文件拒绝时的提示复用通用失败文案
