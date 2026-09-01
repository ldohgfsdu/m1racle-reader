package com.m1racle.reader.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DocumentEntity::class,
        PdfPrintPageAnchorEntity::class,
        TextbookOutlineNodeEntity::class,
        BookmarkEntity::class,
        FavoriteDocumentEntity::class,
        HighlightEntity::class,
        NoteEntity::class,
        SearchIndexEntity::class,
        DocumentAliasEntity::class,
    ],
    version = 13,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun documentDao(): DocumentDao
    abstract fun pdfPrintPageAnchorDao(): PdfPrintPageAnchorDao
    abstract fun textbookOutlineNodeDao(): TextbookOutlineNodeDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun favoriteDocumentDao(): FavoriteDocumentDao
    abstract fun highlightDao(): HighlightDao
    abstract fun noteDao(): NoteDao
    abstract fun searchIndexDao(): SearchIndexDao
    abstract fun documentAliasDao(): DocumentAliasDao

    companion object {
        private const val DB_NAME = "m1racle-reader.db"

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE documents ADD COLUMN documentType TEXT NOT NULL DEFAULT 'MARKDOWN'",
                )
                db.execSQL(
                    "ALTER TABLE documents ADD COLUMN pageCount INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pdf_print_page_anchors (
                        documentId TEXT NOT NULL,
                        pdfPageIndex INTEGER NOT NULL,
                        printPageNumber INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(documentId, pdfPageIndex),
                        FOREIGN KEY(documentId) REFERENCES documents(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_pdf_print_page_anchors_documentId " +
                        "ON pdf_print_page_anchors(documentId)",
                )
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS textbook_outline_nodes (
                        id TEXT NOT NULL,
                        documentId TEXT NOT NULL,
                        parentId TEXT,
                        type TEXT NOT NULL,
                        title TEXT NOT NULL,
                        startPageIndex INTEGER NOT NULL,
                        endPageIndex INTEGER NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(id),
                        FOREIGN KEY(documentId) REFERENCES documents(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_textbook_outline_nodes_documentId " +
                        "ON textbook_outline_nodes(documentId)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "index_textbook_outline_nodes_documentId_parentId " +
                        "ON textbook_outline_nodes(documentId, parentId)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "index_textbook_outline_nodes_documentId_startPageIndex_endPageIndex " +
                        "ON textbook_outline_nodes(documentId, startPageIndex, endPageIndex)",
                )
            }
        }

        /**
         * v4 → v5: outline provenance for embedded PDF import.
         */
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE textbook_outline_nodes ADD COLUMN source TEXT NOT NULL DEFAULT 'MANUAL'",
                )
                db.execSQL(
                    "ALTER TABLE textbook_outline_nodes ADD COLUMN importKey TEXT",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "index_textbook_outline_nodes_documentId_importKey " +
                        "ON textbook_outline_nodes(documentId, importKey)",
                )
            }
        }

        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE documents ADD COLUMN pdfViewportSessionVersion INTEGER")
                db.execSQL("ALTER TABLE documents ADD COLUMN pdfViewportReadingMode TEXT")
                db.execSQL("ALTER TABLE documents ADD COLUMN pdfViewportPageIndex INTEGER")
                db.execSQL("ALTER TABLE documents ADD COLUMN pdfPageAnchorXFraction REAL")
                db.execSQL("ALTER TABLE documents ADD COLUMN pdfPageAnchorYFraction REAL")
                db.execSQL("ALTER TABLE documents ADD COLUMN pdfViewportAnchorXFraction REAL")
                db.execSQL("ALTER TABLE documents ADD COLUMN pdfViewportAnchorYFraction REAL")
                db.execSQL("ALTER TABLE documents ADD COLUMN pdfViewportScale REAL")
                db.execSQL("ALTER TABLE documents ADD COLUMN pdfViewportGeneration INTEGER")
            }
        }

        /**
         * v6 -> v7: reserve durable source-byte identity on documents.
         *
         * Existing rows intentionally remain NULL. SQLite UNIQUE indices allow
         * multiple NULL values, while preventing duplicate known hashes.
         */
        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MIGRATION_6_7_ADD_CONTENT_SHA_SQL)
                db.execSQL(MIGRATION_6_7_CREATE_CONTENT_SHA_INDEX_SQL)
            }
        }

        internal const val MIGRATION_6_7_ADD_CONTENT_SHA_SQL =
            "ALTER TABLE documents ADD COLUMN contentSha256 TEXT"

        internal const val MIGRATION_6_7_CREATE_CONTENT_SHA_INDEX_SQL =
            "CREATE UNIQUE INDEX IF NOT EXISTS index_documents_contentSha256 " +
                "ON documents(contentSha256)"

        /**
         * v7 -> v8: Reading Library foundation (H2.1) — additive tables only.
         *
         * All three tables hang off documents(id) with CASCADE so deleting a
         * document record removes its derived hub data in one step. SQL lives
         * in constants because the host JVM suite executes the exact statements
         * against real SQLite (MigrationV7ToV8Test), mirroring MIGRATION_6_7.
         */
        val MIGRATION_7_8: Migration = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MIGRATION_7_8_CREATE_READING_STATES_SQL)
                db.execSQL(MIGRATION_7_8_INDEX_READING_STATES_TIME_SQL)
                db.execSQL(MIGRATION_7_8_INDEX_READING_STATES_DOC_SQL)
                db.execSQL(MIGRATION_7_8_CREATE_BOOKMARKS_SQL)
                db.execSQL(MIGRATION_7_8_INDEX_BOOKMARKS_DOC_SQL)
                db.execSQL(MIGRATION_7_8_CREATE_FAVORITES_SQL)
                db.execSQL(MIGRATION_7_8_INDEX_FAVORITES_TIME_SQL)
            }
        }

        /** v7→v8 历史链路：H2.1 初版建表（reading_states 后于 v9 删除）。SQL 冻结保留。 */
        internal val MIGRATION_7_8_CREATE_READING_STATES_SQL =
            """
            CREATE TABLE IF NOT EXISTS reading_states (
                documentId TEXT NOT NULL,
                lastPosition INTEGER NOT NULL,
                progress REAL NOT NULL,
                lastOpenedTime INTEGER NOT NULL,
                PRIMARY KEY(documentId),
                FOREIGN KEY(documentId) REFERENCES documents(id) ON DELETE CASCADE
            )
            """.trimIndent()

        internal const val MIGRATION_7_8_INDEX_READING_STATES_TIME_SQL =
            "CREATE INDEX IF NOT EXISTS index_reading_states_lastOpenedTime " +
                "ON reading_states(lastOpenedTime)"

        internal const val MIGRATION_7_8_INDEX_READING_STATES_DOC_SQL =
            "CREATE INDEX IF NOT EXISTS index_reading_states_documentId " +
                "ON reading_states(documentId)"

        internal val MIGRATION_7_8_CREATE_BOOKMARKS_SQL =
            """
            CREATE TABLE IF NOT EXISTS bookmarks (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                documentId TEXT NOT NULL,
                page INTEGER NOT NULL,
                `offset` INTEGER,
                title TEXT,
                note TEXT,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(documentId) REFERENCES documents(id) ON DELETE CASCADE
            )
            """.trimIndent()

        internal const val MIGRATION_7_8_INDEX_BOOKMARKS_DOC_SQL =
            "CREATE INDEX IF NOT EXISTS index_bookmarks_documentId " +
                "ON bookmarks(documentId)"

        internal val MIGRATION_7_8_CREATE_FAVORITES_SQL =
            """
            CREATE TABLE IF NOT EXISTS favorite_documents (
                documentId TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                PRIMARY KEY(documentId),
                FOREIGN KEY(documentId) REFERENCES documents(id) ON DELETE CASCADE
            )
            """.trimIndent()

        internal const val MIGRATION_7_8_INDEX_FAVORITES_TIME_SQL =
            "CREATE INDEX IF NOT EXISTS index_favorite_documents_createdAt " +
                "ON favorite_documents(createdAt)"

        /**
         * v8 -> v9: reading-model authority closure (H2.1).
         *
         * - reading_states was a coarse duplicate of the documents reading
         *   field family with zero writers/consumers; the authority is the
         *   documents family (single writer: DocumentRepository). The table
         *   is dropped outright — no projection store, no dual write.
         * - bookmarks lose their PDF-leaning `page` column in favour of a
         *   document-neutral location pair (locationKind + location). Legacy
         *   rows map to PAGE_INDEX. Portable rebuild (create→copy→drop→rename)
         *   keeps compatibility with framework SQLite versions.
         */
        val MIGRATION_8_9: Migration = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MIGRATION_8_9_DROP_READING_STATES_SQL)
                db.execSQL(MIGRATION_8_9_CREATE_BOOKMARKS_V2_SQL)
                db.execSQL(MIGRATION_8_9_COPY_BOOKMARKS_SQL)
                db.execSQL(MIGRATION_8_9_DROP_OLD_BOOKMARKS_SQL)
                db.execSQL(MIGRATION_8_9_RENAME_BOOKMARKS_SQL)
                db.execSQL(MIGRATION_8_9_BOOKMARKS_INDEX_SQL)
            }
        }

        internal const val MIGRATION_8_9_DROP_READING_STATES_SQL =
            "DROP TABLE IF EXISTS reading_states"

        internal val MIGRATION_8_9_CREATE_BOOKMARKS_V2_SQL =
            """
            CREATE TABLE IF NOT EXISTS bookmarks_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                documentId TEXT NOT NULL,
                locationKind TEXT NOT NULL,
                location INTEGER NOT NULL,
                `offset` INTEGER,
                title TEXT,
                note TEXT,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(documentId) REFERENCES documents(id) ON DELETE CASCADE
            )
            """.trimIndent()

        internal val MIGRATION_8_9_COPY_BOOKMARKS_SQL =
            """
            INSERT INTO bookmarks_new(id, documentId, locationKind, location, `offset`, title, note, createdAt)
            SELECT id, documentId, 'PAGE_INDEX', page, `offset`, title, note, createdAt FROM bookmarks
            """.trimIndent()

        internal const val MIGRATION_8_9_DROP_OLD_BOOKMARKS_SQL =
            "DROP TABLE bookmarks"

        internal const val MIGRATION_8_9_RENAME_BOOKMARKS_SQL =
            "ALTER TABLE bookmarks_new RENAME TO bookmarks"

        internal const val MIGRATION_8_9_BOOKMARKS_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS index_bookmarks_documentId " +
                "ON bookmarks(documentId)"

        /**
         * v9 -> v10: R23 Annotation Foundation — additive tables only.
         * highlights / notes share the bookmark location semantics
         * (locationKind + location) and cascade with their document.
         */
        val MIGRATION_9_10: Migration = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MIGRATION_9_10_CREATE_HIGHLIGHTS_SQL)
                db.execSQL(MIGRATION_9_10_HIGHLIGHTS_DOC_INDEX_SQL)
                db.execSQL(MIGRATION_9_10_HIGHLIGHTS_LOC_INDEX_SQL)
                db.execSQL(MIGRATION_9_10_CREATE_NOTES_SQL)
                db.execSQL(MIGRATION_9_10_NOTES_DOC_INDEX_SQL)
                db.execSQL(MIGRATION_9_10_NOTES_LOC_INDEX_SQL)
            }
        }

        internal val MIGRATION_9_10_CREATE_HIGHLIGHTS_SQL =
            """
            CREATE TABLE IF NOT EXISTS highlights (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                documentId TEXT NOT NULL,
                locationKind TEXT NOT NULL,
                location INTEGER NOT NULL,
                anchorStart INTEGER,
                anchorEnd INTEGER,
                excerpt TEXT,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(documentId) REFERENCES documents(id) ON DELETE CASCADE
            )
            """.trimIndent()

        internal const val MIGRATION_9_10_HIGHLIGHTS_DOC_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS index_highlights_documentId " +
                "ON highlights(documentId)"

        internal const val MIGRATION_9_10_HIGHLIGHTS_LOC_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS index_highlights_documentId_locationKind_location " +
                "ON highlights(documentId, locationKind, location)"

        internal val MIGRATION_9_10_CREATE_NOTES_SQL =
            """
            CREATE TABLE IF NOT EXISTS notes (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                documentId TEXT NOT NULL,
                locationKind TEXT NOT NULL,
                location INTEGER NOT NULL,
                anchorStart INTEGER,
                anchorEnd INTEGER,
                content TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                FOREIGN KEY(documentId) REFERENCES documents(id) ON DELETE CASCADE
            )
            """.trimIndent()

        internal const val MIGRATION_9_10_NOTES_DOC_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS index_notes_documentId " +
                "ON notes(documentId)"

        internal const val MIGRATION_9_10_NOTES_LOC_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS index_notes_documentId_locationKind_location " +
                "ON notes(documentId, locationKind, location)"

        /**
         * v10 -> v11: R24 Annotation Anchor Engine — annotations gain the
         * canonical `anchor` column (AnchorCodec form). kind/location stay as
         * write-time projected index columns; legacy rows keep a null anchor
         * and are synthesized on read by AnnotationAnchorResolver.
         */
        val MIGRATION_10_11: Migration = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MIGRATION_10_11_HIGHLIGHTS_ANCHOR_SQL)
                db.execSQL(MIGRATION_10_11_NOTES_ANCHOR_SQL)
            }
        }

        internal const val MIGRATION_10_11_HIGHLIGHTS_ANCHOR_SQL =
            "ALTER TABLE highlights ADD COLUMN anchor TEXT"

        internal const val MIGRATION_10_11_NOTES_ANCHOR_SQL =
            "ALTER TABLE notes ADD COLUMN anchor TEXT"

        /** v11 -> v12: R29 derived local-search index. */
        val MIGRATION_11_12: Migration = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MIGRATION_11_12_CREATE_SEARCH_INDEX_SQL)
                db.execSQL(MIGRATION_11_12_SEARCH_DOCUMENT_INDEX_SQL)
                db.execSQL(MIGRATION_11_12_SEARCH_TYPE_INDEX_SQL)
            }
        }

        /**
         * v12 -> v13: add location aliases and backfill only identity values
         * proven by the historical primary-key shape. Existing ids and child
         * rows are never rewritten or deleted.
         */
        val MIGRATION_12_13: Migration = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MIGRATION_12_13_CREATE_ALIASES_SQL)
                db.execSQL(MIGRATION_12_13_INDEX_ALIASES_DOC_SQL)
                db.query(MIGRATION_12_13_COLLISION_COUNT_SQL).use { cursor ->
                    cursor.moveToFirst()
                    val collisions = cursor.getLong(0)
                    if (collisions != 0L) {
                        throw IllegalStateException(
                            "contentSha256 backfill would collide ($collisions rows)",
                        )
                    }
                }
                db.execSQL(MIGRATION_12_13_BACKFILL_CONTENT_SHA_SQL)
                db.query(MIGRATION_12_13_BACKFILL_VALIDATE_SQL).use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0)
                        val hash = cursor.getString(1)
                        val suffix = id.substring(2)
                        if (hash == null || hash != suffix || !CONTENT_SHA256_HEX.matches(suffix)) {
                            throw IllegalStateException(
                                "invalid contentSha256 backfill for id=$id",
                            )
                        }
                    }
                }
                db.execSQL(MIGRATION_12_13_BACKFILL_LEGACY_URI_ALIASES_SQL)
            }
        }

        internal val MIGRATION_12_13_CREATE_ALIASES_SQL =
            """
            CREATE TABLE IF NOT EXISTS document_aliases (
                aliasKey TEXT NOT NULL,
                documentId TEXT NOT NULL,
                kind TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                lastSeenAt INTEGER NOT NULL,
                PRIMARY KEY(aliasKey),
                FOREIGN KEY(documentId) REFERENCES documents(id) ON DELETE CASCADE
            )
            """.trimIndent()

        internal const val MIGRATION_12_13_INDEX_ALIASES_DOC_SQL =
            "CREATE INDEX IF NOT EXISTS index_document_aliases_documentId " +
                "ON document_aliases(documentId)"

        internal val MIGRATION_12_13_COLLISION_COUNT_SQL =
            """
            SELECT COUNT(*) FROM documents AS a
            JOIN documents AS b
              ON lower(substr(a.id, 3)) = b.contentSha256
            WHERE a.id GLOB 'c:[0-9a-f][0-9a-f]*'
              AND length(a.id) = 66
              AND a.contentSha256 IS NULL
              AND b.contentSha256 IS NOT NULL
              AND a.id != b.id
            """.trimIndent()

        internal val MIGRATION_12_13_BACKFILL_CONTENT_SHA_SQL =
            """
            UPDATE documents
            SET contentSha256 = substr(id, 3)
            WHERE contentSha256 IS NULL
              AND length(id) = 66
              AND id GLOB 'c:[0-9a-f][0-9a-f]*'
            """.trimIndent()

        internal val MIGRATION_12_13_BACKFILL_VALIDATE_SQL =
            """
            SELECT id, contentSha256 FROM documents
            WHERE length(id) = 66
              AND substr(id, 1, 2) = 'c:'
            """.trimIndent()

        internal val MIGRATION_12_13_BACKFILL_LEGACY_URI_ALIASES_SQL =
            """
            INSERT INTO document_aliases (aliasKey, documentId, kind, createdAt, lastSeenAt)
            SELECT
              'legacy-raw-uri-v1:' || id,
              id,
              'legacy-raw-uri-v1',
              createdAt,
              lastOpenedAt
            FROM documents
            WHERE length(id) = 64
              AND id GLOB '[0-9a-f][0-9a-f]*'
            """.trimIndent()

        private val CONTENT_SHA256_HEX = Regex("^[0-9a-f]{64}$")

        internal val MIGRATION_11_12_CREATE_SEARCH_INDEX_SQL =
            """
            CREATE TABLE IF NOT EXISTS search_index (
                documentId TEXT NOT NULL,
                contentType TEXT NOT NULL,
                sourceId TEXT NOT NULL,
                content TEXT NOT NULL,
                locationKind TEXT NOT NULL,
                location INTEGER NOT NULL,
                anchor TEXT NOT NULL,
                indexedAt INTEGER NOT NULL,
                PRIMARY KEY(documentId, contentType, sourceId),
                FOREIGN KEY(documentId) REFERENCES documents(id) ON DELETE CASCADE
            )
            """.trimIndent()

        internal const val MIGRATION_11_12_SEARCH_DOCUMENT_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS index_search_index_documentId " +
                "ON search_index(documentId)"

        internal const val MIGRATION_11_12_SEARCH_TYPE_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS index_search_index_contentType " +
                "ON search_index(contentType)"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME,
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                        MIGRATION_11_12,
                        MIGRATION_12_13,
                    )
                    .build()
                    .also { instance = it }
            }
        }

        @Synchronized
        fun clearInstanceForTests() {
            instance = null
        }
    }
}
