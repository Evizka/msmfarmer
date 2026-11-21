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
    val Canvas = Color(0xFFF7F8F4)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceMuted = Color(0xFFF1F4EE)
    val Border = Color(0xFFE6EAE2)
    val Text = Color(0xFF1F2A24)
    val TextMuted = Color(0xFF6B7A70)
    val Primary = Color(0xFF245C3C)
    val PrimarySoft = Color(0xFFDCE9DC)
    val Success = Color(0xFF2E7D4F)
    val Danger = Color(0xFFB44A3F)
    val Gold = Color(0xFFD9A21B)
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
