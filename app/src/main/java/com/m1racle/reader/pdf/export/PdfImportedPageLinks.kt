package com.m1racle.reader.pdf.export

import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionGoTo
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import java.io.IOException
import java.util.IdentityHashMap

/**
 * Breaks annotation page-tree references inherited from an [PDDocument.importPage] call.
 *
 * Source pages are read-only. Every imported annotation dictionary and `/Annots`
 * array is cloned before its `/P` entry or link destination is rewritten.
 */
internal object PdfImportedPageLinks {
    /**
     * Rewrites annotations from destination page [destinationStart0] through the
     * last page. That page corresponds to source page [sourceStart0], and each
     * later page advances both indexes together. Destination pages before
     * [destinationStart0] stay untouched, including a generated cover at index 0.
     *
     * Internal links retarget the imported page. Links to source pages outside
     * the imported range are disconnected.
     */
    @Throws(IOException::class)
    fun isolate(
        sourceDoc: PDDocument,
        destDoc: PDDocument,
        sourceStart0: Int,
        destinationStart0: Int = 0,
    ) {
        require(sourceStart0 >= 0) { "sourceStart0 must not be negative" }
        require(destinationStart0 >= 0) { "destinationStart0 must not be negative" }
        require(destinationStart0 <= destDoc.numberOfPages) {
            "destinationStart0 exceeds destination document"
        }
        val mappedPageCount = destDoc.numberOfPages - destinationStart0
        val sourceEndExclusive = sourceStart0.toLong() + mappedPageCount.toLong()
        require(sourceEndExclusive <= sourceDoc.numberOfPages.toLong()) {
            "selected source pages exceed source document"
        }

        val sourceToDestination = IdentityHashMap<COSDictionary, PDPage>()
        val importedPages = ArrayList<PDPage>(mappedPageCount)
        repeat(mappedPageCount) { offset ->
            val sourcePage = sourceDoc.getPage(sourceStart0 + offset)
            val destinationPage = destDoc.getPage(destinationStart0 + offset)
            sourceToDestination[sourcePage.cosObject] = destinationPage
            importedPages += destinationPage
        }

        importedPages.forEach { destinationPage ->
            val annotations = destinationPage.annotations
            if (annotations.isEmpty()) return@forEach

            val clonedAnnotations = COSArray()
            annotations.forEach { sourceAnnotation ->
                val clonedAnnotation = PDAnnotation.createAnnotation(
                    COSDictionary(sourceAnnotation.cosObject),
                )
                clonedAnnotation.page = destinationPage
                if (clonedAnnotation is PDAnnotationLink) {
                    isolateLink(
                        link = clonedAnnotation,
                        sourceDoc = sourceDoc,
                        sourceStart0 = sourceStart0,
                        importedPages = importedPages,
                        sourceToDestination = sourceToDestination,
                    )
                }
                clonedAnnotations.add(clonedAnnotation.cosObject)
            }
            destinationPage.cosObject.setItem(COSName.ANNOTS, clonedAnnotations)
        }
    }

    private fun isolateLink(
        link: PDAnnotationLink,
        sourceDoc: PDDocument,
        sourceStart0: Int,
        importedPages: List<PDPage>,
        sourceToDestination: Map<COSDictionary, PDPage>,
    ) {
        val directDestination = link.destination
        if (directDestination != null) {
            val remapped = remapDestination(
                destination = directDestination,
                sourceDoc = sourceDoc,
                sourceStart0 = sourceStart0,
                importedPages = importedPages,
                sourceToDestination = sourceToDestination,
            )
            if (remapped == null) {
                link.cosObject.removeItem(COSName.DEST)
            } else {
                link.cosObject.setItem(COSName.DEST, remapped)
            }
        }

        val action = link.action
        if (action is PDActionGoTo) {
            val remapped = action.destination?.let { destination ->
                remapDestination(
                    destination = destination,
                    sourceDoc = sourceDoc,
                    sourceStart0 = sourceStart0,
                    importedPages = importedPages,
                    sourceToDestination = sourceToDestination,
                )
            }
            if (remapped == null) {
                link.cosObject.removeItem(COSName.A)
            } else {
                val clonedAction = COSDictionary(action.cosObject)
                clonedAction.setItem(COSName.D, remapped)
                link.cosObject.setItem(COSName.A, clonedAction)
            }
        }
    }

    private fun remapDestination(
        destination: PDDestination,
        sourceDoc: PDDocument,
        sourceStart0: Int,
        importedPages: List<PDPage>,
        sourceToDestination: Map<COSDictionary, PDPage>,
    ): COSArray? {
        val pageDestination = when (destination) {
            is PDPageDestination -> destination
            is PDNamedDestination ->
                sourceDoc.documentCatalog.findNamedDestinationPage(destination)
            else -> null
        } ?: return null

        val destinationPage = pageDestination.page?.let { sourcePage ->
            sourceToDestination[sourcePage.cosObject]
        } ?: pageDestination.pageNumber
            .takeIf { sourcePageIndex -> sourcePageIndex >= sourceStart0 }
            ?.let { sourcePageIndex ->
                importedPages.getOrNull(sourcePageIndex - sourceStart0)
            }
            ?: return null

        return COSArray().also { clone ->
            clone.addAll(pageDestination.cosObject)
            clone.set(0, destinationPage)
        }
    }
}
