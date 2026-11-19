package pl.adrian.ptaki.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.adrian.ptaki.Journal
import pl.adrian.ptaki.Player
import pl.adrian.ptaki.Prefs
import java.util.Locale

@Composable
fun SettingsDialog(onDismiss: () -> Unit) {
    var thr by remember { mutableFloatStateOf(Prefs.threshold) }
    var loc by remember { mutableStateOf(Prefs.locationFilter) }
    var clips by remember { mutableStateOf(Prefs.saveClips) }
    var clipsMb by remember { mutableStateOf(Journal.clipsBytes() / 1_048_576f) }
    var confirmWipe by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.surface,
        title = { Text("Ustawienia", fontFamily = Serif, color = C.text) },
        text = {
            Column {
                Text("Próg pewności: ${(thr * 100).toInt()}%", color = C.text, fontSize = 16.sp)
                Slider(
                    value = thr,
                    onValueChange = { thr = it; Prefs.threshold = it },
                    valueRange = 0.2f..0.9f,
                    colors = SliderDefaults.colors(thumbColor = C.accent, activeTrackColor = C.accent)
                )
                Text(
                    "Niżej = więcej ptaków, ale i więcej pomyłek. Wyżej = tylko pewne rozpoznania.",
                    color = C.text2, fontSize = 13.sp
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Tylko ptaki z mojej okolicy", color = C.text, fontSize = 16.sp)
                        Text(
                            "Odrzuca gatunki, których tu o tej porze roku nie ma (wg lokalizacji). Działa od następnego startu.",
                            color = C.text2, fontSize = 13.sp
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Switch(
                        checked = loc,
                        onCheckedChange = { loc = it; Prefs.locationFilter = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = C.moss, checkedThumbColor = C.live)
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Zapisuj nagrania w dzienniku", color = C.text, fontSize = 16.sp)
                        Text(
                            "3 s najpewniejszego fragmentu każdej obserwacji (ok. 140 kB). " +
                                "Teraz zajmują: ${String.format(Locale.US, "%.1f", clipsMb)} MB.",
                            color = C.text2, fontSize = 13.sp
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Switch(
                        checked = clips,
                        onCheckedChange = { clips = it; Prefs.saveClips = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = C.moss, checkedThumbColor = C.live)
                    )
                }
                if (clipsMb > 0f) {
                    TextButton(onClick = {
                        if (confirmWipe) {
                            Player.stop()
                            Journal.deleteAllClips()
                            clipsMb = 0f
                            confirmWipe = false
                        } else confirmWipe = true
                    }) {
                        Text(
                            if (confirmWipe) "Na pewno? Dotknij jeszcze raz (dziennik zostaje)" else "Usuń wszystkie nagrania",
                            color = C.accent
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = C.line)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Rozpoznawanie: model BirdNET V2.4 — K. Lisa Yang Center for Conservation Bioacoustics, " +
                        "Cornell Lab of Ornithology i Chemnitz University of Technology. Licencja CC BY-NC-SA 4.0 " +
                        "(użytek niekomercyjny). Opisy i zdjęcia: Wikipedia, CC BY-SA.",
                    color = C.text2, fontSize = 12.sp, lineHeight = 17.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Gotowe", color = C.accent) }
        },
    )
}
