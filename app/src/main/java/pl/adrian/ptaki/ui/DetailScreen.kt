package pl.adrian.ptaki.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.FlutterDash
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import pl.adrian.ptaki.Detection
import pl.adrian.ptaki.Wiki

@Composable
fun DetailPane(d: Detection, onClose: (() -> Unit)?) {
    val ctx = LocalContext.current
    val (loading, info) = rememberWiki(d)

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
                    contentDescription = d.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    Icons.Outlined.FlutterDash, null, tint = C.line,
                    modifier = Modifier.size(110.dp).align(Alignment.Center)
                )
            }
            // przyciemnienie dołu, żeby tytuł był czytelny
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
            Text(d.name, fontFamily = Serif, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = C.text)
            Text(d.sci, fontSize = 17.sp, fontStyle = FontStyle.Italic, color = C.accent)
            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Pierwszy raz", fmtTime(d.first), Modifier.weight(1f))
                Stat("Ostatnio", fmtTime(d.last), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Odezwał się", "${d.count}×", Modifier.weight(1f))
                Stat("Najwyższa pewność", "${(d.best * 100).toInt()}%", Modifier.weight(1f))
            }

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

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    val url = info?.url ?: Wiki.pageUrl(d.sci)
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
            Spacer(Modifier.height(10.dp))
            Text(
                "Tekst i zdjęcie: Wikipedia (CC BY-SA). Rozpoznanie: BirdNET.",
                color = C.text2.copy(alpha = 0.6f), fontSize = 11.sp
            )
            Spacer(Modifier.height(28.dp))
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
