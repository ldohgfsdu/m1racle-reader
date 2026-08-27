package com.m1racle.reader.pdf

import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem

/**
 * Resolves [PDOutlineItem] destinations to 0-based page indices using the
 * **stable semantic identity** of a PDF page: its underlying [COSDictionary].
 *
 * Why not wrapper identity: pdfbox-android 2.0.27 constructs a **fresh
 * [com.tom_roush.pdfbox.pdmodel.PDPage] on every access** (`getPage(i)`,
 * `findDestinationPage` → `retrievePage`), so `IdentityHashMap<PDPage, Int>`
 * can never hit. The underlying COSDictionary instances, however, ARE shared
 * across all access paths (COSDocument object cache), and
 * `PDPageTree.indexOf` compares dictionaries by reference — which is exactly
 * why the pre-fix fallback worked (but at O(pages) per bookmark).
 *
 * Strategy: index pages once by dictionary (O(pages)), then O(1) per bookmark,
 * keeping `indexOf` as a defensive fallback for documents where dictionary
 * instances are not shared (e.g. unusual incremental-update layouts).
 */
class OutlinePageIndexResolver(private val doc: PDDocument) {

    private val indexByDict: HashMap<COSDictionary, Int> =
        HashMap(doc.numberOfPages.coerceAtLeast(16))

    init {
        val pageCount = doc.numberOfPages
        for (i in 0 until pageCount) {
            try {
                indexByDict[doc.getPage(i).cosObject] = i
            } catch (_: Throwable) {
                // Unreadable page — skip; the indexOf fallback still covers it.
            }
        }
    }

    /**
     * @return 0-based page index, or -1 when the destination cannot be resolved.
     */
    fun resolvePageIndex(item: PDOutlineItem): Int = try {
        val page = item.findDestinationPage(doc) ?: return -1
        indexByDict[page.cosObject]
            ?: doc.pages.indexOf(page).takeIf { it >= 0 }
            ?: -1
    } catch (_: Throwable) {
        -1
    }
}
