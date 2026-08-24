# B-4 Pending Issues — Runtime Risk Cleanup 清单

> 更新：2026-08-24 · HEAD ec5edc8+
> 规则：每项先报告、经批准后修改。本清单为唯一 pending 权威，完成即勾销。

## 已批准 / 已完成
- [x] `.memory` 三件套重写（A3 Finalization §3 预批准）→ b716beb 兑现
- [x] ZIP VIEW/SEND 契约修复（B-0）→ ce31f00
- [x] displayedBitmaps retention（B-1）→ 878209f；JVM 契约 11 例 + 真机往返采样（RSS 377→344MB 反向释放）

## 待批准（先报告，批准后立批）

### P-1 跨入口 zip 身份模型统一
- **现状**：FAB 行 `id=stableUriId(uri)` 无 content_sha；Path B 行 `id=c:<sha>`。同一 zip 先 FAB 后 VIEW → 两行重复。
- **建议方案**：importZipPackage 计算包字节 sha 并写入 entity.contentSha256；importDocument 在 zip 分支前增加 content 去重查询命中转 ExistingLibrary 复用。存量行不迁移（新数据自然收敛）。
- **影响**：仅新增去重命中，不改既有行为；Room 无 schema 变更（contentSha256 列已存在于 v6）。
- **工作量**：小（一处写列+一处查询+测试 2 例）。

### P-2 ZIP 导出取消 UX 歧义（Device Evidence §B-3 定性）
- **现状**：小章节 ZIP 导出 <2s 完成；用户点「取消」时任务常已 Completed，对话框消失实为完成——714KB「残留」即完整产物（非缺陷）。真机 tap 取消命中的场景（大任务）清理语义 JVM 已证正确。
- **建议**：Completed 后禁用进度框取消按钮（UI 冻结中 → 仅记录）；或导出完成 toast 与取消反馈区分文案。

### P-3 SourceTooLarge UI 文案拆分
- 现映射 ReadFailed 通用失败 toast；UI 解冻批次加专属事件分支与文案（"文件超过 200MB 上限"）。

### P-4 LocalImageLoader Log.w 门控（P3）
- 4 处解码失败 Log.w 可并入 READER_PERFORMANCE_EXPERIMENTS 编译门控模式；非必需。

### P-5 force-stop 中途写入场景复现（P3）
- 小章节导出快于杀进程窗口；需大范围导出（全书 384 页）+ 立即 force-stop 复测残留。依赖设备空闲窗口。

## 观察项（非缺陷）
- ThumbnailLoader closeAsync 与在飞 renderPage 的长会话竞态表现
- B-1 不中断长程复测（本轮两次被设备实际使用打断：Twitter 前台/音乐播放；待设备独占窗口执行，脚本已就绪：重启应用→卡片打开→swipe 循环滚动→六点 VmRSS 采样）
