# PDF_WORKBENCH_P0_4A

按教材目录节点导出 `medlearn-source-chapter` ZIP 处理包。

## 流程

教材目录节点 → 分享图标「导出此节点」→ 确认页（路径/页范围/印刷页/结束页来源，可改起止）→ SAF `CreateDocument` → 后台导出。

## 技术

| 能力 | 实现 |
|------|------|
| 阅读/缩略图 | Android PdfRenderer |
| 页面提取 | pdfbox-android `importPage`（非截图栅格化） |
| 内存 | `MemoryUsageSetting.setupTempFileOnly()` + 临时目录，导出后删除 |
| 打包 | `ZipOutputStream`，固定条目名 |

## 包结构

```
*.zip
├── source.pdf
├── manifest.json
├── outline.json
├── page_map.json
└── checksums.json
```

页码：对外 `pdf_page_*` 为 **1-based**，另存 `source_page_index` **0-based**。

## 结束页推导

下一同级 start−1 → 祖先同级 start−1 → 父 end → 文档末页；子范围钳制在父范围内。
