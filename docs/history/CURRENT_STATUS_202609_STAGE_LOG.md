# 历史：2026-09 项目状态流水（已归档）

> 本文是 2026-09-20 至 2026-09-30 期间 `docs/CURRENT_STATUS.md` 的逐日阶段记录，原样保留。
> 它描述的是**当时**的证据，其中的构建产物摘要、Room 版本、分支与工作区状态大多已被后续提交取代。
> 当前有效状态见 [CURRENT_STATUS.md](../CURRENT_STATUS.md)；文档路由见 [PROJECT_INDEX.md](../PROJECT_INDEX.md)。

---

# 当前项目状态

更新：2026-09-27。此文件是当前工程与发布状态的唯一入口；阶段报告只描述当时证据。

## 当前任务（唯一 active object：PDF_MENU_ELASTIC_HIGHLIGHT_20260927）

### 2026-09-27 对照视频后的有限跟手（IMPLEMENTED，AUTOMATED_VERIFIED，DEVICE_PENDING）

- 重新逐帧看了 `VID_20260927_084006.mp4`（1080×1548，50fps）。菜单卡片中心会跟手指挪大约 10px，灰高亮停在手指所在行；不是整张卡片被拖走，也不是钉在原地只做缩放。
- 根因：上一版把跟手做成绕中心缩放。菜单盒子很高，缩放把上沿往反方向拉，手指下移时第一行几乎不动。
- implemented：按住时整张卡片按有界阻力平移，上限 22dp，方向与手指相同，位移明显小于手指行程；松手弹簧回位。行高亮仍立即命中当前行。底栏、PDF 引擎和文档数据未改。
- automated_verified：弹性 7、行高亮 3、菜单宿主 19，0 失败。生产菜单长按后卡片跟着手指走且仍能打开二级。证据 `.external_outbox/menu-direct-touch/`。
- Release：正式签名 1.0.0/23，**6,917,909 bytes / 6.60 MiB**，SHA-256 `8c8569057b4fbfc6a76b4db34a8ea8261d5ae9020e0ba55cece7a545b75d8691`。构建输出、Outbox 与 Download 一致，ZIP 通过，证书 CN=M1racle Reader，无 INTERNET。已覆盖 `/storage/emulated/0/Download/m1racle-reader-release-latest.apk`。
- device_verified：NOT_RUN（`adb devices` 为空）。pending：安装后长按并上下移动，卡片应轻轻跟着走，灰高亮停在当前行，松手回到原位。

### 2026-09-27 用户纠正：菜单原位 Q 弹与长按行高亮（IMPLEMENTED，AUTOMATED_VERIFIED，DEVICE_PENDING）

- 用户否定上一版整卡位移，要求卡片原位弹性形变，并显示长按手指所在行。放大视频 3–6 秒逐帧确认浅灰圆角行高亮随手指切换。
- 根因：PdfMenuDrag 使用 Modifier.offset 移动整卡；父层长按消费事件导致子行 clickable PressInteraction 取消，且没有父层手指到行高亮的映射。
- implemented：删除旧平移实现，PdfMenuElasticPress 仅绕固定中心改变 scale；父层统一提供长按手指 root 坐标，行按真实边界命中显示高亮。底栏本轮不改。
- automated_verified：71 项定向回归通过，0 failures/errors/skipped，覆盖原位中心、形变/回弹、跨行实际背景颜色、移出/取消清除、短按/普通滑动/二级来源。完整 lintRelease 通过（0 errors / 157 warnings / 4 hints）；一次 ADB 为空，device_verified=NOT_RUN。详见 [修正记录](PDF_MENU_ELASTIC_HIGHLIGHT_20260927.md)。

- Release：正式签名 1.0.0/23，**6,917,909 bytes / 6.60 MiB**，SHA-256 `c19e6c49bbfb6f9a500751b4f15a9f2172e48f62c43f09f8605b6705dae0c788`。本次 assembleRelease 成功，构建输出 / Outbox / Download 摘要一致且产物时间晚于本轮开始，已覆盖固定 Download 安装路径。ZIP 和生产证书校验通过、无 INTERNET；DEX 存在 `menu elastic press`、不存在旧 `menu drag return`。包含工作区此前已有改动。
- pending：用户安装对照视频确认原位 Q 弹和行高亮观感，GPU 采样/帧率未实机验证。此前整卡位移版本已被用户否定，不视为视觉验收通过。

### 2026-09-27 视频菜单与 RiseDiary 底栏继续优化（IMPLEMENTED，AUTOMATED_VERIFIED，DEVICE_PENDING）

- 用户目标：对照 VID_20260927_084006.mp4 优化生产 PDF 三点菜单动效，并参考 RiseDiary 底栏；保留既有未提交工作。
- implemented：菜单径向阻力、阻尼跟随、再次拾起接续当前位移；底栏选中玻璃胶囊、按压膨胀与弹簧归位，复用原玻璃材质和导航状态，无新依赖。菜单文字仍整体运动，二级来源静止几何保留。
- automated_verified：最终定向回归 72 项通过，0 failures/errors/skipped（含底栏 9 项）；lintRelease 0 errors / 157 warnings / 4 hints。初次 Java 25 编译器缺失改用本机 JDK 17；新测试由未变换布局尺寸改为实际屏幕边界后，按压增大/取消精确归位通过。
- Release：正式签名 assembleRelease 通过，1.0.0/23，**6,917,909 bytes / 6.60 MiB**。SHA-256 `0ab31d69499f454e6190601c2d14a51a22eca29974b68b1270394f703ba2d817`。构建输出 / Outbox / Download 摘要一致，mtime 晚于本轮构建开始；ZIP/签名通过，新增 dockLensPickup 标记存在、manifest 无 INTERNET。已自动覆盖 `/storage/emulated/0/Download/m1racle-reader-release-latest.apk`，包含此前工作区已有改动。
- device_verified：NOT_RUN（一次 ADB 空列表）。pending：人工安装核对菜单跟手/回弹、底栏按压/横拖、玻璃采样及帧率，不能声称和参考像素或手感一致。
- 详见 [参考与实现](VIDEO_MENU_RISE_DOCK_20260927.md)；证据 `.external_outbox/video-menu-rise/`。

### 2026-09-27 PDF 菜单长按拖动、移除设置实验、底栏软边缘（IMPLEMENTED，AUTOMATED_VERIFIED，DEVICE_PENDING）

