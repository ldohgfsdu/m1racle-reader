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
        // getPage(i) traverses the page tree from its root on every call.
        // The page-tree iterator visits it once, including large nested textbooks.
        for ((index, page) in doc.pages.withIndex()) {
            indexByDict[page.cosObject] = index
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
