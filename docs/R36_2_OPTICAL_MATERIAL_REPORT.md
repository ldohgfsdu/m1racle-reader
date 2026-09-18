# R36.2 Optical Material Rewrite

2026-09-18。任务源：`/storage/emulated/0/r36.2.txt`。

## Status / checkpoint

基础实现与自动化已完成；R36.2 静态光学最终验收 **PENDING**。不进入 R36.3。
真机调参只用已签名 Release（`com.m1racle.reader`），不用 QA。
Batch 1 锁定 Content blur 26dp。Batch 2 light tint 已走到 **.45**（当前已安装）。dark / lens / edge / shadow 未调。Control 仍与 Capsule/Menu 共用 readerOverlayFrost，被误带到 26dp/.45，需拆开。

## Baseline

- branch: `feat/risediary-inspired-dock`
- HEAD: `5284f801fffa62904ce80101ca687cfd415ea8c6`
- 工作树已有 74 个 tracked 文件修改（2100 insertions / 2223 deletions），另有 untracked 文件；本轮保留，不提交旧改动。
- 原始 status、diff stat、引擎快照：`.external_outbox/r36-2/baseline/`。
- 版本：1.0.0 (23)。Kotlin/Compose、Room、PdfRenderer/PDFBox、Kyant backdrop 2.0.0；不新增依赖或迁移 PDF 引擎。
- R36.1 的统一 authority 已落地；本轮新增目标替代旧 active object 的“禁止 R36.2 调参”范围。

以下为实际源码的 idle recipe，L = APP_LIGHT / READER_LIGHT，D = APP_DARK / READER_DARK，环境同 dark 位数值相同。dp 为逻辑单位。H/A 是 refraction height/amount。

| Role | Environment | Capability | blur | tint α | vibrancy | lens | refraction H/A | edge width/α | shadow radius/α |
|---|---|---|---:|---:|---|---|---|---|---|
| Control / Capsule / Menu | L | FULL_OPTICAL |18|.86|on|off|0/0|0/0|6/.14|
| Control / Capsule / Menu | D | FULL_OPTICAL |18|.80|on|off|0/0|0/0|6/.28|
| ExpandedMenu / SettingsCard | L | FULL_OPTICAL |18|.90|on|off|0/0|0/0|6/.14|
| ExpandedMenu / SettingsCard | D | FULL_OPTICAL |18|.86|on|off|0/0|0/0|6/.28|
| Dialog | L | FULL_OPTICAL |18|.97|off|off|0/0|.5/.30|18/.10|
| Dialog | D | FULL_OPTICAL |18|.97|off|off|0/0|.5/.38|18/.46|
| Dock | L | FULL_OPTICAL |0|.98|off|off|0/0|0/0|2/.10|
| Dock | D | FULL_OPTICAL |0|.98|off|off|0/0|0/0|2/.20|
| ProgressiveChrome | L | FULL_OPTICAL |16|.62|off|off|0/0|0/0|0/.14|
| ProgressiveChrome | D | FULL_OPTICAL |16|.62|off|off|0/0|0/0|0/.28|
| PreviewCapsule (preview only) | L | FULL_OPTICAL |4.32|.42|on|on|16/32|.5/.96|6/.10|
| PreviewCapsule (preview only) | D | FULL_OPTICAL |4.32|.30|on|on|16/32|.5/.78|6/.46|
| DockSelection (NO_CURRENT_CONSUMER) | L | FULL_OPTICAL |0|.16|off|off|0/0|0/0|3/.10|
| DockSelection (NO_CURRENT_CONSUMER) | D | FULL_OPTICAL |0|.20|off|off|0/0|0/0|3/.46|

完整 capability 展开规则（适用于表中每一行）：
- BLUR_ONLY：其余同表，lens off、H/A=0/0、depth off、chromatic off。
- SOLID_FALLBACK：再令 blur=0、vibrancy off；**旧 resolver 的 tint/edge/shadow 不变**。
- 真实生产：Dialog、Markdown ExpandedMenu 用 `sampleBackdrop=false`；但 modifier 仍按 LocalGlassCapability（通常 FULL）解析，再由 renderer 放弃采样。Dock 直接画色板、无采样。Home Dropdown 显式 resolve SOLID_FALLBACK。
- Progressive renderer 实际只有 blur+tint+空间遮罩，不画 shadow/edge。
- 所有 chromatic=false；foreground contentAlpha=1。
- 中性 tint：常规 #F5F5F5 / #000000；Dialog #FAFAFA / #121212；Dock #FFFFFF / #171717；孤儿 selection #8F99A3 / #121212。

