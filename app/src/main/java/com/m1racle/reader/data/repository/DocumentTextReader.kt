package com.m1racle.reader.data.repository

import java.io.InputStream
import java.io.ByteArrayOutputStream

/** Bounded UTF-8 reading; optional integrity check never changes document identity. */
internal fun readUtf8TextFully(
    input: InputStream,
    maxBytes: Int = DocumentRepository.MAX_MARKDOWN_BYTES,
    expectedContentSha256: String? = null,
): String {
    require(maxBytes > 0) { "maxBytes must be positive" }
    val digest = expectedContentSha256?.let { java.security.MessageDigest.getInstance("SHA-256") }
    val output = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
    val buffer = ByteArray(64 * 1024)
    var total = 0
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        if (read == 0) continue
        if (read > maxBytes - total) {
            throw DocumentReadException.TooLarge(maxBytes)
        }
        digest?.update(buffer, 0, read)
        output.write(buffer, 0, read)
        total += read
    }
    if (digest != null && digest.digest().joinToString("") { "%02x".format(it) } != expectedContentSha256) {
        throw DocumentReadException.ContentChanged
    }
    return output.toString(Charsets.UTF_8.name())
}
