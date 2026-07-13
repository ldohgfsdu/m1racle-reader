# PDF_WORKBENCH_P0_2B

按需缩略图导航（不预生成全文档）。

## 架构

```
顶栏 Grid 入口
  → ModalBottomSheet
    → LazyVerticalGrid（虚拟化）
      → 可见 Cell LaunchedEffect → PdfThumbnailLoader.load(index)
        → 独立 PdfPageRenderer + 内存 LRU(36)
        → 离屏 cancel 忽略结果
```

- 主阅读页与缩略图各持一个 `PdfRenderer`（分 PFD），互不抢 openPage。
- 缩略分辨率 ~144px 宽，像素预算远小于正文页。
- 点击缩略图 → `goToPage` + 关闭 Sheet；P0.2A 跳页/Slider/印刷锚点不变。
