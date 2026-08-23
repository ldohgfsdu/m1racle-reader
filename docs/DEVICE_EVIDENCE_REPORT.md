# Batch B Device Evidence Report

| 字段 | 值 |
|------|-----|
| 日期 | 2026-08-24 |
| HEAD | b716beb（与交付 APK 构建一致：其后的提交仅为文档/memory） |
| 设备探测 | `adb devices` ×2（含 server 重启恢复）→ **空列表，OFFLINE** |
| 结论 | **B-1/B-2/B-3 全部 PENDING(device)。本报告不含任何 PASS 声明。** |

---

## 1. 设备连接确认

```
$ adb devices -l
List of devices attached
(空)
```
两次尝试（含 kill-server/start-server 恢复）均无设备。依据工程公约 §5：不轮询、不死等，完成可完成部分并移交。

## 2. 安装身份确认（待人工执行）

交付物已就位且三方一致（Download 侧 = outbox = 构建输出）：

```
/storage/emulated/0/Download/markleaf-release-latest.apk
SHA256: e1c6735d7a5c61fb93d4bda82aab599b9dc2d779772c5d2a9145978913432c27
size:   11,509,615 bytes   versionCode=21 (v0.1.0, minify+shrink)
```

**人工安装后请核对**（任选其一）：
- `adb shell dumpsys package com.markleaf.app | grep -E "versionName|versionCode"` → 期望 `0.1.0 / 21`
- 或系统设置 → 应用 → 墨阅 → 版本 `0.1.0`

## 3. B-1 displayedBitmaps — PENDING(device)

到岗采集 SOP（连续模式，同一大 PDF ≥80 页）：

```bash
# 冷启应用→打开目标 PDF→等首帧渲染完成
adb shell dumpsys meminfo com.markleaf.app > b1_before.txt
# 脚本化连续滚动至文档尾（每页停留 ~600ms，禁 fling 直穿）：
#   手势或 adb shell input swipe 540 1800 540 400 300 ×N
adb shell dumpsys meminfo com.markleaf.app > b1_mid.txt      # 约第 40 页
adb shell dumpsys meminfo com.markleaf.app > b1_after.txt    # 文档尾
```

判定（对应 878209f retention 实现）：
- **PASS**：`Graphics/Native Heap` 增量在 mid→after 段趋平（≤ ±2 页位图 ≈ ±20MB 级别），before→after 不随页数线性增长；
- **FAIL**：线性增长趋势仍在 → 回查 prune 触发条件（phase 门控/anchor 更新路径）。
附加观察：`adb logcat -s DISPLAY_BITMAP_ACCEPT DISPLAY_BITMAP_REJECT BITMAP_SWAP` 中 bitmapId 复用情况（retention 下离窗页不应再出现旧 id 持续引用）。

## 4. B-2 Liquid Glass GPU Profiling — PENDING(device)

三场景变量隔离，各采 ≥3 组：

```bash
adb shell dumpsys gfxinfo com.markleaf.app reset
# 场景A：chrome 全显状态下滚动内容
# 场景B：反复开合 chrome（tap 触发收起/展开 ×20）
# 场景C：chrome 收起后滚动同等内容（glass 不可见对照组）
adb shell dumpsys gfxinfo com.markleaf.app > b2_<scene>.txt
# FrameTimeline / GPU 轨道：
adb shell perfetto -o /data/misc/perfetto-traces/b2.pftrace -t 30s \
  sched freq gpu gfx view wm am input res memory
```

判定输出：三场景 P50/P90/P95 帧时长 + jank 率对照 → glass 可见性的每帧增量成本 ms；超过预算阈值（如 P90 > 12ms 且显著高于场景 C）才进入优化提案（仅报告，不调参）。

## 5. B-3 A2 遗留 — Export Cancellation 人工确认清单

前置：教材类 PDF + 教材目录节点存在。

1. 进入章节导出（ZIP 包格式）→ 开始导出 → **进度中点取消** → 验证：无部分文件残留于 SAF 目标目录 / packages 目录；
2. 同上 PDF_ANNOTATED 格式 → 中点取消 → 同验证；
3. 导出中杀进程 → 重开应用 → 最近阅读列表正常、无损坏行、可继续正常导出。

三项全过 → A2 closure §2.1 三问闭环，PENDING 转 PASS；任一失败 → 记录复现步骤回修。

## 6. 汇总表

| 项 | 状态 | 依赖 |
|---|---|---|
| 设备连接 | ❌ OFFLINE | 物理/无线 adb 连接 |
| APK 身份确认 | 📦 交付就位，核对命令见 §2 | 安装动作 |
| B-1 内存曲线/retention 实测 | ⏳ PENDING | §3 SOP |
| B-2 FrameTimeline/gfxinfo/GPU | ⏳ PENDING | §4 SOP |
| B-3 Export Cancellation | ⏳ PENDING | §5 清单 |

**下一会话动作**：连接设备 → §2 身份核对 → 依次执行 §3→§4→§5 → 将结果与本报告合并为最终 Device Evidence Closure（PASS/PENDING 终态）。