- 用户明确将录屏动效应用于生产 PDF 菜单，删除设置实验，保留正常设置；底栏参考 Apple 官方 soft scroll edge effect。
- implemented：主菜单长按阻尼拖动、整体轻微拉伸、松手/取消归位；二级来源坐标只接收静止几何。删除所有设置玻璃预览、实验配方及专属测试，生产材质/手势测试保留或迁移。
- 底栏：胶囊外形、滑动选中背景；2/8/20dp 三档真实背景模糊平滑叠合，透明端衔接内容、模糊端延伸至系统导航区。内容 → 软边缘 → 底栏单向采样，无黑/白遮罩。
- automated_verified：111 项定向测试通过，0 failures/errors/skipped。覆盖真实 PDF 菜单长按拖动后打开二级、归位与取消、四个二级来源、生产材质、底栏路由/RTL/横拖与软边缘透明度/高斯方差/触摸穿透。device_verified：NOT_RUN（ADB 为空）。完整 lintRelease 通过，0 errors / 157 warnings / 4 hints。
- Release：本轮正式签名 `assembleRelease` 通过，1.0.0/23，**6,917,909 bytes / 6.60 MiB**。SHA-256 `60eedaf1fca6b5ae6bb048f9201c5ea7e12997d2fab9123b4d5a530a81993204`。构建输出 / Outbox / Download 摘要一致，构建时间晚于本次启动；ZIP/签名验证通过，包内包含 PDF 拖动和底栏软边缘标记、没有旧预览标记及 INTERNET。已覆盖 `/storage/emulated/0/Download/m1racle-reader-release-latest.apk`。包含本次前工作区已有的未提交改动。
- pending：实际设备上的视频动效一致性、采样稳定性、帧率、深浅色和系统导航区观感。详见 [实现与验收](PDF_MENU_DRAG_SOFT_SCROLL_EDGE_20260927.md)。
- 官方依据：https://developer.apple.com/design/human-interface-guidelines/scroll-views 。Android 三档模糊是参照近似，不能声称与 iOS 原生像素一致。

### 2026-09-27 设置预览菜单长按拖动（IMPLEMENTED，AUTOMATED_VERIFIED，DEVICE_PENDING）

- 本轮目标：参考 `VID_20260927_084006.mp4` 的长按拖动动效，先应用于当前设置页柔光菜单预览。
- implemented：长按拾起、阻尼跟手、轻微方向拉伸、松手/取消弹性归位；短按仍选择菜单项。复用 Compose 手势和弹簧动画，无新增依赖。拖动检测节点保持原位，避免视觉位移反过来干扰手势坐标。
- automated_verified：11 项定向测试通过（拖动手势 5、柔光菜单 4、返回按钮 2），0 失败/错误/跳过。覆盖长按位移、松手/取消归位且不误选、短按选择、长按前滑动不拾起、阻尼有界；材质和生产范围约束通过。
- device_verified：NOT_RUN，`adb devices -l` 为空；视频没有触点标记，动画参数是参考近似，观感待真机对照。
- Release：本轮 `assembleRelease` 已成功，正式签名 1.0.0/23，**6,967,061 bytes / 6.64 MiB**。SHA-256 `32b6636ebbf89c0f343674e16c41013eff1c0440afc94e812584ecd199df19a6`。已核对本轮构建输出 / Outbox / Download 摘要一致，ZIP 完整性和签名验证通过，包内确认有新增拖动动画标记，manifest 无 INTERNET 字符串。已覆盖 `/storage/emulated/0/Download/m1racle-reader-release-latest.apk`。完整 lintRelease 通过：0 errors / 158 warnings。此包包含工作区之前已有的未提交改动。

### 2026-09-26 设置页柔光菜单（IMPLEMENTED，AUTOMATED_VERIFIED，DEVICE_PENDING）

- 用户确认先在设置页复现截图里的柔光菜单，并按同一张图学习返回交互。阅读器光学配方、主页和底栏不改。
- 设置页对照默认是这一层。菜单和返回圆按钮都采样舞台背景，并且位于 `GlassBackdropSource` 之外。菜单配方 32dp / 白 0.72 / 折射 0。返回是 44dp 圆按钮，按住整颗缩到 0.94，箭头跟着圆走，正中标题不动。
- PDF 圆形返回改为同一按压方式：整颗按钮缩放，没有单独水波纹。Markdown / EPUB 顶栏仍是普通图标按钮。
- automated_verified：返回交互测试与预览结构测试通过。`lintVitalRelease` 随正式包通过。未另跑完整 `lintRelease`。
- Release **6,967,061 bytes / 6.64 MiB**，SHA-256 `87ec19b6fc22c5c279fe83c36e2f7128c5672d56a755924bfdabb7ac1a7b6f8a`。1.0.0/23。构建输出 / Outbox / Download 摘要一致，已覆盖固定安装路径。签名校验通过，无 INTERNET。该包包含当前工作区里此前已有的未提交改动。
- device_verified：NOT_RUN（此前 `adb devices -l` 为空，本轮未再探测到在线设备）。pending：设置页按住左上角返回，确认圆和箭头一起缩小、标题不动；PDF 阅读页返回同样整颗响应。详见 [设置页柔光菜单](SETTINGS_SOFT_LIGHT_MENU_20260926.md)。

### 2026-09-25 二级面板从上一行展开（IMPLEMENTED，AUTOMATED_VERIFIED，DEVICE_PENDING）

- 根因已确认：生产菜单在四个二级入口前有“编辑正文”，但 childRowIndex 和行隐藏仍固定映射 2..5；测试菜单漏掉该普通入口。
- 实际 Compose 回归已复现四项全失败：教材页码 419→282、目录 556→419、缩略图 693→556、导出 830→693 px，均偏上一行 137px。
- 修正方向：删除固定行号，PdfChromeMenuItem.childState 统一驱动打开、坐标登记/冻结和原行隐藏，普通项插入不影响来源身份。保留已交付液态材质与容器动画时序，不调整位置补偿常数。
- automated_verified：105 项定向测试全部通过，含四个实测来源坐标、有无“编辑正文”、重排、状态机及反向几何。lintRelease 0 errors / 158 warnings；正式签名 Release、ZIP 完整性和无 INTERNET 校验通过。
- Release **6,950,677 bytes / 6.63 MiB**，SHA-256 `f5dce26278c3fc7a12007a924e9fde887f2ec8a5c3cf0c339ccbaa0569c9f1ec`；构建输出 / Outbox / Download 摘要一致，已自动覆盖固定安装路径。
- device_verified：NOT_RUN（一次 ADB 为空）；pending：人工安装后验证四个入口各自原位展开、收回。证据 `.external_outbox/nested-row-origin/`；详见 [修复记录](NESTED_ROW_ORIGIN_20260925.md)。

### 2026-09-25 二级面板材质（IMPLEMENTED，AUTOMATED_VERIFIED，DEVICE_PENDING；动画暂缓）

