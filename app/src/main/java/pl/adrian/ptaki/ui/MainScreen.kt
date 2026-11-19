package pl.adrian.ptaki.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FlutterDash
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import pl.adrian.ptaki.Detection
import pl.adrian.ptaki.Hub
import pl.adrian.ptaki.Status
import pl.adrian.ptaki.Wiki
import pl.adrian.ptaki.WikiInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
fun fmtTime(t: Long): String = timeFmt.format(Date(t))

@Composable
fun AppRoot() {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var journal by rememberSaveable { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val dets by Hub.detections.collectAsStateWithLifecycle()

    @Composable
    fun LeftPane(modifier: Modifier) {
        if (journal) JournalPane(selected, { selected = it }, { journal = false; selected = null }, modifier)
        else MainPane(dets, selected, { selected = it }, { showSettings = true }, { journal = true; selected = null }, modifier)
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(C.bg)
            .safeDrawingPadding()
    ) {
        val wide = maxWidth >= 700.dp   // rozłożony Fold — dwa panele
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                LeftPane(Modifier.weight(1f).fillMaxHeight())
                Box(Modifier.width(1.dp).fillMaxHeight().background(C.line))
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    val s = selected
                    if (s != null) DetailPane(s, onClose = null) else Placeholder()
                }
            }
            BackHandler(enabled = journal) { journal = false; selected = null }
        } else {
            LeftPane(Modifier.fillMaxSize())
            val s = selected
            if (s != null) DetailPane(s, onClose = { selected = null })
            BackHandler(enabled = s != null || journal) {
                if (selected != null) selected = null else journal = false
            }
        }
    }
    if (showSettings) SettingsDialog(onDismiss = { showSettings = false })
}

@Composable
private fun Placeholder() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Outlined.FlutterDash, null, tint = C.line, modifier = Modifier.size(96.dp))
        Spacer(Modifier.height(12.dp))
        Text("Dotknij ptaka na liście, żeby zobaczyć opis", color = C.text2, textAlign = TextAlign.Center)
    }
}

@Composable
private fun MainPane(
    dets: List<Detection>,
    selected: String?,
    onSelect: (String) -> Unit,
    onSettings: () -> Unit,
    onJournal: () -> Unit,
    modifier: Modifier,
) {
    val ctx = LocalContext.current
    val status by Hub.status.collectAsStateWithLifecycle()
    val filterInfo by Hub.filterInfo.collectAsStateWithLifecycle()
    val running = status is Status.Listening || status is Status.Loading

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val mic = res[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (mic) Hub.start(ctx)
        else Toast.makeText(ctx, "Bez dostępu do mikrofonu nie usłyszę ptaków", Toast.LENGTH_LONG).show()
    }

    fun onStartStop() {
        if (running) {
            Hub.stop(ctx)
            return
        }
        val perms = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED }
        if (perms.isEmpty()) Hub.start(ctx) else launcher.launch(perms.toTypedArray())
    }

    // zegar do odświeżania znaczka "słychać teraz"
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    Column(modifier.padding(horizontal = 14.dp)) {
        // nagłówek
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Ptaki", fontFamily = Serif, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = C.text)
                Text(statusText(status, dets.size), color = statusColor(status), fontSize = 13.sp)
            }
            if (dets.isNotEmpty()) {
                IconButton(onClick = { Hub.clear() }) {
                    Icon(Icons.Outlined.DeleteSweep, "Wyczyść listę", tint = C.text2)
                }
            }
            IconButton(onClick = onJournal) {
                Icon(Icons.Outlined.AutoStories, "Dziennik obserwacji", tint = C.accent)
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Outlined.Tune, "Ustawienia", tint = C.text2)
            }
        }
        Spacer(Modifier.height(10.dp))

        // spektrogram
        Box(
            Modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, C.line, RoundedCornerShape(16.dp))
        ) {
            SpectrogramView(Modifier.fillMaxSize())
            if (!running) {
                Text(
                    "Tu zobaczysz dźwięk",
                    color = C.text2.copy(alpha = 0.6f),
                    fontFamily = Serif,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        filterInfo?.let {
            Text(it, color = C.text2.copy(alpha = 0.7f), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp, start = 4.dp))
        }
        Spacer(Modifier.height(10.dp))

        StartButton(running, status is Status.Loading, ::onStartStop)
        Spacer(Modifier.height(12.dp))

        if (dets.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    if (running) "Nasłuchuję… Pierwsze rozpoznanie po ok. 3 sekundach śpiewu."
                    else "Naciśnij Start i poczekaj, aż jakiś ptak się odezwie.",
                    color = C.text2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(dets, key = { it.sci }) { d ->
                    DetectionRow(d, selected == d.sci, now - d.last < 6_000) { onSelect(d.sci) }
                }
            }
        }
    }
}

