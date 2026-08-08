# Markdown 表格表头/正文列对齐修复

**Date:** 2026-07-21  
**Variant:** release (非 QA)

## 根因

`TableHeaderRow` 将每个表头单元格写死为 `TextAlign.Start`，而 `TableRow`（正文）使用 `block.alignments`（来自 GFM 分隔行 `:---` / `:---:` / `---:`）。

《老子（帛书精堪版）-整理排版版》目录表：

```text
| 帛书编次 | 章名 | 通行本章次 | ...
|---:|---|---:|---:|---|---:|
```

→ 第 1、3、4、6 列为 **End（右对齐）**。  
表头仍左对齐、正文右对齐 → 视觉列网格错位。第二列 `---` 为 Start，双方一致，故“基本正常”。

## 修复

1. `TableHeaderRow` 接收并应用同一 `block.alignments`。
2. 共享 `tableColumnTextAlign()`：`Start` / `Center` / `End`。
3. 表头与正文继续共享同一 `columnWidths`（layout engine 一次计算）。
4. 表头与正文使用同一 `tokens.horizontalCellPaddingDp` / `verticalCellPaddingDp`。
5. 宽表仍 `horizontalScroll` + 固定 `tableWidth`；不压缩塞屏；第 4 列在滚动内容内，非父级永久 clip。

## 测试

`TableHeaderBodyAlignmentTest`：

- 左对齐 start 边一致  
- 居中 center 一致  
- 右对齐 end 边一致  
- header/body padding 与 `columnWidths` 同源  
- 宽表可横滚到最后一列  
- 溢出场景横竖屏列宽稳定，header/body 同宽下 end 边一致  
- 老子式分隔行解析  

`TableHeaderParityTest` 已更新：不再要求 header 写死 `TextAlign.Start`。

## 产物

| 项 | 路径 |
|----|------|
| Release APK | `.external_outbox/markleaf-release-latest.apk` |
| SHA-256 | 见 `dist/table-header-body-alignment/*.sha256` |
| Download | `/storage/emulated/0/Download/markleaf-release-latest.apk` |
| 对齐对比图（示意） | `dist/table-header-body-alignment/before-after-col0-col2-alignment.svg` |

## 真机截图说明

本环境 **ADB offline**，且 Termux 无 `pm install` / `screencap` 权限，**未能**在 25098PN5AC 上安装候选包并抓真实 UI 截图。

提供的 SVG 为**几何精确示意**（同列宽、同 padding、GFM 对齐规则），用于对比 Col0/Col2 表头-正文对齐线；装上候选包后请用人眼确认真机效果。
