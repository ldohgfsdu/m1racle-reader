package com.m1racle.reader.data.repository

import java.io.File

/**
 * Path containment check used by the import and export paths.
 *
 * Lives here rather than next to the ZIP package importer because it is not a
 * package concern: the repository uses it to decide whether a document's stored
 * URI still points at the app's own library copy (any format), before treating
 * the file as safe to reuse. It was the only part of ZipPackageImporter with a
 * non-package caller.
 */
internal object FilePathSafety {

    /** True when [child] is [root] itself or lives underneath it, after resolving symlinks. */
    fun isUnder(child: File, root: File): Boolean {
        val c = child.canonicalFile.path
        val r = root.canonicalFile.path
        return c == r || c.startsWith(r + File.separator)
    }
}
