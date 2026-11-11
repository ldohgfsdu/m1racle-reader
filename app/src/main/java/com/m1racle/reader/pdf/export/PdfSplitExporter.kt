package com.m1racle.reader.pdf.export

import android.content.Context
import android.net.Uri
import com.m1racle.reader.pdf.PdfBoxRuntime
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.graphics.PDXObject
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColorSpace
import com.tom_roush.pdfbox.pdmodel.graphics.pattern.PDAbstractPattern
import com.tom_roush.pdfbox.pdmodel.graphics.shading.PDShading
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

data class PdfSplitOutput(
    val displayName: String,
    val uri: Uri,
    val sourceRange: PdfSplitPageRange,
    val pageCount: Int,
)

data class PdfSplitResidue(
    val displayName: String,
    val uri: Uri,
)

sealed interface PdfSplitState {
    data class Preparing(
        val partCount: Int,
        val totalPages: Int,
    ) : PdfSplitState

    data class CopyingPart(
        val partIndex: Int,
        val partCount: Int,
        val copiedPages: Int,
        val totalPages: Int,
        val outputName: String,
    ) : PdfSplitState

    data class VerifyingPart(
        val partIndex: Int,
        val partCount: Int,
        val verifiedPages: Int,
        val totalPages: Int,
        val outputName: String,
    ) : PdfSplitState

    data class Completed(val outputs: List<PdfSplitOutput>) : PdfSplitState

    data class Cancelled(
        val cleanedOutputs: Boolean,
        val retainedOutputs: List<PdfSplitResidue> = emptyList(),
    ) : PdfSplitState

    data class Failed(
        val message: String,
        val outputCleanupFailed: Boolean,
        val retainedOutputs: List<PdfSplitResidue> = emptyList(),
    ) : PdfSplitState
}

/**
 * Splits a source PDF into vector PDFs under a selected directory.
 *
 * The source stream is staged with 64 KiB reads and PDFBox runs in temp-file
 * memory mode. Each local part and each completed SAF output is reopened and
 * compared with its source pages before it is reported as complete.
 */
