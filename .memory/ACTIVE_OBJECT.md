# ACTIVE_OBJECT

## Current

**BATCH_B_RUNTIME_HARDENING** — 运行时可靠性与性能证明（长期重负载稳定基础）

## Machine-readable

See `.memory/active_object.yaml` (source of truth).

## Status

`in_progress_partial_automated_verified_device_pending` — B-0 zip 契约修复与 B-1 displayedBitmaps retention 已落地（1145/0 全绿，assembleRelease PASS）；B-2/B-3 因 adb 离线挂起；设备侧验收（内存曲线/FrameTimeline/GPU 采样）待真机。

## Goal

不新增功能：以证据驱动把 PDF 阅读器推进到长期重负载稳定。先测试后实现；行为变化必须有测试；废弃路径不留。

## Handoff

下一位 Agent 必须先读：

1. `.memory/CURRENT_STATE.md`
2. `docs/BATCH_B_FINAL_REPORT.md`（B-0/B-1 详情、B-2/B-3 采集计划、zip identity 决策请求）
3. `docs/A3_FINALIZATION_REPORT.md`（A3 台账与导入链终审）

## Out of scope

- TOC UX / 网盘兼容 / EPUB / 新阅读模式
- Liquid Glass 视觉调参 / PDF renderer 重写 / generation 门禁与手势模型变更
- 未批准的 B-4 代码修改（zip identity 统一等：先报告等批准）
