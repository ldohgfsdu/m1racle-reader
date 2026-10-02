package com.m1racle.reader.ui.pdf.edit

import com.m1racle.reader.pdf.edit.PdfEditableText

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m1racle.reader.ui.material.GlassAlertDialog

@Composable
fun PdfBodyEditorScreen(viewModel: PdfBodyEditorViewModel, onBack: () -> Unit, onSaved: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedOperator by rememberSaveable { mutableStateOf<Int?>(null) }
    val selected = state.page?.runs?.firstOrNull { it.key.operator == selectedOperator }
    var observedRevision by rememberSaveable { mutableIntStateOf(state.revision) }
    var pendingSave by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingOverwrite by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingBackup by rememberSaveable { mutableStateOf<String?>(null) }
    var saveOptions by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    var draft by rememberSaveable(stateSaver=TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var discard by rememberSaveable { mutableStateOf(false) }
    var listVisible by rememberSaveable { mutableStateOf(false) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) pendingSave=uri.toString()
    }
    val overwrite = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) result.data?.data?.let { uri ->
            val grantedFlags = result.data!!.flags
            val readGranted = grantedFlags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION != 0
            val writeGranted = grantedFlags and android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0
            val persistFlags = when {
                readGranted && writeGranted -> android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                readGranted -> android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                writeGranted -> android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                else -> 0
            }
            if (persistFlags != 0) {
                runCatching { context.contentResolver.takePersistableUriPermission(uri, persistFlags) }
            }
            pendingOverwrite=uri.toString()
        }
    }
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) pendingBackup=uri.toString()
    }
    val back = { if (!state.busy) { if (state.dirty) discard=true else onBack() } }
    BackHandler { back() }
    LaunchedEffect(state.savedDocumentId) { state.savedDocumentId?.let(onSaved) }
    LaunchedEffect(pendingSave, state.busy) {
        if (pendingSave != null && !state.busy && state.page != null) {
            val uri = android.net.Uri.parse(pendingSave); pendingSave=null; viewModel.saveCopy(uri)
        }
    }
    LaunchedEffect(pendingOverwrite,pendingBackup,state.busy) {
        if (!state.busy && state.page != null) {
            if (pendingOverwrite != null) {
                val uri=android.net.Uri.parse(pendingOverwrite); pendingOverwrite=null; viewModel.saveCurrent(uri)
            } else if (pendingBackup != null) {
                val uri=android.net.Uri.parse(pendingBackup); pendingBackup=null; viewModel.exportRecoveryBackup(uri)
            }
        }
    }
    LaunchedEffect(state.revision) {
        if (state.revision != observedRevision) { selectedOperator=null; observedRevision=state.revision }
    }
    val select: (PdfEditableText) -> Unit = { run ->
        viewModel.clearMessage(); selectedOperator=run.key.operator; draft=TextFieldValue(run.text,TextRange(0,run.text.length))
    }
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(colors.background).safeDrawingPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=back,enabled=!state.busy) { Icon(Icons.AutoMirrored.Outlined.ArrowBack,"返回") }
            Column(Modifier.weight(1f)) {
                Text("正文编辑",style=MaterialTheme.typography.titleMedium)
                Text(state.title,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant)
            }
            TextButton(onClick={ viewModel.saveCurrent() },enabled=state.dirty&&!state.busy) { Text("保存") }
            IconButton(onClick={ saveOptions=true },enabled=!state.busy) { Icon(Icons.Outlined.MoreVert,"保存方式") }
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth()) else HorizontalDivider()
        Box(Modifier.weight(1f).fillMaxWidth().background(colors.surfaceContainerLow)) {
            val image = state.image
            val page = state.page
            if (image != null && page != null) {
                val pageScroll = remember(page.index) { ScrollState(0) }
                Column(Modifier.fillMaxSize().verticalScroll(pageScroll).padding(12.dp)) {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val width = maxWidth
                        val height = width * image.height.toFloat()/image.width.toFloat()
                        Image(image,"PDF 第 ${page.index+1} 页预览",Modifier.fillMaxWidth().height(height).background(Color.White))
                        if (page.reason == null) page.runs.forEach { run ->
                            if (run.reason == null) Box(
                                Modifier.offset(x=width*run.left,y=height*run.top)
                                    .size(width*(run.right-run.left),height*(run.bottom-run.top))
                                    .border(1.dp,colors.primary.copy(alpha=0.55f),RoundedCornerShape(2.dp))
                                    .background(colors.primary.copy(alpha=0.07f))
                                    .semantics { contentDescription="编辑文字：${run.text}" }
                                    .clickable(enabled=!state.busy) { select(run) },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(page.reason ?: "点选框内文字修改。短小文字可从下方列表选择。",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                    if (page.reason == null) {
                        TextButton(onClick={ listVisible=!listVisible },enabled=!state.busy) { Text(if(listVisible) "收起文字列表" else "展开文字列表（${page.runs.size}）") }
                        if (listVisible) page.runs.forEach { run ->
                            Column(Modifier.fillMaxWidth().clickable(enabled=!state.busy) { select(run) }.padding(vertical=12.dp,horizontal=4.dp)) {
                                Text(run.text,style=MaterialTheme.typography.bodyMedium)
                                run.reason?.let { Text(it,style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant) }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            } else if (state.busy) Text("正在读取文字…",Modifier.align(Alignment.Center),color=colors.onSurfaceVariant)
        }
        if (selected == null) {
            (state.error ?: state.notice)?.let { Text(it,Modifier.fillMaxWidth().padding(16.dp),color=if(state.error!=null) colors.error else colors.onSurfaceVariant,style=MaterialTheme.typography.bodySmall) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            Row {
                IconButton(onClick=viewModel::undo,enabled=state.canUndo&&!state.busy) { Icon(Icons.AutoMirrored.Outlined.Undo,"撤销") }
                IconButton(onClick=viewModel::redo,enabled=state.canRedo&&!state.busy) { Icon(Icons.AutoMirrored.Outlined.Redo,"重做") }
            }
            val page = state.page
            Row(verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick={ viewModel.goTo((page?.index ?: 0)-1) },enabled=!state.busy&&page!=null&&page.index>0) { Icon(Icons.Outlined.ChevronLeft,"上一页") }
                Text(if(page==null) "—" else "${page.index+1} / ${page.pageCount}",style=MaterialTheme.typography.labelMedium)
                IconButton(onClick={ viewModel.goTo((page?.index ?: 0)+1) },enabled=!state.busy&&page!=null&&page.index+1<page.pageCount) { Icon(Icons.Outlined.ChevronRight,"下一页") }
            }
        }
    }
    selected?.let { run ->
        GlassAlertDialog(
            onDismissRequest={ if(!state.busy) selectedOperator=null },
            title={ Text("修改文字") },
            text={ Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(run.reason ?: "保留原字体与字号，新内容须在原有宽度内。",style=MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value=draft,onValueChange={ if(it.text.length<=2048) { draft=it; viewModel.clearMessage() } },
                    enabled=!state.busy&&run.reason==null,singleLine=true,label={ Text("正文") },modifier=Modifier.fillMaxWidth(),isError=state.error!=null)
                state.error?.let { Text(it,color=colors.error,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=8.dp)) }
            } },
            confirmButton={ TextButton(onClick={ viewModel.apply(run.key,draft.text) },enabled=!state.busy&&run.reason==null&&draft.text.isNotBlank()) { Text(if(state.busy) "生成预览…" else "应用") } },
            dismissButton={ TextButton(onClick={ selectedOperator=null; viewModel.clearMessage() },enabled=!state.busy) { Text("取消") } },
        )
    }
    if(saveOptions) PdfEditorSaveOptions(
        state=state,
        onClose={ saveOptions=false },
        onOverwrite={
            saveOptions=false
            if(state.sourceWritable && state.sourceUri!=null) viewModel.saveCurrent(android.net.Uri.parse(state.sourceUri))
            else overwrite.launch(android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                type="application/pdf"; addCategory(android.content.Intent.CATEGORY_OPENABLE)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION or android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                state.sourceUri?.let { putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI,android.net.Uri.parse(it)) }
            })
        },
        onCopy={ saveOptions=false; save.launch(state.title.substringBeforeLast(".pdf")+"-已编辑.pdf") },
        onBackup={ saveOptions=false; backup.launch("原文件恢复备份.pdf") },
    )
    if(discard) GlassAlertDialog(
        onDismissRequest={ discard=false },title={ Text("放弃未保存的修改？") },text={ Text("原文档不会改变。返回后，本次修改将被丢弃。") },
        confirmButton={ TextButton(onClick={ discard=false; onBack() }) { Text("放弃修改") } },
        dismissButton={ TextButton(onClick={ discard=false }) { Text("继续编辑") } },
    )
}


@Composable
internal fun PdfEditorSaveOptions(state: PdfEditorState, onClose: () -> Unit, onOverwrite: () -> Unit, onCopy: () -> Unit, onBackup: () -> Unit) {
    GlassAlertDialog(
        onDismissRequest=onClose,title={ Text("保存方式") },
        text={ Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("保存会更新当前书库文档，并保留阅读记录和书签。",style=MaterialTheme.typography.bodySmall)
            if(state.sourceNeedsUpdate) Text("书库里的修改尚未写回源文件。",style=MaterialTheme.typography.bodySmall)
            TextButton(onClick=onOverwrite,enabled=state.canOverwriteSource&&!state.busy) { Text("覆盖源文件并保存") }
            Text(if(state.sourceWritable) "将更新外部源文件及当前书库文档。" else "选择原 PDF 授权写入；内容不匹配时不会覆盖。",style=MaterialTheme.typography.bodySmall)
            TextButton(onClick=onCopy,enabled=state.page!=null&&!state.busy) { Text("另存副本") }
            if(state.recoveryAvailable) TextButton(onClick=onBackup,enabled=!state.busy) { Text("导出原文件备份") }
        } },confirmButton={ TextButton(onClick=onClose) { Text("关闭") } },
    )
}
