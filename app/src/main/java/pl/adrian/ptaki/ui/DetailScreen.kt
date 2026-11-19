package pl.adrian.ptaki.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FlutterDash
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.launch
import pl.adrian.ptaki.Hub
import pl.adrian.ptaki.Journal
import pl.adrian.ptaki.Obs
import pl.adrian.ptaki.Player
import pl.adrian.ptaki.RefSound
import pl.adrian.ptaki.Sounds
import pl.adrian.ptaki.Wiki

@Composable
fun DetailPane(sci: String, onClose: (() -> Unit)?) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val all by Journal.items.collectAsStateWithLifecycle()
    val session by Hub.detections.collectAsStateWithLifecycle()
    val playing by Player.playing.collectAsStateWithLifecycle()

    val obs = remember(all, sci) { all.filter { it.sci == sci } }
    val det = session.firstOrNull { it.sci == sci }
    val name = obs.firstOrNull()?.name ?: det?.name ?: sci
    val (loading, info) = rememberWiki(sci, name)

    var ref by remember(sci) { mutableStateOf<RefSound?>(null) }
    var refState by remember(sci) { mutableStateOf(0) }   // 0 nic, 1 ładuję, 2 brak
    var toDelete by remember { mutableStateOf<Obs?>(null) }

    DisposableEffect(sci) { onDispose { Player.stop() } }

    Column(
        Modifier
            .fillMaxSize()
            .background(C.bg)
            .verticalScroll(rememberScrollState())
    ) {
        Box(Modifier.fillMaxWidth().height(300.dp).background(C.surface)) {
            if (info?.thumb != null) {
                AsyncImage(
                    model = info.bigImage,
                    error = rememberAsyncImagePainter(info.thumb),
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    Icons.Outlined.FlutterDash, null, tint = C.line,
                    modifier = Modifier.size(110.dp).align(Alignment.Center)
                )
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.55f to Color.Transparent, 1f to C.bg)
                )
            )
            if (onClose != null) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.padding(10.dp).clip(CircleShape).background(C.bg.copy(alpha = 0.6f))
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wróć", tint = C.text)
                }
            }
        }

        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(name, fontFamily = Serif, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = C.text)
            Text(sci, fontSize = 17.sp, fontStyle = FontStyle.Italic, color = C.accent)
            Spacer(Modifier.height(14.dp))

            // statystyki z dziennika (albo z bieżącej sesji, jeśli dziennik pusty)
            val first = obs.minOfOrNull { it.start } ?: det?.first
            val last = obs.maxOfOrNull { it.end } ?: det?.last
            val best = obs.maxOfOrNull { it.best } ?: det?.best ?: 0f
            val days = obs.map { dayKey(it.start) }.distinct().size
            if (first != null && last != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat("Pierwszy raz", fmtWhen(first), Modifier.weight(1f))
                    Stat("Ostatnio", fmtWhen(last), Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat(
                        "Obserwacji",
                        if (obs.isEmpty()) "${det?.count ?: 0}" else "${obs.size} (dni: $days)",
                        Modifier.weight(1f)
                    )
                    Stat("Najwyższa pewność", "${(best * 100).toInt()}%", Modifier.weight(1f))
                }
            }

            // wzorcowy głos
            Spacer(Modifier.height(16.dp))
            val refKey = "ref:$sci"
            Button(
                onClick = {
                    val r = ref
                    if (r != null) {
                        Player.toggle(refKey, r.file)
                    } else if (refState != 1) {
                        refState = 1
                        scope.launch {
                            val got = Sounds.get(ctx, sci)
                            ref = got
                            refState = if (got == null) 2 else 0
                            if (got != null) Player.toggle(refKey, got.file)
                        }
                    }
                },
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = C.surface2, contentColor = C.text),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                when {
                    refState == 1 -> CircularProgressIndicator(Modifier.size(20.dp), color = C.accent, strokeWidth = 2.dp)
                    playing == refKey -> Icon(Icons.Filled.Stop, null)
                    else -> Icon(Icons.Outlined.MusicNote, null)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        refState == 1 -> "Szukam nagrania…"
                        playing == refKey -> "Zatrzymaj"
                        else -> "Posłuchaj, jak śpiewa (wzorzec)"
                    },
                    fontSize = 16.sp
                )
            }
            ref?.let { r ->
                Text(
                    "Nagranie: ${r.title} — Wikimedia Commons",
                    color = C.text2.copy(alpha = 0.7f), fontSize = 11.sp,
                    modifier = Modifier
                        .padding(top = 4.dp, start = 4.dp)
                        .clickable { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.page))) }
                )
            }
            if (refState == 2) {
                Text(
                    "Nie znalazłem nagrania wzorcowego (albo brak internetu).",
                    color = C.text2, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                )
            }

            // opis z Wikipedii
            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = C.line)
            Spacer(Modifier.height(16.dp))
            when {
                info != null -> {
                    Text(info.extract, color = C.text, fontSize = 16.sp, lineHeight = 25.sp)
                    if (info.lang == "en") {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Polska Wikipedia nie ma artykułu — opis z angielskiej.",
                            color = C.text2, fontSize = 12.sp, fontStyle = FontStyle.Italic
                        )
                    }
                }
                loading -> Text("Pobieram opis z Wikipedii…", color = C.text2)
                else -> Text(
                    "Nie udało się pobrać opisu. Sprawdź internet albo otwórz Wikipedię przyciskiem poniżej.",
                    color = C.text2
                )
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val url = info?.url ?: Wiki.pageUrl(sci)
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                },
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = C.moss, contentColor = C.text),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, null)
                Spacer(Modifier.width(8.dp))
                Text("Więcej w Wikipedii", fontSize = 16.sp)
            }

            // twoje obserwacje i nagrania
            if (obs.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text(
                    "Twoje obserwacje", fontFamily = Serif, fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold, color = C.text
                )
                Text(
                    "Odtwarzany jest najpewniejszy 3-sekundowy fragment każdej obserwacji.",
                    color = C.text2, fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                obs.take(60).forEach { o ->
                    ObsRow(o, playing == "obs:${o.id}", onPlay = {
                        Journal.clipFile(o)?.let { Player.toggle("obs:${o.id}", it) }
                    }, onDelete = { toDelete = o })
                    Spacer(Modifier.height(6.dp))
                }
                if (obs.size > 60) Text("…i ${obs.size - 60} starszych", color = C.text2, fontSize = 12.sp)
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "Tekst i zdjęcie: Wikipedia (CC BY-SA). Rozpoznanie: BirdNET.",
                color = C.text2.copy(alpha = 0.6f), fontSize = 11.sp
            )
            Spacer(Modifier.height(28.dp))
        }
    }

    toDelete?.let { o ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            containerColor = C.surface,
            title = { Text("Usunąć obserwację?", color = C.text) },
            text = {
                Text(
                    "${o.name}, ${fmtWhen(o.start)}. Przyda się, jeśli to była pomyłka. Nagranie też zostanie usunięte.",
                    color = C.text2
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    Player.stop()
                    Journal.delete(o.id)
                    toDelete = null
                }) { Text("Usuń", color = C.accent) }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text("Anuluj", color = C.text2) }
            }
        )
    }
}

@Composable
private fun ObsRow(o: Obs, isPlaying: Boolean, onPlay: () -> Unit, onDelete: () -> Unit) {
    val hasClip = Journal.clipFile(o) != null
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(C.surface)
            .border(1.dp, if (isPlaying) C.live.copy(alpha = 0.6f) else C.line, RoundedCornerShape(12.dp))
            .padding(start = 4.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPlay, enabled = hasClip) {
            Icon(
                if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                if (isPlaying) "Zatrzymaj" else "Odtwórz",
                tint = if (hasClip) C.accent else C.line
            )
        }
        Column(Modifier.weight(1f)) {
            val span = if (o.end - o.start < 60_000) fmtWhen(o.start) else "${fmtWhen(o.start)}–${fmtHm(o.end)}"
            Text(span, color = C.text, fontSize = 15.sp)
            Text(
                "pewność ${(o.best * 100).toInt()}%" + if (!hasClip) " · bez nagrania" else "",
                color = C.text2, fontSize = 12.sp
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.DeleteOutline, "Usuń", tint = C.text2.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(C.surface)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Text(label, color = C.text2, fontSize = 11.sp)
        Text(value, color = C.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}
