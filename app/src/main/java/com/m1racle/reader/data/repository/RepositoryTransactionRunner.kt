package com.m1racle.reader.data.repository

import androidx.room.RoomDatabase
import androidx.room.withTransaction

interface RepositoryTransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}

class RoomRepositoryTransactionRunner(
    private val db: RoomDatabase,
) : RepositoryTransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T =
        db.withTransaction { block() }
}

