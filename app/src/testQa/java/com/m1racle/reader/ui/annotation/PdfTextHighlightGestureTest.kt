package com.m1racle.reader.ui.annotation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.m1racle.reader.data.anchor.NormalizedRect
import com.m1racle.reader.pdf.PdfSelectableGlyph
import com.m1racle.reader.pdf.PdfSelectableLine
import com.m1racle.reader.pdf.PdfSelectablePage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class PdfTextHighlightGestureTest {
    @get:Rule val rule=createComposeRule()
    private val saved=mutableListOf<TextSelection.PdfSelection>()
    private fun show(enabled: Boolean=true, items: List<AnnotationOverlayItem> = emptyList(), delete: ((Long,Int)->Unit)? = null, note: ((AnnotationOverlayItem,Int)->Unit)? = null) {
        val page=PdfSelectablePage(0,300f,400f,listOf(
            PdfSelectableLine(listOf(PdfSelectableGlyph("A",NormalizedRect(.1f,.2f,.15f,.05f)),PdfSelectableGlyph("B",NormalizedRect(.25f,.2f,.15f,.05f))),.1f,.4f),
            PdfSelectableLine(listOf(PdfSelectableGlyph("C",NormalizedRect(.1f,.4f,.15f,.05f)),PdfSelectableGlyph("D",NormalizedRect(.25f,.4f,.15f,.05f))),.1f,.4f),
        ))
        rule.setContent { Box(Modifier.size(300.dp,400.dp)) {
            PdfHighlightLayer(0,items,enabled,page,{}, { saved+=it },Modifier.testTag("page"),delete,note)
        } }
    }
    @Test fun singleCharacterHighlightCanBeDeletedWithoutHittingNoteAction() {
        val rect=NormalizedRect(.1f,.2f,.02f,.04f)
        var deleted=0;var noted=0
        show(items=listOf(AnnotationOverlayItem(7,AnnotationType.HIGHLIGHT,com.m1racle.reader.data.anchor.DocumentAnchor.PdfPage(0,listOf(rect)),"A")),
            delete={ _,_->deleted++ },note={ _,_->noted++ })
        rule.onNodeWithTag("page").performTouchInput { click(Offset(width*.11f,height*.22f)) }
        rule.onNodeWithTag("page").performTouchInput {
            val (x,y)=PdfHighlightGeometry.badgeCenter(rect,width.toFloat(),height.toFloat(),width/300f*18f,true)
            click(Offset(x,y))
        }
        rule.runOnIdle { assertEquals(1,deleted);assertEquals(0,noted) }
    }
    @Test fun longPressDragCommitsTwoTextLines() {
        show()
        rule.onNodeWithTag("page").performTouchInput {
            down(Offset(width*.15f,height*.22f));advanceEventTime(700)
            moveTo(Offset(width*.32f,height*.42f));up()
        }
        rule.runOnIdle {
            assertEquals(1,saved.size);assertEquals("AB\nCD",saved.single().excerpt)
            assertEquals(2,saved.single().rects.size)
        }
    }
    @Test fun scrollBeforeLongPressDoesNotCommit() {
        show()
        rule.onNodeWithTag("page").performTouchInput { swipe(Offset(width*.15f,height*.22f),Offset(width*.32f,height*.42f),150) }
        rule.runOnIdle { assertTrue(saved.isEmpty()) }
    }
    @Test fun cancellationDoesNotCommitThePreview() {
        show()
        rule.onNodeWithTag("page").performTouchInput {
            down(Offset(width*.15f,height*.22f));advanceEventTime(700)
            moveTo(Offset(width*.32f,height*.42f));cancel()
        }
        rule.runOnIdle { assertTrue(saved.isEmpty()) }
    }
    @Test fun secondFingerCancelsSelection() {
        show()
        rule.onNodeWithTag("page").performTouchInput {
            down(0,Offset(width*.15f,height*.22f));advanceEventTime(700)
            moveTo(0,Offset(width*.32f,height*.42f))
            down(1,Offset(width*.6f,height*.6f));up(1);up(0)
        }
        rule.runOnIdle { assertTrue(saved.isEmpty()) }
    }
    @Test fun disabledSelectionNeverCommits() {
        show(false)
        rule.onNodeWithTag("page").performTouchInput { down(Offset(width*.15f,height*.22f));advanceEventTime(700);up() }
        rule.runOnIdle { assertTrue(saved.isEmpty()) }
    }
    @Test fun longPressOnBlankSpaceDoesNotMakeABox() {
        show()
        rule.onNodeWithTag("page").performTouchInput {
            down(Offset(width*.8f,height*.7f));advanceEventTime(700);moveTo(Offset(width*.32f,height*.42f));up()
        }
        rule.runOnIdle { assertTrue(saved.isEmpty()) }
    }
}
