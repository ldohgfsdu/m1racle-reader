# R22.5 Interaction Contract & Material Regression Tests 报告

**日期：** 2026-08-26
**性质：** 纯测试任务。零生产改动（生产 UI / GlassMaterial 参数 / 动画参数 / Chrome 状态机逻辑全部未触碰，无新依赖）。
**目标：** 用自动化测试锁定当前已验收行为（Glass Engine 冻结态 + R22 交互 Polish），降低后续 UI/交互迭代回归风险。

---

## 验证结果（automated_verified）

| 项目 | 结果 |
|---|---|
| `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64 ./gradlew :app:testQaUnitTest` | **BUILD SUCCESSFUL** |
| 总量 | **1213 tests / 0 failures / 0 errors / 0 skipped** |
| 基线保持 | **1173 原有测试一个不少**（1213 − 新增 40 = 1173） |
| 本轮新增 | **40 tests / 全部通过** |

环境注意（本机）：默认 JDK 为 25，旧版 Kotlin 编译器无法解析其版本号，必须以
`JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64` 运行 Gradle。

---

## 新增文件与数量

| 文件（app/src/test/java/com/markleaf/app/…） | 测试数 | 覆盖 |
|---|---|---|
| `ui/pdf/CapsuleInteractionContractTest.kt` | 13 | Part 1a：胶囊源码钉契约 + PdfCapsuleData 模型 |
| `ui/pdf/CapsuleMorphModelContractTest.kt` | 7 | Part 1b：morph 派生可执行规格扫描 |
| `ui/pdf/CapsuleMorphSpringInterruptTest.kt` | 4 | Part 1c：真实冻结 spring 的中断连续性 |
| `pdf/PdfChromeInteractionLockContractTest.kt` | 8 | Part 2：三类交互锁全生命周期 |
| `ui/material/GlassMaterialRegressionTest.kt` | 8 | Part 3：Glass Engine 防回归 |

---

## Part 1 — 胶囊交互契约（Capsule Interaction Contract）

### 锁定的行为

- **PdfCapsuleData 模型**：`printDetail` 默认 null；copy/equality 语义；位置与进度是独立可空维度。
- **Ready 态单一事实源派生**（源码钉）：标题 = 最深大纲节点标题（空白回退文档名）；`positionLine` 仅 pageCount>0；进度 = `(pageIndex+1)/pageCount` 四舍五入并 coerce 0–100；教材页详情仅在绑定存在时输出；JumpCard 收到的是 1-based 人读页码。
- **Loading/Error 态**：title-only payload（positionLine/progress 均 null），且不提供胶囊点击目标。
- **morph 单一驱动源**（源码钉 + 可执行规格双层）：整个 `PdfInfoCapsule` 区域仅一个 `Animatable`、一次 `animateTo`、一次读取 `morph.value`，其余维度（宽度、梯形轮廓、header 交叉淡变、body 高度、body 内容 alpha ×1.5 提前淡入）全部从同一 fraction 派生；无 tween/snapTo/animateFloatAsState 第二时钟。禁例「宽度已展开但内容未展开」在 fraction 全扫面上被证伪。
- **中断连续性（真实执行）**：反射读取生产冻结的 `CapsuleMorphSpring`（damping 0.8 / stiffness 450 防漂移），用真实动画核验证 expand≈50% 打断转 collapse、collapse≈30% 打断转 expand —— 接缝值与速度精确延续、逐帧位移受速度约束（终帧亚阈值并入除外）、端点收敛、ζ=0.8 过冲 ≤1.03 有界。
- **章节标题 marquee**：慢速参数冻结（iterations=MAX、1500ms 起播、1200ms 循环间隔）；恰好收起态与展开态两个标题挂载；「仅溢出才滚动」由 Compose `basicMarquee` 库语义保证（fitting 文本不动画），非本项目代码。

### 方法论说明（如实记录）

本宿主约束：Robolectric 原生图形运行时不支持 Linux/aarch64，SDK34 下 `android.graphics.Path`
由需要原生运行时的 shadow 承载，因此 `PdfInfoCapsule`（private）内联派生与
`CapsulePopoverShape.createOutline` 的**真实渲染路径无法在 JVM 上执行**；且本任务禁止为可测性
重构生产代码。故 Part 1 采用仓库既有范式（同 `ReaderChromeMaterialTest`）：

1. **源码结构钉**锁定生产公式原文（公式被改而未修订钉 → 测试红）；
2. **镜像模型可执行规格**按同一冻结常量跑契约性质扫描（单调性、互补淡变、可见性同步、端点退化合法）；
3. **机制层真执行**（Animatable+animateTo 同机制的连续性、真实 spring 参数与运动界）。

三层各自独立成立；生产公式若漂移，第 1 层先红；动画手感回归由第 3 层捕获。

---

## Part 2 — Chrome Interaction Lock（PdfChromeScrollStateMachine）

覆盖 PINCH / PAGE_JUMP / TEXT_SELECTION 三类锁源的统一语义（纯状态机直测）：

