package com.baglamaarsivim.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Palet bağlamanın malzemelerinden: dut/ardıç tekne (ceviz kahvesi), kapak (ladin sarısı),
// sedef kakma (fildişi) ve perde bağları (koyu kahve).
private val Ceviz = Color(0xFF6B3E1F)
private val CevizKoyu = Color(0xFF3E2616)
private val Kehribar = Color(0xFFC98A45)
private val Ladin = Color(0xFFF3D9A4)
private val Fildisi = Color(0xFFFBF6EE)
private val Sedef = Color(0xFFF1E7D8)

private val Acik = lightColorScheme(
    primary = Ceviz,
    onPrimary = Color.White,
    primaryContainer = Ladin,
    onPrimaryContainer = CevizKoyu,
    secondary = Kehribar,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF6E3C6),
    onSecondaryContainer = CevizKoyu,
    tertiary = Color(0xFF3F6B5B),
    background = Fildisi,
    onBackground = Color(0xFF2A1C12),
    surface = Fildisi,
    onSurface = Color(0xFF2A1C12),
    surfaceVariant = Sedef,
    onSurfaceVariant = Color(0xFF5B4636),
    surfaceContainer = Color(0xFFF5EDE1),
    surfaceContainerHigh = Color(0xFFEFE5D6),
    outline = Color(0xFF9C8573)
)

private val Koyu = darkColorScheme(
    primary = Color(0xFFE8B77C),
    onPrimary = CevizKoyu,
    primaryContainer = Color(0xFF5A3519),
    onPrimaryContainer = Ladin,
    secondary = Kehribar,
    onSecondary = CevizKoyu,
    secondaryContainer = Color(0xFF4A3322),
    onSecondaryContainer = Ladin,
    tertiary = Color(0xFF9CCDB9),
    background = Color(0xFF1C140E),
    onBackground = Color(0xFFEFE2D2),
    surface = Color(0xFF1C140E),
    onSurface = Color(0xFFEFE2D2),
    surfaceVariant = Color(0xFF3A2C21),
    onSurfaceVariant = Color(0xFFD7C3B0),
    surfaceContainer = Color(0xFF261B13),
    surfaceContainerHigh = Color(0xFF2F2319),
    outline = Color(0xFF9C8573)
)

private val Yazilar = Typography().let { t ->
    t.copy(
        headlineSmall = t.headlineSmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontSize = 11.sp, letterSpacing = 0.4.sp, fontWeight = FontWeight.Medium)
    )
}

@Composable
fun BaglamaTema(icerik: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Koyu else Acik,
        typography = Yazilar,
        content = icerik
    )
}
