package pl.adrian.ptaki.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.adrian.ptaki.Journal
import pl.adrian.ptaki.Obs
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val PL = Locale("pl", "PL")
private val dayKeyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
private val dayFmt = SimpleDateFormat("EEEE, d MMMM yyyy", PL)
private val shortDayFmt = SimpleDateFormat("d.MM.yyyy", PL)
private val hmFmt = SimpleDateFormat("HH:mm", PL)

fun dayKey(t: Long): String = dayKeyFmt.format(Date(t))
fun fmtHm(t: Long): String = hmFmt.format(Date(t))

/** "dziś 06:12", "wczoraj 18:40" albo "12.05.2026 06:12". */
fun fmtWhen(t: Long): String {
    val k = dayKey(t)
    val cal = Calendar.getInstance()
    val today = dayKey(cal.timeInMillis)
    cal.add(Calendar.DAY_OF_YEAR, -1)
    val yesterday = dayKey(cal.timeInMillis)
    val d = when (k) {
        today -> "dziś"
        yesterday -> "wczoraj"
        else -> shortDayFmt.format(Date(t))
    }
    return "$d ${fmtHm(t)}"
}

private fun dayTitle(t: Long): String {
    val w = fmtWhen(t).substringBefore(' ')
    val full = dayFmt.format(Date(t)).replaceFirstChar { it.uppercase() }
    return when (w) {
        "dziś" -> "Dziś · $full"
        "wczoraj" -> "Wczoraj · $full"
        else -> full
    }
}

private data class Agg(
    val sci: String,
    val name: String,
    val count: Int,
    val first: Long,
    val last: Long,
    val best: Float,
    val days: Int,
)

private fun aggregate(list: List<Obs>): List<Agg> =
    list.groupBy { it.sci }.map { (sci, o) ->
        Agg(
            sci = sci,
            name = o.first().name,
            count = o.size,
            first = o.minOf { it.start },
            last = o.maxOf { it.end },
            best = o.maxOf { it.best },
            days = o.map { dayKey(it.start) }.distinct().size,
        )
    }

@Composable
fun JournalPane(selected: String?, onSelect: (String) -> Unit, onBack: () -> Unit, modifier: Modifier) {
    val all by Journal.items.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    val byDay = remember(all) {
        all.groupBy { dayKey(it.start) }.toSortedMap(reverseOrder()).map { (_, obs) ->
            obs.first().start to aggregate(obs).sortedBy { it.first }
        }
    }
    val species = remember(all) { aggregate(all).sortedWith(compareByDescending<Agg> { it.count }.thenBy { it.name }) }

    Column(modifier.padding(horizontal = 14.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wróć", tint = C.text)
            }
            Column(Modifier.weight(1f)) {
                Text("Dziennik", fontFamily = Serif, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = C.text)
                Text(
                    "Gatunków: ${species.size} · obserwacji: ${all.size} · dni: ${byDay.size}",
                    color = C.text2, fontSize = 13.sp
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(C.surface).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("Dzień po dniu", "Wszystkie gatunki").forEachIndexed { i, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (tab == i) C.moss else C.surface)
                        .clickable { tab = i }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, color = if (tab == i) C.text else C.text2, fontSize = 14.sp)
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        if (all.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    "Dziennik jest pusty. Każdy rozpoznany ptak zapisze się tu sam — z godziną i krótkim nagraniem.",
                    color = C.text2, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp)
                )
            }
            return@Column
        }

        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            if (tab == 0) {
                byDay.forEach { (t, aggs) ->
                    item(key = "d" + dayKey(t)) {
                        Text(
                            "${dayTitle(t)} · ${aggs.size} ${gatunki(aggs.size)}",
                            color = C.accent, fontFamily = Serif, fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp, start = 4.dp)
                        )
                    }
                    items(aggs, key = { "d" + dayKey(t) + it.sci }) { a ->
                        val span = if (fmtHm(a.first) == fmtHm(a.last)) fmtHm(a.first) else "${fmtHm(a.first)}–${fmtHm(a.last)}"
                        SpeciesRow(
                            a.sci, a.name,
                            "$span · ${(a.best * 100).toInt()}%" + if (a.count > 1) " · ×${a.count}" else "",
                            selected == a.sci, false
                        ) { onSelect(a.sci) }
                    }
                }
            } else {
                items(species, key = { "s" + it.sci }) { a ->
                    SpeciesRow(
                        a.sci, a.name,
                        "×${a.count} · dni: ${a.days} · ostatnio ${fmtWhen(a.last)}",
                        selected == a.sci, false
                    ) { onSelect(a.sci) }
                }
            }
        }
    }
}

fun gatunki(n: Int): String = when {
    n == 1 -> "gatunek"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "gatunki"
    else -> "gatunków"
}
