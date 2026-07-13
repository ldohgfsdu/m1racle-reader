# PDF_WORKBENCH_P0_2A

大页 PDF 快速定位 + 教材印刷页基础映射。

## 功能

- 点击页码（顶栏 / 底栏）→ 输入 PDF 页码跳转
- Slider 快速拖动：拖动只更新页码/印刷页提示，松手再渲染
- 设置当前 PDF 页的教材印刷页锚点；后续页线性推算
- Room 表 `pdf_print_page_anchors`（v3 / Migration 2→3）

## 推算规则

最近 `pdfPageIndex ≤ 当前` 的锚点：  
`印刷页 = 锚点印刷页 + (当前PDF页 - 锚点PDF页)`（均为 0-based 索引差分）
