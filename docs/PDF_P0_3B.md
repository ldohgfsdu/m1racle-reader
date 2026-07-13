# PDF_WORKBENCH_P0_3B

PDF 内置 Outline/Bookmarks → `textbook_outline_nodes`。

## 架构

- **渲染**：仍用 Android `PdfRenderer`（正文 + 缩略图）
- **目录解析**：`PdfEmbeddedOutlineParser`（pdfbox-android，只读元数据）
- **推算 / 导入策略**：`EmbeddedOutlineBuilder` + `PdfEmbeddedOutlineImporter`（纯函数可测）

## 字段

- `source = PDF_EMBEDDED`
- `importKey` 幂等键：`PDF_EMBEDDED|depth|order|parentOrder|page|title`
- `endPageIndex`：下一同级 start−1 → 父级边界 → 文档末页

## 导入策略

| 本地已有节点 | 操作 |
|---|---|
| 无 | 导入（REPLACE 写入） |
| 有 | 替换 / 合并 / 取消 |
| 合并 | 按 importKey 去重，不重复插入 |
| 再次导入相同集 | 幂等跳过 |

## UI

- 打开 PDF 后若检测到目录 → 对话框「发现 PDF 内置目录，共 N 个节点」+ 预览
- 教材目录 Sheet：展开/折叠；可「从 PDF 内置目录导入…」
- 手工编目（P0.3A）仍可修订标题/类型/页范围

## 真机（内科学第10版）

1. 导入 PDF 后应弹出内置目录提示  
2. 导入后篇/章/节层级正确、可展开  
3. 点「第三章 慢性阻塞性肺疾病」应跳到对应 PDF 页  
4. 杀进程再开目录仍在  