class PdfSplitExporter internal constructor(
    context: Context,
    private val outputTarget: PdfSplitOutputTarget,
) {
    private val appContext = context.applicationContext

    constructor(context: Context) : this(
        context,
        AndroidSafSplitOutputTarget(context),
    )

    suspend fun export(
        sourceUri: Uri,
        plan: PdfSplitPlan,
        outputDirectoryUri: Uri,
        cancellation: ExportCancellationToken,
        onState: (PdfSplitState) -> Unit,
    ): PdfSplitState = withContext(Dispatchers.IO) {
        validatePlan(plan)
        val workDir = File(
            appContext.cacheDir,
            "pdf_split_${System.currentTimeMillis()}_${System.nanoTime()}",
        )
        val createdOutputs = ArrayList<PdfSplitResidue>(plan.parts.size)
        var completed = false

        fun cleanupOutputs(): List<PdfSplitResidue> {
            val retained = ArrayList<PdfSplitResidue>()
            for (output in createdOutputs) {
                val deleted = runCatching { outputTarget.delete(output.uri) }
                    .getOrDefault(false)
                if (!deleted) retained += output
            }
            createdOutputs.clear()
            createdOutputs.addAll(retained)
            return retained.toList()
        }

        try {
            onState(
                PdfSplitState.Preparing(
                    partCount = plan.parts.size,
                    totalPages = plan.totalPages,
                ),
            )
            if (!workDir.mkdirs()) {
                return@withContext fail(
                    message = "无法创建拆分临时目录",
                    workDir = workDir,
                    retainedOutputs = emptyList(),
                    onState = onState,
                )
            }
            if (!PdfBoxRuntime.ensureInit(appContext)) {
                return@withContext fail(
                    message = "PDF 组件初始化失败",
                    workDir = workDir,
                    retainedOutputs = emptyList(),
                    onState = onState,
                )
            }
            throwIfExportCancelled(cancellation) { coroutineContext.isActive }

            val memorySetting = PdfBoxRuntime.memorySetting()
            PdfBoxRuntime.open(appContext, sourceUri).use { source ->
                if (source.numberOfPages != plan.sourcePageCount) {
                    return@withContext fail(
                        message = "源 PDF 页数已变化：计划 ${plan.sourcePageCount}，实际 ${source.numberOfPages}",
                        workDir = workDir,
                        retainedOutputs = emptyList(),
                        onState = onState,
                    )
                }

                var completedPages = 0
                for (part in plan.parts) {
                    throwIfExportCancelled(cancellation) { coroutineContext.isActive }
                    val localPdf = File(workDir, "part_${part.index.toString().padStart(4, '0')}.pdf")
                    writePart(
                        source = source,
                        part = part,
                        localPdf = localPdf,
                        memorySetting = memorySetting,
                        cancellation = cancellation,
                        jobActive = { coroutineContext.isActive },
                        copiedPagesBefore = completedPages,
                        partCount = plan.parts.size,
                        totalPages = plan.totalPages,
                        onState = onState,
                    )

                    throwIfExportCancelled(cancellation) { coroutineContext.isActive }
                    val outputUri = outputTarget.createPdf(
                        outputDirectoryUri,
                        part.outputName,
                    ) ?: return@withContext fail(
                        message = "无法在所选目录创建 ${part.outputName}",
                        workDir = workDir,
                        retainedOutputs = cleanupOutputs(),
                        onState = onState,
                    )
                    createdOutputs += PdfSplitResidue(part.outputName, outputUri)

                    outputTarget.openOutput(outputUri)?.use { output ->
                        FileInputStream(localPdf).use { input ->
                            copyWithExportCancellation(
                                input = input,
                                output = output,
                                cancellation = cancellation,
                                jobActive = { coroutineContext.isActive },
                            )
                        }
                    } ?: return@withContext fail(
                        message = "无法写入 ${part.outputName}",
                        workDir = workDir,
                        retainedOutputs = cleanupOutputs(),
                        onState = onState,
                    )

                    val localSha = FileInputStream(localPdf).use { input ->
                        sha256WithExportCancellation(
                            input = input,
                            cancellation = cancellation,
                            jobActive = { coroutineContext.isActive },
                        )
                    }
                    val outputSha = outputTarget.openInput(outputUri)?.use { input ->
                        sha256WithExportCancellation(
                            input = input,
                            cancellation = cancellation,
                            jobActive = { coroutineContext.isActive },
                        )
                    } ?: return@withContext fail(
                        message = "无法校验 ${part.outputName} 的完整输出",
                        workDir = workDir,
                        retainedOutputs = cleanupOutputs(),
                        onState = onState,
                    )
                    check(localSha == outputSha) {
                        "输出文件字节不一致：${part.outputName}"
                    }

                    var verifiedPages = 0
                    outputTarget.openInput(outputUri)?.use { input ->
                        PDDocument.load(input, memorySetting).use { readback ->
                            verifyPart(
                                source = source,
                                readback = readback,
                                part = part,
                                cancellation = cancellation,
                                jobActive = { coroutineContext.isActive },
                                onVerifiedPage = { pageInPart ->
                                    verifiedPages = pageInPart
                                    onState(
                                        PdfSplitState.VerifyingPart(
                                            partIndex = part.index,
                                            partCount = plan.parts.size,
                                            verifiedPages = completedPages + pageInPart,
                                            totalPages = plan.totalPages,
                                            outputName = part.outputName,
                                        ),
                                    )
                                },
                            )
                        }
                    } ?: return@withContext fail(
                        message = "无法回读 ${part.outputName}",
                        workDir = workDir,
                        retainedOutputs = cleanupOutputs(),
                        onState = onState,
                    )
                    if (verifiedPages != part.sourceRange.pageCount) {
                        return@withContext fail(
                            message = "回读页数不一致：${part.outputName}",
                            workDir = workDir,
                            retainedOutputs = cleanupOutputs(),
                            onState = onState,
                        )
                    }

                    completedPages += part.sourceRange.pageCount
                    localPdf.delete()
                    throwIfExportCancelled(cancellation) { coroutineContext.isActive }
                }
            }

            throwIfExportCancelled(cancellation) { coroutineContext.isActive }
            val outputs = plan.parts.zip(createdOutputs).map { (part, created) ->
                PdfSplitOutput(
                    displayName = part.outputName,
                    uri = created.uri,
                    sourceRange = part.sourceRange,
                    pageCount = part.sourceRange.pageCount,
                )
            }
            val done = PdfSplitState.Completed(outputs)
            onState(done)
            completed = true
            createdOutputs.clear()
            ChapterPackageExporter.cleanupDir(workDir)
            done
        } catch (cancelled: CancellationException) {
            val retained = cleanupOutputs()
            ChapterPackageExporter.cleanupDir(workDir)
            onState(
                PdfSplitState.Cancelled(
                    cleanedOutputs = retained.isEmpty(),
                    retainedOutputs = retained,
                ),
            )
            throw cancelled
        } catch (t: Throwable) {
            val retained = cleanupOutputs()
            ChapterPackageExporter.cleanupDir(workDir)
            val state = PdfSplitState.Failed(
                message = t.message?.takeIf { it.isNotBlank() } ?: "PDF 拆分失败",
                outputCleanupFailed = retained.isNotEmpty(),
                retainedOutputs = retained,
            )
            onState(state)
            state
        } finally {
            if (!completed) ChapterPackageExporter.cleanupDir(workDir)
        }
    }

    private suspend fun writePart(
        source: PDDocument,
        part: PdfSplitPart,
        localPdf: File,
        memorySetting: com.tom_roush.pdfbox.io.MemoryUsageSetting,
        cancellation: ExportCancellationToken,
        jobActive: () -> Boolean,
        copiedPagesBefore: Int,
        partCount: Int,
        totalPages: Int,
        onState: (PdfSplitState) -> Unit,
    ) {
        PDDocument(memorySetting).use { destination ->
            for (pageIndex in part.sourceRange.startPageIndex..part.sourceRange.endPageIndex) {
                throwIfExportCancelled(cancellation, jobActive)
                val sourcePage = source.getPage(pageIndex)
                val importedPage = destination.importPage(sourcePage)
                preserveEffectivePageGeometry(sourcePage, importedPage)
                materializeInheritedResources(sourcePage, importedPage)
                checkResourceStructure(sourcePage, importedPage, part.outputName)
                val pageInPart = pageIndex - part.sourceRange.startPageIndex + 1
                onState(
                    PdfSplitState.CopyingPart(
                        partIndex = part.index,
                        partCount = partCount,
                        copiedPages = copiedPagesBefore + pageInPart,
                        totalPages = totalPages,
                        outputName = part.outputName,
                    ),
                )
            }
            throwIfExportCancelled(cancellation, jobActive)
            PdfImportedPageLinks.isolate(source, destination, part.sourceRange.startPageIndex)
            destination.save(localPdf)
        }

        FileInputStream(localPdf).use { input ->
            PDDocument.load(input, memorySetting).use { localReadback ->
                verifyPart(
                    source = source,
                    readback = localReadback,
                    part = part,
                    cancellation = cancellation,
                    jobActive = jobActive,
                    onVerifiedPage = {},
                )
            }
        }
    }

    private fun preserveEffectivePageGeometry(sourcePage: PDPage, importedPage: PDPage) {
        importedPage.mediaBox = sourcePage.mediaBox
        importedPage.cropBox = sourcePage.cropBox
        importedPage.bleedBox = sourcePage.bleedBox
        importedPage.trimBox = sourcePage.trimBox
        importedPage.artBox = sourcePage.artBox
        importedPage.rotation = sourcePage.rotation
        importedPage.userUnit = sourcePage.userUnit
    }

    private fun materializeInheritedResources(sourcePage: PDPage, importedPage: PDPage) {
        if (
            !sourcePage.getCOSObject().containsKey(COSName.RESOURCES) &&
            sourcePage.resources != null
        ) {
            importedPage.resources = sourcePage.resources
        }
    }

    private fun checkResourceStructure(
        sourcePage: PDPage,
        importedPage: PDPage,
        outputName: String,
    ) {
        val sourceResources = sourcePage.resources
        val importedResources = importedPage.resources
        if (sourceResources == null) {
            check(importedResources == null) {
                "资源结构不一致：$outputName"
            }
            return
        }
        check(importedResources != null) {
            "资源结构缺失：$outputName"
        }

        checkResourceKinds(
            outputName = outputName,
            kind = "XObject",
            sourceNames = sourceResources.xObjectNames,
            outputNames = importedResources.xObjectNames,
            resolveType = { importedResources.getXObject(it)?.let(::xObjectType) },
            sourceType = { sourceResources.getXObject(it)?.let(::xObjectType) },
        )
        checkResourceKinds(
            outputName = outputName,
            kind = "Font",
            sourceNames = sourceResources.fontNames,
            outputNames = importedResources.fontNames,
            resolveType = { importedResources.getFont(it)?.let(::fontType) },
            sourceType = { sourceResources.getFont(it)?.let(::fontType) },
        )
        checkResourceKinds(
            outputName = outputName,
            kind = "ColorSpace",
            sourceNames = sourceResources.colorSpaceNames,
            outputNames = importedResources.colorSpaceNames,
            resolveType = { importedResources.getColorSpace(it)?.let(::colorSpaceType) },
            sourceType = { sourceResources.getColorSpace(it)?.let(::colorSpaceType) },
        )
        checkResourceKinds(
            outputName = outputName,
            kind = "ExtGState",
            sourceNames = sourceResources.extGStateNames,
            outputNames = importedResources.extGStateNames,
            resolveType = { importedResources.getExtGState(it)?.let(::extGStateType) },
            sourceType = { sourceResources.getExtGState(it)?.let(::extGStateType) },
        )
        checkResourceKinds(
            outputName = outputName,
            kind = "Pattern",
            sourceNames = sourceResources.patternNames,
            outputNames = importedResources.patternNames,
            resolveType = { importedResources.getPattern(it)?.let(::patternType) },
            sourceType = { sourceResources.getPattern(it)?.let(::patternType) },
        )
        checkResourceKinds(
            outputName = outputName,
            kind = "Shading",
            sourceNames = sourceResources.shadingNames,
            outputNames = importedResources.shadingNames,
            resolveType = { importedResources.getShading(it)?.let(::shadingType) },
            sourceType = { sourceResources.getShading(it)?.let(::shadingType) },
        )
    }

    private fun checkResourceKinds(
        outputName: String,
        kind: String,
        sourceNames: Iterable<COSName>,
        outputNames: Iterable<COSName>,
        resolveType: (COSName) -> String?,
        sourceType: (COSName) -> String?,
    ) {
        val sourceKeys = sourceNames.map { it.name }.toSet()
        val outputKeys = outputNames.map { it.name }.toSet()
        check(sourceKeys == outputKeys) {
            "资源键不一致：$kind，$outputName"
        }
        sourceKeys.sorted().forEach { name ->
            val sourceCosName = COSName.getPDFName(name)
            val sourceValue = sourceType(sourceCosName)
            val outputValue = resolveType(sourceCosName)
            check(sourceValue == outputValue) {
                "资源类型不一致：$kind/$name，$outputName"
            }
        }
    }

    private fun xObjectType(value: PDXObject): String =
        value.javaClass.simpleName

    private fun fontType(value: PDFont): String =
        value.javaClass.simpleName

    private fun colorSpaceType(value: PDColorSpace): String =
        value.javaClass.simpleName

    private fun extGStateType(value: PDExtendedGraphicsState): String =
        value.javaClass.simpleName

    private fun patternType(value: PDAbstractPattern): String =
        value.javaClass.simpleName

    private fun shadingType(value: PDShading): String =
        value.javaClass.simpleName

    private fun verifyPart(
        source: PDDocument,
        readback: PDDocument,
        part: PdfSplitPart,
        cancellation: ExportCancellationToken,
        jobActive: () -> Boolean,
        onVerifiedPage: (Int) -> Unit,
    ) {
        check(readback.numberOfPages == part.sourceRange.pageCount) {
            "页数不符：${part.outputName}，期望 ${part.sourceRange.pageCount}，实际 ${readback.numberOfPages}"
        }
        repeat(part.sourceRange.pageCount) { offset ->
            val sourcePage = source.getPage(part.sourceRange.startPageIndex + offset)
            val outputPage = readback.getPage(offset)
            check(
                pageFingerprint(
                    page = sourcePage,
                    cancellation = cancellation,
                    jobActive = jobActive,
                ) == pageFingerprint(
                    page = outputPage,
                    cancellation = cancellation,
                    jobActive = jobActive,
                ),
            ) {
                "第 ${offset + 1} 页内容、尺寸或旋转不一致：${part.outputName}"
            }
            throwIfExportCancelled(cancellation, jobActive)
            onVerifiedPage(offset + 1)
        }
    }

    private fun pageFingerprint(
        page: PDPage,
        cancellation: ExportCancellationToken,
        jobActive: () -> Boolean,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        listOf(
            page.mediaBox,
            page.cropBox,
            page.bleedBox,
            page.trimBox,
            page.artBox,
            page.userUnit,
            page.rotation,
        ).forEach { value ->
            digest.update(value.toString().toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
        }
        if (page.hasContents()) {
            page.contents.use { input ->
                val buffer = ByteArray(EXPORT_COPY_CHUNK_BYTES)
                while (true) {
                    throwIfExportCancelled(cancellation, jobActive)
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun validatePlan(plan: PdfSplitPlan) {
        require(plan.sourcePageCount > 0) { "文档无有效页" }
        require(plan.selection.startPageIndex >= 0) { "起始页不能为负" }
        require(plan.selection.endPageIndex < plan.sourcePageCount) {
            "结束页超出文档范围"
        }
        require(plan.selection.startPageIndex <= plan.selection.endPageIndex) {
            "起始页不能大于结束页"
        }
        require(plan.parts.isNotEmpty()) { "拆分计划不能为空" }
        require(plan.parts.first().sourceRange.startPageIndex == plan.selection.startPageIndex) {
            "拆分计划未覆盖选区起始页"
        }
        require(plan.parts.last().sourceRange.endPageIndex == plan.selection.endPageIndex) {
            "拆分计划未覆盖选区末页"
        }
        plan.parts.zipWithNext().forEach { (current, next) ->
            require(current.sourceRange.endPageIndex + 1 == next.sourceRange.startPageIndex) {
                "拆分计划存在重叠或漏页"
            }
        }
        plan.parts.forEach { part ->
            require(part.sourceRange.startPageIndex >= plan.selection.startPageIndex)
            require(part.sourceRange.endPageIndex <= plan.selection.endPageIndex)
            require(part.sourceRange.startPageIndex <= part.sourceRange.endPageIndex)
            require(part.outputName.endsWith(".pdf", ignoreCase = true))
            require(
                part.outputName.none { it == '/' || it == '\\' || it == '\u0000' },
            ) { "输出文件名非法" }
        }
        require(plan.parts.map { it.outputName.lowercase() }.distinct().size == plan.parts.size) {
            "拆分输出文件名重复"
        }
    }

    private fun fail(
        message: String,
        workDir: File,
        retainedOutputs: List<PdfSplitResidue>,
        onState: (PdfSplitState) -> Unit,
    ): PdfSplitState {
        ChapterPackageExporter.cleanupDir(workDir)
        val state = PdfSplitState.Failed(
            message = message,
            outputCleanupFailed = retainedOutputs.isNotEmpty(),
            retainedOutputs = retainedOutputs,
        )
        onState(state)
        return state
    }
}
