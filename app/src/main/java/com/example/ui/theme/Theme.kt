package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class CivicGlassPalette(
    val isDark: Boolean,
    val canvasStart: Color,
    val canvasMid: Color,
    val canvasEnd: Color,
    val glassTop: Color,
    val glassBottom: Color,
    val glassBorder: Color,
    val orbPrimary: Color,
    val orbSecondary: Color,
    val orbTertiary: Color,
    val primaryActionBg: Color,
    val primaryActionBgEnd: Color,
    val primaryActionText: Color,
    val accentPill: Color,
    val subtleSurface: Color
)

private val LightGlassPalette = CivicGlassPalette(
    isDark = false,
    canvasStart = AlabasterCanvasStart,
    canvasMid = AlabasterCanvasMid,
    canvasEnd = AlabasterCanvasEnd,
    glassTop = AlabasterGlassTop,
    glassBottom = AlabasterGlassBottom,
    glassBorder = AlabasterGlassBorder,
    orbPrimary = AlabasterOrbPeriwinkle,
    orbSecondary = AlabasterOrbWarm,
    orbTertiary = AlabasterOrbCyan,
    primaryActionBg = AlabasterInkPrimary,
    primaryActionBgEnd = Color(0xFF1F242D),
    primaryActionText = Color.White,
    accentPill = AlabasterPeriwinkle,
    subtleSurface = Color(0x66FFFFFF)
)

private val DarkGlassPalette = CivicGlassPalette(
    isDark = true,
    canvasStart = ObsidianCanvasStart,
    canvasMid = ObsidianCanvasMid,
    canvasEnd = ObsidianCanvasEnd,
    glassTop = ObsidianGlassTop,
    glassBottom = ObsidianGlassBottom,
    glassBorder = ObsidianGlassBorder,
    orbPrimary = ObsidianOrbPeriwinkle,
    orbSecondary = ObsidianOrbCyan,
    orbTertiary = ObsidianOrbIndigo,
    primaryActionBg = ObsidianButtonStart,
    primaryActionBgEnd = ObsidianButtonEnd,
    primaryActionText = Color.White,
    accentPill = ObsidianPeriwinkle,
    subtleSurface = Color(0x26FFFFFF)
)

val LocalCivicGlass = staticCompositionLocalOf { LightGlassPalette }

private val CivicLightColorScheme = lightColorScheme(
    primary = AlabasterPeriwinkle,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E3FF),
    onPrimaryContainer = Color(0xFF1B236E),
    secondary = Color(0xFF0E7490),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFFAFE),
    onSecondaryContainer = Color(0xFF083344),
    background = AlabasterCanvasMid,
    onBackground = AlabasterInkPrimary,
    surface = Color(0xFFF8F7F4),
    onSurface = AlabasterInkPrimary,
    surfaceVariant = Color(0xFFE6E4DE),
    onSurfaceVariant = AlabasterInkSecondary,
    outline = Color(0x33111315),
    error = CivicCrimson,
    onError = Color.White
)

private val CivicDarkColorScheme = darkColorScheme(
    primary = ObsidianPeriwinkle,
    onPrimary = Color(0xFF0A0D14),
    primaryContainer = Color(0xFF232B52),
    onPrimaryContainer = Color(0xFFE0E3FF),
    secondary = CivicCyan,
    onSecondary = Color(0xFF082F49),
    secondaryContainer = Color(0xFF13334C),
    onSecondaryContainer = Color(0xFFCFFAFE),
    background = ObsidianCanvasStart,
    onBackground = ObsidianInkPrimary,
    surface = ObsidianCanvasMid,
    onSurface = ObsidianInkPrimary,
    surfaceVariant = Color(0xFF1C2438),
    onSurfaceVariant = ObsidianInkSecondary,
    outline = Color(0x2EFFFFFF),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A)
)

private val CivicShapes = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun CivicTriageTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) CivicDarkColorScheme else CivicLightColorScheme
    val glassPalette = if (darkTheme) DarkGlassPalette else LightGlassPalette

    CompositionLocalProvider(LocalCivicGlass provides glassPalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CivicTypography,
            shapes = CivicShapes,
            content = content
        )
    }
}
