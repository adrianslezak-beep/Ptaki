package pl.adrian.ptaki.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/** Paleta "notes przyrodnika": ciemny mech, kremowy papier, mosiądz. */
object C {
    val bg = Color(0xFF0F1611)
    val surface = Color(0xFF172119)
    val surface2 = Color(0xFF203024)
    val line = Color(0xFF2C3B2F)
    val text = Color(0xFFECE5D3)
    val text2 = Color(0xFFA3AD9B)
    val accent = Color(0xFFC9A66B)
    val live = Color(0xFF9CC87F)
    val moss = Color(0xFF3A4C37)
    val rust = Color(0xFF5A3F31)
}

val Serif = FontFamily.Serif

@Composable
fun PtakiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.accent,
            onPrimary = C.bg,
            secondary = C.live,
            background = C.bg,
            onBackground = C.text,
            surface = C.surface,
            onSurface = C.text,
            surfaceVariant = C.surface2,
            onSurfaceVariant = C.text2,
            outline = C.line,
        ),
        content = content,
    )
}
