# STATUS — 20261031-md-repro

状态：FIXED_UNCOMMITTED (2026-10-31, Asia/Shanghai)。未提交，未推送。

## 修复
- F1：新增 markdown/MarkdownAnnotationRemapper.kt（按块内容做 LCS 对齐，编辑过的块落到相邻已对齐块之间，块内区间夹紧）。DocumentRepository.saveDocumentEdits 对 MARKDOWN 在同一事务内调用 rebaseAnnotations，改写 highlights/notes 的 location 和 anchor，无 anchor 的旧行按 location 处理。编辑器提示改为“已保存，批注已按新内容重新定位”。
- F2：MarkdownAssetResolver.imageBaseUriFor(document)：MARKDOWN 且 sourceUri 为 file:// 时用原文件，否则用书库副本。fromDocument 与 ViewerScreen 都改用它。

## 测试改动（断言未放宽）
- highlightStaysOnSameParagraphAfterInsertingParagraphBefore：原写法直接解析未改动的存储行。修复改的是存储行，所以测试改为经过与 rebaseAnnotations 相同的 remap，再 project/resolve，期望值与断言不变。
- relativeImageNextToOriginalResolvesWhenReadingManagedCopy：基准从写死的副本 uri 改为 imageBaseUriFor(带 file:// sourceUri 的实体)，与 Viewer 新调用一致；断言不变。

## 结果
- repro 4/4 通过：evidence/fix-gradle.log、evidence/TEST-*.xml。
- 全量 :app:testQaUnitTest：BUILD SUCCESSFUL，total=1886 fail=0 skipped=2（evidence/full-unit-gradle.log）。

## 限制
- content:// 来源（SAF）仍无法解析相对图片，需要目录授权等另行设计。
- 重定位基于块内容；被改写的块按位置推断，块内字符区间只夹紧不重算；无 quote 字段。
- rebaseAnnotations 没有针对真实 Room 写入的单测，也未做真机验证。