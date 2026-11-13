# Code Wiki 阅读入口

这套中文文档说明 M1racle Reader 的实际代码结构、关键接口、数据流、依赖与运行方式。初次分析：2026-10-13；最近同步：**2026-11-13（Asia/Shanghai）**。

## 基准与事实来源

- 当前 Git 工程根：`D:/03_M1racle Reader/reader-ui-release-20261025`。本文中的工程路径均相对此目录，旧工作树、`output/` 与既有 APK 不作为当前源码链接目标。
- 当前源码规模、版本、HEAD 与摘要以 [扫描基准](reference/snapshot.md) 为准；实际交付及验证范围以 [当前状态](../CURRENT_STATUS.md) 为准，源码版本号不证明整个工作区已进入对应 APK。
- `AppDatabase` 与导出的 `17.json`：数据库版本 17，共 9 张业务表。
- 当前分析包含未提交修改；具体 HEAD 以 [扫描基准](reference/snapshot.md) 为准。本 Wiki 不把 HEAD 当作完整工作树快照。
- 核心分析对象为当前 `app`、`core/model`、`core/database`、`core/pdf`、独立 `pdf-tool`、`benchmark`、构建配置、Manifest、测试与本机运行脚本；外围 `output/` 中的第三方研究仓库、虚拟环境、缓存和备份不计入当前源码。
- 各源集文件数、文本行数与输入摘要统一见 [生成信息](reference/snapshot.md)，只描述扫描时的规模，不衡量复杂度或测试质量。

## 文档目录

| 篇章 | 内容 |
|---|---|
| [01 项目与仓库地图](01-project-map.md) | 能力范围、工作区和源集、模块职责、旧文档差异 |
| [02 整体架构](02-architecture.md) | 手动依赖组装、状态流、线程、生命周期、架构图 |
| [03 数据与锚点](03-data-and-anchors.md) | Room 表关系、事务、位置权威、锚点、设置、迁移 |
| [04 导入与身份](04-import-and-identity.md) | SAF、内容去重、私有副本、ZIP、安全与外部会话 |
| [05 Markdown](05-markdown.md) | 块解析、行内解析、目录、搜索、资源与阅读位置 |
| [06 PDF](06-pdf.md) | 渲染、瓦片、几何、手势、恢复、目录、编辑与导出 |
| [07 EPUB](07-epub.md) | 容器解析、排版分页、三种模式、锚点恢复与范围 |
| [08 UI 与工具](08-ui-and-tools.md) | 路由、Hub、控制器、玻璃材质、扫描和文档操作 |
| [09 依赖关系](09-dependencies.md) | 固定依赖版本、外部库职责、源码包依赖 |
| [10 构建与运行](10-build-and-run.md) | 工具链、Windows/其他主机、Release 签名与产物 |
| [11 测试与排障](11-testing-and-troubleshooting.md) | 单元/设备/性能测试、保护策略、常见故障 |
| [12 端到端调用链](12-call-flows.md) | 导入、批注、书签、编辑、导出、退出的时序 |
| [13 维护说明](13-maintenance.md) | 复核方法、文档更新、证据边界与术语 |

## 自动生成的精确参考

| 参考 | 用途 |
|---|---|
| [源码索引](reference/source-index.md) | 覆盖当前 app、core、pdf-tool 与 benchmark 的生产源码，列出文件、声明与非 private 函数定位 |
| [包依赖索引](reference/package-dependencies.md) | 根据当前生产源码的显式内部 import 汇总包级依赖；与理想分层区分 |
| [数据库字典](reference/database-schema.md) | 从 Room v17 导出文件读取字段、类型、空值、索引、外键 |
| [测试索引](reference/test-index.md) | 当前 app、core、pdf-tool 与 benchmark 的测试文件及文本 `@Test` 数量，按源集与包组织 |
| [扫描与验证信息](reference/snapshot.md) | 当前版本、规模、生成时间、源码摘要和本轮静态校验结果入口 |

建议首次阅读顺序：01 → 02 → 03 → 04 → 需要研究的阅读器 → 10 → 11。带具体问题接手时，从 12 的调用链或源码索引进入。

## 文档使用规则

源码链接使用相对路径，便于随仓库移动。`#L数字` 是 GitHub 风格行号，部分本地 Markdown 阅读器会仅打开文件。函数名用于定位；复杂函数的完整签名、默认参数和异常细节以链接源码为准。

本文把“存在并被调用的代码”“存在但未接入的代码”“历史验证报告”分开描述。静态阅读不能证明设备上的视觉、性能、存储提供者行为或所有产品功能已经验收。历史测试结论只通过原始报告引用，本次仅进行文档与代码引用校验。

Wiki 生成后的应用验证和修复另见 [稳定性核查](../STABILIZATION_20261013.md)；其运行结果不混入本 Wiki 静态引用校验的统计。

本 Wiki 最初索引来自 74 工作树，保留其历史正文与 [旧 validation.json](reference/validation.json) 作为追溯材料；旧索引中的迁移源码路径已更新到当前模块位置，真正移除的源码只保留明确标注的历史代码路径。当前源码索引、规模和静态引用结果分别以 [source-index](reference/source-index.md)、[snapshot](reference/snapshot.md) 和 [validation-current.json](reference/validation-current.json) 为准。旧 `validation.json` 的 PASS 不属于本轮结果。当前交付和验收仍以 [CURRENT_STATUS](../CURRENT_STATUS.md) 为准；历史 77 报告见 [原生高斯连续过渡](../MARKDOWN_TOPBAR_NATIVE_TRANSITION_20261014.md)。
