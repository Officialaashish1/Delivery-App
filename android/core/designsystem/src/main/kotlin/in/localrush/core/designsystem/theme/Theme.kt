package `in`.localrush.core.designsystem.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val LightColors: ColorScheme = lightColorScheme(
    primary = LRColors.Orange,
    onPrimary = Color.White,
    primaryContainer = LRColors.OrangeContainer,
    onPrimaryContainer = LRColors.OrangeDark,
    secondary = LRColors.Charcoal,
    onSecondary = Color.White,
    secondaryContainer = LRColors.SurfaceDim,
    onSecondaryContainer = LRColors.Charcoal,
    tertiary = LRColors.Green,
    onTertiary = Color.White,
    tertiaryContainer = LRColors.GreenLight,
    onTertiaryContainer = LRColors.VegGreen,
    error = LRColors.Red,
    onError = Color.White,
    errorContainer = LRColors.RedLight,
    onErrorContainer = LRColors.Red,
    background = LRColors.SurfaceVariant,
    onBackground = LRColors.TextPrimary,
    surface = LRColors.Surface,
    onSurface = LRColors.TextPrimary,
    surfaceVariant = LRColors.SurfaceVariant,
    onSurfaceVariant = LRColors.TextSecondary,
    surfaceContainer = LRColors.SurfaceVariant,
    surfaceContainerLow = LRColors.Surface,
    surfaceContainerHigh = LRColors.SurfaceDim,
    outline = LRColors.Outline,
    outlineVariant = LRColors.OutlineStrong,
    inverseSurface = LRColors.Charcoal,
    inverseOnSurface = Color.White,
    scrim = Color.Black,
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = LRColors.Orange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4A2A18),
    onPrimaryContainer = LRColors.OrangeLight,
    secondary = Color(0xFFCBD0D9),
    onSecondary = LRColors.Charcoal,
    secondaryContainer = LRColors.DarkSurfaceVariant,
    onSecondaryContainer = LRColors.DarkTextPrimary,
    tertiary = Color(0xFF4FCB84),
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF17402A),
    onTertiaryContainer = Color(0xFFB6F0CC),
    error = Color(0xFFFF6B6B),
    onError = Color.Black,
    errorContainer = Color(0xFF4B1F1F),
    onErrorContainer = Color(0xFFFFC2C2),
    background = LRColors.DarkBackground,
    onBackground = LRColors.DarkTextPrimary,
    surface = LRColors.DarkSurface,
    onSurface = LRColors.DarkTextPrimary,
    surfaceVariant = LRColors.DarkSurfaceVariant,
    onSurfaceVariant = LRColors.DarkTextSecondary,
    surfaceContainer = LRColors.DarkSurfaceVariant,
    surfaceContainerLow = LRColors.DarkSurface,
    surfaceContainerHigh = Color(0xFF2B303A),
    outline = LRColors.DarkOutline,
    outlineVariant = Color(0xFF4A505C),
    inverseSurface = LRColors.DarkTextPrimary,
    inverseOnSurface = LRColors.Charcoal,
)

/** Semantic colours not covered by Material3. */
@Immutable
data class LRExtendedColors(
    val success: Color,
    val onSuccessContainer: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val star: Color,
    val veg: Color,
    val nonVeg: Color,
    val egg: Color,
    val textTertiary: Color,
    val shimmerBase: Color,
    val shimmerHighlight: Color,
)

private val LightExtended = LRExtendedColors(
    success = LRColors.Green, onSuccessContainer = LRColors.VegGreen, successContainer = LRColors.GreenLight,
    warning = LRColors.Amber, warningContainer = LRColors.AmberLight,
    info = LRColors.Blue, infoContainer = LRColors.BlueLight,
    star = LRColors.Star, veg = LRColors.VegGreen, nonVeg = LRColors.NonVegRed, egg = LRColors.EggYellow,
    textTertiary = LRColors.TextTertiary,
    shimmerBase = Color(0xFFE9EBEF), shimmerHighlight = Color(0xFFF8F9FB),
)

private val DarkExtended = LightExtended.copy(
    successContainer = Color(0xFF17402A), onSuccessContainer = Color(0xFFB6F0CC),
    warningContainer = Color(0xFF4A3A14), infoContainer = Color(0xFF1C2B4F),
    textTertiary = Color(0xFF7D8491),
    shimmerBase = Color(0xFF262B34), shimmerHighlight = Color(0xFF333945),
)

val LocalLRExtendedColors = staticCompositionLocalOf { LightExtended }

@Immutable
data class LRSpacing(
    val xxs: Dp = 2.dp, val xs: Dp = 4.dp, val sm: Dp = 8.dp, val md: Dp = 12.dp,
    val lg: Dp = 16.dp, val xl: Dp = 24.dp, val xxl: Dp = 32.dp, val screen: Dp = 16.dp,
)

val LocalLRSpacing = staticCompositionLocalOf { LRSpacing() }

object LRTheme {
    val colors: LRExtendedColors @Composable get() = LocalLRExtendedColors.current
    val spacing: LRSpacing @Composable get() = LocalLRSpacing.current
}

@Composable
fun LocalRushTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val extended = if (darkTheme) DarkExtended else LightExtended
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            // Bars are transparent via enableEdgeToEdge() in each MainActivity; only the icon tint is set here.
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    CompositionLocalProvider(LocalLRExtendedColors provides extended, LocalLRSpacing provides LRSpacing()) {
        MaterialTheme(colorScheme = colorScheme, typography = LRTypography, shapes = LRShapes, content = content)
    }
}
