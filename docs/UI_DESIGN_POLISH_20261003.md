# 基于 design.md 的界面优化（2026-10-03）

本轮针对实际模拟器界面修正首页、文库、书签、设置、搜索与 Markdown 工具栏的层级和布局。设计依据为根目录 [design.md](../design.md)。版本仍为未发布候选 1.0.0 / 23；本报告不代表公开发布或真机验收。

## 问题与实现

| 实际问题 | 本轮实现 |
|---|---|
| 四入口标题大小、顶距与水平对齐不一致 | 共用 HubPageHeader：22sp / 28sp、SemiBold、24dp 内容对齐；保留搜索和导入操作 |
| 继续阅读区域紧凑，列表辅助信息缺少文件类型 | 增加卡片留白，位置说明支持两行；列表显示文件类型与真实进度，增加轻分隔线 |
| 分类选中状态辨识度低，无结果说明混淆 | 分类采用高对比前景 / 背景；区分文库为空和当前分类无结果 |
| 空态图标与说明缺乏稳定组合 | 共用普通表面的图标、标题、说明与操作布局，保留滚动与底栏避让 |
| 设置页长段落堆叠 | 分为应用身份、阅读偏好、数据与隐私；明确阅读设置实际入口，不添加虚假可点击选项 |
| 搜索输入缺少便捷清空，结果层级不统一 | 增加清空按钮，统一内容对齐与结果标题，辅助标签允许换行 |
| 文档首章标题与工具栏文档名重复 | 同名章节仅显示进度，不同章节保留章节名及进度；新增三项根因回归测试 |

内容层继续使用普通表面；既有四入口玻璃底栏与导航状态机制保留，没有新增底部整片铺层。本轮未重写 PDF 正文编辑、EPUB 分页或其他待完成设计能力。

## 自动化验证

使用 JDK 17 执行：

```text
./gradlew.bat :app:testQaUnitTest
  --tests com.m1racle.reader.ui.home.*
  --tests com.m1racle.reader.ui.hub.*
  --tests com.m1racle.reader.ui.navigation.*
  --tests com.m1racle.reader.ui.viewer.components.ReaderPositionCaptionTest
  --tests com.m1racle.reader.data.repository.SearchExperienceIntegrationTest
  :app:assembleQa :app:lintRelease --console=plain
```

退出码 0。针对性单测 75 项，0 失败 / 0 错误 / 0 跳过；QA 构建通过；Release Lint 为 0 errors / 157 warnings / 4 hints。设置页调整使旧 app_subtitle 资源不再使用，新增一条 UnusedResources 警告，其他警告尚未整体治理。未重跑完整套件。git diff --check 退出码 0。

## 模拟器验证与边界

Android 15 x86_64、emulator-5554、独立包 com.m1racle.reader.qa。检查四入口浅色 / 深色、100% / 200% 字号、分类无结果、搜索结果及清空、Markdown 标题与进度。200% 设置页可滚动到末尾，最后一段完整显示在 Dock 上方。新增书签通过阅读器实际操作生成，仅影响 QA 数据。系统字号与主题已恢复至 100% / 浅色。

搜索初次输入被模拟器系统手写引导弹窗打断；关闭系统引导后重新输入并验证，清空后查询为空且清空按钮消失。不能将该系统弹窗当作应用缺陷。

截图、命令日志与可恢复交接存于仓库外 `.devhub/runs/unmanaged-reader-ui-polish-20261003/`（本机用户目录下）。当前会话为 unmanaged，runId / projectId / machineId 未提供，记为 unknown。

QA APK 大小 80,358,529 bytes，SHA-256：

```text
62162343bcb4517a8e33e566ecb7d678c55f2e0796773dc327862d13f4a21424
```

主机 QA 产物与模拟器安装 base.apk 的 SHA-256 一致，仅用于测试。

`:app:assembleRelease` 退出码 1：validatePublicReleaseConfiguration 拒绝缺少正式签名环境配置的构建。未生成本轮正式 Release，未访问签名私钥。真机、TalkBack、降低透明度 / 增强对比度 / 减弱动态效果完整矩阵，以及性能与光学材质验收仍待完成。