## Optical families / implementation

implemented：
- 增加 `GlassOpticalFamily`，仍由原 Engine 配方表解析，未创建第二个 authority。当前家族是后续共享物理参数的边界，尚未完成各 family 的光学重写。
- Control / PreviewCapsule → INTERACTIVE；Capsule / Menu / ExpandedMenu / ProgressiveChrome → CONTENT；SettingsCard / Dialog → PANEL；Dock 与不可采样窗口 → FALLBACK。
- 角色不等于 UI 名称：SettingsCard 当前实际消费者为 PDF submenu；首页 import 没有 Glass consumer，不擅自增加捕获拓扑。
- 删除 DockSelection 及孤儿配方、noCurrentConsumer 字段。依据：全生产源码无 consumer；GlassDock 实际选中标识为 `DockIndicatorHeight=3.dp` 的实色条，且 R36.1 只记录“是否在未来复活”UNKNOWN，没有已批准的 R36.3 selector。替换孤儿行为测试为“枚举/注册表不再包含孤儿”回归门禁；Dock 正常交互断言保留。
- 现有 press spring、pointer/drag、geometry、Document→Backdrop→Overlay 拓扑不变；本轮不新增动态行为。
- ProgressiveChrome 保留空间 DstIn falloff；记住 Engine endpoints，尊重 vibrancy 开关、SOLID 不采样。Settings preview 渐变分支改用同一 `readerGradient`，删除重复 AGSL 遮罩，避免预览只糊图却不使用生产 tint。
- 现有 Kyant `Highlight` 的 width / blurRadius / alpha / style 已通过本地 2.0.0 runtime jar 的 javap 确认可用。后续 edge 无需新框架；成熟库用法参考 [Kyant 官方 LiquidBottomTabs](https://github.com/Kyant0/AndroidLiquidGlass/blob/kmp/app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components/LiquidBottomTabs.kt)，仅参考静态 API，不引入其交互系统。

## Final recipe matrix

PENDING：未获得逐变量真机证据，不宣称最终配方已选定。以上 baseline 是本轮调参起点。

当前工作配方的完整展开方式（9 roles × 4 environments × 3 capabilities；PreviewCapsule 仅预览，不作为生产验收）：

| Role | Environment | Capability | 当前配方 |
|---|---|---|---|
| Control, Capsule, Menu, ExpandedMenu, SettingsCard, Dialog, ProgressiveChrome, PreviewCapsule | APP_LIGHT / READER_LIGHT | FULL_OPTICAL / BLUR_ONLY | 数值同 baseline 对应 L 行及能力展开规则 |
| 同上 | APP_DARK / READER_DARK | FULL_OPTICAL / BLUR_ONLY | 数值同 baseline 对应 D 行及能力展开规则 |
| 同上 | APP_LIGHT / READER_LIGHT | SOLID_FALLBACK | blur=0, tint=#FAFAFA/.97, vibrancy=off, lens=off, H/A=0/0, edge=.5/.30, shadow=该 role baseline L 值 |
| 同上 | APP_DARK / READER_DARK | SOLID_FALLBACK | blur=0, tint=#121212/.97, vibrancy=off, lens=off, H/A=0/0, edge=.5/.38, shadow=该 role baseline D 值 |
| Dock | APP_LIGHT / READER_LIGHT | 全部三种 capability | baseline Dock L；生产显式 SOLID_FALLBACK |
| Dock | APP_DARK / READER_DARK | 全部三种 capability | baseline Dock D；生产显式 SOLID_FALLBACK |

Foreground alpha=1、chromatic=false。DockSelection 已不存在。表中旧边缘/阴影仍待 Batch 4/5，不代表最终 .5dp/18dp 已验收。

跨窗口实际选 SOLID_FALLBACK；不能支持 lens 的 custom CapsulePopoverShape 选 BLUR_ONLY；普通圆角胶囊保留设备 capability。环境只影响明暗 tint，尚无额外 APP/READER 参数差异。

## Tuning history

| Batch | Parameter | Before | Candidates | Visual result | Decision |
|---|---|---|---|---|---|
|1|Content blur|18dp|device 18 / 22 / 26 on A–E + 内科学笔记。20/24/28 未单独装包|18/22/26 在 tint .86 下全部 FAIL A：菜单后汉字仍可读，三者几乎无差。|Carry **26dp**。截图 `device/batch1/`。|
|2|tint|.86 light / .80 dark|light .80 .70 .60 .50 .45 on signed Release A–E。dark 未改|.80/.70/.60 仍 FAIL A。.45 胶囊已能看到书页结构，不再是白板（相对 .86 的 FAIL B 改善）；胶囊内汉字仍可辨认残画，FAIL A 未清零。菜单同。|暂留 **light .45 / blur 26**。下一步不是 lens，而是：1) 拆开 Control（现被 readerOverlayFrost 带着改了）；2) 在 .45 上再加 blur 或再降 tint；3) 独立 dark。截图 `device/batch2/`。|
|3|lens|off|Interactive H6–12/A16–30；Content H2–6/A4–12|NOT_RUN|需先完成 Batch 2|
|4|edge|0（Dialog 除外）|.15–.4dp，轻量 Highlight|NOT_RUN|现有 Kyant API 支持 width/blurRadius/alpha/style，无需另建框架|
|5|shadow|6dp reader|4,2,0dp|NOT_RUN|先确定 lens+edge 可分离背景|

