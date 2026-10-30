package com.m1racle.reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import com.m1racle.reader.ui.theme.M1racleReaderTheme
import com.m1racle.reader.ui.theme.m1racleReaderOutlinedFieldColors

/** Material's supported decoration API, with a density-correct 0.5px border in every state. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = RoundedCornerShape(M1racleReaderTheme.cornerRadius.control),
    colors: TextFieldColors = m1racleReaderOutlinedFieldColors(),
) {
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val textColor = textStyle.color.takeOrElse {
        when {
            !enabled -> colors.disabledTextColor
            isError -> colors.errorTextColor
            focused -> colors.focusedTextColor
            else -> colors.unfocusedTextColor
        }
    }
    val labelInset = with(LocalDensity.current) {
        MaterialTheme.typography.bodySmall.lineHeight.toDp() / 2
    }
    CompositionLocalProvider(LocalTextSelectionColors provides colors.textSelectionColors) {
        BasicTextField(
            value = value, onValueChange = onValueChange,
            modifier = modifier.then(if (label != null) {
                Modifier.semantics(mergeDescendants = true) {}.padding(top = labelInset)
            } else Modifier).semantics { if (isError) error("输入有误") }
                .defaultMinSize(OutlinedTextFieldDefaults.MinWidth, OutlinedTextFieldDefaults.MinHeight),
            enabled = enabled, readOnly = readOnly,
            textStyle = textStyle.merge(TextStyle(color = textColor)),
            cursorBrush = SolidColor(if (isError) colors.errorCursorColor else colors.cursorColor),
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
            singleLine = singleLine, maxLines = maxLines, minLines = minLines,
            interactionSource = interactions,
            decorationBox = { inner ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = value, innerTextField = inner,
                    enabled = enabled, singleLine = singleLine,
                    visualTransformation = visualTransformation, interactionSource = interactions,
                    isError = isError, label = label, placeholder = placeholder,
                    leadingIcon = leadingIcon, trailingIcon = trailingIcon,
                    supportingText = supportingText, colors = colors,
                    container = {
                        val border = when {
                            !enabled -> colors.disabledIndicatorColor
                            isError -> colors.errorIndicatorColor
                            focused -> colors.focusedIndicatorColor
                            else -> colors.unfocusedIndicatorColor
                        }
                        val background = when {
                            !enabled -> colors.disabledContainerColor
                            isError -> colors.errorContainerColor
                            focused -> colors.focusedContainerColor
                            else -> colors.unfocusedContainerColor
                        }
                        Box(Modifier.background(background, shape).readerHairlineBorder(border, shape))
                    },
                )
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderOutlinedTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = RoundedCornerShape(M1racleReaderTheme.cornerRadius.control),
    colors: TextFieldColors = m1racleReaderOutlinedFieldColors(),
) {
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val textColor = textStyle.color.takeOrElse {
        when {
            !enabled -> colors.disabledTextColor
            isError -> colors.errorTextColor
            focused -> colors.focusedTextColor
            else -> colors.unfocusedTextColor
        }
    }
    val labelInset = with(LocalDensity.current) {
        MaterialTheme.typography.bodySmall.lineHeight.toDp() / 2
    }
    CompositionLocalProvider(LocalTextSelectionColors provides colors.textSelectionColors) {
        BasicTextField(
            value = value, onValueChange = onValueChange,
            modifier = modifier.then(if (label != null) {
                Modifier.semantics(mergeDescendants = true) {}.padding(top = labelInset)
            } else Modifier).semantics { if (isError) error("输入有误") }
                .defaultMinSize(OutlinedTextFieldDefaults.MinWidth, OutlinedTextFieldDefaults.MinHeight),
            enabled = enabled, readOnly = readOnly,
            textStyle = textStyle.merge(TextStyle(color = textColor)),
            cursorBrush = SolidColor(if (isError) colors.errorCursorColor else colors.cursorColor),
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
            singleLine = singleLine, maxLines = maxLines, minLines = minLines,
            interactionSource = interactions,
            decorationBox = { inner ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = value.text, innerTextField = inner,
                    enabled = enabled, singleLine = singleLine,
                    visualTransformation = visualTransformation, interactionSource = interactions,
                    isError = isError, label = label, placeholder = placeholder,
                    leadingIcon = leadingIcon, trailingIcon = trailingIcon,
                    supportingText = supportingText, colors = colors,
                    container = {
                        val border = when {
                            !enabled -> colors.disabledIndicatorColor
                            isError -> colors.errorIndicatorColor
                            focused -> colors.focusedIndicatorColor
                            else -> colors.unfocusedIndicatorColor
                        }
                        val background = when {
                            !enabled -> colors.disabledContainerColor
                            isError -> colors.errorContainerColor
                            focused -> colors.focusedContainerColor
                            else -> colors.unfocusedContainerColor
                        }
                        Box(Modifier.background(background, shape).readerHairlineBorder(border, shape))
                    },
                )
            },
        )
    }
}
