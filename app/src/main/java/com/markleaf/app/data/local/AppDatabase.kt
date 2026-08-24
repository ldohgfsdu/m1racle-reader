package com.markleaf.app.data.local

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
    ],
    version = 7,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun documentDao(): DocumentDao
    abstract fun pdfPrintPageAnchorDao(): PdfPrintPageAnchorDao
    abstract fun textbookOutlineNodeDao(): TextbookOutlineNodeDao

    companion object {
        private const val DB_NAME = "markleaf.db"

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
