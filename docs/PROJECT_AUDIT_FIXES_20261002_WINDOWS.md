# Windows 全面审查修复 — 2026-10-02

本轮针对备份审查中的 F1–F8 修复真实源码。工作树从原 bundle 恢复到 `reader/`，分支 `codex/fix-project-audit`，基线为 `a314247d9cc27f7b3adc1edb3074b65eee3afbfe`。未提交、未推送；原源码 ZIP、Git bundle 和历史 APK 保留。

## 修复与回归

| 项 | 根因修复 | 回归证据 |
|---|---|---|
| F1 / P1 | 所有 Markdown 导入保存私有正文副本；哈希命中旧 SAF 记录时从已验证 staging 恢复正文。旧空哈希记录和 sidecar alias 提升必须在提交时重新读取并核对原字节。读取正文可校验已有身份，源文件变化不污染旧资产。相对图片仍使用原授权位置。删除可变 SOURCE_URI 策略。 | `ContentIdentityImportWiringTest`：可变源、旧哈希恢复、不可读 alias、prepare 后变化、正文校验；保留原 id 和书签。 |
| F2 / P1 | 目录 REPLACE / MERGE 的读取、计划、删除和全部写入放进同一 Room 事务；子树删除和批量插入也整体事务化。 | `OutlineImportTransactionTest`：真实 Room 下替换、合并、取消、子树删除和批插入失败回滚，以及完整成功提交。 |
| F3 / P1 | EPUB 全书限制：spine 2048、逻辑章节 XML 32 MiB、正文 4 Mi 字符、块 40000（含列表项）；重复 spine 引用同样计量并共享解析结果。超限明确失败，不降级成异常占位。 | `EpubPackageImporterTest`：重复章节正文、XML、块数与 spine 放大。 |
| F4 / P2 | EPUB 在同级唯一临时目录解压；全部完成后写入 extractor version + content SHA 标记，再原子发布。只有精确匹配的完整标记可复用；取消/失败清理临时目录。 | 测试覆盖有 container 但章节残缺的缓存、不同源身份、解压失败不发布部分目录。 |
| F5 / P2 | Markdown ZIP 入口与正文读取统一 32 MiB；package manifest 最多 64 KiB；标题扫描最多 16 Ki 字符。普通 Markdown 提交也遵守可读正文上限。 | `ZipPackageImporterTest` 与 `ImportSizeLimitBoundaryTest`：入口、manifest、标题前缀、普通 Markdown 两入口拒绝超限。 |
| F6 / P2 | EPUB 行转换在 Default dispatcher；单次文本测量最多 2048 UTF-16 单位，保留 span 且不拆 surrogate pair。原 block row / text offset 独立于测量行，TOC 与阅读坐标按原块映射；图片不因长 alt 重复。 | 行拆分、样式、字符边界、图片、分页后坐标恢复与 TOC 回归。真机帧率和视觉验收仍待执行。 |
| F7 / P2 | 删除项目全局 Unix aapt2 override，原生 AGP 默认覆盖 Windows；ARM override 改为本机设置。官方依赖仓库优先，Robolectric 独立 runtime 下载也显式使用官方 Central。 | Windows JDK 17 原生 aapt2 编译及 QA 测试执行。 |
| F8 / P2 | 校正还原 HEAD、提交数、文件数、大小和子目录路径；同步 MANIFEST 中说明文件摘要。旧状态和发布清单归档，当前状态明确历史 APK 与本轮修复的区别，新增候选更新说明。 | `scripts/verify-offline-backup.py` 核对公开文件、sidecar、清单、还原 HEAD、ZIP CRC、文件数，不读取私钥/口令。 |

## Windows 验证中发现的补充问题

- Markdown `file:` URI 的 `/C:/…` 需要转换为 Windows 本机路径，原安全解析矩阵在 Windows 上因此失败；修复后沿用全部原断言。
- 历史 Room schema 的摘要约束要求精确字节。新增 `.gitattributes` 保持 schema 原字节并让 Unix 脚本使用 LF；schema 内容与原 HEAD 完全相同。
- 两项 SAF 取消测试的自制复制夹具在流仍打开时删除文件。夹具改为与两个生产 exporter 相同的“关闭流后删除”顺序，保留删除和取消断言。
- 200 MiB 通用导入包络使用 PDF 验证；Markdown 另测严格 32 MiB 提交限制。Provider 共享状态在每例前重置，防止 MIME/descriptor 状态污染后续测试。
- 旧工程契约测试仍要求的发布证据组和 v12 公开基线约束均保留；历史已勾选事项没有移作本轮通过证据。

## 验证结果

本轮最终结果如下。日志和运行证据写在仓库外：用户缓存 `m1racle-build-tools/` 与 `~/.devhub/`。本轮为 unmanaged，runId / machineId / taskId 未提供，不猜测受监管身份。

- `:app:compileQaKotlin`：Windows / JDK 17，退出码 0。
- `:app:testQaUnitTest`：**1614 项 / 0 失败 / 0 错误 / 2 跳过**。现有 `LaoziAnchorFixtureTest` 因教材 fixture 未挂载、`MarkdownAssetResolverSecurityTest.symlink_escape_rejected_when_supported` 因 Windows 不支持创建符号链接而跳过，未增加排除或弱化断言。
- `:app:lintRelease`：**退出码 0，0 errors / 156 warnings / 4 hints**。初次本机 local.properties 转义错误已修正并强制刷新结果。现有警告包括未使用资源、KTX/版本建议、库中 TLS trust manager 和 trace 等；不在 F1–F8 修复范围内，未添加 baseline 或 suppression 隐藏。
- `:app:validatePublicReleaseConfiguration`：退出码 1，缺四项正式签名环境变量，按原发布门禁拒绝产物。
- 早期完整套件在下载另一版本 Android runtime 时中止，状态为 interrupted，不能算通过。后续一次完整执行为 1614 项 / 1 失败 / 0 错误 / 2 跳过；唯一失败的 provider 夹具共享状态已修复并复测。
- 备份校验脚本：退出码 0；源码 ZIP 758 文件、APK 233 文件、CRC 与公开 SHA-256 全部一致。
- 原 APK `apksigner verify --verbose --print-certs`：退出码 0，v2，正式证书 SHA-256 为 `2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1`。这只验证历史 APK。
- `adb devices`：退出码 0，设备列表为空；device_verified = NOT_RUN。

## 交付与待办

implemented：F1–F8 的源码和文档修复。automated_verified：上述已执行检查通过，保留两项环境跳过和 Lint 警告。device_verified：NOT_RUN。

未生成本轮正式 Release：全局项目规则禁止 Agent 处理发布/签名密钥，且本轮未安全注入正式签名环境。私钥和口令未读取、未改动。发布负责人需安全注入原正式签名并按 `RELEASE_CHECKLIST.md` 构建、验签、交付；禁止使用旧 APK 冒充修复产物或改用 Debug/临时签名。

真机待验：长段 EPUB 翻页与排版、字号重排、目录跳转、阅读位置恢复、Markdown/PDF 打开与阅读资产。全局 `/root/code/AgentStandards/ENGINEERING_COVENANT.md` 与项目 `.devhub/project.json` 在本机不存在，已按可用项目规则保留最低验证和安全边界。
