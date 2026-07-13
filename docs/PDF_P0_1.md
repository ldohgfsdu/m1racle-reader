# PDF_WORKBENCH_P0_1

最小可用 PDF 导入与基础阅读（墨阅统一文档库）。

## 实现摘要

- SAF 可选 PDF（`application/pdf` + 扩展名校验）
- `documents.documentType` / `pageCount`（Room v2，Migration 1→2，无 destructive）
- PDF 进度复用 `lastScrollIndex`（页码）+ `readingProgress` + `pageCount`
- 渲染：`android.graphics.pdf.PdfRenderer`（本地、只读、IO 线程、限 Bitmap 边长）
- 导航：`pdf/{documentId}` 独立页；Markdown `viewer/{documentId}` 不变

## 已知限制（P0.1）

- PDF 顶栏固定显示（与捏合缩放共存；未复用 Markdown TopBar 滚动显隐）
- 无缩略图 / 目录 / 连续长列表翻页（上一页/下一页 + 双指缩放）
- 无 OCR / 编辑 / 导出
