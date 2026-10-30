package com.m1racle.reader.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Three UI sizes; document typography keeps its own user-controlled scale. */
object ReaderUiSize {
    val Caption = 12.sp
    val Body = 14.sp
    val Title = 18.sp
}

private val UiTitle = TextStyle(fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold, fontSize = ReaderUiSize.Title, lineHeight = 26.sp)
private val UiBody = TextStyle(fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal, fontSize = ReaderUiSize.Body, lineHeight = 22.sp)
private val UiCaption = TextStyle(fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal, fontSize = ReaderUiSize.Caption, lineHeight = 18.sp)

val M1racleReaderTypography = Typography(
    displayLarge = UiTitle, displayMedium = UiTitle, displaySmall = UiTitle,
    headlineLarge = UiTitle, headlineMedium = UiTitle, headlineSmall = UiTitle,
    titleLarge = UiTitle,
    titleMedium = UiBody.copy(fontWeight = FontWeight.Medium),
    titleSmall = UiBody.copy(fontWeight = FontWeight.Medium),
    bodyLarge = UiBody, bodyMedium = UiBody, bodySmall = UiCaption,
    labelLarge = UiBody, labelMedium = UiCaption, labelSmall = UiCaption,
)
