# 二级面板错误来源行修复 · 2026-09-25

## 用户报告与根因

用户指出点击教材目录后从上一栏展开，其他入口也一样。此前测试只覆盖旧的两条内联入口+四条任务入口，漏掉生产菜单中新增的“编辑正文”。

生产 PdfViewerScreen 将“编辑正文”放在四条任务入口之前，但 ReaderMoreHost 的 childRowIndex 仍将 CHILD_* 固定映射到 2..5；行坐标登记与原行隐藏也复制了这套位置规则。因此实际四个入口都使用了上一行的坐标，且隐藏错误的行。

## 修改

PdfChromeMenuItem.childState 为唯一二级入口身份，统一驱动打开请求、测量坐标键、canonical/frozen 来源和原行隐藏。打开动作由 onOpenChild 统一处理；缩略图准备、导出准备保留在对应 onClick 内，普通操作继续执行后关闭菜单。

删除 childRowIndex、旧位置助手和重复 closeOnClick 标志；去掉所有 2..5/2+i 位置映射。采样层级、液态材质、容器动画参数、阅读与数据逻辑保持。风险中（菜单事件与来源绑定）。

## 验证

- implemented：完成。
- automated_verified：修复前含“编辑正文”的四项 Compose UI 回归全部失败：页码 419→282、目录 556→419、缩略图 693→556、导出 830→693 px，正好高一行 137px。before-test.xml 保存完整反证。
- 修复后四项回归全部通过；补充四项入口重排、一项无编辑入口对照。最终 **105 tests / 0 failures / 0 errors / 0 skipped**，含来源/逆向几何、状态机、菜单交互、大字体和材质契约。
- 旧固定行号测试改为身份绑定、重排与非法身份校验，不保留旧运行路径。回归没有放宽容差；扩展测试集发现一条上轮材质遗留断言，已对齐实际 Menu 液态配方。
- 完整 lintRelease PASS，0 errors / 158 warnings。最终源码与验证时 SHA-256 一致。
- device_verified：NOT_RUN，一次 adb devices -l 为空。pending：用户安装后确认真实 PDF 菜单各入口原位展开/收回。

## 交付

正式签名 assembleRelease PASS；1.0.0/23；6,950,677 bytes / 6.63 MiB。ZIP、签名、无 INTERNET 校验通过。
SHA-256 `f5dce26278c3fc7a12007a924e9fde887f2ec8a5c3cf0c339ccbaa0569c9f1ec`。
构建输出、Outbox、`/storage/emulated/0/Download/m1racle-reader-release-latest.apk` 摘要一致，已自动覆盖。

证据 `.external_outbox/nested-row-origin/`：before.log/before-test.xml、verify-final.log/test-results、verification.json、lint-release.xml、release.log/delivery.json、source-hashes.json。未提交 Git，保留既有工作区修改。
