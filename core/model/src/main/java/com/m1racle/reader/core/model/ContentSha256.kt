package com.m1racle.reader.core.model

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/** Content identity shared by document import, revision and source-write recovery. */
object ContentSha256 {
    fun of(file: File): String = file.inputStream().use(::of)

    fun of(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
