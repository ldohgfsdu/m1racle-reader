# M1racle Reader

现行范围以 `docs/PRD.md` 与[全清单授权与验收](docs/FULL_BACKLOG_20261009.md)为准；2026-10-24 授权的当前页 Markdown 编辑已接入 Note 编辑引擎。当前版本与验证状态只看 `docs/CURRENT_STATUS.md`。

Android 本地优先文档阅读器：Markdown、PDF 与有限 EPUB。

## Scope

本地 Markdown、PDF 与有限 EPUB 阅读，当前页 Markdown 编辑及有限 PDF 正文编辑。保留文档标题与标注搜索；云同步、账号和文档正文全文索引不在范围内。早期“Markdown 编辑不做”的说明已由后续明确授权取代。

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
8. 交互/UI 问题：先看 Android、iOS、Chrome、Acrobat、微信读书、成熟阅读器等现成范式，再决定 M1racle Reader 应该怎么做，而不是凭空设计。

## 界面禁止项（2026-10-30）

界面按成熟阅读器的克制来做。下面这些 AI slop 一律不做。通用词汇参考 [pbakaus/impeccable](https://github.com/pbakaus/impeccable)；历史记录中的 4.8.0 `/impeccable` 不是当前已验证可调用的本机入口。若要使用外部技能，先在当次可用技能目录定位并读取实际 `SKILL.md`，记录其路径与版本；未定位时直接执行本节规则，不虚构命令或取消设计要求。Reader 是 Android Compose，网页检测器不能替代 Kotlin 界面检查。本节的下拉小图标禁令以这里为准。

最严重的一条：下拉菜单按钮不要画下拉小图标。选择器、菜单按钮、`ExposedDropdownMenu`、`Spinner` 不要加 `arrow_drop_down`、`ExposedDropdownMenuDefaults.TrailingIcon` 或同类小箭头。控件本身已经是下拉，这个图标必须去掉。国内产品普遍留着它，Reader 不留。

同时禁止：

- 到处贴标签和斜体
- Instrument Serif、Space Grotesk、Bricolage Grotesque
- 滥用等宽字体
- 过度细节：分割线、多层阴影、半透明叠色、带边框的容器
- 过细的图表
- 口语化、自造词、比苹果官网更做作的文案
- 代码面包屑、章节编号、标题上方的小眉标
- 紫色渐变
- 全大写小标题
- 用中圆点当分隔符
- 正文里的 Emoji，以及堆砌的无用文字
- 大得离谱的 hero 字号
- 画蛇添足的解释性描述
- 嵌套圆角不同心
- 线条和边框粗细不统一；发丝线用 0.5px，不用 1px 充数
- 滚动时动效过多，过渡不收敛、发硬
- 字号超过 4 种，或最小字号低于 12px
- 卡片左边缘的彩色竖线

## 变更与验收纪律

1. 先读后改：修改前必须定位真实调用链、现有实现、相关测试和依赖，禁止仅凭文件名、截图或局部代码猜架构，修 bug 必须先确认根因，不能只压表象。
2. 单一事实源：同一个状态、规则、配置、数据不得存在两个权威来源，新路径落地后删除旧路径，禁止双写、双读、同步桥接，UI 只消费领域层或状态层的最终语义，不自行重新推导同一规则。
3. 范围内修改：不做顺手重构、顺手升级依赖、顺手格式化无关文件，发现范围外问题可以记录，但除非阻塞当前任务，否则不修改，一个任务只解决它声明的问题。
4. 验证必须证明问题真的解决：bug 修复必须有覆盖根因的回归测试，能自动化的不要只靠人工截图；测试通过不等于验收通过，涉及 UI、手势、性能、系统行为时必须验证对应真实场景；不允许通过削弱断言、删除测试、扩大容差、跳过测试来制造“通过”；报告必须区分 implemented / automated_verified / device_verified / pending，没有证据不能写完成。

## Inbox / Outbox

- Inbox (inputs): `/storage/emulated/0/inbox/M1racleReader/`
- **Outbox (built APKs):** `.external_outbox/` — every successful `assemble*` copies APKs here automatically

## 构建类型（用户明确要求，2026-09-15）

- 只构建和交付 **Release** APK，使用 `:app:assembleRelease`；不主动构建、安装或交付 Debug APK，也不以 Debug 构建作为验证替代。
- Release 签名凭据缺失或构建失败时，报告具体阻塞并继续可执行的单测/静态检查；禁止退回 Debug、临时签名或未签名 APK。
- QA / benchmark 保留为隔离测试能力，仅在任务确需对应设备测试时使用，不作为用户交付包。
- 本规则取代全局公约中默认 `assembleDebug` / latest Debug APK 的要求；除非用户后续明确要求 Debug，否则不得使用。
- 旧 Release 不得冒充本轮产物，不因其他变体构建成功而复制旧 Release 作为本轮交付。

## 构建后交付（强制，无需提示）

**每次 `assembleRelease` 成功后，确认构建输出与 Outbox Release 的 SHA-256 一致，再按执行环境自动交付：**

- Windows / Linux 宿主：先查询 `adb devices -l`，有唯一在线设备才用 `adb -s <serial> push` 写入手机 Download；多设备须明确目标。
- Android 本机 shell：只有当前进程确实能访问目标共享存储时，才使用本机 `cp`。
- 无在线设备且无共享存储挂载：保留本机 Outbox 与正式交付文件，手机传输、安装与验收均记为 pending；不能把宿主上的同名目录当手机。

- 路径固定：设备端 `/storage/emulated/0/Download/m1racle-reader-release-latest.apk` 可直接安装
- **覆盖旧文件，无需用户额外提示**
- 这是构建流程的一部分，不是可选操作

## GitHub 自动正式发版

仅允许 master 上显式 `[publish]` 提交或 workflow_dispatch 的 `publish=true` 触发。完整发布门禁见 [RELEASE_AUTOMATION](docs/RELEASE_AUTOMATION.md)；无原正式签名密钥、递增版本、真实测试或远端回读时不得公开发布。CI 测试及工作流配置本身不构成一次新 APK 发版。

## Device install / ADB (required for any real-device claim)

**Read:** `docs/DEVICE_INSTALL_AND_ADB_TEST.md` and `docs/APK_DELIVERY.md`.

- Check `adb devices` first. Only `device` state allows auto-install/smoke.
- If offline: keep the current Release locally and mark device transfer/install/smoke pending. Android-local copy is allowed only with actual shared-storage access; **do not mark device smoke as PASS**.
- Only deliver the release minify package: `.external_outbox/m1racle-reader-release-latest.apk`; no Debug or QA delivery. Measure the actual APK size.
- Helper: `bash scripts/install-release-outbox.sh`

## Agent startup / handoff

Before implementing, read in order:

1. `.memory/active_object.yaml` — current active object (id, priority, status, scope)
2. `.memory/CURRENT_STATE.md` — project state, APK, last build, git notes
3. **`docs/PROJECT_INDEX.md`** — doc routing; `docs/` holds 200+ files and most are one-off stage reports, not current state
4. If active object is PDF viewer stability: **`docs/PDF_VIEWER_HANDOFF.md`** (executable technical handoff)
5. For install/test on device: **`docs/DEVICE_INSTALL_AND_ADB_TEST.md`**

`docs/CURRENT_STATUS.md` holds only current state (version, verification baseline, the one delivered artifact,
device status, open items). Never append a dated work log to it; per-session evidence belongs in that session's report,
and superseded state goes to `docs/history/`.

Do not expand beyond the active object scope. Do not migrate PDFium unless explicitly tasked.