private fun statusText(s: Status, n: Int): String = when (s) {
    Status.Idle -> if (n == 0) "Gotowy do nasłuchu" else "Zatrzymano · gatunków: $n"
    Status.Loading -> "Wczytuję model rozpoznawania…"
    Status.Listening -> if (n == 0) "Nasłuchuję" else "Nasłuchuję · gatunków: $n"
    is Status.Error -> s.msg
}

private fun statusColor(s: Status) = when (s) {
    Status.Listening -> C.live
    is Status.Error -> C.accent
    else -> C.text2
}

@Composable
private fun StartButton(running: Boolean, loading: Boolean, onClick: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val a by pulse.animateFloat(
        0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a"
    )
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (running) C.rust else C.moss,
            contentColor = C.text,
        ),
    ) {
        if (running) {
            Box(Modifier.size(10.dp).alpha(a).clip(CircleShape).background(if (loading) C.accent else C.live))
            Spacer(Modifier.width(10.dp))
            Icon(Icons.Filled.Stop, null)
            Spacer(Modifier.width(6.dp))
            Text("Zatrzymaj", fontSize = 17.sp)
        } else {
            Icon(Icons.Filled.Mic, null)
            Spacer(Modifier.width(8.dp))
            Text("Start nasłuchu", fontSize = 17.sp)
        }
    }
}

@Composable
fun rememberWiki(sci: String, name: String): Pair<Boolean, WikiInfo?> {
    val state by produceState(Wiki.cached(sci).let { (it == null) to it }, sci) {
        if (value.second == null) value = false to Wiki.get(sci, name)
    }
    return state
}

@Composable
private fun DetectionRow(d: Detection, isSelected: Boolean, live: Boolean, onClick: () -> Unit) {
    SpeciesRow(
        d.sci, d.name,
        "${fmtTime(d.last)} · ${(d.lastConf * 100).toInt()}%" + if (d.count > 1) " · ×${d.count}" else "",
        isSelected, live, onClick
    )
}

@Composable
fun SpeciesRow(sci: String, name: String, info3: String, isSelected: Boolean, live: Boolean, onClick: () -> Unit) {
    val (_, info) = rememberWiki(sci, name)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) C.surface2 else C.surface)
            .border(1.dp, if (live) C.live.copy(alpha = 0.55f) else C.line, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(58.dp).clip(RoundedCornerShape(10.dp)).background(C.surface2),
            contentAlignment = Alignment.Center
        ) {
            if (info?.thumb != null) {
                AsyncImage(
                    model = info.thumb,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Outlined.FlutterDash, null, tint = C.text2.copy(alpha = 0.5f))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name, color = C.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                fontFamily = Serif, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(sci, color = C.text2, fontSize = 13.sp, fontStyle = FontStyle.Italic, maxLines = 1)
            Text(info3, color = C.text2.copy(alpha = 0.8f), fontSize = 12.sp)
        }
        if (live) {
            Text(
                "słychać",
                color = C.bg,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(C.live)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}