- **锁定期冻结**：任一锁激活时滚动样本不迁移、不累计（level 不变 = chrome 不自动隐藏）、fling 返回 null；中央单击作为显式操作仍允许，且其 level 变更在解锁后保留（正交模式位语义）。
- **fling 不污染**：锁定期内的 fling 尝试不留速度/冷却/方向残留；解锁后首个恢复阈值样本立即按干净规则正确迁移（CAPSULE_SHOW_THRESHOLD）。
- **解锁清残留**：部分累计（30dp<36 阈值）被丢弃而非携带；陈旧 UP 方向不会把解锁后的 DOWN 首帧误当反向确认吞掉。
- **幂等与嵌套**：begin/end 对每个锁源幂等；多锁并存时逐一释放期间仍保持锁定，全部释放后才恢复响应。
- 与既有 `PdfChromeScrollStateMachineTest` §⑨（R22.4 ×4）互补，不重复其用例。

TEXT_SELECTION 当前无生产调用方（PDF 无文本选择，枚举预留）——测试锁定的是状态机对它的统一处理，防止未来接线时行为分叉。

---

## Part 3 — Glass Material Regression（GlassMaterialEngine）

- **材质层级**：surface alpha 严格序 Dialog > SettingsCard(Card) > Menu > Capsule == Control（双主题）；blur/shadow 同向（Dialog 18 ≥ Card 18 > Menu 16 > Control 12；Card 与 Menu 同 blur、更重投影）。五个角色的冻结 alpha 数值逐一核对（0.50/0.50/0.60/0.66/0.97 light；0.68/0.68/0.74/0.78/0.97 dark）。
- **Dark/Light 一致性**：所有角色 Dark 表面亮度 < Light（luminance + brightness 双指标）；双侧 highlight 保留（宽度恒 0.5dp、alpha>0）；edge separation 保留（阴影双侧为正，夜间 0.46 > 日间 0.10）；主题无关指标（blur/lens/shadow/divider/saturation=1.25/depthEffect=false）跨主题一致；夜间 muted 更实（可读性方向不反转）；夜间单色调 0xFF111820 冻结。
- **NORMAL/PRESSED 连续性**：按压只动受控通道（表面 +0.04、highlight +0.10、scale ×0.965），其余通道逐一相等 —— 不允许换材质；PressSpring 冻结（damping 0.82 / StiffnessMedium / threshold 0.001）；plate 与 backdrop 两条绘制路径均对同一对 idle/pressed 规格插值（lerp/alpha 混合计数钉），中间态落在两端点之间且单调。
- Color 分量打包存在半精度量化（≈1/255 步进），数值断言带 0.004–0.006 容差（非削弱：远小于任何可感知变化）。

---

## 保护行为清单（防什么）

| 回归场景 | 被谁拦住 |
|---|---|
| 有人给胶囊加第二个动画时钟 / tween / snapTo | CapsuleInteractionContractTest 单时钟钉 |
| morph 派生公式漂移（宽度带、交叉淡变、×1.5 提前淡入等） | 同上源码钉 + ModelContract 规格 |
| spring 参数被调（0.8/450、PressSpring 三参、MENU_CLOSE_MORPH_MS） | 反射冻结断言 + 源码钉 |
| 中断续播改为重建状态源（跳变） | SpringInterrupt 接缝零跳变 + 帧速界限 |
| Ready 态派生口径分裂（标题回退/进度钳制/print 门控） | 派生钉 + JumpCard 1-based 钉 |
| 锁定期间 chrome 自动收起 / 样本泄漏 / 解锁脏启动 | LockContract 八项 |
| Glass 层级倒挂、夜间变亮、高光丢失、按压瞬切材质 | GlassMaterialRegression 八项 |

---

## 未覆盖项目（诚实清单）

- **device_pending（沿用 R22 报告 §设备回归清单，本轮不进入）**：spring 手感、marquee 视觉节奏、IME 与 ADJUST_NOTHING 交互、backdrop/lens 实际 GPU 渲染、Menu 锚点出入的观感 —— 属审美/系统行为，按公约由人工装包验收。
- **本宿主不可执行**：`PdfInfoCapsule` 内联派生与 `CapsulePopoverShape` 轮廓的真实光栅化（Robolectric 原生图形不支持 Linux/aarch64）。已用「源码钉 + 可执行规格 + 真实 spring 机制」三层替代；若未来宿主支持 RNG，可将镜像模型升级为真实轮廓断言。
- **库语义委托**：「仅溢出才 marquee」由 Compose foundation 保证，未自建溢出检测测试（避免脆弱 UI 断言，符合公约 §2.3/§4.2）。
- **R22.3 横向快翻 scale 泄漏**：与本任务无关，仍按原计划等待设备取证（H14/H15），未做任何猜测性修改。

---

## 变更面

```
app/src/test/java/com/markleaf/app/ui/pdf/CapsuleInteractionContractTest.kt   (new)
app/src/test/java/com/markleaf/app/ui/pdf/CapsuleMorphModelContractTest.kt    (new)
app/src/test/java/com/markleaf/app/ui/pdf/CapsuleMorphSpringInterruptTest.kt  (new)
app/src/test/java/com/markleaf/app/pdf/PdfChromeInteractionLockContractTest.kt(new)
app/src/test/java/com/markleaf/app/ui/material/GlassMaterialRegressionTest.kt (new)
docs/R22_5_TEST_REPORT.md                                                     (new)
```

生产代码、构建脚本、依赖：零改动。
