# 测试源码索引

[返回 Wiki](../README.md)

生成时间：2026-11-13T13:46:11+08:00（Asia/Shanghai）。列出当前仓库 app、core、pdf-tool 与 benchmark 的测试及辅助 Kotlin 文件。

数量按去注释代码中的 `@Test` 文本标记统计，不等于参数化展开后的 case 数、执行数或通过数。benchmark 单列为隔离测量入口。

## `app/src/test`

187 个 Kotlin 文件，1309 个 `@Test` 标记。

### `com.m1racle.reader`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [DisplayRefreshRatePolicyTest.kt](../../../app/src/test/java/com/m1racle/reader/DisplayRefreshRatePolicyTest.kt) | [DisplayRefreshRatePolicyTest](../../../app/src/test/java/com/m1racle/reader/DisplayRefreshRatePolicyTest.kt#L7) | 2 |
| [MainActivityExternalIntentRestoreTest.kt](../../../app/src/test/java/com/m1racle/reader/MainActivityExternalIntentRestoreTest.kt) | [MainActivityExternalIntentRestoreTest](../../../app/src/test/java/com/m1racle/reader/MainActivityExternalIntentRestoreTest.kt#L8) | 1 |
| [NightModeConfigChangeTest.kt](../../../app/src/test/java/com/m1racle/reader/NightModeConfigChangeTest.kt) | [NightModeConfigChangeTest](../../../app/src/test/java/com/m1racle/reader/NightModeConfigChangeTest.kt#L12) | 1 |
| [R31ArchitectureReleaseContractTest.kt](../../../app/src/test/java/com/m1racle/reader/R31ArchitectureReleaseContractTest.kt) | [R31ArchitectureReleaseContractTest](../../../app/src/test/java/com/m1racle/reader/R31ArchitectureReleaseContractTest.kt#L14) | 5 |
| [R32ReleaseHardeningContractTest.kt](../../../app/src/test/java/com/m1racle/reader/R32ReleaseHardeningContractTest.kt) | [R32ReleaseHardeningContractTest](../../../app/src/test/java/com/m1racle/reader/R32ReleaseHardeningContractTest.kt#L12) | 5 |
| [R33ProductIdentityMigrationTest.kt](../../../app/src/test/java/com/m1racle/reader/R33ProductIdentityMigrationTest.kt) | [R33ProductIdentityMigrationTest](../../../app/src/test/java/com/m1racle/reader/R33ProductIdentityMigrationTest.kt#L10) | 5 |
| [R37ContentIdentityAuthorityContractTest.kt](../../../app/src/test/java/com/m1racle/reader/R37ContentIdentityAuthorityContractTest.kt) | [R37ContentIdentityAuthorityContractTest](../../../app/src/test/java/com/m1racle/reader/R37ContentIdentityAuthorityContractTest.kt#L12) | 3 |
| [R57AtomicIdentityAuthorityContractTest.kt](../../../app/src/test/java/com/m1racle/reader/R57AtomicIdentityAuthorityContractTest.kt) | [R57AtomicIdentityAuthorityContractTest](../../../app/src/test/java/com/m1racle/reader/R57AtomicIdentityAuthorityContractTest.kt#L11) | 5 |
| [ReleaseHygieneHostTest.kt](../../../app/src/test/java/com/m1racle/reader/ReleaseHygieneHostTest.kt) | [ReleaseHygieneHostTest](../../../app/src/test/java/com/m1racle/reader/ReleaseHygieneHostTest.kt#L16) | 4 |

### `com.m1racle.reader.data.anchor`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [AnchorResolverTest.kt](../../../app/src/test/java/com/m1racle/reader/data/anchor/AnchorResolverTest.kt) | [AnchorResolverTest](../../../app/src/test/java/com/m1racle/reader/data/anchor/AnchorResolverTest.kt#L16) | 6 |
| [BookmarkTargetTest.kt](../../../app/src/test/java/com/m1racle/reader/data/anchor/BookmarkTargetTest.kt) | [BookmarkTargetTest](../../../app/src/test/java/com/m1racle/reader/data/anchor/BookmarkTargetTest.kt#L13) | 3 |
| [DocumentAnchorCodecTest.kt](../../../app/src/test/java/com/m1racle/reader/data/anchor/DocumentAnchorCodecTest.kt) | [DocumentAnchorCodecTest](../../../app/src/test/java/com/m1racle/reader/data/anchor/DocumentAnchorCodecTest.kt#L14) | 5 |
| [EpubTextAnchorCodecTest.kt](../../../app/src/test/java/com/m1racle/reader/data/anchor/EpubTextAnchorCodecTest.kt) | [EpubTextAnchorCodecTest](../../../app/src/test/java/com/m1racle/reader/data/anchor/EpubTextAnchorCodecTest.kt#L14) | 5 |

### `com.m1racle.reader.data.local`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [ReadingModelAuthorityTest.kt](../../../app/src/test/java/com/m1racle/reader/data/local/ReadingModelAuthorityTest.kt) | [ReadingModelAuthorityTest](../../../app/src/test/java/com/m1racle/reader/data/local/ReadingModelAuthorityTest.kt#L18) | 4 |

### `com.m1racle.reader.data.repository`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [AnnotationAnchorRepositoryTest.kt](../../../app/src/test/java/com/m1racle/reader/data/repository/AnnotationAnchorRepositoryTest.kt) | [AnnotationAnchorRepositoryTest](../../../app/src/test/java/com/m1racle/reader/data/repository/AnnotationAnchorRepositoryTest.kt#L25) · [FakeHighlightDao](../../../app/src/test/java/com/m1racle/reader/data/repository/AnnotationAnchorRepositoryTest.kt#L27) · [FakeNoteDao](../../../app/src/test/java/com/m1racle/reader/data/repository/AnnotationAnchorRepositoryTest.kt#L54) · [NoBookmarks](../../../app/src/test/java/com/m1racle/reader/data/repository/AnnotationAnchorRepositoryTest.kt#L86) · [NoFavorites](../../../app/src/test/java/com/m1racle/reader/data/repository/AnnotationAnchorRepositoryTest.kt#L98) | 3 |
| [AppPrivateDocumentCleanupTest.kt](../../../app/src/test/java/com/m1racle/reader/data/repository/AppPrivateDocumentCleanupTest.kt) | [AppPrivateDocumentCleanupTest](../../../app/src/test/java/com/m1racle/reader/data/repository/AppPrivateDocumentCleanupTest.kt#L10) | 3 |
| [BookmarkRepositoryTest.kt](../../../app/src/test/java/com/m1racle/reader/data/repository/BookmarkRepositoryTest.kt) | [BookmarkRepositoryTest](../../../app/src/test/java/com/m1racle/reader/data/repository/BookmarkRepositoryTest.kt#L23) · [FakeBookmarkDao](../../../app/src/test/java/com/m1racle/reader/data/repository/BookmarkRepositoryTest.kt#L26) · [NoFavorites](../../../app/src/test/java/com/m1racle/reader/data/repository/BookmarkRepositoryTest.kt#L74) · [NoHighlights](../../../app/src/test/java/com/m1racle/reader/data/repository/BookmarkRepositoryTest.kt#L81) · [NoNotes](../../../app/src/test/java/com/m1racle/reader/data/repository/BookmarkRepositoryTest.kt#L89) | 4 |
| [ContentIdentityImportWiringTest.kt](../../../app/src/test/java/com/m1racle/reader/data/repository/ContentIdentityImportWiringTest.kt) | [ContentIdentityImportWiringTest](../../../app/src/test/java/com/m1racle/reader/data/repository/ContentIdentityImportWiringTest.kt#L62) · [Fixture](../../../app/src/test/java/com/m1racle/reader/data/repository/ContentIdentityImportWiringTest.kt#L446) · [IdentityDocumentDao](../../../app/src/test/java/com/m1racle/reader/data/repository/ContentIdentityImportWiringTest.kt#L607) · [UnusedPdfPrintPageAnchorDao](../../../app/src/test/java/com/m1racle/reader/data/repository/ContentIdentityImportWiringTest.kt#L741) · [UnusedTextbookOutlineNodeDao](../../../app/src/test/java/com/m1racle/reader/data/repository/ContentIdentityImportWiringTest.kt#L749) | 22 |
| [DocumentTextReaderTest.kt](../../../app/src/test/java/com/m1racle/reader/data/repository/DocumentTextReaderTest.kt) | [DocumentTextReaderTest](../../../app/src/test/java/com/m1racle/reader/data/repository/DocumentTextReaderTest.kt#L8) | 2 |
| [EpubAnchorPersistenceTest.kt](../../../app/src/test/java/com/m1racle/reader/data/repository/EpubAnchorPersistenceTest.kt) | [EpubAnchorPersistenceTest](../../../app/src/test/java/com/m1racle/reader/data/repository/EpubAnchorPersistenceTest.kt#L30) | 3 |
| [FavoriteRepositoryTest.kt](../../../app/src/test/java/com/m1racle/reader/data/repository/FavoriteRepositoryTest.kt) | [FavoriteRepositoryTest](../../../app/src/test/java/com/m1racle/reader/data/repository/FavoriteRepositoryTest.kt#L22) · [FakeFavoriteDao](../../../app/src/test/java/com/m1racle/reader/data/repository/FavoriteRepositoryTest.kt#L25) · [NoBookmarks](../../../app/src/test/java/com/m1racle/reader/data/repository/FavoriteRepositoryTest.kt#L45) · [NoHighlights](../../../app/src/test/java/com/m1racle/reader/data/repository/FavoriteRepositoryTest.kt#L55) · [NoNotes](../../../app/src/test/java/com/m1racle/reader/data/repository/FavoriteRepositoryTest.kt#L63) | 4 |
| [MarkdownAssetRebaseTransactionTest.kt](../../../app/src/test/java/com/m1racle/reader/data/repository/MarkdownAssetRebaseTransactionTest.kt) | [MarkdownAssetRebaseTransactionTest](../../../app/src/test/java/com/m1racle/reader/data/repository/MarkdownAssetRebaseTransactionTest.kt#L42) | 2 |
| [MarkdownPackageExportTest.kt](../../../app/src/test/java/com/m1racle/reader/data/repository/MarkdownPackageExportTest.kt) | [MarkdownPackageExportTest](../../../app/src/test/java/com/m1racle/reader/data/repository/MarkdownPackageExportTest.kt#L35) | 7 |
| [OutlineImportPlanBoundaryTest.kt](../../../app/src/test/java/com/m1racle/reader/data/repository/OutlineImportPlanBoundaryTest.kt) | [OutlineImportPlanBoundaryTest](../../../app/src/test/java/com/m1racle/reader/data/repository/OutlineImportPlanBoundaryTest.kt#L16) | 3 |
| [OutlineImportTransactionTest.kt](../../../app/src/test/java/com/m1racle/reader/data/repository/OutlineImportTransactionTest.kt) | [OutlineImportTransactionTest](../../../app/src/test/java/com/m1racle/reader/data/repository/OutlineImportTransactionTest.kt#L28) | 6 |
| [PackageDirCleanupPolicyTest.kt](../../../app/src/test/java/com/m1racle/reader/data/repository/PackageDirCleanupPolicyTest.kt) | [PackageDirCleanupPolicyTest](../../../app/src/test/java/com/m1racle/reader/data/repository/PackageDirCleanupPolicyTest.kt#L14) | 3 |
| [PdfProgressTest.kt](../../../app/src/test/java/com/m1racle/reader/data/repository/PdfProgressTest.kt) | [PdfProgressTest](../../../app/src/test/java/com/m1racle/reader/data/repository/PdfProgressTest.kt#L9) | 4 |
| [PdfSourceWriteStoreTest.kt](../../../app/src/test/java/com/m1racle/reader/data/repository/PdfSourceWriteStoreTest.kt) | [PdfSourceWriteStoreTest](../../../app/src/test/java/com/m1racle/reader/data/repository/PdfSourceWriteStoreTest.kt#L19) | 6 |
| [ReadingRepositoryTransactionTest.kt](../../../app/src/test/java/com/m1racle/reader/data/repository/ReadingRepositoryTransactionTest.kt) | [ReadingRepositoryTransactionTest](../../../app/src/test/java/com/m1racle/reader/data/repository/ReadingRepositoryTransactionTest.kt#L23) | 2 |
| [SearchExperienceIntegrationTest.kt](../../../app/src/test/java/com/m1racle/reader/data/repository/SearchExperienceIntegrationTest.kt) | [SearchExperienceIntegrationTest](../../../app/src/test/java/com/m1racle/reader/data/repository/SearchExperienceIntegrationTest.kt#L29) · [FakeHighlightDao](../../../app/src/test/java/com/m1racle/reader/data/repository/SearchExperienceIntegrationTest.kt#L182) · [FakeNoteDao](../../../app/src/test/java/com/m1racle/reader/data/repository/SearchExperienceIntegrationTest.kt#L198) · [NoBookmarks](../../../app/src/test/java/com/m1racle/reader/data/repository/SearchExperienceIntegrationTest.kt#L219) · [NoFavorites](../../../app/src/test/java/com/m1racle/reader/data/repository/SearchExperienceIntegrationTest.kt#L229) | 6 |
| [SearchRepositoryTest.kt](../../../app/src/test/java/com/m1racle/reader/data/repository/SearchRepositoryTest.kt) | [SearchRepositoryTest](../../../app/src/test/java/com/m1racle/reader/data/repository/SearchRepositoryTest.kt#L21) · [FakeSearchIndexDao](../../../app/src/test/java/com/m1racle/reader/data/repository/SearchRepositoryTest.kt#L23) · [Key](../../../app/src/test/java/com/m1racle/reader/data/repository/SearchRepositoryTest.kt#L24) | 6 |

### `com.m1racle.reader.data.settings`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [ReaderLineHeightMigrationTest.kt](../../../app/src/test/java/com/m1racle/reader/data/settings/ReaderLineHeightMigrationTest.kt) | [ReaderLineHeightMigrationTest](../../../app/src/test/java/com/m1racle/reader/data/settings/ReaderLineHeightMigrationTest.kt#L8) | 2 |

### `com.m1racle.reader.documents`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [DocumentOutputSaveTest.kt](../../../app/src/test/java/com/m1racle/reader/documents/DocumentOutputSaveTest.kt) | [DocumentOutputSaveTest](../../../app/src/test/java/com/m1racle/reader/documents/DocumentOutputSaveTest.kt#L17) | 9 |
| [DocumentToolsTest.kt](../../../app/src/test/java/com/m1racle/reader/documents/DocumentToolsTest.kt) | [DocumentToolsTest](../../../app/src/test/java/com/m1racle/reader/documents/DocumentToolsTest.kt#L26) | 10 |
| [MarkdownDraftStoreTest.kt](../../../app/src/test/java/com/m1racle/reader/documents/MarkdownDraftStoreTest.kt) | [MarkdownDraftStoreTest](../../../app/src/test/java/com/m1racle/reader/documents/MarkdownDraftStoreTest.kt#L15) · [AndroidAtomicRenameShadow](../../../app/src/test/java/com/m1racle/reader/documents/MarkdownDraftStoreTest.kt#L51) | 3 |
| [MarkdownEditorDraftRecoveryTest.kt](../../../app/src/test/java/com/m1racle/reader/documents/MarkdownEditorDraftRecoveryTest.kt) | [MarkdownEditorDraftRecoveryTest](../../../app/src/test/java/com/m1racle/reader/documents/MarkdownEditorDraftRecoveryTest.kt#L37) | 2 |
| [MarkdownEditorOriginTest.kt](../../../app/src/test/java/com/m1racle/reader/documents/MarkdownEditorOriginTest.kt) | [MarkdownEditorOriginTest](../../../app/src/test/java/com/m1racle/reader/documents/MarkdownEditorOriginTest.kt#L7) | 3 |

### `com.m1racle.reader.epub`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [EpubLocatorRollbackTest.kt](../../../app/src/test/java/com/m1racle/reader/epub/EpubLocatorRollbackTest.kt) | [EpubLocatorRollbackTest](../../../app/src/test/java/com/m1racle/reader/epub/EpubLocatorRollbackTest.kt#L15) | 7 |
| [EpubPackageImporterTest.kt](../../../app/src/test/java/com/m1racle/reader/epub/EpubPackageImporterTest.kt) | [EpubPackageImporterTest](../../../app/src/test/java/com/m1racle/reader/epub/EpubPackageImporterTest.kt#L11) · [NoXIncludeFactory](../../../app/src/test/java/com/m1racle/reader/epub/EpubPackageImporterTest.kt#L116) | 17 |
| [EpubReadingAnchorsTest.kt](../../../app/src/test/java/com/m1racle/reader/epub/EpubReadingAnchorsTest.kt) | [EpubReadingAnchorsTest](../../../app/src/test/java/com/m1racle/reader/epub/EpubReadingAnchorsTest.kt#L18) | 12 |
| [EpubRestoreSessionTest.kt](../../../app/src/test/java/com/m1racle/reader/epub/EpubRestoreSessionTest.kt) | [EpubRestoreSessionTest](../../../app/src/test/java/com/m1racle/reader/epub/EpubRestoreSessionTest.kt#L7) | 4 |
| [EpubTocSafetyTest.kt](../../../app/src/test/java/com/m1racle/reader/epub/EpubTocSafetyTest.kt) | [EpubTocSafetyTest](../../../app/src/test/java/com/m1racle/reader/epub/EpubTocSafetyTest.kt#L9) | 4 |
| [EpubXhtmlBoundaryTest.kt](../../../app/src/test/java/com/m1racle/reader/epub/EpubXhtmlBoundaryTest.kt) | [EpubXhtmlBoundaryTest](../../../app/src/test/java/com/m1racle/reader/epub/EpubXhtmlBoundaryTest.kt#L6) | 8 |

### `com.m1racle.reader.identity`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [ContentIdentityResolverTest.kt](../../../app/src/test/java/com/m1racle/reader/identity/ContentIdentityResolverTest.kt) | [ContentIdentityResolverTest](../../../app/src/test/java/com/m1racle/reader/identity/ContentIdentityResolverTest.kt#L37) · [ResolverDocumentDao](../../../app/src/test/java/com/m1racle/reader/identity/ContentIdentityResolverTest.kt#L266) · [FailingInputStream](../../../app/src/test/java/com/m1racle/reader/identity/ContentIdentityResolverTest.kt#L363) · [BlockingInterruptibleInputStream](../../../app/src/test/java/com/m1racle/reader/identity/ContentIdentityResolverTest.kt#L377) · [GeneratedByteInputStream](../../../app/src/test/java/com/m1racle/reader/identity/ContentIdentityResolverTest.kt#L391) | 7 |
| [LegacyContentHashMigrationV1Test.kt](../../../app/src/test/java/com/m1racle/reader/identity/LegacyContentHashMigrationV1Test.kt) | [LegacyContentHashMigrationV1Test](../../../app/src/test/java/com/m1racle/reader/identity/LegacyContentHashMigrationV1Test.kt#L34) · [LegacyHashDocumentDao](../../../app/src/test/java/com/m1racle/reader/identity/LegacyContentHashMigrationV1Test.kt#L201) · [ThrowingOnceDocumentDao](../../../app/src/test/java/com/m1racle/reader/identity/LegacyContentHashMigrationV1Test.kt#L299) · [LegacyHashDocumentAliasDao](../../../app/src/test/java/com/m1racle/reader/identity/LegacyContentHashMigrationV1Test.kt#L313) | 6 |
| [SidecarIdentityMigrationV1Test.kt](../../../app/src/test/java/com/m1racle/reader/identity/SidecarIdentityMigrationV1Test.kt) | [SidecarIdentityMigrationV1Test](../../../app/src/test/java/com/m1racle/reader/identity/SidecarIdentityMigrationV1Test.kt#L26) · [FakeStore](../../../app/src/test/java/com/m1racle/reader/identity/SidecarIdentityMigrationV1Test.kt#L328) · [FakeRoomTransaction](../../../app/src/test/java/com/m1racle/reader/identity/SidecarIdentityMigrationV1Test.kt#L344) | 14 |

### `com.m1racle.reader.image`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [LocalImageSampleTest.kt](../../../app/src/test/java/com/m1racle/reader/image/LocalImageSampleTest.kt) | [LocalImageSampleTest](../../../app/src/test/java/com/m1racle/reader/image/LocalImageSampleTest.kt#L13) | 2 |
| [RelativeImageResolveTest.kt](../../../app/src/test/java/com/m1racle/reader/image/RelativeImageResolveTest.kt) | [RelativeImageResolveTest](../../../app/src/test/java/com/m1racle/reader/image/RelativeImageResolveTest.kt#L14) | 4 |

### `com.m1racle.reader.markdown`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [AnchorRegistryIsolationTest.kt](../../../app/src/test/java/com/m1racle/reader/markdown/AnchorRegistryIsolationTest.kt) | [AnchorRegistryIsolationTest](../../../app/src/test/java/com/m1racle/reader/markdown/AnchorRegistryIsolationTest.kt#L13) | 2 |
| [DocumentListTitleTest.kt](../../../app/src/test/java/com/m1racle/reader/markdown/DocumentListTitleTest.kt) | [DocumentListTitleTest](../../../app/src/test/java/com/m1racle/reader/markdown/DocumentListTitleTest.kt#L8) | 16 |
| [HtmlCommentStripTest.kt](../../../app/src/test/java/com/m1racle/reader/markdown/HtmlCommentStripTest.kt) | [HtmlCommentStripTest](../../../app/src/test/java/com/m1racle/reader/markdown/HtmlCommentStripTest.kt#L8) | 2 |
| [HtmlEmptyAnchorTest.kt](../../../app/src/test/java/com/m1racle/reader/markdown/HtmlEmptyAnchorTest.kt) | [HtmlEmptyAnchorTest](../../../app/src/test/java/com/m1racle/reader/markdown/HtmlEmptyAnchorTest.kt#L10) | 11 |
| [InlineMarkdownParserPerformanceTest.kt](../../../app/src/test/java/com/m1racle/reader/markdown/InlineMarkdownParserPerformanceTest.kt) | [InlineMarkdownParserPerformanceTest](../../../app/src/test/java/com/m1racle/reader/markdown/InlineMarkdownParserPerformanceTest.kt#L19) | 5 |
| [LaoziAnchorFixtureTest.kt](../../../app/src/test/java/com/m1racle/reader/markdown/LaoziAnchorFixtureTest.kt) | [LaoziAnchorFixtureTest](../../../app/src/test/java/com/m1racle/reader/markdown/LaoziAnchorFixtureTest.kt#L14) | 1 |
| [MarkdownAnchorRegistryTest.kt](../../../app/src/test/java/com/m1racle/reader/markdown/MarkdownAnchorRegistryTest.kt) | [MarkdownAnchorRegistryTest](../../../app/src/test/java/com/m1racle/reader/markdown/MarkdownAnchorRegistryTest.kt#L8) | 4 |
| [MarkdownAnnotationRemapperTest.kt](../../../app/src/test/java/com/m1racle/reader/markdown/MarkdownAnnotationRemapperTest.kt) | [MarkdownAnnotationRemapperTest](../../../app/src/test/java/com/m1racle/reader/markdown/MarkdownAnnotationRemapperTest.kt#L9) | 7 |
| [MarkdownAssetResolverSecurityTest.kt](../../../app/src/test/java/com/m1racle/reader/markdown/MarkdownAssetResolverSecurityTest.kt) | [MarkdownAssetResolverSecurityTest](../../../app/src/test/java/com/m1racle/reader/markdown/MarkdownAssetResolverSecurityTest.kt#L18) | 21 |
| [MarkdownAssetResolverTest.kt](../../../app/src/test/java/com/m1racle/reader/markdown/MarkdownAssetResolverTest.kt) | [MarkdownAssetResolverTest](../../../app/src/test/java/com/m1racle/reader/markdown/MarkdownAssetResolverTest.kt#L16) | 22 |
| [MarkdownAuditFixesRegressionTest.kt](../../../app/src/test/java/com/m1racle/reader/markdown/MarkdownAuditFixesRegressionTest.kt) | [MarkdownAuditFixesRegressionTest](../../../app/src/test/java/com/m1racle/reader/markdown/MarkdownAuditFixesRegressionTest.kt#L7) | 9 |
| [MarkdownBomTest.kt](../../../app/src/test/java/com/m1racle/reader/markdown/MarkdownBomTest.kt) | [MarkdownBomTest](../../../app/src/test/java/com/m1racle/reader/markdown/MarkdownBomTest.kt#L7) | 3 |
| [MarkdownEdgeCaseHostTest.kt](../../../app/src/test/java/com/m1racle/reader/markdown/MarkdownEdgeCaseHostTest.kt) | [MarkdownEdgeCaseHostTest](../../../app/src/test/java/com/m1racle/reader/markdown/MarkdownEdgeCaseHostTest.kt#L15) | 12 |
| [MarkdownInlinePositionTest.kt](../../../app/src/test/java/com/m1racle/reader/markdown/MarkdownInlinePositionTest.kt) | [MarkdownInlinePositionTest](../../../app/src/test/java/com/m1racle/reader/markdown/MarkdownInlinePositionTest.kt#L6) | 2 |
| [MarkdownSourceTest.kt](../../../app/src/test/java/com/m1racle/reader/markdown/MarkdownSourceTest.kt) | [MarkdownSourceTest](../../../app/src/test/java/com/m1racle/reader/markdown/MarkdownSourceTest.kt#L6) | 3 |
| [TocTitleAndInPageLinkTest.kt](../../../app/src/test/java/com/m1racle/reader/markdown/TocTitleAndInPageLinkTest.kt) | [TocTitleAndInPageLinkTest](../../../app/src/test/java/com/m1racle/reader/markdown/TocTitleAndInPageLinkTest.kt#L13) | 14 |

### `com.m1racle.reader.packageimport`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [PackageImageIntegrationTest.kt](../../../app/src/test/java/com/m1racle/reader/packageimport/PackageImageIntegrationTest.kt) | [PackageImageIntegrationTest](../../../app/src/test/java/com/m1racle/reader/packageimport/PackageImageIntegrationTest.kt#L21) | 3 |
| [ZipPackageImporterTest.kt](../../../app/src/test/java/com/m1racle/reader/packageimport/ZipPackageImporterTest.kt) | [ZipPackageImporterTest](../../../app/src/test/java/com/m1racle/reader/packageimport/ZipPackageImporterTest.kt#L18) | 14 |

### `com.m1racle.reader.pdf`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [AdvancedGeometryTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/AdvancedGeometryTest.kt) | [AdvancedGeometryTest](../../../app/src/test/java/com/m1racle/reader/pdf/AdvancedGeometryTest.kt#L14) | 3 |
| [ContinuousTileIndexTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/ContinuousTileIndexTest.kt) | [ContinuousTileIndexTest](../../../app/src/test/java/com/m1racle/reader/pdf/ContinuousTileIndexTest.kt#L19) | 5 |
| [ContinuousViewportHotPathContractTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/ContinuousViewportHotPathContractTest.kt) | [ContinuousViewportHotPathContractTest](../../../app/src/test/java/com/m1racle/reader/pdf/ContinuousViewportHotPathContractTest.kt#L9) | 6 |
| [FinalPdfProgressFlusherTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/FinalPdfProgressFlusherTest.kt) | [FinalPdfProgressFlusherTest](../../../app/src/test/java/com/m1racle/reader/pdf/FinalPdfProgressFlusherTest.kt#L40) · [FlusherHarness](../../../app/src/test/java/com/m1racle/reader/pdf/FinalPdfProgressFlusherTest.kt#L89) | 18 |
| [MotionContinuityGeometryTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/MotionContinuityGeometryTest.kt) | [MotionContinuityGeometryTest](../../../app/src/test/java/com/m1racle/reader/pdf/MotionContinuityGeometryTest.kt#L28) | 9 |
| [PdfEmbeddedOutlineCycleTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/PdfEmbeddedOutlineCycleTest.kt) | [PdfEmbeddedOutlineCycleTest](../../../app/src/test/java/com/m1racle/reader/pdf/PdfEmbeddedOutlineCycleTest.kt#L37) | 3 |
| [PdfImmersiveSystemBarsTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/PdfImmersiveSystemBarsTest.kt) | [PdfImmersiveSystemBarsTest](../../../app/src/test/java/com/m1racle/reader/pdf/PdfImmersiveSystemBarsTest.kt#L9) | 3 |
| [PdfReadingModeTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/PdfReadingModeTest.kt) | [PdfReadingModeTest](../../../app/src/test/java/com/m1racle/reader/pdf/PdfReadingModeTest.kt#L11) | 9 |
| [PdfTextSelectionTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/PdfTextSelectionTest.kt) | [PdfTextSelectionTest](../../../app/src/test/java/com/m1racle/reader/pdf/PdfTextSelectionTest.kt#L24) | 15 |
| [PdfThumbnailCachePolicyTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/PdfThumbnailCachePolicyTest.kt) | [PdfThumbnailCachePolicyTest](../../../app/src/test/java/com/m1racle/reader/pdf/PdfThumbnailCachePolicyTest.kt#L10) | 2 |
| [PdfViewportSessionStateTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/PdfViewportSessionStateTest.kt) | [PdfViewportSessionStateTest](../../../app/src/test/java/com/m1racle/reader/pdf/PdfViewportSessionStateTest.kt#L10) | 9 |
| [R71FrameStabilityTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/R71FrameStabilityTest.kt) | [R71FrameStabilityTest](../../../app/src/test/java/com/m1racle/reader/pdf/R71FrameStabilityTest.kt#L12) | 10 |
| [R7ContinuousViewportTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/R7ContinuousViewportTest.kt) | [R7ContinuousViewportTest](../../../app/src/test/java/com/m1racle/reader/pdf/R7ContinuousViewportTest.kt#L14) | 8 |
| [R7H11bHorizontalSharedPhysicsTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/R7H11bHorizontalSharedPhysicsTest.kt) | [R7H11bHorizontalSharedPhysicsTest](../../../app/src/test/java/com/m1racle/reader/pdf/R7H11bHorizontalSharedPhysicsTest.kt#L9) | 6 |
| [R7H14UnifiedGestureArchitectureTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/R7H14UnifiedGestureArchitectureTest.kt) | [R7H14UnifiedGestureArchitectureTest](../../../app/src/test/java/com/m1racle/reader/pdf/R7H14UnifiedGestureArchitectureTest.kt#L14) | 5 |
| [R7H15PlatformScrollAndPageStripTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/R7H15PlatformScrollAndPageStripTest.kt) | [R7H15PlatformScrollAndPageStripTest](../../../app/src/test/java/com/m1racle/reader/pdf/R7H15PlatformScrollAndPageStripTest.kt#L16) | 10 |
| [R7H15ThreePageStripTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/R7H15ThreePageStripTest.kt) | [R7H15ThreePageStripTest](../../../app/src/test/java/com/m1racle/reader/pdf/R7H15ThreePageStripTest.kt#L10) | 16 |
| [TextbookOutlineResolverTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/TextbookOutlineResolverTest.kt) | [TextbookOutlineResolverTest](../../../app/src/test/java/com/m1racle/reader/pdf/TextbookOutlineResolverTest.kt#L11) | 9 |

### `com.m1racle.reader.pdf.edit`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [PdfBodyEditorTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/edit/PdfBodyEditorTest.kt) | [PdfBodyEditorTest](../../../app/src/test/java/com/m1racle/reader/pdf/edit/PdfBodyEditorTest.kt#L23) | 11 |

### `com.m1racle.reader.pdf.export`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [ChapterPackageCleanupTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/export/ChapterPackageCleanupTest.kt) | [ChapterPackageCleanupTest](../../../app/src/test/java/com/m1racle/reader/pdf/export/ChapterPackageCleanupTest.kt#L9) | 2 |
| [ChapterPackageP04aAcceptanceTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/export/ChapterPackageP04aAcceptanceTest.kt) | [ChapterPackageP04aAcceptanceTest](../../../app/src/test/java/com/m1racle/reader/pdf/export/ChapterPackageP04aAcceptanceTest.kt#L22) | 7 |
| [ExportCancellationIoTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/export/ExportCancellationIoTest.kt) | [ExportCancellationIoTest](../../../app/src/test/java/com/m1racle/reader/pdf/export/ExportCancellationIoTest.kt#L21) · [CancellingAfterReads](../../../app/src/test/java/com/m1racle/reader/pdf/export/ExportCancellationIoTest.kt#L23) | 6 |
| [ExportCancellationOverlapTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/export/ExportCancellationOverlapTest.kt) | [ExportCancellationOverlapTest](../../../app/src/test/java/com/m1racle/reader/pdf/export/ExportCancellationOverlapTest.kt#L30) | 3 |
| [ExportRangeResolverTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/export/ExportRangeResolverTest.kt) | [ExportRangeResolverTest](../../../app/src/test/java/com/m1racle/reader/pdf/export/ExportRangeResolverTest.kt#L10) | 10 |
| [ExportSafCancellationCleanupTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/export/ExportSafCancellationCleanupTest.kt) | [ExportSafCancellationCleanupTest](../../../app/src/test/java/com/m1racle/reader/pdf/export/ExportSafCancellationCleanupTest.kt#L46) · [StubDocumentsProvider](../../../app/src/test/java/com/m1racle/reader/pdf/export/ExportSafCancellationCleanupTest.kt#L53) · [CancelMidStream](../../../app/src/test/java/com/m1racle/reader/pdf/export/ExportSafCancellationCleanupTest.kt#L185) | 12 |
| [PdfImportedPageLinksTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/export/PdfImportedPageLinksTest.kt) | [PdfImportedPageLinksTest](../../../app/src/test/java/com/m1racle/reader/pdf/export/PdfImportedPageLinksTest.kt#L41) | 2 |
| [PdfSplitExporterTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/export/PdfSplitExporterTest.kt) | [PdfSplitExporterTest](../../../app/src/test/java/com/m1racle/reader/pdf/export/PdfSplitExporterTest.kt#L45) · [FileTarget](../../../app/src/test/java/com/m1racle/reader/pdf/export/PdfSplitExporterTest.kt#L47) · [ScriptedTarget](../../../app/src/test/java/com/m1racle/reader/pdf/export/PdfSplitExporterTest.kt#L507) | 15 |
| [PdfSplitPlannerTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/export/PdfSplitPlannerTest.kt) | [PdfSplitPlannerTest](../../../app/src/test/java/com/m1racle/reader/pdf/export/PdfSplitPlannerTest.kt#L8) | 10 |

### `com.m1racle.reader.repro`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [MarkdownAnnotationDriftAfterEditReproTest.kt](../../../app/src/test/java/com/m1racle/reader/repro/MarkdownAnnotationDriftAfterEditReproTest.kt) | [MarkdownAnnotationDriftAfterEditReproTest](../../../app/src/test/java/com/m1racle/reader/repro/MarkdownAnnotationDriftAfterEditReproTest.kt#L18) | 2 |
| [OutlineExportRangeReproTest.kt](../../../app/src/test/java/com/m1racle/reader/repro/OutlineExportRangeReproTest.kt) | [OutlineExportRangeReproTest](../../../app/src/test/java/com/m1racle/reader/repro/OutlineExportRangeReproTest.kt#L10) | 4 |
| [StandaloneMarkdownRelativeImageReproTest.kt](../../../app/src/test/java/com/m1racle/reader/repro/StandaloneMarkdownRelativeImageReproTest.kt) | [StandaloneMarkdownRelativeImageReproTest](../../../app/src/test/java/com/m1racle/reader/repro/StandaloneMarkdownRelativeImageReproTest.kt#L27) | 2 |

### `com.m1racle.reader.search`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [DocumentSearchIsolationTest.kt](../../../app/src/test/java/com/m1racle/reader/search/DocumentSearchIsolationTest.kt) | [DocumentSearchIsolationTest](../../../app/src/test/java/com/m1racle/reader/search/DocumentSearchIsolationTest.kt#L18) | 11 |
| [DocumentSearchTest.kt](../../../app/src/test/java/com/m1racle/reader/search/DocumentSearchTest.kt) | [DocumentSearchTest](../../../app/src/test/java/com/m1racle/reader/search/DocumentSearchTest.kt#L11) | 20 |
| [DocumentSearchUnicodeOffsetTest.kt](../../../app/src/test/java/com/m1racle/reader/search/DocumentSearchUnicodeOffsetTest.kt) | [DocumentSearchUnicodeOffsetTest](../../../app/src/test/java/com/m1racle/reader/search/DocumentSearchUnicodeOffsetTest.kt#L17) | 15 |
| [MarkdownSearchTextExtractorTest.kt](../../../app/src/test/java/com/m1racle/reader/search/MarkdownSearchTextExtractorTest.kt) | [MarkdownSearchTextExtractorTest](../../../app/src/test/java/com/m1racle/reader/search/MarkdownSearchTextExtractorTest.kt#L11) | 13 |
| [SearchHighlightTest.kt](../../../app/src/test/java/com/m1racle/reader/search/SearchHighlightTest.kt) | [SearchHighlightTest](../../../app/src/test/java/com/m1racle/reader/search/SearchHighlightTest.kt#L13) | 21 |
| [WholeBlockHighlightRegressionTest.kt](../../../app/src/test/java/com/m1racle/reader/search/WholeBlockHighlightRegressionTest.kt) | [WholeBlockHighlightRegressionTest](../../../app/src/test/java/com/m1racle/reader/search/WholeBlockHighlightRegressionTest.kt#L19) | 5 |

### `com.m1racle.reader.share`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [ExternalDocumentImporterSanitizeGoldenTest.kt](../../../app/src/test/java/com/m1racle/reader/share/ExternalDocumentImporterSanitizeGoldenTest.kt) | [ExternalDocumentImporterSanitizeGoldenTest](../../../app/src/test/java/com/m1racle/reader/share/ExternalDocumentImporterSanitizeGoldenTest.kt#L17) | 10 |
| [ExternalIntentManifestTest.kt](../../../app/src/test/java/com/m1racle/reader/share/ExternalIntentManifestTest.kt) | [ExternalIntentManifestTest](../../../app/src/test/java/com/m1racle/reader/share/ExternalIntentManifestTest.kt#L9) | 1 |
| [ExternalIntentUriTest.kt](../../../app/src/test/java/com/m1racle/reader/share/ExternalIntentUriTest.kt) | [ExternalIntentUriTest](../../../app/src/test/java/com/m1racle/reader/share/ExternalIntentUriTest.kt#L12) | 2 |
| [ExternalOpenAtomicSessionTest.kt](../../../app/src/test/java/com/m1racle/reader/share/ExternalOpenAtomicSessionTest.kt) | [ExternalOpenAtomicSessionTest](../../../app/src/test/java/com/m1racle/reader/share/ExternalOpenAtomicSessionTest.kt#L13) | 19 |
| [ExternalOpenPendingNavigationTest.kt](../../../app/src/test/java/com/m1racle/reader/share/ExternalOpenPendingNavigationTest.kt) | [ExternalOpenPendingNavigationTest](../../../app/src/test/java/com/m1racle/reader/share/ExternalOpenPendingNavigationTest.kt#L59) · [FakeImporter](../../../app/src/test/java/com/m1racle/reader/share/ExternalOpenPendingNavigationTest.kt#L468) · [FakeDocumentDao](../../../app/src/test/java/com/m1racle/reader/share/ExternalOpenPendingNavigationTest.kt#L508) · [FakePdfPrintPageAnchorDao](../../../app/src/test/java/com/m1racle/reader/share/ExternalOpenPendingNavigationTest.kt#L639) · [FakeTextbookOutlineNodeDao](../../../app/src/test/java/com/m1racle/reader/share/ExternalOpenPendingNavigationTest.kt#L654) | 14 |
| [ExternalOpenPolicyTest.kt](../../../app/src/test/java/com/m1racle/reader/share/ExternalOpenPolicyTest.kt) | [ExternalOpenPolicyTest](../../../app/src/test/java/com/m1racle/reader/share/ExternalOpenPolicyTest.kt#L11) | 11 |
| [ExternalOpenSessionRulesTest.kt](../../../app/src/test/java/com/m1racle/reader/share/ExternalOpenSessionRulesTest.kt) | [ExternalOpenSessionRulesTest](../../../app/src/test/java/com/m1racle/reader/share/ExternalOpenSessionRulesTest.kt#L8) | 8 |
| [ExternalUriMetadataTest.kt](../../../app/src/test/java/com/m1racle/reader/share/ExternalUriMetadataTest.kt) | [ExternalUriMetadataTest](../../../app/src/test/java/com/m1racle/reader/share/ExternalUriMetadataTest.kt#L8) | 13 |
| [ImportAcceptanceParityGoldenTest.kt](../../../app/src/test/java/com/m1racle/reader/share/ImportAcceptanceParityGoldenTest.kt) | [ImportAcceptanceParityGoldenTest](../../../app/src/test/java/com/m1racle/reader/share/ImportAcceptanceParityGoldenTest.kt#L78) · [StubContentProvider](../../../app/src/test/java/com/m1racle/reader/share/ImportAcceptanceParityGoldenTest.kt#L322) · [RecordingDocumentDao](../../../app/src/test/java/com/m1racle/reader/share/ImportAcceptanceParityGoldenTest.kt#L471) · [UnusedPdfPrintPageAnchorDao](../../../app/src/test/java/com/m1racle/reader/share/ImportAcceptanceParityGoldenTest.kt#L590) · [UnusedTextbookOutlineNodeDao](../../../app/src/test/java/com/m1racle/reader/share/ImportAcceptanceParityGoldenTest.kt#L604) | 13 |
| [ImportSizeLimitBoundaryTest.kt](../../../app/src/test/java/com/m1racle/reader/share/ImportSizeLimitBoundaryTest.kt) | [ImportSizeLimitBoundaryTest](../../../app/src/test/java/com/m1racle/reader/share/ImportSizeLimitBoundaryTest.kt#L63) · [FakeSizedStream](../../../app/src/test/java/com/m1racle/reader/share/ImportSizeLimitBoundaryTest.kt#L83) · [NullStream](../../../app/src/test/java/com/m1racle/reader/share/ImportSizeLimitBoundaryTest.kt#L94) · [StubContentProvider](../../../app/src/test/java/com/m1racle/reader/share/ImportSizeLimitBoundaryTest.kt#L99) · [B3RecordingDocumentDao](../../../app/src/test/java/com/m1racle/reader/share/ImportSizeLimitBoundaryTest.kt#L319) · [B3UnusedPdfPrintPageAnchorDao](../../../app/src/test/java/com/m1racle/reader/share/ImportSizeLimitBoundaryTest.kt#L430) · [B3UnusedTextbookOutlineNodeDao](../../../app/src/test/java/com/m1racle/reader/share/ImportSizeLimitBoundaryTest.kt#L444) | 11 |
| [ProviderMetadataSourceMatrixTest.kt](../../../app/src/test/java/com/m1racle/reader/share/ProviderMetadataSourceMatrixTest.kt) | [ProviderMetadataSourceMatrixTest](../../../app/src/test/java/com/m1racle/reader/share/ProviderMetadataSourceMatrixTest.kt#L39) · [StubProvider](../../../app/src/test/java/com/m1racle/reader/share/ProviderMetadataSourceMatrixTest.kt#L45) | 9 |
| [ZipExternalOpenContractTest.kt](../../../app/src/test/java/com/m1racle/reader/share/ZipExternalOpenContractTest.kt) | [ZipExternalOpenContractTest](../../../app/src/test/java/com/m1racle/reader/share/ZipExternalOpenContractTest.kt#L64) · [StubContentProvider](../../../app/src/test/java/com/m1racle/reader/share/ZipExternalOpenContractTest.kt#L91) · [ZbRecordingDocumentDao](../../../app/src/test/java/com/m1racle/reader/share/ZipExternalOpenContractTest.kt#L280) · [ZbUnusedPdfPrintPageAnchorDao](../../../app/src/test/java/com/m1racle/reader/share/ZipExternalOpenContractTest.kt#L391) · [ZbUnusedTextbookOutlineNodeDao](../../../app/src/test/java/com/m1racle/reader/share/ZipExternalOpenContractTest.kt#L405) | 6 |

### `com.m1racle.reader.testing`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [ImportTestFakes.kt](../../../app/src/test/java/com/m1racle/reader/testing/ImportTestFakes.kt) | [TestDocumentAliasDao](../../../app/src/test/java/com/m1racle/reader/testing/ImportTestFakes.kt#L8) · [ImmediateRepositoryTransactionRunner](../../../app/src/test/java/com/m1racle/reader/testing/ImportTestFakes.kt#L52) | 0 |
| [ProductionKotlinRoots.kt](../../../app/src/test/java/com/m1racle/reader/testing/ProductionKotlinRoots.kt) | [ProductionKotlinRoots](../../../app/src/test/java/com/m1racle/reader/testing/ProductionKotlinRoots.kt#L9) | 0 |
| [ProductionSource.kt](../../../app/src/test/java/com/m1racle/reader/testing/ProductionSource.kt) | [ProductionSource](../../../app/src/test/java/com/m1racle/reader/testing/ProductionSource.kt#L16) | 0 |
| [TestSearchIndexDao.kt](../../../app/src/test/java/com/m1racle/reader/testing/TestSearchIndexDao.kt) | [TestSearchIndexDao](../../../app/src/test/java/com/m1racle/reader/testing/TestSearchIndexDao.kt#L10) · [Key](../../../app/src/test/java/com/m1racle/reader/testing/TestSearchIndexDao.kt#L11) | 0 |

### `com.m1racle.reader.ui.annotation`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [AnnotationCreationFlowTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/annotation/AnnotationCreationFlowTest.kt) | [AnnotationCreationFlowTest](../../../app/src/test/java/com/m1racle/reader/ui/annotation/AnnotationCreationFlowTest.kt#L26) · [FakeHighlightDao](../../../app/src/test/java/com/m1racle/reader/ui/annotation/AnnotationCreationFlowTest.kt#L28) · [FakeNoteDao](../../../app/src/test/java/com/m1racle/reader/ui/annotation/AnnotationCreationFlowTest.kt#L54) · [NoBookmarks](../../../app/src/test/java/com/m1racle/reader/ui/annotation/AnnotationCreationFlowTest.kt#L85) · [NoFavorites](../../../app/src/test/java/com/m1racle/reader/ui/annotation/AnnotationCreationFlowTest.kt#L97) | 5 |
| [AnnotationOverlayContractTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/annotation/AnnotationOverlayContractTest.kt) | [AnnotationOverlayContractTest](../../../app/src/test/java/com/m1racle/reader/ui/annotation/AnnotationOverlayContractTest.kt#L28) · [FakeHighlightDao](../../../app/src/test/java/com/m1racle/reader/ui/annotation/AnnotationOverlayContractTest.kt#L30) · [FakeNoteDao](../../../app/src/test/java/com/m1racle/reader/ui/annotation/AnnotationOverlayContractTest.kt#L50) · [NoBookmarks](../../../app/src/test/java/com/m1racle/reader/ui/annotation/AnnotationOverlayContractTest.kt#L73) · [NoFavorites](../../../app/src/test/java/com/m1racle/reader/ui/annotation/AnnotationOverlayContractTest.kt#L85) | 2 |
| [AnnotationPerformanceContractTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/annotation/AnnotationPerformanceContractTest.kt) | [AnnotationPerformanceContractTest](../../../app/src/test/java/com/m1racle/reader/ui/annotation/AnnotationPerformanceContractTest.kt#L26) · [CountingHighlightDao](../../../app/src/test/java/com/m1racle/reader/ui/annotation/AnnotationPerformanceContractTest.kt#L28) · [CountingNoteDao](../../../app/src/test/java/com/m1racle/reader/ui/annotation/AnnotationPerformanceContractTest.kt#L53) · [NoBookmarks](../../../app/src/test/java/com/m1racle/reader/ui/annotation/AnnotationPerformanceContractTest.kt#L185) · [NoFavorites](../../../app/src/test/java/com/m1racle/reader/ui/annotation/AnnotationPerformanceContractTest.kt#L196) | 3 |
| [PdfSelectionActionScaleTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/annotation/PdfSelectionActionScaleTest.kt) | [PdfSelectionActionScaleTest](../../../app/src/test/java/com/m1racle/reader/ui/annotation/PdfSelectionActionScaleTest.kt#L18) | 4 |
| [PdfSelectionLoupeGeometryTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/annotation/PdfSelectionLoupeGeometryTest.kt) | [PdfSelectionLoupeGeometryTest](../../../app/src/test/java/com/m1racle/reader/ui/annotation/PdfSelectionLoupeGeometryTest.kt#L10) | 6 |
| [PdfTextSelectionGeometryTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/annotation/PdfTextSelectionGeometryTest.kt) | [PdfTextSelectionGeometryTest](../../../app/src/test/java/com/m1racle/reader/ui/annotation/PdfTextSelectionGeometryTest.kt#L15) | 15 |
| [PdfTextSelectionSessionTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/annotation/PdfTextSelectionSessionTest.kt) | [PdfTextSelectionSessionTest](../../../app/src/test/java/com/m1racle/reader/ui/annotation/PdfTextSelectionSessionTest.kt#L10) | 7 |
| [SelectionAnchorConversionTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/annotation/SelectionAnchorConversionTest.kt) | [SelectionAnchorConversionTest](../../../app/src/test/java/com/m1racle/reader/ui/annotation/SelectionAnchorConversionTest.kt#L15) | 5 |

### `com.m1racle.reader.ui.documents`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [MarkdownEditorSnapshotTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/documents/MarkdownEditorSnapshotTest.kt) | [MarkdownEditorSnapshotTest](../../../app/src/test/java/com/m1racle/reader/ui/documents/MarkdownEditorSnapshotTest.kt#L20) | 4 |

### `com.m1racle.reader.ui.epub`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [EpubCurlPageCommitTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/epub/EpubCurlPageCommitTest.kt) | [EpubCurlPageCommitTest](../../../app/src/test/java/com/m1racle/reader/ui/epub/EpubCurlPageCommitTest.kt#L7) | 7 |
| [EpubPaginationTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/epub/EpubPaginationTest.kt) | [EpubPaginationTest](../../../app/src/test/java/com/m1racle/reader/ui/epub/EpubPaginationTest.kt#L7) | 19 |
| [EpubRowIndexTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/epub/EpubRowIndexTest.kt) | [EpubRowIndexTest](../../../app/src/test/java/com/m1racle/reader/ui/epub/EpubRowIndexTest.kt#L26) | 2 |
| [EpubTextLayoutTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/epub/EpubTextLayoutTest.kt) | [EpubTextLayoutTest](../../../app/src/test/java/com/m1racle/reader/ui/epub/EpubTextLayoutTest.kt#L9) | 8 |

### `com.m1racle.reader.ui.home`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [HomeImportEventTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/home/HomeImportEventTest.kt) | [HomeImportEventTest](../../../app/src/test/java/com/m1racle/reader/ui/home/HomeImportEventTest.kt#L10) | 2 |
| [HomeLayoutContractTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/home/HomeLayoutContractTest.kt) | [HomeLayoutContractTest](../../../app/src/test/java/com/m1racle/reader/ui/home/HomeLayoutContractTest.kt#L13) | 12 |
| [HomeListUpdateTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/home/HomeListUpdateTest.kt) | [HomeListUpdateTest](../../../app/src/test/java/com/m1racle/reader/ui/home/HomeListUpdateTest.kt#L12) | 6 |
| [HomeOperationsTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/home/HomeOperationsTest.kt) | [HomeOperationsTest](../../../app/src/test/java/com/m1racle/reader/ui/home/HomeOperationsTest.kt#L42) | 2 |
| [HomeViewModelTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/home/HomeViewModelTest.kt) | [HomeViewModelTest](../../../app/src/test/java/com/m1racle/reader/ui/home/HomeViewModelTest.kt#L14) | 13 |

### `com.m1racle.reader.ui.hub`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [BookmarkScreenTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/hub/BookmarkScreenTest.kt) | [BookmarkScreenTest](../../../app/src/test/java/com/m1racle/reader/ui/hub/BookmarkScreenTest.kt#L32) · [FakeBookmarkDao](../../../app/src/test/java/com/m1racle/reader/ui/hub/BookmarkScreenTest.kt#L34) · [NoHighlights](../../../app/src/test/java/com/m1racle/reader/ui/hub/BookmarkScreenTest.kt#L58) · [NoNotes](../../../app/src/test/java/com/m1racle/reader/ui/hub/BookmarkScreenTest.kt#L66) · [NoFavorites](../../../app/src/test/java/com/m1racle/reader/ui/hub/BookmarkScreenTest.kt#L75) | 4 |
| [DockMotionTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/hub/DockMotionTest.kt) | [DockMotionTest](../../../app/src/test/java/com/m1racle/reader/ui/hub/DockMotionTest.kt#L6) | 6 |
| [HubTabTransitionsTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/hub/HubTabTransitionsTest.kt) | [HubTabTransitionsTest](../../../app/src/test/java/com/m1racle/reader/ui/hub/HubTabTransitionsTest.kt#L10) | 3 |

### `com.m1racle.reader.ui.material`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [FrostShadowRendererTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/material/FrostShadowRendererTest.kt) | [FrostShadowRendererTest](../../../app/src/test/java/com/m1racle/reader/ui/material/FrostShadowRendererTest.kt#L13) | 1 |
| [GlassAuthorityContractTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/material/GlassAuthorityContractTest.kt) | [GlassAuthorityContractTest](../../../app/src/test/java/com/m1racle/reader/ui/material/GlassAuthorityContractTest.kt#L9) | 7 |
| [GlassBackdropTopologyContractTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/material/GlassBackdropTopologyContractTest.kt) | [GlassBackdropTopologyContractTest](../../../app/src/test/java/com/m1racle/reader/ui/material/GlassBackdropTopologyContractTest.kt#L12) · [TrailingLambda](../../../app/src/test/java/com/m1racle/reader/ui/material/GlassBackdropTopologyContractTest.kt#L182) | 11 |
| [GlassInteractionContractTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/material/GlassInteractionContractTest.kt) | [GlassInteractionContractTest](../../../app/src/test/java/com/m1racle/reader/ui/material/GlassInteractionContractTest.kt#L17) | 13 |
| [GlassMaterialRegressionTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/material/GlassMaterialRegressionTest.kt) | [GlassMaterialRegressionTest](../../../app/src/test/java/com/m1racle/reader/ui/material/GlassMaterialRegressionTest.kt#L27) | 13 |
| [GlassOpticalPolicyTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/material/GlassOpticalPolicyTest.kt) | [GlassOpticalPolicyTest](../../../app/src/test/java/com/m1racle/reader/ui/material/GlassOpticalPolicyTest.kt#L8) | 5 |
| [LiquidGlassStyleTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/material/LiquidGlassStyleTest.kt) | [LiquidGlassStyleTest](../../../app/src/test/java/com/m1racle/reader/ui/material/LiquidGlassStyleTest.kt#L8) | 7 |
| [QaGlassVariantTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/material/QaGlassVariantTest.kt) | [QaGlassVariantTest](../../../app/src/test/java/com/m1racle/reader/ui/material/QaGlassVariantTest.kt#L13) | 11 |
| [ReaderBackButtonTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/material/ReaderBackButtonTest.kt) | [ReaderBackButtonTest](../../../app/src/test/java/com/m1racle/reader/ui/material/ReaderBackButtonTest.kt#L8) | 1 |

### `com.m1racle.reader.ui.navigation`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [NavigationStateTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/navigation/NavigationStateTest.kt) | [NavigationStateTest](../../../app/src/test/java/com/m1racle/reader/ui/navigation/NavigationStateTest.kt#L14) | 5 |

### `com.m1racle.reader.ui.pdf`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [AdvancedChildLayoutSpecTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/pdf/AdvancedChildLayoutSpecTest.kt) | [AdvancedChildLayoutSpecTest](../../../app/src/test/java/com/m1racle/reader/ui/pdf/AdvancedChildLayoutSpecTest.kt#L13) | 6 |
| [AdvancedOriginFidelityTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/pdf/AdvancedOriginFidelityTest.kt) | [AdvancedOriginFidelityTest](../../../app/src/test/java/com/m1racle/reader/ui/pdf/AdvancedOriginFidelityTest.kt#L18) | 6 |
| [CapsuleInteractionContractTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/pdf/CapsuleInteractionContractTest.kt) | [CapsuleInteractionContractTest](../../../app/src/test/java/com/m1racle/reader/ui/pdf/CapsuleInteractionContractTest.kt#L18) | 13 |
| [CapsuleMorphModelContractTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/pdf/CapsuleMorphModelContractTest.kt) | [CapsuleMorphModelContractTest](../../../app/src/test/java/com/m1racle/reader/ui/pdf/CapsuleMorphModelContractTest.kt#L21) · [Frozen](../../../app/src/test/java/com/m1racle/reader/ui/pdf/CapsuleMorphModelContractTest.kt#L24) | 7 |
| [CapsuleMorphSpringInterruptTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/pdf/CapsuleMorphSpringInterruptTest.kt) | [CapsuleMorphSpringInterruptTest](../../../app/src/test/java/com/m1racle/reader/ui/pdf/CapsuleMorphSpringInterruptTest.kt#L20) · [Seam](../../../app/src/test/java/com/m1racle/reader/ui/pdf/CapsuleMorphSpringInterruptTest.kt#L94) | 4 |
| [DisplayedBitmapRetentionPolicyTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/pdf/DisplayedBitmapRetentionPolicyTest.kt) | [DisplayedBitmapRetentionPolicyTest](../../../app/src/test/java/com/m1racle/reader/ui/pdf/DisplayedBitmapRetentionPolicyTest.kt#L18) | 11 |
| [DropdownCaretContractTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/pdf/DropdownCaretContractTest.kt) | [DropdownCaretContractTest](../../../app/src/test/java/com/m1racle/reader/ui/pdf/DropdownCaretContractTest.kt#L8) | 1 |
| [ExportStateGateTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/pdf/ExportStateGateTest.kt) | [ExportStateGateTest](../../../app/src/test/java/com/m1racle/reader/ui/pdf/ExportStateGateTest.kt#L16) | 4 |
| [PdfEntryRestoreWiringTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/pdf/PdfEntryRestoreWiringTest.kt) | [PdfEntryRestoreWiringTest](../../../app/src/test/java/com/m1racle/reader/ui/pdf/PdfEntryRestoreWiringTest.kt#L16) | 3 |
| [R25ReaderFoundationTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/pdf/R25ReaderFoundationTest.kt) | [R25ReaderFoundationTest](../../../app/src/test/java/com/m1racle/reader/ui/pdf/R25ReaderFoundationTest.kt#L15) | 9 |
| [ReaderChromeMaterialTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/pdf/ReaderChromeMaterialTest.kt) | [ReaderChromeMaterialTest](../../../app/src/test/java/com/m1racle/reader/ui/pdf/ReaderChromeMaterialTest.kt#L19) | 11 |
| [ReaderChromeTokensContractTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/pdf/ReaderChromeTokensContractTest.kt) | [ReaderChromeTokensContractTest](../../../app/src/test/java/com/m1racle/reader/ui/pdf/ReaderChromeTokensContractTest.kt#L14) | 10 |
| [ReaderMoreStateMachineTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/pdf/ReaderMoreStateMachineTest.kt) | [ReaderMoreStateMachineTest](../../../app/src/test/java/com/m1racle/reader/ui/pdf/ReaderMoreStateMachineTest.kt#L12) | 14 |
| [ReaderMoreTransitionsTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/pdf/ReaderMoreTransitionsTest.kt) | [ReaderMoreTransitionsTest](../../../app/src/test/java/com/m1racle/reader/ui/pdf/ReaderMoreTransitionsTest.kt#L15) | 7 |
| [ViewModelClearOrderEvidenceTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/pdf/ViewModelClearOrderEvidenceTest.kt) | [ViewModelClearOrderEvidenceTest](../../../app/src/test/java/com/m1racle/reader/ui/pdf/ViewModelClearOrderEvidenceTest.kt#L62) | 5 |

### `com.m1racle.reader.ui.reader`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [AnnotationManagementTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/reader/AnnotationManagementTest.kt) | [AnnotationManagementTest](../../../app/src/test/java/com/m1racle/reader/ui/reader/AnnotationManagementTest.kt#L30) · [FakeHighlightDao](../../../app/src/test/java/com/m1racle/reader/ui/reader/AnnotationManagementTest.kt#L32) · [FakeNoteDao](../../../app/src/test/java/com/m1racle/reader/ui/reader/AnnotationManagementTest.kt#L60) · [NoBookmarks](../../../app/src/test/java/com/m1racle/reader/ui/reader/AnnotationManagementTest.kt#L76) · [NoFavorites](../../../app/src/test/java/com/m1racle/reader/ui/reader/AnnotationManagementTest.kt#L88) | 4 |
| [NoteFoundationAuthorityTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/reader/NoteFoundationAuthorityTest.kt) | [NoteFoundationAuthorityTest](../../../app/src/test/java/com/m1racle/reader/ui/reader/NoteFoundationAuthorityTest.kt#L9) | 2 |
| [PdfHighlightLoopTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/reader/PdfHighlightLoopTest.kt) | [PdfHighlightLoopTest](../../../app/src/test/java/com/m1racle/reader/ui/reader/PdfHighlightLoopTest.kt#L34) · [FakeHighlightDao](../../../app/src/test/java/com/m1racle/reader/ui/reader/PdfHighlightLoopTest.kt#L36) · [FakeNoteDao](../../../app/src/test/java/com/m1racle/reader/ui/reader/PdfHighlightLoopTest.kt#L62) · [NoBookmarks](../../../app/src/test/java/com/m1racle/reader/ui/reader/PdfHighlightLoopTest.kt#L80) · [NoFavorites](../../../app/src/test/java/com/m1racle/reader/ui/reader/PdfHighlightLoopTest.kt#L92) | 5 |
| [ReaderBookmarkControllerTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/reader/ReaderBookmarkControllerTest.kt) | [ReaderBookmarkControllerTest](../../../app/src/test/java/com/m1racle/reader/ui/reader/ReaderBookmarkControllerTest.kt#L34) · [FakeBookmarkDao](../../../app/src/test/java/com/m1racle/reader/ui/reader/ReaderBookmarkControllerTest.kt#L37) · [NoFavorites](../../../app/src/test/java/com/m1racle/reader/ui/reader/ReaderBookmarkControllerTest.kt#L89) · [NoHighlights](../../../app/src/test/java/com/m1racle/reader/ui/reader/ReaderBookmarkControllerTest.kt#L99) · [NoNotes](../../../app/src/test/java/com/m1racle/reader/ui/reader/ReaderBookmarkControllerTest.kt#L112) · [SerialTransactions](../../../app/src/test/java/com/m1racle/reader/ui/reader/ReaderBookmarkControllerTest.kt#L139) | 7 |
| [ReaderMarkdownAnnotationTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/reader/ReaderMarkdownAnnotationTest.kt) | [ReaderMarkdownAnnotationTest](../../../app/src/test/java/com/m1racle/reader/ui/reader/ReaderMarkdownAnnotationTest.kt#L31) · [FakeHighlightDao](../../../app/src/test/java/com/m1racle/reader/ui/reader/ReaderMarkdownAnnotationTest.kt#L33) · [FakeNoteDao](../../../app/src/test/java/com/m1racle/reader/ui/reader/ReaderMarkdownAnnotationTest.kt#L62) · [NoBookmarks](../../../app/src/test/java/com/m1racle/reader/ui/reader/ReaderMarkdownAnnotationTest.kt#L101) · [NoFavorites](../../../app/src/test/java/com/m1racle/reader/ui/reader/ReaderMarkdownAnnotationTest.kt#L117) | 6 |
| [ReaderNoteControllerTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/reader/ReaderNoteControllerTest.kt) | [ReaderNoteControllerTest](../../../app/src/test/java/com/m1racle/reader/ui/reader/ReaderNoteControllerTest.kt#L33) · [FakeHighlightDao](../../../app/src/test/java/com/m1racle/reader/ui/reader/ReaderNoteControllerTest.kt#L35) · [FakeNoteDao](../../../app/src/test/java/com/m1racle/reader/ui/reader/ReaderNoteControllerTest.kt#L62) · [NoBookmarks](../../../app/src/test/java/com/m1racle/reader/ui/reader/ReaderNoteControllerTest.kt#L96) · [NoFavorites](../../../app/src/test/java/com/m1racle/reader/ui/reader/ReaderNoteControllerTest.kt#L108) · [Harness](../../../app/src/test/java/com/m1racle/reader/ui/reader/ReaderNoteControllerTest.kt#L117) | 6 |
| [ReaderProgressWheelTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/reader/ReaderProgressWheelTest.kt) | [ReaderProgressWheelTest](../../../app/src/test/java/com/m1racle/reader/ui/reader/ReaderProgressWheelTest.kt#L8) | 1 |
| [ReadingAssetsAuthorityTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/reader/ReadingAssetsAuthorityTest.kt) | [ReadingAssetsAuthorityTest](../../../app/src/test/java/com/m1racle/reader/ui/reader/ReadingAssetsAuthorityTest.kt#L12) | 3 |

### `com.m1racle.reader.ui.theme`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [M1racleMotionContractTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/theme/M1racleMotionContractTest.kt) | [M1racleMotionContractTest](../../../app/src/test/java/com/m1racle/reader/ui/theme/M1racleMotionContractTest.kt#L10) | 5 |
| [M1racleTokensContractTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/theme/M1racleTokensContractTest.kt) | [M1racleTokensContractTest](../../../app/src/test/java/com/m1racle/reader/ui/theme/M1racleTokensContractTest.kt#L9) | 7 |

### `com.m1racle.reader.ui.viewer`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [AnchorJumpTraceTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/viewer/AnchorJumpTraceTest.kt) | [AnchorJumpTraceTest](../../../app/src/test/java/com/m1racle/reader/ui/viewer/AnchorJumpTraceTest.kt#L18) | 2 |
| [DocumentSearchUiStateTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/viewer/DocumentSearchUiStateTest.kt) | [DocumentSearchUiStateTest](../../../app/src/test/java/com/m1racle/reader/ui/viewer/DocumentSearchUiStateTest.kt#L13) | 5 |
| [MarkdownAnchorNavigationTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/viewer/MarkdownAnchorNavigationTest.kt) | [MarkdownAnchorNavigationTest](../../../app/src/test/java/com/m1racle/reader/ui/viewer/MarkdownAnchorNavigationTest.kt#L6) | 1 |
| [ReaderTopBarScrollStateTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/viewer/ReaderTopBarScrollStateTest.kt) | [ReaderTopBarScrollStateTest](../../../app/src/test/java/com/m1racle/reader/ui/viewer/ReaderTopBarScrollStateTest.kt#L15) | 27 |
| [ReadingPositionSaverTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/viewer/ReadingPositionSaverTest.kt) | [ReadingPositionSaverTest](../../../app/src/test/java/com/m1racle/reader/ui/viewer/ReadingPositionSaverTest.kt#L22) · [Harness](../../../app/src/test/java/com/m1racle/reader/ui/viewer/ReadingPositionSaverTest.kt#L24) | 9 |

### `com.m1racle.reader.ui.viewer.components`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [HeadingAnchorPlacementTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/viewer/components/HeadingAnchorPlacementTest.kt) | [HeadingAnchorPlacementTest](../../../app/src/test/java/com/m1racle/reader/ui/viewer/components/HeadingAnchorPlacementTest.kt#L7) | 1 |
| [MarkdownSelectionContextTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/viewer/components/MarkdownSelectionContextTest.kt) | [MarkdownSelectionContextTest](../../../app/src/test/java/com/m1racle/reader/ui/viewer/components/MarkdownSelectionContextTest.kt#L8) | 5 |
| [MarkdownTableLayoutEngineTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/viewer/components/MarkdownTableLayoutEngineTest.kt) | [MarkdownTableLayoutEngineTest](../../../app/src/test/java/com/m1racle/reader/ui/viewer/components/MarkdownTableLayoutEngineTest.kt#L17) | 34 |
| [ReaderPositionCaptionTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/viewer/components/ReaderPositionCaptionTest.kt) | [ReaderPositionCaptionTest](../../../app/src/test/java/com/m1racle/reader/ui/viewer/components/ReaderPositionCaptionTest.kt#L6) | 3 |
| [TableHeaderBodyAlignmentTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/viewer/components/TableHeaderBodyAlignmentTest.kt) | [TableHeaderBodyAlignmentTest](../../../app/src/test/java/com/m1racle/reader/ui/viewer/components/TableHeaderBodyAlignmentTest.kt#L17) | 12 |
| [TableHeaderParityTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/viewer/components/TableHeaderParityTest.kt) | [TableHeaderParityTest](../../../app/src/test/java/com/m1racle/reader/ui/viewer/components/TableHeaderParityTest.kt#L21) | 3 |
| [TableLayoutCacheTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/viewer/components/TableLayoutCacheTest.kt) | [TableLayoutCacheTest](../../../app/src/test/java/com/m1racle/reader/ui/viewer/components/TableLayoutCacheTest.kt#L9) | 2 |

## `app/src/testQa`

33 个 Kotlin 文件，186 个 `@Test` 标记。

### `com.m1racle.reader.ui.annotation`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [PdfSelectionReferenceRenderTest.kt](../../../app/src/testQa/java/com/m1racle/reader/ui/annotation/PdfSelectionReferenceRenderTest.kt) | [PdfSelectionReferenceRenderTest](../../../app/src/testQa/java/com/m1racle/reader/ui/annotation/PdfSelectionReferenceRenderTest.kt#L50) · [RenderedHandle](../../../app/src/testQa/java/com/m1racle/reader/ui/annotation/PdfSelectionReferenceRenderTest.kt#L53) · [SelectionCapture](../../../app/src/testQa/java/com/m1racle/reader/ui/annotation/PdfSelectionReferenceRenderTest.kt#L59) | 1 |
| [PdfTextHighlightGestureTest.kt](../../../app/src/testQa/java/com/m1racle/reader/ui/annotation/PdfTextHighlightGestureTest.kt) | [PdfTextHighlightGestureTest](../../../app/src/testQa/java/com/m1racle/reader/ui/annotation/PdfTextHighlightGestureTest.kt#L38) · [StubImageBitmap](../../../app/src/testQa/java/com/m1racle/reader/ui/annotation/PdfTextHighlightGestureTest.kt#L641) | 28 |

### `com.m1racle.reader.ui.components`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [PillSegmentedControlTest.kt](../../../app/src/testQa/java/com/m1racle/reader/ui/components/PillSegmentedControlTest.kt) | [PillSegmentedControlTest](../../../app/src/testQa/java/com/m1racle/reader/ui/components/PillSegmentedControlTest.kt#L45) | 11 |

### `com.m1racle.reader.ui.epub`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [EpubAnchorReflowHostTest.kt](../../../app/src/testQa/java/com/m1racle/reader/ui/epub/EpubAnchorReflowHostTest.kt) | [EpubAnchorReflowHostTest](../../../app/src/testQa/java/com/m1racle/reader/ui/epub/EpubAnchorReflowHostTest.kt#L59) · [Typography](../../../app/src/testQa/java/com/m1racle/reader/ui/epub/EpubAnchorReflowHostTest.kt#L80) | 6 |
| [EpubFontSettingsHostTest.kt](../../../app/src/testQa/java/com/m1racle/reader/ui/epub/EpubFontSettingsHostTest.kt) | [EpubFontSettingsHostTest](../../../app/src/testQa/java/com/m1racle/reader/ui/epub/EpubFontSettingsHostTest.kt#L23) | 1 |
| [EpubImmersiveHostTest.kt](../../../app/src/testQa/java/com/m1racle/reader/ui/epub/EpubImmersiveHostTest.kt) | [EpubImmersiveHostTest](../../../app/src/testQa/java/com/m1racle/reader/ui/epub/EpubImmersiveHostTest.kt#L43) | 3 |
| [EpubPagerOffsetReadHostTest.kt](../../../app/src/testQa/java/com/m1racle/reader/ui/epub/EpubPagerOffsetReadHostTest.kt) | [EpubPagerOffsetReadHostTest](../../../app/src/testQa/java/com/m1racle/reader/ui/epub/EpubPagerOffsetReadHostTest.kt#L36) | 2 |
| [EpubPaginationHostTest.kt](../../../app/src/testQa/java/com/m1racle/reader/ui/epub/EpubPaginationHostTest.kt) | [EpubPaginationHostTest](../../../app/src/testQa/java/com/m1racle/reader/ui/epub/EpubPaginationHostTest.kt#L39) | 11 |
| [EpubReaderUiTest.kt](../../../app/src/testQa/java/com/m1racle/reader/ui/epub/EpubReaderUiTest.kt) | [EpubReaderUiTest](../../../app/src/testQa/java/com/m1racle/reader/ui/epub/EpubReaderUiTest.kt#L30) | 11 |
| [EpubReferenceFidelityHostTest.kt](../../../app/src/testQa/java/com/m1racle/reader/ui/epub/EpubReferenceFidelityHostTest.kt) | [EpubReferenceFidelityHostTest](../../../app/src/testQa/java/com/m1racle/reader/ui/epub/EpubReferenceFidelityHostTest.kt#L41) | 12 |
| [EpubReleaseHandoffHostTest.kt](../../../app/src/testQa/java/com/m1racle/reader/ui/epub/EpubReleaseHandoffHostTest.kt) | [EpubReleaseHandoffHostTest](../../../app/src/testQa/java/com/m1racle/reader/ui/epub/EpubReleaseHandoffHostTest.kt#L35) | 3 |

### `com.m1racle.reader.ui.home`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [HomeDocumentActionsUiTest.kt](../../../app/src/testQa/java/com/m1racle/reader/ui/home/HomeDocumentActionsUiTest.kt) | [HomeDocumentActionsUiTest](../../../app/src/testQa/java/com/m1racle/reader/ui/home/HomeDocumentActionsUiTest.kt#L27) | 3 |
| [HomeReturnInsetsHostTest.kt](../../../app/src/testQa/java/com/m1racle/reader/ui/home/HomeReturnInsetsHostTest.kt) | [HomeReturnInsetsHostTest](../../../app/src/testQa/java/com/m1racle/reader/ui/home/HomeReturnInsetsHostTest.kt#L23) | 1 |
| [HomeViewportUiTest.kt](../../../app/src/testQa/java/com/m1racle/reader/ui/home/HomeViewportUiTest.kt) | [HomeViewportUiTest](../../../app/src/testQa/java/com/m1racle/reader/ui/home/HomeViewportUiTest.kt#L34) | 6 |

### `com.m1racle.reader.ui.hub`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [BackgroundPositionSliderTest.kt](../../../app/src/testQa/java/com/m1racle/reader/ui/hub/BackgroundPositionSliderTest.kt) | [BackgroundPositionSliderTest](../../../app/src/testQa/java/com/m1racle/reader/ui/hub/BackgroundPositionSliderTest.kt#L25) | 3 |
| [DockAccentTest.kt](../../../app/src/testQa/java/com/m1racle/reader/ui/hub/DockAccentTest.kt) | [DockAccentTest](../../../app/src/testQa/java/com/m1racle/reader/ui/hub/DockAccentTest.kt#L11) | 2 |
| [FloatingDockSurfaceTest.kt](../../../app/src/testQa/java/com/m1racle/reader/ui/hub/FloatingDockSurfaceTest.kt) | [FloatingDockSurfaceTest](../../../app/src/testQa/java/com/m1racle/reader/ui/hub/FloatingDockSurfaceTest.kt#L32) | 1 |
| [GlassDockUiTest.kt](../../../app/src/testQa/java/com/m1racle/reader/ui/hub/GlassDockUiTest.kt) | [GlassDockUiTest](../../../app/src/testQa/java/com/m1racle/reader/ui/hub/GlassDockUiTest.kt#L46) | 22 |
| [HubSettingsScreenTest.kt](../../../app/src/testQa/java/com/m1racle/reader/ui/hub/HubSettingsScreenTest.kt) | [HubSettingsScreenTest](../../../app/src/testQa/java/com/m1racle/reader/ui/hub/HubSettingsScreenTest.kt#L21) | 2 |
| [SettingsBrandHeaderTest.kt](../../../app/src/testQa/java/com/m1racle/reader/ui/hub/SettingsBrandHeaderTest.kt) | [SettingsBrandHeaderTest](../../../app/src/testQa/java/com/m1racle/reader/ui/hub/SettingsBrandHeaderTest.kt#L49) · [Brand](../../../app/src/testQa/java/com/m1racle/reader/ui/hub/SettingsBrandHeaderTest.kt#L290) · [NameText](../../../app/src/testQa/java/com/m1racle/reader/ui/hub/SettingsBrandHeaderTest.kt#L292) · [VisibleTextSnapshot](../../../app/src/testQa/java/com/m1racle/reader/ui/hub/SettingsBrandHeaderTest.kt#L297) | 5 |

### `com.m1racle.reader.ui.material`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [GlassThemeScopeTest.kt](../../../app/src/testQa/java/com/m1racle/reader/ui/material/GlassThemeScopeTest.kt) | [GlassThemeScopeTest](../../../app/src/testQa/java/com/m1racle/reader/ui/material/GlassThemeScopeTest.kt#L20) | 1 |

### `com.m1racle.reader.ui.navigation`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [ReaderReturnHostTest.kt](../../../app/src/testQa/java/com/m1racle/reader/ui/navigation/ReaderReturnHostTest.kt) | [ReaderReturnHostTest](../../../app/src/testQa/java/com/m1racle/reader/ui/navigation/ReaderReturnHostTest.kt#L49) | 3 |

### `com.m1racle.reader.ui.pdf`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [PdfChromeVisibilityTest.kt](../../../app/src/testQa/java/com/m1racle/reader/ui/pdf/PdfChromeVisibilityTest.kt) | [PdfChromeVisibilityTest](../../../app/src/testQa/java/com/m1racle/reader/ui/pdf/PdfChromeVisibilityTest.kt#L34) | 2 |
| [PdfMenuAccessibilityTest.kt](../../../app/src/testQa/java/com/m1racle/reader/ui/pdf/PdfMenuAccessibilityTest.kt) | [PdfMenuAccessibilityTest](../../../app/src/testQa/java/com/m1racle/reader/ui/pdf/PdfMenuAccessibilityTest.kt#L30) | 3 |
| [PdfMenuElasticPressTest.kt](../../../app/src/testQa/java/com/m1racle/reader/ui/pdf/PdfMenuElasticPressTest.kt) | [PdfMenuElasticPressTest](../../../app/src/testQa/java/com/m1racle/reader/ui/pdf/PdfMenuElasticPressTest.kt#L30) | 7 |
| [PdfMenuHighlightTest.kt](../../../app/src/testQa/java/com/m1racle/reader/ui/pdf/PdfMenuHighlightTest.kt) | [PdfMenuHighlightTest](../../../app/src/testQa/java/com/m1racle/reader/ui/pdf/PdfMenuHighlightTest.kt#L37) | 3 |
| [PdfSplitProgressDialogTest.kt](../../../app/src/testQa/java/com/m1racle/reader/ui/pdf/PdfSplitProgressDialogTest.kt) | [PdfSplitProgressDialogTest](../../../app/src/testQa/java/com/m1racle/reader/ui/pdf/PdfSplitProgressDialogTest.kt#L21) | 4 |
| [ReaderMoreHostUiTest.kt](../../../app/src/testQa/java/com/m1racle/reader/ui/pdf/ReaderMoreHostUiTest.kt) | [ReaderMoreHostUiTest](../../../app/src/testQa/java/com/m1racle/reader/ui/pdf/ReaderMoreHostUiTest.kt#L56) | 19 |

### `com.m1racle.reader.ui.pdf.edit`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [PdfEditorSaveOptionsTest.kt](../../../app/src/testQa/java/com/m1racle/reader/ui/pdf/edit/PdfEditorSaveOptionsTest.kt) | [PdfEditorSaveOptionsTest](../../../app/src/testQa/java/com/m1racle/reader/ui/pdf/edit/PdfEditorSaveOptionsTest.kt#L21) | 2 |

### `com.m1racle.reader.ui.theme`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [RadixUiRuntimeTest.kt](../../../app/src/testQa/java/com/m1racle/reader/ui/theme/RadixUiRuntimeTest.kt) | [RadixUiRuntimeTest](../../../app/src/testQa/java/com/m1racle/reader/ui/theme/RadixUiRuntimeTest.kt#L35) | 2 |
| [RadixUiScreenshotsTest.kt](../../../app/src/testQa/java/com/m1racle/reader/ui/theme/RadixUiScreenshotsTest.kt) | [RadixUiScreenshotsTest](../../../app/src/testQa/java/com/m1racle/reader/ui/theme/RadixUiScreenshotsTest.kt#L53) | 2 |

### `com.m1racle.reader.ui.viewer`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [ReaderFixedGradientUiTest.kt](../../../app/src/testQa/java/com/m1racle/reader/ui/viewer/ReaderFixedGradientUiTest.kt) | [ReaderFixedGradientUiTest](../../../app/src/testQa/java/com/m1racle/reader/ui/viewer/ReaderFixedGradientUiTest.kt#L33) | 2 |
| [ReaderScrollEdgeRenderTest.kt](../../../app/src/testQa/java/com/m1racle/reader/ui/viewer/ReaderScrollEdgeRenderTest.kt) | [ReaderScrollEdgeRenderTest](../../../app/src/testQa/java/com/m1racle/reader/ui/viewer/ReaderScrollEdgeRenderTest.kt#L38) | 2 |

## `app/src/testRelease`

1 个 Kotlin 文件，1 个 `@Test` 标记。

### `com.m1racle.reader`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [MaterialReleasePolicyTest.kt](../../../app/src/testRelease/java/com/m1racle/reader/MaterialReleasePolicyTest.kt) | [MaterialReleasePolicyTest](../../../app/src/testRelease/java/com/m1racle/reader/MaterialReleasePolicyTest.kt#L10) | 1 |

## `app/src/androidTest`

39 个 Kotlin 文件，180 个 `@Test` 标记。

### `com.m1racle.reader`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [QaDataIsolationInstrumentedTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/QaDataIsolationInstrumentedTest.kt) | [QaDataIsolationInstrumentedTest](../../../app/src/androidTest/java/com/m1racle/reader/QaDataIsolationInstrumentedTest.kt#L14) | 1 |

### `com.m1racle.reader.data.local`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [MigrationV5ToV6Test.kt](../../../app/src/androidTest/java/com/m1racle/reader/data/local/MigrationV5ToV6Test.kt) | [MigrationV5ToV6Test](../../../app/src/androidTest/java/com/m1racle/reader/data/local/MigrationV5ToV6Test.kt#L43) | 1 |
| [RoomUpgradeTo17InstrumentedTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/data/local/RoomUpgradeTo17InstrumentedTest.kt) | [RoomUpgradeTo17InstrumentedTest](../../../app/src/androidTest/java/com/m1racle/reader/data/local/RoomUpgradeTo17InstrumentedTest.kt#L17) | 1 |

### `com.m1racle.reader.data.repository`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [BookmarkToggleAtomicInstrumentedTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/data/repository/BookmarkToggleAtomicInstrumentedTest.kt) | [BookmarkToggleAtomicInstrumentedTest](../../../app/src/androidTest/java/com/m1racle/reader/data/repository/BookmarkToggleAtomicInstrumentedTest.kt#L24) | 1 |
| [DeleteByIdTransactionTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/data/repository/DeleteByIdTransactionTest.kt) | [DeleteByIdTransactionTest](../../../app/src/androidTest/java/com/m1racle/reader/data/repository/DeleteByIdTransactionTest.kt#L38) · [FailingOutlineDao](../../../app/src/androidTest/java/com/m1racle/reader/data/repository/DeleteByIdTransactionTest.kt#L107) · [FailingAnchorDao](../../../app/src/androidTest/java/com/m1racle/reader/data/repository/DeleteByIdTransactionTest.kt#L127) · [FailingDocumentDao](../../../app/src/androidTest/java/com/m1racle/reader/data/repository/DeleteByIdTransactionTest.kt#L143) | 5 |
| [UpdatePdfProgressSnapshotAtomicTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/data/repository/UpdatePdfProgressSnapshotAtomicTest.kt) | [UpdatePdfProgressSnapshotAtomicTest](../../../app/src/androidTest/java/com/m1racle/reader/data/repository/UpdatePdfProgressSnapshotAtomicTest.kt#L35) · [FailingViewportSessionDao](../../../app/src/androidTest/java/com/m1racle/reader/data/repository/UpdatePdfProgressSnapshotAtomicTest.kt#L333) · [FailingPositionDao](../../../app/src/androidTest/java/com/m1racle/reader/data/repository/UpdatePdfProgressSnapshotAtomicTest.kt#L356) | 6 |

### `com.m1racle.reader.pdf`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [PdfLargeFileSplitInstrumentedTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/pdf/PdfLargeFileSplitInstrumentedTest.kt) | [PdfLargeFileSplitInstrumentedTest](../../../app/src/androidTest/java/com/m1racle/reader/pdf/PdfLargeFileSplitInstrumentedTest.kt#L68) · [FileSplitOutputTarget](../../../app/src/androidTest/java/com/m1racle/reader/pdf/PdfLargeFileSplitInstrumentedTest.kt#L765) · [LargeSparsePdfFixture](../../../app/src/androidTest/java/com/m1racle/reader/pdf/PdfLargeFileSplitInstrumentedTest.kt#L806) · [FixtureNode](../../../app/src/androidTest/java/com/m1racle/reader/pdf/PdfLargeFileSplitInstrumentedTest.kt#L895) · [FixtureLayout](../../../app/src/androidTest/java/com/m1racle/reader/pdf/PdfLargeFileSplitInstrumentedTest.kt#L903) | 4 |
| [PdfRenderMetadataInstrumentedTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/pdf/PdfRenderMetadataInstrumentedTest.kt) | [PdfRenderMetadataInstrumentedTest](../../../app/src/androidTest/java/com/m1racle/reader/pdf/PdfRenderMetadataInstrumentedTest.kt#L19) | 1 |
| [R7P0c12TilePixelCompositionInstrumentedTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/pdf/R7P0c12TilePixelCompositionInstrumentedTest.kt) | [R7P0c12TilePixelCompositionInstrumentedTest](../../../app/src/androidTest/java/com/m1racle/reader/pdf/R7P0c12TilePixelCompositionInstrumentedTest.kt#L25) | 1 |

### `com.m1racle.reader.ui.documents`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [MarkdownEditorRotationInstrumentedTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/ui/documents/MarkdownEditorRotationInstrumentedTest.kt) | [MarkdownEditorRotationInstrumentedTest](../../../app/src/androidTest/java/com/m1racle/reader/ui/documents/MarkdownEditorRotationInstrumentedTest.kt#L34) | 1 |
| [MarkdownWebEditorInstrumentedTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/ui/documents/MarkdownWebEditorInstrumentedTest.kt) | [MarkdownWebEditorInstrumentedTest](../../../app/src/androidTest/java/com/m1racle/reader/ui/documents/MarkdownWebEditorInstrumentedTest.kt#L17) | 1 |

### `com.m1racle.reader.ui.epub`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [EpubNamedAnchorNavigationInstrumentedTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/ui/epub/EpubNamedAnchorNavigationInstrumentedTest.kt) | [EpubNamedAnchorNavigationInstrumentedTest](../../../app/src/androidTest/java/com/m1racle/reader/ui/epub/EpubNamedAnchorNavigationInstrumentedTest.kt#L29) | 1 |
| [EpubNativeFontInstrumentedTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/ui/epub/EpubNativeFontInstrumentedTest.kt) | [EpubNativeFontInstrumentedTest](../../../app/src/androidTest/java/com/m1racle/reader/ui/epub/EpubNativeFontInstrumentedTest.kt#L34) | 3 |
| [ReaderFontInstrumentedTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/ui/epub/ReaderFontInstrumentedTest.kt) | [ReaderFontInstrumentedTest](../../../app/src/androidTest/java/com/m1racle/reader/ui/epub/ReaderFontInstrumentedTest.kt#L19) | 1 |

### `com.m1racle.reader.ui.home`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [HomeLibraryNavigationInstrumentedTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/ui/home/HomeLibraryNavigationInstrumentedTest.kt) | [HomeLibraryNavigationInstrumentedTest](../../../app/src/androidTest/java/com/m1racle/reader/ui/home/HomeLibraryNavigationInstrumentedTest.kt#L14) | 1 |
| [HomePreviewInstrumentedTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/ui/home/HomePreviewInstrumentedTest.kt) | [HomePreviewInstrumentedTest](../../../app/src/androidTest/java/com/m1racle/reader/ui/home/HomePreviewInstrumentedTest.kt#L43) | 3 |

### `com.m1racle.reader.ui.hub`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [GlassDockPixelsInstrumentedTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/ui/hub/GlassDockPixelsInstrumentedTest.kt) | [GlassDockPixelsInstrumentedTest](../../../app/src/androidTest/java/com/m1racle/reader/ui/hub/GlassDockPixelsInstrumentedTest.kt#L60) | 35 |
| [HubNavigationMotionInstrumentedTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/ui/hub/HubNavigationMotionInstrumentedTest.kt) | [HubNavigationMotionInstrumentedTest](../../../app/src/androidTest/java/com/m1racle/reader/ui/hub/HubNavigationMotionInstrumentedTest.kt#L13) | 1 |

### `com.m1racle.reader.ui.pdf`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [ExternalOpenSingleSessionInstrumentedTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/ExternalOpenSingleSessionInstrumentedTest.kt) | [ExternalOpenSingleSessionInstrumentedTest](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/ExternalOpenSingleSessionInstrumentedTest.kt#L27) | 11 |
| [H1QaMotionEventTrace.kt](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/H1QaMotionEventTrace.kt) | [H1NormalizedPointer](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/H1QaMotionEventTrace.kt#L9) · [H1InjectedPointerFrame](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/H1QaMotionEventTrace.kt#L20) · [H1QaMotionEventTrace](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/H1QaMotionEventTrace.kt#L28) | 0 |
| [PdfAutomaticOutlineInstrumentedTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/PdfAutomaticOutlineInstrumentedTest.kt) | [PdfAutomaticOutlineInstrumentedTest](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/PdfAutomaticOutlineInstrumentedTest.kt#L35) | 1 |
| [PdfContinuousShortPageRetentionInstrumentedTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/PdfContinuousShortPageRetentionInstrumentedTest.kt) | [PdfContinuousShortPageRetentionInstrumentedTest](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/PdfContinuousShortPageRetentionInstrumentedTest.kt#L23) · [VisibleState](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/PdfContinuousShortPageRetentionInstrumentedTest.kt#L78) | 1 |
| [PdfFirstFrameInstrumentedTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/PdfFirstFrameInstrumentedTest.kt) | [PdfFirstFrameInstrumentedTest](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/PdfFirstFrameInstrumentedTest.kt#L61) | 4 |
| [PdfLatePageCountInstrumentedTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/PdfLatePageCountInstrumentedTest.kt) | [PdfLatePageCountInstrumentedTest](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/PdfLatePageCountInstrumentedTest.kt#L24) | 1 |
| [PdfReaderSelectionInstrumentedTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/PdfReaderSelectionInstrumentedTest.kt) | [PdfReaderSelectionInstrumentedTest](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/PdfReaderSelectionInstrumentedTest.kt#L79) · [GlyphTarget](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/PdfReaderSelectionInstrumentedTest.kt#L230) | 4 |
| [PdfScrollJumpInstrumentedTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/PdfScrollJumpInstrumentedTest.kt) | [PdfScrollJumpInstrumentedTest](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/PdfScrollJumpInstrumentedTest.kt#L64) · [PdfKnownAspectWindowRetentionInstrumentedTest](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/PdfScrollJumpInstrumentedTest.kt#L366) | 5 |
| [PdfSystemScrollCaptureInstrumentedTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/PdfSystemScrollCaptureInstrumentedTest.kt) | [PdfSystemScrollCaptureInstrumentedTest](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/PdfSystemScrollCaptureInstrumentedTest.kt#L25) | 1 |
| [PdfViewerRenderIntegrationInstrumentedTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/PdfViewerRenderIntegrationInstrumentedTest.kt) | [PdfViewerRenderIntegrationInstrumentedTest](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/PdfViewerRenderIntegrationInstrumentedTest.kt#L51) | 1 |
| [R7GestureLifecycleInstrumentedTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/R7GestureLifecycleInstrumentedTest.kt) | [R7GestureLifecycleInstrumentedTest](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/R7GestureLifecycleInstrumentedTest.kt#L56) · [DecoupledHarness](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/R7GestureLifecycleInstrumentedTest.kt#L540) · [R7ConfigurationRecreationInstrumentedTest](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/R7GestureLifecycleInstrumentedTest.kt#L663) | 20 |
| [R7H12AutomatedAcceptanceTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/R7H12AutomatedAcceptanceTest.kt) | [R7H12AutomatedAcceptanceTest](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/R7H12AutomatedAcceptanceTest.kt#L21) | 8 |
| [R7H13RealUiAcceptanceTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/R7H13RealUiAcceptanceTest.kt) | [R7H13RealUiAcceptanceTest](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/R7H13RealUiAcceptanceTest.kt#L16) | 1 |
| [R7H14RealMainActivityAcceptanceTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/R7H14RealMainActivityAcceptanceTest.kt) | [R7H14RealMainActivityAcceptanceTest](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/R7H14RealMainActivityAcceptanceTest.kt#L21) | 2 |
| [R7H15ThreePageStripInstrumentedTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/R7H15ThreePageStripInstrumentedTest.kt) | [R7H15ThreePageStripInstrumentedTest](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/R7H15ThreePageStripInstrumentedTest.kt#L16) | 8 |
| [R7H15ThreePageStripRealUiTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/R7H15ThreePageStripRealUiTest.kt) | [R7H15ThreePageStripRealUiTest](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/R7H15ThreePageStripRealUiTest.kt#L23) | 4 |
| [R7P0c11H1InstrumentedTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/R7P0c11H1InstrumentedTest.kt) | [R7P0c11H1InstrumentedTest](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/R7P0c11H1InstrumentedTest.kt#L20) | 14 |
| [R7VerticalRealFrameInstrumentedTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/R7VerticalRealFrameInstrumentedTest.kt) | [R7VerticalRealFrameInstrumentedTest](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/R7VerticalRealFrameInstrumentedTest.kt#L43) · [Measured](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/R7VerticalRealFrameInstrumentedTest.kt#L191) | 1 |

### `com.m1racle.reader.ui.viewer`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [MarkdownReadingBacklogInstrumentedTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/ui/viewer/MarkdownReadingBacklogInstrumentedTest.kt) | [MarkdownReadingBacklogInstrumentedTest](../../../app/src/androidTest/java/com/m1racle/reader/ui/viewer/MarkdownReadingBacklogInstrumentedTest.kt#L38) | 10 |
| [ReaderProgressiveChromeInstrumentedTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/ui/viewer/ReaderProgressiveChromeInstrumentedTest.kt) | [ReaderProgressiveChromeInstrumentedTest](../../../app/src/androidTest/java/com/m1racle/reader/ui/viewer/ReaderProgressiveChromeInstrumentedTest.kt#L57) | 10 |

### `com.m1racle.reader.ui.viewer.components`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [MarkdownResponsiveTableInstrumentedTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/ui/viewer/components/MarkdownResponsiveTableInstrumentedTest.kt) | [MarkdownResponsiveTableInstrumentedTest](../../../app/src/androidTest/java/com/m1racle/reader/ui/viewer/components/MarkdownResponsiveTableInstrumentedTest.kt#L28) | 5 |

## `core/model/src/test`

0 个 Kotlin 文件，0 个 `@Test` 标记。

## `core/database/src/test`

15 个 Kotlin 文件，48 个 `@Test` 标记。

### `com.m1racle.reader.data.local`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [DocumentMigrationSqlTest.kt](../../../core/database/src/test/java/com/m1racle/reader/data/local/DocumentMigrationSqlTest.kt) | [DocumentMigrationSqlTest](../../../core/database/src/test/java/com/m1racle/reader/data/local/DocumentMigrationSqlTest.kt#L11) | 8 |
| [DocumentTypeTest.kt](../../../core/database/src/test/java/com/m1racle/reader/data/local/DocumentTypeTest.kt) | [DocumentTypeTest](../../../core/database/src/test/java/com/m1racle/reader/data/local/DocumentTypeTest.kt#L8) | 10 |
| [DocumentTypeZipTest.kt](../../../core/database/src/test/java/com/m1racle/reader/data/local/DocumentTypeZipTest.kt) | [DocumentTypeZipTest](../../../core/database/src/test/java/com/m1racle/reader/data/local/DocumentTypeZipTest.kt#L6) | 2 |
| [MigrationV10ToV11Test.kt](../../../core/database/src/test/java/com/m1racle/reader/data/local/MigrationV10ToV11Test.kt) | [MigrationV10ToV11Test](../../../core/database/src/test/java/com/m1racle/reader/data/local/MigrationV10ToV11Test.kt#L11) | 1 |
| [MigrationV11ToV12Test.kt](../../../core/database/src/test/java/com/m1racle/reader/data/local/MigrationV11ToV12Test.kt) | [MigrationV11ToV12Test](../../../core/database/src/test/java/com/m1racle/reader/data/local/MigrationV11ToV12Test.kt#L12) | 1 |
| [MigrationV12ToV13Test.kt](../../../core/database/src/test/java/com/m1racle/reader/data/local/MigrationV12ToV13Test.kt) | [MigrationV12ToV13Test](../../../core/database/src/test/java/com/m1racle/reader/data/local/MigrationV12ToV13Test.kt#L12) | 2 |
| [MigrationV13ToV14Test.kt](../../../core/database/src/test/java/com/m1racle/reader/data/local/MigrationV13ToV14Test.kt) | [MigrationV13ToV14Test](../../../core/database/src/test/java/com/m1racle/reader/data/local/MigrationV13ToV14Test.kt#L7) | 1 |
| [MigrationV14ToV15Test.kt](../../../core/database/src/test/java/com/m1racle/reader/data/local/MigrationV14ToV15Test.kt) | [MigrationV14ToV15Test](../../../core/database/src/test/java/com/m1racle/reader/data/local/MigrationV14ToV15Test.kt#L20) | 2 |
| [MigrationV15ToV16Test.kt](../../../core/database/src/test/java/com/m1racle/reader/data/local/MigrationV15ToV16Test.kt) | [MigrationV15ToV16Test](../../../core/database/src/test/java/com/m1racle/reader/data/local/MigrationV15ToV16Test.kt#L7) | 1 |
| [MigrationV16ToV17Test.kt](../../../core/database/src/test/java/com/m1racle/reader/data/local/MigrationV16ToV17Test.kt) | [MigrationV16ToV17Test](../../../core/database/src/test/java/com/m1racle/reader/data/local/MigrationV16ToV17Test.kt#L7) | 1 |
| [MigrationV6ToV7Test.kt](../../../core/database/src/test/java/com/m1racle/reader/data/local/MigrationV6ToV7Test.kt) | [MigrationV6ToV7Test](../../../core/database/src/test/java/com/m1racle/reader/data/local/MigrationV6ToV7Test.kt#L19) | 5 |
| [MigrationV7ToV8Test.kt](../../../core/database/src/test/java/com/m1racle/reader/data/local/MigrationV7ToV8Test.kt) | [MigrationV7ToV8Test](../../../core/database/src/test/java/com/m1racle/reader/data/local/MigrationV7ToV8Test.kt#L17) | 3 |
| [MigrationV8ToV9Test.kt](../../../core/database/src/test/java/com/m1racle/reader/data/local/MigrationV8ToV9Test.kt) | [MigrationV8ToV9Test](../../../core/database/src/test/java/com/m1racle/reader/data/local/MigrationV8ToV9Test.kt#L18) | 4 |
| [MigrationV9ToV10Test.kt](../../../core/database/src/test/java/com/m1racle/reader/data/local/MigrationV9ToV10Test.kt) | [MigrationV9ToV10Test](../../../core/database/src/test/java/com/m1racle/reader/data/local/MigrationV9ToV10Test.kt#L15) | 3 |
| [OutlineImportPlannerTest.kt](../../../core/database/src/test/java/com/m1racle/reader/data/local/OutlineImportPlannerTest.kt) | [OutlineImportPlannerTest](../../../core/database/src/test/java/com/m1racle/reader/data/local/OutlineImportPlannerTest.kt#L12) | 4 |

## `core/pdf/src/test`

41 个 Kotlin 文件，465 个 `@Test` 标记。

### `com.m1racle.reader.pdf`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [AnchoredNestedGeometryTest.kt](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/AnchoredNestedGeometryTest.kt) | [AnchoredNestedGeometryTest](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/AnchoredNestedGeometryTest.kt#L11) | 14 |
| [BitmapAllocationGuardTest.kt](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/BitmapAllocationGuardTest.kt) | [BitmapAllocationGuardTest](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/BitmapAllocationGuardTest.kt#L18) | 9 |
| [ContinuousDocumentGeometryTest.kt](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/ContinuousDocumentGeometryTest.kt) | [ContinuousDocumentGeometryTest](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/ContinuousDocumentGeometryTest.kt#L8) | 10 |
| [ContinuousEntryRestoreStateTest.kt](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/ContinuousEntryRestoreStateTest.kt) | [ContinuousEntryRestoreStateTest](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/ContinuousEntryRestoreStateTest.kt#L13) | 8 |
| [ContinuousInteractionTransactionTest.kt](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/ContinuousInteractionTransactionTest.kt) | [ContinuousInteractionTransactionTest](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/ContinuousInteractionTransactionTest.kt#L12) | 8 |
| [ContinuousLazyPolicyTest.kt](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/ContinuousLazyPolicyTest.kt) | [ContinuousLazyPolicyTest](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/ContinuousLazyPolicyTest.kt#L16) | 7 |
| [ContinuousRenderSchedulingTest.kt](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/ContinuousRenderSchedulingTest.kt) | [ContinuousRenderSchedulingTest](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/ContinuousRenderSchedulingTest.kt#L10) | 4 |
| [ContinuousTransformMathTest.kt](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/ContinuousTransformMathTest.kt) | [ContinuousTransformMathTest](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/ContinuousTransformMathTest.kt#L7) | 12 |
| [ContinuousViewportDispatchGateRedTest.kt](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/ContinuousViewportDispatchGateRedTest.kt) | [ContinuousViewportDispatchGateRedTest](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/ContinuousViewportDispatchGateRedTest.kt#L24) | 2 |
| [ContinuousViewportPolicyTest.kt](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/ContinuousViewportPolicyTest.kt) | [ContinuousViewportPolicyTest](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/ContinuousViewportPolicyTest.kt#L7) | 8 |
| [ContinuousZoomHandoffTest.kt](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/ContinuousZoomHandoffTest.kt) | [ContinuousZoomHandoffTest](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/ContinuousZoomHandoffTest.kt#L7) | 4 |
| [EmbeddedOutlineBuilderTest.kt](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/EmbeddedOutlineBuilderTest.kt) | [EmbeddedOutlineBuilderTest](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/EmbeddedOutlineBuilderTest.kt#L9) | 5 |
| [OutlinePageIndexResolverTest.kt](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/OutlinePageIndexResolverTest.kt) | [OutlinePageIndexResolverTest](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/OutlinePageIndexResolverTest.kt#L30) | 8 |
| [PdfBoxRuntimeLargeFileTest.kt](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/PdfBoxRuntimeLargeFileTest.kt) | [PdfBoxRuntimeLargeFileTest](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/PdfBoxRuntimeLargeFileTest.kt#L25) | 4 |
| [PdfChromeInteractionLockContractTest.kt](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/PdfChromeInteractionLockContractTest.kt) | [PdfChromeInteractionLockContractTest](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/PdfChromeInteractionLockContractTest.kt#L18) | 8 |
| [PdfChromeScrollStateMachineTest.kt](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/PdfChromeScrollStateMachineTest.kt) | [PdfChromeScrollStateMachineTest](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/PdfChromeScrollStateMachineTest.kt#L13) | 30 |
| [PdfEntryRestoreTest.kt](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/PdfEntryRestoreTest.kt) | [PdfEntryRestoreTest](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/PdfEntryRestoreTest.kt#L10) | 5 |
| [PdfPageBitmapCacheTest.kt](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/PdfPageBitmapCacheTest.kt) | [PdfPageBitmapCacheTest](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/PdfPageBitmapCacheTest.kt#L18) | 5 |
| [PdfRenderQualityTest.kt](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/PdfRenderQualityTest.kt) | [PdfRenderQualityTest](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/PdfRenderQualityTest.kt#L11) | 11 |
| [PdfResourceCloseOrderTest.kt](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/PdfResourceCloseOrderTest.kt) | [PdfResourceCloseOrderTest](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/PdfResourceCloseOrderTest.kt#L7) | 6 |
| [PdfTileLruEvictionTest.kt](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/PdfTileLruEvictionTest.kt) | [PdfTileLruEvictionTest](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/PdfTileLruEvictionTest.kt#L19) | 8 |
| [PrintPageResolverTest.kt](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/PrintPageResolverTest.kt) | [PrintPageResolverTest](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/PrintPageResolverTest.kt#L13) | 18 |
| [R5ContinuousScrollTest.kt](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/R5ContinuousScrollTest.kt) | [R5ContinuousScrollTest](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/R5ContinuousScrollTest.kt#L10) | 17 |
| [R7H11bContinuousPhysicsCharacterizationTest.kt](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/R7H11bContinuousPhysicsCharacterizationTest.kt) | [R7H11bContinuousPhysicsCharacterizationTest](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/R7H11bContinuousPhysicsCharacterizationTest.kt#L17) | 6 |
| [R7H12HorizontalPolicyTest.kt](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/R7H12HorizontalPolicyTest.kt) | [R7H12HorizontalPolicyTest](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/R7H12HorizontalPolicyTest.kt#L12) | 12 |
| [R7H13HorizontalRealPathRecoveryTest.kt](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/R7H13HorizontalRealPathRecoveryTest.kt) | [R7H13HorizontalRealPathRecoveryTest](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/R7H13HorizontalRealPathRecoveryTest.kt#L7) | 7 |
| [R7H14UnifiedGestureControllerTest.kt](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/R7H14UnifiedGestureControllerTest.kt) | [R7H14UnifiedGestureControllerTest](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/R7H14UnifiedGestureControllerTest.kt#L15) | 13 |
| [R7H1HorizontalZoomPanPhysicsTest.kt](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/R7H1HorizontalZoomPanPhysicsTest.kt) | [R7H1HorizontalZoomPanPhysicsTest](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/R7H1HorizontalZoomPanPhysicsTest.kt#L7) | 38 |
| [R7P0a1RestoreReadyReleaseTest.kt](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/R7P0a1RestoreReadyReleaseTest.kt) | [R7P0a1RestoreReadyReleaseTest](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/R7P0a1RestoreReadyReleaseTest.kt#L28) | 25 |
| [R7P0aGestureContractTest.kt](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/R7P0aGestureContractTest.kt) | [R7P0aGestureContractTest](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/R7P0aGestureContractTest.kt#L15) | 10 |
| [R7P0b11PointerEpochTest.kt](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/R7P0b11PointerEpochTest.kt) | [R7P0b11PointerEpochTest](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/R7P0b11PointerEpochTest.kt#L6) | 6 |
| [R7P0b1DisplayPolicyTest.kt](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/R7P0b1DisplayPolicyTest.kt) | [R7P0b1DisplayPolicyTest](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/R7P0b1DisplayPolicyTest.kt#L21) | 23 |
| [R7P0b1PinchAnchorTest.kt](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/R7P0b1PinchAnchorTest.kt) | [R7P0b1PinchAnchorTest](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/R7P0b1PinchAnchorTest.kt#L16) | 6 |
| [R7P0c11PageAheadTest.kt](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/R7P0c11PageAheadTest.kt) | [R7P0c11PageAheadTest](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/R7P0c11PageAheadTest.kt#L5) | 10 |
| [R7P0c12TileGeometryTest.kt](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/R7P0c12TileGeometryTest.kt) | [R7P0c12TileGeometryTest](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/R7P0c12TileGeometryTest.kt#L9) | 7 |
| [R7P0c14GestureRenderDecouplingTest.kt](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/R7P0c14GestureRenderDecouplingTest.kt) | [R7P0c14GestureRenderDecouplingTest](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/R7P0c14GestureRenderDecouplingTest.kt#L16) | 24 |
| [R7P0c1PredictiveTilesTest.kt](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/R7P0c1PredictiveTilesTest.kt) | [R7P0c1PredictiveTilesTest](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/R7P0c1PredictiveTilesTest.kt#L8) | 29 |
| [ReaderChromeGeometryTest.kt](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/ReaderChromeGeometryTest.kt) | [ReaderChromeGeometryTest](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/ReaderChromeGeometryTest.kt#L19) | 10 |
| [TileOverlayPurgeTest.kt](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/TileOverlayPurgeTest.kt) | [TileOverlayPurgeTest](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/TileOverlayPurgeTest.kt#L18) | 3 |
| [TileWorkQueueTest.kt](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/TileWorkQueueTest.kt) | [TileWorkQueueTest](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/TileWorkQueueTest.kt#L33) | 10 |
| [ZoomPanPhysicsTest.kt](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/ZoomPanPhysicsTest.kt) | [ZoomPanPhysicsTest](../../../core/pdf/src/test/java/com/m1racle/reader/pdf/ZoomPanPhysicsTest.kt#L9) | 15 |

## `pdf-tool/src/test`

9 个 Kotlin 文件，51 个 `@Test` 标记。

### `com.m1racle.reader.pdfsplit.split`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [ConcurrencyPolicyTest.kt](../../../pdf-tool/src/test/java/com/m1racle/reader/pdfsplit/split/ConcurrencyPolicyTest.kt) | [ConcurrencyPolicyTest](../../../pdf-tool/src/test/java/com/m1racle/reader/pdfsplit/split/ConcurrencyPolicyTest.kt#L14) | 2 |
| [OutlinePlannerTest.kt](../../../pdf-tool/src/test/java/com/m1racle/reader/pdfsplit/split/OutlinePlannerTest.kt) | [OutlinePlannerTest](../../../pdf-tool/src/test/java/com/m1racle/reader/pdfsplit/split/OutlinePlannerTest.kt#L8) | 9 |
| [OutputPlannerTest.kt](../../../pdf-tool/src/test/java/com/m1racle/reader/pdfsplit/split/OutputPlannerTest.kt) | [OutputPlannerTest](../../../pdf-tool/src/test/java/com/m1racle/reader/pdfsplit/split/OutputPlannerTest.kt#L6) | 5 |
| [PageRangeParserTest.kt](../../../pdf-tool/src/test/java/com/m1racle/reader/pdfsplit/split/PageRangeParserTest.kt) | [PageRangeParserTest](../../../pdf-tool/src/test/java/com/m1racle/reader/pdfsplit/split/PageRangeParserTest.kt#L7) | 14 |
| [PdfChapterDetectorTest.kt](../../../pdf-tool/src/test/java/com/m1racle/reader/pdfsplit/split/PdfChapterDetectorTest.kt) | [PdfChapterDetectorTest](../../../pdf-tool/src/test/java/com/m1racle/reader/pdfsplit/split/PdfChapterDetectorTest.kt#L7) | 5 |
| [PdfOutlineReaderTest.kt](../../../pdf-tool/src/test/java/com/m1racle/reader/pdfsplit/split/PdfOutlineReaderTest.kt) | [PdfOutlineReaderTest](../../../pdf-tool/src/test/java/com/m1racle/reader/pdfsplit/split/PdfOutlineReaderTest.kt#L7) | 4 |
| [PdfPageLabelsTest.kt](../../../pdf-tool/src/test/java/com/m1racle/reader/pdfsplit/split/PdfPageLabelsTest.kt) | [PdfPageLabelsTest](../../../pdf-tool/src/test/java/com/m1racle/reader/pdfsplit/split/PdfPageLabelsTest.kt#L6) | 3 |
| [PdfSplitCleanupTest.kt](../../../pdf-tool/src/test/java/com/m1racle/reader/pdfsplit/split/PdfSplitCleanupTest.kt) | [PdfSplitCleanupTest](../../../pdf-tool/src/test/java/com/m1racle/reader/pdfsplit/split/PdfSplitCleanupTest.kt#L13) | 5 |
| [SplitCleanupMessageTest.kt](../../../pdf-tool/src/test/java/com/m1racle/reader/pdfsplit/split/SplitCleanupMessageTest.kt) | [SplitCleanupMessageTest](../../../pdf-tool/src/test/java/com/m1racle/reader/pdfsplit/split/SplitCleanupMessageTest.kt#L8) | 4 |

## `benchmark/src`

1 个 Kotlin 文件，2 个 `@Test` 标记。

### `com.m1racle.reader.benchmark`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [ReaderScrollBenchmark.kt](../../../benchmark/src/main/java/com/m1racle/reader/benchmark/ReaderScrollBenchmark.kt) | [ReaderScrollBenchmark](../../../benchmark/src/main/java/com/m1racle/reader/benchmark/ReaderScrollBenchmark.kt#L29) | 2 |