- 用户反馈二级界面展开问题后明确“先改材质”；本轮只将 SettingsCard（教材目录/教材页码/缩略图/导出章节）接入与 Menu 相同液态配方。保留 RootAndMenu 层叠采样与全部既有几何/动画。
- automated_verified：95 项材质定向测试全部通过；完整 lintRelease 0 errors / 158 warnings；正式签名 Release、ZIP 完整性与无 INTERNET 校验通过。运行代码仅修改 LiquidGlassStyle/GlassMaterial；动画、布局、几何源文件 SHA-256 与修改前一致。
- Release **6,950,677 bytes / 6.63 MiB**，SHA-256 `a3bbf8f1c7fe2b324610c700a237112f705b50e2cb284f18e49d48eddc2445bc`；构建输出 / Outbox / Download 一致，已自动覆盖固定 Download 路径。
- device_verified：NOT_RUN（单次 ADB 为空）；pending：二级材质实机观感。二级文字受变形宽高约束的动画问题已定位，遵从“先改材质”暂缓，不声称修复。
- 证据 `.external_outbox/nested-liquid-material/`；动画源文件修改前摘要已保存，交付前再次核对。

### 2026-09-25 三点展开材质与文字动效修正（IMPLEMENTED，AUTOMATED_VERIFIED，DEVICE_PENDING）

- 用户指出三点展开未呈现液态玻璃，文字独立运动廉价。根因：Menu 被分到 .90/24dp/无折射面板；文字有独立 .94→1 缩放且宽度受变形容器约束，二级正文另有 6dp 位移。
- 旧实现实际布局回归失败：同一文字展开中 X 从 691.0851 变到 647 px。修正 Menu 为按钮同款液态材质；固定文本最终宽度、右侧锚点，仅容器轮廓展开裁切；去除正文独立缩放和二级滑移。
- automated_verified：124 项定向测试全部通过（含旧实现失败的真实布局回归、菜单/二级菜单/大字体场景）；完整 lintRelease PASS，0 errors / 158 warnings；正式签名 Release、ZIP 完整性及无 INTERNET 检查通过。
- 新 Release **6,950,677 bytes / 6.63 MiB**，SHA-256 `cda3891e534e302742ebacd650ddd36570906f3279d5bac9e307c5e2d0aefef1`；构建输出 / Outbox / Download 一致，已覆盖固定安装路径。单次 ADB 探测为空，device_verified=NOT_RUN；pending：用户安装后确认三点菜单材质及展开/收回文字稳定性。证据 `.external_outbox/more-liquid-stable/`；详见 [修正记录](MORE_LIQUID_STABLE_20260925.md)。

### 2026-09-25 全部悬浮材质替换（IMPLEMENTED，AUTOMATED_VERIFIED，DEVICE_PENDING）

- 用户要求“把悬浮全换成液态玻璃”；推广设置页现有液态配方至正式按钮、胶囊、菜单/面板及 Hub 底栏，复用 Backdrop，无新依赖。
- 统一 LiquidGlassStyle 参数来源；Hub 底栏通过导航内容的 sibling 采样，避免捕获自身。跨窗口不可采样表面继续遵守平台能力约束。
- automated_verified：92 项材质/采样边界/底栏交互/阅读控件定向测试全部通过；完整 lintRelease 0 errors / 158 warnings；正式签名 Release、ZIP 完整性及无 INTERNET 校验通过。
- 本轮 Release **6,950,677 bytes / 6.63 MiB**，SHA-256 `064d5200738683a8939c91230ec307b2cc41f36434d9ce60fc18d057d51e5009`；构建输出 / Outbox / Download 一致，已自动覆盖固定 Download 安装路径。包体相同但 DEX 已更新，不是旧包。
- device_verified：NOT_RUN（单次 adb devices -l 为空）；pending：人工安装后检查 PDF 悬浮按钮/胶囊/菜单/设置、Hub 底栏的深浅色、滚动采样与交互。旧柔光 H0 残余闪烁没有宣称解决；保留既有工作区。详见 [交付记录](FLOATING_LIQUID_GLASS_20260925.md)。


### 2026-09-24 残余闪烁继续修复（IN_PROGRESS）

- 用户反馈 4px 预滤波版本“还会有”，上一版未通过用户视觉验收。设备仍在线，正在验证按 Android sigma 换算、保持总方差的逐级模糊，胶囊路径保持。证据 `.external_outbox/soft-glass-cascade/`；未声称新方案完成。

### 2026-09-24 面板闪烁实机修复（IMPLEMENTED，AUTOMATED_VERIFIED，DEVICE_IMPROVEMENT_VERIFIED）

- 用户反馈胶囊已好、面板仍闪；无线 ADB 配对成功，在真实设备 25098PN5AC 录制完整/仅模糊场景，复现面板内部灰斑逐帧跳动。
- implemented：仅柔光 Panel 在大模糊前增加 4px 预滤波；胶囊及生产材质保持。automated_verified：72 项材质测试、完整 lintRelease PASS。
- device_verified：最终签名 Release 安装启动成功，已安装 APK 摘要核对一致；同设备 H0 场景前后录屏，完整与仅模糊帧差中位数均降低约 55%，P99 降低约 59–60%。该指标包含正常移动，不等于闪烁消除比例。pending：用户最终观感确认，未声称所有场景完全无闪烁。
- 当前 Release **6,950,677 bytes / 6.63 MiB**，SHA-256 `f39b1f2a90b5b8510738475f8ad08601062931d09b78129fe401dcc50eee7294`；已自动覆盖 Download，验签及无 INTERNET 校验 PASS。详见 [面板实机修复](SOFT_GLASS_PANEL_20260924.md)。

### 2026-09-24 柔光玻璃移动文字采样（IMPLEMENTED，AUTOMATED_VERIFIED，DEVICE_PENDING）

- 用户报告闪烁并要求继续。真实 Backdrop scope 与 Compose 布局回归确认：默认首步 blur 未保留采样边距、采样区域被父布局约束。不能据此认定 GPU 帧不同步。
- implemented：显式模糊采样边距、实际扩边/文字条尺寸修复；automated_verified：修复前失败反证、修复后 70 项材质测试及完整 lintRelease PASS。
- device_verified：NOT_RUN（一次 ADB 探测为空），视觉稳定性待安装验收。新签名 Release 已覆盖 Download：6,950,677 bytes，SHA-256 `92109187e23c44eb7491e4d61200d2ffa1611550f41e614bc35a2d136b8662f7`，验签及无 INTERNET 校验 PASS；详见 [柔光采样修复](SOFT_GLASS_SAMPLING_20260924.md)。

### 2026-09-24 设置页液态玻璃（IMPLEMENTED，AUTOMATED_VERIFIED，DEVICE_PENDING）

- 用户授权借鉴 lightmemo；默认预览改为低模糊/折射/按压胶囊 + 独立可读面板。旧生产映射删除；阅读器生产配方不变。
- implemented：完成；automated_verified：82 项材质/Hub 测试通过、完整 lintRelease PASS（158 warnings）；device_verified：NOT_RUN（一次 ADB 探测为空）。
- 新签名 Release 已交付：**6,950,677 bytes**，SHA-256 `61b04675646a38ee77e4dea03c3c928e315637657b6070b9a1e84f7dda5584e3`；Outbox / Download 一致，验签及无 INTERNET 校验 PASS。打开设置默认“液态玻璃”，可按住胶囊测试回弹。设计及人工验收见 [设置页液态玻璃](SETTINGS_LIQUID_GLASS_20260924.md)。

