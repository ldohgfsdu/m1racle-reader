package com.markleaf.app

import android.app.Application
import com.markleaf.app.data.local.AppDatabase
import com.markleaf.app.data.repository.DocumentRepository

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
}
