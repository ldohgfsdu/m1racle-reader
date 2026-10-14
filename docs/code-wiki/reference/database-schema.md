# Room v17 数据库字典

[返回 Wiki](../README.md)

自动来源：[17.json](../../../app/schemas/com.m1racle.reader.data.local.AppDatabase/17.json)。共有 9 张表；字段类型/空值/主键/索引/外键以导出 JSON 为准，业务解释见数据章节。

## `documents`

主键：`id`；autoGenerate=false。

| 字段 | SQLite 类型 | NOT NULL | 默认值 |
|---|---|---|---|
| `id` | TEXT | 是 | `—` |
| `displayName` | TEXT | 是 | `—` |
| `uri` | TEXT | 是 | `—` |
| `mimeType` | TEXT | 否 | `—` |
| `fileSize` | INTEGER | 否 | `—` |
| `lastOpenedAt` | INTEGER | 是 | `—` |
| `createdAt` | INTEGER | 是 | `—` |
| `lastScrollIndex` | INTEGER | 是 | `—` |
| `lastScrollOffset` | INTEGER | 是 | `—` |
| `readingProgress` | REAL | 是 | `—` |
| `title` | TEXT | 否 | `—` |
| `headingCount` | INTEGER | 是 | `—` |
| `documentType` | TEXT | 是 | `—` |
| `pageCount` | INTEGER | 是 | `—` |
| `pdfViewportSessionVersion` | INTEGER | 否 | `—` |
| `pdfViewportReadingMode` | TEXT | 否 | `—` |
| `pdfViewportPageIndex` | INTEGER | 否 | `—` |
| `pdfPageAnchorXFraction` | REAL | 否 | `—` |
| `pdfPageAnchorYFraction` | REAL | 否 | `—` |
| `pdfViewportAnchorXFraction` | REAL | 否 | `—` |
| `pdfViewportAnchorYFraction` | REAL | 否 | `—` |
| `pdfViewportScale` | REAL | 否 | `—` |
| `pdfViewportGeneration` | INTEGER | 否 | `—` |
| `contentSha256` | TEXT | 否 | `—` |
| `sourceUri` | TEXT | 否 | `—` |
| `sourceSha256` | TEXT | 否 | `—` |
| `readingAnchor` | TEXT | 否 | `—` |

索引：

- `index_documents_contentSha256`：contentSha256；unique=true。

外键：

无导出外键。

## `pdf_print_page_anchors`

主键：`documentId`, `pdfPageIndex`；autoGenerate=false。

| 字段 | SQLite 类型 | NOT NULL | 默认值 |
|---|---|---|---|
| `documentId` | TEXT | 是 | `—` |
| `pdfPageIndex` | INTEGER | 是 | `—` |
| `printPageNumber` | INTEGER | 是 | `—` |
| `updatedAt` | INTEGER | 是 | `—` |

索引：

- `index_pdf_print_page_anchors_documentId`：documentId；unique=false。

外键：

- `documentId` → `documents(id)`；ON DELETE CASCADE；ON UPDATE NO ACTION。

## `textbook_outline_nodes`

主键：`id`；autoGenerate=false。

| 字段 | SQLite 类型 | NOT NULL | 默认值 |
|---|---|---|---|
| `id` | TEXT | 是 | `—` |
| `documentId` | TEXT | 是 | `—` |
| `parentId` | TEXT | 否 | `—` |
| `type` | TEXT | 是 | `—` |
| `title` | TEXT | 是 | `—` |
| `startPageIndex` | INTEGER | 是 | `—` |
| `endPageIndex` | INTEGER | 是 | `—` |
| `sortOrder` | INTEGER | 是 | `—` |
| `createdAt` | INTEGER | 是 | `—` |
| `updatedAt` | INTEGER | 是 | `—` |
| `source` | TEXT | 是 | `—` |
| `importKey` | TEXT | 否 | `—` |

索引：

- `index_textbook_outline_nodes_documentId`：documentId；unique=false。

- `index_textbook_outline_nodes_documentId_parentId`：documentId, parentId；unique=false。

- `index_textbook_outline_nodes_documentId_startPageIndex_endPageIndex`：documentId, startPageIndex, endPageIndex；unique=false。

- `index_textbook_outline_nodes_documentId_importKey`：documentId, importKey；unique=false。

外键：

- `documentId` → `documents(id)`；ON DELETE CASCADE；ON UPDATE NO ACTION。

## `bookmarks`

主键：`id`；autoGenerate=true。