### 2026-09-23—24 全面审查修复（IMPLEMENTED，AUTOMATED_VERIFIED，DEVICE_PENDING）

- 用户授权的八项问题已修复：EPUB 翻页、目录层级/相对路径/沙箱、图片比例、删除清理、阅读进度、标注事务、构建检查；保留既有 EPUB 工作和冻结 PDF 光学配方。
- 主机证据：纯逻辑 44/0；全量 ARM 1,565 项中 1,563 通过、2 项被 Robolectric SQLite 的主机架构限制阻塞；同一事务测试 x86 仿真复验 **2/0**。最终 UI **424/0**；仪器测试源码编译 PASS。没有跳过或删除测试，不能称 ARM 全量 Gradle 零失败。
- 完整 lintRelease **PASS，0 errors / 158 warnings / 4 hints**；恢复全部既有禁用门禁。Room schema v14 未变。工具链变更及证据见 [审查修复](PROJECT_AUDIT_FIXES_20260923.md)。
- 第一次包在交付校验中发现残缺，已修复 AAPT2 版本选择并增加导出完整性门禁；残缺时间戳包已删除，不作为交付。2026-09-24 重新签名构建及验签 PASS。
- 当前 Release **6,950,677 bytes（6.63 MiB）**，SHA-256 `0f60f3db5d9f9b6fbd9082455cc7c5b10107af8c5141ccd83000cad4e79fa886`；构建输出 / Outbox / Download 摘要一致，已覆盖固定安装路径。1.0.0/23，正式 signer，无 INTERNET。
- device_verified：NOT_RUN（本轮一次 adb 探测为空）；pending：人工安装后检验 EPUB 前后跨半页滑动、长图比例、目录、退出/后台恢复进度，以及 Markdown/PDF 基本打开及标注。
- 本次未提交、未修改远端/tag/发布状态；证据 `.external_outbox/audit-fixes/`。下方日期记录为历史阶段事实。

### 2026-09-21 EPUB 翻页跳动

- 用户报告页面老是跳。根因：覆盖翻页在静止时把上一页平移到当前页上，且 `currentPageOffsetFraction` 在 0 附近抖动会整页翻转；视口差 1px 会整本重排并 `scrollToPage`。
- implemented：静止和完全离屏页不再做覆盖变换；视口 ≤2px 抖动忽略；仅在目标页变化时恢复进度。
- automated_verified：分页定向测试含静止邻页不叠加。正式签名 Release 覆盖 Download。
- device_verified：NOT_RUN。

### 2026-09-21 EPUB 沉浸阅读

- 用户要求对照开源 EPUB / 本仓库 Markdown 阅读器做沉浸。KOReader、Foliate、微信读书、Apple Books 默认都是正文铺满、点中间才出控件；本仓库 Markdown 靠滚动收起顶栏，PDF 靠点按切换 chrome。分页 EPUB 应对齐点按显隐，而不是常驻 Material 顶栏和 72dp 翻页条。
- implemented：默认隐藏返回/目录/设置；只留淡页码；中间点按唤出顶栏；翻页后自动收回。左右点按与滑动仍翻页。不改 PDF 系统栏隐藏机。
- automated_verified：EPUB UI 定向测试含「默认无 chrome、中点唤出」。正式签名 Release 覆盖 Download。
- device_verified：NOT_RUN。

### 2026-09-20 EPUB 覆盖翻页过渡

- 用户反馈翻页没有过渡、像滚筒。根因：HorizontalPager 默认两页一起平移（走马灯），顶栏全屏遮罩还挡住跟手滑动。
- implemented：覆盖翻页（当前页钉住，下一页带阴影盖上来），点按/按钮 460ms 缓动；顶栏和页码条不再挡住正文手势。
- automated_verified：EPUB UI/分页定向测试通过。正式签名 Release 已覆盖 Download。
- device_verified：NOT_RUN。pending：真机看跟手覆盖与点按过渡。

### 2026-09-20 EPUB 阅读质量（图片/表格/点按翻页，进行中）

- 对照用户书库两本实书（`HowToLiveBetter.epub`、`认知觉醒…epub`）：封面/插图被占位、Calibre SVG 封面被当成不支持媒体、前言表格变成「表格」、章标题与 h1 重复、分页 chrome 常驻占高。
- implemented：本地图片按章相对路径沙箱解析并参与分页绘制（含 `svg image` 封面）；表格行转为可读文本；去掉合成章标题；正文首行缩进 2em；顶栏/翻页控件叠在正文上，左右点按翻页、中间点按显隐 chrome；重排时保留上一版分页，不再先清空。不改 PDF/Markdown，零新依赖。
- automated_verified：EPUB 定向 QA 主机测试 **26 / 0 / 0 / 0**（importer 12、pagination 8、text layout 2、reader UI 4）；`compileReleaseKotlin` PASS。证据 `.external_outbox/epub-reading-quality/`。
- 本轮正式签名 `assembleRelease` PASS（keystore 来自设备内部存储 `M1racle Reader/keystore`，未写入仓库、未在日志打印口令）。产物 **7,068,346 bytes**；SHA-256 `2ef1b622a47ecb65f2ec45c61ca6ba1c4b2b11fc555b15d1f2590d1078d218eb`；signer `2d9daba0…`；1.0.0/23；v2 验签通过。构建输出 / Outbox / Download 三处摘要一致，已覆盖 `/storage/emulated/0/Download/m1racle-reader-release-latest.apk`。
- device_verified：NOT_RUN，单次 `adb devices -l` 为空。pending：手动安装后用《高性价比人生指南》《认知觉醒》看封面、插图、表格、点按翻页与目录安全区。

### 2026-09-19 EPUB 分页、横向翻页与目录安全区（进行中）

- 用户明确要求：实际分页、当前页/总页数、左右翻页，修复目录顶到状态栏。
- 根因：现有正文只有 LazyColumn，未实现分页与页码；目录 fillMaxSize 未应用系统栏安全区。
- 实现方向：复用 Compose TextMeasurer 按实际行布局分页、HorizontalPager 横向翻页；同一分页结果驱动正文/页码/目录落点；字号/行距/视口变化后重排并保持文本锚点。目录应用 safeDrawing insets。不改 PDF/Markdown。
- 当前阶段：实现；待验证：分页边界/长段落完整性/页码与目录/手势/安全区；仅交付正式 Release。ADB 单次探测为空，device_verified=NOT_RUN。
- 前一轮 container 修复仍在工作区，保留；当前日志 `.external_outbox/epub-pagination/`。


