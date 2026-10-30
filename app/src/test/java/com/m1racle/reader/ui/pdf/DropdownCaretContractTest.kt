package com.m1racle.reader.ui.pdf

import com.m1racle.reader.testing.ProductionSource
import org.junit.Assert.assertEquals
import org.junit.Test

/** Selectors stay menu anchors. They do not draw a dropdown caret. */
class DropdownCaretContractTest {

    @Test
    fun `production UI does not draw a dropdown caret`() {
        val banned = listOf(
            "ExposedDropdownMenuDefaults.TrailingIcon",
            "ArrowDropDown",
            "arrow_drop_down",
        )
        val root = ProductionSource.javaRoot()
        val hits = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                val text = file.readText()
                banned.filter { token -> text.contains(token) }
                    .map { token -> "${file.relativeTo(root).path}: $token" }
            }
            .toList()
        assertEquals(emptyList<String>(), hits)
    }
}
