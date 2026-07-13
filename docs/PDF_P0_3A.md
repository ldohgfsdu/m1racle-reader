# PDF_WORKBENCH_P0_3A

教材目录编目与 PDF 页面范围标记（**不是**知识点目录）。

## 模型

```
TextbookOutlineNode
├── type: PART | CHAPTER | SECTION | SUBSECTION | ITEM | APPENDIX
├── parentId
├── title
├── startPageIndex / endPageIndex  (0-based, inclusive)
└── documentId
```

Room 表 `textbook_outline_nodes`，Migration 3→4。

## 行为

- 阅读页「书本」图标 → 目录 Sheet
- 新建 / 编辑：类型、父节点、起止 PDF 页
- 起止可「当前页」或「缩略图点两次选范围」
- 点击节点跳转起始页
- 顶栏显示当前最深节点路径
- 删除含子树

不改 PDF 文件；不做拆分/导出/OCR。