## Reader fixtures A–E

已生成：`/storage/emulated/0/inbox/M1racleReader/r36-2/`。

- `R36-2-optical-A-E.pdf`：五页均为 595×842pt；A=10.5pt 密集中文，B=26pt 标题+14pt 正文，C=11pt 表格+0.5pt 线，D=灰阶图，E=彩色图。图片页叠加黑/白文字和高频线条。
- PDF SHA-256：`af30a16ccf8075ac84cd7ebb0138ee2fbef4941494a181a2ecb9b5b1003cc9c1`；中文 TrueType 字体子集嵌入，前 3 页可提取 2632 / 1259 / 1189 字符。
- 同目录 `R36-2-optical.md`、`fixture-D.png`、`fixture-E.png` 和 `manifest.json`，供 Markdown 对照；manifest 含 font/file hashes。
- 可重复生成脚本：`scripts/generate-r36-optical-fixtures.py`。主机依赖 reportlab 5.0.1 / Pillow 12.3.0，安装在 `/tmp/r36-fixture-venv`，不进入 Gradle 或 APK。
- 两次独立生成 PDF 经 `cmp` 完全一致。PyMuPDF 1.28.2 验证页数/尺寸/文字/嵌入字体；已查看主机渲染 contact sheet，内容正常。这不是 Reader 真机视觉证据。
- 主机证据：`.external_outbox/r36-2/fixture-contact-sheet.png`、`fixture-validation.json`、`fixture-repeat.log`。

验收时固定 viewport 像素、density、主题、页码、zoom、scroll offset 与控件展开状态；先为每页记录 baseline，再每次只变一个主维度。不用设置页色块预览代替教材页。

## Light / Dark findings

PENDING：暗色独立验收，不沿用亮色结论。

## Fallback

implemented / automated_verified：`GlassCapabilityProbe.forSurface` 在 idle/pressed spec 解析之前处理 window/host/shape。

旧根因：`sampleBackdrop=false` 只选择了 soft renderer，却没有给 resolver 传 SOLID；将来降低采样材质 tint 会直接露出未模糊正文。本轮让无采样表面采用 Engine 内独立 fallbackPlate 的 tint/edge；Dialog 也共享它。独立 fallback 端点不跟随后续 Panel 光学 tint 调低。Dock 没有 backdrop，显式 SOLID。

覆盖：三种设备 capability × 无 host / 禁采样、custom shape 降级、所有 role/environment 的无 sampling/blur/lens/vibrancy/chromatic 与 foreground 不变，以及 modifier 接线顺序、progressive fallback/preview 共用 renderer。真机可读性仍 pending。

## Performance / Device evidence

NOT_RUN：ADB 无 device。PDF vertical/horizontal、static/slow scroll/fling/pinch/page commit、Markdown、More/submenu/Dialog/Home Dock、Light/Dark 全部待测。idle invalidation、RenderThread、black first frame、old texture、lag、GPU leak 未验证。

## Tests / Release

