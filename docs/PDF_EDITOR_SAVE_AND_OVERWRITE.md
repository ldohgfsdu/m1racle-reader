# PDF 编辑保存与源文件覆盖

2026-09-07。用户明确要求直接保存/覆盖，并反馈还有其他问题；已询问具体复现，暂无明细。当前实现和验收事实如下。

根因：PDF 统一按 MANAGED_COPY 导入，DocumentEntity.uri 是内部副本；原外部地址仅用于哈希别名，不能反推 URI。编辑器只提供另存并新建书库身份，缺少现有身份更新事务。

本轮范围：保存当前书库文档（稳定 id、书签/阅读记录保留）；明确“覆盖源文件”入口（必要时系统定位授权，同步更新书库修订）；来源 URI 与最后已验证来源 SHA-256；外部变更冲突拒绝；持久备份和中断恢复；保存后重新打开当前内容，取消/失败保留编辑。

DocumentEntity 新增可空 sourceUri/sourceSha256，Room 13→14 必要数据保留迁移；PDF 导入记录来源。复用已有库，无引擎迁移。

风险高：文件写入与持久化；必须验证迁移保留数据、失败不丢原文、同一书库 id/子记录保留、外部冲突、输出核验与中断恢复。本任务 ADB 为空，实机验收待人工安装。

## 实现检查点

- `DocumentRepository.savePdfEdits`：先写独立持久修订，再以现有事务入口更新同一文档 id，保留阅读进度、书签/笔记/高亮记录；内容冲突检查复用 ContentIdentityResolver。源文件写回和库内修订属于同一提交，失败先恢复外部备份。
- `PdfSourceWriteStore`：写前两次原内容核验，持久原件备份和 AtomicFile 操作记录，写后 SHA-256 核验。启动时按 Room 是否完成提交判定收尾或回退；检测到非本次写入的外部新内容时保留备份且不盲目覆盖；编辑页允许导出备份。
- UI：顶部保存当前书库文档；保存方式 → 覆盖源文件并保存 / 另存副本 / 恢复备份导出。没有写权限时使用 ACTION_OPEN_DOCUMENT，首次选择匹配原件并授权；记录来源供后续使用。已经本地保存但尚未写回的版本仍可覆盖源文件，不要求再改一次字。
- 保存后新建阅读会话并定位编辑页，避免使用旧渲染缓存；保存中进程退出后通过提交摘要判定是否已经保存，防止把旧草稿再次套在新版本上。
- 修复普通 MCID/Lang PDF 结构标签被整页误拒绝的问题；ActualText、可选图层等未验证结构继续拒绝。
- Room 13→14 仅新增 sourceUri/sourceSha256 两个可空列；程序比对历史 schema 的已有列、主键、索引和外键完全不变（schema-diff.json）。旧 schema 文件未修改，历史摘要断言保留并新增 v14 摘要。

- 内容相同的多个外部文件复用书库身份时，来源关联更新为最近打开的文件，避免继续写回先前路径；增加双来源导入回归。
- 进入编辑前关闭阅读器任务并等待读取句柄释放，再移除旧阅读会话；退出编辑重新打开阅读器。提前释放不跳过最终进度封存。

## 当前验证

implemented：上述保存、覆盖、备份恢复、来源关联及阅读会话切换已落地。

automated_verified：最终全量 QA 单测 **1439 / 0 / 0 / 0**；包含备份/中断恢复 6、迁移 1、导入与保存仓储 13、正文编辑 11、保存选项 UI 2 项。全量回归发现的旧版本断言已按必要迁移扩展，哈希查询复用既有权威入口；未削弱原有数据保护断言。Debug、签名 Release 构建成功；最终代码冻结后重新执行测试与构建。

交付：Release **6,833,894 bytes**，比上一轮 6,817,510 增加 16,384 bytes（约 0.24%）。SHA-256 `e0ef482a89d94c56b926196797a1f62591c9e238054539cae8e8517ec354d9f8`。生产签名摘要 `2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1`。版本 1.0.0/23，Room v14，无 INTERNET 权限，测试字体未打包。已自动复制到 `/storage/emulated/0/Download/m1racle-reader-release-latest.apk`，摘要与 outbox 一致。Debug 测量 79,490,851 bytes。

证据目录 `.external_outbox/pdf-editor-save/`：`debug-and-tests.log`、`release.log`、`final-verification.json`、`signature.txt`、`badging.txt`、`schema-diff.json`、`source-hashes.json`。
本任务一次 ADB 探测为空（.external_outbox/pdf-editor-save/adb.txt），不得再轮询；device_verified=NOT_RUN。lintQa 仍遇到现有 Compose 检测器与 Kotlin 分析 API ABI 崩溃，记录为未通过，不声称 lint 干净。

pending：人工安装并验证 SAF 授权/取消/写回、进程中断恢复、编辑入口与返回定位、渲染/键盘/旋转交互。持久备份覆盖进程中断恢复，不等于任意文件提供方都支持原子写入或断电事务。用户其他问题仍缺具体复现，本报告不宣称所有体验问题已解决。

参考 Android 官方 SAF 文档：https://developer.android.com/training/data-storage/shared/documents-files （CreateDocument 另建文件，OpenDocument 获取现有文档访问；本项目保留无 INTERNET 权限）。
