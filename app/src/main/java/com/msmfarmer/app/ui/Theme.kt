package com.msmfarmer.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Тёмная Material 3 палитра. */
object AppColors {
    val Canvas = Color(0xFF111815)
    val Surface = Color(0xFF1B2620)
    val SurfaceMuted = Color(0xFF243129)
    val Border = Color(0xFF34443A)
    val Text = Color(0xFFE8F0E9)
    val TextMuted = Color(0xFF9FB0A4)
    val Primary = Color(0xFF7BC79B)
    val PrimarySoft = Color(0xFF2E4A39)
    val Success = Color(0xFF7BC79B)
    val Danger = Color(0xFFF09A8F)
    val Gold = Color(0xFFE5B84B)
}

private val Dark = darkColorScheme(
    primary = AppColors.Primary,
    onPrimary = Color(0xFF102018),
    primaryContainer = AppColors.PrimarySoft,
    onPrimaryContainer = AppColors.Primary,
    secondary = AppColors.Primary,
    onSecondary = Color(0xFF102018),
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
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun MSMFarmerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Dark,
        typography = AppTypography,
        content = content,
    )
}