### 2026-09-19 EPUB container 初始化异常修复

- implemented：删除 `parseXml` 中 `setXIncludeAware(false)` 调用；Android 不支持该 setter，会在读取 container 前抛 UnsupportedOperationException。XInclude 默认关闭，外部实体防护保持原值。
- 回归证据：测试 provider 模拟不支持 XInclude setter，旧代码 10 项中 1 项失败并复现 container 异常；修复后验证 EPUB2/EPUB3 的 container→OPF→目录→章节链路。
- automated_verified：EPUB 定向 QA 主机测试 **10 / 0 / 0 / 0**；旧代码回归失败→修复后通过。日志 `.external_outbox/epub-container-fix/`。正式签名 `assembleRelease` PASS；lint 出现既有 ASM `NegativeArraySizeException`，不声称完整 lint 通过。
- 本轮 Release：**7,019,194 bytes**；SHA-256 `aba73d92bdffba926a17ba249220d151428a3a49481afb0125a4c23c5a6d177b`；已覆盖 Download，构建输出/Outbox/Download 三处摘要一致。
- device_verified：NOT_RUN，单次 `adb devices -l` 为空；pending：新 Release 实机重开故障 EPUB。


### 2026-09-19 EPUB 有限阅读 MVP（IMPLEMENTATION_DONE，DEVICE_VISUAL_NOT_RUN）

- 范围：用户授权修订 AGENTS/PRD；reflowable 文字章节（路线 B：XHTML→EpubBlock→Compose，零新依赖）；DRM/fixed-layout/音视频/搜索/批注不做。
- implemented：DocumentType.EPUB（TEXT 存储，无迁移）+ 导入门控/外部打开类型化导航（isPdf 保留派生）+ 安全解压（slip/bomb/XXE/加密拒绝）+ OPF/spine + nav/NCX + XHTML 子集 + `epub/{id}` 路由 + 阅读器（连续章节、目录抽屉、fragment 跳转、进度、设置复用）+ Home/Library/Manifest/strings。
- 进度语义：复用 Markdown 约定（lastScrollIndex=行索引、lastScrollOffset=px、readingProgress=全书比例），打开精确恢复；spine 章序隐含在行号中，TOC fragment 直达已实现。
- automated_verified：`testQaUnitTest` **1517 / 0 / 0 / 0**（+7 parser/+3 doctype/+1 外开路由）；`compileReleaseKotlin` PASS；`assembleRelease` BLOCKED_EXTERNAL_CONFIGURATION（无新包、无冒充）。
- 本轮 Release（2026-09-19，正式签名）：**7,019,194 bytes**（EPUB 代码增量约 66KB，零新依赖），SHA-256 `91e8011e29faaf4b5b87891f2d7ca570c683dd7aaa19adba17e046181e019f8d`，signer `2d9daba0…`，1.0.0/23，无 INTERNET。已覆盖 Download。证据 `.external_outbox/epub-release.log`。
- 体积：零新增依赖（平台 Zip/DOM），发布包增量待签名构建实测。
- device_verified：NOT_RUN（单次 adb 为空）。R36 光学暂停（light frozen .86 / dark D0，harness 保留）。

### 2026-09-18 R36.2 Dark Recipe（AUDIT_DONE，DEVICE_APPROVAL_PENDING）

### 2026-09-18 R36.2 Dark Recipe（AUDIT_DONE，DEVICE_APPROVAL_PENDING）

- 审计：production dark authority 逐 role 落表（Control 系 .80 / Panel 系 .86，DARK_ROLE_OPACITY_DELTA=+.06 intentional；Dialog #121212/.97/18dp/.46；Dock #171717/.98），见 [R36_2_DARK_RECIPE_EVALUATION](R36_2_DARK_RECIPE_EVALUATION.md)。无 production bug，frozen 不动。
- 候选：D0(.80/.86) / D1(−.06) / D2(−.12) / D3(+.06)，DarkD1–D3 previewPreset 全 role 统一 delta、frozen blur、前景独立，隔离契约 PASS；R11 审计缺口已闭合（picker 继承 S4，无独立 surface）。
- 分析：白底 D0 已是 #333333 深灰板（场景 A 最高优判别）；D2 白底 #525252 灰板风险最高；深色底四档无判别力、风险在表面同化。视觉列 NOT_RUN。
- 验证：`compileReleaseKotlin` PASS；`testQaUnitTest` **1506 / 0 / 0 / 0**；`assembleRelease` BLOCKED_EXTERNAL_CONFIGURATION。证据 `.external_outbox/r36-2/dark/`。
- device/human：NOT_RUN。Audit 文档 R11 行已更新。

### 2026-09-18 R36.2 Light Tint Reconciliation（LIGHT_RECONCILIATION_PASS）

- 追踪：4 frozen 失败全部绑定 production recipe（`resolve(role, dark)` null-preset 路径），故生产回归 frozen：light `.45`→`.86`、blur 26dp→18dp；dark 与 frozen 一致未动。
- 语义三分：PRODUCTION `.86`/CLOSED_ON_FROZEN_BASELINE；FROZEN CONTRACT `.86`（`LIGHT_TINT_PRODUCTION_AUTHORITY = FROZEN_086`）；CANDIDATE HARNESS `.45/.55/.65/.75` preview-only/DEVICE_APPROVAL_PENDING。新增 production authority test 防回退。
- 验证：`compileReleaseKotlin` PASS；`testQaUnitTest` **1505 / 0 / 0 / 0**；`assembleRelease` 仍 BLOCKED_EXTERNAL_CONFIGURATION。证据 `.external_outbox/r36-2/light-tint/reconciliation-verify.log`。
- 授权进入：R36.2 DARK OPTICAL RECIPE（dark 先审计当前 authority，再提有限候选；edge/shadow/lens 继续冻结）。

### 2026-09-18 R36.2 Light Tint Closure（ENGINEERING_CANDIDATE，DEVICE_APPROVAL_PENDING）

- 起点：clean HEAD `bed8bf4`（`images/r36-home.png` 已移入 `.external_outbox/r36-2/recovery/`，不覆盖旧证据）。
- 单变量已证：对照只动 light tint α（blur 26dp 等全冻结），`GlassOpticalPolicyTest` 新增隔离契约 PASS；harness 新增 .75/.65/.55（preview 路径专用，生产调用不受影响）。
- 评估见 [R36_2_LIGHT_TINT_EVALUATION](R36_2_LIGHT_TINT_EVALUATION.md)：白底 5 候选无判别力（19.4–20.1:1）；深色底 .86→14.6:1、.45→5.3:1；.45 白页“好看”是 backdrop 的功劳，禁止据此批准。
- 裁决：**NOT APPROVED**。frozen authority 保持 `.86`，4 frozen tests 不改；站立工作候选仍 `.45`（不动，不回滚）。
- Audit（只读）：[R36_INTERACTION_ARCHITECTURE_AUDIT](R36_INTERACTION_ARCHITECTURE_AUDIT.md)，11 surfaces / 8 transitions / 6 risks；R11 picker 等 4 个缺口待下一阶段。
- 验证：`compileReleaseKotlin` PASS；`testQaUnitTest` **1504 / 4 failed**（恰为 frozen gate；harness 新测试通过）；`assembleRelease` Wilmington `BLOCKED_EXTERNAL_CONFIGURATION`（无签名环境，未绕过、未冒充）。证据 `.external_outbox/r36-2/light-tint/`。
- device_verified / human_visual：NOT_RUN（单次 adb 为空，不轮询）。

