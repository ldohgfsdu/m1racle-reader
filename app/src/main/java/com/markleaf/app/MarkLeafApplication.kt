package com.markleaf.app

import android.app.Application
import com.markleaf.app.data.local.AppDatabase
import com.markleaf.app.data.repository.DocumentRepository
import com.markleaf.app.data.settings.ReaderSettingsRepository

class MarkLeafApplication : Application() {

    val database: AppDatabase by lazy {
        AppDatabase.getInstance(this)
    }

    val documentRepository: DocumentRepository by lazy {
        DocumentRepository(
            context = this,
            documentDao = database.documentDao(),
        )
    }

    val readerSettingsRepository: ReaderSettingsRepository by lazy {
        ReaderSettingsRepository(this)
    }
}
