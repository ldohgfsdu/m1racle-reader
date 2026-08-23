# ISSUE-001 · 百度网盘 Intent Compatibility

| 字段 | 值 |
|---|---|
| 状态 | OPEN — awaiting evidence |
| 优先级建议 | P2（外部打开兼容性） |
| 来源 | A2 人工验证发现（2026-08-23），记录于 A2_VALIDATION_CLOSURE §3.1 |
| 约束 | 诊断阶段不改 manifest；修复需另立批次 |

## 现象

百度网盘内 PDF 的「打开方式」候选列表中没有墨阅。

## 静态诊断（当前代码事实）

MainActivity VIEW intent-filter 匹配条件：
`scheme = content` AND `mimeType ∈ {application/pdf, application/x-pdf, application/octet-stream, application/zip, application/x-zip-compressed, text/markdown, text/x-markdown, text/plain}`。

候选根因（按可能性排序）：
- **H1** 网盘以 `file://` URI 发射 → content-only 方案不匹配；
- **H2** Intent 不带 MIME 或带私有 MIME → MIME 约束过滤器无法命中；
- **H3** 网盘侧包可见性/白名单（Android 11+ `<queries>`）→ 应用侧不可修；
- **H4** 列表外罕见 MIME。

## 待收集证据

| # | 项 | 判定用途 |
|---|---|---|
| E1 | 设备 Android 版本 | H3 适用性 |
| E2 | 网盘「打开方式」完整候选列表 | 全缺失 vs 排序靠后 |
| E3 | 系统文件管理器同 PDF 候选列表（墨阅在否） | 隔离网盘因素 |
| E4 | （可选，adb 恢复后）发射 Intent 抓取：`adb logcat -s ActivityTaskManager:I` 复现时过滤 start | 终局证据：scheme + MIME 实值 |

判读映射：E3 有 / E2 无 → H1/H2（可评估兼容过滤器）；E3、E2 均无 → 偏 H3；E4 定案。

## 修复方向预案（证据到位后评估，不承诺）

- H1/H2：补 `file://` scheme 与/或无 MIME 兜底过滤器；**注意** `file://` 跨应用私有路径不可读，需同时确认读取路径可行性（ExternalFileIntake 是否能消费）；同步扩展 `ExternalIntentManifestTest` 契约。
- H3：记录结论，不修改。
- 验收：网盘实机「打开方式」出现墨阅且成功进入阅读；既有外部打开测试全绿。

## 退出条件（Definition of Done）

根因定性（E1–E4 至少一项硬证据）→ 若可修则独立批次实现并实测通过 → 本 Issue 关闭或转「不修」决议存档。
