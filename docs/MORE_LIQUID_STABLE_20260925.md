# 三点菜单液态材质与文字稳定 · 2026-09-25

用户反馈三点展开没有换成液态玻璃，文字层独立运动显得廉价。此记录修正上一轮悬浮材质推广中对 Menu 的错误分组。

## 根因与修改

- Menu 原先使用 .90 表面 / 24dp 模糊 / 无折射，导致展开后仍像厚磨砂板。现与 Control / Capsule / Dock 共用低模糊液态配方：4dp blur、8dp/24dp lens、浅色 .60/深色 .64。保持既有平台能力判断。
- 主菜单内容存在 .94→1 独立缩放，且 `.width(MoreMenuWidth)` 受变形容器宽度约束，引起文字布局漂移。删除内容缩放与独立淡入，使用 `wrapContentWidth(Alignment.End, unbounded = true)` 固定最终宽度及右侧锚点。容器尺寸/轮廓继续变形并裁切显露文字。
- 二级菜单删除正文额外 6dp translationY，保留容器变形与正文淡入。未更改菜单业务、状态机、PDF 引擎或其他阅读器。

## 验证

风险：中（菜单布局与动效）。implemented：完成。
automated_verified：旧实现实际 Compose 布局回归失败，同一文字横向位置 691.0851→647 px；修复后该测试检查 160ms、256ms、结束时的位置和尺寸一致，124 项材质/菜单/状态机/大字体定向测试全部通过，0 failures/errors/skipped。补充源码契约阻止重新引入文字独立缩放和二级正文滑移。没有删除测试或扩大容差。

完整 lintRelease PASS，0 errors / 158 warnings；正式签名 assembleRelease PASS。APK ZIP/签名/无 INTERNET 验证通过。单次 adb devices -l 为空，device_verified=NOT_RUN；待用户验证真实设备上的液态观感和展开/收回稳定性。

## 交付

1.0.0/23；6,950,677 bytes / 6.63 MiB。
SHA-256：`cda3891e534e302742ebacd650ddd36570906f3279d5bac9e307c5e2d0aefef1`。
构建输出、Outbox 与 `/storage/emulated/0/Download/m1racle-reader-release-latest.apk` 摘要一致，已自动覆盖。产物摘要与上轮不同，DEX SHA-256 `0b5601f346395a4845ff52401e7df8f0e69363f5e0d0da637a01dc4d3458c294`。

证据 `.external_outbox/more-liquid-stable/`：before.log / before-test.xml / after.log / test-results / release.log / lint-release.xml / verification.json / delivery.json。保留既有工作区，未提交 Git。
