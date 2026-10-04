package com.m1racle.reader.ui.home

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.m1racle.reader.data.local.DocumentEntity
import com.m1racle.reader.ui.hub.GlassDock
import com.m1racle.reader.ui.hub.HubDestination
import com.m1racle.reader.ui.hub.HubDockMotionProvider
import com.m1racle.reader.ui.material.GlassBackdropHost
import com.m1racle.reader.ui.material.GlassBackdropSource
import com.m1racle.reader.ui.material.LocalGlassBackdropHost
import com.m1racle.reader.ui.theme.M1racleReaderTheme

/** QA-only hardware optical and full-home viewport fixture; never writes a database. */
class DockGlassHarnessActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val dark = intent.getBooleanExtra("dark", false)
        val pattern = intent.getBooleanExtra("pattern", false)
        setContent {
            M1racleReaderTheme(darkTheme = dark) {
                val background = MaterialTheme.colorScheme.background
                val page = rememberLayerBackdrop(onDraw = {
                    drawRect(background)
                    drawContent()
                })
                var selected by remember { mutableStateOf(HubDestination.HOME) }
                val documents = remember {
                    (1..40).map { index ->
                        DocumentEntity(
                            id = "qa-dock-$index", displayName = "Glass document $index.pdf",
                            uri = "qa://dock/$index", mimeType = "application/pdf",
                            fileSize = 1L, lastOpenedAt = 1L, createdAt = 1L,
                            lastScrollIndex = 0, lastScrollOffset = 0, readingProgress = 0f,
                            title = null, headingCount = 0, documentType = "PDF",
                        )
                    }
                }
                CompositionLocalProvider(LocalGlassBackdropHost provides GlassBackdropHost(page, page)) {
                    Box(Modifier.fillMaxSize()) {
                        GlassBackdropSource(page, Modifier.fillMaxSize()) {
                            if (pattern) {
                                Canvas(Modifier.fillMaxSize()) {
                                    val colors = listOf(Color(0xFF168BFF), Color(0xFFFF795C), Color(0xFF37C39A))
                                    val cell = 24f * density
                                    for (row in 0..(size.height / cell).toInt()) {
                                        for (column in 0..(size.width / cell).toInt()) {
                                            drawRect(colors[(row + column) % colors.size],
                                                Offset(column * cell, row * cell), Size(cell, cell))
                                        }
                                    }
                                }
                            } else {
                                HomeContent(HomeUiState(documents = documents, ready = true), false,
                                    onImport = {}, onOpenSearch = {}, onOpen = {}, onDelete = {})
                            }
                        }
                        HubDockMotionProvider(selected) {
                            GlassDock(onSelect = { selected = it }, onImportDocument = {})
                        }
                    }
                }
            }
        }
    }
}
