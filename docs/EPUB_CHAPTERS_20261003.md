# EPUB 章节换页与目录定位修复 — 2026-10-03

implemented / automated_verified / device_verified。正式签名侧载 **1.0.0 / 31** 已覆盖安装用户手机并复制至 Download；未公开发布 / tag。

## 根因和结果

旧 `chapterStart` 只标记每个 spine XHTML 的首块；同文件中的多个目录章节 / 小节继续流入同一页。`pageForToc` 仅返回含目标块的页面，标题之前可能还有上一节正文；失效 fragment 还会退回文件首。页脚也按整个 spine 文件计数。

现在分页与目录共用真实块锚点解析：文件边界、有效目录章节 / 小节以及未列入目录的一级章标题从新页开始，普通未列入目录的低级标题仍流排。目录页首就是目标标题，不包含上一节正文；失效 fragment 不再误跳文件首。章节标题和 1/N 按实际逻辑节范围显示。保留原 sourceRow / 字符身份、字号重排、覆盖翻页和固定顶栏避让，不修改导入缓存、数据库或用户阅读偏好。

本轮产品修改仅 `EpubTextLayout.kt`、`EpubPagination.kt`、`EpubReaderContent.kt`，以及 versionCode 30 → 31；新增 4 项根因回归，保留既有测试和其他聊天全部改动。

## 分层验证

- red：同 XHTML 三个嵌套目录章 / 节应有三个独立页，及失效 fragment 不应返回文件首，两项在旧版均失败（退出 1），失败 XML 保留。
- fast：EPUB / Release 身份 46 项定向测试全通过，退出 0；之后补充无目录 H1 的回归并纳入最终完整测试。
- full：`gradlew.bat :app:testQaUnitTest :app:lintRelease --console=plain`，**1654 tests / 0 failures / 0 errors / 2 既有环境跳过**；其中最终 EPUB 42 项全通过。跳过为外部《老子》教材 fixture 和 Windows 符号链接权限。Release Lint 0 errors / 158 warnings / 4 hints；退出 0。真实 UI 回归验证左右翻页及目录点击后，同文件第二章在首行、没有上一章尾文，并单独显示 1/1。长段落、字号 ABA、取消拖动、锚点恢复和顶栏避让回归保留。
- release：JDK 17；`gradlew.bat :app:assembleRelease --console=plain` 退出 0，签名、原证书连续性、16 KiB zipalign、版本 / Manifest 检查通过，无 INTERNET 或 QA Activity。测试、构建、交付的产品源码指纹 `f1828f690a0cc1007c9ec324c2da19b075f2a7b94be560d34bd90d6a1b06248d` 一致。
- device：API 36 真机 `db04f865` / 25098PN5AC，原“大”字号和系统 1.1 倍字号保留，`adb install -r` 成功且未清数据。用户实际书的同文件目录“这本书想回答的问题”跳至第 3 / 1769 页，标题首行、节内 1/6；“怎么读”跳至第 9 / 1769 页，标题首行、节内 1/4。两处均检查上一页不含新标题，往回翻再向前翻恢复标题页和完全相同正文。验收后恢复新包最初的“怎么读”第 1 页，即全书第 9 / 1769 页；退出、结束进程、重开后可见文字和页码完全一致。重开进程日志未见 FATAL EXCEPTION / ANR。

回归期间发现 IntRange 页数 API 编译错误，修正为 count 后重跑成功；该失败发生在测试执行前，外部元数据明确标出旧 XML 不属于该次执行。真机脚本最初以正文长度超过 200 字定位 Canvas，无法识别换章后的短页；改用 UI 树的真实画布边界定位，保留全部标题首行、上一页无标题、目录页码和位置恢复断言，重新完成验收。未为制造通过削弱产品断言。

## 正式产物

来源：本轮 `app/build/outputs/apk/release/app-release.apk`；主机 `.external_outbox/m1racle-reader-release-latest.apk`。大小 **6,994,053 bytes**；SHA-256 `09617de1f9df60e2581bdd3b926c9d44b90549639ae70028f2528ad0c84c38c4`。

手机 `/storage/emulated/0/Download/m1racle-reader-release-latest.apk`，交付时间 `2026-10-03T09:45:38.188356+00:00`。构建输出、Outbox、Download 和实际安装 base.apk 摘要一致。生产证书 SHA-256 `2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1`；沿用用户签名授权，材料仅在仓库外受限临时目录读取并经环境注入，构建后清理，无密码 / 密钥日志。

## 身份与交接

unmanaged；runId / projectId / machineId unknown；Codex / GPT-6 / version unknown，Windows。项目契约与中央工程公约本机不可用，不声明管理租约。分支 `codex/fix-project-audit`，HEAD `a314247d9cc27f7b3adc1edb3074b65eee3afbfe`；构建 dirty fingerprint `56f35caa3ef20fd268f31b7bfd118eeee3b9aca358d9c1658e24bdfe05d921f6`。所有已有 dirty 修改保留，无 reset / stash / clean / commit / push。

外部证据 / handoff：`C:/Users/21234/.devhub/runs/unmanaged-reader-epub-chapters-20261003`，绑定 HEAD、源码指纹、dirty fingerprint、命令 / 退出码、时间与 unknown 身份。当前实机覆盖这本书的两处标题目录及跨页边界，不代表其他书、任意内联锚点、图片、深色设备或性能全量验收；现有 EPUB 块级锚点和有限格式支持范围未扩大。人工观感 / 手感及公开发布未自动标成通过。