### 2026-09-18 R36 Recovery / State Reconciliation Gate（PASS，已归档，内容保留）

- authority：`.memory/active_object.yaml` = `R36_RECOVERY_AND_R36_2_CLOSURE / state_reconciliation_in_progress`；本节与该文件一致，不再以旧 `R36_2_OPTICAL_MATERIAL_REWRITE / foundation_verified…` 作为当前可执行任务。
- 现场：分支 `feat/risediary-inspired-dock`，HEAD `5284f80` + 74 tracked + 63 untracked 脏工作区；HEAD 不是当前实现状态。冻结见 [R36_RECOVERY_SNAPSHOT_20260918](R36_RECOVERY_SNAPSHOT_20260918.md)，归类见 [R36_DIRTY_WORKTREE_INVENTORY](R36_DIRTY_WORKTREE_INVENTORY.md)。
- 4 QA 失败处置：`.45` 尚未成为正式 authority，**不改测试断言**，标记 optical batch 未完成；4 失败为正确的 gate 信号（Control light .45 / blur 26 vs 旧冻结 .86 / 18dp；panel 26 vs dialog 18）。全量另见 1 个 motion 测试引用搬迁 authority（已对齐修复，非 optical），详见 inventory §4。
- Room 14.json 处置：合法变化（MIGRATION_13_14 加 sourceUri/sourceSha256，hash `e5bd1ee2…` 与 R33 期望一致），独立于 glass batch。
- PDF_GLASS_FLICKER：`BLOCKED_REPRODUCTION / WAITING_FOR_DEVICE_SCENE_CONFIRMATION`——保持 NOT_REPRODUCED，无复现证据不改代码（见下节）。
- SETTINGS_GLASS_COMPARISON：`IMPLEMENTATION_DONE / DEVICE_VISUAL_NOT_RUN`——BUILD_VERIFIED=PASS，DEVICE_VERIFIED=NOT_RUN，HUMAN_VISUAL=NOT_RUN（见下节）。
- Recovery Gate 完成前不恢复 R36.2 调参、不启动 R36.3 实现（Interaction 仅允许 audit）。
- 离线验证（本轮，证据 `.external_outbox/r36-recovery/`）：`compileReleaseKotlin` PASS；`testQaUnitTest` 全量 **1503 / 4 failed**，4 失败即上述 optical 冻结 gate；`assembleRelease` 被 `validatePublicReleaseConfiguration` 拒绝（本 shell 无四项 M1RACLE_RELEASE_* 签名环境，不在聊天中索取密码），**无本轮新 APK，不复制旧包冒充，不改用 Debug**，冻结产物仍为 13:13 `e4acceec…`。

### 2026-09-18 PDF 拖动背景闪烁排查（BLOCKED_REPRODUCTION，保持冻结）

- 用户报告 PDF 拖动时背景闪烁；未确认是玻璃采样背景还是正文整页。不可将“采样不同步”猜测写为已确认根因。
- device_verified：198.18.0.1:34609 为 device，当前安装正式 Release SHA-256 `e4acceec6fe687b31c70d77489ef399058e852db2431444a451c1943e2b636fb`；打开当前实际 PDF 第 281–282 / 403 页（6.2 系统性红斑狼疮，放大状态），录制慢拖、快滑、菜单外拖动。
- 观察：已检查整页采样图及胶囊连续帧，玻璃明暗/红黑色随正文移动，所检查帧未捕获明确背景消失或整页闪白；不能据此断言用户问题不存在。菜单外拖动会关闭菜单，未完成菜单保持展开时的滚动验证。手动复现等待录屏只录到静止页面。
- implemented：本轮无代码/配方改动、无重建/安装/清数据；automated_verified：无新增测试；pending：用户确认具体闪烁区域/可复现场景后继续定位，尚未修复。
- 证据 `.external_outbox/pdf-glass-flicker/`：before.mp4、menu-before.mp4、fast-before.mp4、human.mp4、UI XML、连续帧及 checkpoint.md。设备仍留在 PDF 页面，已停止自动触控。

### 2026-09-18 设置页玻璃配方对照（IMPLEMENTATION_DONE，DEVICE_VISUAL_NOT_RUN）

- 用户明确要求在设置页展示调参方案，供对照 iPad Apple Books。仅扩展已有预览，阅读页生产配方不变，不进入 R36.3。
- implemented：当前／渐变／折射参考，以及 26dp × tint .86/.80/.70/.60/.50/.45/.35、32dp/.45、40dp/.45；浅暗色、密集正文/图案、背景手动滑动及播放。暗色候选不代表已验收。
- 配方仍由 GlassMaterialEngine 解析，预览共用 glassMaterial / readerGradient；无采样能力仍使用不透明降级，不让试验 tint 泄漏正文。
- automated_verified：compileReleaseKotlin 通过；定向 QA 主机测试 84 项，80 通过、4 失败、0 errors/skipped。新增候选隔离/能力降级测试通过；4 条既有失败来自 GlassMaterialRegressionTest（3）及 ReaderChromeMaterialTest（1）仍断言此前 .86 等冻结配方，与本轮开始时已有 .45 调参不一致；未改断言。证据 `.external_outbox/glass-comparison/`。Release assembleRelease 成功（正式签名、minify/shrink）；既有 lint ASM NegativeArraySizeException 仍出现，不声称干净 lint 通过。
- 本轮 Release：6,953,186 bytes；SHA-256 `e4acceec6fe687b31c70d77489ef399058e852db2431444a451c1943e2b636fb`；signer `2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1`。构建输出、Outbox、Download 三处摘要一致，已覆盖 `/storage/emulated/0/Download/m1racle-reader-release-latest.apk`。未自动安装。
- device_verified：NOT_RUN。本轮 adb devices 为空，连接用户提供的 198.18.0.1:46825 返回 Connection refused。不重复探测；用户观感待新包手验。
- 本轮涉及 GlassAuthority、GlassMaterial、BlurComparisonPreview、GlassOpticalPolicyTest 与本状态文件。既有大量未提交修改保留。


### 2026-09-18 R36.2 Optical Material Rewrite（进行中，真机调参待接入）

