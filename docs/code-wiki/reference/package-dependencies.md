# 显式包依赖参考

[返回 Wiki](../README.md)

只统计 app/main 中显式内部 import，按最长已知 package 前缀归类。数字为 import 语句数，不是调用次数；不包含同包引用、完全限定名调用、回调、反射或外部库依赖。依赖可以形成环，本表不宣称严格分层。

| 发起包 | 被导入包（语句数） |
|---|---|
| `com.m1racle.reader` | `com.m1racle.reader.data.local` (1)<br>`com.m1racle.reader.data.repository` (3)<br>`com.m1racle.reader.data.settings` (1)<br>`com.m1racle.reader.identity` (5)<br>`com.m1racle.reader.share` (5)<br>`com.m1racle.reader.ui.common` (1)<br>`com.m1racle.reader.ui.material` (1)<br>`com.m1racle.reader.ui.navigation` (2)<br>`com.m1racle.reader.ui.theme` (1)<br>`com.m1racle.reader.ui.viewer` (1) |
| `com.m1racle.reader.data.anchor` | `com.m1racle.reader.pdf` (2) |
| `com.m1racle.reader.data.local` | 无显式跨包 import |
| `com.m1racle.reader.data.repository` | `com.m1racle.reader.data.anchor` (5)<br>`com.m1racle.reader.data.local` (29)<br>`com.m1racle.reader.epub` (3)<br>`com.m1racle.reader.identity` (11)<br>`com.m1racle.reader.packageimport` (1)<br>`com.m1racle.reader.pdf` (1)<br>`com.m1racle.reader.pdf.edit` (1)<br>`com.m1racle.reader.share` (4) |
| `com.m1racle.reader.data.settings` | 无显式跨包 import |
| `com.m1racle.reader.documents` | 无显式跨包 import |
| `com.m1racle.reader.epub` | `com.m1racle.reader.data.anchor` (5)<br>`com.m1racle.reader.data.local` (2) |
| `com.m1racle.reader.highlight` | 无显式跨包 import |
| `com.m1racle.reader.identity` | `com.m1racle.reader.data.local` (8)<br>`com.m1racle.reader.share` (1) |
| `com.m1racle.reader.image` | `com.m1racle.reader.data.local` (2)<br>`com.m1racle.reader.markdown` (3)<br>`com.m1racle.reader.packageimport` (1) |
| `com.m1racle.reader.markdown` | `com.m1racle.reader` (1)<br>`com.m1racle.reader.data.local` (2)<br>`com.m1racle.reader.packageimport` (1) |
| `com.m1racle.reader.packageimport` | 无显式跨包 import |
| `com.m1racle.reader.pdf` | `com.m1racle.reader.data.anchor` (2)<br>`com.m1racle.reader.data.local` (6) |
| `com.m1racle.reader.pdf.edit` | `com.m1racle.reader.pdf` (1) |
| `com.m1racle.reader.pdf.export` | `com.m1racle.reader.data.local` (6)<br>`com.m1racle.reader.pdf` (6) |
| `com.m1racle.reader.search` | `com.m1racle.reader.markdown` (5) |
| `com.m1racle.reader.share` | `com.m1racle.reader.data.local` (1)<br>`com.m1racle.reader.data.repository` (5)<br>`com.m1racle.reader.identity` (3) |
| `com.m1racle.reader.ui.annotation` | `com.m1racle.reader` (2)<br>`com.m1racle.reader.data.anchor` (9)<br>`com.m1racle.reader.data.local` (2)<br>`com.m1racle.reader.data.repository` (2)<br>`com.m1racle.reader.pdf` (1)<br>`com.m1racle.reader.ui.hub` (1)<br>`com.m1racle.reader.ui.material` (1)<br>`com.m1racle.reader.ui.theme` (2) |
| `com.m1racle.reader.ui.common` | 无显式跨包 import |
| `com.m1racle.reader.ui.components` | `com.m1racle.reader` (1)<br>`com.m1racle.reader.ui.material` (7) |
| `com.m1racle.reader.ui.documents` | `com.m1racle.reader.data.local` (3)<br>`com.m1racle.reader.data.repository` (3)<br>`com.m1racle.reader.data.settings` (1)<br>`com.m1racle.reader.documents` (2)<br>`com.m1racle.reader.markdown` (2)<br>`com.m1racle.reader.pdf.edit` (1)<br>`com.m1racle.reader.ui.home` (2)<br>`com.m1racle.reader.ui.viewer.components` (1) |
| `com.m1racle.reader.ui.epub` | `com.m1racle.reader.data.anchor` (1)<br>`com.m1racle.reader.data.local` (1)<br>`com.m1racle.reader.data.repository` (2)<br>`com.m1racle.reader.data.settings` (11)<br>`com.m1racle.reader.epub` (19)<br>`com.m1racle.reader.image` (3)<br>`com.m1racle.reader.ui.common` (1)<br>`com.m1racle.reader.ui.components` (1)<br>`com.m1racle.reader.ui.material` (8)<br>`com.m1racle.reader.ui.theme` (7)<br>`com.m1racle.reader.ui.viewer` (1)<br>`com.m1racle.reader.ui.viewer.components` (2) |
| `com.m1racle.reader.ui.home` | `com.m1racle.reader` (3)<br>`com.m1racle.reader.data.local` (6)<br>`com.m1racle.reader.data.repository` (3)<br>`com.m1racle.reader.markdown` (1)<br>`com.m1racle.reader.ui.hub` (2)<br>`com.m1racle.reader.ui.material` (5)<br>`com.m1racle.reader.ui.theme` (2) |
| `com.m1racle.reader.ui.hub` | `com.m1racle.reader` (5)<br>`com.m1racle.reader.data.local` (5)<br>`com.m1racle.reader.data.repository` (2)<br>`com.m1racle.reader.ui.components` (1)<br>`com.m1racle.reader.ui.home` (7)<br>`com.m1racle.reader.ui.material` (12)<br>`com.m1racle.reader.ui.theme` (5) |
| `com.m1racle.reader.ui.material` | `com.m1racle.reader` (1) |
| `com.m1racle.reader.ui.navigation` | `com.m1racle.reader` (2)<br>`com.m1racle.reader.data.anchor` (2)<br>`com.m1racle.reader.data.local` (2)<br>`com.m1racle.reader.data.repository` (3)<br>`com.m1racle.reader.data.settings` (1)<br>`com.m1racle.reader.share` (1)<br>`com.m1racle.reader.ui.home` (4)<br>`com.m1racle.reader.ui.hub` (9)<br>`com.m1racle.reader.ui.material` (3)<br>`com.m1racle.reader.ui.pdf` (2)<br>`com.m1racle.reader.ui.search` (2)<br>`com.m1racle.reader.ui.viewer` (2) |
| `com.m1racle.reader.ui.pdf` | `com.m1racle.reader` (10)<br>`com.m1racle.reader.data.anchor` (1)<br>`com.m1racle.reader.data.local` (12)<br>`com.m1racle.reader.data.repository` (1)<br>`com.m1racle.reader.pdf` (163)<br>`com.m1racle.reader.pdf.export` (12)<br>`com.m1racle.reader.ui.annotation` (4)<br>`com.m1racle.reader.ui.common` (1)<br>`com.m1racle.reader.ui.material` (31)<br>`com.m1racle.reader.ui.reader` (3)<br>`com.m1racle.reader.ui.theme` (6) |
| `com.m1racle.reader.ui.pdf.edit` | `com.m1racle.reader.data.repository` (2)<br>`com.m1racle.reader.pdf` (2)<br>`com.m1racle.reader.pdf.edit` (6)<br>`com.m1racle.reader.ui.material` (1) |
| `com.m1racle.reader.ui.reader` | `com.m1racle.reader.data.anchor` (2)<br>`com.m1racle.reader.data.local` (4)<br>`com.m1racle.reader.data.repository` (3)<br>`com.m1racle.reader.ui.annotation` (5) |
| `com.m1racle.reader.ui.search` | `com.m1racle.reader` (1)<br>`com.m1racle.reader.data.local` (1)<br>`com.m1racle.reader.data.repository` (2)<br>`com.m1racle.reader.ui.home` (1) |
| `com.m1racle.reader.ui.theme` | `com.m1racle.reader.ui.material` (4) |
| `com.m1racle.reader.ui.viewer` | `com.m1racle.reader` (3)<br>`com.m1racle.reader.data.anchor` (1)<br>`com.m1racle.reader.data.local` (1)<br>`com.m1racle.reader.data.repository` (2)<br>`com.m1racle.reader.data.settings` (4)<br>`com.m1racle.reader.markdown` (10)<br>`com.m1racle.reader.search` (2)<br>`com.m1racle.reader.ui.material` (12)<br>`com.m1racle.reader.ui.theme` (1)<br>`com.m1racle.reader.ui.viewer.components` (7) |
| `com.m1racle.reader.ui.viewer.components` | `com.m1racle.reader` (4)<br>`com.m1racle.reader.data.local` (1)<br>`com.m1racle.reader.data.settings` (13)<br>`com.m1racle.reader.highlight` (1)<br>`com.m1racle.reader.image` (2)<br>`com.m1racle.reader.markdown` (20)<br>`com.m1racle.reader.search` (16)<br>`com.m1racle.reader.ui.annotation` (1)<br>`com.m1racle.reader.ui.components` (1)<br>`com.m1racle.reader.ui.theme` (2) |
