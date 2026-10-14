# 测试源码索引

[返回 Wiki](../README.md)

列出全部测试与辅助 Kotlin 文件；数量按去注释代码中的 `@Test` 文本标记统计，不等于参数化展开后的 case 数、执行数或通过数。benchmark 单列为隔离测量入口。

## `app/src/test`

211 个 Kotlin 文件，1636 个 `@Test` 标记。

### `com.m1racle.reader`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [DisplayRefreshRatePolicyTest.kt](../../../app/src/test/java/com/m1racle/reader/DisplayRefreshRatePolicyTest.kt) | [DisplayRefreshRatePolicyTest](../../../app/src/test/java/com/m1racle/reader/DisplayRefreshRatePolicyTest.kt#L7) | 2 |
| [MainActivityExternalIntentRestoreTest.kt](../../../app/src/test/java/com/m1racle/reader/MainActivityExternalIntentRestoreTest.kt) | [MainActivityExternalIntentRestoreTest](../../../app/src/test/java/com/m1racle/reader/MainActivityExternalIntentRestoreTest.kt#L8) | 1 |
| [NightModeConfigChangeTest.kt](../../../app/src/test/java/com/m1racle/reader/NightModeConfigChangeTest.kt) | [NightModeConfigChangeTest](../../../app/src/test/java/com/m1racle/reader/NightModeConfigChangeTest.kt#L12) | 1 |
| [R31ArchitectureReleaseContractTest.kt](../../../app/src/test/java/com/m1racle/reader/R31ArchitectureReleaseContractTest.kt) | [R31ArchitectureReleaseContractTest](../../../app/src/test/java/com/m1racle/reader/R31ArchitectureReleaseContractTest.kt#L12) | 5 |
| [R32ReleaseHardeningContractTest.kt](../../../app/src/test/java/com/m1racle/reader/R32ReleaseHardeningContractTest.kt) | [R32ReleaseHardeningContractTest](../../../app/src/test/java/com/m1racle/reader/R32ReleaseHardeningContractTest.kt#L12) | 5 |
| [R33ProductIdentityMigrationTest.kt](../../../app/src/test/java/com/m1racle/reader/R33ProductIdentityMigrationTest.kt) | [R33ProductIdentityMigrationTest](../../../app/src/test/java/com/m1racle/reader/R33ProductIdentityMigrationTest.kt#L10) | 5 |
| [R37ContentIdentityAuthorityContractTest.kt](../../../app/src/test/java/com/m1racle/reader/R37ContentIdentityAuthorityContractTest.kt) | [R37ContentIdentityAuthorityContractTest](../../../app/src/test/java/com/m1racle/reader/R37ContentIdentityAuthorityContractTest.kt#L10) | 3 |
| [R57AtomicIdentityAuthorityContractTest.kt](../../../app/src/test/java/com/m1racle/reader/R57AtomicIdentityAuthorityContractTest.kt) | [R57AtomicIdentityAuthorityContractTest](../../../app/src/test/java/com/m1racle/reader/R57AtomicIdentityAuthorityContractTest.kt#L10) | 5 |
| [ReleaseHygieneHostTest.kt](../../../app/src/test/java/com/m1racle/reader/ReleaseHygieneHostTest.kt) | [ReleaseHygieneHostTest](../../../app/src/test/java/com/m1racle/reader/ReleaseHygieneHostTest.kt#L16) | 4 |

### `com.m1racle.reader.data.anchor`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [AnchorResolverTest.kt](../../../app/src/test/java/com/m1racle/reader/data/anchor/AnchorResolverTest.kt) | [AnchorResolverTest](../../../app/src/test/java/com/m1racle/reader/data/anchor/AnchorResolverTest.kt#L13) | 6 |
| [BookmarkTargetTest.kt](../../../app/src/test/java/com/m1racle/reader/data/anchor/BookmarkTargetTest.kt) | [BookmarkTargetTest](../../../app/src/test/java/com/m1racle/reader/data/anchor/BookmarkTargetTest.kt#L11) | 3 |
| [DocumentAnchorCodecTest.kt](../../../app/src/test/java/com/m1racle/reader/data/anchor/DocumentAnchorCodecTest.kt) | [DocumentAnchorCodecTest](../../../app/src/test/java/com/m1racle/reader/data/anchor/DocumentAnchorCodecTest.kt#L11) | 5 |
| [EpubTextAnchorCodecTest.kt](../../../app/src/test/java/com/m1racle/reader/data/anchor/EpubTextAnchorCodecTest.kt) | [EpubTextAnchorCodecTest](../../../app/src/test/java/com/m1racle/reader/data/anchor/EpubTextAnchorCodecTest.kt#L12) | 5 |

### `com.m1racle.reader.data.local`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [DocumentMigrationSqlTest.kt](../../../app/src/test/java/com/m1racle/reader/data/local/DocumentMigrationSqlTest.kt) | [DocumentMigrationSqlTest](../../../app/src/test/java/com/m1racle/reader/data/local/DocumentMigrationSqlTest.kt#L11) | 8 |
| [DocumentTypeTest.kt](../../../app/src/test/java/com/m1racle/reader/data/local/DocumentTypeTest.kt) | [DocumentTypeTest](../../../app/src/test/java/com/m1racle/reader/data/local/DocumentTypeTest.kt#L8) | 10 |
| [DocumentTypeZipTest.kt](../../../app/src/test/java/com/m1racle/reader/data/local/DocumentTypeZipTest.kt) | [DocumentTypeZipTest](../../../app/src/test/java/com/m1racle/reader/data/local/DocumentTypeZipTest.kt#L6) | 2 |
| [MigrationV10ToV11Test.kt](../../../app/src/test/java/com/m1racle/reader/data/local/MigrationV10ToV11Test.kt) | [MigrationV10ToV11Test](../../../app/src/test/java/com/m1racle/reader/data/local/MigrationV10ToV11Test.kt#L11) | 1 |
| [MigrationV11ToV12Test.kt](../../../app/src/test/java/com/m1racle/reader/data/local/MigrationV11ToV12Test.kt) | [MigrationV11ToV12Test](../../../app/src/test/java/com/m1racle/reader/data/local/MigrationV11ToV12Test.kt#L12) | 1 |
| [MigrationV12ToV13Test.kt](../../../app/src/test/java/com/m1racle/reader/data/local/MigrationV12ToV13Test.kt) | [MigrationV12ToV13Test](../../../app/src/test/java/com/m1racle/reader/data/local/MigrationV12ToV13Test.kt#L12) | 2 |
| [MigrationV13ToV14Test.kt](../../../app/src/test/java/com/m1racle/reader/data/local/MigrationV13ToV14Test.kt) | [MigrationV13ToV14Test](../../../app/src/test/java/com/m1racle/reader/data/local/MigrationV13ToV14Test.kt#L7) | 1 |
| [MigrationV14ToV15Test.kt](../../../app/src/test/java/com/m1racle/reader/data/local/MigrationV14ToV15Test.kt) | [MigrationV14ToV15Test](../../../app/src/test/java/com/m1racle/reader/data/local/MigrationV14ToV15Test.kt#L20) | 2 |
| [MigrationV15ToV16Test.kt](../../../app/src/test/java/com/m1racle/reader/data/local/MigrationV15ToV16Test.kt) | [MigrationV15ToV16Test](../../../app/src/test/java/com/m1racle/reader/data/local/MigrationV15ToV16Test.kt#L7) | 1 |
| [MigrationV16ToV17Test.kt](../../../app/src/test/java/com/m1racle/reader/data/local/MigrationV16ToV17Test.kt) | [MigrationV16ToV17Test](../../../app/src/test/java/com/m1racle/reader/data/local/MigrationV16ToV17Test.kt#L7) | 1 |
| [MigrationV6ToV7Test.kt](../../../app/src/test/java/com/m1racle/reader/data/local/MigrationV6ToV7Test.kt) | [MigrationV6ToV7Test](../../../app/src/test/java/com/m1racle/reader/data/local/MigrationV6ToV7Test.kt#L19) | 5 |
| [MigrationV7ToV8Test.kt](../../../app/src/test/java/com/m1racle/reader/data/local/MigrationV7ToV8Test.kt) | [MigrationV7ToV8Test](../../../app/src/test/java/com/m1racle/reader/data/local/MigrationV7ToV8Test.kt#L17) | 3 |
| [MigrationV8ToV9Test.kt](../../../app/src/test/java/com/m1racle/reader/data/local/MigrationV8ToV9Test.kt) | [MigrationV8ToV9Test](../../../app/src/test/java/com/m1racle/reader/data/local/MigrationV8ToV9Test.kt#L18) | 4 |
| [MigrationV9ToV10Test.kt](../../../app/src/test/java/com/m1racle/reader/data/local/MigrationV9ToV10Test.kt) | [MigrationV9ToV10Test](../../../app/src/test/java/com/m1racle/reader/data/local/MigrationV9ToV10Test.kt#L15) | 3 |
| [ReadingModelAuthorityTest.kt](../../../app/src/test/java/com/m1racle/reader/data/local/ReadingModelAuthorityTest.kt) | [ReadingModelAuthorityTest](../../../app/src/test/java/com/m1racle/reader/data/local/ReadingModelAuthorityTest.kt#L18) | 4 |

### `com.m1racle.reader.data.repository`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [AnnotationAnchorRepositoryTest.kt](../../../app/src/test/java/com/m1racle/reader/data/repository/AnnotationAnchorRepositoryTest.kt) | [AnnotationAnchorRepositoryTest](../../../app/src/test/java/com/m1racle/reader/data/repository/AnnotationAnchorRepositoryTest.kt#L23) · [FakeHighlightDao](../../../app/src/test/java/com/m1racle/reader/data/repository/AnnotationAnchorRepositoryTest.kt#L25) · [FakeNoteDao](../../../app/src/test/java/com/m1racle/reader/data/repository/AnnotationAnchorRepositoryTest.kt#L52) · [NoBookmarks](../../../app/src/test/java/com/m1racle/reader/data/repository/AnnotationAnchorRepositoryTest.kt#L84) · [NoFavorites](../../../app/src/test/java/com/m1racle/reader/data/repository/AnnotationAnchorRepositoryTest.kt#L96) | 3 |
| [AppPrivateDocumentCleanupTest.kt](../../../app/src/test/java/com/m1racle/reader/data/repository/AppPrivateDocumentCleanupTest.kt) | [AppPrivateDocumentCleanupTest](../../../app/src/test/java/com/m1racle/reader/data/repository/AppPrivateDocumentCleanupTest.kt#L10) | 3 |
| [BookmarkRepositoryTest.kt](../../../app/src/test/java/com/m1racle/reader/data/repository/BookmarkRepositoryTest.kt) | [BookmarkRepositoryTest](../../../app/src/test/java/com/m1racle/reader/data/repository/BookmarkRepositoryTest.kt#L23) · [FakeBookmarkDao](../../../app/src/test/java/com/m1racle/reader/data/repository/BookmarkRepositoryTest.kt#L26) · [NoFavorites](../../../app/src/test/java/com/m1racle/reader/data/repository/BookmarkRepositoryTest.kt#L74) · [NoHighlights](../../../app/src/test/java/com/m1racle/reader/data/repository/BookmarkRepositoryTest.kt#L81) · [NoNotes](../../../app/src/test/java/com/m1racle/reader/data/repository/BookmarkRepositoryTest.kt#L89) | 4 |
| [ContentIdentityImportWiringTest.kt](../../../app/src/test/java/com/m1racle/reader/data/repository/ContentIdentityImportWiringTest.kt) | [ContentIdentityImportWiringTest](../../../app/src/test/java/com/m1racle/reader/data/repository/ContentIdentityImportWiringTest.kt#L60) · [Fixture](../../../app/src/test/java/com/m1racle/reader/data/repository/ContentIdentityImportWiringTest.kt#L444) · [IdentityDocumentDao](../../../app/src/test/java/com/m1racle/reader/data/repository/ContentIdentityImportWiringTest.kt#L605) · [UnusedPdfPrintPageAnchorDao](../../../app/src/test/java/com/m1racle/reader/data/repository/ContentIdentityImportWiringTest.kt#L739) · [UnusedTextbookOutlineNodeDao](../../../app/src/test/java/com/m1racle/reader/data/repository/ContentIdentityImportWiringTest.kt#L747) | 22 |
| [DocumentTextReaderTest.kt](../../../app/src/test/java/com/m1racle/reader/data/repository/DocumentTextReaderTest.kt) | [DocumentTextReaderTest](../../../app/src/test/java/com/m1racle/reader/data/repository/DocumentTextReaderTest.kt#L8) | 2 |
| [EpubAnchorPersistenceTest.kt](../../../app/src/test/java/com/m1racle/reader/data/repository/EpubAnchorPersistenceTest.kt) | [EpubAnchorPersistenceTest](../../../app/src/test/java/com/m1racle/reader/data/repository/EpubAnchorPersistenceTest.kt#L29) | 2 |
| [FavoriteRepositoryTest.kt](../../../app/src/test/java/com/m1racle/reader/data/repository/FavoriteRepositoryTest.kt) | [FavoriteRepositoryTest](../../../app/src/test/java/com/m1racle/reader/data/repository/FavoriteRepositoryTest.kt#L22) · [FakeFavoriteDao](../../../app/src/test/java/com/m1racle/reader/data/repository/FavoriteRepositoryTest.kt#L25) · [NoBookmarks](../../../app/src/test/java/com/m1racle/reader/data/repository/FavoriteRepositoryTest.kt#L45) · [NoHighlights](../../../app/src/test/java/com/m1racle/reader/data/repository/FavoriteRepositoryTest.kt#L55) · [NoNotes](../../../app/src/test/java/com/m1racle/reader/data/repository/FavoriteRepositoryTest.kt#L63) | 4 |
| [OutlineImportTransactionTest.kt](../../../app/src/test/java/com/m1racle/reader/data/repository/OutlineImportTransactionTest.kt) | [OutlineImportTransactionTest](../../../app/src/test/java/com/m1racle/reader/data/repository/OutlineImportTransactionTest.kt#L25) | 6 |
| [PackageDirCleanupPolicyTest.kt](../../../app/src/test/java/com/m1racle/reader/data/repository/PackageDirCleanupPolicyTest.kt) | [PackageDirCleanupPolicyTest](../../../app/src/test/java/com/m1racle/reader/data/repository/PackageDirCleanupPolicyTest.kt#L14) | 3 |
| [PdfProgressTest.kt](../../../app/src/test/java/com/m1racle/reader/data/repository/PdfProgressTest.kt) | [PdfProgressTest](../../../app/src/test/java/com/m1racle/reader/data/repository/PdfProgressTest.kt#L9) | 4 |
| [ReadingRepositoryTransactionTest.kt](../../../app/src/test/java/com/m1racle/reader/data/repository/ReadingRepositoryTransactionTest.kt) | [ReadingRepositoryTransactionTest](../../../app/src/test/java/com/m1racle/reader/data/repository/ReadingRepositoryTransactionTest.kt#L23) | 2 |
| [SearchExperienceIntegrationTest.kt](../../../app/src/test/java/com/m1racle/reader/data/repository/SearchExperienceIntegrationTest.kt) | [SearchExperienceIntegrationTest](../../../app/src/test/java/com/m1racle/reader/data/repository/SearchExperienceIntegrationTest.kt#L29) · [FakeHighlightDao](../../../app/src/test/java/com/m1racle/reader/data/repository/SearchExperienceIntegrationTest.kt#L184) · [FakeNoteDao](../../../app/src/test/java/com/m1racle/reader/data/repository/SearchExperienceIntegrationTest.kt#L200) · [NoBookmarks](../../../app/src/test/java/com/m1racle/reader/data/repository/SearchExperienceIntegrationTest.kt#L221) · [NoFavorites](../../../app/src/test/java/com/m1racle/reader/data/repository/SearchExperienceIntegrationTest.kt#L231) | 6 |
| [SearchRepositoryTest.kt](../../../app/src/test/java/com/m1racle/reader/data/repository/SearchRepositoryTest.kt) | [SearchRepositoryTest](../../../app/src/test/java/com/m1racle/reader/data/repository/SearchRepositoryTest.kt#L21) · [FakeSearchIndexDao](../../../app/src/test/java/com/m1racle/reader/data/repository/SearchRepositoryTest.kt#L23) · [Key](../../../app/src/test/java/com/m1racle/reader/data/repository/SearchRepositoryTest.kt#L24) | 6 |

### `com.m1racle.reader.data.settings`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [ReaderLineHeightMigrationTest.kt](../../../app/src/test/java/com/m1racle/reader/data/settings/ReaderLineHeightMigrationTest.kt) | [ReaderLineHeightMigrationTest](../../../app/src/test/java/com/m1racle/reader/data/settings/ReaderLineHeightMigrationTest.kt#L8) | 2 |

### `com.m1racle.reader.documents`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [DocumentToolsTest.kt](../../../app/src/test/java/com/m1racle/reader/documents/DocumentToolsTest.kt) | [DocumentToolsTest](../../../app/src/test/java/com/m1racle/reader/documents/DocumentToolsTest.kt#L22) | 4 |
| [MarkdownDraftStoreTest.kt](../../../app/src/test/java/com/m1racle/reader/documents/MarkdownDraftStoreTest.kt) | [MarkdownDraftStoreTest](../../../app/src/test/java/com/m1racle/reader/documents/MarkdownDraftStoreTest.kt#L15) · [AndroidAtomicRenameShadow](../../../app/src/test/java/com/m1racle/reader/documents/MarkdownDraftStoreTest.kt#L51) | 3 |

### `com.m1racle.reader.epub`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [EpubLocatorRollbackTest.kt](../../../app/src/test/java/com/m1racle/reader/epub/EpubLocatorRollbackTest.kt) | [EpubLocatorRollbackTest](../../../app/src/test/java/com/m1racle/reader/epub/EpubLocatorRollbackTest.kt#L15) | 7 |
| [EpubPackageImporterTest.kt](../../../app/src/test/java/com/m1racle/reader/epub/EpubPackageImporterTest.kt) | [EpubPackageImporterTest](../../../app/src/test/java/com/m1racle/reader/epub/EpubPackageImporterTest.kt#L11) · [NoXIncludeFactory](../../../app/src/test/java/com/m1racle/reader/epub/EpubPackageImporterTest.kt#L116) | 17 |
| [EpubReadingAnchorsTest.kt](../../../app/src/test/java/com/m1racle/reader/epub/EpubReadingAnchorsTest.kt) | [EpubReadingAnchorsTest](../../../app/src/test/java/com/m1racle/reader/epub/EpubReadingAnchorsTest.kt#L18) | 12 |
| [EpubRestoreSessionTest.kt](../../../app/src/test/java/com/m1racle/reader/epub/EpubRestoreSessionTest.kt) | [EpubRestoreSessionTest](../../../app/src/test/java/com/m1racle/reader/epub/EpubRestoreSessionTest.kt#L7) | 4 |
| [EpubTocSafetyTest.kt](../../../app/src/test/java/com/m1racle/reader/epub/EpubTocSafetyTest.kt) | [EpubTocSafetyTest](../../../app/src/test/java/com/m1racle/reader/epub/EpubTocSafetyTest.kt#L9) | 4 |
| [EpubXhtmlBoundaryTest.kt](../../../app/src/test/java/com/m1racle/reader/epub/EpubXhtmlBoundaryTest.kt) | [EpubXhtmlBoundaryTest](../../../app/src/test/java/com/m1racle/reader/epub/EpubXhtmlBoundaryTest.kt#L6) | 5 |

### `com.m1racle.reader.identity`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [ContentIdentityResolverTest.kt](../../../app/src/test/java/com/m1racle/reader/identity/ContentIdentityResolverTest.kt) | [ContentIdentityResolverTest](../../../app/src/test/java/com/m1racle/reader/identity/ContentIdentityResolverTest.kt#L37) · [ResolverDocumentDao](../../../app/src/test/java/com/m1racle/reader/identity/ContentIdentityResolverTest.kt#L266) · [FailingInputStream](../../../app/src/test/java/com/m1racle/reader/identity/ContentIdentityResolverTest.kt#L363) · [BlockingInterruptibleInputStream](../../../app/src/test/java/com/m1racle/reader/identity/ContentIdentityResolverTest.kt#L377) · [GeneratedByteInputStream](../../../app/src/test/java/com/m1racle/reader/identity/ContentIdentityResolverTest.kt#L391) | 7 |
| [LegacyContentHashMigrationV1Test.kt](../../../app/src/test/java/com/m1racle/reader/identity/LegacyContentHashMigrationV1Test.kt) | [LegacyContentHashMigrationV1Test](../../../app/src/test/java/com/m1racle/reader/identity/LegacyContentHashMigrationV1Test.kt#L34) · [LegacyHashDocumentDao](../../../app/src/test/java/com/m1racle/reader/identity/LegacyContentHashMigrationV1Test.kt#L201) · [ThrowingOnceDocumentDao](../../../app/src/test/java/com/m1racle/reader/identity/LegacyContentHashMigrationV1Test.kt#L299) · [LegacyHashDocumentAliasDao](../../../app/src/test/java/com/m1racle/reader/identity/LegacyContentHashMigrationV1Test.kt#L313) | 6 |
| [SidecarIdentityMigrationV1Test.kt](../../../app/src/test/java/com/m1racle/reader/identity/SidecarIdentityMigrationV1Test.kt) | [SidecarIdentityMigrationV1Test](../../../app/src/test/java/com/m1racle/reader/identity/SidecarIdentityMigrationV1Test.kt#L26) · [FakeStore](../../../app/src/test/java/com/m1racle/reader/identity/SidecarIdentityMigrationV1Test.kt#L328) · [FakeRoomTransaction](../../../app/src/test/java/com/m1racle/reader/identity/SidecarIdentityMigrationV1Test.kt#L344) | 14 |

### `com.m1racle.reader.image`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [RelativeImageResolveTest.kt](../../../app/src/test/java/com/m1racle/reader/image/RelativeImageResolveTest.kt) | [RelativeImageResolveTest](../../../app/src/test/java/com/m1racle/reader/image/RelativeImageResolveTest.kt#L14) | 4 |

### `com.m1racle.reader.markdown`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [AnchorRegistryIsolationTest.kt](../../../app/src/test/java/com/m1racle/reader/markdown/AnchorRegistryIsolationTest.kt) | [AnchorRegistryIsolationTest](../../../app/src/test/java/com/m1racle/reader/markdown/AnchorRegistryIsolationTest.kt#L13) | 2 |
| [DocumentListTitleTest.kt](../../../app/src/test/java/com/m1racle/reader/markdown/DocumentListTitleTest.kt) | [DocumentListTitleTest](../../../app/src/test/java/com/m1racle/reader/markdown/DocumentListTitleTest.kt#L8) | 16 |
| [HtmlCommentStripTest.kt](../../../app/src/test/java/com/m1racle/reader/markdown/HtmlCommentStripTest.kt) | [HtmlCommentStripTest](../../../app/src/test/java/com/m1racle/reader/markdown/HtmlCommentStripTest.kt#L8) | 2 |
| [HtmlEmptyAnchorTest.kt](../../../app/src/test/java/com/m1racle/reader/markdown/HtmlEmptyAnchorTest.kt) | [HtmlEmptyAnchorTest](../../../app/src/test/java/com/m1racle/reader/markdown/HtmlEmptyAnchorTest.kt#L10) | 11 |
| [InlineMarkdownParserPerformanceTest.kt](../../../app/src/test/java/com/m1racle/reader/markdown/InlineMarkdownParserPerformanceTest.kt) | [InlineMarkdownParserPerformanceTest](../../../app/src/test/java/com/m1racle/reader/markdown/InlineMarkdownParserPerformanceTest.kt#L19) | 5 |
| [LaoziAnchorFixtureTest.kt](../../../app/src/test/java/com/m1racle/reader/markdown/LaoziAnchorFixtureTest.kt) | [LaoziAnchorFixtureTest](../../../app/src/test/java/com/m1racle/reader/markdown/LaoziAnchorFixtureTest.kt#L16) | 1 |
| [MarkdownAnchorRegistryTest.kt](../../../app/src/test/java/com/m1racle/reader/markdown/MarkdownAnchorRegistryTest.kt) | [MarkdownAnchorRegistryTest](../../../app/src/test/java/com/m1racle/reader/markdown/MarkdownAnchorRegistryTest.kt#L8) | 4 |
| [MarkdownAssetResolverSecurityTest.kt](../../../app/src/test/java/com/m1racle/reader/markdown/MarkdownAssetResolverSecurityTest.kt) | [MarkdownAssetResolverSecurityTest](../../../app/src/test/java/com/m1racle/reader/markdown/MarkdownAssetResolverSecurityTest.kt#L18) | 18 |
| [MarkdownAssetResolverTest.kt](../../../app/src/test/java/com/m1racle/reader/markdown/MarkdownAssetResolverTest.kt) | [MarkdownAssetResolverTest](../../../app/src/test/java/com/m1racle/reader/markdown/MarkdownAssetResolverTest.kt#L16) | 22 |
| [MarkdownEdgeCaseHostTest.kt](../../../app/src/test/java/com/m1racle/reader/markdown/MarkdownEdgeCaseHostTest.kt) | [MarkdownEdgeCaseHostTest](../../../app/src/test/java/com/m1racle/reader/markdown/MarkdownEdgeCaseHostTest.kt#L15) | 12 |
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
| [AnchoredNestedGeometryTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/AnchoredNestedGeometryTest.kt) | [AnchoredNestedGeometryTest](../../../app/src/test/java/com/m1racle/reader/pdf/AnchoredNestedGeometryTest.kt#L11) | 14 |
| [BitmapAllocationGuardTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/BitmapAllocationGuardTest.kt) | [BitmapAllocationGuardTest](../../../app/src/test/java/com/m1racle/reader/pdf/BitmapAllocationGuardTest.kt#L18) | 9 |
| [ContinuousDocumentGeometryTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/ContinuousDocumentGeometryTest.kt) | [ContinuousDocumentGeometryTest](../../../app/src/test/java/com/m1racle/reader/pdf/ContinuousDocumentGeometryTest.kt#L8) | 10 |
| [ContinuousEntryRestoreStateTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/ContinuousEntryRestoreStateTest.kt) | [ContinuousEntryRestoreStateTest](../../../app/src/test/java/com/m1racle/reader/pdf/ContinuousEntryRestoreStateTest.kt#L11) | 8 |
| [ContinuousInteractionTransactionTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/ContinuousInteractionTransactionTest.kt) | [ContinuousInteractionTransactionTest](../../../app/src/test/java/com/m1racle/reader/pdf/ContinuousInteractionTransactionTest.kt#L9) | 8 |
| [ContinuousLazyPolicyTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/ContinuousLazyPolicyTest.kt) | [ContinuousLazyPolicyTest](../../../app/src/test/java/com/m1racle/reader/pdf/ContinuousLazyPolicyTest.kt#L12) | 7 |
| [ContinuousRenderSchedulingTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/ContinuousRenderSchedulingTest.kt) | [ContinuousRenderSchedulingTest](../../../app/src/test/java/com/m1racle/reader/pdf/ContinuousRenderSchedulingTest.kt#L8) | 4 |
| [ContinuousTileIndexTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/ContinuousTileIndexTest.kt) | [ContinuousTileIndexTest](../../../app/src/test/java/com/m1racle/reader/pdf/ContinuousTileIndexTest.kt#L19) | 5 |
| [ContinuousTransformMathTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/ContinuousTransformMathTest.kt) | [ContinuousTransformMathTest](../../../app/src/test/java/com/m1racle/reader/pdf/ContinuousTransformMathTest.kt#L7) | 12 |
| [ContinuousViewportHotPathContractTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/ContinuousViewportHotPathContractTest.kt) | [ContinuousViewportHotPathContractTest](../../../app/src/test/java/com/m1racle/reader/pdf/ContinuousViewportHotPathContractTest.kt#L9) | 6 |
| [ContinuousViewportPolicyTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/ContinuousViewportPolicyTest.kt) | [ContinuousViewportPolicyTest](../../../app/src/test/java/com/m1racle/reader/pdf/ContinuousViewportPolicyTest.kt#L7) | 8 |
| [ContinuousZoomHandoffTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/ContinuousZoomHandoffTest.kt) | [ContinuousZoomHandoffTest](../../../app/src/test/java/com/m1racle/reader/pdf/ContinuousZoomHandoffTest.kt#L7) | 4 |
| [EmbeddedOutlineBuilderTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/EmbeddedOutlineBuilderTest.kt) | [EmbeddedOutlineBuilderTest](../../../app/src/test/java/com/m1racle/reader/pdf/EmbeddedOutlineBuilderTest.kt#L7) | 4 |
| [FinalPdfProgressFlusherTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/FinalPdfProgressFlusherTest.kt) | [FinalPdfProgressFlusherTest](../../../app/src/test/java/com/m1racle/reader/pdf/FinalPdfProgressFlusherTest.kt#L37) · [FlusherHarness](../../../app/src/test/java/com/m1racle/reader/pdf/FinalPdfProgressFlusherTest.kt#L86) | 18 |
| [MotionContinuityGeometryTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/MotionContinuityGeometryTest.kt) | [MotionContinuityGeometryTest](../../../app/src/test/java/com/m1racle/reader/pdf/MotionContinuityGeometryTest.kt#L28) | 9 |
| [OutlinePageIndexResolverTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/OutlinePageIndexResolverTest.kt) | [OutlinePageIndexResolverTest](../../../app/src/test/java/com/m1racle/reader/pdf/OutlinePageIndexResolverTest.kt#L30) | 7 |
| [PdfChromeInteractionLockContractTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/PdfChromeInteractionLockContractTest.kt) | [PdfChromeInteractionLockContractTest](../../../app/src/test/java/com/m1racle/reader/pdf/PdfChromeInteractionLockContractTest.kt#L18) | 8 |
| [PdfChromeScrollStateMachineTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/PdfChromeScrollStateMachineTest.kt) | [PdfChromeScrollStateMachineTest](../../../app/src/test/java/com/m1racle/reader/pdf/PdfChromeScrollStateMachineTest.kt#L13) | 29 |
| [PdfEmbeddedOutlineImporterTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/PdfEmbeddedOutlineImporterTest.kt) | [PdfEmbeddedOutlineImporterTest](../../../app/src/test/java/com/m1racle/reader/pdf/PdfEmbeddedOutlineImporterTest.kt#L11) | 4 |
| [PdfImmersiveSystemBarsTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/PdfImmersiveSystemBarsTest.kt) | [PdfImmersiveSystemBarsTest](../../../app/src/test/java/com/m1racle/reader/pdf/PdfImmersiveSystemBarsTest.kt#L9) | 3 |
| [PdfPageBitmapCacheTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/PdfPageBitmapCacheTest.kt) | [PdfPageBitmapCacheTest](../../../app/src/test/java/com/m1racle/reader/pdf/PdfPageBitmapCacheTest.kt#L15) | 5 |
| [PdfReadingModeTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/PdfReadingModeTest.kt) | [PdfReadingModeTest](../../../app/src/test/java/com/m1racle/reader/pdf/PdfReadingModeTest.kt#L8) | 9 |
| [PdfRenderQualityTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/PdfRenderQualityTest.kt) | [PdfRenderQualityTest](../../../app/src/test/java/com/m1racle/reader/pdf/PdfRenderQualityTest.kt#L7) | 11 |
| [PdfResourceCloseOrderTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/PdfResourceCloseOrderTest.kt) | [PdfResourceCloseOrderTest](../../../app/src/test/java/com/m1racle/reader/pdf/PdfResourceCloseOrderTest.kt#L7) | 6 |
| [PdfTextSelectionTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/PdfTextSelectionTest.kt) | [PdfTextSelectionTest](../../../app/src/test/java/com/m1racle/reader/pdf/PdfTextSelectionTest.kt#L24) | 10 |
| [PdfThumbnailCachePolicyTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/PdfThumbnailCachePolicyTest.kt) | [PdfThumbnailCachePolicyTest](../../../app/src/test/java/com/m1racle/reader/pdf/PdfThumbnailCachePolicyTest.kt#L10) | 2 |
| [PdfTileLruEvictionTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/PdfTileLruEvictionTest.kt) | [PdfTileLruEvictionTest](../../../app/src/test/java/com/m1racle/reader/pdf/PdfTileLruEvictionTest.kt#L19) | 8 |
| [PdfViewportSessionStateTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/PdfViewportSessionStateTest.kt) | [PdfViewportSessionStateTest](../../../app/src/test/java/com/m1racle/reader/pdf/PdfViewportSessionStateTest.kt#L6) | 9 |
| [PrintPageResolverTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/PrintPageResolverTest.kt) | [PrintPageResolverTest](../../../app/src/test/java/com/m1racle/reader/pdf/PrintPageResolverTest.kt#L11) | 18 |
| [R5ContinuousScrollTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/R5ContinuousScrollTest.kt) | [R5ContinuousScrollTest](../../../app/src/test/java/com/m1racle/reader/pdf/R5ContinuousScrollTest.kt#L10) | 17 |
| [R71FrameStabilityTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/R71FrameStabilityTest.kt) | [R71FrameStabilityTest](../../../app/src/test/java/com/m1racle/reader/pdf/R71FrameStabilityTest.kt#L9) | 10 |
| [R7ContinuousViewportTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/R7ContinuousViewportTest.kt) | [R7ContinuousViewportTest](../../../app/src/test/java/com/m1racle/reader/pdf/R7ContinuousViewportTest.kt#L9) | 8 |
| [R7H11bContinuousPhysicsCharacterizationTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/R7H11bContinuousPhysicsCharacterizationTest.kt) | [R7H11bContinuousPhysicsCharacterizationTest](../../../app/src/test/java/com/m1racle/reader/pdf/R7H11bContinuousPhysicsCharacterizationTest.kt#L15) | 6 |
| [R7H11bHorizontalSharedPhysicsTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/R7H11bHorizontalSharedPhysicsTest.kt) | [R7H11bHorizontalSharedPhysicsTest](../../../app/src/test/java/com/m1racle/reader/pdf/R7H11bHorizontalSharedPhysicsTest.kt#L8) | 6 |
| [R7H12HorizontalPolicyTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/R7H12HorizontalPolicyTest.kt) | [R7H12HorizontalPolicyTest](../../../app/src/test/java/com/m1racle/reader/pdf/R7H12HorizontalPolicyTest.kt#L9) | 12 |
| [R7H13HorizontalRealPathRecoveryTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/R7H13HorizontalRealPathRecoveryTest.kt) | [R7H13HorizontalRealPathRecoveryTest](../../../app/src/test/java/com/m1racle/reader/pdf/R7H13HorizontalRealPathRecoveryTest.kt#L7) | 7 |
| [R7H14UnifiedGestureArchitectureTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/R7H14UnifiedGestureArchitectureTest.kt) | [R7H14UnifiedGestureArchitectureTest](../../../app/src/test/java/com/m1racle/reader/pdf/R7H14UnifiedGestureArchitectureTest.kt#L8) | 5 |
| [R7H14UnifiedGestureControllerTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/R7H14UnifiedGestureControllerTest.kt) | [R7H14UnifiedGestureControllerTest](../../../app/src/test/java/com/m1racle/reader/pdf/R7H14UnifiedGestureControllerTest.kt#L9) | 13 |
| [R7H15PlatformScrollAndPageStripTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/R7H15PlatformScrollAndPageStripTest.kt) | [R7H15PlatformScrollAndPageStripTest](../../../app/src/test/java/com/m1racle/reader/pdf/R7H15PlatformScrollAndPageStripTest.kt#L10) | 10 |
| [R7H15ThreePageStripTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/R7H15ThreePageStripTest.kt) | [R7H15ThreePageStripTest](../../../app/src/test/java/com/m1racle/reader/pdf/R7H15ThreePageStripTest.kt#L10) | 16 |
| [R7H1HorizontalZoomPanPhysicsTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/R7H1HorizontalZoomPanPhysicsTest.kt) | [R7H1HorizontalZoomPanPhysicsTest](../../../app/src/test/java/com/m1racle/reader/pdf/R7H1HorizontalZoomPanPhysicsTest.kt#L7) | 38 |
| [R7P0a1RestoreReadyReleaseTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/R7P0a1RestoreReadyReleaseTest.kt) | [R7P0a1RestoreReadyReleaseTest](../../../app/src/test/java/com/m1racle/reader/pdf/R7P0a1RestoreReadyReleaseTest.kt#L23) | 25 |
| [R7P0aGestureContractTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/R7P0aGestureContractTest.kt) | [R7P0aGestureContractTest](../../../app/src/test/java/com/m1racle/reader/pdf/R7P0aGestureContractTest.kt#L9) | 10 |
| [R7P0b11PointerEpochTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/R7P0b11PointerEpochTest.kt) | [R7P0b11PointerEpochTest](../../../app/src/test/java/com/m1racle/reader/pdf/R7P0b11PointerEpochTest.kt#L6) | 6 |
| [R7P0b1DisplayPolicyTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/R7P0b1DisplayPolicyTest.kt) | [R7P0b1DisplayPolicyTest](../../../app/src/test/java/com/m1racle/reader/pdf/R7P0b1DisplayPolicyTest.kt#L19) | 23 |
| [R7P0b1PinchAnchorTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/R7P0b1PinchAnchorTest.kt) | [R7P0b1PinchAnchorTest](../../../app/src/test/java/com/m1racle/reader/pdf/R7P0b1PinchAnchorTest.kt#L16) | 6 |
| [R7P0c11PageAheadTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/R7P0c11PageAheadTest.kt) | [R7P0c11PageAheadTest](../../../app/src/test/java/com/m1racle/reader/pdf/R7P0c11PageAheadTest.kt#L5) | 10 |
| [R7P0c12TileGeometryTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/R7P0c12TileGeometryTest.kt) | [R7P0c12TileGeometryTest](../../../app/src/test/java/com/m1racle/reader/pdf/R7P0c12TileGeometryTest.kt#L9) | 6 |
| [R7P0c14GestureRenderDecouplingTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/R7P0c14GestureRenderDecouplingTest.kt) | [R7P0c14GestureRenderDecouplingTest](../../../app/src/test/java/com/m1racle/reader/pdf/R7P0c14GestureRenderDecouplingTest.kt#L16) | 24 |
| [R7P0c1PredictiveTilesTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/R7P0c1PredictiveTilesTest.kt) | [R7P0c1PredictiveTilesTest](../../../app/src/test/java/com/m1racle/reader/pdf/R7P0c1PredictiveTilesTest.kt#L8) | 29 |
| [ReaderChromeGeometryTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/ReaderChromeGeometryTest.kt) | [ReaderChromeGeometryTest](../../../app/src/test/java/com/m1racle/reader/pdf/ReaderChromeGeometryTest.kt#L19) | 10 |
| [TextbookOutlineResolverTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/TextbookOutlineResolverTest.kt) | [TextbookOutlineResolverTest](../../../app/src/test/java/com/m1racle/reader/pdf/TextbookOutlineResolverTest.kt#L9) | 9 |
| [TileOverlayPurgeTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/TileOverlayPurgeTest.kt) | [TileOverlayPurgeTest](../../../app/src/test/java/com/m1racle/reader/pdf/TileOverlayPurgeTest.kt#L18) | 3 |
| [TileWorkQueueTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/TileWorkQueueTest.kt) | [TileWorkQueueTest](../../../app/src/test/java/com/m1racle/reader/pdf/TileWorkQueueTest.kt#L33) | 10 |
| [ZoomPanPhysicsTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/ZoomPanPhysicsTest.kt) | [ZoomPanPhysicsTest](../../../app/src/test/java/com/m1racle/reader/pdf/ZoomPanPhysicsTest.kt#L9) | 15 |

### `com.m1racle.reader.pdf.edit`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [PdfBodyEditorTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/edit/PdfBodyEditorTest.kt) | [PdfBodyEditorTest](../../../app/src/test/java/com/m1racle/reader/pdf/edit/PdfBodyEditorTest.kt#L23) | 11 |
| [PdfSourceWriteStoreTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/edit/PdfSourceWriteStoreTest.kt) | [PdfSourceWriteStoreTest](../../../app/src/test/java/com/m1racle/reader/pdf/edit/PdfSourceWriteStoreTest.kt#L19) | 6 |

### `com.m1racle.reader.pdf.export`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [ChapterPackageCleanupTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/export/ChapterPackageCleanupTest.kt) | [ChapterPackageCleanupTest](../../../app/src/test/java/com/m1racle/reader/pdf/export/ChapterPackageCleanupTest.kt#L9) | 2 |
| [ChapterPackageP04aAcceptanceTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/export/ChapterPackageP04aAcceptanceTest.kt) | [ChapterPackageP04aAcceptanceTest](../../../app/src/test/java/com/m1racle/reader/pdf/export/ChapterPackageP04aAcceptanceTest.kt#L22) | 7 |
| [ExportCancellationIoTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/export/ExportCancellationIoTest.kt) | [ExportCancellationIoTest](../../../app/src/test/java/com/m1racle/reader/pdf/export/ExportCancellationIoTest.kt#L21) · [CancellingAfterReads](../../../app/src/test/java/com/m1racle/reader/pdf/export/ExportCancellationIoTest.kt#L23) | 6 |
| [ExportCancellationOverlapTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/export/ExportCancellationOverlapTest.kt) | [ExportCancellationOverlapTest](../../../app/src/test/java/com/m1racle/reader/pdf/export/ExportCancellationOverlapTest.kt#L30) | 3 |
| [ExportRangeResolverTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/export/ExportRangeResolverTest.kt) | [ExportRangeResolverTest](../../../app/src/test/java/com/m1racle/reader/pdf/export/ExportRangeResolverTest.kt#L10) | 10 |
| [ExportSafCancellationCleanupTest.kt](../../../app/src/test/java/com/m1racle/reader/pdf/export/ExportSafCancellationCleanupTest.kt) | [ExportSafCancellationCleanupTest](../../../app/src/test/java/com/m1racle/reader/pdf/export/ExportSafCancellationCleanupTest.kt#L46) · [StubDocumentsProvider](../../../app/src/test/java/com/m1racle/reader/pdf/export/ExportSafCancellationCleanupTest.kt#L53) · [CancelMidStream](../../../app/src/test/java/com/m1racle/reader/pdf/export/ExportSafCancellationCleanupTest.kt#L185) | 11 |

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
| [ImportSizeLimitBoundaryTest.kt](../../../app/src/test/java/com/m1racle/reader/share/ImportSizeLimitBoundaryTest.kt) | [ImportSizeLimitBoundaryTest](../../../app/src/test/java/com/m1racle/reader/share/ImportSizeLimitBoundaryTest.kt#L63) · [FakeSizedStream](../../../app/src/test/java/com/m1racle/reader/share/ImportSizeLimitBoundaryTest.kt#L83) · [NullStream](../../../app/src/test/java/com/m1racle/reader/share/ImportSizeLimitBoundaryTest.kt#L94) · [StubContentProvider](../../../app/src/test/java/com/m1racle/reader/share/ImportSizeLimitBoundaryTest.kt#L99) · [B3RecordingDocumentDao](../../../app/src/test/java/com/m1racle/reader/share/ImportSizeLimitBoundaryTest.kt#L272) · [B3UnusedPdfPrintPageAnchorDao](../../../app/src/test/java/com/m1racle/reader/share/ImportSizeLimitBoundaryTest.kt#L383) · [B3UnusedTextbookOutlineNodeDao](../../../app/src/test/java/com/m1racle/reader/share/ImportSizeLimitBoundaryTest.kt#L397) | 7 |
| [ProviderMetadataSourceMatrixTest.kt](../../../app/src/test/java/com/m1racle/reader/share/ProviderMetadataSourceMatrixTest.kt) | [ProviderMetadataSourceMatrixTest](../../../app/src/test/java/com/m1racle/reader/share/ProviderMetadataSourceMatrixTest.kt#L39) · [StubProvider](../../../app/src/test/java/com/m1racle/reader/share/ProviderMetadataSourceMatrixTest.kt#L45) | 9 |
| [ZipExternalOpenContractTest.kt](../../../app/src/test/java/com/m1racle/reader/share/ZipExternalOpenContractTest.kt) | [ZipExternalOpenContractTest](../../../app/src/test/java/com/m1racle/reader/share/ZipExternalOpenContractTest.kt#L64) · [StubContentProvider](../../../app/src/test/java/com/m1racle/reader/share/ZipExternalOpenContractTest.kt#L91) · [ZbRecordingDocumentDao](../../../app/src/test/java/com/m1racle/reader/share/ZipExternalOpenContractTest.kt#L280) · [ZbUnusedPdfPrintPageAnchorDao](../../../app/src/test/java/com/m1racle/reader/share/ZipExternalOpenContractTest.kt#L391) · [ZbUnusedTextbookOutlineNodeDao](../../../app/src/test/java/com/m1racle/reader/share/ZipExternalOpenContractTest.kt#L405) | 6 |

### `com.m1racle.reader.testing`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [ImportTestFakes.kt](../../../app/src/test/java/com/m1racle/reader/testing/ImportTestFakes.kt) | [TestDocumentAliasDao](../../../app/src/test/java/com/m1racle/reader/testing/ImportTestFakes.kt#L8) · [ImmediateRepositoryTransactionRunner](../../../app/src/test/java/com/m1racle/reader/testing/ImportTestFakes.kt#L52) | 0 |
| [ProductionSource.kt](../../../app/src/test/java/com/m1racle/reader/testing/ProductionSource.kt) | [ProductionSource](../../../app/src/test/java/com/m1racle/reader/testing/ProductionSource.kt#L16) | 0 |
| [TestSearchIndexDao.kt](../../../app/src/test/java/com/m1racle/reader/testing/TestSearchIndexDao.kt) | [TestSearchIndexDao](../../../app/src/test/java/com/m1racle/reader/testing/TestSearchIndexDao.kt#L10) · [Key](../../../app/src/test/java/com/m1racle/reader/testing/TestSearchIndexDao.kt#L11) | 0 |

### `com.m1racle.reader.ui.annotation`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [AnnotationCreationFlowTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/annotation/AnnotationCreationFlowTest.kt) | [AnnotationCreationFlowTest](../../../app/src/test/java/com/m1racle/reader/ui/annotation/AnnotationCreationFlowTest.kt#L24) · [FakeHighlightDao](../../../app/src/test/java/com/m1racle/reader/ui/annotation/AnnotationCreationFlowTest.kt#L26) · [FakeNoteDao](../../../app/src/test/java/com/m1racle/reader/ui/annotation/AnnotationCreationFlowTest.kt#L52) · [NoBookmarks](../../../app/src/test/java/com/m1racle/reader/ui/annotation/AnnotationCreationFlowTest.kt#L83) · [NoFavorites](../../../app/src/test/java/com/m1racle/reader/ui/annotation/AnnotationCreationFlowTest.kt#L95) | 5 |
| [AnnotationOverlayContractTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/annotation/AnnotationOverlayContractTest.kt) | [AnnotationOverlayContractTest](../../../app/src/test/java/com/m1racle/reader/ui/annotation/AnnotationOverlayContractTest.kt#L26) · [FakeHighlightDao](../../../app/src/test/java/com/m1racle/reader/ui/annotation/AnnotationOverlayContractTest.kt#L28) · [FakeNoteDao](../../../app/src/test/java/com/m1racle/reader/ui/annotation/AnnotationOverlayContractTest.kt#L48) · [NoBookmarks](../../../app/src/test/java/com/m1racle/reader/ui/annotation/AnnotationOverlayContractTest.kt#L71) · [NoFavorites](../../../app/src/test/java/com/m1racle/reader/ui/annotation/AnnotationOverlayContractTest.kt#L83) | 2 |
| [AnnotationPerformanceContractTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/annotation/AnnotationPerformanceContractTest.kt) | [AnnotationPerformanceContractTest](../../../app/src/test/java/com/m1racle/reader/ui/annotation/AnnotationPerformanceContractTest.kt#L26) · [CountingHighlightDao](../../../app/src/test/java/com/m1racle/reader/ui/annotation/AnnotationPerformanceContractTest.kt#L28) · [CountingNoteDao](../../../app/src/test/java/com/m1racle/reader/ui/annotation/AnnotationPerformanceContractTest.kt#L53) · [NoBookmarks](../../../app/src/test/java/com/m1racle/reader/ui/annotation/AnnotationPerformanceContractTest.kt#L189) · [NoFavorites](../../../app/src/test/java/com/m1racle/reader/ui/annotation/AnnotationPerformanceContractTest.kt#L200) | 3 |
| [SelectionAnchorConversionTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/annotation/SelectionAnchorConversionTest.kt) | [SelectionAnchorConversionTest](../../../app/src/test/java/com/m1racle/reader/ui/annotation/SelectionAnchorConversionTest.kt#L13) | 5 |

### `com.m1racle.reader.ui.epub`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [EpubPaginationTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/epub/EpubPaginationTest.kt) | [EpubPaginationTest](../../../app/src/test/java/com/m1racle/reader/ui/epub/EpubPaginationTest.kt#L7) | 19 |
| [EpubRowIndexTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/epub/EpubRowIndexTest.kt) | [EpubRowIndexTest](../../../app/src/test/java/com/m1racle/reader/ui/epub/EpubRowIndexTest.kt#L26) | 2 |
| [EpubTextLayoutTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/epub/EpubTextLayoutTest.kt) | [EpubTextLayoutTest](../../../app/src/test/java/com/m1racle/reader/ui/epub/EpubTextLayoutTest.kt#L9) | 8 |

### `com.m1racle.reader.ui.home`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [HomeImportEventTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/home/HomeImportEventTest.kt) | [HomeImportEventTest](../../../app/src/test/java/com/m1racle/reader/ui/home/HomeImportEventTest.kt#L10) | 2 |
| [HomeLayoutContractTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/home/HomeLayoutContractTest.kt) | [HomeLayoutContractTest](../../../app/src/test/java/com/m1racle/reader/ui/home/HomeLayoutContractTest.kt#L14) | 12 |
| [HomeListUpdateTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/home/HomeListUpdateTest.kt) | [HomeListUpdateTest](../../../app/src/test/java/com/m1racle/reader/ui/home/HomeListUpdateTest.kt#L12) | 6 |
| [HomeOperationsTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/home/HomeOperationsTest.kt) | [HomeOperationsTest](../../../app/src/test/java/com/m1racle/reader/ui/home/HomeOperationsTest.kt#L42) | 2 |
| [HomeViewModelTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/home/HomeViewModelTest.kt) | [HomeViewModelTest](../../../app/src/test/java/com/m1racle/reader/ui/home/HomeViewModelTest.kt#L14) | 10 |

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
| [GlassBackdropTopologyContractTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/material/GlassBackdropTopologyContractTest.kt) | [GlassBackdropTopologyContractTest](../../../app/src/test/java/com/m1racle/reader/ui/material/GlassBackdropTopologyContractTest.kt#L11) · [TrailingLambda](../../../app/src/test/java/com/m1racle/reader/ui/material/GlassBackdropTopologyContractTest.kt#L182) | 11 |
| [GlassInteractionContractTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/material/GlassInteractionContractTest.kt) | [GlassInteractionContractTest](../../../app/src/test/java/com/m1racle/reader/ui/material/GlassInteractionContractTest.kt#L17) | 13 |
| [GlassMaterialRegressionTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/material/GlassMaterialRegressionTest.kt) | [GlassMaterialRegressionTest](../../../app/src/test/java/com/m1racle/reader/ui/material/GlassMaterialRegressionTest.kt#L26) | 13 |
| [GlassOpticalPolicyTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/material/GlassOpticalPolicyTest.kt) | [GlassOpticalPolicyTest](../../../app/src/test/java/com/m1racle/reader/ui/material/GlassOpticalPolicyTest.kt#L8) | 5 |
| [LiquidGlassStyleTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/material/LiquidGlassStyleTest.kt) | [LiquidGlassStyleTest](../../../app/src/test/java/com/m1racle/reader/ui/material/LiquidGlassStyleTest.kt#L7) | 7 |
| [QaGlassVariantTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/material/QaGlassVariantTest.kt) | [QaGlassVariantTest](../../../app/src/test/java/com/m1racle/reader/ui/material/QaGlassVariantTest.kt#L11) | 11 |
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
| [ExportStateGateTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/pdf/ExportStateGateTest.kt) | [ExportStateGateTest](../../../app/src/test/java/com/m1racle/reader/ui/pdf/ExportStateGateTest.kt#L16) | 4 |
| [R25ReaderFoundationTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/pdf/R25ReaderFoundationTest.kt) | [R25ReaderFoundationTest](../../../app/src/test/java/com/m1racle/reader/ui/pdf/R25ReaderFoundationTest.kt#L15) | 9 |
| [ReaderChromeMaterialTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/pdf/ReaderChromeMaterialTest.kt) | [ReaderChromeMaterialTest](../../../app/src/test/java/com/m1racle/reader/ui/pdf/ReaderChromeMaterialTest.kt#L18) | 11 |
| [ReaderChromeTokensContractTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/pdf/ReaderChromeTokensContractTest.kt) | [ReaderChromeTokensContractTest](../../../app/src/test/java/com/m1racle/reader/ui/pdf/ReaderChromeTokensContractTest.kt#L14) | 10 |
| [ReaderMoreStateMachineTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/pdf/ReaderMoreStateMachineTest.kt) | [ReaderMoreStateMachineTest](../../../app/src/test/java/com/m1racle/reader/ui/pdf/ReaderMoreStateMachineTest.kt#L12) | 14 |
| [ReaderMoreTransitionsTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/pdf/ReaderMoreTransitionsTest.kt) | [ReaderMoreTransitionsTest](../../../app/src/test/java/com/m1racle/reader/ui/pdf/ReaderMoreTransitionsTest.kt#L15) | 7 |
| [ViewModelClearOrderEvidenceTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/pdf/ViewModelClearOrderEvidenceTest.kt) | [ViewModelClearOrderEvidenceTest](../../../app/src/test/java/com/m1racle/reader/ui/pdf/ViewModelClearOrderEvidenceTest.kt#L62) | 5 |

### `com.m1racle.reader.ui.reader`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [AnnotationManagementTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/reader/AnnotationManagementTest.kt) | [AnnotationManagementTest](../../../app/src/test/java/com/m1racle/reader/ui/reader/AnnotationManagementTest.kt#L28) · [FakeHighlightDao](../../../app/src/test/java/com/m1racle/reader/ui/reader/AnnotationManagementTest.kt#L30) · [FakeNoteDao](../../../app/src/test/java/com/m1racle/reader/ui/reader/AnnotationManagementTest.kt#L58) · [NoBookmarks](../../../app/src/test/java/com/m1racle/reader/ui/reader/AnnotationManagementTest.kt#L74) · [NoFavorites](../../../app/src/test/java/com/m1racle/reader/ui/reader/AnnotationManagementTest.kt#L86) | 4 |
| [NoteFoundationAuthorityTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/reader/NoteFoundationAuthorityTest.kt) | [NoteFoundationAuthorityTest](../../../app/src/test/java/com/m1racle/reader/ui/reader/NoteFoundationAuthorityTest.kt#L10) | 2 |
| [PdfHighlightLoopTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/reader/PdfHighlightLoopTest.kt) | [PdfHighlightLoopTest](../../../app/src/test/java/com/m1racle/reader/ui/reader/PdfHighlightLoopTest.kt#L32) · [FakeHighlightDao](../../../app/src/test/java/com/m1racle/reader/ui/reader/PdfHighlightLoopTest.kt#L34) · [FakeNoteDao](../../../app/src/test/java/com/m1racle/reader/ui/reader/PdfHighlightLoopTest.kt#L60) · [NoBookmarks](../../../app/src/test/java/com/m1racle/reader/ui/reader/PdfHighlightLoopTest.kt#L78) · [NoFavorites](../../../app/src/test/java/com/m1racle/reader/ui/reader/PdfHighlightLoopTest.kt#L90) | 5 |
| [ReaderBookmarkControllerTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/reader/ReaderBookmarkControllerTest.kt) | [ReaderBookmarkControllerTest](../../../app/src/test/java/com/m1racle/reader/ui/reader/ReaderBookmarkControllerTest.kt#L28) · [FakeBookmarkDao](../../../app/src/test/java/com/m1racle/reader/ui/reader/ReaderBookmarkControllerTest.kt#L31) · [NoFavorites](../../../app/src/test/java/com/m1racle/reader/ui/reader/ReaderBookmarkControllerTest.kt#L79) · [NoHighlights](../../../app/src/test/java/com/m1racle/reader/ui/reader/ReaderBookmarkControllerTest.kt#L89) · [NoNotes](../../../app/src/test/java/com/m1racle/reader/ui/reader/ReaderBookmarkControllerTest.kt#L102) | 5 |
| [ReaderMarkdownAnnotationTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/reader/ReaderMarkdownAnnotationTest.kt) | [ReaderMarkdownAnnotationTest](../../../app/src/test/java/com/m1racle/reader/ui/reader/ReaderMarkdownAnnotationTest.kt#L31) · [FakeHighlightDao](../../../app/src/test/java/com/m1racle/reader/ui/reader/ReaderMarkdownAnnotationTest.kt#L33) · [FakeNoteDao](../../../app/src/test/java/com/m1racle/reader/ui/reader/ReaderMarkdownAnnotationTest.kt#L62) · [NoBookmarks](../../../app/src/test/java/com/m1racle/reader/ui/reader/ReaderMarkdownAnnotationTest.kt#L101) · [NoFavorites](../../../app/src/test/java/com/m1racle/reader/ui/reader/ReaderMarkdownAnnotationTest.kt#L117) | 6 |
| [ReaderNoteControllerTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/reader/ReaderNoteControllerTest.kt) | [ReaderNoteControllerTest](../../../app/src/test/java/com/m1racle/reader/ui/reader/ReaderNoteControllerTest.kt#L31) · [FakeHighlightDao](../../../app/src/test/java/com/m1racle/reader/ui/reader/ReaderNoteControllerTest.kt#L33) · [FakeNoteDao](../../../app/src/test/java/com/m1racle/reader/ui/reader/ReaderNoteControllerTest.kt#L60) · [NoBookmarks](../../../app/src/test/java/com/m1racle/reader/ui/reader/ReaderNoteControllerTest.kt#L94) · [NoFavorites](../../../app/src/test/java/com/m1racle/reader/ui/reader/ReaderNoteControllerTest.kt#L106) · [Harness](../../../app/src/test/java/com/m1racle/reader/ui/reader/ReaderNoteControllerTest.kt#L115) | 6 |
| [ReaderProgressWheelTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/reader/ReaderProgressWheelTest.kt) | [ReaderProgressWheelTest](../../../app/src/test/java/com/m1racle/reader/ui/reader/ReaderProgressWheelTest.kt#L8) | 1 |
| [ReadingAssetsAuthorityTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/reader/ReadingAssetsAuthorityTest.kt) | [ReadingAssetsAuthorityTest](../../../app/src/test/java/com/m1racle/reader/ui/reader/ReadingAssetsAuthorityTest.kt#L13) | 3 |

### `com.m1racle.reader.ui.theme`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [M1racleMotionContractTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/theme/M1racleMotionContractTest.kt) | [M1racleMotionContractTest](../../../app/src/test/java/com/m1racle/reader/ui/theme/M1racleMotionContractTest.kt#L10) | 5 |
| [M1racleTokensContractTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/theme/M1racleTokensContractTest.kt) | [M1racleTokensContractTest](../../../app/src/test/java/com/m1racle/reader/ui/theme/M1racleTokensContractTest.kt#L9) | 7 |

### `com.m1racle.reader.ui.viewer`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [DocumentSearchUiStateTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/viewer/DocumentSearchUiStateTest.kt) | [DocumentSearchUiStateTest](../../../app/src/test/java/com/m1racle/reader/ui/viewer/DocumentSearchUiStateTest.kt#L13) | 5 |
| [MarkdownAnchorNavigationTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/viewer/MarkdownAnchorNavigationTest.kt) | [MarkdownAnchorNavigationTest](../../../app/src/test/java/com/m1racle/reader/ui/viewer/MarkdownAnchorNavigationTest.kt#L6) | 1 |
| [ReaderTopBarScrollStateTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/viewer/ReaderTopBarScrollStateTest.kt) | [ReaderTopBarScrollStateTest](../../../app/src/test/java/com/m1racle/reader/ui/viewer/ReaderTopBarScrollStateTest.kt#L15) | 25 |
| [ReadingPositionSaverTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/viewer/ReadingPositionSaverTest.kt) | [ReadingPositionSaverTest](../../../app/src/test/java/com/m1racle/reader/ui/viewer/ReadingPositionSaverTest.kt#L22) · [Harness](../../../app/src/test/java/com/m1racle/reader/ui/viewer/ReadingPositionSaverTest.kt#L24) | 9 |

### `com.m1racle.reader.ui.viewer.components`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [HeadingAnchorPlacementTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/viewer/components/HeadingAnchorPlacementTest.kt) | [HeadingAnchorPlacementTest](../../../app/src/test/java/com/m1racle/reader/ui/viewer/components/HeadingAnchorPlacementTest.kt#L7) | 1 |
| [MarkdownTableLayoutEngineTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/viewer/components/MarkdownTableLayoutEngineTest.kt) | [MarkdownTableLayoutEngineTest](../../../app/src/test/java/com/m1racle/reader/ui/viewer/components/MarkdownTableLayoutEngineTest.kt#L17) | 34 |
| [ReaderPositionCaptionTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/viewer/components/ReaderPositionCaptionTest.kt) | [ReaderPositionCaptionTest](../../../app/src/test/java/com/m1racle/reader/ui/viewer/components/ReaderPositionCaptionTest.kt#L6) | 3 |
| [TableHeaderBodyAlignmentTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/viewer/components/TableHeaderBodyAlignmentTest.kt) | [TableHeaderBodyAlignmentTest](../../../app/src/test/java/com/m1racle/reader/ui/viewer/components/TableHeaderBodyAlignmentTest.kt#L17) | 12 |
| [TableHeaderParityTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/viewer/components/TableHeaderParityTest.kt) | [TableHeaderParityTest](../../../app/src/test/java/com/m1racle/reader/ui/viewer/components/TableHeaderParityTest.kt#L21) | 3 |
| [TableLayoutCacheTest.kt](../../../app/src/test/java/com/m1racle/reader/ui/viewer/components/TableLayoutCacheTest.kt) | [TableLayoutCacheTest](../../../app/src/test/java/com/m1racle/reader/ui/viewer/components/TableLayoutCacheTest.kt#L9) | 2 |

## `app/src/testQa`

26 个 Kotlin 文件，144 个 `@Test` 标记。

### `com.m1racle.reader.ui.annotation`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [PdfTextHighlightGestureTest.kt](../../../app/src/testQa/java/com/m1racle/reader/ui/annotation/PdfTextHighlightGestureTest.kt) | [PdfTextHighlightGestureTest](../../../app/src/testQa/java/com/m1racle/reader/ui/annotation/PdfTextHighlightGestureTest.kt#L28) | 7 |

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
| [EpubPaginationHostTest.kt](../../../app/src/testQa/java/com/m1racle/reader/ui/epub/EpubPaginationHostTest.kt) | [EpubPaginationHostTest](../../../app/src/testQa/java/com/m1racle/reader/ui/epub/EpubPaginationHostTest.kt#L39) | 11 |
| [EpubReaderUiTest.kt](../../../app/src/testQa/java/com/m1racle/reader/ui/epub/EpubReaderUiTest.kt) | [EpubReaderUiTest](../../../app/src/testQa/java/com/m1racle/reader/ui/epub/EpubReaderUiTest.kt#L30) | 11 |
| [EpubReferenceFidelityHostTest.kt](../../../app/src/testQa/java/com/m1racle/reader/ui/epub/EpubReferenceFidelityHostTest.kt) | [EpubReferenceFidelityHostTest](../../../app/src/testQa/java/com/m1racle/reader/ui/epub/EpubReferenceFidelityHostTest.kt#L41) | 11 |
| [EpubReleaseHandoffHostTest.kt](../../../app/src/testQa/java/com/m1racle/reader/ui/epub/EpubReleaseHandoffHostTest.kt) | [EpubReleaseHandoffHostTest](../../../app/src/testQa/java/com/m1racle/reader/ui/epub/EpubReleaseHandoffHostTest.kt#L35) | 3 |

### `com.m1racle.reader.ui.home`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [HomeDocumentActionsUiTest.kt](../../../app/src/testQa/java/com/m1racle/reader/ui/home/HomeDocumentActionsUiTest.kt) | [HomeDocumentActionsUiTest](../../../app/src/testQa/java/com/m1racle/reader/ui/home/HomeDocumentActionsUiTest.kt#L27) | 3 |
| [HomeReturnInsetsHostTest.kt](../../../app/src/testQa/java/com/m1racle/reader/ui/home/HomeReturnInsetsHostTest.kt) | [HomeReturnInsetsHostTest](../../../app/src/testQa/java/com/m1racle/reader/ui/home/HomeReturnInsetsHostTest.kt#L23) | 1 |
| [HomeViewportUiTest.kt](../../../app/src/testQa/java/com/m1racle/reader/ui/home/HomeViewportUiTest.kt) | [HomeViewportUiTest](../../../app/src/testQa/java/com/m1racle/reader/ui/home/HomeViewportUiTest.kt#L34) | 4 |

### `com.m1racle.reader.ui.hub`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [BackgroundPositionSliderTest.kt](../../../app/src/testQa/java/com/m1racle/reader/ui/hub/BackgroundPositionSliderTest.kt) | [BackgroundPositionSliderTest](../../../app/src/testQa/java/com/m1racle/reader/ui/hub/BackgroundPositionSliderTest.kt#L25) | 3 |
| [FloatingDockSurfaceTest.kt](../../../app/src/testQa/java/com/m1racle/reader/ui/hub/FloatingDockSurfaceTest.kt) | [FloatingDockSurfaceTest](../../../app/src/testQa/java/com/m1racle/reader/ui/hub/FloatingDockSurfaceTest.kt#L32) | 1 |
| [GlassDockUiTest.kt](../../../app/src/testQa/java/com/m1racle/reader/ui/hub/GlassDockUiTest.kt) | [GlassDockUiTest](../../../app/src/testQa/java/com/m1racle/reader/ui/hub/GlassDockUiTest.kt#L42) | 22 |
| [HubSettingsScreenTest.kt](../../../app/src/testQa/java/com/m1racle/reader/ui/hub/HubSettingsScreenTest.kt) | [HubSettingsScreenTest](../../../app/src/testQa/java/com/m1racle/reader/ui/hub/HubSettingsScreenTest.kt#L21) | 2 |

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
| [ReaderMoreHostUiTest.kt](../../../app/src/testQa/java/com/m1racle/reader/ui/pdf/ReaderMoreHostUiTest.kt) | [ReaderMoreHostUiTest](../../../app/src/testQa/java/com/m1racle/reader/ui/pdf/ReaderMoreHostUiTest.kt#L56) | 19 |

### `com.m1racle.reader.ui.pdf.edit`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [PdfEditorSaveOptionsTest.kt](../../../app/src/testQa/java/com/m1racle/reader/ui/pdf/edit/PdfEditorSaveOptionsTest.kt) | [PdfEditorSaveOptionsTest](../../../app/src/testQa/java/com/m1racle/reader/ui/pdf/edit/PdfEditorSaveOptionsTest.kt#L21) | 2 |

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

23 个 Kotlin 文件，139 个 `@Test` 标记。

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
| [DeleteByIdTransactionTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/data/repository/DeleteByIdTransactionTest.kt) | [DeleteByIdTransactionTest](../../../app/src/androidTest/java/com/m1racle/reader/data/repository/DeleteByIdTransactionTest.kt#L38) · [FailingOutlineDao](../../../app/src/androidTest/java/com/m1racle/reader/data/repository/DeleteByIdTransactionTest.kt#L107) · [FailingAnchorDao](../../../app/src/androidTest/java/com/m1racle/reader/data/repository/DeleteByIdTransactionTest.kt#L127) · [FailingDocumentDao](../../../app/src/androidTest/java/com/m1racle/reader/data/repository/DeleteByIdTransactionTest.kt#L143) | 5 |
| [UpdatePdfProgressSnapshotAtomicTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/data/repository/UpdatePdfProgressSnapshotAtomicTest.kt) | [UpdatePdfProgressSnapshotAtomicTest](../../../app/src/androidTest/java/com/m1racle/reader/data/repository/UpdatePdfProgressSnapshotAtomicTest.kt#L35) · [FailingViewportSessionDao](../../../app/src/androidTest/java/com/m1racle/reader/data/repository/UpdatePdfProgressSnapshotAtomicTest.kt#L333) · [FailingPositionDao](../../../app/src/androidTest/java/com/m1racle/reader/data/repository/UpdatePdfProgressSnapshotAtomicTest.kt#L356) | 6 |

### `com.m1racle.reader.pdf`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [R7P0c12TilePixelCompositionInstrumentedTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/pdf/R7P0c12TilePixelCompositionInstrumentedTest.kt) | [R7P0c12TilePixelCompositionInstrumentedTest](../../../app/src/androidTest/java/com/m1racle/reader/pdf/R7P0c12TilePixelCompositionInstrumentedTest.kt#L25) | 1 |

### `com.m1racle.reader.ui.epub`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [EpubNativeFontInstrumentedTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/ui/epub/EpubNativeFontInstrumentedTest.kt) | [EpubNativeFontInstrumentedTest](../../../app/src/androidTest/java/com/m1racle/reader/ui/epub/EpubNativeFontInstrumentedTest.kt#L34) | 3 |
| [ReaderFontInstrumentedTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/ui/epub/ReaderFontInstrumentedTest.kt) | [ReaderFontInstrumentedTest](../../../app/src/androidTest/java/com/m1racle/reader/ui/epub/ReaderFontInstrumentedTest.kt#L19) | 1 |

### `com.m1racle.reader.ui.hub`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [GlassDockPixelsInstrumentedTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/ui/hub/GlassDockPixelsInstrumentedTest.kt) | [GlassDockPixelsInstrumentedTest](../../../app/src/androidTest/java/com/m1racle/reader/ui/hub/GlassDockPixelsInstrumentedTest.kt#L60) | 35 |
| [HubNavigationMotionInstrumentedTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/ui/hub/HubNavigationMotionInstrumentedTest.kt) | [HubNavigationMotionInstrumentedTest](../../../app/src/androidTest/java/com/m1racle/reader/ui/hub/HubNavigationMotionInstrumentedTest.kt#L13) | 1 |

### `com.m1racle.reader.ui.pdf`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [ExternalOpenSingleSessionInstrumentedTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/ExternalOpenSingleSessionInstrumentedTest.kt) | [ExternalOpenSingleSessionInstrumentedTest](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/ExternalOpenSingleSessionInstrumentedTest.kt#L27) | 11 |
| [H1QaMotionEventTrace.kt](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/H1QaMotionEventTrace.kt) | [H1NormalizedPointer](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/H1QaMotionEventTrace.kt#L9) · [H1QaMotionEventTrace](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/H1QaMotionEventTrace.kt#L16) | 0 |
| [PdfSystemScrollCaptureInstrumentedTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/PdfSystemScrollCaptureInstrumentedTest.kt) | [PdfSystemScrollCaptureInstrumentedTest](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/PdfSystemScrollCaptureInstrumentedTest.kt#L25) | 1 |
| [R7GestureLifecycleInstrumentedTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/R7GestureLifecycleInstrumentedTest.kt) | [R7GestureLifecycleInstrumentedTest](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/R7GestureLifecycleInstrumentedTest.kt#L53) · [DecoupledHarness](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/R7GestureLifecycleInstrumentedTest.kt#L467) · [R7ConfigurationRecreationInstrumentedTest](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/R7GestureLifecycleInstrumentedTest.kt#L589) | 19 |
| [R7H12AutomatedAcceptanceTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/R7H12AutomatedAcceptanceTest.kt) | [R7H12AutomatedAcceptanceTest](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/R7H12AutomatedAcceptanceTest.kt#L21) | 8 |
| [R7H13RealUiAcceptanceTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/R7H13RealUiAcceptanceTest.kt) | [R7H13RealUiAcceptanceTest](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/R7H13RealUiAcceptanceTest.kt#L16) | 1 |
| [R7H14RealMainActivityAcceptanceTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/R7H14RealMainActivityAcceptanceTest.kt) | [R7H14RealMainActivityAcceptanceTest](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/R7H14RealMainActivityAcceptanceTest.kt#L21) | 2 |
| [R7H15ThreePageStripInstrumentedTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/R7H15ThreePageStripInstrumentedTest.kt) | [R7H15ThreePageStripInstrumentedTest](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/R7H15ThreePageStripInstrumentedTest.kt#L17) | 8 |
| [R7H15ThreePageStripRealUiTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/R7H15ThreePageStripRealUiTest.kt) | [R7H15ThreePageStripRealUiTest](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/R7H15ThreePageStripRealUiTest.kt#L23) | 4 |
| [R7P0c11H1InstrumentedTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/R7P0c11H1InstrumentedTest.kt) | [R7P0c11H1InstrumentedTest](../../../app/src/androidTest/java/com/m1racle/reader/ui/pdf/R7P0c11H1InstrumentedTest.kt#L20) | 14 |

### `com.m1racle.reader.ui.viewer`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [MarkdownReadingBacklogInstrumentedTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/ui/viewer/MarkdownReadingBacklogInstrumentedTest.kt) | [MarkdownReadingBacklogInstrumentedTest](../../../app/src/androidTest/java/com/m1racle/reader/ui/viewer/MarkdownReadingBacklogInstrumentedTest.kt#L38) | 6 |
| [ReaderProgressiveChromeInstrumentedTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/ui/viewer/ReaderProgressiveChromeInstrumentedTest.kt) | [ReaderProgressiveChromeInstrumentedTest](../../../app/src/androidTest/java/com/m1racle/reader/ui/viewer/ReaderProgressiveChromeInstrumentedTest.kt#L55) | 5 |

### `com.m1racle.reader.ui.viewer.components`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [MarkdownResponsiveTableInstrumentedTest.kt](../../../app/src/androidTest/java/com/m1racle/reader/ui/viewer/components/MarkdownResponsiveTableInstrumentedTest.kt) | [MarkdownResponsiveTableInstrumentedTest](../../../app/src/androidTest/java/com/m1racle/reader/ui/viewer/components/MarkdownResponsiveTableInstrumentedTest.kt#L28) | 5 |

## `benchmark/src`

1 个 Kotlin 文件，2 个 `@Test` 标记。

### `com.m1racle.reader.benchmark`

| 文件 | 类型声明 | @Test 标记数 |
|---|---|---:|
| [ReaderScrollBenchmark.kt](../../../benchmark/src/main/java/com/m1racle/reader/benchmark/ReaderScrollBenchmark.kt) | [ReaderScrollBenchmark](../../../benchmark/src/main/java/com/m1racle/reader/benchmark/ReaderScrollBenchmark.kt#L29) | 2 |

