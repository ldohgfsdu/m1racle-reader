# PDF 顶部胶囊材质回归修复

用户截图中的标题 / 页码胶囊呈平灰底。根因：底栏选中态及分段滑块共用 GlassMaterialRole.Capsule，前序调整将此角色静止态改成 0.10 覆盖、无模糊 / 折射 / 高光 / 阴影，意外影响 PDF。

核对 Git 基线 a314247d9cc27f7b3adc1edb3074b65eee3afbfe：原 PDF Capsule 为 4dp 模糊、浅色 0.60 / 深色 0.64 透色、8dp / 24dp 透镜、8dp 阴影、0.60 高光。本轮恢复原配方；将底栏和分段滑块原有轻灰及按压光学配方移至 SelectionLens。未改 PDF 布局、标题、页码、跳页、竖排模式选项和算法。

验证：117 项定向 QA 单测通过（材质、ReaderChrome、Capsule、可见性、GlassDock、PillSegmentedControl），Release 源码编译和 QA 构建通过。首次测试发现新增角色缺少测试表项，以及旧配方测试仍锁定历史 8 个角色 / 4dp 底栏；修正为当前各角色精确预期，未放宽断言。新增回归覆盖 PDF 在各主题与交互状态下的原玻璃参数，同时保留选中透镜测试。

模拟器 emulator-5554：内置 QA PDF 浅深色检查、标题点击展开跳页、取消关闭通过；系统恢复浅色。只安装隔离 QA，不作为用户交付。

正式签名门禁退出 1：缺少 M1RACLE_RELEASE_STORE_FILE / STORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD；未发布、未交付 Release，未重跑完整套件和真机验收。

日志与截图在机器外部账本 C:/Users/21234/.devhub/runs/unmanaged-reader-pdf-capsule-20261003；此会话为 unmanaged，runId / projectId / machineId 为 unknown。旧分段控件报告中 Capsule 的选中态名称以本次 SelectionLens 分离为准。
