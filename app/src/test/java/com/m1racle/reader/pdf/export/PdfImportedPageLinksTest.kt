package com.m1racle.reader.pdf.export

import androidx.test.core.app.ApplicationProvider
import com.m1racle.reader.pdf.PdfBoxRuntime
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSInteger
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentNameDestinationDictionary
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionGoTo
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class PdfImportedPageLinksTest {
    @get:Rule
    val files = TemporaryFolder()

    private lateinit var app: android.content.Context

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        check(PdfBoxRuntime.ensureInit(app))
    }

    @Test
    fun isolatesAnnotationPagesAndInternalLinksWithoutMutatingSource() {
        val sourceFile = files.newFile("linked-source.pdf")
        createSourcePdf(sourceFile)
        val sourceBytes = sourceFile.readBytes()
        val outputFile = files.newFile("linked-output.pdf")

        PDDocument.load(sourceFile, PdfBoxRuntime.memorySetting(app)).use { source ->
            val sourcePage0 = source.getPage(0)
            val sourcePage1 = source.getPage(1)
            val sourcePage2 = source.getPage(2)
            val sourceAnnotations0 = sourcePage0.annotations
            val sourceAnnotations1 = sourcePage1.annotations
            val directInternal = sourceAnnotations0[0] as PDAnnotationLink
            val namedInternal = sourceAnnotations0[1] as PDAnnotationLink
            val numericGoto = sourceAnnotations1[0] as PDAnnotationLink
            val outsideSelection = sourceAnnotations1[1] as PDAnnotationLink
            val sourceDirectArray =
                (directInternal.destination as PDPageDestination).cosObject
            val sourceNamedDestination = namedInternal.destination as PDNamedDestination
            val sourceNumericArray =
                (numericGoto.action as PDActionGoTo).cosObject.getCOSArray(COSName.D)
                    ?: error("numeric GoTo destination missing")
            val sourceOutsideArray =
                (outsideSelection.destination as PDPageDestination).cosObject

            PDDocument().use { destination ->
                destination.importPage(sourcePage0)
                destination.importPage(sourcePage1)

                PdfImportedPageLinks.isolate(source, destination, sourceStart0 = 0)

                assertSame(
                    sourceAnnotations0[0].cosObject,
                    sourcePage0.annotations[0].cosObject,
                )
                assertSame(sourcePage0.cosObject, sourcePage0.annotations[0].page?.cosObject)
                assertSame(sourcePage1.cosObject, sourcePage1.annotations[0].page?.cosObject)
                assertSame(
                    sourceDirectArray,
                    (directInternal.destination as PDPageDestination).cosObject,
                )
                assertSame(
                    sourcePage1.cosObject,
                    (directInternal.destination as PDPageDestination).page?.cosObject,
                )
                assertEquals("named-first", sourceNamedDestination.namedDestination)
                assertEquals(0, sourceNumericArray.getInt(0))
                assertSame(
                    sourceNumericArray,
                    (numericGoto.action as PDActionGoTo).cosObject.getCOSArray(COSName.D),
                )
                assertSame(
                    sourceOutsideArray,
                    (outsideSelection.destination as PDPageDestination).cosObject,
                )
                assertSame(
                    sourcePage2.cosObject,
                    (outsideSelection.destination as PDPageDestination).page?.cosObject,
                )

                destination.save(outputFile)
            }

            assertSame(sourcePage0.cosObject, source.getPage(0).cosObject)
            assertEquals(sourceAnnotations0.size, source.getPage(0).annotations.size)
            assertEquals(sourceAnnotations1.size, source.getPage(1).annotations.size)
        }

        assertArrayEquals("isolation must never save the source document", sourceBytes, sourceFile.readBytes())

        PDDocument.load(outputFile, PdfBoxRuntime.memorySetting(app)).use { reopened ->
            assertEquals(2, reopened.numberOfPages)
            val page0 = reopened.getPage(0)
            val page1 = reopened.getPage(1)
            val annotations0 = page0.annotations
            val annotations1 = page1.annotations
            assertEquals(2, annotations0.size)
            assertEquals(2, annotations1.size)

            annotations0.forEach { annotation ->
                assertSame(page0.cosObject, annotation.page?.cosObject)
            }
            annotations1.forEach { annotation ->
                assertSame(page1.cosObject, annotation.page?.cosObject)
            }

            val direct = annotations0[0] as PDAnnotationLink
            val named = annotations0[1] as PDAnnotationLink
            val numeric = annotations1[0] as PDAnnotationLink
            val outside = annotations1[1] as PDAnnotationLink
            assertDestinationPage(page1, direct.destination)
            assertDestinationPage(page0, named.destination)
            assertDestinationPage(page0, (numeric.action as PDActionGoTo).destination)
            assertNull(outside.destination)
            assertNull(outside.action)

            val annotationPages = listOf(page0 to annotations0, page1 to annotations1)
            annotationPages.forEach { (page, annotations) ->
                annotations.forEach { annotation ->
                    assertSame(page.cosObject, annotation.page?.cosObject)
                }
            }
        }
    }

    @Test
    fun keepsGeneratedCoverAndRemapsOnlyImportedPages() {
        val sourceFile = files.newFile("covered-source.pdf")
        createSourcePdf(sourceFile)
        val sourceBytes = sourceFile.readBytes()
        val outputFile = files.newFile("covered-output.pdf")

        PDDocument.load(sourceFile, PdfBoxRuntime.memorySetting(app)).use { source ->
            val sourcePage0 = source.getPage(0)
            val sourceDirectArray =
                ((sourcePage0.annotations[0] as PDAnnotationLink).destination as PDPageDestination)
                    .cosObject
            val sourceOutsideArray =
                ((source.getPage(1).annotations[1] as PDAnnotationLink).destination as PDPageDestination)
                    .cosObject

            PDDocument().use { destination ->
                val cover = PDPage(PDRectangle(50f, 50f))
                destination.addPage(cover)
                setAnnotations(cover, listOf(link(page = cover)))
                val coverAnnotations = cover.cosObject.getCOSArray(COSName.ANNOTS)
                val coverAnnotation = cover.annotations[0].cosObject

                destination.importPage(sourcePage0)
                destination.importPage(source.getPage(1))

                PdfImportedPageLinks.isolate(
                    source,
                    destination,
                    sourceStart0 = 0,
                    destinationStart0 = 1,
                )

                assertSame(coverAnnotations, destination.getPage(0).cosObject.getCOSArray(COSName.ANNOTS))
                assertSame(coverAnnotation, destination.getPage(0).annotations[0].cosObject)
                assertSame(
                    destination.getPage(0).cosObject,
                    destination.getPage(0).annotations[0].page?.cosObject,
                )
                assertSame(
                    sourceDirectArray,
                    ((sourcePage0.annotations[0] as PDAnnotationLink).destination as PDPageDestination)
                        .cosObject,
                )
                assertSame(
                    source.getPage(1).cosObject,
                    ((sourcePage0.annotations[0] as PDAnnotationLink).destination as PDPageDestination)
                        .page
                        ?.cosObject,
                )
                assertSame(
                    sourceOutsideArray,
                    ((source.getPage(1).annotations[1] as PDAnnotationLink).destination as PDPageDestination)
                        .cosObject,
                )
                assertSame(
                    source.getPage(2).cosObject,
                    ((source.getPage(1).annotations[1] as PDAnnotationLink).destination as PDPageDestination)
                        .page
                        ?.cosObject,
                )

                destination.save(outputFile)
            }
        }

        assertArrayEquals(sourceBytes, sourceFile.readBytes())

        PDDocument.load(outputFile, PdfBoxRuntime.memorySetting(app)).use { reopened ->
            assertEquals(3, reopened.numberOfPages)
            val cover = reopened.getPage(0)
            val importedFirst = reopened.getPage(1)
            val importedSecond = reopened.getPage(2)
            assertEquals(1, cover.annotations.size)
            assertSame(cover.cosObject, cover.annotations[0].page?.cosObject)

            val importedFirstAnnotations = importedFirst.annotations
            val importedSecondAnnotations = importedSecond.annotations
            assertEquals(2, importedFirstAnnotations.size)
            assertEquals(2, importedSecondAnnotations.size)
            importedFirstAnnotations.forEach { annotation ->
                assertSame(importedFirst.cosObject, annotation.page?.cosObject)
            }
            importedSecondAnnotations.forEach { annotation ->
                assertSame(importedSecond.cosObject, annotation.page?.cosObject)
            }

            val direct = importedFirstAnnotations[0] as PDAnnotationLink
            val named = importedFirstAnnotations[1] as PDAnnotationLink
            val numeric = importedSecondAnnotations[0] as PDAnnotationLink
            val outside = importedSecondAnnotations[1] as PDAnnotationLink
            assertDestinationPage(importedSecond, direct.destination)
            assertDestinationPage(importedFirst, named.destination)
            assertDestinationPage(importedFirst, (numeric.action as PDActionGoTo).destination)
            assertNull(outside.destination)
            assertNull(outside.action)
        }
    }

    private fun createSourcePdf(file: File) {
        PDDocument().use { document ->
            listOf(
                PDRectangle(100f, 100f),
                PDRectangle(200f, 200f),
                PDRectangle(300f, 300f),
            ).forEach { rectangle ->
                document.addPage(PDPage(rectangle))
            }

            val namedDestinations = PDDocumentNameDestinationDictionary(COSDictionary())
            namedDestinations.cosObject.setItem(
                COSName.getPDFName("named-first"),
                PDPageXYZDestination().apply {
                    setPage(document.getPage(0))
                }.cosObject,
            )
            document.documentCatalog.cosObject.setItem(
                COSName.getPDFName("Dests"),
                namedDestinations.cosObject,
            )

            setAnnotations(
                document.getPage(0),
                listOf(
                    link(
                        page = document.getPage(0),
                        destination = PDPageFitDestination().apply {
                            setPage(document.getPage(1))
                        },
                    ),
                    link(
                        page = document.getPage(0),
                        destination = PDNamedDestination("named-first"),
                    ),
                ),
            )
            setAnnotations(
                document.getPage(1),
                listOf(
                    link(
                        page = document.getPage(1),
                        action = PDActionGoTo().apply {
                            cosObject.setItem(
                                COSName.D,
                                COSArray().apply {
                                    add(COSInteger.get(0))
                                    add(COSName.getPDFName("Fit"))
                                },
                            )
                        },
                    ),
                    link(
                        page = document.getPage(1),
                        destination = PDPageFitDestination().apply {
                            setPage(document.getPage(2))
                        },
                    ),
                ),
            )
            document.save(file)
        }
    }

    private fun link(
        page: PDPage,
        destination: com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDDestination? = null,
        action: com.tom_roush.pdfbox.pdmodel.interactive.action.PDAction? = null,
    ): PDAnnotationLink =
        PDAnnotationLink().apply {
            rectangle = PDRectangle(5f, 5f, 25f, 25f)
            this.page = page
            this.destination = destination
            this.action = action
        }

    private fun setAnnotations(page: PDPage, annotations: List<PDAnnotation>) {
        page.setAnnotations(annotations)
    }

    private fun assertDestinationPage(
        expectedPage: PDPage,
        destination: com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDDestination?,
    ) {
        val pageDestination = destination as? PDPageDestination
        assertNotNull(pageDestination)
        assertSame(expectedPage.cosObject, pageDestination?.page?.cosObject)
        assertTrue((pageDestination?.pageNumber ?: -1) < 0)
    }
}