- `:app:compileReleaseKotlin`：PASS（最终源码）。
- 首轮 `:app:testReleaseUnitTest --tests '*Glass*' --tests '*ReaderChrome*' --tests '*ReaderFixedGradientUiTest'`：82 tests / 10 failures / 0 errors / 0 skipped；72 非 UI 测试通过。10 个 UI 测试因 Release 无测试 ComponentActivity 无法启动；未删除/跳过测试或改变断言来制造成功。
- 使用项目已有 `qaImplementation(ui-test-manifest)` 隔离测试配置运行同组测试：首轮 82/0/0/0；补充 renderer 接线回归后最终 **83/0/0/0**。只运行主机单测，没有 assemble/install QA。
- 最终命令：`JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64 ./gradlew :app:compileReleaseKotlin :app:testQaUnitTest --tests '*Glass*' --tests '*ReaderChrome*' --tests '*ReaderFixedGradientUiTest'`。
- 定向 diff whitespace 与 fixture Python 语法检查通过。没有运行全仓测试，也不声称 GPU 光学正确。
- `:app:assembleRelease`：FAIL，`:app:validatePublicReleaseConfiguration` 拒绝；当前进程未注入 `M1RACLE_RELEASE_STORE_FILE`、`M1RACLE_RELEASE_STORE_PASSWORD`、`M1RACLE_RELEASE_KEY_ALIAS`、`M1RACLE_RELEASE_KEY_PASSWORD`。需在可信运行环境注入既有正式 signer，不在聊天中提供密码。
- 构建同时记录既有 lint ASM `NegativeArraySizeException`；不将其描述为完整 lint 通过，未禁用检查。
- 版本仍 1.0.0 (23)；本轮 APK size / digest / signing status：N/A（没有成功新产物）。没有复制或交付旧 Release，Download 未覆盖。
- 日志与 XML：`.external_outbox/r36-2/` 下 `compile-tests.log`、`release-test-results/`、`final-tests.log`、`qa-test-results/`、`assemble-release.log`。

## Known issues / Deferred

- R36.2 光学调参及真机验收尚未完成。
- Kyant Combined/export/frame-sync 的设备行为仍 UNKNOWN；不改既有 Document→Backdrop→Overlay DAG。
- ProgressiveChrome 完整 progressive shader 延后，geometry 留在 renderer。
- DockSelection 已删除；未来 selector 需要新的明确任务，不借本阶段重新接入。
- **R36.3 Liquid Interaction System 未做**；pointer、drag、velocity、touch-following highlight 均不新增。
- Commits: none。


## Files changed in this session

只列本轮触碰文件；它们可能已经包含用户先前未提交修改，不把整个 git diff 当本轮结果：

1. `app/src/main/java/com/m1racle/reader/ui/material/GlassAuthority.kt`
2. `app/src/main/java/com/m1racle/reader/ui/material/GlassMaterial.kt`
3. `app/src/main/java/com/m1racle/reader/ui/material/BlurComparisonPreview.kt`
4. `app/src/main/java/com/m1racle/reader/ui/hub/GlassDock.kt`
5. `app/src/main/java/com/m1racle/reader/ui/viewer/ReaderGradient.kt`
6. `app/src/test/java/com/m1racle/reader/ui/material/GlassAuthorityContractTest.kt`
7. `app/src/test/java/com/m1racle/reader/ui/material/GlassMaterialRegressionTest.kt`
8. `app/src/test/java/com/m1racle/reader/ui/material/GlassOpticalPolicyTest.kt`
9. `app/src/test/java/com/m1racle/reader/ui/pdf/ReaderChromeTokensContractTest.kt`
10. `scripts/generate-r36-optical-fixtures.py`
11. `.memory/active_object.yaml`
12. `docs/CURRENT_STATUS.md`
13. `docs/R36_2_OPTICAL_MATERIAL_REPORT.md`

## Resume / R36.3 prerequisites

1. 接入当前 Android 16 Xiaomi 真机（只有新的有效端点/连接信息才再次探测），在可信环境注入正式 Release signer。
2. 从 `.external_outbox/r36-2/baseline/` 和本报告重建/记录相同 viewport 的 before；不把历史 APK 当 R36.1 baseline，因为 R36.1 未打 Release。
3. Batch 1 只比较 18/20/22/24/26/28dp blur；固定 tint，确认正文失去语义且结构仍在，然后才进入 tint。每批保留参数与实际截图/理由。
4. 继续 Interactive/Content/Panel role overrides、独立 Dark、弱 lens、Kyant edge、低 shadow，移除不再需要的实验/参考预览后收口。当前基础代码尚不能算这些视觉目标达成。
5. 完整设备矩阵 + 性能/稳定性证据、Release 打包/验签/实际大小/Download 核对后才标记 R36.2 完成。
6. **即使 R36.2 完成，也停止；只有新的明确授权才进入 R36.3。**
