# 13 维护、证据与术语

[返回目录](README.md)

## Wiki 的边界

这套文档解释当前实现，不替代 PRD、工程约定、设计规范、发布门禁和当前状态。文档引用代码不等于已经执行相应功能；测试源文件存在不等于测试通过；模拟器测试不等于物理手机验收；当前 Outbox 存在不等于本轮重新构建了它。

分析过程读取了应用入口/导航、仓库/DAO/模型、导入/身份、各格式核心、材质、工具、构建脚本及相关测试入口。对全部 Kotlin 文件扫描包、声明、非 private 函数与显式内部 import，并对当前 Room 导出 schema 生成字典。词法扫描用于导航，不是 Kotlin 编译器、调用图分析器或覆盖率测量器。

当前 Wiki 不包含 signing 材料内容、密码、环境变量值、用户私有文档正文或缓存中的第三方完整源码。

## 复核与再生成

维护工具使用 Python 3.10 及以上，只依赖标准库。在仓库根执行：

```powershell
python docs/code-wiki/tools/build_reference.py
python docs/code-wiki/tools/build_reference.py --check
```

第一个命令更新自动参考：source-index、test-index、package-dependencies、database-schema、snapshot。第二个命令不改生产代码，校验 Wiki 的相对链接、源码行号、受标注的 API 名称及当前引用源摘要；机器结果写入 `reference/validation.json`。链接校验不会执行文档代码块，也不会构建、安装或联网。

手写章节需要在代码变更后人工更新，不能仅重新生成索引就声称全部叙述保持准确。版本升级时同时复核入口基准、数据库版本、能力范围与构建说明。

## 更新触发器

| 变更 | 需要同步复核 |
|---|---|
| 增减 Gradle 模块或依赖 | 01/02/09/10、包依赖参考 |
| 表结构、DAO、迁移 | 03、数据库字典、相关迁移测试入口 |
| 导入、身份、删除 | 04/12、私有副本所有权、取消和去重边界 |
| Markdown 解析/搜索/图片 | 05、锚点/字符坐标、限制与测试 |
| PDF 渲染/手势/持久化 | 06/12、资源关闭、generation、稳定快照 |
| EPUB 字体/分页/恢复 | 07/12、parserVersion、恢复 tier 与模式 |
| 新页面/路由/权限 | 01/08/10、功能是否真正接入 |
| 发布/设备验收 | CURRENT_STATUS 与原始证据；Wiki 只更新实现事实 |

## 当前需保留的实现区别

- Markdown 编辑文件存在但未接入当前导航。
- EPUB 锚点模型可表达更多信息，但搜索、批注、书签消费链路不能因此自动视为完备。
- 搜索索引是普通 LIKE 表，只覆盖标题/摘录/笔记；文内 Markdown 搜索是另一条路径。
- 普通文档具有应用私有读取副本，外部源身份与读取修订分别记录。
- 主应用不联网，但全盘发现需要所有文件访问授权。
- 当前源码包相互引用，不是已经物理拆分的严格层级模块。
- Room v17 是标注 anchor 的数据修复，不是新增业务表。

## 术语

| 术语 | 含义 |
|---|---|
| documentId | Room 文档主键；新内容一般为 UUID，历史 ID 可能有其他形态 |
| contentSha256 | 当前受控读取内容的字节身份；去重依据 |
| sourceUri / sourceSha256 | 外部源位置与最后核验的外部字节身份 |
| alias | 经过身份校验绑定的位置线索，不是替代内容哈希的权威 |
| canonical anchor | 跨格式规范位置模型及其编码表达 |
| projection | anchor 到 locationKind/location 等查询字段的投影 |
| viewport session | PDF 稳定视口快照，含页、归一化锚点、缩放和模式 |
| generation | 单调世代标记，识别文档、几何或异步任务是否仍有效 |
| tile / bleed | PDF 局部高分辨率图块及边缘重叠像素 |
| last-good bitmap | 新渲染成功之前保留显示的旧有效底图 |
| spine | EPUB 的阅读顺序清单；href 比排列序号更适合作章节身份 |
| restore tier | EPUB 恢复采用的精确/片段/章节/进度等层级 |
| backdrop source | 被捕获的正文/页面 RenderNode 内容 |
| chrome | 阅读正文之外的工具栏、菜单、导航等界面 |
| QA sandbox | applicationId 后缀 `.qa` 的隔离数据空间 |
| Outbox | 每次 assemble 成功导出的 APK 与摘要目录 |

## 关联工程规范

[AGENTS](../../AGENTS.md) · [产品需求](../PRD.md) · [设计规范](../../design.md) · [项目文档路由](../PROJECT_INDEX.md) · [当前状态](../CURRENT_STATUS.md) · [发布检查](../../RELEASE_CHECKLIST.md) · [功能与验证入口](../FEATURE_LAYERS_AND_VERIFICATION.md)。
