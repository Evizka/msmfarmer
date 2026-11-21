package com.msmfarmer.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Спокойная светлая палитра концепции «Минимал / Material 3». */
object AppColors {
    val Canvas = Color(0xFFFAF9F4)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceMuted = Color(0xFFEFF3ED)
    val Border = Color(0xFFE3E9E0)
    val Text = Color(0xFF1E2B23)
    val TextMuted = Color(0xFF66766B)
    val Primary = Color(0xFF2F6B4F)
    val PrimarySoft = Color(0xFFDCEBDD)
    val Success = Color(0xFF2E7D4F)
    val Danger = Color(0xFFB44A3F)
    val Gold = Color(0xFFE0A81E)
}

private val Light = lightColorScheme(
    primary = AppColors.Primary,
    onPrimary = Color.White,
    primaryContainer = AppColors.PrimarySoft,
    onPrimaryContainer = AppColors.Primary,
    secondary = AppColors.Primary,
    onSecondary = Color.White,
    background = AppColors.Canvas,
    onBackground = AppColors.Text,
    surface = AppColors.Surface,
    onSurface = AppColors.Text,
    surfaceVariant = AppColors.SurfaceMuted,
    onSurfaceVariant = AppColors.TextMuted,
    outline = AppColors.Border,
    error = AppColors.Danger,
)

private val AppTypography = Typography(
    titleLarge = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontSize = 17.sp,
        lineHeight = 23.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = TextStyle(
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
    ),
)

@Composable
fun MSMFarmerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Light,
        typography = AppTypography,
        content = content,
    )
}
