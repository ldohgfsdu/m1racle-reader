package com.m1racle.reader.ui.pdf

import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.m1racle.reader.pdf.export.PdfSplitResidue
import com.m1racle.reader.pdf.export.PdfSplitState
import com.m1racle.reader.ui.theme.M1racleReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34])
class PdfSplitProgressDialogTest {
    @get:Rule val rule = createComposeRule()

    private fun residue(name: String) = PdfSplitResidue(name, Uri.parse("content://out/$name"))

    @Test fun `failed cleanup lists every incomplete file name`() {
        val retained = listOf(residue("教材_0001-0001.pdf"), residue("教材_0002-0002.pdf"))
        rule.setContent {
            M1racleReaderTheme {
                PdfSplitProgressDialog(
                    state = PdfSplitState.Failed(
                        message = "无法校验 教材_0003-0003.pdf 的完整输出",
                        outputCleanupFailed = true,
                        retainedOutputs = retained,
                    ),
                    onCancel = {},
                    onDismiss = {},
                )
            }
        }
        rule.onNodeWithText("拆分失败").assertIsDisplayed()
        rule.onNodeWithText("无法校验 教材_0003-0003.pdf 的完整输出").assertIsDisplayed()
        rule.onNodeWithText("部分不完整文件无法由文件提供方删除。").assertIsDisplayed()
        retained.forEach { rule.onNodeWithText(it.displayName, substring = true).assertIsDisplayed() }
    }

    @Test fun `failed cleanup success hides the cleanup warning`() {
        rule.setContent {
            M1racleReaderTheme {
                PdfSplitProgressDialog(
                    state = PdfSplitState.Failed(
                        message = "无法写入 教材_0001-0001.pdf",
                        outputCleanupFailed = false,
                    ),
                    onCancel = {},
                    onDismiss = {},
                )
            }
        }
        rule.onNodeWithText("无法写入 教材_0001-0001.pdf").assertIsDisplayed()
        rule.onNodeWithText("部分不完整文件无法由文件提供方删除。").assertDoesNotExist()
    }

    @Test fun `cancelled with refused delete lists retained outputs`() {
        val retained = listOf(residue("教材_0001-0001.pdf"))
        rule.setContent {
            M1racleReaderTheme {
                PdfSplitProgressDialog(
                    state = PdfSplitState.Cancelled(
                        cleanedOutputs = false,
                        retainedOutputs = retained,
                    ),
                    onCancel = {},
                    onDismiss = {},
                )
            }
        }
        rule.onNodeWithText("已取消拆分").assertIsDisplayed()
        rule.onNodeWithText("已取消，但部分输出无法由文件提供方删除。").assertIsDisplayed()
        rule.onNodeWithText("教材_0001-0001.pdf", substring = true).assertIsDisplayed()
    }

    @Test fun `cancelled with all outputs deleted shows the clean message`() {
        rule.setContent {
            M1racleReaderTheme {
                PdfSplitProgressDialog(
                    state = PdfSplitState.Cancelled(cleanedOutputs = true),
                    onCancel = {},
                    onDismiss = {},
                )
            }
        }
        rule.onNodeWithText("本次生成的输出和临时文件已删除。").assertIsDisplayed()
        rule.onNodeWithText("部分不完整文件无法由文件提供方删除。").assertDoesNotExist()
    }
}