- 用户明确启动 `/storage/emulated/0/r36.2.txt`；只做静态材质，不进入 R36.3。
- implemented：baseline、optical family、window/shape capability fallback、删除孤儿 DockSelection、progressive preview 复用生产 renderer；A–E PDF/Markdown fixture 已生成收件箱。
- automated_verified：Release 源码编译通过；隔离 QA 定向 83/0/0/0。首轮 Release UI 测试因缺少测试 Activity 启动失败，未跳过或削弱断言；细节见报告。
- ADB 无设备；逐变量 blur→tint→lens→edge→shadow 尚未执行，不能声称光学完成。
- `assembleRelease` 被正式签名 gate 拒绝：缺少四项 M1RACLE_RELEASE_* 环境；无本轮 APK，未覆盖 Download。构建还打印既有 lint ASM 异常，不能声称完整 lint 通过。
- 接续唯一报告：[R36.2](R36_2_OPTICAL_MATERIAL_REPORT.md)，含 baseline、实验顺序、待验收矩阵。

### 2026-09-17 R36.1 Glass Authority Closure（实现与自动化验证完成）

- 目标：生产玻璃路径单一事实源 `GlassMaterialEngine`；视觉与 R36.0 基线等价。未做 R36.2 光学调参。
- implemented：Role×Environment×Capability×Interaction 解析；Markdown 顶栏 / Settings preview / Dock / Home Dropdown 收进引擎；删除 Home/Hub 空捕获；Menu 不再恒 INTERACTION；`DockSelection` 标 NO_CURRENT_CONSUMER。
- automated_verified：定向 QA **93 / 0 / 0 / 0**；`compileReleaseKotlin` 成功。未全量 testQaUnitTest，未 assembleRelease。
- 报告：[R36.1 Glass Authority Closure](R36_1_GLASS_AUTHORITY_CLOSURE.md)。
- pending：R36.2 optical refinement；真机观感。

### 2026-09-16 PDF 控件按可读性收紧（Release 已交付，实机待装）

- 依据：Apple HIG 的对比与材质分层，以及 2026-09-15 DCIM 胶囊截图。不使用 Apple 模板/符号/Icon Composer。
- 截图问题：页码胶囊上的标题被 PDF 正文透出干扰；外影过重，像一块灰板。不是再改裁切几何。
- implemented：阅读浮层（返回/胶囊/菜单）表面浅色 0.86 / 深色 0.80，大面板 0.90 / 0.86；外影浅色 0.14 / 深色 0.28。底栏仍用较透的 0.62，因为叠在首页而不是教材页。18dp 模糊、6dp 半径和裁切留白未改。
- automated_verified：材质与 chrome 定向 **36 / 0 / 0 / 0**；`assembleRelease` 成功。
- 本轮 Release：**6,953,186 bytes**，SHA-256 `10b2181f2cc38c5cebd7a20a2791192feb5b42e184b4dd8dbc8d28bf2dc070c0`；生产签名证书 SHA-256 `2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1`。已覆盖 Download。不是上一轮 `ba4fe2a0…` 截边包。
- device_verified：NOT_RUN，`adb devices` 为空。请安装后看胶囊标题是否还能被正文透出、外影是否还是大方灰块。

### 2026-09-15 PDF 阴影截边修复（实现与自动化验证完成，Release 已交付，实机待装）

- 用户报告阴影异常，并指定 DCIM 最新截图。已查看 Screenshots 内 18:26:58、18:27:01、18:27:04 三张 M1racle Reader 截图；最新一张收起胶囊外影出现矩形截边，另外两张为完整投影。截图不是二级面板，先前二级面板裁切排查不作为本轮根因。
- 代码链路：PdfInfoCapsule 外部 fade 改变整层 alpha，dropShadow 绘制越出控件边界。Compose 在半透明淡入淡出时按**发生动画的节点**分配矩形离屏缓冲；若该节点被收成控件原尺寸，外影会被裁成方框。
- implemented：`PdfChromeVisibility` 在淡入淡出节点内部预留 3× 阴影半径，外层 wrapper 把测量/位置抵消回原控件足迹。三个 PDF 控件入口接入；材质、阴影参数和阅读逻辑保持原值。
- automated_verified：`PdfChromeVisibilityTest` **2 / 0 / 0 / 0**，含淡入中途、淡入结束、淡出中途的缓冲覆盖与原位置/尺寸，以及整屏菜单约束。反向验证：临时 `shadowSpace=0` 时同一胶囊测试失败，已恢复 3×。相关 chrome/材质 **52 / 0 / 0 / 0**；`compileReleaseKotlin` 成功。本轮 `assembleRelease` 成功。日志 `.external_outbox/pdf-shadow-clip/`。
- 本轮 Release：**6,953,186 bytes**，SHA-256 `ba4fe2a0489df6a3745fe866d59aa00a538e570bdef710f6826dfe9c2eabceed`；生产签名证书 SHA-256 `2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1`。Outbox 与 `app/build/outputs/apk/release/app-release.apk` 一致，已覆盖 `/storage/emulated/0/Download/m1racle-reader-release-latest.apk`。不是 2026-09-12 旧包。
- device_verified：NOT_RUN。单次 `adb devices` 为空，请手动安装 Download 中的新包后看胶囊滚动显隐时外影是否还被切成方框。

## 此前 PDF 菜单检查点

### 2026-09-15 PDF 控件完善（实现与自动化验证完成，Release 交付受阻）

- 用户要求完善 PDF 控件并明确“继续”，当前目标转为 PDF 控件可用性；首页不再是当前任务。
- 范围：菜单大字号文字、选中及展开状态语义，对应回归；保留现有磨砂/投影与阅读引擎。
- implemented：菜单行高与字体缩放联动，主容器复用同行高计算，标签最多两行；选择项使用 selectable/RadioButton，展开项增加状态语义；子面板准备判定使用动画目标高度，避免将受约束的实测高度与自然高度比较。
- automated_verified：最终 PDF 相关 QA 单测 **179 / 0 / 0 / 0**；compileReleaseKotlin、lintVitalRelease 退出成功，但 lintVitalAnalyzeRelease 仍打印既有 ASM NegativeArraySizeException，不声称完整或干净 lint 通过。
- 回归证明：修改前 selected 语义测试失败；两倍字号/260dp 矮屏测试在旧高度判定下卡于 PREPARING_CHILD_PAGE_MAPPING，修复后进入 CHILD_PAGE_MAPPING。文字布局测试修改前也通过，不作为裁字复现证据。日志/测试摘要/源码摘要见 `.external_outbox/pdf-controls-polish/`。
- pending：正式签名 Release APK 及设备验收。未打包 Debug、未覆盖 Download、未将旧包作为本轮产物。见 [本轮报告](PDF_CONTROLS_POLISH_20260915.md)。
- 只构建 Release；正式签名环境仍未注入，不得改用 Debug。当前会话 ADB 探测为空，实机验收待设备可用。

