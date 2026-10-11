package com.m1racle.reader.testing

import android.database.sqlite.SQLiteConstraintException
import com.m1racle.reader.data.local.DocumentAliasDao
import com.m1racle.reader.data.local.DocumentAliasEntity
import com.m1racle.reader.data.repository.RepositoryTransactionRunner

class TestDocumentAliasDao(
    initialAliases: Iterable<DocumentAliasEntity> = emptyList(),
) : DocumentAliasDao {
    private val aliases = linkedMapOf<String, DocumentAliasEntity>()

    init {
        initialAliases.forEach { alias ->
            check(aliases.put(alias.aliasKey, alias) == null) {
                "duplicate initial alias key: ${alias.aliasKey}"
            }
        }
    }

    override suspend fun getByKey(aliasKey: String): DocumentAliasEntity? =
        aliases[aliasKey]

    override suspend fun listForDocument(documentId: String): List<DocumentAliasEntity> =
        aliases.values
            .filter { it.documentId == documentId }
            .sortedWith(compareBy(DocumentAliasEntity::createdAt, DocumentAliasEntity::aliasKey))

    override suspend fun insertAlias(alias: DocumentAliasEntity): Long {
        if (alias.aliasKey in aliases) {
            throw SQLiteConstraintException("duplicate alias key: ${alias.aliasKey}")
        }
        aliases[alias.aliasKey] = alias
        return aliases.size.toLong()
    }

    override suspend fun updateAlias(alias: DocumentAliasEntity) {
        if (alias.aliasKey in aliases) {
            aliases[alias.aliasKey] = alias
        }
    }

    override suspend fun deleteByKind(kind: String): Int {
        val removed = aliases.values.count { it.kind == kind }
        aliases.entries.removeAll { it.value.kind == kind }
        return removed
    }

    fun allAliases(): List<DocumentAliasEntity> = aliases.values.toList()
}

internal class ImmediateRepositoryTransactionRunner : RepositoryTransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = block()
}
