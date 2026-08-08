# MarkLeaf

Android 本地优先 Markdown 阅读器（墨阅）。

## Scope

Follow `docs/PRD.md`. MVP is local Markdown reading only.

- No INTERNET permission
- No ads / analytics / accounts / cloud sync
- Do not expand beyond the current task scope

## 工程原则

- 不保留向后兼容：不为旧代码结构、旧 API、废弃功能保留兼容层、fallback 或双路径，旧实现确认废弃后直接删除；除非 PRD 明确要求保留既有用户持久化数据，否则不做 migration，需要保留用户数据时，只允许做一次性的必要数据迁移，不保留旧运行路径。
- 选能满足当前需求的最简单实现：不要预防性抽象，不要多此一举的配置层。
- 系统分层长：先跑通一个最小的端到端版本，再往上加东西。绝不为了未完成的复杂度拆掉能跑的东西。
- 组件保持模块化，关注点分离。
- 优先用成熟的、有人维护的库。没有明确理由别自己重写。
- 先翻项目里已有的依赖能做什么，再考虑加新包或自己写。别上来就假设库里没有。
- 架构决策往长了做。不接受“先这样以后再换”的临时方案。
- 先看成熟产品怎么解决同一个问题，用已验证的模式，别从零发明。

### 实际判定标准

1. 看到旧实现：确认已废弃就删干净，不保留 `Legacy*`、双路径、版本判断、兼容适配器。
2. 准备新增抽象时：先问“当前是否至少有两个真实调用场景？”没有就不抽。
3. 准备加依赖时：先查现有 dependency graph 和库能力；能复用就不重复造轮子。
4. 准备自研组件时：先确认成熟库/成熟产品为什么不适用，否则默认不用自研。
5. 做大功能时：优先打通 输入 → 核心逻辑 → UI/输出 → 验收 的最小闭环，再补横向能力。
6. 架构选择：允许实现简单，但不允许方向临时。也就是“代码可以少，边界必须对”。
7. 评审方案时：会主动把“为了兼容旧东西”“以后可能会用”“先糊一下以后重构”视为负面信号。
8. 交互/UI 问题：先看 Android、iOS、Chrome、Acrobat、微信读书、成熟阅读器等现成范式，再决定 MarkLeaf 应该怎么做，而不是凭空设计。

## 变更与验收纪律

1. 先读后改：修改前必须定位真实调用链、现有实现、相关测试和依赖，禁止仅凭文件名、截图或局部代码猜架构，修 bug 必须先确认根因，不能只压表象。
2. 单一事实源：同一个状态、规则、配置、数据不得存在两个权威来源，新路径落地后删除旧路径，禁止双写、双读、同步桥接，UI 只消费领域层或状态层的最终语义，不自行重新推导同一规则。
3. 范围内修改：不做顺手重构、顺手升级依赖、顺手格式化无关文件，发现范围外问题可以记录，但除非阻塞当前任务，否则不修改，一个任务只解决它声明的问题。
4. 验证必须证明问题真的解决：bug 修复必须有覆盖根因的回归测试，能自动化的不要只靠人工截图；测试通过不等于验收通过，涉及 UI、手势、性能、系统行为时必须验证对应真实场景；不允许通过削弱断言、删除测试、扩大容差、跳过测试来制造“通过”；报告必须区分 implemented / automated_verified / device_verified / pending，没有证据不能写完成。

## Inbox / Outbox

- Inbox (inputs): `/storage/emulated/0/inbox/MarkLeaf/`
- **Outbox (built APKs):** `.external_outbox/` — every successful `assemble*` copies APKs here automatically

## 构建后交付（强制，无需提示）

**每次 `assemble*` 成功后，必须自动执行：**

```bash
cp .external_outbox/markleaf-release-latest.apk /storage/emulated/0/Download/markleaf-release-latest.apk
```

- 路径固定：设备端 `/storage/emulated/0/Download/markleaf-release-latest.apk` 可直接安装
- **覆盖旧文件，无需用户额外提示**
- 这是构建流程的一部分，不是可选操作

## Device install / ADB (required for any real-device claim)

**Read:** `docs/DEVICE_INSTALL_AND_ADB_TEST.md` and `docs/APK_DELIVERY.md`.

- Check `adb devices` first. Only `device` state allows auto-install/smoke.
- If offline: copy release APK to `/storage/emulated/0/Download/` and ask human to install; **do not mark device smoke as PASS**.
- Prefer release minify package: `.external_outbox/markleaf-release-latest.apk` (~10–11MB), not 70MB+ QA unless tasked.
- Helper: `bash scripts/install-release-outbox.sh`

## Agent startup / handoff

Before implementing, read in order:

1. `.memory/active_object.yaml` — current active object (id, priority, status, scope)
2. `.memory/CURRENT_STATE.md` — project state, APK, last build, git notes
3. If active object is PDF viewer stability: **`docs/PDF_VIEWER_HANDOFF.md`** (executable technical handoff)
4. For install/test on device: **`docs/DEVICE_INSTALL_AND_ADB_TEST.md`**

Do not expand beyond the active object scope. Do not migrate PDFium unless explicitly tasked.