| 字段 | SQLite 类型 | NOT NULL | 默认值 |
|---|---|---|---|
| `id` | INTEGER | 是 | `—` |
| `documentId` | TEXT | 是 | `—` |
| `locationKind` | TEXT | 是 | `—` |
| `location` | INTEGER | 是 | `—` |
| `offset` | INTEGER | 否 | `—` |
| `title` | TEXT | 否 | `—` |
| `note` | TEXT | 否 | `—` |
| `createdAt` | INTEGER | 是 | `—` |
| `anchor` | TEXT | 否 | `—` |

索引：

- `index_bookmarks_documentId`：documentId；unique=false。

外键：

- `documentId` → `documents(id)`；ON DELETE CASCADE；ON UPDATE NO ACTION。

## `favorite_documents`

主键：`documentId`；autoGenerate=false。

| 字段 | SQLite 类型 | NOT NULL | 默认值 |
|---|---|---|---|
| `documentId` | TEXT | 是 | `—` |
| `createdAt` | INTEGER | 是 | `—` |

索引：

- `index_favorite_documents_createdAt`：createdAt；unique=false。

外键：

- `documentId` → `documents(id)`；ON DELETE CASCADE；ON UPDATE NO ACTION。

## `highlights`

主键：`id`；autoGenerate=true。

| 字段 | SQLite 类型 | NOT NULL | 默认值 |
|---|---|---|---|
| `id` | INTEGER | 是 | `—` |
| `documentId` | TEXT | 是 | `—` |
| `locationKind` | TEXT | 是 | `—` |
| `location` | INTEGER | 是 | `—` |
| `anchor` | TEXT | 否 | `—` |
| `anchorStart` | INTEGER | 否 | `—` |
| `anchorEnd` | INTEGER | 否 | `—` |
| `excerpt` | TEXT | 否 | `—` |
| `createdAt` | INTEGER | 是 | `—` |
| `style` | TEXT | 是 | `'Yellow'` |

索引：

- `index_highlights_documentId`：documentId；unique=false。

- `index_highlights_documentId_locationKind_location`：documentId, locationKind, location；unique=false。

外键：

- `documentId` → `documents(id)`；ON DELETE CASCADE；ON UPDATE NO ACTION。

## `notes`

主键：`id`；autoGenerate=true。

| 字段 | SQLite 类型 | NOT NULL | 默认值 |
|---|---|---|---|
| `id` | INTEGER | 是 | `—` |
| `documentId` | TEXT | 是 | `—` |
| `locationKind` | TEXT | 是 | `—` |
| `location` | INTEGER | 是 | `—` |
| `anchor` | TEXT | 否 | `—` |
| `anchorStart` | INTEGER | 否 | `—` |
| `anchorEnd` | INTEGER | 否 | `—` |
| `content` | TEXT | 是 | `—` |
| `createdAt` | INTEGER | 是 | `—` |
| `updatedAt` | INTEGER | 是 | `—` |

索引：

- `index_notes_documentId`：documentId；unique=false。

- `index_notes_documentId_locationKind_location`：documentId, locationKind, location；unique=false。

外键：

- `documentId` → `documents(id)`；ON DELETE CASCADE；ON UPDATE NO ACTION。

## `search_index`

主键：`documentId`, `contentType`, `sourceId`；autoGenerate=false。

| 字段 | SQLite 类型 | NOT NULL | 默认值 |
|---|---|---|---|
| `documentId` | TEXT | 是 | `—` |
| `contentType` | TEXT | 是 | `—` |
| `sourceId` | TEXT | 是 | `—` |
| `content` | TEXT | 是 | `—` |
| `locationKind` | TEXT | 是 | `—` |
| `location` | INTEGER | 是 | `—` |
| `anchor` | TEXT | 是 | `—` |
| `indexedAt` | INTEGER | 是 | `—` |

索引：

- `index_search_index_documentId`：documentId；unique=false。

- `index_search_index_contentType`：contentType；unique=false。

外键：

- `documentId` → `documents(id)`；ON DELETE CASCADE；ON UPDATE NO ACTION。

## `document_aliases`

主键：`aliasKey`；autoGenerate=false。

| 字段 | SQLite 类型 | NOT NULL | 默认值 |
|---|---|---|---|
| `aliasKey` | TEXT | 是 | `—` |
| `documentId` | TEXT | 是 | `—` |
| `kind` | TEXT | 是 | `—` |
| `createdAt` | INTEGER | 是 | `—` |
| `lastSeenAt` | INTEGER | 是 | `—` |

索引：

- `index_document_aliases_documentId`：documentId；unique=false。

外键：

- `documentId` → `documents(id)`；ON DELETE CASCADE；ON UPDATE NO ACTION。

