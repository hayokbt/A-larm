package io.github.arashiyama11.a_larm.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF7790ED),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF4E6ED1),
    onPrimaryContainer = Color(0xFFE1E8FF),
    inversePrimary = Color(0xFFB3C4FF),

    secondary = Color(0xFFED9777),
    onSecondary = Color(0xFF1F1A16),
    secondaryContainer = Color(0xFF6E3A25),
    onSecondaryContainer = Color(0xFFFFDBCE),

    tertiary = Color(0xFF77EDB3),
    onTertiary = Color(0xFF0A1F16),
    tertiaryContainer = Color(0xFF1E3A2B),
    onTertiaryContainer = Color(0xFFCEFAE0),

    background = Color(0xFF1E1F28),
    onBackground = Color(0xFFECEFF4),
    surface = Color(0xFF1E1F28),
    onSurface = Color(0xFFECEFF4),

    surfaceVariant = Color(0xFF2C2E3A),
    onSurfaceVariant = Color(0xFFBFC4CB),
    surfaceTint = Color(0xFF7790ED),

    inverseSurface = Color(0xFFECEFF4),
    inverseOnSurface = Color(0xFF1E1F28),

    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),

    outline = Color(0xFF8F92A1),
    outlineVariant = Color(0xFF3F4146),
    scrim = Color(0xFF000000),

    surfaceBright = Color(0xFF2C2E3A),
    surfaceDim = Color(0xFF14151A),

    surfaceContainer = Color(0xFF23252F),
    surfaceContainerHigh = Color(0xFF2B2D38),
    surfaceContainerHighest = Color(0xFF343643),
    surfaceContainerLow = Color(0xFF1A1B24),
    surfaceContainerLowest = Color(0xFF121217),
)

private val LightColorScheme = lightColorScheme(
    // Primary
    primary = Color(0xFF7790ED),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD7E0FE),
    onPrimaryContainer = Color(0xFF00154C),
    inversePrimary = Color(0xFF4E535E),

    // Secondary
    secondary = Color(0xFF5B6CED),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0E4FF),
    onSecondaryContainer = Color(0xFF00124F),

    // Tertiary (Text/Icon)
    tertiary = Color(0xFF262E37),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDCE1E6),
    onTertiaryContainer = Color(0xFF0E1318),

    // Background & Surface をニュートラルなほぼ白に
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF262E37),
    surface = Color(0xFFFAFAFA),
    onSurface = Color(0xFF262E37),

    // Surface variants（コンテナやカード背景など）
    surfaceVariant = Color(0xFFF0F0F2),
    onSurfaceVariant = Color(0xFF494C50),
    surfaceTint = Color(0xFF7790ED),

    // Inverse surfaces
    inverseSurface = Color(0xFF353742),
    inverseOnSurface = Color(0xFFECEFF4),

    // Error（そのまま）
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),

    // その他
    outline = Color(0xFF7F8896),
    scrim = Color(0xFF000000),

    // カスタム拡張
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFEEEEEE),

    surfaceContainer = Color(0xFFF7F7F7),
    surfaceContainerHigh = Color(0xFFF9F9F9),
    surfaceContainerHighest = Color(0xFFFBFBFB),
    surfaceContainerLow = Color(0xFFEFEFEF),
    surfaceContainerLowest = Color(0xFFECECEC),
)


@Composable
fun AlarmTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}