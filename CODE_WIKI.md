# M1racle Reader Code Wiki

本 Wiki 基于 **2026-10-13 当前工作树**整理，源码身份为 `1.0.0 / versionCode 71`、Room schema 17。包括尚未提交的实现；Git HEAD `1150aa6` 仅用于追溯，不能单独代表本文分析的代码。

**[进入完整 Code Wiki](docs/code-wiki/README.md)**

| 阅读目标 | 文档 |
|---|---|
| 了解产品、目录和实现边界 | [项目与仓库地图](docs/code-wiki/01-project-map.md) |
| 了解架构、对象生命周期和依赖方向 | [整体架构](docs/code-wiki/02-architecture.md) |
| 理解数据库、阅读位置、书签与标注 | [数据与持久化](docs/code-wiki/03-data-and-anchors.md) |
| 理解导入、去重、外部打开和安全边界 | [导入与文档身份](docs/code-wiki/04-import-and-identity.md) |
| 研究三种格式的阅读实现 | [Markdown](docs/code-wiki/05-markdown.md) · [PDF](docs/code-wiki/06-pdf.md) · [EPUB](docs/code-wiki/07-epub.md) |
| 理解导航、界面、材质与文档工具 | [UI 与应用功能](docs/code-wiki/08-ui-and-tools.md) |
| 查看技术栈及模块依赖 | [依赖关系](docs/code-wiki/09-dependencies.md) |
| 配置、构建和启动项目 | [运行手册](docs/code-wiki/10-build-and-run.md) |
| 选择验证方式、排查故障 | [测试与排障](docs/code-wiki/11-testing-and-troubleshooting.md) |
| 跟踪典型业务调用链 | [端到端流程](docs/code-wiki/12-call-flows.md) |
| 定位全部 Kotlin 源文件和符号 | [源码索引](docs/code-wiki/reference/source-index.md) |
| 查阅全部测试入口和数据库字段 | [测试索引](docs/code-wiki/reference/test-index.md) · [数据库字典](docs/code-wiki/reference/database-schema.md) |

发布状态以 [CURRENT_STATUS](docs/CURRENT_STATUS.md) 为准。本文是代码说明，不代表本轮重新执行了构建、测试或设备验收。
