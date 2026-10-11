package com.m1racle.reader.ui.material

import android.content.Context

/** Normal builds have one immutable recipe and never read experiment preferences. */
object QaGlass {
    val selection = QaGlassSelection()
    const val enabled = false
    fun load(context: Context) = Unit
}
