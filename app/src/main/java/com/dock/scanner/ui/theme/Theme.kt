package com.dock.scanner.ui.theme

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF1E5AA8),
    secondary = Color(0xFF555F71),
    tertiary = Color(0xFF8A5100),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA6C8FF),
    secondary = Color(0xFFBDC7DC),
    tertiary = Color(0xFFFFB86C),
)

/**
 * Size-specific tracking and leading: large titles are tight (negative tracking, short
 * leading), body text sits near zero, small labels get a little extra room.
 */
private val DockTypography = Typography().run {
    copy(
        headlineLarge = headlineLarge.copy(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.7).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
        titleLarge = titleLarge.copy(fontSize = 21.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
        titleMedium = titleMedium.copy(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp),
        titleSmall = titleSmall.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
        bodyLarge = bodyLarge.copy(letterSpacing = 0.sp, lineHeight = 23.sp),
        bodyMedium = bodyMedium.copy(letterSpacing = 0.sp, lineHeight = 20.sp),
        bodySmall = bodySmall.copy(fontSize = 13.sp, letterSpacing = 0.1.sp),
        labelLarge = labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    )
}

/** Motion tokens: critically damped springs by default; no overshoot unless a gesture threw something. */
object DockMotion {
    /** ~0.3 s response, no bounce: the default for anything that moves. */
    fun <T> standard(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)

    /** Quick press feedback. */
    fun <T> press(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)
}

@Composable
fun DockTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, typography = DockTypography, content = content)
}