## 此前首页检查点

### 2026-09-15 首页设计改进检查点

- 用户以 Apple Design 为参考要求改进；范围继续限定 HOME_LAYOUT_POLISH。
- implemented：空首页增加文字“导入文件”按钮，复用 onImport / isImporting；空状态使用既有品牌文字样式和居中排版，支持矮屏/大字号滚动；空状态及最近阅读标题增加 heading 语义。
- automated_verified：首页定向 QA 测试 **39 / 0 / 0 / 0**（tests / failures / errors / skipped），日志 `.external_outbox/home-apple-guidance/tests.log`；包含空状态导入/忙碌一致性与两倍字号矮屏触达测试。
- device_verified：NOT_RUN，单次 `adb devices -l` 为空；本次无法完成基于当前截图的视觉审查。
- automated_verified：assembleDebug 成功；新 Debug APK `.external_outbox/m1racle-reader-debug-latest.apk`，79,490,851 bytes，SHA-256 `c526adfa199d5166a60d04d4e9677973485a4a090277cd27df037fb039565fb6`。未重跑完整 lint，不能声称 lint 通过。
- pending：Release 所需四项签名环境未注入，本机 signing/credentials 目录为空，正式包交付受阻；人工观感及 TalkBack 验收待设备可用。按 AGENTS 构建后规则已复制并核对既有 Release 到 Download；该包仍是下文 2026-09-12 候选，不含本次改动。详见 [本轮报告](HOME_APPLE_DESIGN_GUIDANCE.md)。


用户授权修复首页实机审查全部问题并改善观感。本轮固定顶栏搜索/导入、约束列表滚动视口避开系统栏及底部导航、移除遮挡内容的悬浮加号；整理继续阅读与列表的字号、间距和两行标题；三点菜单锚定按钮，使用不透明表面，新增独立文件信息入口查看及复制完整文件名。生产签名 Release 已交付，新界面真机及观感验收待无线 ADB 恢复。见 [首页实机问题修复与排版整理](HOME_LAYOUT_POLISH.md)。

## 源码与候选

- 工作目录 `/root/code/MarkLeaf`，分支 `feat/risediary-inspired-dock`；HEAD `2332b21` 加本地未提交修改。HEAD 不等同于本轮构建源码；源码/构建输入摘要 `.external_outbox/soft-glass-sampling/source-hashes.json`。
- 版本 **1.0.0/23**，Room v14，无 INTERNET；Release 保持 minify + shrinkResources。
- 当前正式签名 Release：**6,950,677 bytes**，SHA-256 `92109187e23c44eb7491e4d61200d2ffa1611550f41e614bc35a2d136b8662f7`；比审查开始的 7,101,114 bytes 减少 150,437 bytes。工具链、Room、Compose BOM 调整原因见审查报告。
- signer SHA-256：`2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1`，验签通过。
- 固定交付：`.external_outbox/m1racle-reader-release-latest.apk` → `/storage/emulated/0/Download/m1racle-reader-release-latest.apk`，摘要一致；另附 `.sha256`。

## 发布事实

2026-09-05 通过 GitHub API 与 `git ls-remote` 只读核准：

- `ldohgfsdu/m1racle-reader` 只有 `v1.0.0` Draft，`published_at=null`，尚未发布。
- 本地与远端 `v1.0.0` 指向 `a43633a`；Draft APK hash 为 `e8c0431ab2b38c522a6e12bd3e39349acf78269dd7189bd293b74514f7a64777`，不含最新四个产品提交。
- 本地及该远端均无 `v1.0.1` tag。旧 `POST_RELEASE_BASELINE_1_0_1.md` 的已发布描述与当前事实不符，已随旧工作区归档，不作为现行基线。
- `origin` 仍指向历史 `ldohgfsdu/markleaf`；本次未改远端、tag、Draft 或发布任何内容。

## 历史：首页阶段验证与剩余事项（本轮状态见顶部）

- implemented：本轮首页安全滚动视口、固定操作顶栏、标题层级、紧凑继续阅读卡片、按钮锚定的实色菜单与独立文件信息已实现；此前主页移除确认与操作入口、完整文件名、导入状态与失败反馈已接入；此前有限原生 PDF 正文编辑、撤销/重做、实际 PDF 预览、保存当前书库文档、明确覆盖源文件、另存副本、持久备份恢复；保留书库身份及关联记录，外部打开等待编辑退出；逐行文字高亮、固定渐变 Markdown 顶栏、统一厚雾及设置预览已接入。
- automated_verified：最终全量 QA 单测 **1484 / 0 / 0 / 0**，包含新增视口几何/滚动、导入入口及独立信息查看行为测试；Release、lintVitalRelease、生产签名、Download 摘要和源码摘要通过。证据 `.external_outbox/home-polish/`。
- device_verified：本轮新版本 NOT_RUN。修改前已通过 `198.18.0.1:41681` 检查首页、菜单和滚动，证据 `.external_outbox/home-audit-live/`；随后端点失效，Connection refused，修改前截图不作为新版验收。
- lintQa：前轮已执行并失败，既有 Compose UnrememberedStateDetector / Kotlin UAST API 不兼容导致 IncompatibleClassChangeError（NoteEditDialog.kt、ReaderMoreHostUiTest.kt）；未降低检查项，不能声称完整 lint 通过。本轮 Release 构建及 lintVitalRelease 成功退出，未重复完整 lintQa；本轮 lintVital 分析仍打印既有 ASM NegativeArraySizeException。
- pending：新版首页顶端/滚动/末尾安全区、菜单锚点、文件信息、搜索导入触达及深浅色/横屏/字体缩放实际观感；主页真实 SAF 导入/耗时/超限、移除取消与确认/失败反馈、仅一本书及完整文件名查看；此前编辑选框与实际渲染、键盘/字体缩放、旋转/后台恢复、SAF 授权/取消失败重试/覆盖与另存重开、实际进程中断恢复、外部打开保护、内存耗时和独立阅读器对照；文字高亮实际对齐、Markdown 渐变观感与滚动流畅度、深浅色/横屏/字体缩放、全局厚雾及菜单视觉体验仍需人工验收；本轮 PDF 方影/亮度变化和浅色控件需在相同页面对照。发布 tag/Draft 与候选源码不一致仍待独立处理。

证据：[文字高亮与模糊预览](PDF_TEXT_HIGHLIGHT.md)、[保存与覆盖](PDF_EDITOR_SAVE_AND_OVERWRITE.md)、[正文编辑首个闭环](PDF_BODY_EDITOR_FIRST_LOOP.md)、[此前阅读流程完善](READER_FLOW_POLISH_20260906.md)、[展开菜单加厚](EXPANDED_READER_MENU_FROST.md)、[B 材质与界面打磨](GENTLE_FROST_AND_UI_POLISH.md)。
