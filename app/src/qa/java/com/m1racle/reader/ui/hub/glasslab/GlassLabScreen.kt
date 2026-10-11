package com.m1racle.reader.ui.hub.glasslab

import android.content.Context
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m1racle.reader.R
import com.m1racle.reader.ui.components.PillSegmentedControl
import com.m1racle.reader.ui.material.LocalGlassEnvironment
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.util.Locale

/** Settings child route. Optical controls only affect its isolated captured preview. */
@Composable
internal fun GlassLabScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("glass-workbench-v1", Context.MODE_PRIVATE) }
    var parameters by rememberSaveable(stateSaver = GlassLabParameters.Saver) {
        mutableStateOf(GlassLabParameters.from(GlassLabControl.entries.map { control ->
            runCatching { prefs.getFloat(control.name, control.value(GlassLabParameters())) }.getOrDefault(control.value(GlassLabParameters()))
        }))
    }
    val latestParameters by rememberUpdatedState(parameters)
    val defaultDark = LocalGlassEnvironment.current.isDark
    fun save(p: GlassLabParameters) {
        prefs.edit().apply { GlassLabControl.entries.forEach { putFloat(it.name, it.value(p)) } }.apply()
    }
    LaunchedEffect(parameters) { delay(300); save(parameters) }
    DisposableEffect(prefs) { onDispose { save(latestParameters) } }
    var preset by rememberSaveable { mutableIntStateOf(when(parameters) {
        GlassLabParameters.Regular -> 0
        GlassLabParameters.Clear -> 1
        else -> 2
    }) }
    var background by rememberSaveable { mutableIntStateOf(0) }
    var compare by rememberSaveable { mutableStateOf(false) }
    var animate by rememberSaveable { mutableStateOf(false) }
    var dark by rememberSaveable { mutableStateOf(defaultDark) }
    var resetToken by remember { mutableIntStateOf(0) }
    var export by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<GlassLabControl?>(null) }
    val scheme = MaterialTheme.colorScheme
    val onChange: (GlassLabParameters) -> Unit = { parameters = it; preset = 2 }
    Column(Modifier.fillMaxSize().background(scheme.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min=56.dp), verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack,stringResource(R.string.back)) }
            Text(stringResource(R.string.glass_lab_title),style=MaterialTheme.typography.titleLarge,
                modifier=Modifier.weight(1f),fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
            IconButton(onClick={ export=true }) { Icon(Icons.Outlined.ContentCopy,stringResource(R.string.glass_lab_export)) }
            IconButton(onClick={ parameters=GlassLabParameters();preset=2;resetToken++ }) {
                Icon(Icons.Outlined.RestartAlt,stringResource(R.string.glass_lab_reset))
            }
        }
        Text(stringResource(R.string.glass_lab_subtitle),style=MaterialTheme.typography.bodySmall,
            color=scheme.onSurfaceVariant,modifier=Modifier.padding(horizontal=24.dp,vertical=4.dp))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth >= 600.dp
            val previewHeight = (maxHeight * .38f).coerceIn(160.dp, 280.dp)
            if(wide) {
                Row(Modifier.fillMaxSize().padding(horizontal=24.dp),horizontalArrangement=Arrangement.spacedBy(24.dp)) {
                    Column(Modifier.weight(1.35f).fillMaxHeight()) {
                        PreviewControls(compare,{compare=it},background,{background=it})
                        GlassLabPreview(parameters,background,compare,dark,animate,resetToken,
                            Modifier.weight(1f).fillMaxWidth())
                        PreviewFooter(animate,{animate=it},dark,{dark=it})
                    }
                    ParameterPanel(parameters,preset,{index ->
                        preset=index;parameters=if(index==0) GlassLabParameters.Regular else if(index==1) GlassLabParameters.Clear else parameters
                    },onChange,{editing=it},Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize().padding(horizontal=24.dp)) {
                    PreviewControls(compare,{compare=it},background,{background=it})
                    GlassLabPreview(parameters,background,compare,dark,animate,resetToken,
                        Modifier.fillMaxWidth().height(previewHeight))
                    PreviewFooter(animate,{animate=it},dark,{dark=it})
                    HorizontalDivider(color=scheme.outlineVariant.copy(alpha=.5f))
                    ParameterPanel(parameters,preset,{index ->
                        preset=index;parameters=if(index==0) GlassLabParameters.Regular else if(index==1) GlassLabParameters.Clear else parameters
                    },onChange,{editing=it},Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
    if(export) ParameterExport(parameters,{export=false})
    editing?.let { control ->
        var input by remember(control) { mutableStateOf(formatValue(control,control.value(parameters))) }
        val number=input.toFloatOrNull()
        val valid=number!=null && number.isFinite() && number in control.min..control.max
        AlertDialog(onDismissRequest={editing=null},title={Text(control.label)},text={
            OutlinedTextField(value=input,onValueChange={input=it},singleLine=true,
                label={Text("${control.min} – ${control.max} ${control.unit}")},
                isError=!valid,keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType=androidx.compose.ui.text.input.KeyboardType.Decimal))
        },confirmButton={TextButton(enabled=valid,onClick={onChange(control.update(parameters,number!!));editing=null}) {
            Text(stringResource(R.string.glass_lab_apply))
        }},dismissButton={TextButton(onClick={editing=null}){Text(stringResource(R.string.cancel))}})
    }
}

@Composable
private fun PreviewControls(compare:Boolean,onCompare:(Boolean)->Unit,background:Int,onBackground:(Int)->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
        Text(stringResource(R.string.glass_lab_preview),style=MaterialTheme.typography.titleSmall)
        TextButton(enabled=Build.VERSION.SDK_INT>=33,onClick={onCompare(!compare)}) {
            Text(stringResource(if(compare) R.string.glass_lab_show_glass else R.string.glass_lab_split))
        }
    }
    PillSegmentedControl(listOf(stringResource(R.string.glass_lab_grid),stringResource(R.string.glass_lab_spectrum),
        stringResource(R.string.glass_lab_text)),background,onBackground,Modifier.fillMaxWidth())
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun PreviewFooter(animate:Boolean,onAnimate:(Boolean)->Unit,dark:Boolean,onDark:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(stringResource(R.string.glass_lab_dynamic),style=MaterialTheme.typography.labelMedium)
        Switch(checked=animate,onCheckedChange=onAnimate,modifier=Modifier.padding(start=4.dp)
            .semantics {contentDescription="动态光线"})
        Spacer(Modifier.weight(1f))
        Text(stringResource(R.string.glass_lab_dark),style=MaterialTheme.typography.labelMedium)
        Switch(checked=dark,onCheckedChange=onDark,modifier=Modifier.padding(start=4.dp)
            .semantics {contentDescription="深色背景"})
    }
}

@Composable
private fun ParameterPanel(p:GlassLabParameters,preset:Int,onPreset:(Int)->Unit,onChange:(GlassLabParameters)->Unit,
    onEdit:(GlassLabControl)->Unit,modifier:Modifier) {
    val scheme=MaterialTheme.colorScheme
    Column(modifier.verticalScroll(rememberScrollState()).padding(top=12.dp,bottom=24.dp)) {
        PillSegmentedControl(listOf("Regular","Clear",stringResource(R.string.glass_lab_custom)),preset,onPreset,Modifier.fillMaxWidth())
        Text(stringResource(when(preset){0->R.string.glass_lab_regular_hint;1->R.string.glass_lab_clear_hint;else->R.string.glass_lab_custom_hint}),
            style=MaterialTheme.typography.bodySmall,color=scheme.onSurfaceVariant,modifier=Modifier.padding(vertical=12.dp))
        if(Build.VERSION.SDK_INT<33)Text(stringResource(R.string.glass_lab_api_hint),style=MaterialTheme.typography.bodySmall,
            color=scheme.error,modifier=Modifier.padding(bottom=12.dp))
        GlassLabControl.entries.forEachIndexed { index,control ->
            if(index==0||index==4||index==11)Text(stringResource(when(index){0->R.string.glass_lab_geometry;4->R.string.glass_lab_optics;else->R.string.glass_lab_light}),
                style=MaterialTheme.typography.labelLarge,color=scheme.onSurfaceVariant,
                modifier=Modifier.padding(top=if(index==0) 0.dp else 16.dp,bottom=4.dp).semantics{heading()})
            val shaderOnly=control in listOf(GlassLabControl.Depth,GlassLabControl.Curvature,GlassLabControl.Refraction,GlassLabControl.Splay,GlassLabControl.Chroma)
            ParameterSlider(control,control.value(p),{onChange(control.update(p,it))},{onEdit(control)},!shaderOnly||Build.VERSION.SDK_INT>=33)
        }
        Text(stringResource(R.string.glass_lab_note),style=MaterialTheme.typography.bodySmall,color=scheme.onSurfaceVariant,
            modifier=Modifier.padding(top=18.dp))
    }
}

private fun formatValue(c:GlassLabControl,v:Float)=if(c.decimal)String.format(Locale.US,"%.2f",v) else v.toInt().toString()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParameterSlider(control:GlassLabControl,value:Float,onChange:(Float)->Unit,onEdit:()->Unit,enabled:Boolean) {
    val scheme=MaterialTheme.colorScheme
    val label=control.label
    val colors=SliderDefaults.colors(activeTrackColor=scheme.primary,inactiveTrackColor=scheme.onSurface.copy(alpha=.1f))
    Row(Modifier.fillMaxWidth().heightIn(min=52.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(label,fontSize=13.sp,color=scheme.onSurfaceVariant,modifier=Modifier.width(104.dp))
        Slider(value=value,onValueChange=onChange,valueRange=control.min..control.max,enabled=enabled,colors=colors,
            modifier=Modifier.weight(1f).semantics{contentDescription=label},
            thumb={Box(Modifier.size(24.dp,48.dp),contentAlignment=Alignment.Center) {
                Box(Modifier.size(22.dp).shadow(2.dp,CircleShape).background(Color.White,CircleShape)
                    .border(.5.dp,Color.Black.copy(alpha=.1f),CircleShape))
            }},track={state->SliderDefaults.Track(sliderState=state,colors=colors,modifier=Modifier.height(4.dp),
                thumbTrackGapSize=0.dp,trackInsideCornerSize=2.dp,drawStopIndicator=null)})
        TextButton(onClick=onEdit,enabled=enabled,modifier=Modifier.width(64.dp),contentPadding=PaddingValues(0.dp)) {
            Column(horizontalAlignment=Alignment.End) {
                Text(formatValue(control,value),fontSize=13.sp,fontFamily=FontFamily.Monospace,color=scheme.onSurface)
                if(control.unit.isNotEmpty()) Text(control.unit,fontSize=10.sp,color=scheme.onSurfaceVariant)
            }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun ParameterExport(p:GlassLabParameters,onClose:()->Unit) {
    val clipboard=LocalClipboardManager.current
    val json=remember(p) {
        JSONObject().put("format","m1racle-glass-workbench-v1").put("engine","Backdrop 2.0.0 + experimental AGSL lens")
            .put("parameters",JSONObject().apply {GlassLabControl.entries.forEach {put(it.label,it.value(p).toDouble())}}).toString(2)
    }
    var copied by remember{mutableStateOf(false)}
    AlertDialog(onDismissRequest=onClose,title={Text(stringResource(R.string.glass_lab_export))},text={
        Text(json,fontFamily=FontFamily.Monospace,fontSize=12.sp,modifier=Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState()))
    },confirmButton={TextButton(onClick={clipboard.setText(AnnotatedString(json));copied=true}) {
        Text(stringResource(if(copied)R.string.glass_lab_copied else R.string.glass_lab_copy))
    }},dismissButton={TextButton(onClick=onClose){Text(stringResource(R.string.glass_lab_close))}})
}
