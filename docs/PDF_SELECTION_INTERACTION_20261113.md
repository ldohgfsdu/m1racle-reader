# PDF 选字交互重做（95）

2026-11-13。用户对比两段录屏（约 22 秒 Reader、16 秒 Adobe Acrobat）后指出：Reader 的 PDF 选字落后于 Acrobat，差距不在样式，而在拖动中的选区反馈、字符精度和手指遮挡。本轮按 P0 → P1 顺序重做，并给出实际证据边界。

## 定位的真实调用链

录屏问题对应的是当前工作树，而不是 GitHub 上那份旧文件：

- 命中与选区：`core/pdf/.../PdfTextSelection.kt`（`PdfSelectablePage.hit` / `selection`）
- 手势与绘制：`app/.../ui/annotation/PdfHighlightLayer.kt`
- 手柄几何：`app/.../ui/annotation/PdfTextSelectionGeometry.kt`
- 宿主：`PdfViewerScreen.kt` 的 `pageAnnotation` → `ContinuousDocumentViewport` / `PdfHorizontalPagedArea`

旧实现的三处根因：

1. `hit()` 用“到字符矩形的最短欧氏距离”选最近字符，拖动横向偏移时会在相邻行之间跳。
2. 手柄画在字符矩形垂直中心，半径 9dp，视觉中心压在文字上；手柄尺寸不随页面缩放反算，放大后进一步遮字。
3. 拖动过程只有选区色块，没有任何放大预览，手指覆盖处无从判断。

## 本轮实现

### 字符吸附（P1）

`hit()` 改为按行计算：

- 触点落在某行的垂直带内时，只在该行内按水平间距选字符，横向拖动不会跳到别的行。
- 落在行间时按“垂直距离加权 2 倍”的最近字符回退，双栏共用基线时仍由水平间距区分。
- 容忍半径统一按 28dp 手指尺寸换算，长按起点和拖动使用同一口径。

### 选区与手柄（P0）

- 手柄移到字符外侧：位于该行基线下方 `3dp + 半径`，靠近页底时整对翻到行上方，不再压在文字上。
- 视觉半径 9dp → 7dp；触控半径 22dp，屏幕上约 44dp 直径，满足最小触控。
- 手柄半径与触控半径都按 `1/pageScale` 反算，缩放时视觉和触控尺寸恒定。
- 手柄与字符边缘之间画一条细引线，明确指向它控制的那一侧字符边界。
- 选区填充色改为按主题切换混合模式：浅色纸面 Multiply、深色反色纸面 Screen，避免反色模式下蓝块发黑。
- 手柄外圈用纸面底色，内圈蓝色，与 Acrobat 的“轻量圆点 + 明确边界”一致，而不是实心大蓝点。

### 拖动放大镜（P0）

新增 `PdfSelectionLoupe.kt` + `PdfSelectionLoupeGeometry.kt`：

- 复用屏幕上已经渲染的页面 bitmap（`PdfSelectionLoupeSource`），不额外触发 PDF 渲染，放大的是用户真实看到的字符。
- 放大 2 倍，尺寸 148×72dp，位于手指上方 44dp；页面顶部空间不足时自动落到手指下方。
- 放大镜内实时绘制当前吸附字符的左右边界线，以及本次选区的全部行。
- 长按拖选和手柄拖动都会显示；松手、取消、第二指、离开页面立即消失（状态在 `finally` 中清理）。
- 深色反色模式下放大镜同样套用页面反色 ColorFilter 和 Screen 混合。

### 宿主接线

`pageAnnotation` 回调从 `(page, pageScale)` 扩展为 `(page, pageScale, pageBitmap, paperInverted)`，纵向连续与横向分页两种模式都传入当前页 bitmap 与纸张反色状态，`PdfHighlightLayer` 据此提供放大镜源和纸面颜色。

## 验证

| 项目 | 结果 |
|---|---|
| `:app:testQaUnitTest` | 1460 项，0 failures / 0 errors，2 项既有环境跳过 |
| `:app:testReleaseUnitTest` | 1285 项，0 failures / 0 errors，2 项既有环境跳过 |
| `:app:lintRelease` | 0 errors，175 warnings，6 hints |
| `:app:compileQaAndroidTestKotlin` / `compileReleaseKotlin` | 通过 |
| 正式包 | 1.0.0 / 95，27741565 字节，签名 / 压缩 / 16 KiB 对齐核验通过 |

新增回归：

- `PdfTextSelectionTest`：横向拖动留在本行、行内最近字符、逐行边界跟随所选字符而非整行。
- `PdfTextSelectionGeometryTest`：手柄在文字带之外、页底翻到上方、窄字形仍可分辨与命中。
- `PdfSelectionLoupeGeometryTest`：裁剪以手指为中心、不越出纸面、不同 bitmap 分辨率下裁剪一致。
- `PdfTextHighlightGestureTest`：拖选显示放大镜、松手消失、长按不拖动不闪现、无 bitmap 时不显示、手柄拖动也显示。

## 未完成 / 待验

- **device_verified：未完成。** 构建时 `adb devices` 无设备，未安装、未做真机手感验收。Download 交付也因无设备未执行。
- 用户要求的实机对照（同一份 PDF：单字、整句、反向拖动、跨行拖动、放大状态下拖动、复制文本准确性）尚未执行，不能宣称达到 Acrobat 手感。
- 本轮只改 Reader 自身实现，未验证 Adobe 的文本提取准确率是否更高，也未引入 OCR 或替换 PDFium。
- 放大镜在连续滚动模式使用当前页基础 bitmap；若该页仍处于低分辨率档位，放大后会偏软，这是复用现有像素的固有代价，需真机确认可接受。

## 产物

- `D:/03_M1racle Reader/apk/M1racle-Reader-1.0.0-95.apk`
- SHA-256：`A19D529EB4B2C16067380D565C10EB41E85C80C4CFEA3B9D3B4F23874B20EEE5`
- 证书 SHA-256：`2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1`
